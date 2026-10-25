//! Sheet features beyond cells: tables, conditional formatting, data validation, comments,
//! hyperlinks, charts, images, autofilter, print settings.

use gridcraft_core::{CellRef, RangeRef};
use serde::{Deserialize, Serialize};

use crate::style::{Color, Style};

// ---------------------------------------------------------------- tables

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum TotalsFn {
    #[default]
    None,
    Average,
    Count,
    CountNums,
    Max,
    Min,
    Sum,
    StdDev,
    Var,
    Custom,
}

impl TotalsFn {
    /// SUBTOTAL function number.
    pub fn subtotal_code(&self) -> Option<u32> {
        Some(match self {
            TotalsFn::Average => 101,
            TotalsFn::Count => 103,
            TotalsFn::CountNums => 102,
            TotalsFn::Max => 104,
            TotalsFn::Min => 105,
            TotalsFn::Sum => 109,
            TotalsFn::StdDev => 107,
            TotalsFn::Var => 110,
            _ => return None,
        })
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct TableColumn {
    pub name: String,
    pub totals: TotalsFn,
    /// Totals row label (first column) or custom formula.
    pub totals_label: Option<String>,
    /// Calculated column formula.
    pub formula: Option<String>,
    /// `uniqueName`: the name XML maps know the column by.
    #[serde(default)]
    pub unique_name: Option<String>,
    /// Binding to an element of an XML map. It moves with the column and goes when the column is
    /// deleted; inserted columns are unmapped.
    #[serde(default)]
    pub xml: Option<XmlColumnPr>,
}

/// A table column's binding to an XML map (`<xmlColumnPr>`).
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct XmlColumnPr {
    pub map_id: u32,
    pub xpath: String,
    /// `xmlDataType` as written in the file (`string`, `boolean`, `integer`…).
    pub data_type: String,
    pub denormalized: bool,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Table {
    pub id: u32,
    pub name: String,
    /// Whole table including header and totals rows.
    pub range: RangeRef,
    pub header_row: bool,
    pub totals_row: bool,
    pub columns: Vec<TableColumn>,
    pub style: String,
    pub banded_rows: bool,
    pub banded_cols: bool,
    pub first_col: bool,
    pub last_col: bool,
    pub filter_button: bool,
    /// Criteria of the table's AutoFilter (column offset within the table → criterion). With
    /// criteria present, hidden data rows are filtered out rather than hidden by hand, which
    /// SUBTOTAL distinguishes.
    #[serde(default)]
    pub filter: Vec<(u32, FilterCriterion)>,
    /// `tableType` when it isn't a plain worksheet table: `xml` for a table bound to an XML map.
    #[serde(default)]
    pub table_type: Option<String>,
    /// The connection (`xl/connections.xml`) an XML table is bound through.
    #[serde(default)]
    pub connection_id: Option<u32>,
    /// What a screen reader says for the table (Alt Text; `x14:table altText` in the file).
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub alt_text: String,
}

impl Table {
    pub fn data_range(&self) -> Option<RangeRef> {
        let r0 = self.range.start.row + self.header_row as u32;
        let r1 = self.range.end.row.checked_sub(self.totals_row as u32)?;
        if r1 < r0 {
            return None;
        }
        Some(RangeRef::new(CellRef::new(r0, self.range.start.col), CellRef::new(r1, self.range.end.col)))
    }
    pub fn header_range(&self) -> Option<RangeRef> {
        self.header_row.then(|| RangeRef::new(self.range.start, CellRef::new(self.range.start.row, self.range.end.col)))
    }
    pub fn totals_range(&self) -> Option<RangeRef> {
        self.totals_row.then(|| RangeRef::new(CellRef::new(self.range.end.row, self.range.start.col), self.range.end))
    }
    pub fn column_index(&self, name: &str) -> Option<usize> {
        self.columns.iter().position(|c| c.name.eq_ignore_ascii_case(name))
    }
}

// ---------------------------------------------------------------- conditional formatting

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum CfOperator {
    Between,
    NotBetween,
    Equal,
    NotEqual,
    Greater,
    Less,
    GreaterOrEqual,
    LessOrEqual,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub enum CfValueKind {
    Min,
    Max,
    Number(f64),
    Percent(f64),
    Percentile(f64),
    Formula(String),
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub enum CfRule {
    /// Cell value compared with one or two formulas.
    CellIs {
        op: CfOperator,
        a: String,
        b: Option<String>,
        style: Box<Style>,
    },
    /// Custom formula (relative to the top-left cell of the range).
    Expression {
        formula: String,
        style: Box<Style>,
    },
    ContainsText {
        text: String,
        style: Box<Style>,
    },
    NotContainsText {
        text: String,
        style: Box<Style>,
    },
    BeginsWith {
        text: String,
        style: Box<Style>,
    },
    EndsWith {
        text: String,
        style: Box<Style>,
    },
    Blanks {
        style: Box<Style>,
    },
    NoBlanks {
        style: Box<Style>,
    },
    Errors {
        style: Box<Style>,
    },
    NoErrors {
        style: Box<Style>,
    },
    Duplicate {
        style: Box<Style>,
    },
    Unique {
        style: Box<Style>,
    },
    /// Top/bottom N or N percent.
    Top10 {
        bottom: bool,
        percent: bool,
        rank: u32,
        style: Box<Style>,
    },
    AboveAverage {
        below: bool,
        equal: bool,
        std_dev: u8,
        style: Box<Style>,
    },
    /// `yesterday`, `today`, `tomorrow`, `last7Days`, `lastWeek`, `thisWeek`, `nextWeek`, `lastMonth`, `thisMonth`, `nextMonth`.
    TimePeriod {
        period: String,
        style: Box<Style>,
    },
    ColorScale {
        stops: Vec<(CfValueKind, Color)>,
    },
    DataBar {
        min: CfValueKind,
        max: CfValueKind,
        color: Color,
        gradient: bool,
        show_value: bool,
    },
    /// Icon set name (`3Arrows`, `3TrafficLights1`, `4Rating`, `5Quarters`…).
    IconSet {
        set: String,
        thresholds: Vec<CfValueKind>,
        reverse: bool,
        show_value: bool,
    },
}

/// Number of icons in an icon set (`3Arrows` → 3, `5Quarters` → 5), always 3 to 5.
pub fn icon_count(set: &str) -> usize {
    set.chars().next().and_then(|c| c.to_digit(10)).unwrap_or(3).clamp(3, 5) as usize
}

/// Excel's default icon-set thresholds: one per icon, the first being the lower bound,
/// then even percent steps (`0, 33, 67` for three icons).
pub fn default_icon_thresholds(set: &str) -> Vec<CfValueKind> {
    let k = icon_count(set);
    (0..k).map(|i| CfValueKind::Percent((i as f64 * 100.0 / k as f64).round())).collect()
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct CondFormat {
    pub ranges: Vec<RangeRef>,
    pub rule: CfRule,
    pub priority: u32,
    pub stop_if_true: bool,
}

// ---------------------------------------------------------------- data validation

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum ValidationKind {
    #[default]
    Any,
    Whole,
    Decimal,
    List,
    Date,
    Time,
    TextLength,
    Custom,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum ErrorStyle {
    #[default]
    Stop,
    Warning,
    Information,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Validation {
    pub ranges: Vec<RangeRef>,
    pub kind: ValidationKind,
    pub op: CfOperator,
    /// Formula 1 (list source: `"a,b,c"` literal list or `=$A$1:$A$5`).
    pub f1: String,
    pub f2: Option<String>,
    pub allow_blank: bool,
    pub in_cell_dropdown: bool,
    pub input_title: String,
    pub input_message: String,
    pub show_input: bool,
    pub error_title: String,
    pub error_message: String,
    pub error_style: ErrorStyle,
    pub show_error: bool,
}

impl Default for Validation {
    fn default() -> Self {
        Validation {
            ranges: vec![],
            kind: ValidationKind::Any,
            op: CfOperator::Between,
            f1: String::new(),
            f2: None,
            allow_blank: true,
            in_cell_dropdown: true,
            input_title: String::new(),
            input_message: String::new(),
            show_input: true,
            error_title: String::new(),
            error_message: String::new(),
            error_style: ErrorStyle::Stop,
            show_error: true,
        }
    }
}

// ---------------------------------------------------------------- comments, links

/// A note (classic comment) or a threaded comment.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Comment {
    pub author: String,
    pub text: String,
    /// Replies of a threaded comment (author, text).
    pub replies: Vec<(String, String)>,
    pub threaded: bool,
    pub resolved: bool,
    pub visible: bool,
    /// Threaded comments: when the thread (first entry) and each reply were written, ISO 8601 as
    /// read from the file (`dT`); empty when unknown, e.g. for comments made here.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub dates: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Hyperlink {
    /// URL, `mailto:`, a file path (`Book.xlsx`, `Book.xlsx#Sheet1!A2`), or an in-workbook location
    /// like `Sheet2!A1`.
    pub target: String,
    pub tooltip: Option<String>,
    /// The target is outside this workbook (Excel's "Existing File or Web Page"): a relationship in
    /// the file, not a `location`. A bare file name can't be told from a defined name by its text.
    #[serde(default)]
    pub external: bool,
}

// ---------------------------------------------------------------- drawing objects

/// How a floating object is pinned to the grid when its cells move or resize. Mirrors Excel's
/// Format ▸ Size and Properties ▸ Properties, stored in the file as the anchor's `editAs`:
/// `twoCell` (absent `editAs`), `oneCell` and `absolute`.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum AnchorMode {
    /// Move and size with cells (Excel's default; `<xdr:twoCellAnchor>`).
    #[default]
    MoveAndSize,
    /// Move but don't size with cells (`<xdr:twoCellAnchor editAs="oneCell">` / `<xdr:oneCellAnchor>`).
    MoveOnly,
    /// Don't move or size with cells (`editAs="absolute"` / `<xdr:absoluteAnchor>`).
    Absolute,
}

/// Position of a floating object: top-left cell plus offset, size in points.
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct Anchor {
    pub cell: CellRef,
    pub dx: f32,
    pub dy: f32,
    pub width: f32,
    pub height: f32,
    /// How the object follows its anchor cell when rows/columns move (sort, insert, delete).
    #[serde(default)]
    pub mode: AnchorMode,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum ChartKind {
    #[default]
    ColumnClustered,
    ColumnStacked,
    ColumnStacked100,
    BarClustered,
    BarStacked,
    BarStacked100,
    Line,
    LineMarkers,
    LineStacked,
    /// Lines stacked to 100% of each category's total.
    LineStacked100,
    LineStackedMarkers,
    LineStacked100Markers,
    Pie,
    /// A pie whose last points come out as one slice and are drawn again as a smaller pie
    /// (Pie of Pie) or a stacked bar (Bar of Pie) beside it.
    PieOfPie,
    BarOfPie,
    Doughnut,
    Area,
    AreaStacked,
    AreaStacked100,
    Scatter,
    /// Scatter with straight (or, with the series' smooth flag, smooth) lines and markers.
    ScatterLines,
    /// Scatter with lines and no markers.
    ScatterLinesNoMarkers,
    Bubble,
    Radar,
    RadarMarkers,
    RadarFilled,
    /// A 3-D surface over the categories (across) and series (in depth), coloured in bands of value.
    Surface,
    /// The surface drawn as lines only.
    SurfaceWireframe,
    /// The surface seen from above: bands of value as coloured regions.
    Contour,
    /// The contour's band edges as lines only.
    ContourWireframe,
    Histogram,
    /// Columns sorted from the largest down, with a line of their running total in percent.
    Pareto,
    Waterfall,
    Funnel,
    Treemap,
    Sunburst,
    BoxWhisker,
    Stock,
    Combo,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum LegendPos {
    None,
    #[default]
    Bottom,
    Top,
    Left,
    Right,
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct Series {
    /// Formula text for the series name (`Sheet1!$B$1`) or a literal.
    pub name: Option<String>,
    pub categories: Option<String>,
    pub values: String,
    /// X values for scatter / bubble sizes.
    pub bubble_sizes: Option<String>,
    pub color: Option<Color>,
    /// Combo charts: this series drawn as a line on the secondary axis.
    pub secondary: bool,
    pub kind: Option<ChartKind>,
    /// Draw line segments as a smoothed (spline) curve.
    #[serde(default)]
    pub smooth: bool,
    /// Trendlines fitted to this series (Excel allows several per series).
    #[serde(default)]
    pub trendlines: Vec<Trendline>,
}

/// Regression a trendline fits (`c:trendlineType`).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum TrendlineKind {
    #[default]
    Linear,
    /// `y = c·e^(b·x)`.
    Exponential,
    /// `y = c·ln(x) + b`.
    Logarithmic,
    /// Polynomial of this order (2..=6).
    Polynomial(u8),
    /// `y = c·x^b`.
    Power,
    /// Average of this many consecutive points (2..=255).
    MovingAverage(u32),
}

/// DrawingML preset line dash (`a:prstDash`).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum LineDash {
    #[default]
    Solid,
    Dot,
    Dash,
    LgDash,
    DashDot,
    LgDashDot,
    LgDashDotDot,
    SysDash,
    SysDot,
    SysDashDot,
    SysDashDotDot,
}

/// A chart trendline (Add Chart Element ▸ Trendline, Format Trendline pane).
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct Trendline {
    pub kind: TrendlineKind,
    /// Custom name shown in the legend; `None` = automatic ("Linear (Sales)").
    pub name: Option<String>,
    /// Forecast past the last point, in x units (categories on a category axis).
    pub forward: f64,
    /// Forecast before the first point, in x units.
    pub backward: f64,
    /// Set Intercept: fixed y at x = 0 (linear, exponential, polynomial).
    pub intercept: Option<f64>,
    pub display_equation: bool,
    pub display_r_squared: bool,
    /// Line colour; `None` = the series colour.
    pub color: Option<Color>,
    /// Line width in points; `None` = default.
    pub width: Option<f32>,
    /// Line dash; `None` = default.
    pub dash: Option<LineDash>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Chart {
    pub id: u32,
    pub kind: ChartKind,
    pub anchor: Anchor,
    pub title: Option<String>,
    pub series: Vec<Series>,
    pub legend: LegendPos,
    pub data_labels: bool,
    pub gridlines: bool,
    pub style: u32,
    pub x_title: Option<String>,
    pub y_title: Option<String>,
    /// Source range when created from a selection (for Switch Row/Column).
    pub source: Option<String>,
    pub by_rows: bool,
    /// A PivotChart's PivotTable (`Sheet2!PivotTable1`): its series are rebuilt from the report
    /// whenever the PivotTable is refreshed or its layout changes.
    #[serde(default)]
    pub pivot: Option<String>,
    /// Alternative text, what a screen reader says for the chart (`xdr:cNvPr descr`).
    #[serde(default, skip_serializing_if = "String::is_empty")]
    pub alt: String,
    /// Label position, error bars, an overlay title, up/down bars, drop and high-low lines, a
    /// data table (Add Chart Element).
    #[serde(default, skip_serializing_if = "ChartElements::is_none")]
    pub elements: ChartElements,
    /// Drawn in 3-D (column, bar, line, area, pie and bubble charts).
    #[serde(default, skip_serializing_if = "Depth::is_flat")]
    pub depth: Depth,
}

/// Whether a chart is drawn in 3-D (`c:view3D` and the `*3DChart` groups).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum Depth {
    #[default]
    Flat,
    /// The 2-D layout given depth: 3-D Clustered Column, 3-D Pie, 3-D Bubble.
    Raised,
    /// Each series in a row of its own, one behind another (3-D Column, 3-D Line, 3-D Area).
    Rows,
}

impl Depth {
    pub fn is_flat(&self) -> bool {
        *self == Depth::Flat
    }
}

/// Where data labels sit (`c:dLblPos`); `Auto` leaves it to the chart type.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum LabelPos {
    #[default]
    Auto,
    Center,
    InsideEnd,
    InsideBase,
    OutsideEnd,
    BestFit,
    Left,
    Right,
    Above,
    Below,
}

/// How long error bars are, either side of each value (`c:errValType`, `c:val`).
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
pub enum ErrorAmount {
    /// The standard error of the series' values.
    StandardError,
    /// This percent of each value.
    Percent(f64),
    /// This many standard deviations of the series' values.
    StdDev(f64),
    /// A fixed amount.
    Fixed(f64),
}

/// The chart elements beyond the title, legend, labels and gridlines.
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct ChartElements {
    #[serde(default)]
    pub label_pos: LabelPos,
    /// Error bars on every series that can have them (`c:errBars`, plus and minus).
    #[serde(default)]
    pub error_bars: Option<ErrorAmount>,
    /// The title sits over the plot, which keeps its height (`c:overlay`).
    #[serde(default)]
    pub title_overlay: bool,
    /// Line charts: bars from the first series to the last at each category (`c:upDownBars`).
    #[serde(default)]
    pub up_down_bars: bool,
    /// Line and area charts: a line from each category's highest point to the axis (`c:dropLines`).
    #[serde(default)]
    pub drop_lines: bool,
    /// Line charts: a line from each category's highest point to its lowest (`c:hiLowLines`).
    #[serde(default)]
    pub high_low_lines: bool,
    /// A table of the values under the plot, with legend keys (`c:dTable`).
    #[serde(default)]
    pub data_table: bool,
    /// The Color page's palette (`cs:colorStyle id`, see [`crate::chart_palette`]), 0 for the
    /// automatic one: the colours of series without their own.
    #[serde(default)]
    pub palette: u16,
    /// The category axis (a scatter chart's X axis) or the value axis left out (`c:delete`).
    #[serde(default)]
    pub hide_cat_axis: bool,
    #[serde(default)]
    pub hide_val_axis: bool,
    /// Gridlines across the categories, and minor gridlines on either axis (the value axis's
    /// major ones are `Chart::gridlines`).
    #[serde(default)]
    pub cat_gridlines: bool,
    #[serde(default)]
    pub minor_val_gridlines: bool,
    #[serde(default)]
    pub minor_cat_gridlines: bool,
    /// Columns and bars: the gap between categories as a percentage of a column's width
    /// (`c:gapWidth`, 150 when unset), and how far a category's columns overlap (`c:overlap`,
    /// -100 to 100; below 0 leaves gaps between them).
    #[serde(default)]
    pub gap_width: Option<u16>,
    #[serde(default)]
    pub overlap: Option<i8>,
    /// The data table without the series' colour keys (`c:showKeys` 0).
    #[serde(default)]
    pub data_table_no_keys: bool,
    /// Data labels on each series' last point only (a `c:dLbl`), as one of the Layout gallery's
    /// layouts puts them.
    #[serde(default)]
    pub last_point_labels: bool,
    /// What data labels say.
    #[serde(default)]
    pub label_text: LabelText,
    /// The Style page's style (`cs:chartStyle id`, see [`crate::chart_style`]); 0 for the plain look.
    #[serde(default)]
    pub style: u16,
    /// A doughnut's hole, percent of its size (`c:holeSize`, 10 to 90; 50 when unset).
    #[serde(default)]
    pub hole_size: Option<u8>,
}

/// What a data label says (`c:showVal`, `showCatName`, `showSerName`, `showPercent`): the
/// value by default; the category's and series' names and a pie slice's share as well or instead.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct LabelText {
    pub value: bool,
    pub category: bool,
    pub series: bool,
    pub percent: bool,
}

impl Default for LabelText {
    fn default() -> Self {
        LabelText { value: true, category: false, series: false, percent: false }
    }
}

impl LabelText {
    /// The parts a layout names, value first.
    pub const fn of(value: bool, category: bool, series: bool, percent: bool) -> Self {
        LabelText { value, category, series, percent }
    }

    /// Says something.
    pub fn any(&self) -> bool {
        self.value || self.category || self.series || self.percent
    }
}

impl ChartElements {
    pub fn is_none(&self) -> bool {
        *self == Self::default()
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Image {
    pub id: u32,
    pub anchor: Anchor,
    /// Encoded bytes (PNG/JPEG), base64 in JSON.
    #[serde(with = "crate::b64")]
    pub data: Vec<u8>,
    pub mime: String,
    pub alt: String,
    /// Turned or mirrored (`a:xfrm rot`, `flipH`, `flipV`).
    #[serde(default, skip_serializing_if = "Turn::is_none")]
    pub turn: Turn,
    /// The part of the picture shown (`a:srcRect`): fractions cut from each side.
    #[serde(default, skip_serializing_if = "Crop::is_none")]
    pub crop: Crop,
    /// Its outline, frame and shadow (`a:prstGeom`, `a:ln`, `a:outerShdw`).
    #[serde(default, skip_serializing_if = "PictureLook::is_none")]
    pub look: PictureLook,
}

/// How a picture is framed: the outline it is cut to, a frame around it, its shadow and its
/// reflection (Picture Effects).
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct PictureLook {
    pub shape: PictureShape,
    /// The frame's colour and width in points.
    pub border: Option<(Color, f32)>,
    /// Its shadow, cast outside it or falling inside it. Older documents say only `true` here:
    /// the bottom-right drop shadow.
    #[serde(deserialize_with = "shadow_or_flag")]
    pub shadow: Option<Shadow>,
    pub reflection: Option<Reflection>,
    /// How the frame is drawn and written beyond its colour and width (`a:ln`'s cap, lines, dash
    /// and join), as Picture Styles set it.
    pub frame: FrameStyle,
    /// No frame, said outright (`a:ln` with `a:noFill`), as some Picture Styles say it.
    pub no_frame: bool,
    /// Soft edges: the picture fades out over this many points inside its outline
    /// (`a:softEdge`).
    pub soft_edge: Option<f32>,
    /// What it keeps but doesn't draw, as DrawingML written back as it came: a fill behind the
    /// picture (`a:solidFill`… in its spPr) and its 3-D settings (`a:scene3d`, `a:sp3d`).
    pub backdrop_xml: String,
    pub three_d_xml: String,
    /// The outline's adjust values (`a:avLst`'s `a:gd name fmla="val N"`), as Picture Styles set
    /// a rounded rectangle's corners.
    pub adjust: Vec<(String, i64)>,
}

impl PictureLook {
    pub fn is_none(&self) -> bool {
        *self == PictureLook::default()
    }

    /// How far its 3-D camera turns it in its own plane, in degrees clockwise (`a:camera`'s
    /// `a:rot rev` with no tilt, `lat` and `lon` 0), which GridCraft draws flat: "Rotated, White"
    /// tilts the picture this way. A camera that also tilts is drawn unturned.
    pub fn camera_turn(&self) -> f32 {
        let Some(cam) = self.three_d_xml.find("<a:camera") else { return 0.0 };
        let rest = &self.three_d_xml[cam..];
        let end = rest.find("</a:camera>").or_else(|| rest.find("/>")).unwrap_or(rest.len());
        let cam = &rest[..end];
        let attr = |name: &str| -> Option<f64> {
            let at = cam.find(&format!(" {name}=\""))? + name.len() + 3;
            cam.get(at..)?.chars().take_while(|c| c.is_ascii_digit() || *c == '-').collect::<String>().parse::<f64>().ok().filter(|v| v.is_finite())
        };
        if attr("lat").unwrap_or(0.0) != 0.0 || attr("lon").unwrap_or(0.0) != 0.0 {
            return 0.0;
        }
        attr("rev").map_or(0.0, |v| (v / 60_000.0).rem_euclid(360.0) as f32)
    }

    /// Its `adj` adjust value as a fraction (a rounded rectangle's corner, of its shorter side).
    pub fn adj(&self) -> Option<f32> {
        self.adjust.iter().find(|(n, _)| n == "adj").map(|(_, v)| (*v as f32 / 100_000.0).clamp(0.0, 0.5))
    }
}

/// A shadow as documents written before shadows had their own settings keep it: `true` is the
/// bottom-right drop shadow.
fn shadow_or_flag<'de, D: serde::Deserializer<'de>>(d: D) -> Result<Option<Shadow>, D::Error> {
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Either {
        Flag(bool),
        Shadow(Option<Shadow>),
    }
    Ok(match Either::deserialize(d)? {
        Either::Flag(on) => on.then(Shadow::drop),
        Either::Shadow(s) => s,
    })
}

/// The lines a frame is drawn as (DrawingML `a:ln cmpd`): one, or two or three side by side
/// sharing its width.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum Compound {
    #[default]
    Single,
    Double,
    ThickThin,
    ThinThick,
    Triple,
}

impl Compound {
    /// Its DrawingML name.
    pub fn name(self) -> &'static str {
        match self {
            Compound::Single => "sng",
            Compound::Double => "dbl",
            Compound::ThickThin => "thickThin",
            Compound::ThinThick => "thinThick",
            Compound::Triple => "tri",
        }
    }

    /// From its DrawingML name (one line for anything else).
    pub fn from_name(n: &str) -> Compound {
        match n {
            "dbl" => Compound::Double,
            "thickThin" => Compound::ThickThin,
            "thinThick" => Compound::ThinThick,
            "tri" => Compound::Triple,
            _ => Compound::Single,
        }
    }

    /// The lines across the frame's width, from its outside in: where each starts and ends, as
    /// fractions of the width.
    pub fn lines(self) -> &'static [(f32, f32)] {
        match self {
            Compound::Single => &[(0.0, 1.0)],
            Compound::Double => &[(0.0, 0.34), (0.66, 1.0)],
            Compound::ThickThin => &[(0.0, 0.55), (0.75, 1.0)],
            Compound::ThinThick => &[(0.0, 0.25), (0.45, 1.0)],
            Compound::Triple => &[(0.0, 0.2), (0.35, 0.65), (0.8, 1.0)],
        }
    }
}

/// How a frame's ends are drawn (`a:ln cap`).
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum LineCap {
    Square,
    Round,
    Flat,
}

/// How a frame's corners are drawn (`a:miter lim`, `a:round`, `a:bevel`); a miter keeps its
/// limit, in thousandths of a percent.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub enum LineJoin {
    Round,
    Bevel,
    Miter(u32),
}

/// A frame's style beyond its colour and width; the default says nothing more.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct FrameStyle {
    pub cap: Option<LineCap>,
    pub compound: Compound,
    /// Its dash said outright as solid (`a:prstDash val="solid"`).
    pub solid: bool,
    pub join: Option<LineJoin>,
}

/// A corner, side or the centre of a box: what a shadow or reflection is scaled about
/// (DrawingML `algn`).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum BoxAlign {
    TopLeft,
    Top,
    TopRight,
    Left,
    Center,
    Right,
    BottomLeft,
    #[default]
    Bottom,
    BottomRight,
}

impl BoxAlign {
    /// Its DrawingML name.
    pub fn name(self) -> &'static str {
        match self {
            BoxAlign::TopLeft => "tl",
            BoxAlign::Top => "t",
            BoxAlign::TopRight => "tr",
            BoxAlign::Left => "l",
            BoxAlign::Center => "ctr",
            BoxAlign::Right => "r",
            BoxAlign::BottomLeft => "bl",
            BoxAlign::Bottom => "b",
            BoxAlign::BottomRight => "br",
        }
    }

