//! DrawingML charts (`xl/charts/chartN.xml`): reading the common 2-D/3-D chart groups into the
//! model, and writing model charts (with cached series values) from scratch.

use std::fmt::Write as _;

use gridcraft_core::Value;
use gridcraft_formula::{Expr, SheetSel};
use gridcraft_model::{
    Chart, ChartElements, ChartKind, Color, Depth, ErrorAmount, LabelPos, LegendPos, LineDash, Series, Trendline, TrendlineKind, Workbook,
};

use crate::drawing::{dml_color, srgb};
use crate::xml::{El, esc, num};

// ---------------------------------------------------------------- reading

fn group_kind(g: &El) -> Option<ChartKind> {
    let grouping = g.child_val("grouping").unwrap_or("standard");
    let stacked = grouping == "stacked";
    let pct = grouping == "percentStacked";
    Some(match g.name.as_str() {
        "barChart" | "bar3DChart" => {
            let bar = g.child_val("barDir") == Some("bar");
            match (bar, stacked, pct) {
                (false, true, _) => ChartKind::ColumnStacked,
                (false, _, true) => ChartKind::ColumnStacked100,
                (false, _, _) => ChartKind::ColumnClustered,
                (true, true, _) => ChartKind::BarStacked,
                (true, _, true) => ChartKind::BarStacked100,
                (true, _, _) => ChartKind::BarClustered,
            }
        }
        "lineChart" | "line3DChart" => {
            let group_markers = g.child_val("marker").is_none_or(|v| v == "1" || v == "true");
            let all_none = g.kids("ser").all(|s| s.path(&["marker", "symbol"]).and_then(|m| m.attr("val")) == Some("none"));
            // a 3-D line is a ribbon, without markers
            let markers = g.name != "line3DChart" && group_markers && !(all_none && g.kids("ser").next().is_some());
            match (stacked, pct, markers) {
                (true, _, false) => ChartKind::LineStacked,
                (true, _, true) => ChartKind::LineStackedMarkers,
                (_, true, false) => ChartKind::LineStacked100,
                (_, true, true) => ChartKind::LineStacked100Markers,
                (_, _, true) => ChartKind::LineMarkers,
                _ => ChartKind::Line,
            }
        }
        "pieChart" | "pie3DChart" => ChartKind::Pie,
        "ofPieChart" => {
            if g.child_val("ofPieType") == Some("bar") {
                ChartKind::BarOfPie
            } else {
                ChartKind::PieOfPie
            }
        }
        "doughnutChart" => ChartKind::Doughnut,
        "areaChart" | "area3DChart" => match (stacked, pct) {
            (true, _) => ChartKind::AreaStacked,
            (_, true) => ChartKind::AreaStacked100,
            _ => ChartKind::Area,
        },
        "scatterChart" => {
            let no_lines = g.kids("ser").all(|s| s.path(&["spPr", "ln"]).is_some_and(|l| l.child("noFill").is_some()));
            let no_markers =
                g.kids("ser").next().is_some() && g.kids("ser").all(|s| s.path(&["marker", "symbol"]).and_then(|m| m.attr("val")) == Some("none"));
            if g.child_val("scatterStyle") == Some("marker") || (no_lines && g.kids("ser").next().is_some()) {
                ChartKind::Scatter
            } else if no_markers {
                ChartKind::ScatterLinesNoMarkers
            } else {
                ChartKind::ScatterLines
            }
        }
        "bubbleChart" => ChartKind::Bubble,
        "radarChart" => {
            let no_markers =
                g.kids("ser").next().is_some() && g.kids("ser").all(|s| s.path(&["marker", "symbol"]).and_then(|m| m.attr("val")) == Some("none"));
            match g.child_val("radarStyle") {
                Some("filled") => ChartKind::RadarFilled,
                Some("marker") if !no_markers => ChartKind::RadarMarkers,
                _ => ChartKind::Radar,
            }
        }
        "stockChart" => ChartKind::Stock,
        "surface3DChart" => {
            if bool_val(g, "wireframe") {
                ChartKind::SurfaceWireframe
            } else {
                ChartKind::Surface
            }
        }
        "surfaceChart" => {
            if bool_val(g, "wireframe") {
                ChartKind::ContourWireframe
            } else {
                ChartKind::Contour
            }
        }
        _ => return None,
    })
}

/// How deep a chart group is drawn: its series in rows of their own when the group stands them
/// one behind another (`grouping` standard; a 3-D line always does).
fn group_depth(g: &El) -> Depth {
    let standard = g.child_val("grouping").is_none_or(|v| v == "standard");
    match g.name.as_str() {
        "bar3DChart" if standard && g.child_val("barDir") != Some("bar") => Depth::Rows,
        "line3DChart" => Depth::Rows,
        "area3DChart" if standard => Depth::Rows,
        "bar3DChart" | "area3DChart" | "pie3DChart" => Depth::Raised,
        "bubbleChart" if bool_val(g, "bubble3D") || g.kids("ser").any(|s| bool_val(s, "bubble3D")) => Depth::Raised,
        _ => Depth::Flat,
    }
}

fn data_ref(e: Option<&El>) -> Option<String> {
    let e = e?;
    for k in ["numRef", "strRef", "multiLvlStrRef"] {
        if let Some(f) = e.child(k).and_then(|r| r.child("f")) {
            let t = f.text.trim();
            return Some(t.strip_prefix('=').unwrap_or(t).to_string());
        }
    }
    // Literal values become an array constant.
    for k in ["numLit", "strLit"] {
        if let Some(l) = e.child(k) {
            let pts: Vec<String> = l
                .kids("pt")
                .filter_map(|p| p.child("v"))
                .map(|v| if k == "numLit" { v.text.trim().to_string() } else { format!("\"{}\"", v.text.replace('"', "\"\"")) })
                .collect();
            return Some(format!("{{{}}}", pts.join(",")));
        }
    }
    None
}

fn read_series(s: &El) -> Series {
    let name = s.child("tx").and_then(|tx| {
        if let Some(f) = tx.path(&["strRef", "f"]) {
            let t = f.text.trim();
            Some(t.strip_prefix('=').unwrap_or(t).to_string())
        } else {
            tx.child("v").map(|v| v.text.clone())
        }
    });
    let sp = s.child("spPr");
    let color = sp
        .and_then(|p| p.child("solidFill"))
        .and_then(dml_color)
        .or_else(|| sp.and_then(|p| p.path(&["ln", "solidFill"])).and_then(dml_color))
        .or_else(|| s.path(&["marker", "spPr", "solidFill"]).and_then(dml_color));
    Series {
        name,
        categories: data_ref(s.child("cat")).or_else(|| data_ref(s.child("xVal"))),
        values: data_ref(s.child("val")).or_else(|| data_ref(s.child("yVal"))).unwrap_or_default(),
        bubble_sizes: data_ref(s.child("bubbleSize")),
        color,
        secondary: false,
        kind: None,
        smooth: bool_val(s, "smooth"),
        trendlines: s.kids("trendline").filter_map(read_trendline).collect(),
    }
}

