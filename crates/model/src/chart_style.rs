//! The chart Style page's styles: how each draws a chart's background, its series, its text and
//! its lines. Known by the numbers files give them (`cs:chartStyle id`); laid out from what the
//! reference saves for a column chart in each.

use crate::chart_palette::ColorMod;
use crate::features::{LabelPos, LabelText, LegendPos};

/// Where a style's colour comes from: a theme colour (`Theme::colors` slot) or the series' own.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Slot {
    Theme(usize),
    Series,
}

/// A colour a style names, with its changes.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Shade {
    pub slot: Slot,
    pub mods: &'static [ColorMod],
}

/// How a part is filled.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Paint {
    None,
    Solid(Shade),
    /// Colour stops (where, in thousandths of a percent) down the part (`a:lin` 90°), or out from a
    /// point (`a:path circle`, at this place across and down, in thousandths of a percent).
    Gradient {
        stops: &'static [(u32, Shade)],
        from: Option<(i32, i32)>,
    },
    /// Colour stops from the right edge to the left (`a:lin` 180°): a bar's gradient.
    Leftward(&'static [(u32, Shade)]),
    /// Two colours in a pattern (`a:pattFill prst`): thin horizontal or diagonal lines.
    Pattern {
        preset: Pattern,
        fg: Shade,
        bg: Shade,
    },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Pattern {
    /// `narHorz`
    NarrowHorizontal,
    /// `narVert`
    NarrowVertical,
    /// `ltDnDiag`
    LightDownwardDiagonal,
    /// `ltUpDiag`
    LightUpwardDiagonal,
    /// `dkDnDiag`
    DarkDownwardDiagonal,
}

impl Pattern {
    pub fn preset(self) -> &'static str {
        match self {
            Pattern::NarrowHorizontal => "narHorz",
            Pattern::NarrowVertical => "narVert",
            Pattern::LightDownwardDiagonal => "ltDnDiag",
            Pattern::LightUpwardDiagonal => "ltUpDiag",
            Pattern::DarkDownwardDiagonal => "dkDnDiag",
        }
    }
}

/// Text a style sets: size in points, bold, in capitals, letter spacing in hundredths of a point.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Type {
    pub size: f32,
    pub bold: bool,
    pub caps: bool,
    pub spacing: i32,
    pub color: Shade,
}

/// A line: its colour and width in points.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Stroke {
    pub color: Shade,
    pub width: f32,
}

/// Markers a style puts on a line's points: round or diamond, `size` points across, filled
/// `fill`, with an edge (none: unedged).
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Marker {
    pub diamond: bool,
    pub size: u8,
    pub fill: Shade,
    pub edge: Option<Stroke>,
}

/// What a style puts round a series' columns.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Effect {
    None,
    /// A shadow inside the edges, in the series' colour (`a:innerShdw`).
    InnerShadow,
    /// A soft halo in the series' colour (`a:glow`).
    Glow,
    /// A soft grey shadow all round (`a:outerShdw`), blurred this many points.
    Shadow(u8),
}

/// One style of the Style page.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ChartStyle {
    pub id: u16,
    /// The chart area: its fill and its edge.
    pub area: Paint,
    pub area_line: Option<Shade>,
    /// The plot area behind the series.
    pub plot: Paint,
    pub legend_fill: Paint,
    pub title: Type,
    /// Axis labels and the legend (9 pt); the category labels in capitals with this spacing.
    pub text: Shade,
    pub category_caps: Option<i32>,
    pub category_size: f32,
    pub labels: Type,
    pub series: Paint,
    pub series_line: Option<Stroke>,
    pub effect: Effect,
    pub grid: Shade,
    pub minor_grid: Shade,
    /// The category axis's line.
    pub axis_line: Option<Stroke>,
    /// Data labels turned a quarter turn (reading upwards), and on a box of this colour.
    pub label_turn: bool,
    pub label_box: Option<Shade>,
    /// A line chart's series: the line (drawn double: `cmpd dbl`, or dotted: `sysDot`), and
    /// markers on its points.
    pub line: Stroke,
    pub line_double: bool,
    pub line_dots: bool,
    pub marker: Option<Marker>,
    /// Picking it brings in the chart's title; sets a doughnut's hole (percent; 0 leaves it).
    pub title_on: bool,
    pub hole: u8,
    /// What picking it sets: the legend's side, the value axis shown, the value axis's major and
    /// minor gridlines and the category gridlines, data labels turned on at a place (`None`
    /// leaves them as they are), and the columns' gap width and overlap.
    pub legend: LegendPos,
    pub value_axis: bool,
    pub value_lines: bool,
    pub minor_lines: bool,
    pub category_lines: bool,
    pub minor_category_lines: bool,
    pub drop_lines: bool,
    pub labels_at: Option<LabelPos>,
    /// What the labels it turns on say (`None` leaves it).
    pub labels_say: Option<LabelText>,
    pub gap: u16,
    pub overlap: i8,
}