    /// From its DrawingML name (the bottom edge for anything else).
    pub fn from_name(n: &str) -> BoxAlign {
        match n {
            "tl" => BoxAlign::TopLeft,
            "t" => BoxAlign::Top,
            "tr" => BoxAlign::TopRight,
            "l" => BoxAlign::Left,
            "ctr" => BoxAlign::Center,
            "r" => BoxAlign::Right,
            "bl" => BoxAlign::BottomLeft,
            "br" => BoxAlign::BottomRight,
            _ => BoxAlign::Bottom,
        }
    }

    /// Where it lies in a box, as fractions of its width and height from the top left.
    pub fn at(self) -> (f32, f32) {
        match self {
            BoxAlign::TopLeft => (0.0, 0.0),
            BoxAlign::Top => (0.5, 0.0),
            BoxAlign::TopRight => (1.0, 0.0),
            BoxAlign::Left => (0.0, 0.5),
            BoxAlign::Center => (0.5, 0.5),
            BoxAlign::Right => (1.0, 0.5),
            BoxAlign::BottomLeft => (0.0, 1.0),
            BoxAlign::Bottom => (0.5, 1.0),
            BoxAlign::BottomRight => (1.0, 1.0),
        }
    }
}

/// A shadow (DrawingML `a:outerShdw`, or `a:innerShdw` when [`Shadow::inner`]): the picture's
/// silhouette, blurred `blur` points, moved `distance` points in `direction` (degrees clockwise
/// from the right); an inner one darkens the edges inside it that `direction` points to. An
/// outer one may be scaled (`scale_x`, `scale_y` percent, negative turning it over) and leaned
/// (`skew_x`, `skew_y` degrees) about `align` first: a perspective shadow.
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Shadow {
    pub inner: bool,
    pub color: Color,
    /// 0 (clear) to 1.
    pub opacity: f32,
    pub blur: f32,
    pub distance: f32,
    pub direction: f32,
    pub scale_x: f32,
    pub scale_y: f32,
    pub skew_x: f32,
    pub skew_y: f32,
    pub align: BoxAlign,
    /// Whether it turns with the picture (`rotWithShape`).
    pub turns: bool,
    /// Its black said by name (`a:prstClr`), as Picture Effects write it; Picture Styles write
    /// it as RGB.
    pub named: bool,
}