const DASHES: [(LineDash, &str); 11] = [
    (LineDash::Solid, "solid"),
    (LineDash::Dot, "dot"),
    (LineDash::Dash, "dash"),
    (LineDash::LgDash, "lgDash"),
    (LineDash::DashDot, "dashDot"),
    (LineDash::LgDashDot, "lgDashDot"),
    (LineDash::LgDashDotDot, "lgDashDotDot"),
    (LineDash::SysDash, "sysDash"),
    (LineDash::SysDot, "sysDot"),
    (LineDash::SysDashDot, "sysDashDot"),
    (LineDash::SysDashDotDot, "sysDashDotDot"),
];

const EMU_PER_PT: f64 = 12_700.0;

/// A `CT_Boolean` child (`val` defaults to true).
fn bool_val(e: &El, tag: &str) -> bool {
    e.child(tag).is_some_and(|c| c.flag("val", true))
}

fn f64_val(e: &El, tag: &str) -> Option<f64> {
    e.child(tag).and_then(|c| c.attr_f64("val"))
}

/// `<c:trendline>` (ECMA-376 Part 1, 21.2.2.211). Unknown types are dropped.
fn read_trendline(t: &El) -> Option<Trendline> {
    let kind = match t.child_val("trendlineType").unwrap_or("linear") {
        "linear" => TrendlineKind::Linear,
        "exp" => TrendlineKind::Exponential,
        "log" => TrendlineKind::Logarithmic,
        "power" => TrendlineKind::Power,
        "poly" => TrendlineKind::Polynomial(t.child_val("order").and_then(|v| v.trim().parse::<u8>().ok()).unwrap_or(2).clamp(2, 6)),
        "movingAvg" => TrendlineKind::MovingAverage(t.child_val("period").and_then(|v| v.trim().parse::<u32>().ok()).unwrap_or(2).clamp(2, 255)),
        _ => return None,
    };
    let ln = t.path(&["spPr", "ln"]);
    Some(Trendline {
        kind,
        name: t.child("name").map(|n| n.text.clone()),
        forward: f64_val(t, "forward").unwrap_or(0.0).max(0.0),
        backward: f64_val(t, "backward").unwrap_or(0.0).max(0.0),
        intercept: f64_val(t, "intercept"),
        display_equation: bool_val(t, "dispEq"),
        display_r_squared: bool_val(t, "dispRSqr"),
        color: ln.and_then(|l| l.child("solidFill")).and_then(dml_color),
        width: ln.and_then(|l| l.attr_f64("w")).map(|w| (w / EMU_PER_PT) as f32).filter(|w| w.is_finite() && *w >= 0.0),
        dash: ln.and_then(|l| l.child("prstDash")).and_then(|d| d.attr("val")).and_then(|v| DASHES.iter().find(|(_, n)| *n == v).map(|(d, _)| *d)),
    })
}

fn rich_title(t: &El) -> Option<String> {
    let tx = t.child("tx")?;
    if let Some(rich) = tx.child("rich") {
        let paras: Vec<String> =
            rich.kids("p").map(|p| p.kids("r").filter_map(|r| r.child("t")).map(|t| t.text.as_str()).collect::<String>()).collect();
        let s = paras.join("\n");
        return if s.is_empty() { None } else { Some(s) };
    }
    if let Some(sr) = tx.child("strRef") {
        if let Some(v) = sr.path(&["strCache", "pt", "v"]) {
            return Some(v.text.clone());
        }
        return sr.child("f").map(|f| f.text.trim().to_string());
    }
    None
}

fn axis_ids(g: &El) -> Vec<String> {
    g.kids("axId").filter_map(|a| a.attr("val")).map(str::to_string).collect()
}

pub fn read_chart(root: &El) -> Option<Chart> {
    let chart = root.child("chart")?;
    let plot = chart.child("plotArea")?;
    let groups: Vec<(&El, ChartKind)> = plot.children.iter().filter_map(|g| group_kind(g).map(|k| (g, k))).collect();
    let (first, first_kind) = *groups.first()?;
    let first_axes = axis_ids(first);
    let mut series = vec![];
    for (gi, (g, k)) in groups.iter().enumerate() {
        for s in g.kids("ser") {
            let mut ser = read_series(s);
            if gi > 0 {
                ser.kind = Some(*k);
                ser.secondary = axis_ids(g) != first_axes;
            }
            series.push(ser);
        }
    }
    let kind = if groups.len() > 1 { ChartKind::Combo } else { first_kind };
    let depth = if groups.len() > 1 { Depth::Flat } else { group_depth(first) };
    let show_val = |e: &El| e.child("dLbls").is_some_and(|d| bool_val(d, "showVal"));
    let data_labels = groups.iter().any(|(g, _)| show_val(g) || g.kids("ser").any(show_val));
    let val_axes: Vec<&El> = plot.kids("valAx").collect();
    let cat_ax = plot.child("catAx").or_else(|| plot.child("dateAx"));
    let scatter = matches!(first_kind, ChartKind::Scatter | ChartKind::ScatterLines | ChartKind::ScatterLinesNoMarkers | ChartKind::Bubble);
    let (x_ax, y_ax) = if scatter {
        let x = val_axes.iter().find(|a| matches!(a.child_val("axPos"), Some("b") | Some("t"))).copied();
        let y = val_axes.iter().find(|a| matches!(a.child_val("axPos"), Some("l") | Some("r"))).copied();
        (x, y)
    } else {
        (cat_ax, val_axes.first().copied())
    };
    let legend = match chart.child("legend") {
        None => LegendPos::None,
        Some(l) => match l.child_val("legendPos").unwrap_or("r") {
            "b" => LegendPos::Bottom,
            "t" => LegendPos::Top,
            "l" => LegendPos::Left,
            _ => LegendPos::Right,
        },
    };
    // the first label position, error bars and line marks found in the groups or their series
    let label_pos = groups
        .iter()
        .flat_map(|(g, _)| std::iter::once(*g).chain(g.kids("ser")))
        .find_map(|e| e.path(&["dLbls", "dLblPos"]).and_then(|p| p.attr("val")))
        .map(|v| match v {
            "ctr" => LabelPos::Center,
            "inEnd" => LabelPos::InsideEnd,
            "inBase" => LabelPos::InsideBase,
            "outEnd" => LabelPos::OutsideEnd,
            "bestFit" => LabelPos::BestFit,
            "l" => LabelPos::Left,
            "r" => LabelPos::Right,
            "t" => LabelPos::Above,
            "b" => LabelPos::Below,
            _ => LabelPos::Auto,
        })
        .unwrap_or_default();
    let error_bars = groups.iter().flat_map(|(g, _)| g.kids("ser")).find_map(|s| s.child("errBars")).and_then(|e| {
        let v = f64_val(e, "val").filter(|v| v.is_finite()).map(f64::abs);
        match e.child_val("errValType").unwrap_or("fixedVal") {
            "stdErr" => Some(ErrorAmount::StandardError),
            "percentage" => Some(ErrorAmount::Percent(v.unwrap_or(5.0))),
            "stdDev" => Some(ErrorAmount::StdDev(v.unwrap_or(1.0))),
            "fixedVal" => Some(ErrorAmount::Fixed(v.unwrap_or(1.0))),
            _ => None,
        }
    });
    let elements = ChartElements {
        label_pos,
        error_bars,
        title_overlay: chart.child("title").is_some_and(|t| bool_val(t, "overlay")),
        up_down_bars: groups.iter().any(|(g, _)| g.child("upDownBars").is_some()),
        drop_lines: groups.iter().any(|(g, _)| g.child("dropLines").is_some()),
        // a stock chart always has its high-low lines; only a line chart's are a choice
        high_low_lines: groups.iter().any(|(g, k)| *k != ChartKind::Stock && g.child("hiLowLines").is_some()),
        data_table: plot.child("dTable").is_some(),
        // the colour part beside the chart part says which (see the drawing reader)
        palette: 0,
    };
    Some(Chart {
        depth,
        alt: String::new(),
        elements,
        id: 0,
        kind,
        anchor: Default::default(),
        title: chart.child("title").and_then(rich_title),
        series,
        legend,
        data_labels,
        gridlines: val_axes.iter().any(|a| a.child("majorGridlines").is_some()),
        style: root.child_val("style").and_then(|v| v.parse().ok()).unwrap_or(2),
        x_title: x_ax.and_then(|a| a.child("title")).and_then(rich_title),
        y_title: y_ax.and_then(|a| a.child("title")).and_then(rich_title),
        source: None,
        by_rows: false,
        pivot: root.path(&["pivotSource", "name"]).map(|n| n.text.trim().to_string()).filter(|n| !n.is_empty()),
    })
}