const LT1: usize = 0;
const DK1: usize = 1;
const DK2: usize = 3;
const ACCENT1: usize = 4;

const fn theme(slot: usize, mods: &'static [ColorMod]) -> Shade {
    Shade { slot: Slot::Theme(slot), mods }
}

const fn series(mods: &'static [ColorMod]) -> Shade {
    Shade { slot: Slot::Series, mods }
}

/// The text colour lightened: `lumMod` the rest, `lumOff` this.
const fn grey(lighter: u32) -> Shade {
    match lighter {
        5 => theme(DK1, &[ColorMod::LumMod(5000), ColorMod::LumOff(95000)]),
        15 => theme(DK1, &[ColorMod::LumMod(15000), ColorMod::LumOff(85000)]),
        25 => theme(DK1, &[ColorMod::LumMod(25000), ColorMod::LumOff(75000)]),
        50 => theme(DK1, &[ColorMod::LumMod(50000), ColorMod::LumOff(50000)]),
        65 => theme(DK1, &[ColorMod::LumMod(65000), ColorMod::LumOff(35000)]),
        _ => theme(DK1, &[ColorMod::LumMod(75000), ColorMod::LumOff(25000)]),
    }
}

const fn ty(size: f32, bold: bool, color: Shade) -> Type {
    Type { size, bold, caps: false, spacing: 0, color }
}

const WHITE: Shade = theme(LT1, &[]);

/// The halo [`Effect::Glow`] puts round a series: its colour, more saturated, a quarter opaque.
pub const GLOW: Shade = series(&[ColorMod::SatMod(175000), ColorMod::Alpha(25000)]);
const PLAIN_SERIES: Paint = Paint::Solid(series(&[]));

/// A light chart: white with a hairline edge, text in greys.
const LIGHT: ChartStyle = ChartStyle {
    id: 201,
    area: Paint::Solid(WHITE),
    area_line: Some(grey(15)),
    plot: Paint::None,
    legend_fill: Paint::None,
    title: ty(14.0, false, grey(65)),
    text: grey(65),
    category_caps: None,
    category_size: 9.0,
    labels: ty(9.0, false, grey(75)),
    series: PLAIN_SERIES,
    series_line: None,
    effect: Effect::None,
    grid: grey(15),
    minor_grid: grey(5),
    axis_line: Some(Stroke { color: grey(15), width: 0.75 }),
    label_turn: false,
    label_box: None,
    title_on: false,
    hole: 0,
    line: Stroke { color: series(&[]), width: 2.25 },
    line_double: false,
    line_dots: false,
    marker: None,
    legend: LegendPos::Bottom,
    value_axis: true,
    value_lines: true,
    minor_lines: false,
    category_lines: false,
    minor_category_lines: false,
    drop_lines: false,
    labels_at: None,
    labels_say: None,
    gap: 219,
    overlap: -27,
};

