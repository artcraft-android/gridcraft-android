//! Office 2016 chart types (histogram, box & whisker, waterfall, funnel, treemap, sunburst),
//! which files keep as `cx:chartSpace` "chartex" parts (`xl/charts/chartExN.xml`) rather than
//! DrawingML chart parts.

use std::fmt::Write as _;

use gridcraft_core::RangeRef;
use gridcraft_formula::{Expr, SheetSel};
use gridcraft_model::{Chart, ChartKind, LabelPos, LegendPos, Series, Workbook};

use crate::drawing::{dml_color, srgb};
use crate::xml::{El, esc, esc_attr};

pub const CT_CHARTEX: &str = "application/vnd.ms-office.chartex+xml";
pub const REL_CHARTEX: &str = "http://schemas.microsoft.com/office/2014/relationships/chartEx";
pub const CT_STYLE: &str = "application/vnd.ms-office.chartstyle+xml";
pub const CT_COLORS: &str = "application/vnd.ms-office.chartcolorstyle+xml";
pub const REL_STYLE: &str = "http://schemas.microsoft.com/office/2011/relationships/chartStyle";
pub const REL_COLORS: &str = "http://schemas.microsoft.com/office/2011/relationships/chartColorStyle";

/// Kinds written as chartex parts.
pub fn is_chartex(k: ChartKind) -> bool {
    matches!(
        k,
        ChartKind::Histogram
            | ChartKind::Pareto
            | ChartKind::BoxWhisker
            | ChartKind::Waterfall
            | ChartKind::Funnel
            | ChartKind::Treemap
            | ChartKind::Sunburst
    )
}

/// The `mc:Choice` requirement for a kind's drawing frame: (prefix, namespace). Funnel charts
/// came in a later revision of the format.
pub fn requires(k: ChartKind) -> (&'static str, &'static str) {
    match k {
        ChartKind::Funnel => ("cx2", "http://schemas.microsoft.com/office/drawing/2015/10/21/chartex"),
        _ => ("cx1", "http://schemas.microsoft.com/office/drawing/2015/9/8/chartex"),
    }
}

// ---------------------------------------------------------------- reading

fn kind_of(layout: &str) -> Option<ChartKind> {
    Some(match layout {
        "clusteredColumn" | "paretoLine" => ChartKind::Histogram,
        "boxWhisker" => ChartKind::BoxWhisker,
        "waterfall" => ChartKind::Waterfall,
        "funnel" => ChartKind::Funnel,
        "treemap" => ChartKind::Treemap,
        "sunburst" => ChartKind::Sunburst,
        _ => return None,
    })
}

fn formula(dim: &El) -> Option<String> {
    let f = dim.child("f")?;
    let t = f.text.trim();
    Some(crate::fmla::from_file(t.strip_prefix('=').unwrap_or(t)))
}

fn text(tx: &El) -> Option<String> {
    if let Some(d) = tx.child("txData") {
        if let Some(f) = d.child("f") {
            let t = f.text.trim();
            return Some(t.strip_prefix('=').unwrap_or(t).to_string());
        }
        return d.child("v").map(|v| v.text.clone());
    }
    let rich = tx.child("rich")?;
    let paras: Vec<String> = rich.kids("p").map(|p| p.kids("r").filter_map(|r| r.child("t")).map(|t| t.text.as_str()).collect()).collect();
    let s = paras.join("\n");
    if s.is_empty() { None } else { Some(s) }
}