impl Default for Shadow {
    fn default() -> Shadow {
        Shadow {
            inner: false,
            color: Color::Rgb(0),
            opacity: 1.0,
            blur: 0.0,
            distance: 0.0,
            direction: 0.0,
            scale_x: 100.0,
            scale_y: 100.0,
            skew_x: 0.0,
            skew_y: 0.0,
            align: BoxAlign::Bottom,
            turns: true,
            named: false,
        }
    }
}

/// `v` when finite, kept between `lo` and `hi`; `d` otherwise.
fn finite(v: f32, lo: f32, hi: f32, d: f32) -> f32 {
    if v.is_finite() { v.clamp(lo, hi) } else { d }
}

/// An angle in degrees as 0 up to (not including) 360.
fn turn(v: f32) -> f32 {
    let a = finite(v, -1e6, 1e6, 0.0).rem_euclid(360.0);
    if a < 360.0 { a } else { 0.0 }
}

impl Shadow {
    /// The bottom-right drop shadow ("Offset: Bottom Right").
    pub fn drop() -> Shadow {
        Shadow { opacity: 0.4, blur: 4.0, distance: 3.0, direction: 45.0, align: BoxAlign::TopLeft, turns: false, named: true, ..Shadow::default() }
    }

    /// One of Picture Effects' shadows by name, as the phone app makes it: `offsetBottomRight`…
    /// `offsetTopLeft` and `offsetCenter` (Outer), `insideTopLeft`… `insideCenter` (Inner),
    /// `perspectiveUpperLeft`, `perspectiveUpperRight`, `perspectiveBelow`, `perspectiveLowerLeft`,
    /// `perspectiveLowerRight`.
    pub fn preset(name: &str) -> Option<Shadow> {
        // the outer ones: a 3 pt drop at 40%, scaled about the side away from it
        let outer = |direction: f32, align: BoxAlign| Shadow { direction, align, ..Shadow::drop() };
        // the inner ones: 8 pt toward the edge they darken, at 50%
        let inner = |direction: f32| Shadow { inner: true, opacity: 0.5, blur: 5.0, distance: 8.0, direction, named: true, ..Shadow::default() };
        let perspective = |opacity: f32, blur: f32, distance: f32, direction: f32, scale_x: f32, scale_y: f32, skew_x: f32, align: BoxAlign| Shadow {
            opacity,
            blur,
            distance,
            direction,
            scale_x,
            scale_y,
            skew_x,
            align,
            turns: false,
            named: true,
            ..Shadow::default()
        };
        Some(match name {
            "offsetBottomRight" => outer(45.0, BoxAlign::TopLeft),
            "offsetBottom" => outer(90.0, BoxAlign::Top),
            "offsetBottomLeft" => outer(135.0, BoxAlign::TopRight),
            "offsetRight" => outer(0.0, BoxAlign::Left),
            "offsetCenter" => {
                Shadow { blur: 5.0, distance: 0.0, direction: 0.0, scale_x: 102.0, scale_y: 102.0, align: BoxAlign::Center, ..Shadow::drop() }
            }
            "offsetLeft" => outer(180.0, BoxAlign::Right),
            "offsetTopRight" => outer(315.0, BoxAlign::BottomLeft),
            "offsetTop" => outer(270.0, BoxAlign::Bottom),
            "offsetTopLeft" => outer(225.0, BoxAlign::BottomRight),
            "insideTopLeft" => inner(225.0),
            "insideTop" => inner(270.0),
            "insideTopRight" => inner(315.0),
            "insideLeft" => inner(180.0),
            // all round the inside, solid black blurred 9 pt
            "insideCenter" => Shadow { opacity: 1.0, distance: 0.0, blur: 9.0, ..inner(0.0) },
            "insideRight" => inner(0.0),
            "insideBottomLeft" => inner(135.0),
            "insideBottom" => inner(90.0),
            "insideBottomRight" => inner(45.0),
            // flattened behind it, leaning back left or right; under it; turned down in front of
            // it, leaning
            "perspectiveUpperLeft" => perspective(0.2, 6.0, 0.0, 225.0, 100.0, 23.0, 20.0, BoxAlign::BottomRight),
            "perspectiveUpperRight" => perspective(0.2, 6.0, 0.0, 315.0, 100.0, 23.0, -20.0, BoxAlign::BottomLeft),
            "perspectiveBelow" => perspective(0.15, 12.0, 25.0, 90.0, 90.0, -19.0, 0.0, BoxAlign::Bottom),
            "perspectiveLowerLeft" => perspective(0.2, 6.0, 1.0, 135.0, 100.0, -23.0, 13.34, BoxAlign::BottomRight),
            "perspectiveLowerRight" => perspective(0.2, 6.0, 1.0, 45.0, 100.0, -23.0, -13.34, BoxAlign::BottomLeft),
            _ => return None,
        })
    }

