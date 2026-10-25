//! Chart layout: title, legend, axes and every chart kind, drawn into [`Prim`]s.

use std::collections::{HashMap, HashSet};
use std::f32::consts::{PI, TAU};

use gridcraft_model::chart_style::{self, ChartStyle, Effect, Paint, Pattern, Shade};
use gridcraft_model::{Chart, ChartKind, Depth, ErrorAmount, LabelPos, LabelText, LegendPos, LineDash, Theme, TrendlineKind};

use crate::axis::{Fmt, Scale, clean, fixed_scale, nice_scale, nice_scale_within};
use crate::resolve::{MAX_POINTS, MAX_SERIES, MAX_TRENDLINES, series_color};
use crate::trend::{auto_name, fit_trendline, moving_average};
use crate::{ChartData, HAlign, Measure, Prim, Rgba, SeriesData, VAlign};

/// The plain look's text, gridline and axis greys. Drawn first as tokens (one step short of
/// opaque, which nothing else uses) that `restyle` turns into the plain greys or a style's colours.
pub(crate) const TEXT: Rgba = [0x59, 0x59, 0x59, 0xFE];
const DARK_TEXT: Rgba = [0x26, 0x26, 0x26, 0xFF];
const GRID: Rgba = [0xD9, 0xD9, 0xD9, 0xFE];
const MINOR_GRID: Rgba = [0xF2, 0xF2, 0xF2, 0xFE];
const AXIS: Rgba = [0xBF, 0xBF, 0xBF, 0xFE];
const WHITE: Rgba = [0xFF, 0xFF, 0xFF, 0xFF];
const BLACK: Rgba = [0, 0, 0, 0xFF];
const STOCK: Rgba = [0x40, 0x40, 0x40, 0xFF];
pub(crate) const UP: Rgba = [0x3E, 0x9B, 0x5F, 0xFF];
pub(crate) const DOWN: Rgba = [0xD6, 0x4F, 0x45, 0xFF];

const TITLE_SIZE: f32 = 14.0;
const LABEL_SIZE: f32 = 9.0;
const AXIS_TITLE_SIZE: f32 = 10.0;
/// Most categories drawn by category-axis charts.
const MAX_CATS: usize = 20_000;
/// Lines with more points than this are decimated (min/max per pixel column).
const MAX_LINE_PTS: usize = 2000;
/// Most markers per series (after de-duplicating by pixel).
const MAX_MARKERS: usize = 5000;
/// Data labels are skipped when a series has more points than this.
const MAX_LABELS: usize = 200;
/// A data table shows at most this many categories and series.
const MAX_TABLE_CATS: usize = 60;
/// Most categories that get lines of their own across the plot.
const MAX_GRID_CATS: usize = 2000;
const MAX_TABLE_SERIES: usize = 20;
/// How many steps a value axis takes (points): an upright one a step for each `MIN_GRID_GAP` of
/// its length past the first `GRID_LOST`, one across a step for each `MIN_GRID_GAP_ACROSS` of
/// the chart's width. Fitted to the steps the reference picks on charts 150 to 288 pixels tall
/// and 300 to 480 wide: a short axis takes fewer, wider steps.
const MIN_GRID_GAP: f32 = 10.5;
/// How far below the plot its category labels start (points), as the reference sets them.
const CAT_LABEL_GAP: f32 = 7.0;
const GRID_LOST: f32 = 10.0;
const MIN_GRID_GAP_ACROSS: f32 = 50.0;

/// The most steps an upright value axis `len` points long takes.
fn value_room(len: f32) -> f64 {
    f64::from((len - GRID_LOST) / MIN_GRID_GAP)
}

#[derive(Clone, Copy, Debug)]
struct Bx {
    x: f32,
    y: f32,
    w: f32,
    h: f32,
}

impl Bx {
    fn right(&self) -> f32 {
        self.x + self.w
    }
    fn bottom(&self) -> f32 {
        self.y + self.h
    }
    fn ok(&self) -> bool {
        self.w.is_finite() && self.h.is_finite() && self.w > 4.0 && self.h > 4.0
    }
    fn cx(&self) -> f32 {
        self.x + self.w / 2.0
    }
    fn cy(&self) -> f32 {
        self.y + self.h / 2.0
    }
}

struct Ctx<'a> {
    out: Vec<Prim>,
    m: &'a dyn Measure,
    /// The chart's Style page style, and the theme colours it is made from.
    style: Option<&'static ChartStyle>,
    theme: Vec<[u8; 3]>,
}

impl Ctx<'_> {
    fn tw(&self, s: &str, size: f32) -> f32 {
        let w = self.m.text_width(s, size, false);
        if w.is_finite() { w.max(0.0) } else { 0.0 }
    }
    fn fit(&self, s: &str, size: f32, maxw: f32) -> String {
        if self.tw(s, size) <= maxw {
            return s.to_string();
        }
        let chars: Vec<char> = s.chars().collect();
        let mut n = chars.len().min(400);
        while n > 0 {
            n -= 1;
            let t: String = chars.iter().take(n).collect::<String>() + "…";
            if self.tw(&t, size) <= maxw {
                return if n == 0 { String::new() } else { t };
            }
        }
        String::new()
    }
    #[allow(clippy::too_many_arguments)]
    fn text(&mut self, x: f32, y: f32, s: &str, size: f32, color: Rgba, align: HAlign, valign: VAlign) {
        if s.is_empty() {
            return;
        }
        self.out.push(Prim::Text { x, y, text: s.to_string(), size, color, bold: false, align, valign, rotation: 0.0 });
    }
    /// A style's colour for a series coloured `series`.
    fn shade(&self, s: &Shade, series: Rgba) -> Rgba {
        let [r, g, b, a] = chart_style::color_of(s, &self.theme, [series[0], series[1], series[2]]);
        [r, g, b, ((u16::from(a) * u16::from(series[3])) / 255) as u8]
    }
    /// A data label: in the chart's style's type, or the plain size in `fallback`.
    #[allow(clippy::too_many_arguments)]
    fn label(&mut self, x: f32, y: f32, s: &str, fallback: Rgba, series: Rgba, align: HAlign, valign: VAlign) {
        if s.is_empty() {
            return;
        }
        let (size, color, bold) = match self.style {
            Some(st) => (st.labels.size, self.shade(&st.labels.color, series), st.labels.bold),
            None => (LABEL_SIZE, fallback, false),
        };
        // a style's box behind the label
        if let Some(b) = self.style.and_then(|st| st.label_box) {
            let fill = self.shade(&b, series);
            let (w, h) = (self.m.text_width(s, size, bold).max(0.0) + 6.0, size * 1.4);
            let left = match align {
                HAlign::Left => x - 3.0,
                HAlign::Center => x - w / 2.0,
                HAlign::Right => x - w + 3.0,
            };
            let top = match valign {
                VAlign::Top => y - 1.0,
                VAlign::Middle => y - h / 2.0,
                VAlign::Bottom => y - h + 1.0,
            };
            self.out.push(Prim::Rect { x: left, y: top, w, h, fill: Some(fill), stroke: None, radius: 0.0 });
        }
        // a style's labels turned a quarter turn read upwards from where they start
        if self.style.is_some_and(|st| st.label_turn) {
            let (align, x) = match valign {
                VAlign::Bottom => (HAlign::Left, x),
                VAlign::Top => (HAlign::Right, x),
                VAlign::Middle => (HAlign::Center, x),
            };
            self.out.push(Prim::Text { x, y, text: s.to_string(), size, color, bold, align, valign: VAlign::Middle, rotation: -PI / 2.0 });
            return;
        }
        self.out.push(Prim::Text { x, y, text: s.to_string(), size, color, bold, align, valign, rotation: 0.0 });
    }
    /// Fills `b` with `paint`, a style's (its series colour `series`).
    fn paint(&mut self, b: Bx, paint: &Paint, series: Rgba) {
        if !(b.w > 0.0 && b.h > 0.0) {
            return;
        }
        match paint {
            Paint::None => {}
            Paint::Solid(c) => {
                let c = self.shade(c, series);
                self.rect(b.x, b.y, b.w, b.h, c, None);
            }
            Paint::Gradient { stops, from } => {
                let stops: Vec<(f32, Rgba)> = stops.iter().map(|(at, c)| (*at as f32 / 100_000.0, self.shade(c, series))).collect();
                let at = |t: f32| gradient_at(&stops, t);
                match from {
                    // down the part, in bands
                    None => {
                        let n = ((b.h / 2.0) as usize).clamp(1, 48);
                        for k in 0..n {
                            let (t0, t1) = (k as f32 / n as f32, (k + 1) as f32 / n as f32);
                            self.rect(b.x, b.y + b.h * t0, b.w, b.h * (t1 - t0) + 0.5, at((t0 + t1) / 2.0), None);
                        }
                    }
                    // out from a point, in rings of rectangles
                    Some((fx, fy)) => {
                        let (cx, cy) = (b.x + b.w * *fx as f32 / 100_000.0, b.y + b.h * *fy as f32 / 100_000.0);
                        let (rx, ry) = ((cx - b.x).abs().max((b.right() - cx).abs()), (cy - b.y).abs().max((b.bottom() - cy).abs()));
                        let n = 32;
                        for k in (1..=n).rev() {
                            let t = k as f32 / n as f32;
                            let (x0, x1) = ((cx - rx * t).max(b.x), (cx + rx * t).min(b.right()));
                            let (y0, y1) = ((cy - ry * t).max(b.y), (cy + ry * t).min(b.bottom()));
                            if x1 > x0 && y1 > y0 {
                                self.rect(x0, y0, x1 - x0, y1 - y0, at(t), None);
                            }
                        }
                    }
                }
            }
            Paint::Leftward(stops) => {
                let stops: Vec<(f32, Rgba)> = stops.iter().map(|(at, c)| (*at as f32 / 100_000.0, self.shade(c, series))).collect();
                let n = ((b.w / 2.0) as usize).clamp(1, 48);
                for k in 0..n {
                    let (t0, t1) = (k as f32 / n as f32, (k + 1) as f32 / n as f32);
                    // from the right edge leftwards
                    self.rect(b.right() - b.w * t1, b.y, b.w * (t1 - t0) + 0.5, b.h, gradient_at(&stops, (t0 + t1) / 2.0), None);
                }
            }
            Paint::Pattern { preset, fg, bg } => {
                let (fg, bg) = (self.shade(fg, series), self.shade(bg, series));
                self.rect(b.x, b.y, b.w, b.h, bg, None);
                match preset {
                    Pattern::NarrowHorizontal => {
                        let mut y = b.y + 1.0;
                        while y < b.bottom() && y < b.y + 4000.0 {
                            self.hline(b.x, b.right(), y, fg, 0.8);
                            y += 2.0;
                        }
                    }
                    Pattern::NarrowVertical => {
                        let mut x = b.x + 1.0;
                        while x < b.right() && x < b.x + 4000.0 {
                            self.vline(x, b.y, b.bottom(), fg, 0.8);
                            x += 2.0;
                        }
                    }
                    Pattern::LightUpwardDiagonal => {
                        // lines from bottom left to top right, cut to the part
                        let mut k = 0.0;
                        while k < b.w + b.h && k < 8000.0 {
                            let (x0, y0) = if k <= b.h { (b.x, b.y + k) } else { (b.x + k - b.h, b.bottom()) };
                            let len = (b.right() - x0).min(y0 - b.y);
                            if len > 0.0 {
                                self.line(vec![[x0, y0], [x0 + len, y0 - len]], fg, 0.6);
                            }
                            k += 4.0;
                        }
                    }
                    Pattern::LightDownwardDiagonal | Pattern::DarkDownwardDiagonal => {
                        // lines from top left to bottom right, cut to the part
                        let mut k = -b.h;
                        while k < b.w && k < 8000.0 {
                            let (x0, y0) = if k >= 0.0 { (b.x + k, b.y) } else { (b.x, b.y - k) };
                            let len = (b.right() - x0).min(b.bottom() - y0);
                            if len > 0.0 {
                                let width = if *preset == Pattern::DarkDownwardDiagonal { 1.4 } else { 0.6 };
                                self.line(vec![[x0, y0], [x0 + len, y0 + len]], fg, width);
                            }
                            k += 4.0;
                        }
                    }
                }
            }
        }
    }
    /// A style's markers on `pts` for a series coloured `color`, glowing in a glowing style;
    /// false when the style has none (or there is no style).
    fn styled_markers(&mut self, pts: impl Iterator<Item = [f32; 2]>, color: Rgba) -> bool {
        let Some(st) = self.style else { return false };
        let Some(mk) = st.marker else { return false };
        let fill = self.shade(&mk.fill, color);
        let edge = mk.edge.map(|l| (self.shade(&l.color, color), l.width));
        let glow = (st.effect == Effect::Glow).then(|| self.shade(&chart_style::GLOW, color));
        let r = f32::from(mk.size) * 0.5;
        for p in pts.take(MAX_MARKERS) {
            if let Some(g) = glow {
                self.out.push(Prim::Circle { cx: p[0], cy: p[1], r: r + 2.5, fill: Some(g), stroke: None });
            }
            if mk.diamond {
                let pts = vec![[p[0], p[1] - r], [p[0] + r, p[1]], [p[0], p[1] + r], [p[0] - r, p[1]]];
                self.out.push(Prim::Polygon { pts, fill, stroke: edge });
            } else {
                self.out.push(Prim::Circle { cx: p[0], cy: p[1], r, fill: Some(fill), stroke: edge });
            }
        }
        true
    }
    /// A pie slice coloured `color`: its fill and edge, plain or in the chart's style.
    fn slice(&self, color: Rgba) -> (Rgba, Option<(Rgba, f32)>) {
        let Some(st) = self.style else { return (color, Some((WHITE, 1.5))) };
        let fill = match &st.series {
            Paint::None => [0, 0, 0, 0],
            Paint::Solid(c) => self.shade(c, color),
            // a wedge takes a pattern's or gradient's colours mixed
            Paint::Gradient { stops, .. } => {
                let stops: Vec<(f32, Rgba)> = stops.iter().map(|(at, c)| (*at as f32 / 100_000.0, self.shade(c, color))).collect();
                gradient_at(&stops, 0.35)
            }
            Paint::Leftward(stops) => {
                let stops: Vec<(f32, Rgba)> = stops.iter().map(|(at, c)| (*at as f32 / 100_000.0, self.shade(c, color))).collect();
                gradient_at(&stops, 0.35)
            }
            Paint::Pattern { fg, bg, .. } => gradient_at(&[(0.0, self.shade(fg, color)), (1.0, self.shade(bg, color))], 0.5),
        };
        (fill, st.series_line.map(|l| (self.shade(&l.color, color), l.width)))
    }
    /// A column or bar `color`: plain, or as the chart's style draws its series.
    fn column(&mut self, x: f32, y: f32, w: f32, h: f32, color: Rgba) {
        let Some(st) = self.style else {
            return self.rect(x, y, w, h, color, None);
        };
        let b = Bx { x, y, w, h };
        let line = st.series_line.map(|l| (self.shade(&l.color, color), l.width));
        if st.effect == Effect::Glow {
            let glow = self.shade(&chart_style::GLOW, color);
            self.out.push(Prim::Rect { x: x - 2.0, y: y - 2.0, w: w + 4.0, h: h + 4.0, fill: None, stroke: Some((glow, 4.0)), radius: 2.0 });
        }
        self.paint(b, &st.series, color);
        if st.effect == Effect::InnerShadow && w > 4.0 && h > 4.0 {
            let edge = [color[0], color[1], color[2], 150];
            self.out.push(Prim::Rect { x: x + 1.0, y: y + 1.0, w: w - 2.0, h: h - 2.0, fill: None, stroke: Some((edge, 2.0)), radius: 0.0 });
        }
        if let Some(l) = line {
            self.out.push(Prim::Rect { x, y, w, h, fill: None, stroke: Some(l), radius: 0.0 });
        }
    }
    fn rect(&mut self, x: f32, y: f32, w: f32, h: f32, fill: Rgba, stroke: Option<(Rgba, f32)>) {
        self.out.push(Prim::Rect { x, y, w, h, fill: Some(fill), stroke, radius: 0.0 });
    }
    fn line(&mut self, pts: Vec<[f32; 2]>, color: Rgba, width: f32) {
        if pts.len() >= 2 {
            self.out.push(Prim::Line { pts, color, width, dash: false });
        }
    }
    fn hline(&mut self, x0: f32, x1: f32, y: f32, color: Rgba, width: f32) {
        self.line(vec![[x0, y], [x1, y]], color, width);
    }
    fn vline(&mut self, x: f32, y0: f32, y1: f32, color: Rgba, width: f32) {
        self.line(vec![[x, y0], [x, y1]], color, width);
    }
}

/// Text colour readable on `fill`.
fn on(fill: Rgba) -> Rgba {
    let l = 0.299 * fill[0] as f32 + 0.587 * fill[1] as f32 + 0.114 * fill[2] as f32;
    if l < 150.0 { WHITE } else { DARK_TEXT }
}

/// How a legend entry's swatch is drawn.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Swatch {
    Box,
    Line,
    LineMarker,
    Marker,
}

/// The kind a series is drawn as (per-series kinds only apply to combo charts).
fn plot_kind(chart: &Chart, s: &SeriesData) -> ChartKind {
    if chart.kind == ChartKind::Combo { if s.kind == ChartKind::Combo { ChartKind::Line } else { s.kind } } else { chart.kind }
}

/// Colour of point `j` for charts that colour by point (pie, doughnut, treemap, sunburst).
fn point_color(data: &ChartData, j: usize) -> Rgba {
    match (j, data.series.first()) {
        (0, Some(s)) => s.color,
        _ => j.checked_rem(data.palette.len()).and_then(|k| data.palette.get(k)).copied().unwrap_or_else(|| series_color(&Theme::default(), j)),
    }
}

fn first_len(data: &ChartData) -> usize {
    data.series.first().map(|s| s.values.len()).unwrap_or(0)
}

fn cat_label(data: &ChartData, j: usize) -> String {
    data.categories.get(j).cloned().unwrap_or_else(|| (j + 1).to_string())
}

/// Legend entries (text, colour, swatch) `render` draws for this chart; empty when hidden.
pub fn legend_entries(chart: &Chart, data: &ChartData) -> Vec<(String, Rgba, Swatch)> {
    if chart.legend == LegendPos::None {
        return vec![];
    }
    match chart.kind {
        ChartKind::Pie | ChartKind::PieOfPie | ChartKind::BarOfPie | ChartKind::Doughnut | ChartKind::Treemap | ChartKind::Sunburst => {
            let n = first_len(data).min(MAX_CATS);
            (0..n).map(|j| (cat_label(data, j), point_color(data, j), Swatch::Box)).collect()
        }
        ChartKind::Funnel | ChartKind::Stock => vec![],
        ChartKind::Histogram | ChartKind::Pareto => data.series.first().map(|s| vec![(s.name.clone(), s.color, Swatch::Box)]).unwrap_or_default(),
        ChartKind::Surface | ChartKind::Contour => surface_legend(data, false),
        ChartKind::SurfaceWireframe | ChartKind::ContourWireframe => surface_legend(data, true),
        ChartKind::Waterfall => {
            if data.series.is_empty() {
                return vec![];
            }
            let total = data.series.first().map(|s| s.color).unwrap_or(TEXT);
            vec![("Increase".into(), UP, Swatch::Box), ("Decrease".into(), DOWN, Swatch::Box), ("Total".into(), total, Swatch::Box)]
        }
        _ => {
            let series = data.series.iter().take(MAX_SERIES);
            let mut out: Vec<(String, Rgba, Swatch)> = series
                .clone()
                .map(|s| {
                    let sw = match plot_kind(chart, s) {
                        ChartKind::Line
                        | ChartKind::LineStacked
                        | ChartKind::LineStacked100
                        | ChartKind::Radar
                        | ChartKind::ScatterLinesNoMarkers => Swatch::Line,
                        ChartKind::LineMarkers
                        | ChartKind::LineStackedMarkers
                        | ChartKind::LineStacked100Markers
                        | ChartKind::RadarMarkers
                        | ChartKind::ScatterLines => Swatch::LineMarker,
                        ChartKind::Scatter | ChartKind::Bubble => Swatch::Marker,
                        _ => Swatch::Box,
                    };
                    (s.name.clone(), s.color, sw)
                })
                .collect();
            if reversed_legend(chart) {
                out.reverse();
            }
            // Trendlines follow the series, named "Linear (Sales)" unless given a name.
            for s in series.filter(|s| supports_trendline(plot_kind(chart, s))) {
                for t in s.trendlines.iter().take(MAX_TRENDLINES) {
                    out.push((t.spec.name.clone().unwrap_or_else(|| auto_name(t.spec.kind, &s.name)), t.color, Swatch::Line));
                }
            }
            out
        }
    }
}