/// The Style page in order, Style 1 to Style 14.
pub const STYLES: [ChartStyle; 14] = [
    LIGHT,
    ChartStyle {
        id: 202,
        title: Type { size: 16.0, bold: true, caps: true, spacing: 120, color: grey(65) },
        category_caps: Some(120),
        category_size: 8.0,
        labels: ty(8.0, false, grey(50)),
        label_turn: true,
        legend: LegendPos::Top,
        value_axis: false,
        value_lines: false,
        category_lines: true,
        labels_at: Some(LabelPos::OutsideEnd),
        gap: 444,
        overlap: -90,
        ..LIGHT
    },
    ChartStyle {
        id: 203,
        title: Type { size: 18.0, bold: true, caps: true, spacing: 150, color: grey(50) },
        series: Paint::Pattern {
            preset: Pattern::NarrowHorizontal,
            fg: series(&[]),
            bg: series(&[ColorMod::LumMod(20000), ColorMod::LumOff(80000)]),
        },
        effect: Effect::InnerShadow,
        axis_line: Some(Stroke { color: grey(25), width: 1.5 }),
        legend: LegendPos::Top,
        value_lines: false,
        gap: 164,
        overlap: -22,
        ..LIGHT
    },
    ChartStyle {
        id: 205,
        area: Paint::Gradient {
            stops: &[(0, WHITE), (39000, WHITE), (100_000, theme(LT1, &[ColorMod::LumMod(75000)]))],
            from: Some((50000, -80000)),
        },
        area_line: Some(grey(25)),
        legend_fill: Paint::Solid(theme(LT1, &[ColorMod::LumMod(95000), ColorMod::Alpha(39000)])),
        title: ty(18.0, true, grey(75)),
        text: grey(75),
        category_caps: Some(0),
        labels: ty(9.0, true, WHITE),
        series: Paint::Solid(series(&[ColorMod::Alpha(85000)])),
        series_line: Some(Stroke { color: theme(LT1, &[ColorMod::Alpha(50000)]), width: 0.75 }),
        grid: theme(DK1, &[ColorMod::LumMod(95000), ColorMod::LumOff(5000), ColorMod::Alpha(42000)]),
        axis_line: Some(Stroke { color: grey(75), width: 1.5 }),
        value_axis: false,
        labels_at: Some(LabelPos::InsideEnd),
        gap: 65,
        overlap: 0,
        ..LIGHT
    },
    ChartStyle {
        id: 206,
        title: Type { size: 14.0, bold: false, caps: false, spacing: 20, color: grey(50) },
        text: grey(50),
        labels: ty(9.0, false, grey(50)),
        series_line: Some(Stroke { color: series(&[ColorMod::Shade(95000)]), width: 0.75 }),
        gap: 100,
        overlap: -24,
        ..LIGHT
    },
    ChartStyle {
        id: 207,
        area_line: Some(theme(DK2, &[ColorMod::LumMod(15000), ColorMod::LumOff(85000)])),
        title: ty(16.0, true, theme(DK2, &[])),
        text: theme(DK2, &[]),
        labels: ty(9.0, false, theme(DK2, &[])),
        grid: theme(DK2, &[ColorMod::LumMod(15000), ColorMod::LumOff(85000)]),
        axis_line: Some(Stroke { color: theme(DK2, &[ColorMod::LumMod(15000), ColorMod::LumOff(85000)]), width: 0.75 }),
        gap: 100,
        overlap: -24,
        ..LIGHT
    },
    ChartStyle {
        id: 208,
        plot: Paint::Pattern { preset: Pattern::LightDownwardDiagonal, fg: grey(15), bg: WHITE },
        title: ty(16.0, true, grey(50)),
        category_lines: true,
        gap: 267,
        overlap: -43,
        ..LIGHT
    },
    ChartStyle {
        id: 209,
        area: Paint::Gradient {
            stops: &[
                (0, theme(DK1, &[ColorMod::LumMod(65000), ColorMod::LumOff(35000)])),
                (100_000, theme(DK1, &[ColorMod::LumMod(85000), ColorMod::LumOff(15000)])),
            ],
            from: Some((50000, 50000)),
        },
        area_line: None,
        title: Type { size: 16.0, bold: true, caps: false, spacing: 100, color: theme(LT1, &[ColorMod::LumMod(95000)]) },
        text: theme(LT1, &[ColorMod::LumMod(85000)]),
        labels: ty(9.0, false, theme(LT1, &[ColorMod::LumMod(85000)])),
        grid: theme(LT1, &[ColorMod::LumMod(95000), ColorMod::Alpha(10000)]),
        axis_line: Some(Stroke { color: theme(LT1, &[ColorMod::LumMod(95000), ColorMod::Alpha(54000)]), width: 1.0 }),
        gap: 100,
        overlap: -24,
        ..LIGHT
    },
    ChartStyle {
        id: 210,
        title: Type { size: 18.0, bold: true, caps: true, spacing: 50, color: grey(65) },
        series: Paint::Gradient {
            stops: &[
                (0, series(&[])),
                (51000, series(&[ColorMod::Alpha(75000)])),
                (75000, series(&[ColorMod::LumMod(60000), ColorMod::LumOff(40000)])),
                (100_000, series(&[ColorMod::LumMod(20000), ColorMod::LumOff(80000), ColorMod::Alpha(15000)])),
            ],
            from: None,
        },
        grid: grey(5),
        gap: 355,
        overlap: -70,
        ..LIGHT
    },
    ChartStyle {
        id: 211,
        title: Type { size: 18.0, bold: false, caps: false, spacing: 50, color: grey(65) },
        text: grey(50),
        labels: ty(9.0, false, grey(65)),
        series: Paint::None,
        series_line: Some(Stroke { color: series(&[]), width: 2.0 }),
        axis_line: None,
        legend: LegendPos::Top,
        value_lines: false,
        gap: 164,
        overlap: -35,
        ..LIGHT
    },
    ChartStyle { id: 212, title: ty(20.0, false, grey(65)), legend: LegendPos::Top, minor_lines: true, gap: 199, overlap: 0, ..LIGHT },
    ChartStyle {
        id: 213,
        area: Paint::Solid(grey(75)),
        title: ty(14.0, true, theme(LT1, &[ColorMod::LumMod(85000)])),
        text: theme(LT1, &[ColorMod::LumMod(75000)]),
        labels: ty(9.0, false, theme(LT1, &[ColorMod::LumMod(75000)])),
        series: Paint::None,
        series_line: Some(Stroke { color: series(&[]), width: 0.75 }),
        effect: Effect::Glow,
        grid: grey(75),
        axis_line: None,
        legend: LegendPos::Top,
        category_lines: true,
        gap: 315,
        overlap: -40,
        ..LIGHT
    },
    ChartStyle {
        id: 215,
        title: Type { size: 16.0, bold: false, caps: false, spacing: 50, color: grey(65) },
        series: Paint::Solid(series(&[ColorMod::Alpha(70000)])),
        grid: grey(5),
        axis_line: Some(Stroke { color: grey(25), width: 1.25 }),
        gap: 80,
        overlap: 25,
        ..LIGHT
    },
    ChartStyle { id: 340, title: ty(16.0, true, grey(65)), axis_line: Some(Stroke { color: grey(15), width: 1.0 }), gap: 100, overlap: -24, ..LIGHT },
];