pub fn read_chartex(root: &El) -> Option<Chart> {
    let chart = root.child("chart")?;
    let plot = chart.child("plotArea")?;
    let region = plot.child("plotAreaRegion")?;
    // Data by id: (categories, values).
    let data: Vec<(String, Option<String>, Option<String>)> = root
        .child("chartData")
        .map(|cd| {
            cd.kids("data")
                .map(|d| {
                    let id = d.attr("id").unwrap_or("").to_string();
                    let cat = d.kids("strDim").chain(d.kids("numDim")).find(|x| matches!(x.attr("type"), Some("cat" | "x"))).and_then(formula);
                    let val = d.kids("numDim").find(|x| matches!(x.attr("type"), Some("val" | "size" | "y"))).and_then(formula);
                    (id, cat, val)
                })
                .collect()
        })
        .unwrap_or_default();
    // a histogram with a Pareto line is a Pareto chart
    let pareto = region.kids("series").any(|s| s.attr("layoutId") == Some("paretoLine"));
    let mut kind = pareto.then_some(ChartKind::Pareto);
    let mut series = vec![];
    let mut data_labels = false;
    let mut label_pos = LabelPos::Auto;
    let mut no_connectors = false;
    let mut banner = false;
    let mut label_text = gridcraft_model::LabelText::default();
    let (mut mean_line, mut no_mean_marker) = (false, false);
    for s in region.kids("series") {
        let layout = s.attr("layoutId").unwrap_or("");
        // A Pareto line belongs to its histogram.
        if layout == "paretoLine" {
            continue;
        }
        let k = kind_of(layout)?;
        kind.get_or_insert(k);
        let id = s.child_val("dataId").unwrap_or("");
        let (cat, val) = data.iter().find(|(i, _, _)| i == id).map(|(_, c, v)| (c.clone(), v.clone())).unwrap_or_default();
        // labels that say anything: the value, the category's or the series' name
        data_labels |= s
            .path(&["dataLabels", "visibility"])
            .is_some_and(|v| v.flag("value", false) || v.flag("categoryName", false) || v.flag("seriesName", false));
        no_connectors |= s.path(&["layoutPr", "visibility"]).is_some_and(|v| v.attr("connectorLines") == Some("0"));
        banner |= s.path(&["layoutPr", "parentLabelLayout"]).is_some_and(|p| p.attr("val") == Some("banner"));
        if let Some(v) = s.path(&["dataLabels", "visibility"]) {
            label_text = gridcraft_model::LabelText::of(v.flag("value", false), v.flag("categoryName", false), v.flag("seriesName", false), false);
        }
        if let Some(v) = s.path(&["layoutPr", "visibility"]).filter(|_| layout == "boxWhisker") {
            mean_line = v.attr("meanLine") == Some("1");
            no_mean_marker = v.attr("meanMarker") == Some("0");
        }
        if let Some(pos) = s.child("dataLabels").and_then(|d| d.attr("pos")) {
            label_pos = match pos {
                "inEnd" => LabelPos::InsideEnd,
                "outEnd" => LabelPos::OutsideEnd,
                "inBase" => LabelPos::InsideBase,
                "ctr" => LabelPos::Center,
                "r" => LabelPos::Right,
                "l" => LabelPos::Left,
                "t" => LabelPos::Above,
                "b" => LabelPos::Below,
                _ => label_pos,
            };
        }
        series.push(Series {
            name: s.child("tx").and_then(text),
            categories: cat,
            values: val.unwrap_or_default(),
            // The default colour (the series' theme accent, which the writer spells out) isn't kept.
            totals: s
                .path(&["layoutPr", "subtotals"])
                .map(|t| t.kids("idx").filter_map(|i| i.attr("val")?.parse().ok()).take(10_000).collect())
                .unwrap_or_default(),
            color: s
                .path(&["spPr", "solidFill"])
                .and_then(dml_color)
                .filter(|c| *c != gridcraft_model::Color::Theme(4 + (series.len() % 6) as u8, 0)),
            ..Default::default()
        });
    }
    let legend = match chart.child("legend") {
        None => LegendPos::None,
        Some(l) => match l.attr("pos").unwrap_or("r") {
            "b" => LegendPos::Bottom,
            "t" => LegendPos::Top,
            "l" => LegendPos::Left,
            _ => LegendPos::Right,
        },
    };
    // an axis title without text of its own shows the placeholder
    let axis_title = |id: &str| {
        plot.kids("axis")
            .find(|a| a.attr("id") == Some(id))
            .and_then(|a| a.child("title"))
            .map(|t| t.child("tx").and_then(text).unwrap_or_else(|| "Axis Title".into()))
    };
    let axis = |id: &str| plot.kids("axis").find(|a| a.attr("id") == Some(id));
    let elements = gridcraft_model::ChartElements {
        label_pos,
        hide_val_axis: axis("1").is_some_and(|a| a.attr("hidden") == Some("1")),
        hide_cat_axis: axis("0").is_some_and(|a| a.attr("hidden") == Some("1")),
        cat_gridlines: axis("0").is_some_and(|a| a.child("majorGridlines").is_some()),
        minor_val_gridlines: axis("1").is_some_and(|a| a.child("minorGridlines").is_some()),
        val_ticks: axis("1").and_then(|a| a.child("majorTickMarks")).is_some_and(|t| t.attr("type") != Some("none")),
        box_mean_line: mean_line,
        parent_banner: banner,
        label_text,
        box_no_mean_marker: no_mean_marker,
        no_connectors,
        // a waterfall's, box chart's or funnel's gap, kept when not its usual one
        gap_width: axis("0")
            .and_then(|a| a.child("catScaling"))
            .and_then(|c| c.attr("gapWidth"))
            .and_then(|g| g.parse::<f64>().ok())
            .filter(|g| g.is_finite())
            .map(|g| (g * 100.0).round().clamp(0.0, 500.0) as u16)
            .filter(|g| match kind {
                Some(ChartKind::Waterfall) => *g != 50,
                Some(ChartKind::BoxWhisker) => *g != 150,
                Some(ChartKind::Funnel) => *g != 6,
                _ => false,
            }),
        // the category axis's tick marks: none unless the part has them
        no_cat_ticks: !axis("0").and_then(|a| a.child("majorTickMarks")).is_some_and(|t| t.attr("type") != Some("none")),
        ..Default::default()
    };
    Some(Chart {
        depth: Default::default(),
        elements,
        alt: String::new(),
        id: 0,
        kind: kind?,
        anchor: Default::default(),
        // no title element: no title (a chartex part has no automatic one to fall back on)
        title: match chart.child("title") {
            None => Some(String::new()),
            Some(t) => t.child("tx").and_then(text),
        },
        series,
        legend,
        data_labels,
        gridlines: axis("1").is_some_and(|a| a.child("majorGridlines").is_some()),
        style: 2,
        x_title: axis_title("0"),
        y_title: axis_title("1"),
        source: None,
        by_rows: false,
        pivot: None,
    })
}