/// Whether the legend lists the series last first, as Excel's does: a clustered bar chart's in any
/// place (its first series is the lowest bar), and a stacked chart's in a column beside the plot,
/// so it reads in the stack's order from the top. Stacked bars read left to right and keep theirs.
fn reversed_legend(chart: &Chart) -> bool {
    use ChartKind::*;
    match chart.kind {
        BarClustered => true,
        ColumnStacked
        | ColumnStacked100
        | AreaStacked
        | AreaStacked100
        | LineStacked
        | LineStacked100
        | LineStackedMarkers
        | LineStacked100Markers => {
            matches!(chart.legend, LegendPos::Left | LegendPos::Right)
        }
        _ => false,
    }
}

fn sanitize_vals(v: &[Option<f64>]) -> Vec<Option<f64>> {
    v.iter().take(MAX_POINTS).map(|x| x.filter(|n| n.is_finite()).map(clean)).collect()
}

fn sanitize(d: &ChartData) -> ChartData {
    ChartData {
        style: d.style,
        theme: d.theme.iter().take(16).copied().collect(),
        palette: d.palette.iter().take(MAX_POINTS).copied().collect(),
        categories: d.categories.iter().take(MAX_POINTS).cloned().collect(),
        series: d
            .series
            .iter()
            .take(MAX_SERIES)
            .map(|s| SeriesData {
                name: s.name.clone(),
                values: sanitize_vals(&s.values),
                x: s.x.as_deref().map(sanitize_vals),
                sizes: s.sizes.as_deref().map(sanitize_vals),
                color: s.color,
                kind: s.kind,
                secondary: s.secondary,
                smooth: s.smooth,
                trendlines: s.trendlines.iter().take(MAX_TRENDLINES).cloned().collect(),
                number_format: s.number_format.clone(),
            })
            .collect(),
    }
}

fn chart_title(chart: &Chart, data: &ChartData) -> Option<String> {
    match &chart.title {
        Some(t) => Some(t.trim().to_string()).filter(|t| !t.is_empty()),
        None if data.series.len() == 1 => data.series.first().map(|s| s.name.clone()).filter(|t| !t.is_empty()),
        None => None,
    }
}

/// The colour `t` of the way along gradient `stops` (in order or not).
fn gradient_at(stops: &[(f32, Rgba)], t: f32) -> Rgba {
    let mut sorted = stops.to_vec();
    sorted.sort_by(|a, b| a.0.total_cmp(&b.0));
    let Some(first) = sorted.first().copied() else { return WHITE };
    let mut prev = first;
    for (at, c) in sorted {
        if t <= at {
            let k = if at > prev.0 { (t - prev.0) / (at - prev.0) } else { 1.0 };
            let mix = |a: u8, b: u8| (f32::from(a) + (f32::from(b) - f32::from(a)) * k.clamp(0.0, 1.0)).round() as u8;
            return [mix(prev.1[0], c[0]), mix(prev.1[1], c[1]), mix(prev.1[2], c[2]), mix(prev.1[3], c[3])];
        }
        prev = (at, c);
    }
    prev.1
}

/// The plain look's tokens as their greys, or as the style's text, gridline and axis colours.
fn restyle(prims: &mut [Prim], ctx: &Ctx) {
    let opaque = |c: Rgba| [c[0], c[1], c[2], 0xFF];
    let (text, grid, minor, axis) = match ctx.style {
        Some(st) => (
            ctx.shade(&st.text, BLACK),
            ctx.shade(&st.grid, BLACK),
            ctx.shade(&st.minor_grid, BLACK),
            st.axis_line.map_or([0, 0, 0, 0], |l| ctx.shade(&l.color, BLACK)),
        ),
        None => (opaque(TEXT), opaque(GRID), opaque(MINOR_GRID), opaque(AXIS)),
    };
    let swap = |c: &mut Rgba| {
        *c = match *c {
            TEXT => text,
            GRID => grid,
            MINOR_GRID => minor,
            AXIS => axis,
            other => other,
        }
    };
    for p in prims.iter_mut() {
        match p {
            Prim::Text { color, .. } | Prim::Line { color, .. } => swap(color),
            Prim::Rect { fill, stroke, .. } | Prim::Circle { fill, stroke, .. } => {
                if let Some(f) = fill {
                    swap(f);
                }
                if let Some((c, _)) = stroke {
                    swap(c);
                }
            }
            Prim::Polygon { fill, stroke, .. } | Prim::Wedge { fill, stroke, .. } => {
                swap(fill);
                if let Some((c, _)) = stroke {
                    swap(c);
                }
            }
        }
    }
}

/// Lays out and draws a chart into a `w`×`h` box at the origin. Empty for a degenerate box.
pub fn render(chart: &Chart, data: &ChartData, w: f32, h: f32, m: &dyn Measure) -> Vec<Prim> {
    if !(w.is_finite() && h.is_finite()) || w < 1.0 || h < 1.0 || w > 1e7 || h > 1e7 {
        return vec![];
    }
    let prims = render_points(chart, data, w * PT, h * PT, m);
    finalize(prims.into_iter().map(|p| scaled(p, 1.0 / PT)).collect(), w, h)
}

/// Points per pixel (96 to the inch). A chart's text sizes, gaps and line widths are points, as
/// the reference's are (9 pt labels, a 14 pt title, 0.75 pt gridlines): it is laid out in points
/// and drawn in pixels.
const PT: f32 = 0.75;

/// `p` drawn `k` times as large.
fn scaled(p: Prim, k: f32) -> Prim {
    let pts = |v: Vec<[f32; 2]>| v.into_iter().map(|[x, y]| [x * k, y * k]).collect();
    let stroke = |s: Option<(Rgba, f32)>| s.map(|(c, w)| (c, w * k));
    match p {
        Prim::Rect { x, y, w, h, fill, stroke: st, radius } => {
            Prim::Rect { x: x * k, y: y * k, w: w * k, h: h * k, fill, stroke: stroke(st), radius: radius * k }
        }
        Prim::Line { pts: v, color, width, dash } => Prim::Line { pts: pts(v), color, width: width * k, dash },
        Prim::Polygon { pts: v, fill, stroke: st } => Prim::Polygon { pts: pts(v), fill, stroke: stroke(st) },
        Prim::Wedge { cx, cy, r_outer, r_inner, a0, a1, fill, stroke: st } => {
            Prim::Wedge { cx: cx * k, cy: cy * k, r_outer: r_outer * k, r_inner: r_inner * k, a0, a1, fill, stroke: stroke(st) }
        }
        Prim::Circle { cx, cy, r, fill, stroke: st } => Prim::Circle { cx: cx * k, cy: cy * k, r: r * k, fill, stroke: stroke(st) },
        Prim::Text { x, y, text, size, color, bold, align, valign, rotation } => {
            Prim::Text { x: x * k, y: y * k, text, size: size * k, color, bold, align, valign, rotation }
        }
    }
}

/// [`render`] in points: `w` by `h` points.
fn render_points(chart: &Chart, data: &ChartData, w: f32, h: f32, m: &dyn Measure) -> Vec<Prim> {
    let data = sanitize(data);
    let theme = if data.theme.len() >= 10 {
        data.theme.clone()
    } else {
        Theme::default().colors.iter().map(|c| [(c >> 16) as u8, (c >> 8) as u8, *c as u8]).collect()
    };
    let style = (data.style != 0).then(|| chart_style::by_id(data.style)).flatten();
    let mut ctx = Ctx { out: Vec::new(), m, style, theme };
    match style {
        Some(st) => {
            ctx.paint(Bx { x: 0.0, y: 0.0, w, h }, &st.area, BLACK);
            if let Some(edge) = st.area_line.map(|c| ctx.shade(&c, BLACK)) {
                ctx.out.push(Prim::Rect { x: 0.0, y: 0.0, w, h, fill: None, stroke: Some((edge, 1.0)), radius: 0.0 });
            }
        }
        None => ctx.out.push(Prim::Rect { x: 0.0, y: 0.0, w, h, fill: Some(WHITE), stroke: Some((GRID, 1.0)), radius: 0.0 }),
    }
    // the reference keeps 8 pt above the title and below the legend, whatever the chart's size
    let pad = (w.min(h) * 0.04).clamp(2.0, 10.0);
    let pad_v = (h * 0.07).clamp(2.0, 8.0);
    let mut area = Bx { x: pad, y: pad_v, w: w - 2.0 * pad, h: h - 2.0 * pad_v };
    // an overlay title sits on the plot, drawn after it, and takes no room from it
    let mut overlay = None;
    // the style's title: its size, weight, capitals and colour
    let (title_size, title_bold, title_caps, title_color) = match style {
        Some(st) => (st.title.size, st.title.bold, st.title.caps, ctx.shade(&st.title.color, BLACK)),
        None => (TITLE_SIZE, false, false, TEXT),
    };
    if let Some(t) = chart_title(chart, &data) {
        let size = title_size.min(h * 0.12).max(4.0);
        let t = if title_caps { t.to_uppercase() } else { t };
        let t = ctx.fit(&t, size, area.w);
        if chart.elements.title_overlay {
            overlay = Some((t, size, area.y));
        } else {
            ctx.out.push(Prim::Text {
                x: w / 2.0,
                y: area.y,
                text: t,
                size,
                color: title_color,
                bold: title_bold,
                align: HAlign::Center,
                valign: VAlign::Top,
                rotation: 0.0,
            });
            let used = size * 1.3 + 4.0;
            area.y += used;
            area.h -= used;
        }
    }
    let entries = legend_entries(chart, &data);
    if !entries.is_empty() && area.ok() {
        area = draw_legend(&mut ctx, &entries, chart.legend, area);
    }
    if area.ok() && !data.series.is_empty() {
        draw_kind(&mut ctx, chart, &data, area);
    }
    if let Some((t, size, y)) = overlay {
        ctx.out.push(Prim::Text {
            x: w / 2.0,
            y,
            text: t,
            size,
            color: title_color,
            bold: title_bold,
            align: HAlign::Center,
            valign: VAlign::Top,
            rotation: 0.0,
        });
    }
    let mut out = std::mem::take(&mut ctx.out);
    restyle(&mut out, &ctx);
    finalize(out, w, h)
}

fn draw_kind(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    use ChartKind::*;
    match chart.kind {
        Pie if chart.depth != Depth::Flat => pie3d(ctx, chart, data, area),
        Pie => pie(ctx, chart, data, area, 0.0, false),
        PieOfPie => of_pie(ctx, chart, data, area, false),
        BarOfPie => of_pie(ctx, chart, data, area, true),
        Doughnut => pie(ctx, chart, data, area, f32::from(chart.elements.hole_size.unwrap_or(50).clamp(10, 90)) / 100.0, false),
        Sunburst => pie(ctx, chart, data, area, 0.2, true),
        Treemap => treemap(ctx, chart, data, area),
        Funnel => funnel(ctx, chart, data, area),
        Radar | RadarMarkers | RadarFilled => radar(ctx, chart, data, area),
        _ => {
            let area = axis_titles(ctx, chart, area);
            if !area.ok() {
                return;
            }
            match chart.kind {
                BarClustered | BarStacked | BarStacked100 => cartesian(ctx, chart, data, area, true),
                Scatter | ScatterLines | ScatterLinesNoMarkers | Bubble => scatter(ctx, chart, data, area),
                Histogram => histogram(ctx, chart, data, area),
                Pareto => pareto(ctx, chart, data, area),
                Surface => surface3d(ctx, chart, data, area, false),
                SurfaceWireframe => surface3d(ctx, chart, data, area, true),
                Contour => contour(ctx, data, area, false),
                ContourWireframe => contour(ctx, data, area, true),
                Waterfall => waterfall(ctx, chart, data, area),
                BoxWhisker => box_whisker(ctx, chart, data, area),
                Stock => stock(ctx, chart, data, area),
                _ => cartesian(ctx, chart, data, area, false),
            }
        }
    }
}

fn axis_titles(ctx: &mut Ctx, chart: &Chart, mut area: Bx) -> Bx {
    let lh = AXIS_TITLE_SIZE * 1.4 + 2.0;
    if let Some(t) = chart.x_title.as_deref().map(str::trim).filter(|t| !t.is_empty()) {
        let t = ctx.fit(t, AXIS_TITLE_SIZE, area.w);
        ctx.text(area.cx(), area.bottom(), &t, AXIS_TITLE_SIZE, TEXT, HAlign::Center, VAlign::Bottom);
        area.h -= lh;
    }
    if let Some(t) = chart.y_title.as_deref().map(str::trim).filter(|t| !t.is_empty()) {
        let t = ctx.fit(t, AXIS_TITLE_SIZE, area.h);
        if !t.is_empty() {
            ctx.out.push(Prim::Text {
                x: area.x,
                y: area.cy(),
                text: t,
                size: AXIS_TITLE_SIZE,
                color: TEXT,
                bold: false,
                align: HAlign::Center,
                valign: VAlign::Top,
                rotation: -PI / 2.0,
            });
        }
        area.x += lh;
        area.w -= lh;
    }
    area
}

// ---------------------------------------------------------------- legend

fn swatch_w(sw: Swatch, s: f32) -> f32 {
    if sw == Swatch::Box { s } else { s * 2.0 }
}

fn draw_swatch(ctx: &mut Ctx, sw: Swatch, x: f32, yc: f32, s: f32, color: Rgba) {
    match sw {
        Swatch::Box => ctx.rect(x, yc - s / 2.0, s, s, color, None),
        Swatch::Line => ctx.hline(x, x + 2.0 * s, yc, color, 2.0),
        Swatch::LineMarker => {
            ctx.hline(x, x + 2.0 * s, yc, color, 2.0);
            ctx.out.push(Prim::Circle { cx: x + s, cy: yc, r: s * 0.35, fill: Some(color), stroke: None });
        }
        Swatch::Marker => ctx.out.push(Prim::Circle { cx: x + s, cy: yc, r: s * 0.4, fill: Some(color), stroke: None }),
    }
}

fn draw_legend(ctx: &mut Ctx, entries: &[(String, Rgba, Swatch)], pos: LegendPos, area: Bx) -> Bx {
    // as the reference's: small keys close to their names, entries a little apart
    let size = LABEL_SIZE;
    let row_h = size * 1.6;
    let s = size * 0.52;
    let gap = 2.0;
    let spacing = size * 0.65;
    let item_w = |ctx: &Ctx, e: &(String, Rgba, Swatch)| swatch_w(e.2, s) + gap + ctx.tw(&e.0, size);
    match pos {
        LegendPos::None => area,
        LegendPos::Top | LegendPos::Bottom => {
            let max_rows = ((area.h * 0.3 / row_h).floor() as usize).max(1);
            let mut rows: Vec<Vec<(usize, f32)>> = vec![vec![]];
            let mut cur = 0.0f32;
            for (i, e) in entries.iter().enumerate() {
                let iw = item_w(ctx, e).min(area.w);
                let need = if cur > 0.0 { spacing + iw } else { iw };
                if cur > 0.0 && cur + need > area.w {
                    if rows.len() >= max_rows {
                        break;
                    }
                    rows.push(vec![]);
                    cur = 0.0;
                }
                cur += if cur > 0.0 { spacing + iw } else { iw };
                if let Some(r) = rows.last_mut() {
                    r.push((i, iw));
                }
            }
            let lh = rows.len() as f32 * row_h;
            let y0 = if pos == LegendPos::Bottom { area.bottom() - lh } else { area.y };
            for (ri, row) in rows.iter().enumerate() {
                let total: f32 = row.iter().map(|(_, w)| w).sum::<f32>() + spacing * row.len().saturating_sub(1) as f32;
                let mut x = area.x + (area.w - total).max(0.0) / 2.0;
                let yc = y0 + ri as f32 * row_h + row_h / 2.0;
                for &(i, iw) in row {
                    if let Some(e) = entries.get(i) {
                        draw_swatch(ctx, e.2, x, yc, s, e.1);
                        let sx = swatch_w(e.2, s) + gap;
                        let t = ctx.fit(&e.0, size, (iw - sx).max(0.0) + 0.5);
                        ctx.text(x + sx, yc, &t, size, TEXT, HAlign::Left, VAlign::Middle);
                    }
                    x += iw + spacing;
                }
            }
            let used = lh + 4.0;
            if pos == LegendPos::Bottom { Bx { h: area.h - used, ..area } } else { Bx { y: area.y + used, h: area.h - used, ..area } }
        }
        LegendPos::Left | LegendPos::Right => {
            let colw = entries.iter().map(|e| item_w(ctx, e)).fold(0.0f32, f32::max).min(area.w * 0.35);
            let max_rows = ((area.h / row_h).floor() as usize).max(1);
            let n = entries.len().min(max_rows);
            let lh = n as f32 * row_h;
            let y0 = area.y + (area.h - lh).max(0.0) / 2.0;
            let x0 = if pos == LegendPos::Right { area.right() - colw } else { area.x };
            for (i, e) in entries.iter().take(n).enumerate() {
                let yc = y0 + i as f32 * row_h + row_h / 2.0;
                draw_swatch(ctx, e.2, x0, yc, s, e.1);
                let sx = swatch_w(e.2, s) + gap;
                let t = ctx.fit(&e.0, size, (colw - sx).max(0.0) + 0.5);
                ctx.text(x0 + sx, yc, &t, size, TEXT, HAlign::Left, VAlign::Middle);
            }
            let used = colw + 8.0;
            if pos == LegendPos::Right { Bx { w: area.w - used, ..area } } else { Bx { x: area.x + used, w: area.w - used, ..area } }
        }
    }
}

// ---------------------------------------------------------------- axes frame

enum CatAxis<'a> {
    Labels(&'a [String]),
    Numeric(Scale, &'a Fmt),
}

struct ValAxis<'a> {
    scale: Scale,
    fmt: &'a Fmt,
}

impl ValAxis<'_> {
    fn labels(&self) -> Vec<(f64, String)> {
        self.scale.ticks().into_iter().map(|v| (v, self.fmt.format(v, Some(self.scale.step)))).collect()
    }
}

/// Which gridlines and axes a chart with axes draws (Elements › Axes and Gridlines).
#[derive(Clone, Copy, Default)]
struct Grid {
    /// The value axis's major and minor gridlines.
    val: bool,
    val_minor: bool,
    /// Lines across the categories: at their edges (major) and their middles (minor); at the
    /// ticks of a scatter chart's X axis.
    cat: bool,
    cat_minor: bool,
    /// Axes left out: their labels and line go and the plot takes their room.
    no_cat: bool,
    no_val: bool,
}

impl Grid {
    fn of(chart: &Chart) -> Self {
        let e = &chart.elements;
        Grid {
            val: chart.gridlines,
            val_minor: e.minor_val_gridlines,
            cat: e.cat_gridlines,
            cat_minor: e.minor_cat_gridlines,
            no_cat: e.hide_cat_axis,
            no_val: e.hide_val_axis,
        }
    }

    /// The axes as they are, without the gridlines (a 3-D chart draws its own on its walls).
    fn bare(self) -> Self {
        Grid { val: false, val_minor: false, cat: false, cat_minor: false, ..self }
    }
}

/// A value axis's minor gridlines: five to each major step, the majors left out.
fn minor_ticks(s: &Scale) -> Vec<f64> {
    let step = s.step / 5.0;
    if !(step.is_finite() && step > 0.0) || (s.max - s.min) / step > 2000.0 {
        return vec![];
    }
    let first = (s.min / step).ceil() as i64;
    let last = (s.max / step).floor() as i64;
    (first..=last).filter(|k| k.rem_euclid(5) != 0).map(|k| k as f64 * step).collect()
}

/// A label saying what `t` says, in the order the series' name, the category's, the value, the
/// share (pie slices), joined by `sep`.
fn label_text(t: LabelText, series: &str, category: &str, value: &str, percent: Option<&str>, sep: &str) -> String {
    let parts = [(t.series, series), (t.category, category), (t.value, value), (t.percent, percent.unwrap_or(""))];
    parts.iter().filter(|(on, s)| *on && !s.is_empty()).map(|(_, s)| *s).collect::<Vec<_>>().join(sep)
}