/// A line style: a look of the column page's, its line and markers, and what it sets.
const fn line_style(id: u16, look: ChartStyle, width: f32) -> ChartStyle {
    ChartStyle {
        id,
        line: Stroke { color: series(&[]), width },
        label_turn: false,
        labels_at: None,
        legend: LegendPos::Bottom,
        value_axis: true,
        value_lines: true,
        minor_lines: false,
        category_lines: false,
        minor_category_lines: false,
        drop_lines: false,
        ..look
    }
}

const fn round(size: u8, fill: Shade, edge: Option<Stroke>) -> Option<Marker> {
    Some(Marker { diamond: false, size, fill, edge })
}

/// The thin edge in the series' own colour most markers have.
const SERIES_EDGE: Option<Stroke> = Some(Stroke { color: series(&[]), width: 0.75 });

/// Gridlines a little see-through: the light grey at this opacity (thousandths of a percent).
const fn faint(alpha: u32) -> Shade {
    match alpha {
        32000 => theme(DK1, &[ColorMod::LumMod(15000), ColorMod::LumOff(85000), ColorMod::Alpha(32000)]),
        _ => theme(DK1, &[ColorMod::LumMod(15000), ColorMod::LumOff(85000), ColorMod::Alpha(54000)]),
    }
}

/// The Style page for line charts, Style 1 to Style 13: the column page's looks in another
/// order, with lines of their own widths and markers.
pub const LINE_STYLES: [ChartStyle; 13] = [
    line_style(227, LIGHT, 2.25),
    ChartStyle { marker: round(17, series(&[]), None), labels_at: Some(LabelPos::Center), value_axis: false, ..line_style(228, STYLES[3], 2.5) },
    ChartStyle {
        title: Type { size: 14.0, bold: false, caps: false, spacing: 20, color: grey(50) },
        plot: Paint::Gradient { stops: &[(0, WHITE), (100_000, theme(LT1, &[ColorMod::LumMod(95000)]))], from: None },
        labels: ty(9.0, false, grey(65)),
        drop_lines: true,
        value_lines: false,
        ..line_style(230, LIGHT, 1.75)
    },
    line_style(231, STYLES[5], 2.5),
    ChartStyle { grid: faint(54000), minor_grid: faint(54000), category_lines: true, minor_category_lines: true, ..line_style(232, STYLES[6], 1.75) },
    line_style(233, STYLES[7], 2.75),
    ChartStyle {
        title: ty(14.0, false, theme(DK1, &[])),
        line: Stroke { color: series(&[ColorMod::Shade(95000), ColorMod::SatMod(105000)]), width: 1.5 },
        marker: round(17, WHITE, None),
        labels: ty(9.0, true, series(&[])),
        labels_at: Some(LabelPos::Center),
        category_size: 10.0,
        value_axis: false,
        value_lines: false,
        ..line_style(234, LIGHT, 1.5)
    },
    ChartStyle { legend: LegendPos::Top, minor_lines: true, ..line_style(235, STYLES[10], 3.0) },
    ChartStyle { legend: LegendPos::Top, category_lines: true, effect: Effect::Glow, ..line_style(236, STYLES[11], 1.75) },
    ChartStyle {
        title: Type { size: 18.0, bold: true, caps: true, spacing: 150, color: grey(50) },
        line_double: true,
        grid: faint(32000),
        axis_line: Some(Stroke { color: grey(15), width: 0.25 }),
        legend: LegendPos::Top,
        category_lines: true,
        ..line_style(237, LIGHT, 3.0)
    },
    ChartStyle {
        marker: Some(Marker { diamond: true, size: 6, fill: series(&[]), edge: SERIES_EDGE }),
        legend: LegendPos::Top,
        category_lines: true,
        value_lines: false,
        ..line_style(239, STYLES[1], 1.75)
    },
    ChartStyle { marker: round(5, series(&[]), SERIES_EDGE), ..line_style(332, LIGHT, 2.25) },
    line_style(342, STYLES[13], 2.75),
];