// ---------------------------------------------------------------- writing

#[derive(Clone, Copy, PartialEq)]
enum G {
    Bar {
        bar: bool,
        grouping: &'static str,
    },
    Line {
        grouping: &'static str,
        markers: bool,
    },
    Pie,
    /// Pie of Pie (`bar` false) or Bar of Pie.
    OfPie {
        bar: bool,
    },
    Doughnut,
    Area {
        grouping: &'static str,
    },
    Scatter {
        lines: bool,
        markers: bool,
    },
    Bubble,
    Radar {
        filled: bool,
        markers: bool,
    },
    Stock,
    /// A 3-D column or bar group; `rows` stands each series in a row of its own.
    Bar3D {
        bar: bool,
        grouping: &'static str,
        rows: bool,
    },
    Line3D,
    Area3D {
        grouping: &'static str,
        rows: bool,
    },
    Pie3D,
    /// Surface (`flat` false) or contour charts.
    Surface {
        flat: bool,
        wireframe: bool,
    },
}

impl G {
    /// Groups drawn in 3-D (no trendlines, error bars or label positions there).
    fn deep(self) -> bool {
        matches!(self, G::Bar3D { .. } | G::Line3D | G::Area3D { .. } | G::Pie3D | G::Surface { .. })
    }

    /// Groups with a series axis (a third axis, going back).
    fn rows(self) -> bool {
        matches!(self, G::Bar3D { rows: true, .. } | G::Line3D | G::Area3D { rows: true, .. } | G::Surface { .. })
    }
}

fn group_for(k: ChartKind) -> G {
    match k {
        ChartKind::ColumnStacked => G::Bar { bar: false, grouping: "stacked" },
        ChartKind::ColumnStacked100 => G::Bar { bar: false, grouping: "percentStacked" },
        ChartKind::BarClustered => G::Bar { bar: true, grouping: "clustered" },
        ChartKind::BarStacked => G::Bar { bar: true, grouping: "stacked" },
        ChartKind::BarStacked100 => G::Bar { bar: true, grouping: "percentStacked" },
        ChartKind::Line => G::Line { grouping: "standard", markers: false },
        ChartKind::LineMarkers => G::Line { grouping: "standard", markers: true },
        ChartKind::LineStacked => G::Line { grouping: "stacked", markers: false },
        ChartKind::LineStackedMarkers => G::Line { grouping: "stacked", markers: true },
        ChartKind::LineStacked100 => G::Line { grouping: "percentStacked", markers: false },
        ChartKind::LineStacked100Markers => G::Line { grouping: "percentStacked", markers: true },
        ChartKind::Pie => G::Pie,
        ChartKind::PieOfPie => G::OfPie { bar: false },
        ChartKind::BarOfPie => G::OfPie { bar: true },
        ChartKind::Doughnut => G::Doughnut,
        ChartKind::Area => G::Area { grouping: "standard" },
        ChartKind::AreaStacked => G::Area { grouping: "stacked" },
        ChartKind::AreaStacked100 => G::Area { grouping: "percentStacked" },
        ChartKind::Scatter => G::Scatter { lines: false, markers: true },
        ChartKind::ScatterLines => G::Scatter { lines: true, markers: true },
        ChartKind::ScatterLinesNoMarkers => G::Scatter { lines: true, markers: false },
        ChartKind::Bubble => G::Bubble,
        ChartKind::Radar => G::Radar { filled: false, markers: false },
        ChartKind::RadarMarkers => G::Radar { filled: false, markers: true },
        ChartKind::RadarFilled => G::Radar { filled: true, markers: false },
        ChartKind::Stock => G::Stock,
        ChartKind::Surface => G::Surface { flat: false, wireframe: false },
        ChartKind::SurfaceWireframe => G::Surface { flat: false, wireframe: true },
        ChartKind::Contour => G::Surface { flat: true, wireframe: false },
        ChartKind::ContourWireframe => G::Surface { flat: true, wireframe: true },
        // Combo charts start from clustered columns; chartex kinds have their own part.
        _ => G::Bar { bar: false, grouping: "clustered" },
    }
}