/// Draws gridlines, axis labels and the category axis line; returns the plot rectangle.
fn frame(ctx: &mut Ctx, area: Bx, cat: CatAxis, y: &ValAxis, y2: Option<&ValAxis>, horizontal: bool, g: Grid) -> Bx {
    let ls = LABEL_SIZE;
    let lh = ls * 1.4;
    let ylabels = if g.no_val { vec![] } else { y.labels() };
    let maxw = |ctx: &Ctx, l: &[(f64, String)]| l.iter().map(|(_, t)| ctx.tw(t, ls)).fold(0.0f32, f32::max);
    if !horizontal {
        let left = if g.no_val { 4.0 } else { (maxw(ctx, &ylabels) + 6.0).min(area.w * 0.4) };
        let y2labels = y2.map(|a| a.labels());
        let right = match (&y2labels, &cat) {
            (Some(l), _) => (maxw(ctx, l) + 6.0).min(area.w * 0.3),
            (None, CatAxis::Numeric(s, f)) if !g.no_cat => (ctx.tw(&f.format(s.max, Some(s.step)), ls) / 2.0).min(area.w * 0.2),
            _ => 4.0,
        };
        let below = if g.no_cat { lh * 0.5 } else { lh * 1.5 + 4.0 + CAT_LABEL_GAP - 3.0 };
        let plot = Bx { x: area.x + left, y: area.y + lh * 0.5, w: area.w - left - right, h: area.h - below };
        if !plot.ok() {
            return plot;
        }
        if let Some(st) = ctx.style {
            ctx.paint(plot, &st.plot, BLACK);
        }
        let at = |v: f64| plot.bottom() - y.scale.frac(v) * plot.h;
        if g.val_minor {
            for v in minor_ticks(&y.scale) {
                ctx.hline(plot.x, plot.right(), at(v), MINOR_GRID, 0.75);
            }
        }
        if g.val {
            for v in y.scale.ticks() {
                ctx.hline(plot.x, plot.right(), at(v), GRID, 0.75);
            }
        }
        for (v, t) in &ylabels {
            ctx.text(plot.x - 4.0, at(*v), t, ls, TEXT, HAlign::Right, VAlign::Middle);
        }
        if let (Some(a), Some(l)) = (y2, &y2labels) {
            for (v, t) in l {
                let yy = plot.bottom() - a.scale.frac(*v) * plot.h;
                ctx.text(plot.right() + 4.0, yy, t, ls, TEXT, HAlign::Left, VAlign::Middle);
            }
        }
        match cat {
            CatAxis::Labels(l) => {
                let n = l.len().max(1);
                let cw = plot.w / n as f32;
                if (g.cat || g.cat_minor) && n <= MAX_GRID_CATS {
                    for j in 0..=n {
                        let xx = plot.x + j as f32 * cw;
                        if g.cat_minor && j < n {
                            ctx.vline(xx + cw / 2.0, plot.y, plot.bottom(), MINOR_GRID, 0.75);
                        }
                        if g.cat {
                            ctx.vline(xx, plot.y, plot.bottom(), GRID, 0.75);
                        }
                    }
                }
                if !g.no_cat {
                    let lw = l.iter().take(2000).map(|t| ctx.tw(t, ls)).fold(0.0f32, f32::max);
                    let k = (((lw + 6.0) / cw.max(1e-3)).ceil() as usize).clamp(1, n);
                    let caps = ctx.style.is_some_and(|st| st.category_caps.is_some());
                    for (j, t) in l.iter().enumerate().step_by(k) {
                        let t = if caps { t.to_uppercase() } else { t.clone() };
                        let t = ctx.fit(&t, ls, cw * k as f32 - 2.0);
                        ctx.text(plot.x + (j as f32 + 0.5) * cw, plot.bottom() + CAT_LABEL_GAP, &t, ls, TEXT, HAlign::Center, VAlign::Top);
                    }
                }
            }
            CatAxis::Numeric(s, f) => {
                let ticks = s.ticks();
                let xat = |v: f64| plot.x + s.frac(v) * plot.w;
                if g.cat_minor {
                    for v in minor_ticks(&s) {
                        ctx.vline(xat(v), plot.y, plot.bottom(), MINOR_GRID, 0.75);
                    }
                }
                if g.cat {
                    for v in &ticks {
                        ctx.vline(xat(*v), plot.y, plot.bottom(), GRID, 0.75);
                    }
                }
                if !g.no_cat {
                    let labels: Vec<String> = ticks.iter().map(|v| f.format(*v, Some(s.step))).collect();
                    let lw = labels.iter().map(|t| ctx.tw(t, ls)).fold(0.0f32, f32::max);
                    let slot = plot.w / ticks.len().max(1) as f32;
                    let k = (((lw + 6.0) / slot.max(1e-3)).ceil() as usize).max(1);
                    for (v, t) in ticks.iter().zip(&labels).step_by(k) {
                        ctx.text(xat(*v), plot.bottom() + CAT_LABEL_GAP, t, ls, TEXT, HAlign::Center, VAlign::Top);
                    }
                }
                // the value axis's line, where X is 0
                if !g.no_val && s.min <= 0.0 && s.max >= 0.0 {
                    ctx.vline(xat(0.0), plot.y, plot.bottom(), AXIS, 0.75);
                }
            }
        }
        // the category axis's line, where the values are 0
        if !g.no_cat {
            let zero = if y.scale.min <= 0.0 && y.scale.max >= 0.0 { 0.0 } else { y.scale.min };
            ctx.hline(plot.x, plot.right(), at(zero), AXIS, 0.75);
        }
        plot
    } else {
        let labels: Vec<String> = match cat {
            CatAxis::Labels(l) if !g.no_cat => l.to_vec(),
            _ => vec![],
        };
        let n = match cat {
            CatAxis::Labels(l) => l.len().max(1),
            CatAxis::Numeric(..) => 1,
        };
        let lw = if g.no_cat { 2.0 } else { (labels.iter().take(2000).map(|t| ctx.tw(t, ls)).fold(0.0f32, f32::max) + 6.0).min(area.w * 0.35) };
        let right = ylabels.last().map(|(_, t)| ctx.tw(t, ls) / 2.0).unwrap_or(0.0).min(area.w * 0.2) + 2.0;
        let below = if g.no_val { 4.0 } else { lh + 6.0 };
        let plot = Bx { x: area.x + lw, y: area.y + 2.0, w: area.w - lw - right, h: area.h - below };
        if !plot.ok() {
            return plot;
        }
        let at = |v: f64| plot.x + y.scale.frac(v) * plot.w;
        if g.val_minor {
            for v in minor_ticks(&y.scale) {
                ctx.vline(at(v), plot.y, plot.bottom(), MINOR_GRID, 0.75);
            }
        }
        if g.val {
            for v in y.scale.ticks() {
                ctx.vline(at(v), plot.y, plot.bottom(), GRID, 0.75);
            }
        }
        let ch = plot.h / n as f32;
        if (g.cat || g.cat_minor) && n <= MAX_GRID_CATS {
            for j in 0..=n {
                let yy = plot.bottom() - j as f32 * ch;
                if g.cat_minor && j < n {
                    ctx.hline(plot.x, plot.right(), yy - ch / 2.0, MINOR_GRID, 0.75);
                }
                if g.cat {
                    ctx.hline(plot.x, plot.right(), yy, GRID, 0.75);
                }
            }
        }
        let vw = maxw(ctx, &ylabels);
        let slot = plot.w / ylabels.len().max(1) as f32;
        let k = (((vw + 6.0) / slot.max(1e-3)).ceil() as usize).max(1);
        for (i, (v, t)) in ylabels.iter().enumerate() {
            if i % k == 0 {
                ctx.text(at(*v), plot.bottom() + CAT_LABEL_GAP, t, ls, TEXT, HAlign::Center, VAlign::Top);
            }
        }
        if !g.no_cat {
            let zero = if y.scale.min <= 0.0 && y.scale.max >= 0.0 { 0.0 } else { y.scale.min };
            ctx.vline(at(zero), plot.y, plot.bottom(), AXIS, 0.75);
        }
        let k = ((lh / ch.max(1e-3)).ceil() as usize).clamp(1, n);
        for (j, t) in labels.iter().enumerate().step_by(k) {
            let t = ctx.fit(t, ls, lw - 6.0);
            ctx.text(plot.x - 4.0, plot.bottom() - (j as f32 + 0.5) * ch, &t, ls, TEXT, HAlign::Right, VAlign::Middle);
        }
        plot
    }
}

// ---------------------------------------------------------------- helpers

/// Reduces a long polyline to first/min/max/last per pixel column.
fn decimate(pts: Vec<[f32; 2]>, max: usize) -> Vec<[f32; 2]> {
    if pts.len() <= max {
        return pts;
    }
    let mut out = Vec::with_capacity(max * 2);
    let mut i = 0;
    while let Some(p) = pts.get(i) {
        let col = p[0].floor();
        let (start, mut mn, mut mx) = (i, i, i);
        let mut k = i + 1;
        while let Some(q) = pts.get(k) {
            if q[0].floor() != col {
                break;
            }
            if pts.get(mn).is_some_and(|m| q[1] < m[1]) {
                mn = k;
            }
            if pts.get(mx).is_some_and(|m| q[1] > m[1]) {
                mx = k;
            }
            k += 1;
        }
        let mut idx = [start, mn, mx, k - 1];
        idx.sort_unstable();
        let mut last = usize::MAX;
        for ix in idx {
            if ix != last
                && let Some(q) = pts.get(ix)
            {
                out.push(*q);
                last = ix;
            }
        }
        i = k;
    }
    if out.len() > max * 4 {
        let step = out.len() / (max * 2) + 1;
        let last = out.last().copied();
        let mut s: Vec<[f32; 2]> = out.into_iter().step_by(step).collect();
        if let Some(l) = last
            && s.last() != Some(&l)
        {
            s.push(l);
        }
        return s;
    }
    out
}

/// Draws a polyline split at gaps (`None`), decimated when long. With `smooth` (the plot box),
/// each run is interpolated with a Catmull-Rom spline so the line curves through the data points.
fn draw_runs(ctx: &mut Ctx, pts: &[Option<[f32; 2]>], color: Rgba, width: f32, smooth: Option<Bx>) {
    let mut run: Vec<[f32; 2]> = Vec::new();
    for p in pts.iter().chain(std::iter::once(&None)) {
        match p {
            Some(p) => run.push(*p),
            None => {
                if run.len() >= 2 {
                    let r = decimate(std::mem::take(&mut run), MAX_LINE_PTS);
                    let r = match smooth {
                        Some(plot) => smooth_run(r, plot),
                        None => r,
                    };
                    ctx.line(r, color, width);
                } else {
                    run.clear();
                }
            }
        }
    }
}

/// Catmull-Rom spline through `pts`: every segment gets the same number of sub-segments (up to
/// 100), chosen so the whole curve stays within `MAX_LINE_PTS * 2` points. Endpoints are
/// duplicated so the curve passes through the first and last points, and the curve is clamped
/// to the `plot` box (a spline overshoots its data near sharp turns).
fn smooth_run(pts: Vec<[f32; 2]>, plot: Bx) -> Vec<[f32; 2]> {
    let n = pts.len();
    if n < 3 {
        return pts;
    }
    let cap = MAX_LINE_PTS * 2;
    let steps = (cap / n).clamp(1, 100);
    let mut out: Vec<[f32; 2]> = Vec::with_capacity(n.saturating_mul(steps).min(cap) + 1);
    let at = |i: isize| -> [f32; 2] {
        let j = i.clamp(0, n as isize - 1) as usize;
        pts.get(j).copied().unwrap_or([0.0, 0.0])
    };
    let (x0, x1) = (plot.x.min(plot.right()), plot.x.max(plot.right()));
    let (y0, y1) = (plot.y.min(plot.bottom()), plot.y.max(plot.bottom()));
    let clip = |p: [f32; 2]| [p[0].clamp(x0, x1), p[1].clamp(y0, y1)];
    for i in 0..n.saturating_sub(1) {
        let (p0, p1, p2, p3) = (at(i as isize - 1), at(i as isize), at(i as isize + 1), at(i as isize + 2));
        for s in 0..steps {
            let t = s as f32 / steps as f32;
            out.push(clip(catmull(p0, p1, p2, p3, t)));
        }
    }
    out.push(clip(at(n as isize - 1)));
    out
}

/// One Catmull-Rom point between `p1` and `p2` (uniform parameterisation), tangent-scaled by 0.5.
fn catmull(p0: [f32; 2], p1: [f32; 2], p2: [f32; 2], p3: [f32; 2], t: f32) -> [f32; 2] {
    let t2 = t * t;
    let t3 = t2 * t;
    let mut o = [0.0f32; 2];
    for k in 0..2 {
        let (a0, a1, a2, a3) = (p0[k], p1[k], p2[k], p3[k]);
        o[k] = 0.5 * ((2.0 * a1) + (-a0 + a2) * t + (2.0 * a0 - 5.0 * a1 + 4.0 * a2 - a3) * t2 + (-a0 + 3.0 * a1 - 3.0 * a2 + a3) * t3);
    }
    o
}

fn draw_markers(ctx: &mut Ctx, pts: impl Iterator<Item = [f32; 2]>, r: f32, color: Rgba) {
    let mut seen = HashSet::new();
    for p in pts {
        if seen.len() >= MAX_MARKERS {
            break;
        }
        if seen.insert(((p[0] * 0.5) as i32, (p[1] * 0.5) as i32)) {
            ctx.out.push(Prim::Circle { cx: p[0], cy: p[1], r, fill: Some(color), stroke: Some((WHITE, 0.75)) });
        }
    }
}

fn value_range(it: impl Iterator<Item = f64>) -> Option<(f64, f64)> {
    it.fold(None, |acc, v| match acc {
        None => Some((v, v)),
        Some((a, b)) => Some((a.min(v), b.max(v))),
    })
}

// ---------------------------------------------------------------- trendlines

/// Samples along a regression trendline.
const TREND_STEPS: usize = 200;

/// Whether series drawn as `k` show trendlines (Excel offers them on 2-D unstacked column,
/// bar, line, area, scatter and bubble charts).
pub fn supports_trendline(k: ChartKind) -> bool {
    use ChartKind::*;
    matches!(k, ColumnClustered | BarClustered | Line | LineMarkers | Area | Scatter | ScatterLines | Bubble)
}

/// A forecast distance as drawn: finite, non-negative and bounded.
fn forecast(v: f64) -> f64 {
    if v.is_finite() && v > 0.0 { v.min(1e9) } else { 0.0 }
}

/// Longest forward forecast among `s`'s regression trendlines.
fn max_forward(s: &SeriesData) -> f64 {
    s.trendlines.iter().filter(|t| !matches!(t.spec.kind, TrendlineKind::MovingAverage(_))).map(|t| forecast(t.spec.forward)).fold(0.0, f64::max)
}

/// Longest backward forecast among `s`'s regression trendlines.
fn max_backward(s: &SeriesData) -> f64 {
    s.trendlines.iter().filter(|t| !matches!(t.spec.kind, TrendlineKind::MovingAverage(_))).map(|t| forecast(t.spec.backward)).fold(0.0, f64::max)
}

/// Liang–Barsky: the part of segment `a`–`b` inside the unit square, with whether the start and
/// end were cut.
#[allow(clippy::type_complexity)]
fn clip_segment(a: (f64, f64), b: (f64, f64)) -> Option<((f64, f64), (f64, f64), bool, bool)> {
    let (dx, dy) = (b.0 - a.0, b.1 - a.1);
    let (mut t0, mut t1) = (0.0f64, 1.0f64);
    for (p, q) in [(-dx, a.0), (dx, 1.0 - a.0), (-dy, a.1), (dy, 1.0 - a.1)] {
        if p == 0.0 {
            if q < 0.0 {
                return None;
            }
        } else {
            let r = q / p;
            if p < 0.0 {
                if r > t1 {
                    return None;
                }
                t0 = t0.max(r);
            } else {
                if r < t0 {
                    return None;
                }
                t1 = t1.min(r);
            }
        }
    }
    Some(((a.0 + t0 * dx, a.1 + t0 * dy), (a.0 + t1 * dx, a.1 + t1 * dy), t0 > 0.0, t1 < 1.0))
}

/// Clips a sampled polyline in plot fractions (`None` = gap) to the plot box (the unit
/// square), returning the visible runs.
fn clip_unit(pts: &[Option<(f64, f64)>]) -> Vec<Vec<(f64, f64)>> {
    const LIM: f64 = 1e9;
    let ok = |p: &(f64, f64)| p.0.is_finite() && p.1.is_finite();
    let cl = |p: (f64, f64)| (p.0.clamp(-LIM, LIM), p.1.clamp(-LIM, LIM));
    let mut runs: Vec<Vec<(f64, f64)>> = Vec::new();
    let mut run: Vec<(f64, f64)> = Vec::new();
    for w in pts.windows(2) {
        let seg = match w {
            [Some(a), Some(b)] if ok(a) && ok(b) => clip_segment(cl(*a), cl(*b)),
            _ => None,
        };
        match seg {
            Some((p, q, cut_start, cut_end)) => {
                if cut_start || run.is_empty() {
                    if run.len() >= 2 {
                        runs.push(std::mem::take(&mut run));
                    }
                    run.clear();
                    run.push(p);
                }
                run.push(q);
                if cut_end {
                    runs.push(std::mem::take(&mut run));
                }
            }
            None => {
                if run.len() >= 2 {
                    runs.push(std::mem::take(&mut run));
                }
                run.clear();
            }
        }
    }
    if run.len() >= 2 {
        runs.push(run);
    }
    runs
}

/// Draws `s`'s trendlines fitted to its plotted points `pts` (data x, y). `frac` maps data to
/// fractions of the plot box (0..1 inside, along the x and y axes), `px` fractions to pixels.
/// Equation and R² label lines are appended to `labels`. Degenerate fits draw nothing.
fn draw_trendlines(
    ctx: &mut Ctx,
    s: &SeriesData,
    pts: &[(f64, f64)],
    frac: &dyn Fn(f64, f64) -> (f64, f64),
    px: &dyn Fn((f64, f64)) -> [f32; 2],
    labels: &mut Vec<String>,
) {
    let Some((xlo, xhi)) = value_range(pts.iter().map(|p| p.0)) else { return };
    for t in s.trendlines.iter().take(MAX_TRENDLINES) {
        let spec = &t.spec;
        let samples: Vec<Option<(f64, f64)>> = match spec.kind {
            TrendlineKind::MovingAverage(p) => moving_average(pts, p).into_iter().map(|(x, y)| Some(frac(x, y))).collect(),
            _ => {
                let Some(fit) = fit_trendline(spec, pts) else { continue };
                let (lo, hi) = (xlo - forecast(spec.backward), xhi + forecast(spec.forward));
                if spec.display_equation {
                    labels.push(fit.equation());
                }
                if spec.display_r_squared {
                    labels.push(fit.r_squared());
                }
                (0..=TREND_STEPS)
                    .map(|i| {
                        let x = lo + (hi - lo) * i as f64 / TREND_STEPS as f64;
                        fit.eval(x).map(|y| frac(x, y))
                    })
                    .collect()
            }
        };
        let width = spec.width.filter(|w| w.is_finite()).unwrap_or(1.5).clamp(0.25, 12.0);
        let dash = spec.dash != Some(LineDash::Solid);
        for run in clip_unit(&samples) {
            let line = decimate(run.into_iter().map(px).collect(), MAX_LINE_PTS);
            if line.len() >= 2 {
                ctx.out.push(Prim::Line { pts: line, color: t.color, width, dash });
            }
        }
    }
}

/// Trendline equation / R² labels, stacked in the plot's top-right corner.
fn draw_trend_labels(ctx: &mut Ctx, plot: Bx, labels: &[String]) {
    let lh = LABEL_SIZE * 1.3;
    for (i, t) in labels.iter().enumerate() {
        let y = plot.y + 4.0 + i as f32 * lh;
        if y + lh > plot.bottom() {
            break;
        }
        let t = ctx.fit(t, LABEL_SIZE, plot.w - 8.0);
        ctx.text(plot.right() - 4.0, y, &t, LABEL_SIZE, TEXT, HAlign::Right, VAlign::Top);
    }
}

// ---------------------------------------------------------------- cartesian (column/bar/line/area/combo)

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Fam {
    Col,
    ColStack,
    Col100,
    Line,
    LineStack,
    /// Lines stacked to 100% of each category's total.
    Line100,
    Area,
    AreaStack,
    Area100,
}

fn family(k: ChartKind) -> Fam {
    use ChartKind::*;
    match k {
        ColumnStacked | BarStacked => Fam::ColStack,
        ColumnStacked100 | BarStacked100 => Fam::Col100,
        Line | LineMarkers | Scatter | ScatterLines | Radar | Stock => Fam::Line,
        LineStacked | LineStackedMarkers => Fam::LineStack,
        LineStacked100 | LineStacked100Markers => Fam::Line100,
        Area => Fam::Area,
        AreaStacked => Fam::AreaStack,
        AreaStacked100 => Fam::Area100,
        _ => Fam::Col,
    }
}

fn is_col(f: Fam) -> bool {
    matches!(f, Fam::Col | Fam::ColStack | Fam::Col100)
}
fn is_line(f: Fam) -> bool {
    matches!(f, Fam::Line | Fam::LineStack | Fam::Line100)
}

fn is_area(f: Fam) -> bool {
    matches!(f, Fam::Area | Fam::AreaStack | Fam::Area100)
}

/// Stacked to 100% of each category's total.
fn is_100(f: Fam) -> bool {
    matches!(f, Fam::Col100 | Fam::Line100 | Fam::Area100)
}