/// The Style page for radar charts, Style 1 to Style 8: lines like the line styles' on the column
/// page's looks (two of them its own ids), most with the legend on top.
pub const RADAR_STYLES: [ChartStyle; 8] = [
    ChartStyle { legend: LegendPos::Top, ..line_style(317, LIGHT, 2.25) },
    ChartStyle {
        title: Type { size: 18.0, bold: true, caps: true, spacing: 150, color: grey(50) },
        line_dots: true,
        legend: LegendPos::Top,
        ..line_style(318, LIGHT, 2.0)
    },
    line_style(206, STYLES[4], 1.25),
    ChartStyle {
        area: Paint::Gradient { stops: &[(43000, WHITE), (100_000, theme(LT1, &[ColorMod::LumMod(95000)]))], from: None },
        title: Type { size: 16.0, bold: false, caps: false, spacing: 70, color: grey(50) },
        text: grey(50),
        labels: ty(9.0, false, grey(50)),
        line: Stroke { color: series(&[ColorMod::Alpha(30000)]), width: 4.0 },
        legend: LegendPos::Top,
        ..line_style(319, LIGHT, 4.0)
    },
    ChartStyle {
        title: Type { size: 14.0, bold: false, caps: false, spacing: 50, color: theme(LT1, &[ColorMod::LumMod(85000)]) },
        grid: theme(LT1, &[ColorMod::Alpha(20000)]),
        legend: LegendPos::Top,
        category_lines: true,
        ..line_style(320, STYLES[11], 2.25)
    },
    line_style(207, STYLES[5], 2.5),
    ChartStyle { title: ty(14.0, true, theme(LT1, &[ColorMod::LumMod(85000)])), legend: LegendPos::Top, ..line_style(321, STYLES[7], 2.75) },
    ChartStyle { legend: LegendPos::Top, ..line_style(351, STYLES[13], 2.75) },
];

/// A pie style: a look of the column page's, its slices' fill, edge and effect, its legend, and
/// the labels it brings in (what they say and where).
const fn pie_style(id: u16, look: ChartStyle, edge: Option<Stroke>, legend: LegendPos, labels: Option<(LabelText, LabelPos)>) -> ChartStyle {
    let (labels_say, labels_at) = match labels {
        Some((say, at)) => (Some(say), Some(at)),
        None => (None, None),
    };
    ChartStyle { id, series: PLAIN_SERIES, series_line: edge, effect: Effect::None, label_turn: false, legend, labels_at, labels_say, ..look }
}

const SHARE: LabelText = LabelText::of(false, false, false, true);
const NAME_AND_SHARE: LabelText = LabelText::of(false, true, false, true);
const WHITE_EDGE: Option<Stroke> = Some(Stroke { color: WHITE, width: 1.5 });

/// The Style page for pie charts, Style 1 to Style 12: each slice filled, edged and shadowed
/// its way, most bringing in labels with the slices' shares.
pub const PIE_STYLES: [ChartStyle; 12] = [
    pie_style(251, LIGHT, WHITE_EDGE, LegendPos::Bottom, None),
    ChartStyle {
        title: Type { size: 18.0, bold: true, caps: true, spacing: 150, color: grey(50) },
        series: Paint::Pattern {
            preset: Pattern::LightUpwardDiagonal,
            fg: series(&[]),
            bg: series(&[ColorMod::LumMod(20000), ColorMod::LumOff(80000)]),
        },
        effect: Effect::InnerShadow,
        ..pie_style(252, LIGHT, WHITE_EDGE, LegendPos::Top, None)
    },
    ChartStyle {
        effect: Effect::Shadow(20),
        labels: ty(10.0, true, WHITE),
        label_box: Some(grey(65)),
        ..pie_style(253, STYLES[3], None, LegendPos::Right, Some((SHARE, LabelPos::Center)))
    },
    ChartStyle {
        labels: ty(9.0, false, grey(65)),
        ..pie_style(
            254,
            STYLES[4],
            Some(Stroke { color: series(&[ColorMod::Shade(95000)]), width: 0.75 }),
            LegendPos::Bottom,
            Some((SHARE, LabelPos::Center)),
        )
    },
    pie_style(255, STYLES[5], None, LegendPos::Bottom, Some((SHARE, LabelPos::Center))),
    ChartStyle {
        area: Paint::Pattern {
            preset: Pattern::DarkDownwardDiagonal,
            fg: WHITE,
            bg: theme(DK1, &[ColorMod::LumMod(10000), ColorMod::LumOff(90000)]),
        },
        plot: Paint::None,
        series: Paint::Gradient { stops: &[(0, series(&[])), (100_000, series(&[ColorMod::LumMod(60000), ColorMod::LumOff(40000)]))], from: None },
        labels: ty(9.0, false, grey(75)),
        ..pie_style(256, STYLES[6], WHITE_EDGE, LegendPos::Right, Some((SHARE, LabelPos::Center)))
    },
    pie_style(257, STYLES[7], None, LegendPos::Bottom, Some((SHARE, LabelPos::Center))),
    ChartStyle {
        title: Type { size: 14.0, bold: true, caps: true, spacing: 50, color: grey(65) },
        labels: ty(9.0, true, WHITE),
        ..pie_style(258, LIGHT, None, LegendPos::Top, Some((SHARE, LabelPos::InsideEnd)))
    },
    ChartStyle {
        title: Type { size: 16.0, bold: true, caps: true, spacing: 0, color: grey(65) },
        effect: Effect::Shadow(5),
        labels: ty(10.0, true, series(&[])),
        ..pie_style(259, LIGHT, None, LegendPos::None, Some((NAME_AND_SHARE, LabelPos::OutsideEnd)))
    },
    ChartStyle {
        area: Paint::Solid(theme(ACCENT1, &[])),
        area_line: Some(theme(ACCENT1, &[])),
        title: Type { size: 15.0, bold: true, caps: true, spacing: 100, color: WHITE },
        text: WHITE,
        series: Paint::Solid(WHITE),
        labels: ty(9.0, true, theme(ACCENT1, &[])),
        ..pie_style(260, LIGHT, Some(Stroke { color: theme(ACCENT1, &[]), width: 1.5 }), LegendPos::None, Some((NAME_AND_SHARE, LabelPos::InsideEnd)))
    },
    ChartStyle {
        area: Paint::Pattern { preset: Pattern::DarkDownwardDiagonal, fg: theme(LT1, &[ColorMod::LumMod(95000)]), bg: WHITE },
        title: ty(18.0, true, grey(65)),
        effect: Effect::Shadow(25),
        labels: ty(9.0, true, WHITE),
        ..pie_style(261, LIGHT, None, LegendPos::Bottom, Some((NAME_AND_SHARE, LabelPos::InsideEnd)))
    },
    ChartStyle { title: ty(16.0, true, grey(65)), ..pie_style(344, LIGHT, None, LegendPos::Bottom, Some((NAME_AND_SHARE, LabelPos::InsideEnd))) },
];