/// A 3-D chart's group: the 2-D group with depth (`group_for` for flat charts and kinds that
/// have no 3-D form).
fn group_3d(g: G, depth: Depth) -> G {
    let rows = depth == Depth::Rows;
    match (g, depth) {
        (_, Depth::Flat) => g,
        (G::Bar { bar, grouping }, _) => {
            let rows = rows && !bar;
            G::Bar3D { bar, grouping: if rows { "standard" } else { grouping }, rows }
        }
        (G::Line { .. }, _) => G::Line3D,
        (G::Area { grouping }, _) => G::Area3D { grouping: if rows { "standard" } else { grouping }, rows: rows || grouping == "standard" },
        (G::Pie, _) => G::Pie3D,
        _ => g,
    }
}

const MAX_CACHE: u64 = 100_000;

/// Values of a same-workbook reference (`Sheet1!$B$2:$B$9`), row-major.
pub(crate) fn ref_values(wb: &Workbook, sheet_idx: usize, f: &str) -> Option<Vec<Value>> {
    let e = gridcraft_formula::parse(f).ok()?;
    let Expr::Ref(r) = e else { return None };
    let si = match &r.sheet {
        SheetSel::Current => sheet_idx,
        SheetSel::Named(n) => wb.sheet_index(n)?,
        SheetSel::Span(..) => return None,
    };
    let sheet = wb.sheet(si)?;
    let mut range = r.range();
    // Whole columns/rows: limit to the used range.
    if let Some(u) = sheet.used_range() {
        range = range.intersection(&u)?;
    }
    if range.count() > MAX_CACHE {
        return None;
    }
    Some(range.iter().map(|c| sheet.value(c)).collect())
}

fn is_ref(f: &str) -> bool {
    matches!(gridcraft_formula::parse(f), Ok(Expr::Ref(_)))
}

fn num_data(tag: &str, f: Option<&str>, wb: &Workbook, si: usize) -> String {
    let f = f.unwrap_or("").trim();
    if f.is_empty() {
        return format!("<c:{tag}><c:numLit><c:ptCount val=\"0\"/></c:numLit></c:{tag}>");
    }
    if f.starts_with('{') {
        let vals: Vec<&str> = f.trim_matches(|c| c == '{' || c == '}').split([',', ';']).collect();
        let mut s = format!("<c:{tag}><c:numLit><c:formatCode>General</c:formatCode><c:ptCount val=\"{}\"/>", vals.len());
        for (i, v) in vals.iter().enumerate() {
            if let Ok(n) = v.trim().parse::<f64>() {
                let _ = write!(s, "<c:pt idx=\"{i}\"><c:v>{}</c:v></c:pt>", num(n));
            }
        }
        let _ = write!(s, "</c:numLit></c:{tag}>");
        return s;
    }
    let mut s = format!("<c:{tag}><c:numRef><c:f>{}</c:f>", esc(&crate::fmla::text_to_file(f)));
    if let Some(vals) = ref_values(wb, si, f) {
        let _ = write!(s, "<c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\"{}\"/>", vals.len());
        for (i, v) in vals.iter().enumerate() {
            if let Value::Number(n) = v {
                let _ = write!(s, "<c:pt idx=\"{i}\"><c:v>{}</c:v></c:pt>", num(*n));
            }
        }
        s.push_str("</c:numCache>");
    }
    let _ = write!(s, "</c:numRef></c:{tag}>");
    s
}

/// Splits the inside of an array constant on `,` and `;` outside double quotes; inside quotes `""` is a literal quote.
/// Unquoted items are trimmed; quoted ones keep the text between the quotes and drop the spaces around them.
fn split_array_items(inner: &str) -> Vec<String> {
    let mut items = Vec::new();
    let mut cur = String::new();
    let mut quoted = false;
    let mut in_quote = false;
    let mut chars = inner.chars().peekable();
    let finish = |cur: &mut String, quoted: &mut bool| {
        let text = std::mem::take(cur);
        let item = if *quoted { text } else { text.trim().to_string() };
        *quoted = false;
        item
    };
    while let Some(c) = chars.next() {
        match c {
            '"' if in_quote && chars.peek() == Some(&'"') => {
                cur.push('"');
                chars.next();
            }
            '"' => {
                if !quoted && cur.trim().is_empty() {
                    cur.clear();
                }
                in_quote = !in_quote;
                quoted = true;
            }
            ',' | ';' if !in_quote => items.push(finish(&mut cur, &mut quoted)),
            c if quoted && !in_quote && c.is_whitespace() => {}
            _ => cur.push(c),
        }
    }
    items.push(finish(&mut cur, &mut quoted));
    items
}

fn str_data(tag: &str, f: &str, wb: &Workbook, si: usize) -> String {
    let f = f.trim();
    if f.starts_with('{') {
        let vals = split_array_items(f.trim_matches(|c| c == '{' || c == '}'));
        let mut s = format!("<c:{tag}><c:strLit><c:ptCount val=\"{}\"/>", vals.len());
        for (i, v) in vals.iter().enumerate() {
            let _ = write!(s, "<c:pt idx=\"{i}\"><c:v>{}</c:v></c:pt>", esc(v));
        }
        let _ = write!(s, "</c:strLit></c:{tag}>");
        return s;
    }
    let mut s = format!("<c:{tag}><c:strRef><c:f>{}</c:f>", esc(&crate::fmla::text_to_file(f)));
    if let Some(vals) = ref_values(wb, si, f) {
        let _ = write!(s, "<c:strCache><c:ptCount val=\"{}\"/>", vals.len());
        for (i, v) in vals.iter().enumerate() {
            if !v.is_empty() {
                let _ = write!(s, "<c:pt idx=\"{i}\"><c:v>{}</c:v></c:pt>", esc(&v.display()));
            }
        }
        s.push_str("</c:strCache>");
    }
    let _ = write!(s, "</c:strRef></c:{tag}>");
    s
}

/// The colour part for a palette picked on the Color page (`None` for the automatic one): its
/// colours, how it uses them, and the changes it makes once each has been used.
pub fn colors_xml(palette: u16) -> Option<String> {
    use gridcraft_model::chart_palette::{self, ColorMod};
    let (slot, _) = chart_palette::series_color(palette, 0, 1).filter(|_| palette != chart_palette::AUTOMATIC)?;
    let mut s = format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
<cs:colorStyle xmlns:cs=\"http://schemas.microsoft.com/office/drawing/2012/chartStyle\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" meth=\"{}\" id=\"{palette}\">",
        match palette {
            14..=19 => "withinLinear",
            21..=26 => "withinLinearReversed",
            _ => "cycle",
        }
    );
    match palette {
        11..=13 => {
            for i in 0..3 {
                let (slot, _) = chart_palette::series_color(palette, i, 3)?;
                s.push_str(&chart_palette::scheme_xml(slot, &[]));
            }
        }
        _ => s.push_str(&chart_palette::scheme_xml(slot, &[])),
    }
    // the cycle's variations: the colourful ones vary each colour in turn, the grey one its tints
    let variations: Vec<Vec<ColorMod>> = match palette {
        11..=13 => (0..9).filter_map(|k| chart_palette::series_color(palette, k * 3, 27).map(|c| c.1)).collect(),
        20 => (0..7).filter_map(|k| chart_palette::series_color(palette, k, 7).map(|c| c.1)).collect(),
        _ => vec![],
    };
    for v in variations {
        if v.is_empty() {
            s.push_str("<cs:variation/>");
        } else {
            let inner = chart_palette::scheme_xml(0, &v);
            let mods = inner.trim_start_matches("<a:schemeClr val=\"lt1\">").trim_end_matches("</a:schemeClr>");
            let _ = write!(s, "<cs:variation>{mods}</cs:variation>");
        }
    }
    s.push_str("</cs:colorStyle>");
    Some(s)
}