    /// Finite and in range.
    pub fn sane(self) -> Shadow {
        Shadow {
            opacity: finite(self.opacity, 0.0, 1.0, 1.0),
            blur: finite(self.blur, 0.0, 200.0, 0.0),
            distance: finite(self.distance, 0.0, 400.0, 0.0),
            direction: turn(self.direction),
            scale_x: finite(self.scale_x, -1000.0, 1000.0, 100.0),
            scale_y: finite(self.scale_y, -1000.0, 1000.0, 100.0),
            skew_x: finite(self.skew_x, -89.0, 89.0, 0.0),
            skew_y: finite(self.skew_y, -89.0, 89.0, 0.0),
            ..self
        }
    }

    /// How far it moves, in points (right, down).
    pub fn offset(&self) -> (f32, f32) {
        let s = self.sane();
        let a = s.direction.to_radians();
        (s.distance * a.cos(), s.distance * a.sin())
    }

    /// Where point (`x`, `y`) of the box (`bx`, `by`, `bw`, `bh`) lands in the shadow, before
    /// its offset: scaled and leaned about its align point.
    pub fn project(&self, x: f32, y: f32, bx: f32, by: f32, bw: f32, bh: f32) -> (f32, f32) {
        let s = self.sane();
        let (fx, fy) = s.align.at();
        let (ax, ay) = (bx + bw * fx, by + bh * fy);
        let (dx, dy) = ((x - ax) * s.scale_x / 100.0, (y - ay) * s.scale_y / 100.0);
        let (tx, ty) = (s.skew_x.to_radians().tan(), s.skew_y.to_radians().tan());
        (ax + dx + dy * tx, ay + dy + dx * ty)
    }
}