fn cartesian(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx, horizontal: bool) {
    let ncat = data.categories.len().max(data.series.iter().map(|s| s.values.len()).max().unwrap_or(0)).min(MAX_CATS);
    if ncat == 0 {
        return;
    }
    let kinds: Vec<ChartKind> = data.series.iter().map(|s| if horizontal { chart.kind } else { plot_kind(chart, s) }).collect();
    let fams: Vec<Fam> = kinds.iter().map(|k| family(*k)).collect();
    let any_primary = data.series.iter().any(|s| !s.secondary);
    let sec: Vec<bool> = data.series.iter().map(|s| s.secondary && any_primary && !horizontal).collect();
    // 3-D: series in rows of their own are drawn apart; otherwise the plot keeps room behind it
    let depth = if chart.kind == ChartKind::Combo { Depth::Flat } else { chart.depth };
    if !horizontal && (depth == Depth::Rows || (depth == Depth::Raised && fams.iter().all(|f| is_line(*f)))) {
        return rows3d(ctx, chart, data, area, fams.first().copied().unwrap_or(Fam::Col));
    }
    let deep = depth != Depth::Flat;

    // Totals for 100% stacking, per axis.
    let mut totals = [vec![0.0f64; ncat], vec![0.0f64; ncat]];
    for (i, s) in data.series.iter().enumerate() {
        if fams.get(i).is_some_and(|f| is_100(*f)) {
            let a = sec.get(i).copied().unwrap_or(false) as usize;
            for (j, v) in s.values.iter().take(ncat).enumerate() {
                if let (Some(v), Some(t)) = (v, totals.get_mut(a).and_then(|t| t.get_mut(j))) {
                    *t += v.abs();
                }
            }
        }
    }
    // Stack accumulators keyed by (axis, family): positive and negative running sums.
    let mut stacks: Vec<((bool, Fam), Vec<f64>, Vec<f64>)> = Vec::new();
    let mut spans: Vec<Vec<Option<(f64, f64)>>> = Vec::with_capacity(data.series.len());
    for (i, s) in data.series.iter().enumerate() {
        let fam = fams.get(i).copied().unwrap_or(Fam::Col);
        let a = sec.get(i).copied().unwrap_or(false);
        let stacked = matches!(fam, Fam::ColStack | Fam::Col100 | Fam::LineStack | Fam::Line100 | Fam::AreaStack | Fam::Area100);
        let key = (a, fam);
        let si = if stacked {
            match stacks.iter().position(|(k, _, _)| *k == key) {
                Some(p) => Some(p),
                None => {
                    stacks.push((key, vec![0.0; ncat], vec![0.0; ncat]));
                    Some(stacks.len() - 1)
                }
            }
        } else {
            None
        };
        let mut row = Vec::with_capacity(ncat);
        for j in 0..ncat {
            let v = s.values.get(j).copied().flatten();
            let span = match (si.and_then(|p| stacks.get_mut(p)), fam) {
                (Some((_, pos, neg)), Fam::ColStack | Fam::Col100) => v.and_then(|v| {
                    let v = if fam == Fam::Col100 {
                        let t = totals.get(a as usize).and_then(|t| t.get(j)).copied().unwrap_or(0.0);
                        if t > 0.0 { v / t } else { 0.0 }
                    } else {
                        v
                    };
                    let acc = if v >= 0.0 { pos.get_mut(j) } else { neg.get_mut(j) }?;
                    let base = *acc;
                    *acc += v;
                    Some((base, *acc))
                }),
                (Some((_, pos, _)), _) => {
                    let v = v.unwrap_or(0.0);
                    let v = if is_100(fam) {
                        let t = totals.get(a as usize).and_then(|t| t.get(j)).copied().unwrap_or(0.0);
                        if t > 0.0 { v / t } else { 0.0 }
                    } else {
                        v
                    };
                    pos.get_mut(j).map(|acc| {
                        let base = *acc;
                        *acc = clean(*acc + v);
                        (base, *acc)
                    })
                }
                (None, _) => v.map(|v| (0.0, v)),
            };
            row.push(span);
        }
        spans.push(row);
    }

    // Axis scales: an upright value axis takes as many steps as it has room for.
    let room = if horizontal {
        f64::from(area.w / MIN_GRID_GAP_ACROSS)
    } else {
        let table = if chart.elements.data_table { LABEL_SIZE * 1.6 * (data.series.len() + 1) as f32 } else { 0.0 };
        value_room(area.h - LABEL_SIZE * 1.4 * 1.5 - 1.0 - CAT_LABEL_GAP - table)
    };
    let mut axes: Vec<(Scale, Fmt)> = Vec::new();
    for axis in [false, true] {
        let members: Vec<usize> = (0..data.series.len()).filter(|i| sec.get(*i).copied().unwrap_or(false) == axis).collect();
        if members.is_empty() {
            continue;
        }
        let force_zero = members.iter().any(|i| fams.get(*i).is_some_and(|f| !is_line(*f)));
        let all100 = members.iter().all(|i| fams.get(*i).is_some_and(|f| is_100(*f)));
        let range = value_range(members.iter().flat_map(|i| {
            let line = fams.get(*i).is_some_and(|f| is_line(*f));
            spans.get(*i).into_iter().flatten().flatten().flat_map(move |(b, t)| if line { vec![*t] } else { vec![*b, *t] })
        }));
        let (lo, hi) = range.unwrap_or((0.0, 0.0));
        let (scale, fmt) = if all100 {
            (fixed_scale(if lo < -1e-9 { -1.0 } else { 0.0 }, if hi > 1e-9 || lo >= -1e-9 { 1.0 } else { 0.0 }, 0.2), Fmt::new("0%"))
        } else {
            let code = members.first().and_then(|i| data.series.get(*i)).map(|s| s.number_format.as_str()).unwrap_or("General");
            (nice_scale_within(lo, hi, force_zero, room.floor()), Fmt::new(code))
        };
        axes.push((scale, fmt));
        if !axis && sec.iter().all(|s| !s) {
            break;
        }
    }
    let Some((s1, f1)) = axes.first() else { return };
    let y1 = ValAxis { scale: *s1, fmt: f1 };
    let y2 = axes.get(1).map(|(s, f)| ValAxis { scale: *s, fmt: f });
    // A forward forecast on a category axis adds empty category slots after the data.
    let fwd = data
        .series
        .iter()
        .enumerate()
        .filter(|(i, _)| kinds.get(*i).is_some_and(|k| supports_trendline(*k)))
        .map(|(_, s)| max_forward(s))
        .fold(0.0, f64::max);
    let ncat_slots = ncat.saturating_add(fwd.ceil() as usize).min(MAX_CATS.max(ncat));
    let labels: Vec<String> = (0..ncat_slots).map(|j| if j < ncat { cat_label(data, j) } else { String::new() }).collect();
    let table = !deep && chart.elements.data_table && ncat <= MAX_TABLE_CATS && data.series.len() <= MAX_TABLE_SERIES;
    let row_h = LABEL_SIZE * 1.6;
    let table_h = if table { row_h * (data.series.len() + 1) as f32 } else { 0.0 };
    let blank = vec![String::new(); if table { ncat_slots } else { 0 }];
    // the table takes the band the category labels would have had (a bar chart's go on beside
    // it, the table under its value labels), and its first column needs room for the series'
    // names: the plot starts right of them
    let frame_area = if table {
        let band = LABEL_SIZE * 1.4 + 4.0;
        let ylw = if horizontal {
            labels.iter().map(|t| ctx.tw(t, LABEL_SIZE)).fold(0.0f32, f32::max) + 6.0
        } else {
            y1.labels().iter().map(|(_, t)| ctx.tw(t, LABEL_SIZE)).fold(0.0f32, f32::max) + 6.0
        };
        let names = data.series.iter().map(|s| ctx.tw(&s.name, LABEL_SIZE)).fold(0.0f32, f32::max) + LABEL_SIZE * 0.7 + 12.0;
        let extra = (names.min(area.w * 0.3) - ylw).max(0.0);
        let freed = if horizontal { 0.0 } else { band };
        Bx { x: area.x + extra, w: area.w - extra, h: area.h - table_h - 2.0 + freed, ..area }
    } else {
        area
    };
    let d = if deep { depth_of(frame_area.w, 0.08) } else { [0.0, 0.0] };
    let frame_area = if deep { Bx { y: frame_area.y + d[1], w: frame_area.w - d[0], h: frame_area.h - d[1], ..frame_area } } else { frame_area };
    let plot = frame(
        ctx,
        frame_area,
        CatAxis::Labels(if table && !horizontal { &blank } else { &labels }),
        &y1,
        y2.as_ref(),
        horizontal,
        if deep { Grid::of(chart).bare() } else { Grid::of(chart) },
    );
    if !plot.ok() {
        return;
    }
    if deep {
        let ticks: Vec<f32> = y1
            .scale
            .ticks()
            .into_iter()
            .map(|v| if horizontal { plot.x + y1.scale.frac(v) * plot.w } else { plot.bottom() - y1.scale.frac(v) * plot.h })
            .collect();
        walls(ctx, plot, d, &ticks, horizontal, chart.gridlines);
    }
    // 3-D columns stand back from the front plane, in the middle of the plot's depth
    let (f0, dd) = ([d[0] * 0.25, d[1] * 0.25], [d[0] * 0.5, d[1] * 0.5]);
    let scale_of = |i: usize| -> Scale { if sec.get(i).copied().unwrap_or(false) { y2.as_ref().map(|a| a.scale).unwrap_or(*s1) } else { *s1 } };
    let cat_len = if horizontal { plot.h } else { plot.w };
    let cw = cat_len / ncat_slots as f32;
    let center = |j: usize| if horizontal { plot.bottom() - (j as f32 + 0.5) * cw } else { plot.x + (j as f32 + 0.5) * cw };
    let vpos = |v: f64, sc: &Scale| if horizontal { plot.x + sc.frac(v) * plot.w } else { plot.bottom() - sc.frac(v) * plot.h };

    // Column slots: each clustered series gets one, each stack group shares one.
    let mut slot_keys: Vec<(bool, Fam, usize)> = Vec::new();
    let mut slot_of = vec![0usize; data.series.len()];
    for (i, f) in fams.iter().enumerate() {
        if !is_col(*f) {
            continue;
        }
        let a = sec.get(i).copied().unwrap_or(false);
        let key = if *f == Fam::Col { (a, *f, i) } else { (a, *f, usize::MAX) };
        let k = match slot_keys.iter().position(|k| *k == key) {
            Some(p) => p,
            None => {
                slot_keys.push(key);
                slot_keys.len() - 1
            }
        };
        if let Some(s) = slot_of.get_mut(i) {
            *s = k;
        }
    }
    let nslots = slot_keys.len().max(1) as f32;
    // a category holds its columns, overlapping by `lap` of a column (apart when below 0), and
    // a gap of `gap` columns (Format Data Series' Gap Width and Series Overlap)
    let gap = f32::from(chart.elements.gap_width.unwrap_or(150).min(500)) / 100.0;
    let lap = f32::from(chart.elements.overlap.unwrap_or(0).clamp(-100, 100)) / 100.0;
    let bw = cw / (nslots - (nslots - 1.0) * lap + gap).max(0.01);
    let group = bw * (nslots - (nslots - 1.0) * lap);
    // the middle of slot `k`'s column, from the category's middle (up the plot when horizontal)
    let slot_mid = move |k: f32| -group / 2.0 + k * bw * (1.0 - lap) + bw / 2.0;
    let last_only = chart.elements.last_point_labels;
    let labels_on = chart.data_labels && (last_only || ncat <= MAX_LABELS);
    // a point's label as the chart's labels say it (on the last point only, for some layouts)
    let label_of = |s: &SeriesData, fmt: &Fmt, j: usize| -> Option<String> {
        let v = s.values.get(j).copied().flatten()?;
        if last_only && j + 1 != s.values.len().min(ncat) {
            return None;
        }
        Some(label_text(chart.elements.label_text, &s.name, &cat_label(data, j), &fmt.format(v, None), None, ", ")).filter(|t| !t.is_empty())
    };
    // Area labels wait until every area is filled, so a later (stacked) area can't paint over them.
    let mut area_labels = Vec::new();

    // Areas first, then columns, then lines on top.
    for pass in 0..3 {
        if pass == 2 && !horizontal && !deep {
            line_marks(ctx, chart, &fams, &sec, &spans, ncat, cw, &center, &|v| vpos(v, s1), plot);
        }
        for (i, s) in data.series.iter().enumerate() {
            let fam = fams.get(i).copied().unwrap_or(Fam::Col);
            let want = match pass {
                0 => is_area(fam),
                1 => is_col(fam),
                _ => is_line(fam),
            };
            if !want {
                continue;
            }
            let sc = scale_of(i);
            let fmt = Fmt::new(&s.number_format);
            let row = match spans.get(i) {
                Some(r) => r,
                None => continue,
            };
            match pass {
                0 => {
                    let mut top = Vec::with_capacity(ncat * 2);
                    let mut base = Vec::with_capacity(ncat);
                    for (j, sp) in row.iter().enumerate() {
                        let (b, t) = sp.unwrap_or((0.0, 0.0));
                        let c = center(j);
                        top.push([c, vpos(t, &sc)]);
                        base.push([c, vpos(b, &sc)]);
                        // Source values (not stack totals), just above the top edge like the line labels.
                        if labels_on
                            && sp.is_some()
                            && let Some(text) = label_of(s, &fmt, j)
                        {
                            area_labels.push((c, vpos(t, &sc) - 5.0, text, s.color));
                        }
                    }
                    if top.len() == 1 {
                        // A single point: give it some width.
                        if let (Some(t), Some(b)) = (top.first().copied(), base.first().copied()) {
                            top = vec![[t[0] - cw * 0.3, t[1]], [t[0] + cw * 0.3, t[1]]];
                            base = vec![[b[0] - cw * 0.3, b[1]], [b[0] + cw * 0.3, b[1]]];
                        }
                    }
                    let top = decimate(top, MAX_LINE_PTS * 2);
                    let base = decimate(base, MAX_LINE_PTS * 2);
                    if deep {
                        raised_area(ctx, &top, &base, [d[0] * 0.2, d[1] * 0.2], [d[0] * 0.6, d[1] * 0.6], s.color);
                        continue;
                    }
                    let mut poly = top;
                    poly.extend(base.into_iter().rev());
                    if poly.len() >= 3 {
                        // a style fills an area as it fills a pie slice
                        let (fill, stroke) = if ctx.style.is_some() { ctx.slice(s.color) } else { (s.color, None) };
                        ctx.out.push(Prim::Polygon { pts: poly, fill, stroke });
                    }
                }
                1 => {
                    let k = slot_of.get(i).copied().unwrap_or(0) as f32;
                    for (j, sp) in row.iter().enumerate() {
                        let Some((b, t)) = sp else { continue };
                        let (pb, pt) = (vpos(*b, &sc), vpos(*t, &sc));
                        let c = center(j);
                        let lo = pb.min(pt);
                        let len = (pb - pt).abs();
                        if horizontal {
                            let y = c - slot_mid(k) - bw / 2.0;
                            if deep {
                                block(ctx, lo + f0[0], y - f0[1], len, bw, dd, s.color);
                            } else {
                                ctx.column(lo, y, len, bw, s.color);
                            }
                        } else {
                            let x = c + slot_mid(k) - bw / 2.0;
                            if deep {
                                block(ctx, x + f0[0], lo - f0[1], bw, len, dd, s.color);
                            } else {
                                ctx.column(x, lo, bw, len, s.color);
                            }
                        }
                        if labels_on && let Some(t) = label_of(s, &fmt, j) {
                            let across = if horizontal { c - slot_mid(k) } else { c + slot_mid(k) };
                            let (x, y, color, ha, va) = bar_label(chart.elements.label_pos, fam != Fam::Col, horizontal, pb, pt, across, s.color);
                            ctx.label(x + f0[0], y - f0[1], &t, color, s.color, ha, va);
                        }
                    }
                }
                _ => {
                    let pts: Vec<Option<[f32; 2]>> = row.iter().enumerate().map(|(j, sp)| sp.map(|(_, t)| [center(j), vpos(t, &sc)])).collect();
                    // a style's line and markers, or the plain ones
                    let (line, width) = match ctx.style {
                        Some(st) => (ctx.shade(&st.line.color, s.color), st.line.width),
                        None => (s.color, 2.25),
                    };
                    if ctx.style.is_some_and(|st| st.effect == Effect::Glow) {
                        let glow = ctx.shade(&chart_style::GLOW, s.color);
                        draw_runs(ctx, &pts, glow, width + 5.0, s.smooth.then_some(plot));
                    }
                    draw_runs(ctx, &pts, line, width, s.smooth.then_some(plot));
                    // a double line: the middle of it left open
                    if ctx.style.is_some_and(|st| st.line_double) {
                        draw_runs(ctx, &pts, WHITE, width / 3.0, s.smooth.then_some(plot));
                    }
                    let k = kinds.get(i).copied().unwrap_or(ChartKind::Line);
                    // a style's markers, or the plain ones on a line-with-markers chart
                    let styled = cw >= 3.0 && ctx.styled_markers(pts.iter().flatten().copied(), s.color);
                    if !styled && matches!(k, ChartKind::LineMarkers | ChartKind::LineStackedMarkers | ChartKind::LineStacked100Markers) && cw >= 3.0
                    {
                        draw_markers(ctx, pts.iter().flatten().copied(), 3.5, s.color);
                    }
                    if labels_on {
                        for (j, p) in pts.iter().enumerate() {
                            if let (Some(p), Some(t)) = (p, label_of(s, &fmt, j)) {
                                point_label(ctx, chart.elements.label_pos, *p, &t, s.color);
                            }
                        }
                    }
                }
            }
        }
        for (x, y, t, color) in area_labels.drain(..) {
            ctx.label(x, y, &t, TEXT, color, HAlign::Center, VAlign::Bottom);
        }
    }
    // Error bars: each column's or point's value (a stack's top, an area's edge) plus and minus the
    // amount. A 3-D chart has none, nor trendlines.
    if let Some(e) = chart.elements.error_bars.filter(|_| !deep) {
        for (i, s) in data.series.iter().enumerate() {
            let fam = fams.get(i).copied().unwrap_or(Fam::Col);
            let sc = scale_of(i);
            let k = slot_of.get(i).copied().unwrap_or(0) as f32;
            let amounts = error_amounts(e, &s.values);
            for (j, sp) in spans.get(i).into_iter().flatten().enumerate().take(MAX_LABELS * 5) {
                let (Some((_, t)), Some(Some((mid, amt)))) = (sp, amounts.get(j)) else { continue };
                // standard deviation bars sit on the series' mean; the others on each value
                let at = if matches!(e, ErrorAmount::StdDev(_)) { *mid } else { *t };
                let across = if is_col(fam) { if horizontal { center(j) - slot_mid(k) } else { center(j) + slot_mid(k) } } else { center(j) };
                let cap = (bw * 0.5).clamp(2.0, 8.0);
                let (a, b) = (vpos(at - amt, &sc), vpos(at + amt, &sc));
                error_bar(ctx, horizontal, across, a, b, cap, plot);
            }
        }
    }
    if table {
        let keys = !chart.elements.data_table_no_keys;
        if horizontal {
            // under the value labels, the categories across the plot
            data_table(ctx, data, area.x, plot, plot.bottom() + LABEL_SIZE * 1.4 + 4.0, row_h, ncat, plot.w / ncat.max(1) as f32, keys);
        } else {
            data_table(ctx, data, area.x, plot, plot.bottom() + 2.0, row_h, ncat, cw, keys);
        }
    }
    // Trendlines on top: x is the category number 1..n.
    let mut trend_labels = Vec::new();
    for (i, s) in data.series.iter().enumerate() {
        if deep || s.trendlines.is_empty() || !kinds.get(i).is_some_and(|k| supports_trendline(*k)) {
            continue;
        }
        let sc = scale_of(i);
        let pts: Vec<(f64, f64)> = s.values.iter().take(ncat).enumerate().filter_map(|(j, v)| v.map(|v| ((j + 1) as f64, v))).collect();
        let frac = |x: f64, y: f64| ((x - 0.5) / ncat_slots as f64, (y - sc.min) / (sc.max - sc.min));
        let px = |(fx, fy): (f64, f64)| {
            if horizontal {
                [plot.x + fy as f32 * plot.w, plot.bottom() - fx as f32 * plot.h]
            } else {
                [plot.x + fx as f32 * plot.w, plot.bottom() - fy as f32 * plot.h]
            }
        };
        draw_trendlines(ctx, s, &pts, &frac, &px, &mut trend_labels);
    }
    draw_trend_labels(ctx, plot, &trend_labels);
}

// ---------------------------------------------------------------- 3-D