/// `auto`: the colour of the chart's palette for this series, written when it has none of its own.
fn series_xml(s: &Series, idx: usize, g: G, err: Option<ErrorAmount>, bubble3d: bool, auto: Option<String>, wb: &Workbook, si: usize) -> String {
    let mut x = format!("<c:ser><c:idx val=\"{idx}\"/><c:order val=\"{idx}\"/>");
    if let Some(n) = &s.name {
        if is_ref(n) {
            x.push_str(&str_data("tx", n, wb, si));
        } else {
            let lit = n.trim().strip_prefix('"').and_then(|t| t.strip_suffix('"')).unwrap_or(n);
            let _ = write!(x, "<c:tx><c:v>{}</c:v></c:tx>", esc(lit));
        }
    }
    let color = s.color.as_ref().and_then(|c: &Color| srgb(c, &wb.theme)).or(auto);
    let line_like = matches!(g, G::Line { .. } | G::Radar { filled: false, .. } | G::Scatter { lines: true, .. });
    match (&color, line_like) {
        (Some(c), true) => {
            let _ = write!(x, "<c:spPr><a:ln w=\"28575\" cap=\"rnd\"><a:solidFill>{c}</a:solidFill><a:round/></a:ln></c:spPr>");
        }
        (Some(c), false) if matches!(g, G::Scatter { lines: false, .. }) => {
            let _ = write!(
                x,
                "<c:spPr><a:ln w=\"19050\"><a:noFill/></a:ln></c:spPr><c:marker><c:symbol val=\"circle\"/><c:size val=\"5\"/><c:spPr><a:solidFill>{c}</a:solidFill></c:spPr></c:marker>"
            );
        }
        (Some(c), false) => {
            let _ = write!(x, "<c:spPr><a:solidFill>{c}</a:solidFill></c:spPr>");
        }
        (None, _) if matches!(g, G::Scatter { lines: false, .. }) || g == G::Stock => {
            x.push_str("<c:spPr><a:ln w=\"19050\"><a:noFill/></a:ln></c:spPr>")
        }
        _ => {}
    }
    match g {
        G::Bar { .. } | G::Bar3D { .. } => x.push_str("<c:invertIfNegative val=\"0\"/>"),
        G::Line { markers: false, .. } | G::Scatter { markers: false, .. } | G::Radar { filled: false, markers: false } | G::Stock => {
            x.push_str("<c:marker><c:symbol val=\"none\"/></c:marker>")
        }
        G::Bubble => x.push_str("<c:invertIfNegative val=\"0\"/>"),
        _ => {}
    }
    // Trendlines sit between dLbls and the data in CT_LineSer, CT_BarSer, CT_AreaSer,
    // CT_ScatterSer and CT_BubbleSer; pie and radar series have none.
    if !matches!(g, G::Pie | G::OfPie { .. } | G::Doughnut | G::Radar { .. }) && !g.deep() {
        for t in &s.trendlines {
            x.push_str(&trendline_xml(t, wb));
        }
    }
    // Error bars follow the trendlines; stock series have none.
    if let Some(e) = err.filter(|_| !matches!(g, G::Pie | G::OfPie { .. } | G::Doughnut | G::Radar { .. } | G::Stock) && !g.deep()) {
        x.push_str(&err_bars_xml(e, g));
    }
    match g {
        G::Scatter { .. } | G::Bubble => {
            if let Some(c) = &s.categories {
                x.push_str(&num_data("xVal", Some(c), wb, si));
            }
            x.push_str(&num_data("yVal", Some(&s.values), wb, si));
            if g == G::Bubble {
                x.push_str(&num_data("bubbleSize", s.bubble_sizes.as_deref().or(Some(&s.values)), wb, si));
                let _ = write!(x, "<c:bubble3D val=\"{}\"/>", bubble3d as u8);
            } else {
                let _ = write!(x, "<c:smooth val=\"{}\"/>", s.smooth as u8);
            }
        }
        _ => {
            if let Some(c) = &s.categories {
                x.push_str(&str_data("cat", c, wb, si));
            }
            x.push_str(&num_data("val", Some(&s.values), wb, si));
            if matches!(g, G::Line { .. }) {
                let _ = write!(x, "<c:smooth val=\"{}\"/>", s.smooth as u8);
            }
        }
    }
    x.push_str("</c:ser>");
    x
}

/// `<c:trendline>` with children in schema order: name, spPr, trendlineType, order, period,
/// forward, backward, intercept, dispRSqr, dispEq, trendlineLbl.
fn trendline_xml(t: &Trendline, wb: &Workbook) -> String {
    let mut x = String::from("<c:trendline>");
    if let Some(n) = &t.name {
        let _ = write!(x, "<c:name>{}</c:name>", esc(n));
    }
    let color = t.color.as_ref().and_then(|c| srgb(c, &wb.theme));
    if color.is_some() || t.width.is_some() || t.dash.is_some() {
        x.push_str("<c:spPr><a:ln");
        if let Some(w) = t.width.filter(|w| w.is_finite()) {
            // ST_LineWidth: 0..=20116800 EMU.
            let _ = write!(x, " w=\"{}\"", (f64::from(w) * EMU_PER_PT).round().clamp(0.0, 20_116_800.0) as i64);
        }
        x.push_str(" cap=\"rnd\">");
        if let Some(c) = &color {
            let _ = write!(x, "<a:solidFill>{c}</a:solidFill>");
        }
        if let Some(d) = t.dash.and_then(|d| DASHES.iter().find(|(k, _)| *k == d)) {
            let _ = write!(x, "<a:prstDash val=\"{}\"/>", d.1);
        }
        x.push_str("</a:ln></c:spPr>");
    }
    let ty = match t.kind {
        TrendlineKind::Linear => "linear",
        TrendlineKind::Exponential => "exp",
        TrendlineKind::Logarithmic => "log",
        TrendlineKind::Polynomial(_) => "poly",
        TrendlineKind::Power => "power",
        TrendlineKind::MovingAverage(_) => "movingAvg",
    };
    let _ = write!(x, "<c:trendlineType val=\"{ty}\"/>");
    match t.kind {
        TrendlineKind::Polynomial(o) => {
            let _ = write!(x, "<c:order val=\"{}\"/>", o.clamp(2, 6));
        }
        TrendlineKind::MovingAverage(p) => {
            let _ = write!(x, "<c:period val=\"{}\"/>", p.clamp(2, 255));
        }
        _ => {}
    }
    for (tag, v) in [("forward", t.forward), ("backward", t.backward)] {
        if v.is_finite() && v > 0.0 {
            let _ = write!(x, "<c:{tag} val=\"{}\"/>", num(v));
        }
    }
    if let Some(i) = t.intercept.filter(|i| i.is_finite()) {
        let _ = write!(x, "<c:intercept val=\"{}\"/>", num(i));
    }
    let _ = write!(x, "<c:dispRSqr val=\"{}\"/><c:dispEq val=\"{}\"/>", t.display_r_squared as u8, t.display_equation as u8);
    if t.display_r_squared || t.display_equation {
        x.push_str("<c:trendlineLbl><c:numFmt formatCode=\"General\" sourceLinked=\"0\"/></c:trendlineLbl>");
    }
    x.push_str("</c:trendline>");
    x
}