/// A reflection (DrawingML `a:reflection`): the picture turned over below itself `distance`
/// points away, fading from `start` to `end` opacity over `end_pos` of its height, blurred
/// `blur` points.
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Reflection {
    pub blur: f32,
    pub start: f32,
    pub end: f32,
    /// How far down it fades out, 0 to 1 of the picture's height.
    pub end_pos: f32,
    pub distance: f32,
    pub direction: f32,
    pub scale_y: f32,
    pub align: BoxAlign,
    pub turns: bool,
}

impl Default for Reflection {
    fn default() -> Reflection {
        Reflection {
            blur: 0.0,
            start: 1.0,
            end: 0.0,
            end_pos: 1.0,
            distance: 0.0,
            direction: 0.0,
            scale_y: 100.0,
            align: BoxAlign::Bottom,
            turns: true,
        }
    }
}

impl Reflection {
    /// One of Picture Effects' reflections by name, as the phone app makes it: `tight`, `half`
    /// or `full` (how far down it fades) and `Touching`, `4pt` or `8pt` (how far below it lies).
    pub fn preset(name: &str) -> Option<Reflection> {
        // (start and end opacity, how far down it fades, how far below it lies)
        let (start, end, end_pos, distance) = match name {
            "tightTouching" => (0.52, 0.003, 0.35, 0.0),
            "halfTouching" => (0.5, 0.003, 0.55, 0.0),
            "fullTouching" => (0.5, 0.003, 0.9, 0.0),
            "tight4pt" => (0.5, 0.003, 0.385, 4.0),
            "half4pt" => (0.5, 0.003, 0.555, 4.0),
            "full4pt" => (0.5, 0.003, 0.9, 4.0),
            "tight8pt" => (0.5, 0.00275, 0.4, 8.0),
            "half8pt" => (0.5, 0.003, 0.555, 8.0),
            "full8pt" => (0.5, 0.00295, 0.92, 8.0),
            _ => return None,
        };
        Some(Reflection { blur: 0.5, start, end, end_pos, distance, direction: 90.0, scale_y: -100.0, align: BoxAlign::BottomLeft, turns: false })
    }