/// `c` darkened to `k` of its brightness (the shaded faces of 3-D shapes).
fn shade(c: Rgba, k: f32) -> Rgba {
    let f = |v: u8| (v as f32 * k).clamp(0.0, 255.0) as u8;
    [f(c[0]), f(c[1]), f(c[2]), c[3]]
}

/// `c` lightened by `k` towards white.
fn tint(c: Rgba, k: f32) -> Rgba {
    let f = |v: u8| (v as f32 + (255.0 - v as f32) * k).clamp(0.0, 255.0) as u8;
    [f(c[0]), f(c[1]), f(c[2]), c[3]]
}

/// How far back a 3-D plot goes, right and up, for a front plane `w` wide.
fn depth_of(w: f32, share: f32) -> [f32; 2] {
    let dx = (w * share).max(3.0);
    [dx, dx * 0.65]
}

/// A box seen from the front at (`x`, `y`), `w`×`h`, its top and right side going back by `d`.
fn block(ctx: &mut Ctx, x: f32, y: f32, w: f32, h: f32, d: [f32; 2], fill: Rgba) {
    let [dx, dy] = d;
    ctx.out.push(Prim::Polygon {
        pts: vec![[x + w, y], [x + w + dx, y - dy], [x + w + dx, y + h - dy], [x + w, y + h]],
        fill: shade(fill, 0.62),
        stroke: None,
    });
    ctx.out.push(Prim::Polygon { pts: vec![[x, y], [x + dx, y - dy], [x + w + dx, y - dy], [x + w, y]], fill: shade(fill, 0.8), stroke: None });
    ctx.rect(x, y, w, h, fill, None);
}

/// An area given depth: its top as a sloping roof, its right end, then its face, all `off` back
/// from the front plane and `d` deep.
fn raised_area(ctx: &mut Ctx, top: &[[f32; 2]], base: &[[f32; 2]], off: [f32; 2], d: [f32; 2], fill: Rgba) {
    let mv = |p: [f32; 2], k: [f32; 2]| [p[0] + k[0], p[1] - k[1]];
    let top: Vec<[f32; 2]> = top.iter().map(|p| mv(*p, off)).collect();
    let base: Vec<[f32; 2]> = base.iter().map(|p| mv(*p, off)).collect();
    let roof = shade(fill, 0.8);
    for w in top.windows(2) {
        if let [a, b] = w {
            ctx.out.push(Prim::Polygon { pts: vec![*a, *b, mv(*b, d), mv(*a, d)], fill: roof, stroke: Some((roof, 0.5)) });
        }
    }
    if let (Some(t), Some(b)) = (top.last(), base.last()) {
        ctx.out.push(Prim::Polygon { pts: vec![*t, mv(*t, d), mv(*b, d), *b], fill: shade(fill, 0.62), stroke: None });
    }
    let mut poly = top;
    poly.extend(base.into_iter().rev());
    if poly.len() >= 3 {
        ctx.out.push(Prim::Polygon { pts: poly, fill, stroke: None });
    }
}

/// The walls behind a 3-D plot whose front plane is `plot`: each gridline (at `ticks` along the
/// value axis) runs back along the floor or side wall and across the back wall; then the walls'
/// edges.
fn walls(ctx: &mut Ctx, plot: Bx, d: [f32; 2], ticks: &[f32], horizontal: bool, grid: bool) {
    let [dx, dy] = d;
    if grid {
        for t in ticks {
            let pts = if horizontal {
                vec![[*t, plot.bottom()], [t + dx, plot.bottom() - dy], [t + dx, plot.y - dy]]
            } else {
                vec![[plot.x, *t], [plot.x + dx, t - dy], [plot.right() + dx, t - dy]]
            };
            ctx.line(pts, GRID, 0.75);
        }
    }
    ctx.line(vec![[plot.x + dx, plot.y - dy], [plot.x + dx, plot.bottom() - dy], [plot.right() + dx, plot.bottom() - dy]], GRID, 0.75);
    ctx.line(vec![[plot.x, plot.bottom()], [plot.x + dx, plot.bottom() - dy]], GRID, 0.75);
    ctx.line(vec![[plot.right(), plot.bottom()], [plot.right() + dx, plot.bottom() - dy]], GRID, 0.75);
}

/// 3-D Column, 3-D Line and 3-D Area: each series in a row of its own going back from the front
/// (the first in front), values up the left, the series' names along the floor's right edge.
fn rows3d(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx, fam: Fam) {
    let ncat = data.categories.len().max(data.series.iter().map(|s| s.values.len()).max().unwrap_or(0)).min(MAX_CATS);
    let nser = data.series.len();
    if ncat == 0 || nser == 0 {
        return;
    }
    let Some((lo, hi)) = value_range(data.series.iter().flat_map(|s| s.values.iter().take(ncat).flatten().copied())) else { return };
    let scale = nice_scale(lo, hi, !is_line(fam));
    let fmt = Fmt::new(data.series.first().map(|s| s.number_format.as_str()).unwrap_or("General"));
    let labels: Vec<String> = (0..ncat).map(|j| cat_label(data, j)).collect();
    let names_w = data.series.iter().map(|s| ctx.tw(&s.name, LABEL_SIZE)).fold(0.0f32, f32::max).min(area.w * 0.25) + 6.0;
    let d = depth_of(area.w, (0.09 * nser as f32).min(0.36));
    let d = [d[0].min(area.w * 0.36), d[1].min(area.h * 0.4)];
    let room = Bx { y: area.y + d[1], w: area.w - d[0] - names_w, h: area.h - d[1], ..area };
    if !room.ok() {
        return;
    }
    let plot = frame(ctx, room, CatAxis::Labels(&labels), &ValAxis { scale, fmt: &fmt }, None, false, Grid::of(chart).bare());
    if !plot.ok() {
        return;
    }
    let ticks: Vec<f32> = scale.ticks().into_iter().map(|v| plot.bottom() - scale.frac(v) * plot.h).collect();
    walls(ctx, plot, d, &ticks, false, chart.gridlines);
    let n = nser as f32;
    let row = [d[0] / n, d[1] / n];
    for (r, s) in data.series.iter().enumerate() {
        let k = r as f32 + 0.5;
        let t = ctx.fit(&s.name, LABEL_SIZE, names_w - 6.0);
        ctx.text(plot.right() + k * row[0] + 4.0, plot.bottom() - k * row[1], &t, LABEL_SIZE, TEXT, HAlign::Left, VAlign::Middle);
    }
    let cw = plot.w / ncat as f32;
    let vy = |v: f64| plot.bottom() - scale.frac(v) * plot.h;
    let zero = if scale.min <= 0.0 && scale.max >= 0.0 { 0.0 } else { scale.min };
    let labels_on = chart.data_labels && ncat <= MAX_LABELS;
    // the back row first, so the rows in front cover it
    for (r, s) in data.series.iter().enumerate().rev() {
        let off = [row[0] * (r as f32 + 0.2), row[1] * (r as f32 + 0.2)];
        let dd = [row[0] * 0.6, row[1] * 0.6];
        let at = |j: usize, v: f64| [plot.x + (j as f32 + 0.5) * cw, vy(v)];
        if is_col(fam) {
            let bw = cw * 0.4;
            let f = Fmt::new(&s.number_format);
            for j in 0..ncat {
                let Some(v) = s.values.get(j).copied().flatten() else { continue };
                let [x, top] = at(j, v);
                let base = vy(zero);
                let (x, y) = (x - bw / 2.0 + off[0], top.min(base) - off[1]);
                block(ctx, x, y, bw, (base - top).abs(), dd, s.color);
                if labels_on {
                    ctx.text(x + bw / 2.0 + dd[0] / 2.0, y - dd[1] - 2.0, &f.format(v, None), LABEL_SIZE, TEXT, HAlign::Center, VAlign::Bottom);
                }
            }
        } else if is_line(fam) {
            let mv = |p: [f32; 2], k: [f32; 2]| [p[0] + k[0], p[1] - k[1]];
            let pts: Vec<Option<[f32; 2]>> = (0..ncat).map(|j| s.values.get(j).copied().flatten().map(|v| mv(at(j, v), off))).collect();
            let edge = shade(s.color, 0.7);
            for w in pts.windows(2) {
                if let [Some(a), Some(b)] = w {
                    ctx.out.push(Prim::Polygon { pts: vec![*a, *b, mv(*b, dd), mv(*a, dd)], fill: s.color, stroke: Some((edge, 0.75)) });
                }
            }
        } else {
            let mut top: Vec<[f32; 2]> = (0..ncat).map(|j| at(j, s.values.get(j).copied().flatten().unwrap_or(zero))).collect();
            let mut base: Vec<[f32; 2]> = (0..ncat).map(|j| at(j, zero)).collect();
            if let ([t], [b]) = (top.as_slice(), base.as_slice()) {
                // a single point: give it some width
                let (t, b) = (*t, *b);
                top = vec![[t[0] - cw * 0.3, t[1]], [t[0] + cw * 0.3, t[1]]];
                base = vec![[b[0] - cw * 0.3, b[1]], [b[0] + cw * 0.3, b[1]]];
            }
            raised_area(ctx, &top, &base, off, dd, s.color);
        }
    }
}

/// Where a column's label goes for the label position: its point, colour and alignment.
/// `base` and `end` are the column's ends along the value axis, `across` its middle.
fn bar_label(pos: LabelPos, stacked: bool, horizontal: bool, base: f32, end: f32, across: f32, fill: Rgba) -> (f32, f32, Rgba, HAlign, VAlign) {
    let pos = match pos {
        LabelPos::Center | LabelPos::InsideEnd | LabelPos::InsideBase | LabelPos::OutsideEnd => pos,
        _ if stacked => LabelPos::Center,
        _ => LabelPos::OutsideEnd,
    };
    let mid = (base + end) / 2.0;
    if horizontal {
        // the value grows to the right for a positive value
        let grows = end >= base;
        let (side, edge) = (if grows { 1.0 } else { -1.0 }, |inside: bool| if grows == inside { HAlign::Right } else { HAlign::Left });
        match pos {
            LabelPos::Center => (mid, across, on(fill), HAlign::Center, VAlign::Middle),
            LabelPos::InsideEnd => (end - 3.0 * side, across, on(fill), edge(true), VAlign::Middle),
            LabelPos::InsideBase => (base + 3.0 * side, across, on(fill), edge(false), VAlign::Middle),
            _ => (end + 3.0 * side, across, TEXT, edge(false), VAlign::Middle),
        }
    } else {
        // the value grows upwards (smaller y) for a positive value
        let up = end <= base;
        let side = if up { -1.0 } else { 1.0 };
        let (toward, away) = if up { (VAlign::Top, VAlign::Bottom) } else { (VAlign::Bottom, VAlign::Top) };
        match pos {
            LabelPos::Center => (across, mid, on(fill), HAlign::Center, VAlign::Middle),
            LabelPos::InsideEnd => (across, end - 2.0 * side, on(fill), HAlign::Center, toward),
            LabelPos::InsideBase => (across, base + 2.0 * side, on(fill), HAlign::Center, away),
            _ => (across, end + 2.0 * side, TEXT, HAlign::Center, away),
        }
    }
}

/// A point's label (lines, scatter) where the label position puts it; above by default.
fn point_label(ctx: &mut Ctx, pos: LabelPos, p: [f32; 2], t: &str, series: Rgba) {
    match pos {
        LabelPos::Center => ctx.label(p[0], p[1], t, TEXT, series, HAlign::Center, VAlign::Middle),
        LabelPos::Left => ctx.label(p[0] - 6.0, p[1], t, TEXT, series, HAlign::Right, VAlign::Middle),
        LabelPos::Right => ctx.label(p[0] + 6.0, p[1], t, TEXT, series, HAlign::Left, VAlign::Middle),
        LabelPos::Below => ctx.label(p[0], p[1] + 6.0, t, TEXT, series, HAlign::Center, VAlign::Top),
        _ => ctx.label(p[0], p[1] - 5.0, t, TEXT, series, HAlign::Center, VAlign::Bottom),
    }
}

/// Error amounts for each value: the middle the bar sits on (the series' mean for standard
/// deviations) and how far it reaches either side.
fn error_amounts(e: ErrorAmount, vals: &[Option<f64>]) -> Vec<Option<(f64, f64)>> {
    let xs: Vec<f64> = vals.iter().flatten().copied().filter(|v| v.is_finite()).collect();
    let n = xs.len() as f64;
    let mean = if n > 0.0 { xs.iter().sum::<f64>() / n } else { 0.0 };
    let sd = if n > 1.0 { (xs.iter().map(|v| (v - mean).powi(2)).sum::<f64>() / (n - 1.0)).sqrt() } else { 0.0 };
    vals.iter()
        .map(|v| {
            let v = v.filter(|v| v.is_finite())?;
            let amt = match e {
                ErrorAmount::StandardError => sd / n.max(1.0).sqrt(),
                ErrorAmount::Percent(p) => v.abs() * p.abs() / 100.0,
                ErrorAmount::StdDev(k) => sd * k.abs(),
                ErrorAmount::Fixed(f) => f.abs(),
            };
            amt.is_finite().then_some((mean, amt))
        })
        .collect()
}

/// One error bar from `a` to `b` along the value axis at `across`, with end caps, kept to the plot.
fn error_bar(ctx: &mut Ctx, horizontal: bool, across: f32, a: f32, b: f32, cap: f32, plot: Bx) {
    if !(a.is_finite() && b.is_finite() && across.is_finite()) {
        return;
    }
    if horizontal {
        let (a, b) = (a.clamp(plot.x, plot.right()), b.clamp(plot.x, plot.right()));
        ctx.hline(a, b, across, DARK_TEXT, 1.0);
        ctx.vline(a, across - cap / 2.0, across + cap / 2.0, DARK_TEXT, 1.0);
        ctx.vline(b, across - cap / 2.0, across + cap / 2.0, DARK_TEXT, 1.0);
    } else {
        let (a, b) = (a.clamp(plot.y, plot.bottom()), b.clamp(plot.y, plot.bottom()));
        ctx.vline(across, a, b, DARK_TEXT, 1.0);
        ctx.hline(across - cap / 2.0, across + cap / 2.0, a, DARK_TEXT, 1.0);
        ctx.hline(across - cap / 2.0, across + cap / 2.0, b, DARK_TEXT, 1.0);
    }
}

/// Up/down bars, high-low lines and drop lines of a (vertical) chart's primary line series.
#[allow(clippy::too_many_arguments)]
fn line_marks(
    ctx: &mut Ctx,
    chart: &Chart,
    fams: &[Fam],
    sec: &[bool],
    spans: &[Vec<Option<(f64, f64)>>],
    ncat: usize,
    cw: f32,
    center: &dyn Fn(usize) -> f32,
    vpos: &dyn Fn(f64) -> f32,
    plot: Bx,
) {
    let el = &chart.elements;
    if !(el.up_down_bars || el.high_low_lines || el.drop_lines) {
        return;
    }
    let lines: Vec<usize> =
        (0..spans.len()).filter(|i| fams.get(*i).is_some_and(|f| is_line(*f)) && !sec.get(*i).copied().unwrap_or(false)).collect();
    let areas: Vec<usize> = (0..spans.len()).filter(|i| fams.get(*i).is_some_and(|f| is_area(*f))).collect();
    for j in 0..ncat.min(MAX_CATS) {
        let top = |of: &[usize]| -> Vec<f64> {
            of.iter().filter_map(|i| spans.get(*i).and_then(|r| r.get(j)).copied().flatten().map(|(_, t)| t)).collect()
        };
        let vals = top(&lines);
        let (lo, hi) = (vals.iter().copied().fold(f64::INFINITY, f64::min), vals.iter().copied().fold(f64::NEG_INFINITY, f64::max));
        let x = center(j);
        if el.drop_lines {
            let reach = top(&lines).into_iter().chain(top(&areas)).fold(f64::NEG_INFINITY, f64::max);
            if reach.is_finite() {
                ctx.vline(x, vpos(reach).clamp(plot.y, plot.bottom()), plot.bottom(), DARK_TEXT, 0.75);
            }
        }
        if el.high_low_lines && vals.len() >= 2 && lo.is_finite() && hi.is_finite() {
            ctx.vline(x, vpos(hi).clamp(plot.y, plot.bottom()), vpos(lo).clamp(plot.y, plot.bottom()), STOCK, 1.0);
        }
        if el.up_down_bars && lines.len() >= 2 {
            let at = |i: usize| spans.get(i).and_then(|r| r.get(j)).copied().flatten().map(|(_, t)| t);
            if let (Some(first), Some(last)) = (lines.first().and_then(|i| at(*i)), lines.last().and_then(|i| at(*i))) {
                let (y0, y1) = (vpos(first).clamp(plot.y, plot.bottom()), vpos(last).clamp(plot.y, plot.bottom()));
                let bw = (cw * 0.4).max(1.0);
                // up (the last series higher) white, down dark
                let fill = if last >= first { WHITE } else { STOCK };
                ctx.rect(x - bw / 2.0, y0.min(y1), bw, (y0 - y1).abs().max(0.5), fill, Some((STOCK, 0.75)));
            }
        }
    }
}

/// The data table under a column, line or area chart: the categories in its first row, then a
/// row per series with its colour (unless `keys` is off) and its values, each under its category.
#[allow(clippy::too_many_arguments)]
fn data_table(ctx: &mut Ctx, data: &ChartData, left: f32, plot: Bx, top: f32, row_h: f32, ncat: usize, cw: f32, keys: bool) {
    let rows = data.series.len() + 1;
    let bottom = top + row_h * rows as f32;
    let right = plot.x + cw * ncat as f32;
    for k in 0..=rows {
        let y = top + row_h * k as f32;
        let from = if k == 0 { plot.x } else { left };
        ctx.hline(from, right, y, GRID, 0.75);
    }
    ctx.vline(left, top + row_h, bottom, GRID, 0.75);
    for j in 0..=ncat {
        ctx.vline(plot.x + cw * j as f32, top, bottom, GRID, 0.75);
    }
    for j in 0..ncat {
        let t = ctx.fit(&cat_label(data, j), LABEL_SIZE, cw - 3.0);
        ctx.text(plot.x + cw * (j as f32 + 0.5), top + row_h / 2.0, &t, LABEL_SIZE, TEXT, HAlign::Center, VAlign::Middle);
    }
    let key = LABEL_SIZE * 0.7;
    for (k, s) in data.series.iter().enumerate() {
        let yc = top + row_h * (k as f32 + 1.5);
        let indent = if keys {
            ctx.rect(left + 3.0, yc - key / 2.0, key, key, s.color, None);
            key + 6.0
        } else {
            3.0
        };
        let name = ctx.fit(&s.name, LABEL_SIZE, (plot.x - left - indent - 2.0).max(0.0));
        ctx.text(left + indent, yc, &name, LABEL_SIZE, TEXT, HAlign::Left, VAlign::Middle);
        let fmt = Fmt::new(&s.number_format);
        for j in 0..ncat {
            if let Some(v) = s.values.get(j).copied().flatten() {
                let t = ctx.fit(&fmt.format(v, None), LABEL_SIZE, cw - 3.0);
                ctx.text(plot.x + cw * (j as f32 + 0.5), yc, &t, LABEL_SIZE, TEXT, HAlign::Center, VAlign::Middle);
            }
        }
    }
}

// ---------------------------------------------------------------- scatter & bubble