fn dlbls(show: bool, pos: Option<&str>) -> String {
    let pos = pos.filter(|_| show).map(|p| format!("<c:dLblPos val=\"{p}\"/>")).unwrap_or_default();
    format!(
        "<c:dLbls>{pos}<c:showLegendKey val=\"0\"/><c:showVal val=\"{}\"/><c:showCatName val=\"0\"/><c:showSerName val=\"0\"/><c:showPercent val=\"0\"/><c:showBubbleSize val=\"0\"/></c:dLbls>",
        show as u8
    )
}

/// The label position as `c:dLblPos` has it, where the chart type allows that position (an area
/// or radar chart allows none; a stacked column no outside end); `None` leaves it automatic.
fn label_pos_xml(pos: LabelPos, g: G) -> Option<&'static str> {
    use LabelPos as P;
    match (g, pos) {
        (_, P::Auto) => None,
        (G::Bar { .. }, P::Center) | (G::Pie | G::OfPie { .. } | G::Doughnut | G::Line { .. } | G::Scatter { .. } | G::Bubble, P::Center) => {
            Some("ctr")
        }
        (G::Bar { .. } | G::Pie | G::OfPie { .. }, P::InsideEnd) => Some("inEnd"),
        (G::Bar { .. }, P::InsideBase) => Some("inBase"),
        (G::Bar { grouping: "clustered", .. } | G::Pie | G::OfPie { .. }, P::OutsideEnd) => Some("outEnd"),
        (G::Pie | G::OfPie { .. }, P::BestFit) => Some("bestFit"),
        (G::Line { .. } | G::Scatter { .. } | G::Bubble, P::Left) => Some("l"),
        (G::Line { .. } | G::Scatter { .. } | G::Bubble, P::Right) => Some("r"),
        (G::Line { .. } | G::Scatter { .. } | G::Bubble, P::Above) => Some("t"),
        (G::Line { .. } | G::Scatter { .. } | G::Bubble, P::Below) => Some("b"),
        _ => None,
    }
}

/// `<c:errBars>`: plus and minus, the amount as Excel stores it.
fn err_bars_xml(e: ErrorAmount, g: G) -> String {
    let dir = if matches!(g, G::Scatter { .. } | G::Bubble) { "<c:errDir val=\"y\"/>" } else { "" };
    let (ty, val) = match e {
        ErrorAmount::StandardError => ("stdErr", None),
        ErrorAmount::Percent(p) => ("percentage", Some(p)),
        ErrorAmount::StdDev(n) => ("stdDev", Some(n)),
        ErrorAmount::Fixed(v) => ("fixedVal", Some(v)),
    };
    let val = val.filter(|v| v.is_finite()).map(|v| format!("<c:val val=\"{}\"/>", num(v.abs()))).unwrap_or_default();
    format!("<c:errBars>{dir}<c:errBarType val=\"both\"/><c:errValType val=\"{ty}\"/><c:noEndCap val=\"0\"/>{val}</c:errBars>")
}