// ---------------------------------------------------------------- writing

/// Prefix of the hidden defined names chartex formulas go through: a chartex part refers to
/// its data by names (`_xlchart.v1.0`), which the workbook defines, not by references.
pub const NAME_PREFIX: &str = "_xlchart.v1.";

/// `<cx:f>` for a reference: a new hidden workbook name for it (collected in `names`, written
/// with the workbook's defined names). A reference along a row says so; otherwise each column
/// is one level.
fn f_xml(wb: &Workbook, si: usize, f: &str, names: &mut Vec<String>) -> String {
    let row = ref_range(wb, si, f).is_some_and(|(_, r)| r.height() == 1 && r.width() > 1);
    names.push(f.to_string());
    format!("<cx:f{}>{NAME_PREFIX}{}</cx:f>", if row { " dir=\"row\"" } else { "" }, names.len() - 1)
}

/// A reference's sheet and range (for the row/column direction).
fn ref_range(wb: &Workbook, si: usize, f: &str) -> Option<(usize, RangeRef)> {
    let Ok(Expr::Ref(r)) = gridcraft_formula::parse(f) else { return None };
    let sheet = match &r.sheet {
        SheetSel::Current => si,
        SheetSel::Named(n) => wb.sheet_index(n)?,
        SheetSel::Span(..) => return None,
    };
    Some((sheet, r.range()))
}

fn tx_xml(t: &str, wb: &Workbook, si: usize, names: &mut Vec<String>) -> String {
    if ref_range(wb, si, t).is_some() {
        let v = crate::chart::ref_values(wb, si, t).and_then(|v| v.into_iter().next()).map(|v| v.display()).unwrap_or_default();
        format!("<cx:tx><cx:txData>{}<cx:v>{}</cx:v></cx:txData></cx:tx>", f_xml(wb, si, t, names), esc(&v))
    } else {
        let lit = t.trim().strip_prefix('"').and_then(|x| x.strip_suffix('"')).unwrap_or(t);
        format!("<cx:tx><cx:txData><cx:v>{}</cx:v></cx:txData></cx:tx>", esc(lit))
    }
}