    /// Finite and in range.
    pub fn sane(self) -> Reflection {
        Reflection {
            blur: finite(self.blur, 0.0, 200.0, 0.0),
            start: finite(self.start, 0.0, 1.0, 1.0),
            end: finite(self.end, 0.0, 1.0, 0.0),
            end_pos: finite(self.end_pos, 0.0, 1.0, 1.0),
            distance: finite(self.distance, 0.0, 400.0, 0.0),
            direction: turn(self.direction),
            scale_y: finite(self.scale_y, -1000.0, 1000.0, 100.0),
            ..self
        }
    }
}

/// The outline a picture is cut to.
#[derive(Clone, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum PictureShape {
    #[default]
    Rectangle,
    /// Corners rounded by a sixth of the shorter side.
    RoundedRectangle,
    Oval,
    /// Any other shape of the Shapes gallery (Change Shape): its DrawingML preset name.
    Preset(String),
}

impl PictureShape {
    /// The outline a DrawingML preset name (`a:prstGeom prst`) cuts a picture to.
    pub fn from_preset(name: &str) -> PictureShape {
        match ShapeFormat::clean_preset(name).as_str() {
            "" | "rect" => PictureShape::Rectangle,
            "roundRect" => PictureShape::RoundedRectangle,
            "ellipse" => PictureShape::Oval,
            n => PictureShape::Preset(n.to_string()),
        }
    }

    /// Its DrawingML preset name.
    pub fn preset(&self) -> &str {
        match self {
            PictureShape::Rectangle => "rect",
            PictureShape::RoundedRectangle => "roundRect",
            PictureShape::Oval => "ellipse",
            PictureShape::Preset(n) => n,
        }
    }
}