fn group_xml(g: G, series: &[(usize, &Series)], ch: &Chart, axes: (u32, u32), wb: &Workbook, si: usize) -> String {
    let el = &ch.elements;
    let bubble3d = ch.depth != Depth::Flat;
    // a palette picked on the Color page: each series' colour as the theme colour it is made from,
    // as the reference writes it (charts coloured by point keep theirs)
    let count = ch.series.len();
    let auto = |i: usize| {
        (el.palette != 0 && !matches!(g, G::Pie | G::OfPie { .. } | G::Doughnut))
            .then(|| gridcraft_model::chart_palette::series_color(el.palette, i, count))
            .flatten()
            .map(|(slot, mods)| gridcraft_model::chart_palette::scheme_xml(slot, &mods))
    };
    let sers: String = series.iter().map(|(i, s)| series_xml(s, *i, g, el.error_bars, bubble3d, auto(*i), wb, si)).collect();
    let dl = dlbls(ch.data_labels, label_pos_xml(el.label_pos, g));
    // drop lines, high-low lines and up/down bars, in the order CT_LineChart has them
    let drop = if el.drop_lines { "<c:dropLines/>" } else { "" };
    let lines = format!(
        "{drop}{}{}",
        if el.high_low_lines { "<c:hiLowLines/>" } else { "" },
        if el.up_down_bars { "<c:upDownBars><c:gapWidth val=\"150\"/><c:upBars/><c:downBars/></c:upDownBars>" } else { "" }
    );
    let mut ax = format!("<c:axId val=\"{}\"/><c:axId val=\"{}\"/>", axes.0, axes.1);
    if g.rows() {
        let _ = write!(ax, "<c:axId val=\"{}\"/>", SER_AX);
    }
    match g {
        G::Bar { bar, grouping } => {
            let overlap = if grouping == "clustered" { String::new() } else { "<c:overlap val=\"100\"/>".into() };
            format!(
                "<c:barChart><c:barDir val=\"{}\"/><c:grouping val=\"{grouping}\"/><c:varyColors val=\"0\"/>{sers}{dl}<c:gapWidth val=\"150\"/>{overlap}{ax}</c:barChart>",
                if bar { "bar" } else { "col" }
            )
        }
        G::Line { grouping, .. } => {
            format!("<c:lineChart><c:grouping val=\"{grouping}\"/><c:varyColors val=\"0\"/>{sers}{dl}{lines}<c:marker val=\"1\"/>{ax}</c:lineChart>")
        }
        G::Pie => format!("<c:pieChart><c:varyColors val=\"1\"/>{sers}{dl}<c:firstSliceAng val=\"0\"/></c:pieChart>"),
        G::OfPie { bar } => format!(
            "<c:ofPieChart><c:ofPieType val=\"{}\"/><c:varyColors val=\"1\"/>{sers}{dl}<c:gapWidth val=\"100\"/><c:secondPieSize val=\"75\"/><c:serLines/></c:ofPieChart>",
            if bar { "bar" } else { "pie" }
        ),
        G::Doughnut => {
            format!("<c:doughnutChart><c:varyColors val=\"1\"/>{sers}{dl}<c:firstSliceAng val=\"0\"/><c:holeSize val=\"50\"/></c:doughnutChart>")
        }
        G::Area { grouping } => format!("<c:areaChart><c:grouping val=\"{grouping}\"/><c:varyColors val=\"0\"/>{sers}{dl}{drop}{ax}</c:areaChart>"),
        G::Scatter { .. } => format!("<c:scatterChart><c:scatterStyle val=\"lineMarker\"/><c:varyColors val=\"0\"/>{sers}{dl}{ax}</c:scatterChart>"),
        G::Bubble => {
            format!("<c:bubbleChart><c:varyColors val=\"0\"/>{sers}{dl}<c:bubbleScale val=\"100\"/><c:showNegBubbles val=\"0\"/>{ax}</c:bubbleChart>")
        }
        G::Radar { filled, .. } => format!(
            "<c:radarChart><c:radarStyle val=\"{}\"/><c:varyColors val=\"0\"/>{sers}{dl}{ax}</c:radarChart>",
            if filled { "filled" } else { "marker" }
        ),
        G::Stock => format!(
            "<c:stockChart>{sers}{dl}{drop}<c:hiLowLines/>{}{ax}</c:stockChart>",
            if el.up_down_bars { "<c:upDownBars><c:gapWidth val=\"150\"/><c:upBars/><c:downBars/></c:upDownBars>" } else { "" }
        ),
        G::Bar3D { bar, grouping, .. } => format!(
            "<c:bar3DChart><c:barDir val=\"{}\"/><c:grouping val=\"{grouping}\"/><c:varyColors val=\"0\"/>{sers}{dl}<c:gapWidth val=\"150\"/><c:shape val=\"box\"/>{ax}</c:bar3DChart>",
            if bar { "bar" } else { "col" }
        ),
        G::Line3D => format!("<c:line3DChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>{sers}{dl}{drop}{ax}</c:line3DChart>"),
        G::Area3D { grouping, .. } => {
            format!("<c:area3DChart><c:grouping val=\"{grouping}\"/><c:varyColors val=\"0\"/>{sers}{dl}{drop}{ax}</c:area3DChart>")
        }
        G::Pie3D => format!("<c:pie3DChart><c:varyColors val=\"1\"/>{sers}{dl}</c:pie3DChart>"),
        G::Surface { flat, wireframe } => {
            let tag = if flat { "surfaceChart" } else { "surface3DChart" };
            format!("<c:{tag}><c:wireframe val=\"{}\"/>{sers}<c:bandFmts/>{ax}</c:{tag}>", wireframe as u8)
        }
    }
}

/// The series axis of charts whose series stand one behind another.
const SER_AX: u32 = 5;

fn ser_axis_xml(delete: bool) -> String {
    format!(
        "<c:serAx><c:axId val=\"{SER_AX}\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"{}\"/><c:axPos val=\"b\"/><c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/><c:crossAx val=\"2\"/><c:crosses val=\"autoZero\"/></c:serAx>",
        delete as u8
    )
}

/// `c:view3D` and the walls for a 3-D chart's group, where `c:chart` has them (before the plot).
fn view3d_xml(g: G) -> &'static str {
    match g {
        G::Pie3D => "<c:view3D><c:rotX val=\"30\"/><c:rotY val=\"0\"/><c:depthPercent val=\"100\"/><c:rAngAx val=\"0\"/></c:view3D>",
        G::Surface { flat: true, .. } => {
            "<c:view3D><c:rotX val=\"90\"/><c:rotY val=\"0\"/><c:depthPercent val=\"100\"/><c:rAngAx val=\"0\"/><c:perspective val=\"0\"/></c:view3D>"
        }
        G::Surface { flat: false, .. } => {
            "<c:view3D><c:rotX val=\"15\"/><c:rotY val=\"20\"/><c:depthPercent val=\"100\"/><c:rAngAx val=\"0\"/><c:perspective val=\"30\"/></c:view3D><c:floor><c:thickness val=\"0\"/></c:floor><c:sideWall><c:thickness val=\"0\"/></c:sideWall><c:backWall><c:thickness val=\"0\"/></c:backWall>"
        }
        G::Bar3D { .. } | G::Line3D | G::Area3D { .. } => {
            "<c:view3D><c:rotX val=\"15\"/><c:rotY val=\"20\"/><c:depthPercent val=\"100\"/><c:rAngAx val=\"1\"/></c:view3D><c:floor><c:thickness val=\"0\"/></c:floor><c:sideWall><c:thickness val=\"0\"/></c:sideWall><c:backWall><c:thickness val=\"0\"/></c:backWall>"
        }
        _ => "",
    }
}

fn title_xml(t: &str, overlay: bool) -> String {
    let mut s = String::from("<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/>");
    for line in t.split('\n') {
        let _ = write!(s, "<a:p><a:pPr><a:defRPr/></a:pPr><a:r><a:t>{}</a:t></a:r></a:p>", esc(line));
    }
    let _ = write!(s, "</c:rich></c:tx><c:overlay val=\"{}\"/></c:title>", overlay as u8);
    s
}

struct Ax<'a> {
    cat: bool,
    id: u32,
    cross: u32,
    pos: &'a str,
    delete: bool,
    grid: bool,
    title: Option<&'a str>,
    crosses_max: bool,
}

fn axis_xml(a: &Ax<'_>) -> String {
    let mut s = format!(
        "<c:{0}><c:axId val=\"{1}\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"{2}\"/><c:axPos val=\"{3}\"/>",
        if a.cat { "catAx" } else { "valAx" },
        a.id,
        a.delete as u8,
        a.pos
    );
    if a.grid {
        s.push_str("<c:majorGridlines/>");
    }
    if let Some(t) = a.title {
        s.push_str(&title_xml(t, false));
    }
    let _ = write!(
        s,
        "<c:numFmt formatCode=\"General\" sourceLinked=\"1\"/><c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/><c:crossAx val=\"{}\"/><c:crosses val=\"{}\"/>",
        a.cross,
        if a.crosses_max { "max" } else { "autoZero" }
    );
    if a.cat {
        s.push_str("<c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>");
    } else {
        s.push_str("<c:crossBetween val=\"between\"/></c:valAx>");
    }
    s
}