/// The chart style part a chart comes with, naming its style `id` (201 for a chartex part):
/// every element at its plain default, the chart part itself saying how each looks, but for a
/// chartex part's title, which takes `look`'s from here, and the points of a chart coloured by
/// point (`by_point`: a waterfall's), which take the palette's colours in `look`'s fill.
pub fn style_xml(id: u16, look: Option<&gridcraft_model::chart_style::ChartStyle>, by_point: bool, chartex: bool) -> String {
    use crate::chart_look::{axis_text, style_text};
    // a chartex part's text: the style's, or the plain look's
    let text = if chartex { look.or_else(|| gridcraft_model::chart_style::by_id(201)) } else { None };
    let mut s = format!(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<cs:chartStyle xmlns:cs=\"http://schemas.microsoft.com/office/drawing/2012/chartStyle\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" id=\"{id}\">",
    );
    for e in [
        "axisTitle",
        "categoryAxis",
        "chartArea",
        "dataLabel",
        "dataLabelCallout",
        "dataPoint",
        "dataPoint3D",
        "dataPointLine",
        "dataPointMarker",
        "dataPointMarkerLayout",
        "dataPointWireframe",
        "dataTable",
        "downBar",
        "dropLine",
        "errorBar",
        "floor",
        "gridlineMajor",
        "gridlineMinor",
        "hiLoLine",
        "leaderLine",
        "legend",
        "plotArea",
        "plotArea3D",
        "seriesAxis",
        "seriesLine",
        "title",
        "trendline",
        "trendlineLabel",
        "upBar",
        "valueAxis",
        "wall",
    ] {
        if e == "dataPointMarkerLayout" {
            s.push_str("<cs:dataPointMarkerLayout symbol=\"circle\" size=\"5\"/>");
        } else if by_point && e == "dataPoint" {
            let fill = match look {
                Some(st) => crate::chart_look::series(st, "<a:schemeClr val=\"phClr\"/>").replace("c:spPr>", "cs:spPr>"),
                None => "<cs:spPr><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></cs:spPr>".into(),
            };
            let _ = write!(
                s,
                "<cs:dataPoint><cs:lnRef idx=\"0\"/><cs:fillRef idx=\"0\"><cs:styleClr val=\"auto\"/></cs:fillRef><cs:effectRef idx=\"0\"/><cs:fontRef idx=\"minor\"><a:schemeClr val=\"tx1\"/></cs:fontRef>{fill}</cs:dataPoint>"
            );
        } else if let Some(st) = text.filter(|_| matches!(e, "title" | "categoryAxis" | "valueAxis" | "dataLabel" | "legend" | "axisTitle")) {
            let ty = match e {
                "title" => st.title,
                "categoryAxis" => axis_text(st, true),
                "dataLabel" => st.labels,
                _ => axis_text(st, false),
            };
            let _ = write!(s, "<cs:{e}><cs:lnRef idx=\"0\"/><cs:fillRef idx=\"0\"/><cs:effectRef idx=\"0\"/>{}</cs:{e}>", style_text(&ty));
        } else {
            let _ = write!(
                s,
                "<cs:{e}><cs:lnRef idx=\"0\"/><cs:fillRef idx=\"0\"/><cs:effectRef idx=\"0\"/><cs:fontRef idx=\"minor\"><a:schemeClr val=\"tx1\"/></cs:fontRef></cs:{e}>"
            );
        }
    }
    s.push_str("</cs:chartStyle>");
    s
}

/// The colour style part a chartex part comes with: the theme accents in turn.
pub fn colors_xml() -> String {
    let mut s = String::from(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<cs:colorStyle xmlns:cs=\"http://schemas.microsoft.com/office/drawing/2012/chartStyle\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" meth=\"cycle\" id=\"10\">",
    );
    for i in 1..=6 {
        let _ = write!(s, "<a:schemeClr val=\"accent{i}\"/>");
    }
    s.push_str("<cs:variation/></cs:colorStyle>");
    s
}

/// A solid fill (and outline, for line-drawn parts like whiskers) as `cx:spPr`.
fn fill_xml(color: &str, line: bool) -> String {
    let ln = if line { format!("<a:ln w=\"9525\"><a:solidFill>{color}</a:solidFill></a:ln>") } else { String::new() };
    format!("<cx:spPr><a:solidFill>{color}</a:solidFill>{ln}</cx:spPr>")
}