/// Fractions of a picture cut away from its left, top, right and bottom (0 = none).
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Crop {
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

impl Crop {
    pub fn is_none(&self) -> bool {
        *self == Crop::default()
    }

    /// The part shown as (x, y, width, height) fractions of the whole picture, kept to a sane
    /// range (at least 1% of each side shows).
    pub fn shown(&self) -> (f32, f32, f32, f32) {
        let f = |v: f32| if v.is_finite() { v.clamp(0.0, 0.99) } else { 0.0 };
        let (l, t) = (f(self.left), f(self.top));
        let w = (1.0 - l - f(self.right)).clamp(0.01, 1.0);
        let h = (1.0 - t - f(self.bottom)).clamp(0.01, 1.0);
        (l, t, w, h)
    }
}

/// How a drawing object is turned about its centre and mirrored (DrawingML `a:xfrm rot`,
/// `flipH`, `flipV`); its anchor stays the unturned box.
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Turn {
    /// Degrees clockwise, `0 <= rotation < 360`.
    pub rotation: f32,
    /// Mirrored left to right.
    pub flip_h: bool,
    /// Mirrored top to bottom.
    pub flip_v: bool,
}

impl Turn {
    pub fn is_none(&self) -> bool {
        *self == Turn::default()
    }
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum ShapeKind {
    #[default]
    Rectangle,
    RoundedRectangle,
    Ellipse,
    Triangle,
    Line,
    Arrow,
    TextBox,
    /// An icon from GridCraft's own icon set; `Shape::text` holds the icon name.
    Icon,
    /// A freehand ink stroke; `Shape::text` holds the points as `x,y;x,y;…` relative to the
    /// anchor (sheet points), `Shape::line` the ink colour and `fill` unused.
    Ink,
    /// Any other preset shape: its DrawingML name (`star5`, `flowChartDecision`…) is in
    /// `ShapeFormat::preset`.
    Preset,
}

impl Shape {
    /// Arrowheads at its start and end: an arrow without either set has one at its end.
    pub fn arrow_ends(&self) -> (bool, bool) {
        let f = &self.format;
        (f.head_end, f.tail_end || (self.kind == ShapeKind::Arrow && !f.head_end))
    }

    /// Its arrowheads set as `ShapeFormat` keeps them: an arrow with only its end head keeps
    /// neither flag (the head is the arrow's own).
    pub fn with_arrows(mut self, start: bool, end: bool) -> Shape {
        self.format.head_end = start;
        self.format.tail_end = end && !(self.kind == ShapeKind::Arrow && !start);
        self
    }
}

impl ShapeFormat {
    /// A preset name as files may hold it: letters and digits, at most 64.
    pub fn clean_preset(name: &str) -> String {
        name.chars().filter(char::is_ascii_alphanumeric).take(64).collect()
    }
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Shape {
    pub id: u32,
    pub kind: ShapeKind,
    pub anchor: Anchor,
    pub fill: Color,
    pub line: Color,
    pub text: String,
    /// Outline visibility and text formatting (DrawingML `a:ln`, `a:bodyPr`, `a:pPr`, `a:rPr`).
    #[serde(default)]
    pub format: ShapeFormat,
}

/// Horizontal alignment of a shape's text (`a:pPr algn`).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum ShapeTextAlign {
    #[default]
    Left,
    Center,
    Right,
}

/// Vertical anchor of a shape's text in its box (`a:bodyPr anchor`).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum ShapeTextAnchor {
    #[default]
    Top,
    Middle,
    Bottom,
}

/// How a shape's outline and text look. The text properties apply to the whole text (the first
/// paragraph's and first run's properties when a file has several).
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct ShapeFormat {
    /// No outline (`a:ln/a:noFill`), whatever `Shape::line` says.
    pub no_line: bool,
    /// Text size in points (`a:rPr sz` / 100); `None` is the default size.
    pub font_size: Option<f32>,
    pub bold: bool,
    pub italic: bool,
    /// Underlined once (`a:rPr u="sng"`).
    pub underline: bool,
    /// Typeface (`a:latin typeface`); `None` is the default font.
    pub font: Option<String>,
    /// Text fill colour; `None` is the default (black in text boxes).
    pub text_color: Option<Color>,
    /// Text outline (`a:rPr/a:ln`): colour and width in points.
    pub text_outline: Option<(Color, f32)>,
    /// `None`: left, DrawingML's default.
    pub align: Option<ShapeTextAlign>,
    /// `None`: top, DrawingML's default.
    pub anchor: Option<ShapeTextAnchor>,
    /// Paragraphs run right to left (`a:pPr rtl`).
    pub rtl: bool,
    /// The shape turned or mirrored (`a:xfrm rot`, `flipH`, `flipV`).
    #[serde(skip_serializing_if = "Turn::is_none")]
    pub turn: Turn,
    /// Alternative text, what a screen reader says for the shape (`xdr:cNvPr descr`).
    #[serde(skip_serializing_if = "String::is_empty")]
    pub alt: String,
    /// A `ShapeKind::Preset` shape's geometry (`a:prstGeom prst`).
    #[serde(skip_serializing_if = "String::is_empty")]
    pub preset: String,
    /// Arrowheads at a line's or connector's start and end (`a:headEnd`, `a:tailEnd`); an arrow
    /// always has one at its end.
    pub head_end: bool,
    pub tail_end: bool,
    /// How much of what is under the fill shows through it, from 0 (none) to 1 (all): the fill
    /// colour's `a:alpha` as one less it.
    #[serde(skip_serializing_if = "is_zero")]
    pub fill_transparency: f32,
    /// A gradient fill (`a:gradFill`): its stops in order, from 0 to 1 along the line it runs,
    /// `gradient_angle` degrees clockwise from left-to-right (90: top to bottom). Empty for a plain
    /// fill; `Shape::fill` then still holds a colour of it for what draws only plain fills.
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub gradient: Vec<GradientStop>,
    #[serde(skip_serializing_if = "is_zero")]
    pub gradient_angle: f32,
}

/// A colour of a gradient and where it sits along it, 0 to 1 (`a:gs pos`).
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct GradientStop {
    pub pos: f32,
    pub color: Color,
}

fn is_zero(v: &f32) -> bool {
    *v == 0.0
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum SparklineKind {
    #[default]
    Line,
    Column,
    WinLoss,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Sparkline {
    pub cell: CellRef,
    pub source: String,
    pub kind: SparklineKind,
    pub color: Color,
    pub markers: bool,
}

// ---------------------------------------------------------------- filter & sort

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub enum FilterCriterion {
    /// Show rows whose display text is one of these (case-insensitive); `blanks` includes empty.
    Values {
        values: Vec<String>,
        blanks: bool,
    },
    /// Custom: one or two comparisons (`op` like `>=`, `=*abc*`), joined by and/or.
    Custom {
        a: (String, String),
        b: Option<(String, String)>,
        and: bool,
    },
    Top10 {
        bottom: bool,
        percent: bool,
        count: u32,
    },
    AboveAverage(bool),
    FillColor(Color),
    FontColor(Color),
    /// Rows whose cell shows icon `id` (0-based; `None` = no icon) of conditional-format icon set
    /// `set` (`iconFilter`). Kept from files; re-applying the filter leaves those rows as they are.
    Icon {
        set: String,
        id: Option<u32>,
    },
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct AutoFilter {
    pub range: RangeRef,
    /// Column offset within `range` → criterion.
    pub criteria: Vec<(u32, FilterCriterion)>,
}

/// A slicer: an on-sheet filter widget bound to one column of a table. Clicking its value tiles
/// sets that column's `FilterCriterion::Values` in the sheet's [`AutoFilter`] and re-applies it,
/// so a slicer is just a visual front-end over the existing filter machinery.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct Slicer {
    pub id: u32,
    /// Name of the table this slicer filters.
    pub table: String,
    /// Header name of the filtered column within the table.
    pub column: String,
    /// Caption shown in the slicer header (defaults to the column name).
    pub caption: String,
    pub anchor: Anchor,
    /// Selected display values. `None` means every value (no filter from this slicer); an empty
    /// list means nothing is selected (all rows hidden), matching Excel's "clear all buttons".
    pub selected: Option<Vec<String>>,
    /// Number of button columns in the layout (Excel default 1).
    pub columns: u32,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum Orientation {
    #[default]
    Portrait,
    Landscape,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct PrintSettings {
    pub orientation: Orientation,
    /// Paper size name (`Letter`, `A4`, `Legal`…).
    pub paper: String,
    /// Margins in inches: left, right, top, bottom, header, footer.
    pub margins: [f32; 6],
    pub print_area: Option<RangeRef>,
    pub title_rows: Option<(u32, u32)>,
    pub title_cols: Option<(u32, u32)>,
    /// Percent scale, or fit to pages (wide, tall).
    pub scale: u16,
    pub fit_to: Option<(u16, u16)>,
    pub gridlines: bool,
    pub headings: bool,
    pub center_h: bool,
    pub center_v: bool,
    /// The header and footer of odd pages, and of every page unless the variants below apply.
    pub header: String,
    pub footer: String,
    pub row_breaks: Vec<u32>,
    pub col_breaks: Vec<u32>,
    /// Different First Page: the sheet's first printed page uses `first_header`/`first_footer`.
    #[serde(default)]
    pub different_first: bool,
    /// Different Odd & Even Pages: even-numbered pages use `even_header`/`even_footer`.
    #[serde(default)]
    pub different_odd_even: bool,
    #[serde(default)]
    pub first_header: String,
    #[serde(default)]
    pub first_footer: String,
    #[serde(default)]
    pub even_header: String,
    #[serde(default)]
    pub even_footer: String,
    /// Scale with Document / Align with Page Margins (both on unless a file turned them off).
    #[serde(default = "yes")]
    pub hf_scale_with_doc: bool,
    #[serde(default = "yes")]
    pub hf_align_with_margins: bool,
}

fn yes() -> bool {
    true
}

/// Which header/footer pair a printed page uses.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum HfPage {
    Odd,
    Even,
    First,
}

impl HfPage {
    pub fn id(self) -> &'static str {
        match self {
            HfPage::Odd => "odd",
            HfPage::Even => "even",
            HfPage::First => "first",
        }
    }
    pub fn parse(id: &str) -> Option<HfPage> {
        match id {
            "odd" | "all" => Some(HfPage::Odd),
            "even" => Some(HfPage::Even),
            "first" => Some(HfPage::First),
            _ => None,
        }
    }
}

impl PrintSettings {
    /// The pair used by page `number` (1-based, as `&P` prints it); `first` is the sheet's first page.
    pub fn hf_page(&self, first: bool, number: usize) -> HfPage {
        if self.different_first && first {
            HfPage::First
        } else if self.different_odd_even && number.is_multiple_of(2) {
            HfPage::Even
        } else {
            HfPage::Odd
        }
    }
    /// The header (`footer` false) or footer of a page kind.
    pub fn hf(&self, page: HfPage, footer: bool) -> &String {
        match (page, footer) {
            (HfPage::Odd, false) => &self.header,
            (HfPage::Odd, true) => &self.footer,
            (HfPage::Even, false) => &self.even_header,
            (HfPage::Even, true) => &self.even_footer,
            (HfPage::First, false) => &self.first_header,
            (HfPage::First, true) => &self.first_footer,
        }
    }
    pub fn hf_mut(&mut self, page: HfPage, footer: bool) -> &mut String {
        match (page, footer) {
            (HfPage::Odd, false) => &mut self.header,
            (HfPage::Odd, true) => &mut self.footer,
            (HfPage::Even, false) => &mut self.even_header,
            (HfPage::Even, true) => &mut self.even_footer,
            (HfPage::First, false) => &mut self.first_header,
            (HfPage::First, true) => &mut self.first_footer,
        }
    }
    /// Any header or footer text at all (on any kind of page).
    pub fn has_header_footer(&self) -> bool {
        [&self.header, &self.footer, &self.first_header, &self.first_footer, &self.even_header, &self.even_footer].iter().any(|t| !t.is_empty())
    }
}

impl Default for PrintSettings {
    fn default() -> Self {
        PrintSettings {
            orientation: Orientation::Portrait,
            paper: "Letter".into(),
            margins: [0.7, 0.7, 0.75, 0.75, 0.3, 0.3],
            print_area: None,
            title_rows: None,
            title_cols: None,
            scale: 100,
            fit_to: None,
            gridlines: false,
            headings: false,
            center_h: false,
            center_v: false,
            header: String::new(),
            footer: String::new(),
            row_breaks: vec![],
            col_breaks: vec![],
            different_first: false,
            different_odd_even: false,
            first_header: String::new(),
            first_footer: String::new(),
            even_header: String::new(),
            even_footer: String::new(),
            hf_scale_with_doc: true,
            hf_align_with_margins: true,
        }
    }
}

#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct SheetProtection {
    /// Hash of the password (Excel-compatible, written to XLSX `sheetProtection`; never the
    /// password itself).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub password: Option<crate::PasswordHash>,
    /// The internal hash older GridCraft JSON workbooks stored ([`password_hash`]); still
    /// verified when unprotecting, never written for new passwords.
    #[serde(rename = "password_hash", default, skip_serializing_if = "Option::is_none")]
    pub legacy_password_hash: Option<u64>,
    pub select_locked: bool,
    pub select_unlocked: bool,
    pub format_cells: bool,
    pub format_columns: bool,
    pub format_rows: bool,
    pub insert_columns: bool,
    pub insert_rows: bool,
    pub delete_columns: bool,
    pub delete_rows: bool,
    pub sort: bool,
    pub autofilter: bool,
}

/// Review › Allow Edit Ranges: cells that stay editable on a protected sheet, with or without a
/// password (XLSX `<protectedRange>`).
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct EditRange {
    pub title: String,
    pub ranges: Vec<RangeRef>,
    pub password: Option<crate::PasswordHash>,
    /// Windows permissions (`securityDescriptor`), kept as read from the file.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub security_descriptor: Option<String>,
}

impl EditRange {
    pub fn contains(&self, c: CellRef) -> bool {
        self.ranges.iter().any(|r| r.contains(c))
    }
}

/// The non-cryptographic password hash older GridCraft versions stored for sheet protection
/// (only verified now, see [`SheetProtection::legacy_password_hash`]).
pub fn password_hash(pw: &str) -> u64 {
    let mut h: u64 = 0xcbf29ce484222325 ^ 0x5348_4545_5443;
    for b in pw.bytes() {
        h ^= b as u64;
        h = h.wrapping_mul(0x100000001b3);
    }
    h
}

/// Outline grouping info for a row or column.
#[derive(Clone, Copy, Debug, Default, PartialEq, Serialize, Deserialize)]
pub struct LineInfo {
    /// Size in points; `None` = default (rows auto-fit to content).
    pub size: Option<f32>,
    pub hidden: bool,
    pub outline: u8,
    pub collapsed: bool,
    pub style: Option<crate::style::StyleId>,
    /// The size was set by the user (rows): automatic heights follow the content.
    #[serde(default)]
    pub custom: bool,
}

// ---------------------------------------------------------------- pivot tables

/// How a PivotTable lays out its row fields.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum PivotLayout {
    /// All row fields in one column, inner items indented ("Row Labels" header).
    #[default]
    Compact,
    /// One column per row field; each item on its own row.
    Outline,
    /// One column per row field; inner items start on their parent's row, totals below.
    Tabular,
}

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum PivotSort {
    #[default]
    Asc,
    Desc,
    /// Source order (first appearance).
    None,
}

/// Grouping of a date field (values are date serials).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum PivotDateGroup {
    #[default]
    None,
    Years,
    Quarters,
    Months,
    Days,
}