/// Chart part XML for a model chart on sheet `si`.
pub fn write_chart(wb: &Workbook, si: usize, ch: &Chart) -> String {
    let mut s = String::from(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><c:roundedCorners val=\"0\"/>",
    );
    if (1..=48).contains(&ch.style) {
        let _ = write!(s, "<c:style val=\"{}\"/>", ch.style);
    }
    // A PivotChart names its PivotTable (`Sheet2!PivotTable1`).
    if let Some(link) = ch.pivot.as_deref().map(str::trim).filter(|l| !l.is_empty()) {
        let _ = write!(s, "<c:pivotSource><c:name>{}</c:name><c:fmtId val=\"0\"/></c:pivotSource>", esc(link));
    }
    s.push_str("<c:chart>");
    match &ch.title {
        Some(t) if !t.is_empty() => {
            s.push_str(&title_xml(t, ch.elements.title_overlay));
            s.push_str("<c:autoTitleDeleted val=\"0\"/>");
        }
        _ => s.push_str("<c:autoTitleDeleted val=\"1\"/>"),
    }
    let solo = match group_for(ch.kind) {
        G::Stock if !(3..=4).contains(&ch.series.len()) => G::Line { grouping: "standard", markers: false },
        g => group_3d(g, ch.depth),
    };
    if ch.kind != ChartKind::Combo {
        s.push_str(view3d_xml(solo));
    }
    s.push_str("<c:plotArea><c:layout/>");
    let all: Vec<(usize, &Series)> = ch.series.iter().enumerate().collect();
    let primary_g;
    let mut secondary: Option<G> = None;
    if ch.kind == ChartKind::Combo {
        // Series without a kind of their own: the first is a column, the others lines (as drawn).
        let kind = |i: usize, x: &Series| x.kind.filter(|k| *k != ChartKind::Combo).or((i > 0).then_some(ChartKind::Line));
        let is_line = |k: Option<ChartKind>| matches!(k.map(group_for), Some(G::Line { .. } | G::Area { .. } | G::Radar { .. } | G::Scatter { .. }));
        let bars: Vec<(usize, &Series)> = all.iter().filter(|(i, x)| !x.secondary && !is_line(kind(*i, x))).copied().collect();
        let lines: Vec<(usize, &Series)> = all.iter().filter(|(i, x)| !x.secondary && is_line(kind(*i, x))).copied().collect();
        let sec: Vec<(usize, &Series)> = all.iter().filter(|(_, x)| x.secondary).copied().collect();
        primary_g = G::Bar { bar: false, grouping: "clustered" };
        if !bars.is_empty() || (lines.is_empty() && sec.is_empty()) {
            s.push_str(&group_xml(primary_g, &bars, ch, (1, 2), wb, si));
        }
        if !lines.is_empty() {
            s.push_str(&group_xml(G::Line { grouping: "standard", markers: false }, &lines, ch, (1, 2), wb, si));
        }
        if !sec.is_empty() {
            let g = match sec.first().and_then(|(_, x)| x.kind).map(group_for) {
                Some(b @ G::Bar { .. }) => b,
                _ => G::Line { grouping: "standard", markers: false },
            };
            s.push_str(&group_xml(g, &sec, ch, (3, 4), wb, si));
            secondary = Some(g);
        }
    } else {
        // A stock chart has 3 or 4 series (high-low-close, open-high-low-close).
        primary_g = solo;
        s.push_str(&group_xml(primary_g, &all, ch, (1, 2), wb, si));
    }
    match primary_g {
        G::Pie | G::OfPie { .. } | G::Doughnut | G::Pie3D => {}
        G::Scatter { .. } | G::Bubble => {
            s.push_str(&axis_xml(&Ax {
                cat: false,
                id: 1,
                cross: 2,
                pos: "b",
                delete: false,
                grid: false,
                title: ch.x_title.as_deref(),
                crosses_max: false,
            }));
            s.push_str(&axis_xml(&Ax {
                cat: false,
                id: 2,
                cross: 1,
                pos: "l",
                delete: false,
                grid: ch.gridlines,
                title: ch.y_title.as_deref(),
                crosses_max: false,
            }));
        }
        G::Bar { bar: true, .. } | G::Bar3D { bar: true, .. } => {
            s.push_str(&axis_xml(&Ax {
                cat: true,
                id: 1,
                cross: 2,
                pos: "l",
                delete: false,
                grid: false,
                title: ch.x_title.as_deref(),
                crosses_max: false,
            }));
            s.push_str(&axis_xml(&Ax {
                cat: false,
                id: 2,
                cross: 1,
                pos: "b",
                delete: false,
                grid: ch.gridlines,
                title: ch.y_title.as_deref(),
                crosses_max: false,
            }));
        }
        _ => {
            s.push_str(&axis_xml(&Ax {
                cat: true,
                id: 1,
                cross: 2,
                pos: "b",
                delete: false,
                grid: false,
                title: ch.x_title.as_deref(),
                crosses_max: false,
            }));
            s.push_str(&axis_xml(&Ax {
                cat: false,
                id: 2,
                cross: 1,
                pos: "l",
                delete: false,
                grid: ch.gridlines,
                title: ch.y_title.as_deref(),
                crosses_max: false,
            }));
        }
    }
    if primary_g.rows() {
        s.push_str(&ser_axis_xml(false));
    }
    if secondary.is_some() {
        s.push_str(&axis_xml(&Ax { cat: true, id: 3, cross: 4, pos: "b", delete: true, grid: false, title: None, crosses_max: false }));
        s.push_str(&axis_xml(&Ax { cat: false, id: 4, cross: 3, pos: "r", delete: false, grid: false, title: None, crosses_max: true }));
    }
    // a data table sits after the axes; a pie has none
    if ch.elements.data_table && !matches!(primary_g, G::Pie | G::OfPie { .. } | G::Doughnut) && !primary_g.deep() {
        s.push_str(
            "<c:dTable><c:showHorzBorder val=\"1\"/><c:showVertBorder val=\"1\"/><c:showOutline val=\"1\"/><c:showKeys val=\"1\"/></c:dTable>",
        );
    }
    s.push_str("</c:plotArea>");
    let pos = match ch.legend {
        LegendPos::None => None,
        LegendPos::Bottom => Some("b"),
        LegendPos::Top => Some("t"),
        LegendPos::Left => Some("l"),
        LegendPos::Right => Some("r"),
    };
    if let Some(p) = pos {
        let _ = write!(s, "<c:legend><c:legendPos val=\"{p}\"/><c:overlay val=\"0\"/></c:legend>");
    }
    s.push_str("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart></c:chartSpace>");
    s
}