/// Light grey for gridlines and the chart border: the text colour at a quarter strength.
const GREY: &str = "<a:schemeClr val=\"tx1\"><a:lumMod val=\"25000\"/><a:lumOff val=\"75000\"/></a:schemeClr>";

fn accent(i: usize) -> String {
    format!("<a:schemeClr val=\"accent{}\"/>", i % 6 + 1)
}

/// Chartex part XML for a model chart on sheet `si`. The references it uses are appended to
/// `names` (see [`NAME_PREFIX`]).
pub fn write_chartex(wb: &Workbook, si: usize, ch: &Chart, names: &mut Vec<String>) -> String {
    let hierarchy = matches!(ch.kind, ChartKind::Treemap | ChartKind::Sunburst);
    let mut s = String::from(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<cx:chartSpace xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:cx=\"http://schemas.microsoft.com/office/drawing/2014/chartex\"><cx:chartData>",
    );
    for (i, ser) in ch.series.iter().enumerate() {
        let _ = write!(s, "<cx:data id=\"{i}\">");
        if let Some(c) = ser.categories.as_deref().filter(|_| ch.kind != ChartKind::Histogram) {
            let _ = write!(s, "<cx:strDim type=\"cat\">{}</cx:strDim>", f_xml(wb, si, c, names));
        }
        let _ = write!(s, "<cx:numDim type=\"{}\">{}</cx:numDim>", if hierarchy { "size" } else { "val" }, f_xml(wb, si, &ser.values, names));
        s.push_str("</cx:data>");
    }
    s.push_str("</cx:chartData><cx:chart>");
    // a Style page style's looks are written out: the style part stays plain
    let look = (ch.elements.style != 0).then(|| gridcraft_model::chart_style::by_id_for(ch.kind, ch.elements.style)).flatten();
    let ex = |xml: String| crate::chart_look::ex(xml);
    // (the title's look goes in the style part: the reference leaves out a title with a txPr)
    match ch.title.as_deref() {
        Some(t) if !t.is_empty() => {
            let _ =
                write!(s, "<cx:title pos=\"t\" align=\"ctr\" overlay=\"0\"><cx:tx><cx:txData><cx:v>{}</cx:v></cx:txData></cx:tx></cx:title>", esc(t));
        }
        // the automatic title spelled out (a chartex part has none of its own): the one series'
        // name, linked to its cell, or the placeholder
        None => {
            let tx = match (ch.series.len(), ch.series.first().and_then(|r| r.name.as_deref())) {
                (1, Some(n)) => tx_xml(n, wb, si, names),
                _ => "<cx:tx><cx:txData><cx:v>Chart Title</cx:v></cx:txData></cx:tx>".to_string(),
            };
            let _ = write!(s, "<cx:title pos=\"t\" align=\"ctr\" overlay=\"0\">{tx}</cx:title>");
        }
        _ => {}
    }
    s.push_str("<cx:plotArea><cx:plotAreaRegion>");
    let layout = match ch.kind {
        ChartKind::BoxWhisker => "boxWhisker",
        ChartKind::Waterfall => "waterfall",
        ChartKind::Funnel => "funnel",
        ChartKind::Treemap => "treemap",
        ChartKind::Sunburst => "sunburst",
        _ => "clusteredColumn",
    };
    let pareto = ch.kind == ChartKind::Pareto;
    for (i, ser) in ch.series.iter().enumerate() {
        let _ = write!(s, "<cx:series layoutId=\"{layout}\" uniqueId=\"{{00000000-0000-0000-0000-{:08X}{i:04X}}}\">", ch.id);
        if let Some(n) = &ser.name {
            s.push_str(&tx_xml(n, wb, si, names));
        }
        // Fills are written out: they don't come from the style part. Waterfall decreases and
        // treemap/sunburst boxes get their own colours.
        let color = ser.color.as_ref().and_then(|c| srgb(c, &wb.theme)).unwrap_or_else(|| accent(i));
        // a waterfall's increases, decreases and totals, and a treemap's or sunburst's boxes, take
        // the palette's colours through the style part, unless the series has a colour of its own
        let by_point = matches!(ch.kind, ChartKind::Waterfall | ChartKind::Treemap | ChartKind::Sunburst) && ser.color.is_none();
        match look {
            _ if by_point => {}
            Some(st) => s.push_str(&ex(crate::chart_look::series(st, &color))),
            None => s.push_str(&fill_xml(&color, ch.kind == ChartKind::BoxWhisker)),
        }
        if ch.data_labels {
            let pos = match ch.elements.label_pos {
                LabelPos::InsideEnd => " pos=\"inEnd\"",
                LabelPos::OutsideEnd => " pos=\"outEnd\"",
                LabelPos::InsideBase => " pos=\"inBase\"",
                LabelPos::Center => " pos=\"ctr\"",
                LabelPos::Right => " pos=\"r\"",
                LabelPos::Left => " pos=\"l\"",
                LabelPos::Above => " pos=\"t\"",
                LabelPos::Below => " pos=\"b\"",
                _ => "",
            };
            let _ = write!(
                s,
                "<cx:dataLabels{pos}>{}<cx:visibility seriesName=\"{}\" categoryName=\"{}\" value=\"{}\"/></cx:dataLabels>",
                look.map(|st| ex(crate::chart_look::labels(st))).unwrap_or_default(),
                u8::from(ch.elements.label_text.series),
                u8::from(ch.elements.label_text.category),
                u8::from(ch.elements.label_text.value)
            );
        }
        let _ = write!(s, "<cx:dataId val=\"{i}\"/>");
        match ch.kind {
            ChartKind::Histogram => s.push_str("<cx:layoutPr><cx:binning intervalClosed=\"r\"/></cx:layoutPr>"),
            // categories are added up; plain numbers are put in bins
            ChartKind::Pareto if ser.categories.is_some() => s.push_str("<cx:layoutPr><cx:aggregation/></cx:layoutPr><cx:axisId val=\"1\"/>"),
            ChartKind::Pareto => s.push_str("<cx:layoutPr><cx:binning intervalClosed=\"r\"/></cx:layoutPr><cx:axisId val=\"1\"/>"),
            ChartKind::BoxWhisker => {
                let _ = write!(
                    s,
                    "<cx:layoutPr><cx:visibility meanLine=\"{}\" meanMarker=\"{}\" nonoutliers=\"0\" outliers=\"1\"/><cx:statistics quartileMethod=\"exclusive\"/></cx:layoutPr>",
                    u8::from(ch.elements.box_mean_line),
                    u8::from(!ch.elements.box_no_mean_marker)
                );
            }
            ChartKind::Treemap => {
                let layout = if ch.elements.parent_banner { "banner" } else { "overlapping" };
                let _ = write!(s, "<cx:layoutPr><cx:parentLabelLayout val=\"{layout}\"/></cx:layoutPr>");
            }
            ChartKind::Waterfall => {
                s.push_str("<cx:layoutPr>");
                if ch.elements.no_connectors {
                    s.push_str("<cx:visibility connectorLines=\"0\"/>");
                }
                s.push_str("<cx:subtotals>");
                for t in ser.totals.iter().take(10_000) {
                    let _ = write!(s, "<cx:idx val=\"{t}\"/>");
                }
                s.push_str("</cx:subtotals></cx:layoutPr>");
            }
            _ => {}
        }
        s.push_str("</cx:series>");
        // the running total, on the percent axis
        if pareto {
            let _ = write!(
                s,
                "<cx:series layoutId=\"paretoLine\" ownerIdx=\"{i}\" uniqueId=\"{{00000000-0000-0000-0001-{:08X}{i:04X}}}\"><cx:spPr><a:ln w=\"28575\"><a:solidFill>{}</a:solidFill></a:ln></cx:spPr><cx:axisId val=\"2\"/></cx:series>",
                ch.id,
                accent(i + 1)
            );
        }
    }
    s.push_str("</cx:plotAreaRegion>");
    // a waterfall's, box chart's or funnel's gap from its style or layout (a fraction of a bar)
    let fraction = |default: u16| format!("{}", f64::from(ch.elements.gap_width.unwrap_or(default).min(500)) / 100.0);
    let (waterfall_gap, box_gap, funnel_gap) = (fraction(50), fraction(150), fraction(6));
    let gap = match ch.kind {
        ChartKind::Histogram | ChartKind::Pareto => Some("0"),
        ChartKind::BoxWhisker => Some(box_gap.as_str()),
        ChartKind::Waterfall => Some(waterfall_gap.as_str()),
        ChartKind::Funnel => Some(funnel_gap.as_str()),
        _ => None,
    };
    let title = |t: Option<&str>| {
        t.filter(|t| !t.is_empty())
            .map(|t| format!("<cx:title><cx:tx><cx:txData><cx:v>{}</cx:v></cx:txData></cx:tx></cx:title>", esc(t)))
            .unwrap_or_default()
    };
    if let Some(gap) = gap {
        let axis_look = |category: bool| look.map(|st| ex(crate::chart_look::axis(st, category))).unwrap_or_default();
        // tick marks out from an axis: a histogram's or waterfall's category axis, any value axis
        let out = "<cx:majorTickMarks type=\"out\"/>";
        let cat_ticks =
            if matches!(ch.kind, ChartKind::Histogram | ChartKind::Pareto | ChartKind::Waterfall) && !ch.elements.no_cat_ticks { out } else { "" };
        let val_ticks = if ch.elements.val_ticks { out } else { "" };
        let grid = |minor: bool| match look {
            Some(st) => ex(crate::chart_look::gridlines(st, minor)),
            None => format!("<cx:spPr><a:ln w=\"9525\"><a:solidFill>{GREY}</a:solidFill></a:ln></cx:spPr>"),
        };
        let _ = write!(
            s,
            "<cx:axis id=\"0\"{}><cx:catScaling gapWidth=\"{}\"/>{}{}{cat_ticks}<cx:tickLabels/>{}</cx:axis>",
            if ch.elements.hide_cat_axis { " hidden=\"1\"" } else { "" },
            esc_attr(gap),
            title(ch.x_title.as_deref()),
            if ch.elements.cat_gridlines { format!("<cx:majorGridlines>{}</cx:majorGridlines>", grid(false)) } else { String::new() },
            axis_look(true)
        );
        if ch.kind != ChartKind::Funnel {
            let _ = write!(
                s,
                "<cx:axis id=\"1\"{}><cx:valScaling/>{}{}{}{val_ticks}<cx:tickLabels/>{}</cx:axis>",
                if ch.elements.hide_val_axis { " hidden=\"1\"" } else { "" },
                title(ch.y_title.as_deref()),
                if ch.gridlines { format!("<cx:majorGridlines>{}</cx:majorGridlines>", grid(false)) } else { String::new() },
                if ch.elements.minor_val_gridlines { format!("<cx:minorGridlines>{}</cx:minorGridlines>", grid(true)) } else { String::new() },
                axis_look(false)
            );
        }
        if pareto {
            s.push_str("<cx:axis id=\"2\"><cx:valScaling max=\"1\" min=\"0\"/><cx:units unit=\"percentage\"/><cx:tickLabels/></cx:axis>");
        }
    }
    s.push_str("</cx:plotArea>");
    let pos = match ch.legend {
        LegendPos::None => None,
        LegendPos::Bottom => Some("b"),
        LegendPos::Top => Some("t"),
        LegendPos::Left => Some("l"),
        LegendPos::Right => Some("r"),
    };
    if let Some(p) = pos {
        let _ = write!(s, "<cx:legend pos=\"{p}\" align=\"ctr\" overlay=\"0\"/>");
    }
    // the style's chart area, or a white one with a light border like other charts
    match look {
        Some(st) => {
            // a chart area in the series' colour takes the first series'
            let first = ch.series.first().and_then(|r| r.color.as_ref()).and_then(|c| srgb(c, &wb.theme)).unwrap_or_else(|| accent(0));
            let _ = write!(s, "</cx:chart>{}</cx:chartSpace>", ex(crate::chart_look::area(st, &first)));
        }
        None => {
            let _ = write!(
                s,
                "</cx:chart><cx:spPr><a:solidFill><a:schemeClr val=\"bg1\"/></a:solidFill><a:ln w=\"9525\"><a:solidFill>{GREY}</a:solidFill></a:ln></cx:spPr></cx:chartSpace>"
            );
        }
    }
    s
}