fn scatter(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let pts_of = |s: &SeriesData| -> Vec<Option<(f64, f64)>> {
        s.values
            .iter()
            .enumerate()
            .map(|(j, v)| {
                let x = match &s.x {
                    Some(xs) => xs.get(j).copied().flatten(),
                    None => Some((j + 1) as f64),
                };
                match (x, v) {
                    (Some(x), Some(y)) => Some((x, *y)),
                    _ => None,
                }
            })
            .collect()
    };
    let all: Vec<Vec<Option<(f64, f64)>>> = data.series.iter().map(pts_of).collect();
    let Some((mut xlo, mut xhi)) = value_range(all.iter().flatten().flatten().map(|p| p.0)) else { return };
    // Forecasts extend the x axis to the trendline's ends.
    for (s, pts) in data.series.iter().zip(&all) {
        if let Some((lo, hi)) = value_range(pts.iter().flatten().map(|p| p.0)).filter(|_| !s.trendlines.is_empty()) {
            xlo = xlo.min(lo - max_backward(s));
            xhi = xhi.max(hi + max_forward(s));
        }
    }
    let Some((ylo, yhi)) = value_range(all.iter().flatten().flatten().map(|p| p.1)) else { return };
    let xs = nice_scale_within(xlo, xhi, false, f64::from(area.w / MIN_GRID_GAP_ACROSS).floor());
    let ys = nice_scale_within(ylo, yhi, false, value_room(area.h - LABEL_SIZE * 1.4 * 1.5 - 1.0 - CAT_LABEL_GAP).floor());
    let fx = Fmt::new("General");
    let fy = Fmt::new(data.series.first().map(|s| s.number_format.as_str()).unwrap_or("General"));
    let plot = frame(ctx, area, CatAxis::Numeric(xs, &fx), &ValAxis { scale: ys, fmt: &fy }, None, false, Grid::of(chart));
    if !plot.ok() {
        return;
    }
    let map = |(x, y): (f64, f64)| [plot.x + xs.frac(x) * plot.w, plot.bottom() - ys.frac(y) * plot.h];
    let bubble = chart.kind == ChartKind::Bubble;
    let rmax = plot.w.min(plot.h) * 0.12;
    for (s, pts) in data.series.iter().zip(&all) {
        let px: Vec<Option<[f32; 2]>> = pts.iter().map(|p| p.map(map)).collect();
        if bubble {
            let sizes = s.sizes.as_deref().unwrap_or(&[]);
            let smax = sizes.iter().flatten().fold(0.0f64, |a, b| a.max(*b));
            let mut bubbles: Vec<([f32; 2], f64)> = px
                .iter()
                .enumerate()
                .filter_map(|(j, p)| {
                    let sz = if sizes.is_empty() { Some(1.0) } else { sizes.get(j).copied().flatten() };
                    match (p, sz) {
                        (Some(p), Some(sz)) if sz > 0.0 => Some((*p, sz)),
                        _ => None,
                    }
                })
                .take(MAX_MARKERS)
                .collect();
            bubbles.sort_by(|a, b| b.1.total_cmp(&a.1));
            let smax = if sizes.is_empty() { 1.0 } else { smax };
            let fill = [s.color[0], s.color[1], s.color[2], 0xC0];
            let raised = chart.depth != Depth::Flat;
            for (p, sz) in bubbles {
                let r = ((sz / smax).sqrt() as f32 * rmax).max(1.0);
                if raised {
                    // a ball, lit from the top left
                    ctx.out.push(Prim::Circle { cx: p[0], cy: p[1], r, fill: Some(shade(s.color, 0.85)), stroke: None });
                    ctx.out.push(Prim::Circle { cx: p[0] - r * 0.2, cy: p[1] - r * 0.2, r: r * 0.7, fill: Some(s.color), stroke: None });
                    ctx.out.push(Prim::Circle {
                        cx: p[0] - r * 0.35,
                        cy: p[1] - r * 0.35,
                        r: r * 0.3,
                        fill: Some(tint(s.color, 0.35)),
                        stroke: None,
                    });
                } else {
                    ctx.out.push(Prim::Circle { cx: p[0], cy: p[1], r, fill: Some(fill), stroke: Some((WHITE, 0.75)) });
                }
            }
        } else {
            let lined = |k: ChartKind| matches!(k, ChartKind::ScatterLines | ChartKind::ScatterLinesNoMarkers);
            let lines = lined(chart.kind) || lined(s.kind);
            if lines {
                draw_runs(ctx, &px, s.color, 2.0, s.smooth.then_some(plot));
            }
            let markers = s.kind != ChartKind::ScatterLinesNoMarkers && chart.kind != ChartKind::ScatterLinesNoMarkers;
            if markers && (!lines || px.len() <= 500) && !ctx.styled_markers(px.iter().flatten().copied(), s.color) {
                draw_markers(ctx, px.iter().flatten().copied(), 3.5, s.color);
            }
        }
        if let Some(e) = chart.elements.error_bars.filter(|_| !bubble) {
            let ys_vals: Vec<Option<f64>> = pts.iter().map(|p| p.map(|(_, y)| y)).collect();
            for (p, a) in pts.iter().zip(error_amounts(e, &ys_vals)).take(MAX_LABELS * 5) {
                let (Some((x, y)), Some((mid, amt))) = (p, a) else { continue };
                let at = if matches!(e, ErrorAmount::StdDev(_)) { mid } else { *y };
                let [px, _] = map((*x, at));
                let (a, b) = (map((*x, at - amt))[1], map((*x, at + amt))[1]);
                error_bar(ctx, false, px, a, b, 4.0, plot);
            }
        }
        if chart.data_labels && pts.len() <= MAX_LABELS {
            let last = pts.len().saturating_sub(1);
            for (j, (p, raw)) in px.iter().zip(pts).enumerate() {
                if chart.elements.last_point_labels && j != last {
                    continue;
                }
                if let (Some(p), Some((x, y))) = (p, raw) {
                    // the X value is the point's category
                    let t = label_text(chart.elements.label_text, &s.name, &fx.format(*x, None), &fy.format(*y, None), None, ", ");
                    point_label(ctx, chart.elements.label_pos, *p, &t, s.color);
                }
            }
        }
    }
    let mut trend_labels = Vec::new();
    for (s, pts) in data.series.iter().zip(&all) {
        if s.trendlines.is_empty() {
            continue;
        }
        let pts: Vec<(f64, f64)> = pts.iter().flatten().copied().collect();
        let frac = |x: f64, y: f64| ((x - xs.min) / (xs.max - xs.min), (y - ys.min) / (ys.max - ys.min));
        let px = |(fx, fy): (f64, f64)| [plot.x + fx as f32 * plot.w, plot.bottom() - fy as f32 * plot.h];
        draw_trendlines(ctx, s, &pts, &frac, &px, &mut trend_labels);
    }
    draw_trend_labels(ctx, plot, &trend_labels);
}

// ---------------------------------------------------------------- histogram

/// The bins a histogram sorts a series' values into: each bin's label and count.
fn bins(s: &SeriesData) -> Option<(Vec<String>, Vec<usize>)> {
    let vals: Vec<f64> = s.values.iter().flatten().copied().collect();
    let (lo, hi) = value_range(vals.iter().copied())?;
    let n = vals.len();
    let span = hi - lo;
    let mut bins = ((n as f64).sqrt().ceil() as usize).clamp(1, 100);
    if !(span.is_finite() && span > 0.0) {
        bins = 1;
    }
    let width = if bins == 1 && span.partial_cmp(&0.0) != Some(std::cmp::Ordering::Greater) { 1.0 } else { span / bins as f64 };
    // Upper edge of each bin, exactly as shown in the labels below. Bins are right-inclusive (the first is closed on both sides), so a value
    // equal to an edge belongs to the bin that ends there.
    let edges: Vec<f64> = (0..bins).map(|b| if b + 1 == bins { hi.max(lo + b as f64 * width) } else { lo + (b + 1) as f64 * width }).collect();
    let mut counts = vec![0usize; bins];
    for v in &vals {
        let b = edges.partition_point(|e| e < v).min(bins - 1);
        if let Some(c) = counts.get_mut(b) {
            *c += 1;
        }
    }
    let f = Fmt::new(&s.number_format);
    let labels: Vec<String> = (0..bins)
        .map(|b| {
            let a = lo + b as f64 * width;
            let e = edges.get(b).copied().unwrap_or(a);
            let open = if b == 0 { '[' } else { '(' };
            format!("{open}{}, {}]", f.format(a, Some(width / 10.0)), f.format(e, Some(width / 10.0)))
        })
        .collect();
    Some((labels, counts))
}

fn histogram(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let Some(s) = data.series.first() else { return };
    let Some((labels, counts)) = bins(s) else { return };
    let bins = counts.len().max(1);
    let maxc = counts.iter().copied().max().unwrap_or(0) as f64;
    let ys = nice_scale(0.0, maxc, true);
    let fc = Fmt::new("General");
    let plot = frame(ctx, area, CatAxis::Labels(&labels), &ValAxis { scale: ys, fmt: &fc }, None, false, Grid::of(chart));
    if !plot.ok() {
        return;
    }
    let cw = plot.w / bins as f32;
    for (b, c) in counts.iter().enumerate() {
        let top = plot.bottom() - ys.frac(*c as f64) * plot.h;
        let x = plot.x + b as f32 * cw;
        ctx.rect(x, top, cw, plot.bottom() - top, s.color, Some((WHITE, 0.75)));
        if chart.data_labels {
            ctx.text(x + cw / 2.0, top - 2.0, &c.to_string(), LABEL_SIZE, TEXT, HAlign::Center, VAlign::Bottom);
        }
    }
}

/// Pareto: columns from the largest down (each category's total, or a histogram's bins when there
/// are no categories), with their running total in percent as a line on a second axis.
fn pareto(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let Some(s) = data.series.first() else { return };
    let mut items: Vec<(String, f64)> = if data.categories.is_empty() {
        let Some((labels, counts)) = bins(s) else { return };
        labels.into_iter().zip(counts.into_iter().map(|c| c as f64)).collect()
    } else {
        // each category once, its values added up
        let mut items: Vec<(String, f64)> = Vec::new();
        let mut at: HashMap<String, usize> = HashMap::new();
        for (j, v) in s.values.iter().enumerate().take(MAX_CATS) {
            let Some(v) = v else { continue };
            let label = cat_label(data, j);
            match at.get(&label).and_then(|i| items.get_mut(*i)) {
                Some(it) => it.1 = clean(it.1 + v.max(0.0)),
                None => {
                    at.insert(label.clone(), items.len());
                    items.push((label, v.max(0.0)));
                }
            }
        }
        items
    };
    if items.is_empty() {
        return;
    }
    items.sort_by(|a, b| b.1.total_cmp(&a.1));
    let total: f64 = items.iter().map(|(_, v)| *v).sum();
    let labels: Vec<String> = items.iter().map(|(l, _)| l.clone()).collect();
    let ys = nice_scale(0.0, items.first().map_or(0.0, |i| i.1), true);
    let f = Fmt::new(&s.number_format);
    let pct = Fmt::new("0%");
    let ys2 = fixed_scale(0.0, 1.0, 0.1);
    let y2 = ValAxis { scale: ys2, fmt: &pct };
    let plot = frame(ctx, area, CatAxis::Labels(&labels), &ValAxis { scale: ys, fmt: &f }, Some(&y2), false, Grid::of(chart));
    if !plot.ok() {
        return;
    }
    let n = items.len();
    let cw = plot.w / n as f32;
    let mut cum = 0.0f64;
    let mut pts = Vec::with_capacity(n);
    for (j, (_, v)) in items.iter().enumerate() {
        let top = plot.bottom() - ys.frac(*v) * plot.h;
        let x = plot.x + j as f32 * cw;
        ctx.rect(x, top, cw, plot.bottom() - top, s.color, Some((WHITE, 0.75)));
        if chart.data_labels && n <= MAX_LABELS {
            ctx.text(x + cw / 2.0, top - 2.0, &f.format(*v, None), LABEL_SIZE, TEXT, HAlign::Center, VAlign::Bottom);
        }
        cum += v;
        let share = if total > 0.0 { cum / total } else { 0.0 };
        pts.push(Some([x + cw / 2.0, plot.bottom() - ys2.frac(share) * plot.h]));
    }
    draw_runs(ctx, &pts, point_color(data, 1), 2.25, None);
}

// ---------------------------------------------------------------- waterfall

fn waterfall(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let Some(s) = data.series.first() else { return };
    let mut ncat = data.categories.len().max(s.values.len()).min(MAX_CATS);
    if ncat == 0 {
        return;
    }
    let mut labels: Vec<String> = (0..ncat).map(|j| cat_label(data, j)).collect();
    let mut vals: Vec<f64> = (0..ncat).map(|j| s.values.get(j).copied().flatten().unwrap_or(0.0)).collect();
    let mut is_total = vec![false; ncat];
    let last_total = labels.last().is_some_and(|l| l.to_lowercase().contains("total"));
    if let Some(t) = is_total.last_mut() {
        *t = last_total;
    }
    if !last_total {
        labels.push("Total".into());
        vals.push(0.0);
        is_total.push(true);
        ncat += 1;
    }
    let mut bars = Vec::with_capacity(ncat);
    let mut cum = 0.0f64;
    for (j, v) in vals.iter().enumerate() {
        if is_total.get(j).copied().unwrap_or(false) {
            let total = if last_total && j + 1 == ncat && *v != 0.0 { *v } else { cum };
            bars.push((0.0, total, 0u8));
            cum = total;
        } else {
            let top = clean(cum + v);
            bars.push((cum, top, if *v >= 0.0 { 1 } else { 2 }));
            cum = top;
        }
    }
    let (lo, hi) = value_range(bars.iter().flat_map(|(b, t, _)| [*b, *t])).unwrap_or((0.0, 0.0));
    let ys = nice_scale(lo, hi, true);
    let f = Fmt::new(&s.number_format);
    let plot = frame(ctx, area, CatAxis::Labels(&labels), &ValAxis { scale: ys, fmt: &f }, None, false, Grid::of(chart));
    if !plot.ok() {
        return;
    }
    let cw = plot.w / ncat as f32;
    let bw = cw / 1.5;
    let vy = |v: f64| plot.bottom() - ys.frac(v) * plot.h;
    for (j, (b, t, kind)) in bars.iter().enumerate() {
        let color = match kind {
            1 => UP,
            2 => DOWN,
            _ => s.color,
        };
        let x = plot.x + (j as f32 + 0.5) * cw - bw / 2.0;
        let (yb, yt) = (vy(*b), vy(*t));
        ctx.rect(x, yb.min(yt), bw, (yb - yt).abs().max(0.5), color, None);
        if j + 1 < bars.len() && ncat <= 500 {
            ctx.out.push(Prim::Line { pts: vec![[x + bw, yt], [x + cw, yt]], color: AXIS, width: 0.75, dash: true });
        }
        if chart.data_labels && ncat <= MAX_LABELS {
            let v = if *kind == 0 { *t } else { t - b };
            ctx.text(x + bw / 2.0, yb.min(yt) - 2.0, &f.format(v, None), LABEL_SIZE, TEXT, HAlign::Center, VAlign::Bottom);
        }
    }
}

// ---------------------------------------------------------------- funnel

fn funnel(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let Some(s) = data.series.first() else { return };
    let n = data.categories.len().max(s.values.len()).min(MAX_CATS);
    if n == 0 {
        return;
    }
    let vals: Vec<f64> = (0..n).map(|j| s.values.get(j).copied().flatten().unwrap_or(0.0).abs()).collect();
    let max = vals.iter().copied().fold(0.0f64, f64::max);
    if max.is_nan() || max <= 0.0 {
        return;
    }
    let labels: Vec<String> = (0..n).map(|j| cat_label(data, j)).collect();
    let lw = (labels.iter().take(2000).map(|t| ctx.tw(t, LABEL_SIZE)).fold(0.0f32, f32::max) + 8.0).min(area.w * 0.35);
    let plot = Bx { x: area.x + lw, w: area.w - lw, ..area };
    if !plot.ok() {
        return;
    }
    let slot = plot.h / n as f32;
    let bh = slot * 0.85;
    let f = Fmt::new(&s.number_format);
    let show = slot >= LABEL_SIZE * 0.9;
    for (j, v) in vals.iter().enumerate() {
        let bw = (*v / max) as f32 * plot.w;
        let x = plot.x + (plot.w - bw) / 2.0;
        let y = plot.y + j as f32 * slot + (slot - bh) / 2.0;
        if bw > 0.0 {
            ctx.rect(x, y, bw, bh, s.color, None);
        }
        if show {
            let yc = y + bh / 2.0;
            if chart.data_labels {
                let t = f.format(s.values.get(j).copied().flatten().unwrap_or(0.0), None);
                let color = if ctx.tw(&t, LABEL_SIZE) + 4.0 <= bw { on(s.color) } else { TEXT };
                ctx.text(plot.cx(), yc, &t, LABEL_SIZE, color, HAlign::Center, VAlign::Middle);
            }
            let l = ctx.fit(labels.get(j).map(String::as_str).unwrap_or(""), LABEL_SIZE, lw - 8.0);
            ctx.text(plot.x - 6.0, yc, &l, LABEL_SIZE, TEXT, HAlign::Right, VAlign::Middle);
        }
    }
}

// ---------------------------------------------------------------- pie, doughnut, sunburst

fn pie(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx, inner: f32, names: bool) {
    let Some(s) = data.series.first() else { return };
    let vals: Vec<f64> = s.values.iter().take(MAX_CATS).map(|v| v.map(f64::abs).unwrap_or(0.0)).collect();
    let total: f64 = vals.iter().sum();
    if !(total.is_finite() && total > 0.0) {
        return;
    }
    let (cx, cy) = (area.cx(), area.cy());
    let r = area.w.min(area.h) / 2.0 * 0.92;
    if r < 2.0 {
        return;
    }
    // labels outside the pie take room from it
    let pos = chart.elements.label_pos;
    let outside = inner == 0.0 && !names && chart.data_labels && matches!(pos, LabelPos::OutsideEnd | LabelPos::BestFit);
    let r = if outside { r * 0.78 } else { r };
    let ri = r * inner;
    let f = Fmt::new(&s.number_format);
    // a style's soft shadow round the pie
    if let Some(Effect::Shadow(blur)) = ctx.style.map(|st| st.effect) {
        let spread = f32::from(blur).min(r * 0.3) * 0.4;
        for k in (1..=4).rev() {
            let rr = r + spread * k as f32 / 4.0;
            ctx.out.push(Prim::Circle { cx, cy, r: rr, fill: Some([0, 0, 0, 12]), stroke: None });
        }
    }
    let mut a = 0.0f32;
    let mut labels = Vec::new();
    for (j, v) in vals.iter().enumerate() {
        let sweep = (*v / total) as f32 * TAU;
        if sweep > 1e-4 {
            let color = point_color(data, j);
            let (fill, edge) = ctx.slice(color);
            ctx.out.push(Prim::Wedge { cx, cy, r_outer: r, r_inner: ri, a0: a, a1: (a + sweep).min(TAU), fill, stroke: edge });
            if (chart.data_labels || names) && vals.len() <= MAX_LABELS {
                let t = if names {
                    cat_label(data, j)
                } else {
                    // a name with the share goes on a line of its own
                    let lt = chart.elements.label_text;
                    let share = format!("{:.0}%", *v / total * 100.0);
                    let sep = if lt.percent && (lt.category || lt.series) && !lt.value { "\n" } else { ", " };
                    let value = f.format(s.values.get(j).copied().flatten().unwrap_or(0.0), None);
                    label_text(lt, &s.name, &cat_label(data, j), &value, Some(&share), sep)
                };
                let mid = a + sweep / 2.0;
                if outside {
                    let rr = r + 4.0;
                    let ha = if mid.sin() >= 0.0 { HAlign::Left } else { HAlign::Right };
                    labels.push((cx + rr * mid.sin(), cy - rr * mid.cos(), t, TEXT, color, ha));
                } else {
                    let rr = match (ri > 0.0, pos) {
                        (true, _) => (r + ri) / 2.0,
                        (false, LabelPos::Center) => r * 0.5,
                        (false, LabelPos::InsideEnd) => r * 0.8,
                        _ => r * 0.65,
                    };
                    let room = sweep * rr;
                    let widest = t.split('\n').map(|l| ctx.tw(l, LABEL_SIZE)).fold(0.0f32, f32::max);
                    if widest <= room.max(r - ri) && room >= LABEL_SIZE {
                        labels.push((cx + rr * mid.sin(), cy - rr * mid.cos(), t, on(fill), color, HAlign::Center));
                    }
                }
            }
        }
        a += sweep;
    }
    for (x, y, t, c, color, ha) in labels {
        let lines: Vec<&str> = t.split('\n').collect();
        let lh = LABEL_SIZE * 1.2;
        let top = y - lh * (lines.len() as f32 - 1.0) / 2.0;
        for (k, l) in lines.iter().enumerate() {
            ctx.label(x, top + lh * k as f32, l, c, color, ha, VAlign::Middle);
        }
    }
}