/// A bar style: a look of the column page's (the value axis kept), its spacing and switches.
const fn bar_style(id: u16, look: ChartStyle, gap: u16, overlap: i8) -> ChartStyle {
    ChartStyle { id, gap, overlap, value_axis: true, label_turn: false, labels_at: None, title_on: false, ..look }
}

/// The Style page for bar charts, Style 1 to Style 12: the column looks with the bars' own
/// stripes and gradients running across, and their own spacing.
pub const BAR_STYLES: [ChartStyle; 12] = [
    bar_style(216, LIGHT, 182, 0),
    ChartStyle {
        series: Paint::Pattern { preset: Pattern::NarrowVertical, fg: series(&[]), bg: series(&[ColorMod::LumMod(20000), ColorMod::LumOff(80000)]) },
        ..bar_style(217, STYLES[2], 227, -48)
    },
    ChartStyle { labels_at: Some(LabelPos::InsideEnd), ..bar_style(218, STYLES[3], 65, 0) },
    bar_style(219, STYLES[4], 100, 0),
    bar_style(220, STYLES[5], 100, 0),
    bar_style(221, STYLES[6], 247, 0),
    ChartStyle { title_on: true, ..bar_style(222, STYLES[7], 115, -20) },
    ChartStyle {
        series: Paint::Leftward(&[
            (0, series(&[])),
            (51000, series(&[ColorMod::Alpha(75000)])),
            (75000, series(&[ColorMod::LumMod(60000), ColorMod::LumOff(40000)])),
            (100_000, series(&[ColorMod::LumMod(20000), ColorMod::LumOff(80000), ColorMod::Alpha(15000)])),
        ]),
        grid: grey(25),
        ..bar_style(223, STYLES[8], 326, -58)
    },
    ChartStyle { axis_line: Some(Stroke { color: grey(15), width: 0.75 }), ..bar_style(224, STYLES[9], 227, -48) },
    bar_style(225, STYLES[10], 269, 0),
    bar_style(339, STYLES[11], 182, -50),
    bar_style(341, STYLES[13], 115, -20),
];

/// An area style: a look of the column page's and its switches.
const fn area_style(id: u16, look: ChartStyle, legend: LegendPos) -> ChartStyle {
    ChartStyle { id, legend, value_axis: true, label_turn: false, labels_at: None, title_on: false, category_lines: false, ..look }
}

/// The Style page for area charts, Style 1 to Style 10: the column looks on areas, some
/// see-through or shaded across, with an inner shadow.
pub const AREA_STYLES: [ChartStyle; 10] = [
    area_style(276, LIGHT, LegendPos::Bottom),
    ChartStyle {
        title: ty(18.0, true, theme(LT1, &[ColorMod::LumMod(85000)])),
        area_line: Some(theme(LT1, &[ColorMod::LumMod(75000)])),
        series: Paint::Leftward(&[(0, series(&[])), (100_000, series(&[ColorMod::LumMod(75000)]))]),
        effect: Effect::InnerShadow,
        drop_lines: true,
        ..area_style(277, STYLES[11], LegendPos::Top)
    },
    ChartStyle {
        series: Paint::Pattern {
            preset: Pattern::LightUpwardDiagonal,
            fg: series(&[]),
            bg: series(&[ColorMod::LumMod(20000), ColorMod::LumOff(80000)]),
        },
        value_lines: true,
        ..area_style(278, STYLES[2], LegendPos::Top)
    },
    ChartStyle {
        series: Paint::Solid(series(&[ColorMod::Alpha(85000)])),
        series_line: None,
        effect: Effect::InnerShadow,
        title_on: true,
        ..area_style(279, STYLES[3], LegendPos::Bottom)
    },
    area_style(280, STYLES[4], LegendPos::Bottom),
    area_style(281, STYLES[5], LegendPos::Bottom),
    ChartStyle { category_lines: true, ..area_style(282, STYLES[6], LegendPos::Bottom) },
    area_style(283, STYLES[7], LegendPos::Bottom),
    ChartStyle {
        series: Paint::Solid(series(&[ColorMod::Alpha(74000)])),
        effect: Effect::InnerShadow,
        labels: ty(10.0, true, series(&[ColorMod::LumMod(50000)])),
        labels_at: Some(LabelPos::Auto),
        grid: grey(15),
        ..area_style(284, STYLES[8], LegendPos::Bottom)
    },
    area_style(346, STYLES[13], LegendPos::Bottom),
];