/// A field placed in the Rows or Columns area.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct PivotField {
    /// Source column header name.
    pub source_col: String,
    pub sort: PivotSort,
    pub date_group: PivotDateGroup,
    /// Item labels whose detail is hidden (collapsed).
    pub collapsed_items: Vec<String>,
}

/// Summarize-values-by function.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum PivotFunc {
    #[default]
    Sum,
    Count,
    Average,
    Max,
    Min,
    Product,
    CountNumbers,
    StdDev,
    StdDevP,
    Var,
    VarP,
}

/// Show-values-as transform.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
pub enum PivotShowAs {
    #[default]
    Normal,
    PercentOfGrandTotal,
    PercentOfColumnTotal,
    PercentOfRowTotal,
    /// Running total in the first row field.
    RunningTotal,
    /// Rank largest to smallest among sibling row items.
    Rank,
}

/// A field in the Values area.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct PivotValue {
    pub source_col: String,
    pub func: PivotFunc,
    /// Caption, e.g. "Sum of Sales".
    pub name: String,
    pub show_as: PivotShowAs,
    pub number_format: Option<String>,
}

/// A report filter (or, for a field also in Rows/Columns, its hidden-items filter).
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct PivotFilter {
    pub source_col: String,
    /// Item labels shown; `None` = all.
    pub selected: Option<Vec<String>>,
}

/// A PivotTable: its definition plus where its output was last written. The output itself is
/// static cell values and styles on the sheet, rewritten on every refresh.
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct PivotTable {
    pub id: u32,
    pub name: String,
    /// Source: range formula text (`Sheet1!$A$1:$E$200`), a table name or a defined name.
    pub source: String,
    /// Top-left of the output body on the pivot's sheet.
    pub anchor: CellRef,
    pub rows: Vec<PivotField>,
    pub columns: Vec<PivotField>,
    pub values: Vec<PivotValue>,
    pub filters: Vec<PivotFilter>,
    pub layout: PivotLayout,
    /// Grand total for each row (the "Grand Total" column), like Excel's "On for Rows".
    pub grand_totals_rows: bool,
    /// Grand total for each column (the "Grand Total" row), like Excel's "On for Columns".
    pub grand_totals_cols: bool,
    pub subtotals: bool,
    /// Subtotals at the top of each group (Compact/Outline); otherwise below it.
    pub subtotals_top: bool,
    pub style: String,
    pub show_headers: bool,
    /// Area written by the last refresh.
    pub last_range: Option<RangeRef>,
    /// Calculated fields, usable in the Values area by name.
    pub calculated_fields: Vec<PivotCalcField>,
    /// The Σ Values pseudo-field (it exists with two or more value fields) sits in Rows rather
    /// than Columns (Excel's "values on rows").
    pub values_on_rows: bool,
    /// Position of Σ Values among the row fields (`None` = after the last). Only used with
    /// `values_on_rows`: in Columns Σ Values always comes after the last column field.
    pub values_position: Option<usize>,
}

/// A calculated field: a formula over other fields (`=Sales*0.1`, `='Unit Price'*Units`). As in
/// Excel, it is evaluated on the sums of the fields it names for each report cell, not per record.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct PivotCalcField {
    pub name: String,
    /// Formula text, starting with `=`.
    pub formula: String,
}

impl Default for PivotTable {
    fn default() -> Self {
        PivotTable {
            id: 0,
            name: "PivotTable1".into(),
            source: String::new(),
            anchor: CellRef::default(),
            rows: vec![],
            columns: vec![],
            values: vec![],
            filters: vec![],
            layout: PivotLayout::Compact,
            grand_totals_rows: true,
            grand_totals_cols: true,
            subtotals: true,
            subtotals_top: true,
            style: "PivotStyleLight16".into(),
            show_headers: true,
            last_range: None,
            calculated_fields: vec![],
            values_on_rows: false,
            values_position: None,
        }
    }
}