/// 3-D Pie: the pie tilted back, the rim of its front half showing below it.
fn pie3d(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let Some(s) = data.series.first() else { return };
    let vals: Vec<f64> = s.values.iter().take(MAX_CATS).map(|v| v.map(f64::abs).unwrap_or(0.0)).collect();
    let total: f64 = vals.iter().sum();
    if !(total.is_finite() && total > 0.0) {
        return;
    }
    let (tilt, thick) = (0.55f32, 0.16f32);
    let rx = (area.w / 2.0 * 0.92).min(area.h * 0.92 / (2.0 * tilt + thick));
    if rx < 2.0 {
        return;
    }
    let (ry, h) = (rx * tilt, rx * thick);
    let (cx, cy) = (area.cx(), area.cy() - h / 2.0);
    let at = |a: f32, down: f32| [cx + rx * a.sin(), cy - ry * a.cos() + down];
    let arc = |a0: f32, a1: f32, down: f32| -> Vec<[f32; 2]> {
        let n = ((a1 - a0) / 0.05).ceil().clamp(1.0, 200.0) as usize;
        (0..=n).map(|i| at(a0 + (a1 - a0) * i as f32 / n as f32, down)).collect()
    };
    let mut slices = Vec::new();
    let mut a = 0.0f32;
    for (j, v) in vals.iter().enumerate() {
        let sweep = (*v / total) as f32 * TAU;
        if sweep > 1e-4 {
            slices.push((a, (a + sweep).min(TAU), j));
        }
        a += sweep;
    }
    // the rim: the part of each slice between 3 and 9 o'clock, round the front
    for (a0, a1, j) in &slices {
        let (s0, s1) = (a0.max(PI / 2.0), a1.min(PI * 1.5));
        if s1 > s0 {
            let mut pts = arc(s0, s1, 0.0);
            pts.extend(arc(s0, s1, h).into_iter().rev());
            let fill = shade(point_color(data, *j), 0.7);
            ctx.out.push(Prim::Polygon { pts, fill, stroke: Some((WHITE, 0.75)) });
        }
    }
    let f = Fmt::new(&s.number_format);
    let mut labels = Vec::new();
    for (a0, a1, j) in &slices {
        let fill = point_color(data, *j);
        let mut pts = vec![[cx, cy]];
        pts.extend(arc(*a0, *a1, 0.0));
        ctx.out.push(Prim::Polygon { pts, fill, stroke: Some((WHITE, 1.0)) });
        if chart.data_labels && vals.len() <= MAX_LABELS {
            let mid = (a0 + a1) / 2.0;
            let t = f.format(s.values.get(*j).copied().flatten().unwrap_or(0.0), None);
            labels.push((cx + rx * 0.62 * mid.sin(), cy - ry * 0.62 * mid.cos(), t, on(fill)));
        }
    }
    for (x, y, t, c) in labels {
        ctx.text(x, y, &t, LABEL_SIZE, c, HAlign::Center, VAlign::Middle);
    }
}

/// Pie of Pie and Bar of Pie: the last third of the points (at least one) come out of the pie as
/// one slice, on its right, and are drawn again beside it as a smaller pie or a stacked bar, joined
/// to the slice by two lines.
fn of_pie(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx, bar: bool) {
    let Some(s) = data.series.first() else { return };
    let vals: Vec<f64> = s.values.iter().take(MAX_CATS).map(|v| v.map(f64::abs).unwrap_or(0.0)).collect();
    let n = vals.len();
    if n < 2 {
        return pie(ctx, chart, data, area, 0.0, false);
    }
    // the second plot takes the last third, at least two points when there are four or more
    let split = (n / 3).max(if n >= 4 { 2 } else { 1 }).clamp(1, n - 1);
    let (head, tail) = vals.split_at(n - split);
    let other: f64 = tail.iter().sum();
    let total = head.iter().sum::<f64>() + other;
    if !(total.is_finite() && total > 0.0) {
        return;
    }
    let r = (area.w * 0.25).min(area.h / 2.0 * 0.9);
    if r < 2.0 {
        return;
    }
    let (cx, cy) = (area.x + area.w * 0.3, area.cy());
    let f = Fmt::new(&s.number_format);
    let label = |ctx: &mut Ctx, x: f32, y: f32, j: usize, fill: Rgba| {
        if chart.data_labels {
            let t = f.format(s.values.get(j).copied().flatten().unwrap_or(0.0), None);
            ctx.text(x, y, &t, LABEL_SIZE, on(fill), HAlign::Center, VAlign::Middle);
        }
    };
    // the slice that holds the rest sits centred on the right (angles clockwise from the top)
    let other_sweep = (other / total) as f32 * TAU;
    let other_a0 = PI / 2.0 - other_sweep / 2.0;
    let mut a = PI / 2.0 + other_sweep / 2.0;
    for (j, v) in head.iter().enumerate() {
        let sweep = (*v / total) as f32 * TAU;
        if sweep > 1e-4 {
            let fill = point_color(data, j);
            ctx.out.push(Prim::Wedge { cx, cy, r_outer: r, r_inner: 0.0, a0: a, a1: a + sweep, fill, stroke: Some((WHITE, 1.5)) });
            let mid = a + sweep / 2.0;
            label(ctx, cx + r * 0.65 * mid.sin(), cy - r * 0.65 * mid.cos(), j, fill);
        }
        a += sweep;
    }
    let other_fill = point_color(data, n);
    ctx.out.push(Prim::Wedge {
        cx,
        cy,
        r_outer: r,
        r_inner: 0.0,
        a0: other_a0,
        a1: other_a0 + other_sweep,
        fill: other_fill,
        stroke: Some((WHITE, 1.5)),
    });
    let edge = |ang: f32| [cx + r * ang.sin(), cy - r * ang.cos()];
    let (top_edge, bottom_edge) = (edge(other_a0), edge(other_a0 + other_sweep));
    // the rest, drawn again beside the pie
    let r2 = r * 0.75;
    let cx2 = area.x + area.w * 0.78;
    let (join_top, join_bottom) = if bar {
        let w = r2 * 0.7;
        let (x0, y0, h) = (cx2 - w / 2.0, cy - r2, r2 * 2.0);
        let mut y = y0 + h;
        for (k, v) in tail.iter().enumerate() {
            let bh = if other > 0.0 { (*v / other) as f32 * h } else { 0.0 };
            let j = n - split + k;
            let fill = point_color(data, j);
            ctx.rect(x0, y - bh, w, bh, fill, Some((WHITE, 1.0)));
            if bh >= LABEL_SIZE {
                label(ctx, cx2, y - bh / 2.0, j, fill);
            }
            y -= bh;
        }
        ([x0, y0], [x0, y0 + h])
    } else {
        let mut a2 = 0.0f32;
        for (k, v) in tail.iter().enumerate() {
            let sweep = if other > 0.0 { (*v / other) as f32 * TAU } else { 0.0 };
            let j = n - split + k;
            let fill = point_color(data, j);
            if sweep >= TAU - 1e-4 {
                // a single point fills the second pie
                ctx.out.push(Prim::Circle { cx: cx2, cy, r: r2, fill: Some(fill), stroke: Some((WHITE, 1.5)) });
            } else if sweep > 1e-4 {
                ctx.out.push(Prim::Wedge { cx: cx2, cy, r_outer: r2, r_inner: 0.0, a0: a2, a1: a2 + sweep, fill, stroke: Some((WHITE, 1.5)) });
                let mid = a2 + sweep / 2.0;
                label(ctx, cx2 + r2 * 0.6 * mid.sin(), cy - r2 * 0.6 * mid.cos(), j, fill);
            }
            a2 += sweep;
        }
        ([cx2, cy - r2], [cx2, cy + r2])
    };
    ctx.line(vec![top_edge, join_top], GRID, 0.75);
    ctx.line(vec![bottom_edge, join_bottom], GRID, 0.75);
}

// ---------------------------------------------------------------- surface & contour

/// Most categories and series a surface draws; the rest are left out.
const MAX_SURFACE_CATS: usize = 200;
const MAX_SURFACE_ROWS: usize = 100;

/// A surface's grid of values: a row per series, a column per category, at least 2×2 (a single
/// row or column is doubled). Blanks count as zero.
fn surface_grid(data: &ChartData) -> Option<Vec<Vec<f64>>> {
    let ncat = data.series.iter().map(|s| s.values.len()).max().unwrap_or(0).min(MAX_SURFACE_CATS);
    if ncat == 0 {
        return None;
    }
    let mut grid: Vec<Vec<f64>> = data
        .series
        .iter()
        .take(MAX_SURFACE_ROWS)
        .map(|s| {
            let mut row: Vec<f64> = (0..ncat).map(|j| s.values.get(j).copied().flatten().unwrap_or(0.0)).collect();
            if let [v] = row.as_slice() {
                row.push(*v);
            }
            row
        })
        .collect();
    if let [r] = grid.as_slice() {
        let r = r.clone();
        grid.push(r);
    }
    (grid.len() >= 2).then_some(grid)
}

/// The value axis of a surface; its ticks bound the bands the surface is coloured in.
fn surface_scale(grid: &[Vec<f64>]) -> Option<Scale> {
    let (lo, hi) = value_range(grid.iter().flatten().copied())?;
    let s = nice_scale(lo, hi, false);
    (s.ticks().len() >= 2).then_some(s)
}

/// The band `v` falls in.
fn band_of(ticks: &[f64], v: f64) -> usize {
    ticks.partition_point(|t| *t <= v).saturating_sub(1).min(ticks.len().saturating_sub(2))
}

/// The value at (`c`, `r`) on the grid (category, series), between its points.
fn grid_at(grid: &[Vec<f64>], c: f32, r: f32) -> f64 {
    let rows = grid.len();
    let cols = grid.first().map_or(0, Vec::len);
    let g = |r: usize, c: usize| grid.get(r).and_then(|row| row.get(c)).copied().unwrap_or(0.0);
    if rows < 2 || cols < 2 {
        return g(0, 0);
    }
    let c0 = (c.floor().max(0.0) as usize).min(cols - 2);
    let r0 = (r.floor().max(0.0) as usize).min(rows - 2);
    let fc = f64::from((c - c0 as f32).clamp(0.0, 1.0));
    let fr = f64::from((r - r0 as f32).clamp(0.0, 1.0));
    let lower = g(r0, c0) + (g(r0, c0 + 1) - g(r0, c0)) * fc;
    let upper = g(r0 + 1, c0) + (g(r0 + 1, c0 + 1) - g(r0 + 1, c0)) * fc;
    lower + (upper - lower) * fr
}

/// A surface's legend: each band's range in its colour.
fn surface_legend(data: &ChartData, wire: bool) -> Vec<(String, Rgba, Swatch)> {
    let Some(scale) = surface_grid(data).as_deref().and_then(surface_scale) else { return vec![] };
    let f = Fmt::new(data.series.first().map(|s| s.number_format.as_str()).unwrap_or("General"));
    let ticks = scale.ticks();
    ticks
        .windows(2)
        .enumerate()
        .map(|(b, w)| {
            let (lo, hi) = (w.first().copied().unwrap_or(0.0), w.get(1).copied().unwrap_or(0.0));
            let text = format!("{}-{}", f.format(lo, Some(scale.step)), f.format(hi, Some(scale.step)));
            (text, point_color(data, b), if wire { Swatch::Line } else { Swatch::Box })
        })
        .collect()
}

/// `poly` (category, series, value at each corner) cut at value `at`, keeping the part above it
/// (or below), the value taken as changing evenly along each edge.
fn clip_value(poly: &[[f32; 3]], at: f32, above: bool) -> Vec<[f32; 3]> {
    let inside = |p: &[f32; 3]| if above { p[2] >= at } else { p[2] <= at };
    let mut out = Vec::with_capacity(poly.len() + 2);
    for (a, b) in poly.iter().zip(poly.iter().cycle().skip(1)) {
        if inside(a) {
            out.push(*a);
        }
        if inside(a) != inside(b) {
            let t = (at - a[2]) / (b[2] - a[2]);
            out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, at]);
        }
    }
    out
}

/// The pieces of `poly` in each band of value it crosses: (band, corners).
fn band_pieces(poly: &[[f32; 3]], ticks: &[f64]) -> Vec<(usize, Vec<[f32; 3]>)> {
    let lo = poly.iter().map(|p| p[2]).fold(f32::INFINITY, f32::min);
    let hi = poly.iter().map(|p| p[2]).fold(f32::NEG_INFINITY, f32::max);
    if !(lo.is_finite() && hi.is_finite()) {
        return vec![];
    }
    let (b0, b1) = (band_of(ticks, f64::from(lo)), band_of(ticks, f64::from(hi)));
    if b0 == b1 {
        return vec![(b0, poly.to_vec())];
    }
    (b0..=b1)
        .filter_map(|b| {
            let mut piece = poly.to_vec();
            if b > b0 {
                piece = clip_value(&piece, ticks.get(b).copied().unwrap_or(0.0) as f32, true);
            }
            if b < b1 {
                piece = clip_value(&piece, ticks.get(b + 1).copied().unwrap_or(0.0) as f32, false);
            }
            (piece.len() >= 3).then_some((b, piece))
        })
        .collect()
}

/// The corners of the piece of grid cell (`c`, `r`) cut into `k`×`k` at (`sx`, `sy`), with their values.
fn sub_cell(grid: &[Vec<f64>], c: usize, r: usize, k: usize, sx: usize, sy: usize) -> [[f32; 3]; 4] {
    let kf = k as f32;
    let (c0, c1) = (c as f32 + sx as f32 / kf, c as f32 + (sx + 1) as f32 / kf);
    let (r0, r1) = (r as f32 + sy as f32 / kf, r as f32 + (sy + 1) as f32 / kf);
    let at = |c: f32, r: f32| [c, r, grid_at(grid, c, r) as f32];
    [at(c0, r0), at(c1, r0), at(c1, r1), at(c0, r1)]
}

/// How many steps (up to `most`) each grid cell is cut into so a surface draws about `budget`
/// pieces at most.
fn steps(cells: usize, budget: usize, most: usize) -> usize {
    ((budget as f64 / cells.max(1) as f64).sqrt().floor() as usize).clamp(1, most)
}

/// Contour: the surface seen from above, categories across and series up, its bands filled (or, as
/// a wireframe, only the lines between them).
fn contour(ctx: &mut Ctx, data: &ChartData, area: Bx, wire: bool) {
    let Some(grid) = surface_grid(data) else { return };
    let Some(scale) = surface_scale(&grid) else { return };
    let ticks = scale.ticks();
    let (rows, cols) = (grid.len(), grid.first().map_or(0, Vec::len));
    if rows < 2 || cols < 2 {
        return;
    }
    let names: Vec<String> = (0..rows).map(|r| data.series.get(r).map(|s| s.name.clone()).unwrap_or_default()).collect();
    let lw = (names.iter().map(|n| ctx.tw(n, LABEL_SIZE)).fold(0.0f32, f32::max) + 6.0).min(area.w * 0.3);
    let lh = LABEL_SIZE * 1.4;
    // the series' names go on the right, the first at the bottom
    let edge = ctx.tw(&cat_label(data, 0), LABEL_SIZE) / 2.0 + 2.0;
    let left = edge.min(area.w * 0.2);
    let plot = Bx { x: area.x + left, y: area.y + lh * 0.5, w: area.w - left - lw, h: area.h - lh * 1.5 - 4.0 };
    if !plot.ok() {
        return;
    }
    let (sx, sy) = (plot.w / (cols - 1) as f32, plot.h / (rows - 1) as f32);
    let px = |c: f32| plot.x + c * sx;
    let py = |r: f32| plot.bottom() - r * sy;
    if !wire {
        // small pieces of each cell, cut where the value crosses a band's edge
        let k = steps((cols - 1) * (rows - 1), 3000, 10);
        for r in 0..rows - 1 {
            for c in 0..cols - 1 {
                for sy in 0..k {
                    for sx in 0..k {
                        for (b, piece) in band_pieces(&sub_cell(&grid, c, r, k, sx, sy), &ticks) {
                            let fill = point_color(data, b);
                            let pts = piece.iter().map(|p| [px(p[0]), py(p[1])]).collect();
                            ctx.out.push(Prim::Polygon { pts, fill, stroke: Some((fill, 0.75)) });
                        }
                    }
                }
            }
        }
    } else {
        // where the value crosses each band's edge, cell by cell (marching squares)
        let g = |r: usize, c: usize| grid.get(r).and_then(|row| row.get(c)).copied().unwrap_or(0.0);
        for (li, level) in ticks.iter().enumerate().skip(1).take(ticks.len().saturating_sub(2)) {
            let color = point_color(data, li);
            for r in 0..rows - 1 {
                for c in 0..cols - 1 {
                    let corners = [(c, r), (c + 1, r), (c + 1, r + 1), (c, r + 1)];
                    let mut hits = Vec::with_capacity(4);
                    for (a, b) in corners.iter().zip(corners.iter().cycle().skip(1)) {
                        let (va, vb) = (g(a.1, a.0), g(b.1, b.0));
                        if (va >= *level) != (vb >= *level) {
                            let t = ((level - va) / (vb - va)) as f32;
                            let (ax, ay, bx, by) = (a.0 as f32, a.1 as f32, b.0 as f32, b.1 as f32);
                            hits.push([px(ax + (bx - ax) * t), py(ay + (by - ay) * t)]);
                        }
                    }
                    for pair in hits.chunks(2) {
                        if let [p, q] = pair {
                            ctx.line(vec![*p, *q], color, 1.25);
                        }
                    }
                }
            }
        }
    }
    ctx.out.push(Prim::Rect { x: plot.x, y: plot.y, w: plot.w, h: plot.h, fill: None, stroke: Some((AXIS, 0.75)), radius: 0.0 });
    for (r, n) in names.iter().enumerate() {
        let t = ctx.fit(n, LABEL_SIZE, lw - 6.0);
        ctx.text(plot.right() + 4.0, py(r as f32), &t, LABEL_SIZE, TEXT, HAlign::Left, VAlign::Middle);
    }
    let cats: Vec<String> = (0..cols).map(|c| cat_label(data, c)).collect();
    let cw = cats.iter().map(|t| ctx.tw(t, LABEL_SIZE)).fold(0.0f32, f32::max) + 6.0;
    let k = ((cw / sx.max(1e-3)).ceil() as usize).clamp(1, cols);
    for (c, t) in cats.iter().enumerate().step_by(k) {
        ctx.text(px(c as f32), plot.bottom() + 3.0, t, LABEL_SIZE, TEXT, HAlign::Center, VAlign::Top);
    }
}

/// 3-D Surface: the grid of values as a sheet over the floor, categories across and series going
/// back, coloured in bands of value (or, as a wireframe, its grid lines only).
fn surface3d(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx, wire: bool) {
    let Some(grid) = surface_grid(data) else { return };
    let Some(scale) = surface_scale(&grid) else { return };
    let ticks = scale.ticks();
    let (rows, cols) = (grid.len(), grid.first().map_or(0, Vec::len));
    if rows < 2 || cols < 2 {
        return;
    }
    let f = Fmt::new(data.series.first().map(|s| s.number_format.as_str()).unwrap_or("General"));
    let vlabels: Vec<String> = ticks.iter().map(|v| f.format(*v, Some(scale.step))).collect();
    let vw = (vlabels.iter().map(|t| ctx.tw(t, LABEL_SIZE)).fold(0.0f32, f32::max) + 6.0).min(area.w * 0.3);
    let names: Vec<String> = (0..rows).map(|r| data.series.get(r).map(|s| s.name.clone()).unwrap_or_default()).collect();
    let nw = (names.iter().map(|n| ctx.tw(n, LABEL_SIZE)).fold(0.0f32, f32::max) + 6.0).min(area.w * 0.25);
    let lh = LABEL_SIZE * 1.4;
    let dx = (area.w * 0.2).min(area.h * 0.5);
    let d = [dx, dx * 0.6];
    // room on the left for the value labels and half the first category's label
    let left = vw.max(ctx.tw(&cat_label(data, 0), LABEL_SIZE) / 2.0 + 2.0).min(area.w * 0.3);
    let plot = Bx { x: area.x + left, y: area.y + d[1] + lh * 0.5, w: area.w - left - d[0] - nw, h: area.h - d[1] - lh * 1.5 - 4.0 };
    if !plot.ok() {
        return;
    }
    let tick_y: Vec<f32> = ticks.iter().map(|v| plot.bottom() - scale.frac(*v) * plot.h).collect();
    walls(ctx, plot, d, &tick_y, false, chart.gridlines);
    for (y, t) in tick_y.iter().zip(&vlabels) {
        ctx.text(plot.x - 4.0, *y, t, LABEL_SIZE, TEXT, HAlign::Right, VAlign::Middle);
    }
    let (cl, rl) = ((cols - 1) as f32, (rows - 1) as f32);
    let at = |c: f32, r: f32, v: f64| -> [f32; 2] {
        let (fx, fz) = (c / cl, r / rl);
        [plot.x + fx * plot.w + fz * d[0], plot.bottom() - scale.frac(v) * plot.h - fz * d[1]]
    };
    let proj = |c: f32, r: f32| at(c, r, grid_at(&grid, c, r));
    let k = steps((cols - 1) * (rows - 1), if wire { 4000 } else { 1500 }, 12);
    let kf = k as f32;
    let edge: Rgba = [0x40, 0x40, 0x40, 0x90];
    // a grid line from `a` to `b` (category, series) along the surface
    let grid_line = |ctx: &mut Ctx, a: (f32, f32), b: (f32, f32)| {
        let at = |t: f32| (a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t);
        if wire {
            for i in 0..k {
                let (p, q) = (at(i as f32 / kf), at((i + 1) as f32 / kf));
                let color = point_color(data, band_of(&ticks, grid_at(&grid, (p.0 + q.0) / 2.0, (p.1 + q.1) / 2.0)));
                ctx.line(vec![proj(p.0, p.1), proj(q.0, q.1)], color, 1.0);
            }
        } else {
            ctx.line((0..=k).map(|i| at(i as f32 / kf)).map(|(c, r)| proj(c, r)).collect(), edge, 0.5);
        }
    };
    // the back row first, so the rows in front cover it
    for r in (0..rows - 1).rev() {
        for c in 0..cols - 1 {
            if !wire {
                for sy in (0..k).rev() {
                    for sx in 0..k {
                        // cut where the value crosses a band's edge, so the bands' edges are smooth
                        for (b, piece) in band_pieces(&sub_cell(&grid, c, r, k, sx, sy), &ticks) {
                            let fill = point_color(data, b);
                            let pts = piece.iter().map(|p| at(p[0], p[1], f64::from(p[2]))).collect();
                            ctx.out.push(Prim::Polygon { pts, fill, stroke: Some((fill, 0.5)) });
                        }
                    }
                }
            }
            let (cf, rf) = (c as f32, r as f32);
            grid_line(ctx, (cf, rf), (cf + 1.0, rf));
            grid_line(ctx, (cf, rf), (cf, rf + 1.0));
            if r + 2 == rows {
                grid_line(ctx, (cf, rf + 1.0), (cf + 1.0, rf + 1.0));
            }
            if c + 2 == cols {
                grid_line(ctx, (cf + 1.0, rf), (cf + 1.0, rf + 1.0));
            }
        }
    }
    let cats: Vec<String> = (0..cols).map(|c| cat_label(data, c)).collect();
    let cw = cats.iter().map(|t| ctx.tw(t, LABEL_SIZE)).fold(0.0f32, f32::max) + 6.0;
    let k = ((cw / (plot.w / cl).max(1e-3)).ceil() as usize).clamp(1, cols);
    for (c, t) in cats.iter().enumerate().step_by(k) {
        ctx.text(plot.x + c as f32 / cl * plot.w, plot.bottom() + 3.0, t, LABEL_SIZE, TEXT, HAlign::Center, VAlign::Top);
    }
    for (r, n) in names.iter().enumerate() {
        let fz = r as f32 / rl;
        let t = ctx.fit(n, LABEL_SIZE, nw - 6.0);
        ctx.text(plot.right() + fz * d[0] + 4.0, plot.bottom() - fz * d[1], &t, LABEL_SIZE, TEXT, HAlign::Left, VAlign::Middle);
    }
}