/// A scatter style: a look of the column page's, its markers, its legend; gridlines both ways.
const fn scatter_style(id: u16, look: ChartStyle, marker: Option<Marker>, legend: LegendPos) -> ChartStyle {
    ChartStyle {
        id,
        marker,
        legend,
        value_axis: true,
        value_lines: true,
        category_lines: true,
        minor_lines: false,
        minor_category_lines: false,
        label_turn: false,
        labels_at: None,
        title_on: false,
        effect: Effect::None,
        ..look
    }
}

/// The Style page for scatter charts, Style 1 to Style 12: mostly markers of their own.
pub const SCATTER_STYLES: [ChartStyle; 12] = [
    scatter_style(240, LIGHT, round(5, series(&[]), SERIES_EDGE), LegendPos::Bottom),
    scatter_style(
        241,
        ChartStyle { category_caps: None, category_size: 9.0, ..STYLES[1] },
        Some(Marker { diamond: true, size: 6, fill: series(&[]), edge: SERIES_EDGE }),
        LegendPos::Top,
    ),
    scatter_style(242, STYLES[5], round(5, series(&[]), SERIES_EDGE), LegendPos::Bottom),
    ChartStyle {
        minor_lines: true,
        minor_category_lines: true,
        ..scatter_style(
            243,
            STYLES[10],
            round(
                6,
                theme(LT1, &[ColorMod::Alpha(0)]),
                Some(Stroke { color: series(&[ColorMod::LumMod(75000), ColorMod::Alpha(70000)]), width: 2.75 }),
            ),
            LegendPos::Top,
        )
    },
    ChartStyle {
        area: Paint::Gradient { stops: &[(43000, WHITE), (100_000, theme(LT1, &[ColorMod::LumMod(95000)]))], from: None },
        title: Type { size: 16.0, bold: false, caps: false, spacing: 70, color: grey(50) },
        ..scatter_style(244, LIGHT, round(4, series(&[]), SERIES_EDGE), LegendPos::Top)
    },
    ChartStyle {
        effect: Effect::Glow,
        ..scatter_style(245, STYLES[11], round(3, series(&[ColorMod::LumMod(60000), ColorMod::LumOff(40000)]), None), LegendPos::Top)
    },
    scatter_style(246, STYLES[4], round(5, series(&[]), Some(Stroke { color: series(&[ColorMod::Shade(95000)]), width: 0.75 })), LegendPos::Bottom),
    ChartStyle {
        area: Paint::Solid(theme(ACCENT1, &[])),
        area_line: Some(theme(ACCENT1, &[])),
        title: Type { size: 15.0, bold: true, caps: true, spacing: 100, color: WHITE },
        text: WHITE,
        ..scatter_style(247, LIGHT, round(6, series(&[]), Some(Stroke { color: WHITE, width: 1.75 })), LegendPos::Top)
    },
    scatter_style(248, STYLES[7], round(6, series(&[]), SERIES_EDGE), LegendPos::Bottom),
    ChartStyle {
        title_on: true,
        ..scatter_style(249, STYLES[12], round(6, WHITE, Some(Stroke { color: series(&[ColorMod::Alpha(60000)]), width: 3.0 })), LegendPos::Bottom)
    },
    scatter_style(
        250,
        ChartStyle { plot: Paint::None, ..STYLES[6] },
        Some(Marker { diamond: true, size: 6, fill: WHITE, edge: Some(Stroke { color: series(&[]), width: 1.25 }) }),
        LegendPos::Bottom,
    ),
    scatter_style(343, STYLES[13], round(6, series(&[]), SERIES_EDGE), LegendPos::Bottom),
];

/// The Style page for doughnut charts, Style 1 to Style 10: the pie styles but two, each with
/// its hole.
pub const DOUGHNUT_STYLES: [ChartStyle; 10] = [
    ChartStyle { hole: 75, ..PIE_STYLES[0] },
    ChartStyle { hole: 70, ..PIE_STYLES[1] },
    ChartStyle { hole: 50, ..PIE_STYLES[2] },
    ChartStyle { hole: 75, ..PIE_STYLES[3] },
    ChartStyle { hole: 75, ..PIE_STYLES[4] },
    ChartStyle { hole: 70, ..PIE_STYLES[5] },
    ChartStyle { hole: 75, ..PIE_STYLES[6] },
    ChartStyle { hole: 50, ..PIE_STYLES[7] },
    ChartStyle { hole: 50, ..PIE_STYLES[9] },
    ChartStyle { hole: 70, ..PIE_STYLES[10] },
];

/// The Style page for a chart of this kind: line and pie charts' own, the column page's for the
/// rest.
pub fn styles_for(kind: crate::features::ChartKind) -> &'static [ChartStyle] {
    use crate::features::ChartKind as K;
    match kind {
        K::Line | K::LineMarkers | K::LineStacked | K::LineStackedMarkers | K::LineStacked100 | K::LineStacked100Markers => &LINE_STYLES,
        K::Doughnut => &DOUGHNUT_STYLES,
        K::Pie | K::PieOfPie | K::BarOfPie => &PIE_STYLES,
        K::BarClustered | K::BarStacked | K::BarStacked100 => &BAR_STYLES,
        K::Area | K::AreaStacked | K::AreaStacked100 => &AREA_STYLES,
        K::Scatter | K::ScatterLines | K::ScatterLinesNoMarkers => &SCATTER_STYLES,
        K::Radar | K::RadarMarkers => &RADAR_STYLES,
        _ => &STYLES,
    }
}

/// Whether a style's category axis has tick marks (outward, between the categories), as the
/// reference writes its styles: a few bar and line styles and most area ones.
pub fn category_ticks(id: u16) -> bool {
    matches!(id, 221 | 224 | 225 | 231 | 276 | 278 | 280 | 281 | 283 | 346)
}

/// The style with this id (`cs:chartStyle id`); `None` for one not on a Style page.
pub fn by_id(id: u16) -> Option<&'static ChartStyle> {
    STYLES
        .iter()
        .chain(LINE_STYLES.iter())
        .chain(PIE_STYLES.iter())
        .chain(BAR_STYLES.iter())
        .chain(AREA_STYLES.iter())
        .chain(SCATTER_STYLES.iter())
        .chain(RADAR_STYLES.iter())
        .find(|s| s.id == id)
}

/// The style with this id as a chart of this kind has it: its own Style page's first (a radar
/// chart's 206 is a line on the column page's 206), then any page's.
pub fn by_id_for(kind: crate::features::ChartKind, id: u16) -> Option<&'static ChartStyle> {
    styles_for(kind).iter().find(|s| s.id == id).or_else(|| by_id(id))
}

/// The colour of `shade` for a series coloured `series` (or none), in `theme`'s colours: red,
/// green, blue and opacity.
pub fn color_of(shade: &Shade, theme: &[[u8; 3]], series: [u8; 3]) -> [u8; 4] {
    let base = match shade.slot {
        Slot::Theme(i) => theme.get(i).copied().unwrap_or([0, 0, 0]),
        Slot::Series => series,
    };
    let [r, g, b] = crate::chart_palette::apply(base, shade.mods);
    [r, g, b, crate::chart_palette::opacity(shade.mods)]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fourteen_styles_by_their_numbers() {
        let ids: Vec<u16> = STYLES.iter().map(|s| s.id).collect();
        assert_eq!(ids, [201, 202, 203, 205, 206, 207, 208, 209, 210, 211, 212, 213, 215, 340]);
        assert!(by_id(204).is_none());
        // line and pie charts have pages of their own, found by their numbers too
        assert_eq!((LINE_STYLES.len(), PIE_STYLES.len()), (13, 12));
        assert_eq!(by_id(342).map(|s| s.line.width), Some(2.75));
        assert_eq!(by_id(260).map(|s| s.legend), Some(LegendPos::None));
        use crate::features::ChartKind as K;
        let count = |k| styles_for(k).len();
        assert_eq!((count(K::BarStacked), count(K::Area), count(K::Scatter)), (12, 10, 12));
        // every id is on one page only
        let mut ids: Vec<u16> = [&STYLES[..], &LINE_STYLES[..], &PIE_STYLES[..], &BAR_STYLES[..], &AREA_STYLES[..], &SCATTER_STYLES[..]]
            .iter()
            .flat_map(|p| p.iter().map(|s| s.id))
            .collect();
        let n = ids.len();
        ids.sort_unstable();
        ids.dedup();
        assert_eq!(ids.len(), n);
        assert_eq!(by_id(213).map(|s| s.effect), Some(Effect::Glow));
    }

    #[test]
    fn shades_take_the_series_colour_and_opacity() {
        let colors = [[255, 255, 255], [0, 0, 0], [231, 230, 230], [68, 84, 106]];
        assert_eq!(color_of(&grey(15), &colors, [0, 0, 0]), [217, 217, 217, 255]);
        assert_eq!(color_of(&series(&[ColorMod::Alpha(70000)]), &colors, [20, 96, 130]), [20, 96, 130, 179]);
        assert_eq!(color_of(&theme(DK2, &[]), &colors, [0, 0, 0]), [68, 84, 106, 255]);
    }
}