// ---------------------------------------------------------------- treemap

fn worst(row: &[f64], side: f64) -> f64 {
    let s: f64 = row.iter().sum();
    if s <= 0.0 || side <= 0.0 {
        return f64::INFINITY;
    }
    let (mn, mx) = row.iter().fold((f64::INFINITY, 0.0f64), |(a, b), v| (a.min(*v), b.max(*v)));
    let s2 = s * s;
    let w2 = side * side;
    (w2 * mx / s2).max(s2 / (w2 * mn))
}

/// Squarified treemap layout of (index, area) items, sorted descending, into `r`.
fn squarify(items: &[(usize, f64)], r: Bx) -> Vec<(usize, Bx)> {
    let total: f64 = items.iter().map(|i| i.1).sum();
    let area = (r.w as f64) * (r.h as f64);
    if !(total > 0.0 && area > 0.0) {
        return vec![];
    }
    let scale = area / total;
    let mut out = Vec::with_capacity(items.len());
    let mut rem = r;
    let mut row: Vec<(usize, f64)> = Vec::new();
    let mut i = 0;
    let layout = |row: &[(usize, f64)], rem: &mut Bx, out: &mut Vec<(usize, Bx)>| {
        let s: f64 = row.iter().map(|x| x.1).sum();
        if s <= 0.0 {
            return;
        }
        if rem.w >= rem.h {
            let cw = (s / rem.h.max(1e-6) as f64) as f32;
            let mut y = rem.y;
            for (j, a) in row {
                let h = (*a / cw.max(1e-6) as f64) as f32;
                out.push((*j, Bx { x: rem.x, y, w: cw, h }));
                y += h;
            }
            rem.x += cw;
            rem.w = (rem.w - cw).max(0.0);
        } else {
            let rh = (s / rem.w.max(1e-6) as f64) as f32;
            let mut x = rem.x;
            for (j, a) in row {
                let w = (*a / rh.max(1e-6) as f64) as f32;
                out.push((*j, Bx { x, y: rem.y, w, h: rh }));
                x += w;
            }
            rem.y += rh;
            rem.h = (rem.h - rh).max(0.0);
        }
    };
    while let Some(&(j, v)) = items.get(i) {
        let a = v * scale;
        let side = rem.w.min(rem.h) as f64;
        let cur: Vec<f64> = row.iter().map(|x| x.1).collect();
        let mut with = cur.clone();
        with.push(a);
        if row.is_empty() || worst(&with, side) <= worst(&cur, side) {
            row.push((j, a));
            i += 1;
        } else {
            layout(&row, &mut rem, &mut out);
            row.clear();
        }
    }
    if !row.is_empty() {
        layout(&row, &mut rem, &mut out);
    }
    out
}

fn treemap(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let Some(s) = data.series.first() else { return };
    let mut items: Vec<(usize, f64)> =
        s.values.iter().take(MAX_MARKERS).enumerate().filter_map(|(j, v)| v.filter(|v| *v > 0.0).map(|v| (j, v))).collect();
    items.sort_by(|a, b| b.1.total_cmp(&a.1));
    let f = Fmt::new(&s.number_format);
    for (j, b) in squarify(&items, area) {
        let fill = point_color(data, j);
        ctx.rect(b.x, b.y, b.w, b.h, fill, Some((WHITE, 1.5)));
        if b.w > 24.0 && b.h > LABEL_SIZE * 1.6 {
            let name = ctx.fit(&cat_label(data, j), LABEL_SIZE, b.w - 8.0);
            ctx.text(b.x + 4.0, b.y + 4.0, &name, LABEL_SIZE, on(fill), HAlign::Left, VAlign::Top);
            if chart.data_labels && b.h > LABEL_SIZE * 3.2 {
                let v = items.iter().find(|x| x.0 == j).map(|x| x.1).unwrap_or(0.0);
                let t = ctx.fit(&f.format(v, None), LABEL_SIZE, b.w - 8.0);
                ctx.text(b.x + 4.0, b.y + 4.0 + LABEL_SIZE * 1.4, &t, LABEL_SIZE, on(fill), HAlign::Left, VAlign::Top);
            }
        }
    }
}

// ---------------------------------------------------------------- radar

fn radar(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let ncat = data.categories.len().max(data.series.iter().map(|s| s.values.len()).max().unwrap_or(0)).min(1000);
    if ncat == 0 {
        return;
    }
    let labels: Vec<String> = (0..ncat).map(|j| cat_label(data, j)).collect();
    let lw = labels.iter().map(|t| ctx.tw(t, LABEL_SIZE)).fold(0.0f32, f32::max).min(area.w * 0.2);
    let r = ((area.w - 2.0 * (lw + 8.0)).min(area.h - 2.0 * (LABEL_SIZE * 1.4 + 6.0)) / 2.0).max(0.0);
    if r < 5.0 {
        return;
    }
    let (cx, cy) = (area.cx(), area.cy());
    let (lo, hi) = value_range(data.series.iter().flat_map(|s| s.values.iter().flatten().copied())).unwrap_or((0.0, 0.0));
    let sc = nice_scale(lo, hi, true);
    let f = Fmt::new(data.series.first().map(|s| s.number_format.as_str()).unwrap_or("General"));
    let ang = |j: usize| j as f32 / ncat as f32 * TAU;
    let pt = |a: f32, rr: f32| [cx + rr * a.sin(), cy - rr * a.cos()];
    let ticks = sc.ticks();
    let spacing = r / ticks.len().saturating_sub(1).max(1) as f32;
    let every = ((LABEL_SIZE * 1.3 / spacing.max(1e-3)).ceil() as usize).max(1);
    for (ti, t) in ticks.into_iter().enumerate() {
        let rr = sc.frac(t) * r;
        if rr > 0.5 && chart.gridlines {
            let mut ring: Vec<[f32; 2]> = (0..ncat).map(|j| pt(ang(j), rr)).collect();
            if ncat < 3 {
                ring = (0..=48).map(|k| pt(k as f32 / 48.0 * TAU, rr)).collect();
            } else if let Some(p) = ring.first().copied() {
                ring.push(p);
            }
            ctx.line(ring, GRID, 0.75);
        }
        if ti % every == 0 {
            ctx.text(cx - 3.0, cy - rr, &f.format(t, Some(sc.step)), LABEL_SIZE * 0.9, TEXT, HAlign::Right, VAlign::Middle);
        }
    }
    let step = ncat.div_ceil(72).max(1);
    for j in (0..ncat).step_by(step) {
        let a = ang(j);
        ctx.line(vec![[cx, cy], pt(a, r)], GRID, 0.75);
        let p = pt(a, r + 6.0);
        let (sa, ca) = (a.sin(), a.cos());
        let align = if sa > 0.1 {
            HAlign::Left
        } else if sa < -0.1 {
            HAlign::Right
        } else {
            HAlign::Center
        };
        let valign = if ca > 0.1 {
            VAlign::Bottom
        } else if ca < -0.1 {
            VAlign::Top
        } else {
            VAlign::Middle
        };
        let t = ctx.fit(labels.get(j).map(String::as_str).unwrap_or(""), LABEL_SIZE, lw.max(20.0));
        ctx.text(p[0], p[1], &t, LABEL_SIZE, TEXT, align, valign);
    }
    for s in &data.series {
        let mut pts: Vec<[f32; 2]> = (0..ncat).map(|j| pt(ang(j), sc.frac(s.values.get(j).copied().flatten().unwrap_or(sc.min)) * r)).collect();
        // Filled Radar: each series a filled shape; Radar with Markers: a marker on each point
        if chart.kind == ChartKind::RadarFilled && pts.len() >= 3 {
            ctx.out.push(Prim::Polygon { pts, fill: s.color, stroke: None });
            continue;
        }
        if chart.kind == ChartKind::RadarMarkers {
            draw_markers(ctx, pts.iter().copied(), 3.5, s.color);
        }
        if let Some(p) = pts.first().copied() {
            pts.push(p);
        }
        ctx.line(pts, s.color, 2.0);
    }
}

// ---------------------------------------------------------------- box & whisker

fn quantile(sorted: &[f64], q: f64) -> f64 {
    let n = sorted.len();
    if n == 0 {
        return 0.0;
    }
    let pos = q * (n - 1) as f64;
    let i = pos.floor() as usize;
    let frac = pos - i as f64;
    let a = sorted.get(i).copied().unwrap_or(0.0);
    let b = sorted.get(i + 1).copied().unwrap_or(a);
    a + (b - a) * frac
}

fn box_whisker(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let labels: Vec<String> = data.series.iter().map(|s| s.name.clone()).collect();
    let Some((lo, hi)) = value_range(data.series.iter().flat_map(|s| s.values.iter().flatten().copied())) else { return };
    let ys = nice_scale(lo, hi, false);
    let f = Fmt::new(data.series.first().map(|s| s.number_format.as_str()).unwrap_or("General"));
    let plot = frame(ctx, area, CatAxis::Labels(&labels), &ValAxis { scale: ys, fmt: &f }, None, false, Grid::of(chart));
    if !plot.ok() {
        return;
    }
    let n = data.series.len().max(1);
    let cw = plot.w / n as f32;
    let bw = (cw * 0.4).min(80.0);
    let vy = |v: f64| plot.bottom() - ys.frac(v) * plot.h;
    for (i, s) in data.series.iter().enumerate() {
        let mut v: Vec<f64> = s.values.iter().flatten().copied().collect();
        if v.is_empty() {
            continue;
        }
        v.sort_by(f64::total_cmp);
        let (q1, med, q3) = (quantile(&v, 0.25), quantile(&v, 0.5), quantile(&v, 0.75));
        let iqr = q3 - q1;
        let (flo, fhi) = (q1 - 1.5 * iqr, q3 + 1.5 * iqr);
        let wlo = v.iter().copied().find(|x| *x >= flo).unwrap_or(q1);
        let whi = v.iter().rev().copied().find(|x| *x <= fhi).unwrap_or(q3);
        let mean = v.iter().sum::<f64>() / v.len() as f64;
        let c = plot.x + (i as f32 + 0.5) * cw;
        ctx.vline(c, vy(whi), vy(q3), s.color, 1.25);
        ctx.vline(c, vy(q1), vy(wlo), s.color, 1.25);
        ctx.hline(c - bw / 4.0, c + bw / 4.0, vy(whi), s.color, 1.25);
        ctx.hline(c - bw / 4.0, c + bw / 4.0, vy(wlo), s.color, 1.25);
        let (yt, yb) = (vy(q3), vy(q1));
        ctx.rect(c - bw / 2.0, yt, bw, (yb - yt).max(0.5), s.color, None);
        ctx.hline(c - bw / 2.0, c + bw / 2.0, vy(med), WHITE, 1.5);
        let (mx, my, d) = (c, vy(mean), 3.0);
        ctx.line(vec![[mx - d, my - d], [mx + d, my + d]], WHITE, 1.25);
        ctx.line(vec![[mx - d, my + d], [mx + d, my - d]], WHITE, 1.25);
        for o in v.iter().filter(|x| **x < flo || **x > fhi).take(MAX_MARKERS) {
            ctx.out.push(Prim::Circle { cx: c, cy: vy(*o), r: 2.5, fill: None, stroke: Some((s.color, 1.0)) });
        }
    }
}

// ---------------------------------------------------------------- stock

fn stock(ctx: &mut Ctx, chart: &Chart, data: &ChartData, area: Bx) {
    let ncat = data.categories.len().max(data.series.iter().map(|s| s.values.len()).max().unwrap_or(0)).min(MAX_CATS);
    if ncat == 0 {
        return;
    }
    let labels: Vec<String> = (0..ncat).map(|j| cat_label(data, j)).collect();
    let Some((lo, hi)) = value_range(data.series.iter().flat_map(|s| s.values.iter().flatten().copied())) else { return };
    let ys = nice_scale(lo, hi, false);
    let f = Fmt::new(data.series.first().map(|s| s.number_format.as_str()).unwrap_or("General"));
    let plot = frame(ctx, area, CatAxis::Labels(&labels), &ValAxis { scale: ys, fmt: &f }, None, false, Grid::of(chart));
    if !plot.ok() {
        return;
    }
    let ser = |k: usize| data.series.get(k);
    let (open, high, low, close) = match data.series.len() {
        0 => return,
        1 => (None, None, None, ser(0)),
        2 => (None, ser(0), ser(1), None),
        3 => (None, ser(0), ser(1), ser(2)),
        _ => (ser(0), ser(1), ser(2), ser(3)),
    };
    let get = |s: Option<&SeriesData>, j: usize| s.and_then(|s| s.values.get(j).copied().flatten());
    let cw = plot.w / ncat as f32;
    let tick = (cw * 0.25).max(1.0);
    let vy = |v: f64| plot.bottom() - ys.frac(v) * plot.h;
    for j in 0..ncat {
        let c = plot.x + (j as f32 + 0.5) * cw;
        if let (Some(h), Some(l)) = (get(high, j), get(low, j)) {
            ctx.vline(c, vy(h), vy(l), STOCK, 1.25);
        }
        match (get(open, j), get(close, j)) {
            (Some(o), Some(cl)) => {
                let (yo, yc) = (vy(o), vy(cl));
                let bw = (cw * 0.5).max(1.0);
                let fill = if cl >= o { WHITE } else { STOCK };
                ctx.rect(c - bw / 2.0, yo.min(yc), bw, (yo - yc).abs().max(0.75), fill, Some((STOCK, 1.0)));
            }
            (None, Some(cl)) => {
                if high.is_some() {
                    ctx.hline(c, c + tick, vy(cl), STOCK, 1.25);
                } else {
                    ctx.out.push(Prim::Circle { cx: c, cy: vy(cl), r: 2.5, fill: Some(STOCK), stroke: None });
                }
            }
            _ => {}
        }
    }
}

// ---------------------------------------------------------------- final safety pass

fn fin(v: f32) -> bool {
    v.is_finite()
}

fn clamp_stroke(s: Option<(Rgba, f32)>) -> Option<(Rgba, f32)> {
    s.filter(|(_, w)| fin(*w) && *w > 0.0).map(|(c, w)| (c, w.min(50.0)))
}

/// Drops non-finite primitives and clamps every coordinate into the `w`×`h` box.
pub(crate) fn finalize(prims: Vec<Prim>, w: f32, h: f32) -> Vec<Prim> {
    let cx = |v: f32| v.clamp(0.0, w);
    let cy = |v: f32| v.clamp(0.0, h);
    let pts_ok = |pts: &[[f32; 2]]| pts.iter().all(|p| fin(p[0]) && fin(p[1]));
    prims
        .into_iter()
        .filter_map(|p| match p {
            Prim::Rect { x, y, w: rw, h: rh, fill, stroke, radius } => {
                if !(fin(x) && fin(y) && fin(rw) && fin(rh)) {
                    return None;
                }
                let (x0, x1) = (cx(x.min(x + rw)), cx(x.max(x + rw)));
                let (y0, y1) = (cy(y.min(y + rh)), cy(y.max(y + rh)));
                if x1 - x0 <= 0.0 || y1 - y0 <= 0.0 {
                    return None;
                }
                let radius = if fin(radius) { radius.clamp(0.0, (x1 - x0).min(y1 - y0) / 2.0) } else { 0.0 };
                Some(Prim::Rect { x: x0, y: y0, w: x1 - x0, h: y1 - y0, fill, stroke: clamp_stroke(stroke), radius })
            }
            Prim::Line { pts, color, width, dash } => {
                if !pts_ok(&pts) || pts.len() < 2 || !fin(width) {
                    return None;
                }
                let pts = pts.into_iter().map(|p| [cx(p[0]), cy(p[1])]).collect();
                Some(Prim::Line { pts, color, width: width.clamp(0.1, 50.0), dash })
            }
            Prim::Polygon { pts, fill, stroke } => {
                if !pts_ok(&pts) || pts.len() < 3 {
                    return None;
                }
                let pts = pts.into_iter().map(|p| [cx(p[0]), cy(p[1])]).collect();
                Some(Prim::Polygon { pts, fill, stroke: clamp_stroke(stroke) })
            }
            Prim::Wedge { cx: x, cy: y, r_outer, r_inner, a0, a1, fill, stroke } => {
                if ![x, y, r_outer, r_inner, a0, a1].iter().all(|v| fin(*v)) {
                    return None;
                }
                let (x, y) = (cx(x), cy(y));
                let maxr = x.min(w - x).min(y).min(h - y);
                let ro = r_outer.min(maxr);
                if ro <= 0.0 {
                    return None;
                }
                Some(Prim::Wedge { cx: x, cy: y, r_outer: ro, r_inner: r_inner.clamp(0.0, ro), a0, a1, fill, stroke: clamp_stroke(stroke) })
            }
            Prim::Circle { cx: x, cy: y, r, fill, stroke } => {
                if !(fin(x) && fin(y) && fin(r)) {
                    return None;
                }
                let (x, y) = (cx(x), cy(y));
                let r = r.min(x.min(w - x).min(y).min(h - y));
                if r <= 0.0 {
                    return None;
                }
                Some(Prim::Circle { cx: x, cy: y, r, fill, stroke: clamp_stroke(stroke) })
            }
            Prim::Text { x, y, text, size, color, bold, align, valign, rotation } => {
                if !(fin(x) && fin(y) && fin(size) && fin(rotation)) || text.is_empty() {
                    return None;
                }
                Some(Prim::Text { x: cx(x), y: cy(y), text, size: size.clamp(1.0, 200.0), color, bold, align, valign, rotation })
            }
        })
        .collect()
}

#[cfg(test)]
mod smooth_tests {
    use super::*;

    #[test]
    fn long_runs_are_smoothed_uniformly_within_the_cap() {
        let plot = Bx { x: 0.0, y: 0.0, w: 3000.0, h: 100.0 };
        let pts: Vec<[f32; 2]> = (0..1500).map(|i| [i as f32 * 2.0, if i % 2 == 0 { 20.0 } else { 80.0 }]).collect();
        let out = smooth_run(pts, plot);
        assert!(out.len() <= MAX_LINE_PTS * 2 + 1, "{}", out.len());
        // Two sub-segments per segment all the way to the end, not 100 each and then a straight tail.
        assert_eq!(out.len(), 1499 * 2 + 1);
        assert_eq!(out.last().copied(), Some([2998.0, 80.0]));
    }

    #[test]
    fn spline_overshoot_is_clamped_to_the_plot() {
        let plot = Bx { x: 0.0, y: 10.0, w: 300.0, h: 100.0 };
        // A step right at the plot edges: Catmull-Rom overshoots past both.
        let pts = vec![[0.0, 110.0], [100.0, 110.0], [110.0, 10.0], [200.0, 10.0], [300.0, 10.0]];
        let out = smooth_run(pts, plot);
        assert!(out.len() > 5);
        assert!(out.iter().all(|p| (0.0..=300.0).contains(&p[0]) && (10.0..=110.0).contains(&p[1])));
    }
}
