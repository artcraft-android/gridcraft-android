//! Preset shapes: the outline of each shape in the Shapes gallery, by its DrawingML preset name
//! (`a:prstGeom prst`), drawn at a size as paths. These are GridCraft's own drawings of the
//! standard shapes at their default proportions; adjustment handles aren't modelled.

/// A step of a path, in the shape's own points (its top-left corner at the origin).
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Seg {
    Move([f32; 2]),
    Line([f32; 2]),
    Cubic([f32; 2], [f32; 2], [f32; 2]),
    Close,
}

/// How a piece of a shape is painted.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Paint {
    /// The shape's fill.
    Fill,
    /// The fill darkened or lightened: the shaded faces of a cube, a cylinder's top.
    Darken,
    DarkenLess,
    Lighten,
    LightenLess,
    /// Not filled: an outline only (brackets, a connector's line).
    None,
    /// Filled in the outline's colour (arrowheads).
    Ink,
}

/// One painted piece of a shape: its subpaths, filled even-odd (so an inner subpath is a hole).
#[derive(Clone, Debug, PartialEq)]
pub struct Piece {
    pub segs: Vec<Seg>,
    pub paint: Paint,
    /// Drawn with the shape's outline.
    pub stroke: bool,
}

/// `rgb` shaded for `paint` (darkened or lightened; unchanged otherwise).
pub fn shade(rgb: [u8; 3], paint: Paint) -> [u8; 3] {
    let f = |v: u8| -> u8 {
        let v = f32::from(v);
        let out = match paint {
            Paint::Darken => v * 0.6,
            Paint::DarkenLess => v * 0.8,
            Paint::Lighten => v + (255.0 - v) * 0.4,
            Paint::LightenLess => v + (255.0 - v) * 0.2,
            _ => v,
        };
        out.round().clamp(0.0, 255.0) as u8
    };
    [f(rgb[0]), f(rgb[1]), f(rgb[2])]
}

/// The Shapes gallery: its bands, each with its shapes as (name shown, preset name). "textBox"
/// is a text box; "line" a straight line; "arrow" and "doubleArrow" a line with an arrowhead at
/// its end or at both; the connectors' `…Arrow` and `…DoubleArrow` names likewise.
pub const GALLERY: &[(&str, &[(&str, &str)])] = &[
    (
        "Lines",
        &[
            ("Straight Connector", "line"),
            ("Straight Arrow Connector", "arrow"),
            ("Straight Double-Arrow Connector", "doubleArrow"),
            ("Connector: Elbow", "bentConnector3"),
            ("Connector: Elbow Arrow", "bentConnector3Arrow"),
            ("Connector: Elbow Double-Arrow", "bentConnector3DoubleArrow"),
            ("Connector: Curved", "curvedConnector3"),
            ("Connector: Curved Arrow", "curvedConnector3Arrow"),
            ("Connector: Curved Double-Arrow", "curvedConnector3DoubleArrow"),
        ],
    ),
    (
        "Rectangles",
        &[
            ("Rectangle", "rect"),
            ("Rectangle: Rounded Corners", "roundRect"),
            ("Rectangle: Single Corner Snipped", "snip1Rect"),
            ("Rectangle: Top Corners Snipped", "snip2SameRect"),
            ("Rectangle: Diagonal Corners Snipped", "snip2DiagRect"),
            ("Rectangle: Top Corners One Rounded and One Snipped", "snipRoundRect"),
            ("Rectangle: Single Corner Rounded", "round1Rect"),
            ("Rectangle: Top Corners Rounded", "round2SameRect"),
            ("Rectangle: Diagonal Corners Rounded", "round2DiagRect"),
        ],
    ),
    (
        "Basic Shapes",
        &[
            ("Insert a Text Box", "textBox"),
            ("Oval", "ellipse"),
            ("Isosceles Triangle", "triangle"),
            ("Right Triangle", "rtTriangle"),
            ("Parallelogram", "parallelogram"),
            ("Trapezoid", "trapezoid"),
            ("Diamond", "diamond"),
            ("Pentagon", "pentagon"),
            ("Hexagon", "hexagon"),
            ("Heptagon", "heptagon"),
            ("Octagon", "octagon"),
            ("Decagon", "decagon"),
            ("Dodecagon", "dodecagon"),
            ("Partial Circle", "pie"),
            ("Chord", "chord"),
            ("Teardrop", "teardrop"),
            ("Frame", "frame"),
            ("Half Frame", "halfFrame"),
            ("L-Shape", "corner"),
            ("Diagonal Stripe", "diagStripe"),
            ("Cross", "plus"),
            ("Plaque", "plaque"),
            ("Cylinder", "can"),
            ("Cube", "cube"),
            ("Rectangle: Beveled", "bevel"),
            ("Circle: Hollow", "donut"),
            ("\"No\" Symbol", "noSmoking"),
            ("Block Arc", "blockArc"),
            ("Rectangle: Folded Corner", "foldedCorner"),
            ("Smiley Face", "smileyFace"),
            ("Heart", "heart"),
            ("Lightning Bolt", "lightningBolt"),
            ("Sun", "sun"),
            ("Moon", "moon"),
            ("Cloud", "cloud"),
            ("Arc", "arc"),
            ("Double Bracket", "bracketPair"),
            ("Double Brace", "bracePair"),
            ("Left Bracket", "leftBracket"),
            ("Right Bracket", "rightBracket"),
            ("Left Brace", "leftBrace"),
            ("Right Brace", "rightBrace"),
        ],
    ),
    (
        "Block Arrows",
        &[
            ("Arrow: Right", "rightArrow"),
            ("Arrow: Left", "leftArrow"),
            ("Arrow: Up", "upArrow"),
            ("Arrow: Down", "downArrow"),
            ("Arrow: Left-Right", "leftRightArrow"),
            ("Arrow: Up-Down", "upDownArrow"),
            ("Arrow: Quad", "quadArrow"),
            ("Arrow: Left-Right-Up", "leftRightUpArrow"),
            ("Arrow: Bent", "bentArrow"),
            ("Arrow: U-Turn", "uturnArrow"),
            ("Arrow: Left-Up", "leftUpArrow"),
            ("Arrow: Bent-Up", "bentUpArrow"),
            ("Arrow: Curved Right", "curvedRightArrow"),
            ("Arrow: Curved Left", "curvedLeftArrow"),
            ("Arrow: Curved Up", "curvedUpArrow"),
            ("Arrow: Curved Down", "curvedDownArrow"),
            ("Arrow: Striped Right", "stripedRightArrow"),
            ("Arrow: Notched Right", "notchedRightArrow"),
            ("Arrow: Pentagon", "homePlate"),
            ("Arrow: Chevron", "chevron"),
            ("Callout: Right Arrow", "rightArrowCallout"),
            ("Callout: Down Arrow", "downArrowCallout"),
            ("Callout: Left Arrow", "leftArrowCallout"),
            ("Callout: Up Arrow", "upArrowCallout"),
            ("Callout: Left-Right Arrow", "leftRightArrowCallout"),
            ("Callout: Quad Arrow", "quadArrowCallout"),
            ("Arrow: Circular", "circularArrow"),
        ],
    ),
    (
        "Equation Shapes",
        &[
            ("Plus Sign", "mathPlus"),
            ("Minus Sign", "mathMinus"),
            ("Multiplication Sign", "mathMultiply"),
            ("Division Sign", "mathDivide"),
            ("Equals", "mathEqual"),
            ("Not Equal", "mathNotEqual"),
        ],
    ),
    (
        "Flowchart",
        &[
            ("Flowchart: Process", "flowChartProcess"),
            ("Flowchart: Alternate Process", "flowChartAlternateProcess"),
            ("Flowchart: Decision", "flowChartDecision"),
            ("Flowchart: Data", "flowChartInputOutput"),
            ("Flowchart: Predefined Process", "flowChartPredefinedProcess"),
            ("Flowchart: Internal Storage", "flowChartInternalStorage"),
            ("Flowchart: Document", "flowChartDocument"),
            ("Flowchart: Multidocument", "flowChartMultidocument"),
            ("Flowchart: Terminator", "flowChartTerminator"),
            ("Flowchart: Preparation", "flowChartPreparation"),
            ("Flowchart: Manual Input", "flowChartManualInput"),
            ("Flowchart: Manual Operation", "flowChartManualOperation"),
            ("Flowchart: Connector", "flowChartConnector"),
            ("Flowchart: Off-page Connector", "flowChartOffpageConnector"),
            ("Flowchart: Card", "flowChartPunchedCard"),
            ("Flowchart: Punched Tape", "flowChartPunchedTape"),
            ("Flowchart: Summing Junction", "flowChartSummingJunction"),
            ("Flowchart: Or", "flowChartOr"),
            ("Flowchart: Collate", "flowChartCollate"),
            ("Flowchart: Sort", "flowChartSort"),
            ("Flowchart: Extract", "flowChartExtract"),
            ("Flowchart: Merge", "flowChartMerge"),
            ("Flowchart: Stored Data", "flowChartOnlineStorage"),
            ("Flowchart: Delay", "flowChartDelay"),
            ("Flowchart: Sequential Access Storage", "flowChartMagneticTape"),
            ("Flowchart: Magnetic Disk", "flowChartMagneticDisk"),
            ("Flowchart: Direct Access Storage", "flowChartMagneticDrum"),
            ("Flowchart: Display", "flowChartDisplay"),
        ],
    ),
    (
        "Stars and Banners",
        &[
            ("Explosion: 8 Points", "irregularSeal1"),
            ("Explosion: 14 Points", "irregularSeal2"),
            ("Star: 4 Points", "star4"),
            ("Star: 5 Points", "star5"),
            ("Star: 6 Points", "star6"),
            ("Star: 7 Points", "star7"),
            ("Star: 8 Points", "star8"),
            ("Star: 10 Points", "star10"),
            ("Star: 12 Points", "star12"),
            ("Star: 16 Points", "star16"),
            ("Star: 24 Points", "star24"),
            ("Star: 32 Points", "star32"),
            ("Ribbon: Tilted Up", "ribbon2"),
            ("Ribbon: Tilted Down", "ribbon"),
            ("Ribbon: Curved and Tilted Up", "ellipseRibbon2"),
            ("Ribbon: Curved and Tilted Down", "ellipseRibbon"),
            ("Scroll: Vertical", "verticalScroll"),
            ("Scroll: Horizontal", "horizontalScroll"),
            ("Wave", "wave"),
            ("Double Wave", "doubleWave"),
        ],
    ),
    (
        "Callouts",
        &[
            ("Speech Bubble: Rectangle", "wedgeRectCallout"),
            ("Speech Bubble: Rectangle with Corners Rounded", "wedgeRoundRectCallout"),
            ("Speech Bubble: Oval", "wedgeEllipseCallout"),
            ("Thought Bubble: Cloud", "cloudCallout"),
            ("Callout: Line", "borderCallout1"),
            ("Callout: Line with Accent Bar", "accentCallout1"),
            ("Callout: Line with No Border", "callout1"),
            ("Callout: Line with Border and Accent Bar", "accentBorderCallout1"),
            ("Callout: Bent Line", "borderCallout2"),
            ("Callout: Bent Line with Accent Bar", "accentCallout2"),
            ("Callout: Bent Line with No Border", "callout2"),
            ("Callout: Bent Line with Border and Accent Bar", "accentBorderCallout2"),
            ("Callout: Double Bent Line", "borderCallout3"),
            ("Callout: Double Bent Line with Accent Bar", "accentCallout3"),
            ("Callout: Double Bent Line with No Border", "callout3"),
            ("Callout: Double Bent Line with Border and Accent Bar", "accentBorderCallout3"),
        ],
    ),
];

/// A gallery name's preset geometry and its arrowheads (start, end): `bentConnector3Arrow` is
/// `bentConnector3` with an arrowhead at its end.
pub fn split_arrows(name: &str) -> (&str, bool, bool) {
    match name {
        "arrow" => ("line", false, true),
        "doubleArrow" => ("line", true, true),
        _ => {
            if let Some(g) = name.strip_suffix("DoubleArrow") {
                (g, true, true)
            } else if let Some(g) = name.strip_suffix("Arrow").filter(|g| g.ends_with("Connector3")) {
                (g, false, true)
            } else {
                (name, false, false)
            }
        }
    }
}

/// The size in points a shape is inserted at, as the phone app inserts it (and the proportions its
/// gallery tile draws it at): two inches square unless it has its own size here.
pub fn default_size(name: &str) -> (f32, f32) {
    match split_arrows(name).0 {
        "flowChartTerminator" => (144.0, 47.52),
        "flowChartConnector" => (72.0, 72.0),
        "flowChartManualInput" => (144.0, 72.0),
        "chevron" => (76.32, 76.32),
        "homePlate" | "leftArrow" | "notchedRightArrow" | "rightArrow" | "stripedRightArrow" => (154.08, 76.32),
        "leftRightArrow" => (191.52, 76.32),
        "leftRightArrowCallout" => (191.52, 90.72),
        "flowChartDelay"
        | "flowChartInternalStorage"
        | "flowChartMagneticTape"
        | "flowChartOffpageConnector"
        | "flowChartOr"
        | "flowChartSummingJunction" => (96.48, 96.48),
        "accentBorderCallout1"
        | "accentBorderCallout2"
        | "accentBorderCallout3"
        | "accentCallout1"
        | "accentCallout2"
        | "accentCallout3"
        | "borderCallout1"
        | "borderCallout2"
        | "borderCallout3"
        | "callout1"
        | "callout2"
        | "callout3"
        | "cloudCallout"
        | "flowChartAlternateProcess"
        | "flowChartDecision"
        | "flowChartDisplay"
        | "flowChartDocument"
        | "flowChartInputOutput"
        | "flowChartMagneticDisk"
        | "flowChartManualOperation"
        | "flowChartOnlineStorage"
        | "flowChartPredefinedProcess"
        | "flowChartProcess"
        | "wedgeEllipseCallout"
        | "wedgeRectCallout"
        | "wedgeRoundRectCallout" => (144.0, 96.48),
        "flowChartPreparation" => (167.04, 96.48),
        "ribbon" | "ribbon2" => (191.52, 96.48),
        "flowChartExtract" | "flowChartMerge" => (108.0, 108.0),
        "flowChartMagneticDrum" => (144.0, 108.0),
        "bentUpArrow" => (133.92, 115.2),
        "curvedDownArrow" | "curvedUpArrow" => (191.52, 115.2),
        "flowChartMultidocument" => (167.04, 119.52),
        "ellipseRibbon" | "ellipseRibbon2" => (191.52, 119.52),
        "flowChartPunchedCard" | "flowChartPunchedTape" => (144.0, 126.72),
        "leftUpArrow" => (133.92, 133.92),
        "leftRightUpArrow" => (191.52, 133.92),
        "bentArrow" => (128.16, 136.8),
        "star5" => (144.0, 136.95),
        "uturnArrow" => (139.68, 138.24),
        "star7" => (144.0, 140.39),
        "leftBracket" | "rightBracket" => (11.52, 144.0),
        "leftBrace" | "rightBrace" => (24.48, 144.0),
        "flowChartCollate" | "flowChartSort" | "moon" => (72.0, 144.0),
        "pentagon" => (151.2, 144.0),
        "hexagon" | "triangle" => (167.04, 144.0),
        "bracePair" => (168.48, 144.0),
        "parallelogram" => (191.52, 144.0),
        "star10" => (144.0, 151.41),
        "downArrow" | "upArrow" => (76.32, 154.08),
        "circularArrow" => (154.08, 154.08),
        "horizontalScroll" => (180.0, 162.72),
        "bevel" => (164.16, 164.16),
        "star6" => (144.0, 166.28),
        "verticalScroll" => (162.72, 180.0),
        "upDownArrow" => (76.32, 191.52),
        "curvedLeftArrow" | "curvedRightArrow" => (115.2, 191.52),
        "can" | "trapezoid" => (144.0, 191.52),
        "cube" | "quadArrow" | "quadArrowCallout" => (191.52, 191.52),
        _ => (144.0, 144.0),
    }
}

/// Is `name` a preset GridCraft draws?
pub fn is_preset(name: &str) -> bool {
    preset(name, 10.0, 10.0).is_some()
}

/// Path builder.
struct B {
    pieces: Vec<Piece>,
    segs: Vec<Seg>,
    open: bool,
}

impl B {
    fn new() -> B {
        B { pieces: vec![], segs: vec![], open: false }
    }
    fn m(&mut self, x: f32, y: f32) {
        self.segs.push(Seg::Move([x, y]));
        self.open = true;
    }
    fn l(&mut self, x: f32, y: f32) {
        if self.open {
            self.segs.push(Seg::Line([x, y]));
        } else {
            self.m(x, y);
        }
    }
    fn c(&mut self, x1: f32, y1: f32, x2: f32, y2: f32, x: f32, y: f32) {
        if !self.open {
            self.m(x1, y1);
        }
        self.segs.push(Seg::Cubic([x1, y1], [x2, y2], [x, y]));
    }
    fn z(&mut self) {
        if self.open {
            self.segs.push(Seg::Close);
        }
        self.open = false;
    }
    fn poly(&mut self, pts: &[[f32; 2]]) {
        for (i, p) in pts.iter().enumerate() {
            if i == 0 {
                self.m(p[0], p[1]);
            } else {
                self.l(p[0], p[1]);
            }
        }
        self.z();
    }
    /// An arc of the ellipse round (`cx`, `cy`) from angle `a0` to `a1` (degrees clockwise from 3
    /// o'clock; `a1` below `a0` runs anticlockwise), joined to the path by a line.
    fn arc(&mut self, cx: f32, cy: f32, rx: f32, ry: f32, a0: f32, a1: f32) {
        let at = |a: f32| {
            let r = a.to_radians();
            [cx + rx * r.cos(), cy + ry * r.sin()]
        };
        let s = at(a0);
        self.l(s[0], s[1]);
        let n = ((a1 - a0).abs() / 90.0).ceil().max(1.0) as usize;
        let step = (a1 - a0) / n as f32;
        for i in 0..n {
            let (s, e) = (a0 + step * i as f32, a0 + step * (i + 1) as f32);
            let k = 4.0 / 3.0 * ((e - s).to_radians() / 4.0).tan();
            let (sr, er) = (s.to_radians(), e.to_radians());
            let c1 = [cx + rx * (sr.cos() - k * sr.sin()), cy + ry * (sr.sin() + k * sr.cos())];
            let c2 = [cx + rx * (er.cos() + k * er.sin()), cy + ry * (er.sin() - k * er.cos())];
            self.segs.push(Seg::Cubic(c1, c2, at(e)));
        }
    }
    fn ellipse(&mut self, cx: f32, cy: f32, rx: f32, ry: f32) {
        self.z();
        self.arc(cx, cy, rx, ry, 0.0, 360.0);
        self.z();
    }
    /// A rectangle whose corners (top-left, top-right, bottom-right, bottom-left) are rounded by
    /// `r`, or cut off where `snip` says so.
    fn rect(&mut self, x: f32, y: f32, w: f32, h: f32, r: [f32; 4], snip: [bool; 4]) {
        let lim = (w.min(h) / 2.0).max(0.0);
        let r = r.map(|v| v.clamp(0.0, lim));
        let (x1, y1) = (x + w, y + h);
        self.z();
        self.m(x + r[0], y);
        self.l(x1 - r[1], y);
        if r[1] > 0.0 {
            if snip[1] { self.l(x1, y + r[1]) } else { self.arc(x1 - r[1], y + r[1], r[1], r[1], 270.0, 360.0) }
        }
        self.l(x1, y1 - r[2]);
        if r[2] > 0.0 {
            if snip[2] { self.l(x1 - r[2], y1) } else { self.arc(x1 - r[2], y1 - r[2], r[2], r[2], 0.0, 90.0) }
        }
        self.l(x + r[3], y1);
        if r[3] > 0.0 {
            if snip[3] { self.l(x, y1 - r[3]) } else { self.arc(x + r[3], y1 - r[3], r[3], r[3], 90.0, 180.0) }
        }
        self.l(x, y + r[0]);
        if r[0] > 0.0 {
            if snip[0] { self.l(x + r[0], y) } else { self.arc(x + r[0], y + r[0], r[0], r[0], 180.0, 270.0) }
        }
        self.z();
    }
    fn piece(&mut self, paint: Paint, stroke: bool) {
        self.z();
        if !self.segs.is_empty() {
            self.pieces.push(Piece { segs: std::mem::take(&mut self.segs), paint, stroke });
        }
    }
    fn fill(&mut self) {
        self.piece(Paint::Fill, true);
    }
    /// An open path (a line or an arc) drawn as an outline only.
    fn line_piece(&mut self) {
        self.open = false;
        if !self.segs.is_empty() {
            self.pieces.push(Piece { segs: std::mem::take(&mut self.segs), paint: Paint::None, stroke: true });
        }
    }
    fn done(mut self) -> Vec<Piece> {
        self.piece(Paint::Fill, true);
        self.pieces
    }
}

/// Every point of `pieces` moved by `f` (an affine map, so curves stay true).
fn map(pieces: Vec<Piece>, f: impl Fn([f32; 2]) -> [f32; 2]) -> Vec<Piece> {
    pieces
        .into_iter()
        .map(|p| Piece {
            segs: p
                .segs
                .into_iter()
                .map(|s| match s {
                    Seg::Move(a) => Seg::Move(f(a)),
                    Seg::Line(a) => Seg::Line(f(a)),
                    Seg::Cubic(a, b, c) => Seg::Cubic(f(a), f(b), f(c)),
                    Seg::Close => Seg::Close,
                })
                .collect(),
            ..p
        })
        .collect()
}

/// The corners of a regular `n`-gon starting at angle `start` (degrees), stretched to fill `w`×`h`.
fn regular(n: usize, start: f32, w: f32, h: f32) -> Vec<[f32; 2]> {
    let pts: Vec<[f32; 2]> = (0..n)
        .map(|k| {
            let a = (start + 360.0 * k as f32 / n as f32).to_radians();
            [a.cos(), a.sin()]
        })
        .collect();
    fit(&pts, w, h)
}

/// Points stretched so their bounds fill `w`×`h`.
fn fit(pts: &[[f32; 2]], w: f32, h: f32) -> Vec<[f32; 2]> {
    let (mut x0, mut y0, mut x1, mut y1) = (f32::MAX, f32::MAX, f32::MIN, f32::MIN);
    for p in pts {
        x0 = x0.min(p[0]);
        y0 = y0.min(p[1]);
        x1 = x1.max(p[0]);
        y1 = y1.max(p[1]);
    }
    let (sx, sy) = ((x1 - x0).max(1e-6), (y1 - y0).max(1e-6));
    pts.iter().map(|p| [(p[0] - x0) / sx * w, (p[1] - y0) / sy * h]).collect()
}

/// A star of `n` points: the inner corners at `inner` of the outer radius, the first point up.
fn star(n: usize, inner: f32, w: f32, h: f32) -> Vec<[f32; 2]> {
    let (cx, cy) = (w / 2.0, h / 2.0);
    (0..n * 2)
        .map(|k| {
            let a = (-90.0 + 180.0 * k as f32 / n as f32).to_radians();
            let r = if k % 2 == 0 { 1.0 } else { inner };
            [cx + cx * r * a.cos(), cy + cy * r * a.sin()]
        })
        .collect()
}

/// An explosion: spikes at the given angles and radii (fractions of the half size), between
/// inner corners at the given radii, stretched to fill the box.
fn burst(spikes: &[(f32, f32, f32)], w: f32, h: f32) -> Vec<[f32; 2]> {
    let mut pts = Vec::with_capacity(spikes.len() * 2);
    for (k, (a, r, inner)) in spikes.iter().enumerate() {
        // the inner corner sits halfway to the next spike (the first one again, a turn on)
        let next = match spikes.get(k + 1) {
            Some(s) => s.0,
            None => spikes.first().map_or(a + 360.0, |s| s.0 + 360.0),
        };
        let p = a.to_radians();
        pts.push([r * p.cos(), r * p.sin()]);
        let q = ((a + next) / 2.0).to_radians();
        pts.push([inner * q.cos(), inner * q.sin()]);
    }
    fit(&pts, w, h)
}

/// The preset `name` drawn `w`×`h`, as pieces painted in turn; `None` for a name GridCraft doesn't draw.
pub fn preset(name: &str, w: f32, h: f32) -> Option<Vec<Piece>> {
    shape(name, w, h, false, false)
}

/// The preset `name` drawn `w`×`h` with arrowheads at its start and/or end (besides any its
/// gallery name gives it).
pub fn shape(name: &str, w: f32, h: f32, start: bool, end: bool) -> Option<Vec<Piece>> {
    let ok = |v: f32| if v.is_finite() && v > 0.0 { v.min(1e6) } else { 1.0 };
    let (w, h) = (ok(w), ok(h));
    let (geom, s, e) = split_arrows(name);
    let (start, end) = (start || s, end || e);
    let mut out = geometry(geom, w, h)?;
    if start || end {
        // a head of a few points, never longer than the line
        let heads = arrowheads(&out, start, end, (w.min(h) * 0.2).clamp(4.0, 9.0).min((w * w + h * h).sqrt() * 0.4));
        out.extend(heads);
    }
    Some(out)
}

fn geometry(name: &str, w: f32, h: f32) -> Option<Vec<Piece>> {
    let ss = w.min(h);
    let (cx, cy) = (w / 2.0, h / 2.0);
    let mut b = B::new();
    match name {
        // ---- lines
        "line" | "straightConnector1" => {
            b.m(0.0, 0.0);
            b.l(w, h);
            b.line_piece();
        }
        "bentConnector2" => {
            b.m(0.0, 0.0);
            b.l(w, 0.0);
            b.l(w, h);
            b.line_piece();
        }
        "bentConnector3" => {
            b.m(0.0, 0.0);
            b.l(cx, 0.0);
            b.l(cx, h);
            b.l(w, h);
            b.line_piece();
        }
        "curvedConnector3" => {
            b.m(0.0, 0.0);
            b.c(cx * 0.5, 0.0, cx, h * 0.25, cx, cy);
            b.c(cx, h * 0.75, w * 0.75, h, w, h);
            b.line_piece();
        }
        // ---- rectangles
        "rect" | "textBox" | "flowChartProcess" => b.rect(0.0, 0.0, w, h, [0.0; 4], [false; 4]),
        "roundRect" | "flowChartAlternateProcess" => b.rect(0.0, 0.0, w, h, [ss / 6.0; 4], [false; 4]),
        "snip1Rect" => b.rect(0.0, 0.0, w, h, [0.0, ss / 6.0, 0.0, 0.0], [true; 4]),
        "snip2SameRect" => b.rect(0.0, 0.0, w, h, [ss / 6.0, ss / 6.0, 0.0, 0.0], [true; 4]),
        "snip2DiagRect" => b.rect(0.0, 0.0, w, h, [0.0, ss / 6.0, 0.0, ss / 6.0], [true; 4]),
        "snipRoundRect" => b.rect(0.0, 0.0, w, h, [ss / 6.0, ss / 6.0, 0.0, 0.0], [false, true, false, false]),
        "round1Rect" => b.rect(0.0, 0.0, w, h, [0.0, ss / 6.0, 0.0, 0.0], [false; 4]),
        "round2SameRect" => b.rect(0.0, 0.0, w, h, [ss / 6.0, ss / 6.0, 0.0, 0.0], [false; 4]),
        "round2DiagRect" => b.rect(0.0, 0.0, w, h, [ss / 6.0, 0.0, ss / 6.0, 0.0], [false; 4]),
        // ---- basic shapes
        "ellipse" | "flowChartConnector" => b.ellipse(cx, cy, cx, cy),
        "triangle" | "flowChartExtract" => b.poly(&[[cx, 0.0], [w, h], [0.0, h]]),
        "rtTriangle" => b.poly(&[[0.0, 0.0], [w, h], [0.0, h]]),
        "parallelogram" => b.poly(&[[ss * 0.25, 0.0], [w, 0.0], [w - ss * 0.25, h], [0.0, h]]),
        "flowChartInputOutput" => b.poly(&[[w * 0.2, 0.0], [w, 0.0], [w * 0.8, h], [0.0, h]]),
        "trapezoid" => b.poly(&[[ss * 0.25, 0.0], [w - ss * 0.25, 0.0], [w, h], [0.0, h]]),
        "diamond" | "flowChartDecision" => b.poly(&[[cx, 0.0], [w, cy], [cx, h], [0.0, cy]]),
        "pentagon" => b.poly(&regular(5, -90.0, w, h)),
        "hexagon" => b.poly(&[[ss * 0.25, 0.0], [w - ss * 0.25, 0.0], [w, cy], [w - ss * 0.25, h], [ss * 0.25, h], [0.0, cy]]),
        "heptagon" => b.poly(&regular(7, -90.0, w, h)),
        "octagon" => {
            let d = ss * 0.2929;
            b.poly(&[[d, 0.0], [w - d, 0.0], [w, d], [w, h - d], [w - d, h], [d, h], [0.0, h - d], [0.0, d]]);
        }
        "decagon" => b.poly(&regular(10, 0.0, w, h)),
        "dodecagon" => b.poly(&regular(12, 15.0, w, h)),
        "pie" => {
            b.m(cx, cy);
            b.arc(cx, cy, cx, cy, 0.0, 270.0);
            b.z();
        }
        "chord" => {
            b.arc(cx, cy, cx, cy, 45.0, 270.0);
            b.z();
        }
        "teardrop" => {
            b.arc(cx, cy, cx, cy, 90.0, 270.0);
            b.c(cx + w * 0.25, 0.0, w, 0.0, w, 0.0);
            b.c(w, 0.0, w, cy - h * 0.25, w, cy);
            b.arc(cx, cy, cx, cy, 0.0, 90.0);
            b.z();
        }
        "frame" => {
            let t = ss * 0.125;
            b.poly(&[[0.0, 0.0], [w, 0.0], [w, h], [0.0, h]]);
            b.poly(&[[t, t], [t, h - t], [w - t, h - t], [w - t, t]]);
        }
        "halfFrame" => {
            let t = ss / 3.0;
            b.poly(&[[0.0, 0.0], [w, 0.0], [w - t, t], [t, t], [t, h - t], [0.0, h]]);
        }
        "corner" => {
            let t = ss * 0.5;
            b.poly(&[[0.0, 0.0], [t, 0.0], [t, h - t], [w, h - t], [w, h], [0.0, h]]);
        }
        "diagStripe" => b.poly(&[[0.0, cy], [cx, 0.0], [w, 0.0], [0.0, h]]),
        "plus" => {
            let a = ss * 0.25;
            b.poly(&[
                [a, 0.0],
                [w - a, 0.0],
                [w - a, a],
                [w, a],
                [w, h - a],
                [w - a, h - a],
                [w - a, h],
                [a, h],
                [a, h - a],
                [0.0, h - a],
                [0.0, a],
                [a, a],
            ]);
        }
        "plaque" => {
            let r = ss / 6.0;
            b.m(r, 0.0);
            b.l(w - r, 0.0);
            b.arc(w, 0.0, r, r, 180.0, 90.0);
            b.l(w, h - r);
            b.arc(w, h, r, r, 270.0, 180.0);
            b.l(r, h);
            b.arc(0.0, h, r, r, 360.0, 270.0);
            b.l(0.0, r);
            b.arc(0.0, 0.0, r, r, 90.0, 0.0);
            b.z();
        }
        "can" | "flowChartMagneticDisk" => {
            let ry = if name == "can" { ss * 0.125 } else { h / 6.0 };
            b.m(0.0, ry);
            b.l(0.0, h - ry);
            b.arc(cx, h - ry, cx, ry, 180.0, 0.0);
            b.l(w, ry);
            b.arc(cx, ry, cx, ry, 360.0, 180.0);
            b.z();
            b.fill();
            b.ellipse(cx, ry, cx, ry);
            b.piece(if name == "can" { Paint::Lighten } else { Paint::None }, true);
        }
        "cube" => {
            let d = ss * 0.25;
            b.poly(&[[0.0, d], [w - d, d], [w - d, h], [0.0, h]]);
            b.fill();
            b.poly(&[[0.0, d], [d, 0.0], [w, 0.0], [w - d, d]]);
            b.piece(Paint::LightenLess, true);
            b.poly(&[[w - d, d], [w, 0.0], [w, h - d], [w - d, h]]);
            b.piece(Paint::Darken, true);
        }
        "bevel" => {
            let t = ss * 0.125;
            b.poly(&[[t, t], [w - t, t], [w - t, h - t], [t, h - t]]);
            b.fill();
            b.poly(&[[0.0, 0.0], [w, 0.0], [w - t, t], [t, t]]);
            b.piece(Paint::LightenLess, true);
            b.poly(&[[0.0, 0.0], [t, t], [t, h - t], [0.0, h]]);
            b.piece(Paint::Lighten, true);
            b.poly(&[[w, 0.0], [w, h], [w - t, h - t], [w - t, t]]);
            b.piece(Paint::DarkenLess, true);
            b.poly(&[[0.0, h], [t, h - t], [w - t, h - t], [w, h]]);
            b.piece(Paint::Darken, true);
        }
        "donut" => {
            let t = ss * 0.25;
            b.ellipse(cx, cy, cx, cy);
            b.ellipse(cx, cy, cx - t, cy - t);
        }
        "noSmoking" => {
            let t = ss * 0.1875;
            b.ellipse(cx, cy, cx, cy);
            // the hole, either side of the bar from top left to bottom right
            let (rx, ry) = ((cx - t).max(0.0), (cy - t).max(0.0));
            let s = (t / 2.0 / rx.min(ry).max(1e-6)).min(0.99);
            let a = s.asin().to_degrees();
            b.arc(cx, cy, rx, ry, 225.0 + a, 405.0 - a);
            b.z();
            b.arc(cx, cy, rx, ry, 45.0 + a, 225.0 - a);
            b.z();
        }
        "blockArc" => {
            let t = ss * 0.25;
            b.arc(cx, cy, cx, cy, 180.0, 360.0);
            b.arc(cx, cy, cx - t, cy - t, 360.0, 180.0);
            b.z();
        }
        "foldedCorner" => {
            let f = ss / 6.0;
            b.poly(&[[0.0, 0.0], [w, 0.0], [w, h - f], [w - f, h], [0.0, h]]);
            b.fill();
            b.poly(&[[w, h - f], [w - f * 0.8, h - f * 0.8], [w - f, h]]);
            b.piece(Paint::DarkenLess, true);
        }
        "smileyFace" => {
            b.ellipse(cx, cy, cx, cy);
            b.fill();
            b.ellipse(w * 0.33, h * 0.37, w * 0.05, h * 0.05);
            b.ellipse(w * 0.67, h * 0.37, w * 0.05, h * 0.05);
            b.piece(Paint::DarkenLess, true);
            b.m(w * 0.25, h * 0.7);
            b.c(w * 0.4, h * 0.82, w * 0.6, h * 0.82, w * 0.75, h * 0.7);
            b.line_piece();
        }
        "heart" => {
            b.m(cx, h * 0.25);
            b.c(w * 0.62, -h * 0.08, w * 1.1, h * 0.05, w * 0.97, h * 0.37);
            b.c(w * 0.88, h * 0.6, w * 0.6, h * 0.8, cx, h);
            b.c(w * 0.4, h * 0.8, w * 0.12, h * 0.6, w * 0.03, h * 0.37);
            b.c(-w * 0.1, h * 0.05, w * 0.38, -h * 0.08, cx, h * 0.25);
            b.z();
        }
        "lightningBolt" => {
            let pts = [
                [0.39, 0.0],
                [0.6, 0.32],
                [0.52, 0.35],
                [0.76, 0.63],
                [0.68, 0.66],
                [1.0, 1.0],
                [0.46, 0.71],
                [0.55, 0.67],
                [0.23, 0.42],
                [0.32, 0.38],
                [0.0, 0.18],
            ];
            b.poly(&pts.map(|p| [p[0] * w, p[1] * h]));
        }
        "sun" => {
            b.ellipse(cx, cy, cx * 0.5, cy * 0.5);
            for k in 0..8 {
                let a = (k as f32 * 45.0).to_radians();
                let at = |r: f32, da: f32| [cx + cx * r * (a + da).cos(), cy + cy * r * (a + da).sin()];
                b.poly(&[at(1.0, 0.0), at(0.66, 0.2), at(0.66, -0.2)]);
            }
        }
        "moon" => {
            b.arc(w, cy, w, cy, 90.0, 270.0);
            b.arc(w, cy, w * 0.5, cy, 270.0, 90.0);
            b.z();
        }
        "cloud" => cloud(&mut b, 0.0, 0.0, w, h),
        "arc" => {
            b.arc(cx, cy, cx, cy, 270.0, 360.0);
            b.line_piece();
        }
        "bracketPair" => {
            let r = ss / 6.0;
            b.m(r, h);
            b.arc(r, h - r, r, r, 90.0, 180.0);
            b.l(0.0, r);
            b.arc(r, r, r, r, 180.0, 270.0);
            b.line_piece();
            b.m(w - r, 0.0);
            b.arc(w - r, r, r, r, 270.0, 360.0);
            b.l(w, h - r);
            b.arc(w - r, h - r, r, r, 0.0, 90.0);
            b.line_piece();
        }
        "bracePair" => {
            let r = ss / 12.0;
            brace(&mut b, 0.0, 2.0 * r, r, h, false);
            brace(&mut b, w, 2.0 * r, r, h, true);
        }
        "leftBracket" => {
            let r = ss / 12.0;
            b.m(w, h);
            b.arc(w, h - r, w, r, 90.0, 180.0);
            b.l(0.0, r);
            b.arc(w, r, w, r, 180.0, 270.0);
            b.line_piece();
        }
        "rightBracket" => {
            let r = ss / 12.0;
            b.m(0.0, 0.0);
            b.arc(0.0, r, w, r, 270.0, 360.0);
            b.l(w, h - r);
            b.arc(0.0, h - r, w, r, 0.0, 90.0);
            b.line_piece();
        }
        "leftBrace" => brace(&mut b, 0.0, w, ss / 12.0, h, false),
        "rightBrace" => brace(&mut b, w, w, ss / 12.0, h, true),
        // ---- block arrows
        "rightArrow" => b.poly(&right_arrow(w, h)),
        "leftArrow" => b.poly(&right_arrow(w, h).map(|p| [w - p[0], p[1]])),
        "downArrow" => b.poly(&right_arrow(h, w).map(|p| [p[1], p[0]])),
        "upArrow" => b.poly(&right_arrow(h, w).map(|p| [p[1], h - p[0]])),
        "leftRightArrow" => b.poly(&left_right_arrow(w, h)),
        "upDownArrow" => b.poly(&left_right_arrow(h, w).map(|p| [p[1], p[0]])),
        "quadArrow" => {
            let (s, hw, hl) = (ss * 0.1125, ss * 0.225, ss * 0.225);
            b.poly(&[
                [0.0, cy],
                [hl, cy - hw],
                [hl, cy - s],
                [cx - s, cy - s],
                [cx - s, hl],
                [cx - hw, hl],
                [cx, 0.0],
                [cx + hw, hl],
                [cx + s, hl],
                [cx + s, cy - s],
                [w - hl, cy - s],
                [w - hl, cy - hw],
                [w, cy],
                [w - hl, cy + hw],
                [w - hl, cy + s],
                [cx + s, cy + s],
                [cx + s, h - hl],
                [cx + hw, h - hl],
                [cx, h],
                [cx - hw, h - hl],
                [cx - s, h - hl],
                [cx - s, cy + s],
                [hl, cy + s],
                [hl, cy + hw],
            ]);
        }
        "leftRightUpArrow" => {
            let (s, hw, hl) = (ss * 0.1125, ss * 0.225, ss * 0.225);
            let y = h - hw;
            b.poly(&[
                [0.0, y],
                [hl, y - hw],
                [hl, y - s],
                [cx - s, y - s],
                [cx - s, hl],
                [cx - hw, hl],
                [cx, 0.0],
                [cx + hw, hl],
                [cx + s, hl],
                [cx + s, y - s],
                [w - hl, y - s],
                [w - hl, y - hw],
                [w, y],
                [w - hl, h],
                [w - hl, y + s],
                [hl, y + s],
                [hl, h],
            ]);
        }
        "bentArrow" => {
            let (t, hw, hl) = (ss * 0.25, ss * 0.25, ss * 0.25);
            let top = hw - t / 2.0;
            let r = ss * 0.4375;
            let ri = (r - t).max(0.0);
            b.m(0.0, h);
            b.l(0.0, top + r);
            b.arc(r, top + r, r, r, 180.0, 270.0);
            b.l(w - hl, top);
            b.l(w - hl, 0.0);
            b.l(w, hw);
            b.l(w - hl, 2.0 * hw);
            b.l(w - hl, top + t);
            b.l(t + ri, top + t);
            if ri > 0.0 {
                b.arc(t + ri, top + t + ri, ri, ri, 270.0, 180.0);
            }
            b.l(t, h);
            b.z();
        }
        "uturnArrow" => {
            let (t, hw, hl) = (ss * 0.25, ss * 0.25, ss * 0.25);
            let xr = w - hw;
            let ca = (xr + t / 2.0) / 2.0;
            let tip = h * 0.75;
            // the arch is round, or flatter where the box is too short for a round one
            let ry = ca.min((tip - hl) * 0.7).max(t * 1.2);
            let (rx, rxi, ryi) = (ca, (ca - t).max(0.0), (ry - t).max(0.0));
            let yb = (tip - hl).max(ry);
            b.m(0.0, h);
            b.l(0.0, ry);
            b.arc(ca, ry, rx, ry, 180.0, 360.0);
            b.l(xr + t / 2.0, yb);
            b.l(xr + hw, yb);
            b.l(xr, tip);
            b.l(xr - hw, yb);
            b.l(xr - t / 2.0, yb);
            b.l(xr - t / 2.0, ry);
            b.arc(ca, ry, rxi, ryi, 360.0, 180.0);
            b.l(t, h);
            b.z();
        }
        "leftUpArrow" => {
            let (t, hw, hl) = (ss * 0.2, ss * 0.25, ss * 0.25);
            let (vx, hy) = (w - hw, h - hw);
            b.poly(&[
                [0.0, hy],
                [hl, hy - hw],
                [hl, hy - t / 2.0],
                [vx - t / 2.0, hy - t / 2.0],
                [vx - t / 2.0, hl],
                [vx - hw, hl],
                [vx, 0.0],
                [w, hl],
                [vx + t / 2.0, hl],
                [vx + t / 2.0, hy + t / 2.0],
                [hl, hy + t / 2.0],
                [hl, h],
            ]);
        }
        "bentUpArrow" => {
            let (t, hw, hl) = (ss * 0.25, ss * 0.25, ss * 0.25);
            let vx = w - hw;
            b.poly(&[
                [0.0, h - t],
                [vx - t / 2.0, h - t],
                [vx - t / 2.0, hl],
                [vx - hw, hl],
                [vx, 0.0],
                [w, hl],
                [vx + t / 2.0, hl],
                [vx + t / 2.0, h],
                [0.0, h],
            ]);
        }
        "curvedRightArrow" => return Some(curved_right(w, h)),
        "curvedLeftArrow" => return Some(map(curved_right(w, h), |p| [w - p[0], p[1]])),
        "curvedDownArrow" => return Some(map(curved_right(h, w), |p| [p[1], p[0]])),
        "curvedUpArrow" => return Some(map(curved_right(h, w), |p| [p[1], h - p[0]])),
        "stripedRightArrow" => {
            let s = ss / 32.0;
            let (y0, y1) = (h * 0.25, h * 0.75);
            b.poly(&[[0.0, y0], [s, y0], [s, y1], [0.0, y1]]);
            b.poly(&[[2.0 * s, y0], [4.0 * s, y0], [4.0 * s, y1], [2.0 * s, y1]]);
            let x0 = w - ss * 0.5;
            b.poly(&[[5.0 * s, y0], [x0, y0], [x0, 0.0], [w, cy], [x0, h], [x0, y1], [5.0 * s, y1]]);
        }
        "notchedRightArrow" => {
            let x0 = w - ss * 0.5;
            let n = h * 0.25 * (ss * 0.5) / ss;
            b.poly(&[[0.0, h * 0.25], [x0, h * 0.25], [x0, 0.0], [w, cy], [x0, h], [x0, h * 0.75], [0.0, h * 0.75], [n, cy]]);
        }
        "homePlate" => {
            let a = ss * 0.5;
            b.poly(&[[0.0, 0.0], [w - a, 0.0], [w, cy], [w - a, h], [0.0, h]]);
        }
        "chevron" => {
            let a = ss * 0.5;
            b.poly(&[[0.0, 0.0], [w - a, 0.0], [w, cy], [w - a, h], [0.0, h], [a, cy]]);
        }
        "rightArrowCallout" => b.poly(&arrow_callout(w, h)),
        "leftArrowCallout" => b.poly(&arrow_callout(w, h).map(|p| [w - p[0], p[1]])),
        "downArrowCallout" => b.poly(&arrow_callout(h, w).map(|p| [p[1], p[0]])),
        "upArrowCallout" => b.poly(&arrow_callout(h, w).map(|p| [p[1], h - p[0]])),
        "leftRightArrowCallout" => {
            let (s, hw, hl) = (ss * 0.125, ss * 0.25, ss * 0.25);
            let (x0, x1) = (w * 0.26, w * 0.74);
            b.poly(&[
                [0.0, cy],
                [hl, cy - hw],
                [hl, cy - s],
                [x0, cy - s],
                [x0, 0.0],
                [x1, 0.0],
                [x1, cy - s],
                [w - hl, cy - s],
                [w - hl, cy - hw],
                [w, cy],
                [w - hl, cy + hw],
                [w - hl, cy + s],
                [x1, cy + s],
                [x1, h],
                [x0, h],
                [x0, cy + s],
                [hl, cy + s],
                [hl, cy + hw],
            ]);
        }
        "quadArrowCallout" => {
            let (s, hw, hl) = (ss * 0.09, ss * 0.18, ss * 0.18);
            let (x0, x1, y0, y1) = (w * 0.26, w * 0.74, h * 0.26, h * 0.74);
            b.poly(&[
                [0.0, cy],
                [hl, cy - hw],
                [hl, cy - s],
                [x0, cy - s],
                [x0, y0],
                [cx - s, y0],
                [cx - s, hl],
                [cx - hw, hl],
                [cx, 0.0],
                [cx + hw, hl],
                [cx + s, hl],
                [cx + s, y0],
                [x1, y0],
                [x1, cy - s],
                [w - hl, cy - s],
                [w - hl, cy - hw],
                [w, cy],
                [w - hl, cy + hw],
                [w - hl, cy + s],
                [x1, cy + s],
                [x1, y1],
                [cx + s, y1],
                [cx + s, h - hl],
                [cx + hw, h - hl],
                [cx, h],
                [cx - hw, h - hl],
                [cx - s, h - hl],
                [cx - s, y1],
                [x0, y1],
                [x0, cy + s],
                [hl, cy + s],
                [hl, cy + hw],
            ]);
        }
        "circularArrow" => {
            let t = ss * 0.125;
            let hw = ss * 0.125;
            let (rx, ry) = (cx - hw, cy - hw);
            let end = 340.0f32;
            b.arc(cx, cy, rx + t / 2.0, ry + t / 2.0, 180.0, end);
            let at = |r: f32, a: f32| {
                let q = a.to_radians();
                [cx + (rx + r) * q.cos(), cy + (ry + r) * q.sin()]
            };
            let o = at(hw, end);
            b.l(o[0], o[1]);
            let tip = at(0.0, end + 22.0);
            b.l(tip[0], tip[1]);
            let i = at(-hw, end);
            b.l(i[0], i[1]);
            b.arc(cx, cy, rx - t / 2.0, ry - t / 2.0, end, 180.0);
            b.z();
        }
        // ---- equation shapes
        "mathPlus" => {
            let s = ss * 0.1176;
            let (lx, ly) = (w * 0.367, h * 0.367);
            b.poly(&[
                [cx - s, cy - ly],
                [cx + s, cy - ly],
                [cx + s, cy - s],
                [cx + lx, cy - s],
                [cx + lx, cy + s],
                [cx + s, cy + s],
                [cx + s, cy + ly],
                [cx - s, cy + ly],
                [cx - s, cy + s],
                [cx - lx, cy + s],
                [cx - lx, cy - s],
                [cx - s, cy - s],
            ]);
        }
        "mathMinus" => {
            let s = ss * 0.1176;
            let lx = w * 0.367;
            b.poly(&[[cx - lx, cy - s], [cx + lx, cy - s], [cx + lx, cy + s], [cx - lx, cy + s]]);
        }
        "mathMultiply" => {
            let (s, l) = (0.1176f32, 0.45f32);
            let unit = [[-s, -l], [s, -l], [s, -s], [l, -s], [l, s], [s, s], [s, l], [-s, l], [-s, s], [-l, s], [-l, -s], [-s, -s]];
            let r = std::f32::consts::FRAC_1_SQRT_2;
            b.poly(&unit.map(|p| [cx + (p[0] - p[1]) * r * w, cy + (p[0] + p[1]) * r * h]));
        }
        "mathDivide" => {
            let s = ss * 0.1176 * 0.5;
            let lx = w * 0.367;
            let r = ss * 0.1176;
            b.poly(&[[cx - lx, cy - s], [cx + lx, cy - s], [cx + lx, cy + s], [cx - lx, cy + s]]);
            b.ellipse(cx, cy - s - ss * 0.06 - r, r, r);
            b.ellipse(cx, cy + s + ss * 0.06 + r, r, r);
        }
        "mathEqual" => {
            let (t, g) = (ss * 0.2352, ss * 0.1176);
            let lx = w * 0.367;
            b.poly(&[[cx - lx, cy - g / 2.0 - t], [cx + lx, cy - g / 2.0 - t], [cx + lx, cy - g / 2.0], [cx - lx, cy - g / 2.0]]);
            b.poly(&[[cx - lx, cy + g / 2.0], [cx + lx, cy + g / 2.0], [cx + lx, cy + g / 2.0 + t], [cx - lx, cy + g / 2.0 + t]]);
        }
        "mathNotEqual" => {
            let (t, g) = (ss * 0.2352, ss * 0.1176);
            let lx = w * 0.367;
            b.poly(&[[cx - lx, cy - g / 2.0 - t], [cx + lx, cy - g / 2.0 - t], [cx + lx, cy - g / 2.0], [cx - lx, cy - g / 2.0]]);
            b.poly(&[[cx - lx, cy + g / 2.0], [cx + lx, cy + g / 2.0], [cx + lx, cy + g / 2.0 + t], [cx - lx, cy + g / 2.0 + t]]);
            b.piece(Paint::Fill, true);
            let s = ss * 0.06;
            let (x0, x1) = (cx - w * 0.12, cx + w * 0.12);
            b.poly(&[[x1 - s, h * 0.02], [x1 + s, h * 0.06], [x0 + s, h * 0.98], [x0 - s, h * 0.94]]);
        }
        // ---- flowchart
        "flowChartPredefinedProcess" => {
            b.rect(0.0, 0.0, w, h, [0.0; 4], [false; 4]);
            b.fill();
            b.m(w / 8.0, 0.0);
            b.l(w / 8.0, h);
            b.m(w * 7.0 / 8.0, 0.0);
            b.l(w * 7.0 / 8.0, h);
            b.line_piece();
        }
        "flowChartInternalStorage" => {
            b.rect(0.0, 0.0, w, h, [0.0; 4], [false; 4]);
            b.fill();
            b.m(w / 8.0, 0.0);
            b.l(w / 8.0, h);
            b.m(0.0, h / 8.0);
            b.l(w, h / 8.0);
            b.line_piece();
        }
        "flowChartDocument" => document(&mut b, 0.0, 0.0, w, h),
        "flowChartMultidocument" => {
            let d = w.min(h) * 0.1;
            document(&mut b, 2.0 * d, 0.0, w - 2.0 * d, h - 2.0 * d);
            b.fill();
            document(&mut b, d, d, w - 2.0 * d, h - 2.0 * d);
            b.fill();
            document(&mut b, 0.0, 2.0 * d, w - 2.0 * d, h - 2.0 * d);
        }
        "flowChartTerminator" => {
            let rx = w * 0.16;
            b.m(rx, 0.0);
            b.l(w - rx, 0.0);
            b.arc(w - rx, cy, rx, cy, 270.0, 450.0);
            b.l(rx, h);
            b.arc(rx, cy, rx, cy, 90.0, 270.0);
            b.z();
        }
        "flowChartPreparation" => b.poly(&[[w * 0.2, 0.0], [w * 0.8, 0.0], [w, cy], [w * 0.8, h], [w * 0.2, h], [0.0, cy]]),
        "flowChartManualInput" => b.poly(&[[0.0, h * 0.2], [w, 0.0], [w, h], [0.0, h]]),
        "flowChartManualOperation" => b.poly(&[[0.0, 0.0], [w, 0.0], [w * 0.8, h], [w * 0.2, h]]),
        "flowChartOffpageConnector" => b.poly(&[[0.0, 0.0], [w, 0.0], [w, h * 0.8], [cx, h], [0.0, h * 0.8]]),
        "flowChartPunchedCard" => b.poly(&[[w * 0.2, 0.0], [w, 0.0], [w, h], [0.0, h], [0.0, h * 0.2]]),
        "flowChartPunchedTape" => {
            let (lo, up) = (h * 0.2333, -h * 0.0333);
            b.m(0.0, h * 0.1);
            b.c(w * 0.15, lo, w * 0.35, lo, cx, h * 0.1);
            b.c(w * 0.65, up, w * 0.85, up, w, h * 0.1);
            b.l(w, h * 0.9);
            b.c(w * 0.85, h - lo, w * 0.65, h - lo, cx, h * 0.9);
            b.c(w * 0.35, h - up, w * 0.15, h - up, 0.0, h * 0.9);
            b.z();
        }
        "flowChartSummingJunction" | "flowChartOr" => {
            b.ellipse(cx, cy, cx, cy);
            b.fill();
            if name == "flowChartOr" {
                b.m(cx, 0.0);
                b.l(cx, h);
                b.m(0.0, cy);
                b.l(w, cy);
            } else {
                let r = std::f32::consts::FRAC_1_SQRT_2;
                b.m(cx - cx * r, cy - cy * r);
                b.l(cx + cx * r, cy + cy * r);
                b.m(cx + cx * r, cy - cy * r);
                b.l(cx - cx * r, cy + cy * r);
            }
            b.line_piece();
        }
        "flowChartCollate" => b.poly(&[[0.0, 0.0], [w, 0.0], [0.0, h], [w, h]]),
        "flowChartSort" => {
            b.poly(&[[cx, 0.0], [w, cy], [cx, h], [0.0, cy]]);
            b.fill();
            b.m(0.0, cy);
            b.l(w, cy);
            b.line_piece();
        }
        "flowChartMerge" => b.poly(&[[0.0, 0.0], [w, 0.0], [cx, h]]),
        "flowChartOnlineStorage" => {
            let r = w / 6.0;
            b.m(r, 0.0);
            b.l(w, 0.0);
            b.arc(w, cy, r, cy, 270.0, 90.0);
            b.l(r, h);
            b.arc(r, cy, r, cy, 90.0, 270.0);
            b.z();
        }
        "flowChartDelay" => {
            b.m(0.0, 0.0);
            b.l(cx, 0.0);
            b.arc(cx, cy, cx, cy, 270.0, 450.0);
            b.l(0.0, h);
            b.z();
        }
        "flowChartMagneticTape" => {
            b.arc(cx, cy, cx, cy, 90.0, 405.0);
            let y = cy + cy * std::f32::consts::FRAC_1_SQRT_2;
            b.l(w, y);
            b.l(w, h);
            b.z();
        }
        "flowChartMagneticDrum" => {
            let rx = w / 6.0;
            b.m(rx, 0.0);
            b.l(w - rx, 0.0);
            b.arc(w - rx, cy, rx, cy, 270.0, 450.0);
            b.l(rx, h);
            b.arc(rx, cy, rx, cy, 90.0, 270.0);
            b.z();
            b.fill();
            b.ellipse(w - rx, cy, rx, cy);
            b.piece(Paint::None, true);
        }
        "flowChartDisplay" => {
            let r = w / 6.0;
            b.m(0.0, cy);
            b.l(r, 0.0);
            b.l(w - r, 0.0);
            b.arc(w - r, cy, r, cy, 270.0, 450.0);
            b.l(r, h);
            b.z();
        }
        // ---- stars and banners
        "irregularSeal1" => {
            let spikes = [
                (-80.0, 1.0, 0.6),
                (-40.0, 0.82, 0.58),
                (-5.0, 1.0, 0.62),
                (30.0, 0.86, 0.6),
                (62.0, 1.0, 0.58),
                (100.0, 0.84, 0.62),
                (140.0, 1.0, 0.6),
                (178.0, 0.86, 0.58),
                (215.0, 0.98, 0.6),
                (245.0, 0.8, 0.6),
            ];
            b.poly(&burst(&spikes, w, h));
        }
        "irregularSeal2" => {
            let spikes = [
                (-85.0, 1.0, 0.62),
                (-60.0, 0.78, 0.6),
                (-35.0, 0.95, 0.64),
                (-10.0, 0.8, 0.6),
                (15.0, 1.0, 0.63),
                (40.0, 0.82, 0.6),
                (65.0, 0.97, 0.64),
                (92.0, 0.78, 0.6),
                (118.0, 1.0, 0.62),
                (145.0, 0.8, 0.6),
                (172.0, 0.98, 0.63),
                (200.0, 0.82, 0.6),
                (228.0, 1.0, 0.62),
                (252.0, 0.8, 0.61),
            ];
            b.poly(&burst(&spikes, w, h));
        }
        "star4" => b.poly(&star(4, 0.25, w, h)),
        "star5" => b.poly(&star(5, 0.382, w, h)),
        "star6" => b.poly(&star(6, 0.577, w, h)),
        "star7" => b.poly(&star(7, 0.692, w, h)),
        "star8" => b.poly(&star(8, 0.75, w, h)),
        "star10" => b.poly(&star(10, 0.851, w, h)),
        "star12" => b.poly(&star(12, 0.75, w, h)),
        "star16" => b.poly(&star(16, 0.75, w, h)),
        "star24" => b.poly(&star(24, 0.75, w, h)),
        "star32" => b.poly(&star(32, 0.75, w, h)),
        "ribbon2" => return Some(ribbon(w, h, false)),
        "ribbon" => return Some(map(ribbon(w, h, false), |p| [p[0], h - p[1]])),
        "ellipseRibbon2" => return Some(ribbon(w, h, true)),
        "ellipseRibbon" => return Some(map(ribbon(w, h, true), |p| [p[0], h - p[1]])),
        "verticalScroll" => return Some(scroll(w, h)),
        "horizontalScroll" => return Some(map(scroll(h, w), |p| [p[1], p[0]])),
        "wave" | "doubleWave" => {
            let double = name == "doubleWave";
            let a = if double { h * 0.0625 } else { h * 0.125 };
            let k = a / 0.289;
            let top = |b: &mut B, y: f32, rev: bool| {
                let waves: &[(f32, f32)] = if double { &[(0.0, 0.5), (0.5, 1.0)] } else { &[(0.0, 1.0)] };
                if rev {
                    for (x0, x1) in waves.iter().rev() {
                        let (x0, x1) = (x0 * w, x1 * w);
                        let d = (x1 - x0) / 3.0;
                        b.c(x1 - d, y + k, x0 + d, y - k, x0, y);
                    }
                } else {
                    for (x0, x1) in waves {
                        let (x0, x1) = (x0 * w, x1 * w);
                        let d = (x1 - x0) / 3.0;
                        b.c(x0 + d, y - k, x1 - d, y + k, x1, y);
                    }
                }
            };
            b.m(0.0, a);
            top(&mut b, a, false);
            b.l(w, h - a);
            top(&mut b, h - a, true);
            b.z();
        }
        // ---- callouts
        "wedgeRectCallout" | "wedgeRoundRectCallout" => {
            let r = if name == "wedgeRoundRectCallout" { ss / 6.0 } else { 0.0 };
            let tip = [w * 0.29, h * 1.125];
            b.m(r, 0.0);
            b.l(w - r, 0.0);
            if r > 0.0 {
                b.arc(w - r, r, r, r, 270.0, 360.0);
            }
            b.l(w, h - r);
            if r > 0.0 {
                b.arc(w - r, h - r, r, r, 0.0, 90.0);
            }
            b.l(w * 7.0 / 12.0, h);
            b.l(tip[0], tip[1]);
            b.l(w * 5.0 / 12.0, h);
            b.l(r, h);
            if r > 0.0 {
                b.arc(r, h - r, r, r, 90.0, 180.0);
            }
            b.l(0.0, r);
            if r > 0.0 {
                b.arc(r, r, r, r, 180.0, 270.0);
            }
            b.z();
        }
        "wedgeEllipseCallout" => {
            let tip = [w * 0.29, h * 1.125];
            let a = ((tip[1] - cy) / cy).atan2((tip[0] - cx) / cx).to_degrees();
            b.arc(cx, cy, cx, cy, a + 10.0, a + 350.0);
            b.l(tip[0], tip[1]);
            b.z();
        }
        "cloudCallout" => {
            cloud(&mut b, 0.0, 0.0, w, h);
            b.fill();
            let tip = [w * 0.29, h * 1.125];
            for (t, r) in [(0.25f32, 0.07f32), (0.55, 0.045), (0.85, 0.03)] {
                let x = cx + (tip[0] - cx) * (0.7 + 0.3 * t);
                let y = cy + (tip[1] - cy) * (0.7 + 0.3 * t);
                b.ellipse(x, y, w * r, h * r);
            }
        }
        _ => return callout(name, w, h),
    }
    Some(b.done())
}

/// Line callouts (`borderCallout1`…`accentBorderCallout3`): a box, and a line (bent once or twice)
/// from its left edge to a point below left of it; an accent bar beside the box.
fn callout(name: &str, w: f32, h: f32) -> Option<Vec<Piece>> {
    let bends = match name.chars().last()? {
        '1' => 1,
        '2' => 2,
        '3' => 3,
        _ => return None,
    };
    let kind = name.get(..name.len() - 1)?;
    let (border, accent) = match kind {
        "borderCallout" => (true, false),
        "accentCallout" => (false, true),
        "callout" => (false, false),
        "accentBorderCallout" => (true, true),
        _ => return None,
    };
    let mut b = B::new();
    b.rect(0.0, 0.0, w, h, [0.0; 4], [false; 4]);
    b.piece(Paint::Fill, border);
    let pts: Vec<[f32; 2]> = match bends {
        1 => vec![[-w * 0.0833, h * 0.1875], [-w * 0.3833, h * 1.125]],
        2 => vec![[-w * 0.0833, h * 0.1875], [-w * 0.1667, h * 0.1875], [-w * 0.4667, h * 1.125]],
        _ => vec![[-w * 0.0833, h * 0.1875], [-w * 0.1667, h * 0.1875], [-w * 0.1667, h], [-w * 0.0833, h * 1.1296]],
    };
    for (i, p) in pts.iter().enumerate() {
        if i == 0 {
            b.m(p[0], p[1]);
        } else {
            b.l(p[0], p[1]);
        }
    }
    b.line_piece();
    if accent {
        b.m(-w * 0.0833, 0.0);
        b.l(-w * 0.0833, h);
        b.line_piece();
    }
    Some(b.pieces)
}

/// A right arrow: the shaft half the height, the head as long as half the smaller side.
fn right_arrow(w: f32, h: f32) -> [[f32; 2]; 7] {
    let x0 = (w - w.min(h) * 0.5).max(0.0);
    [[0.0, h * 0.25], [x0, h * 0.25], [x0, 0.0], [w, h / 2.0], [x0, h], [x0, h * 0.75], [0.0, h * 0.75]]
}

fn left_right_arrow(w: f32, h: f32) -> [[f32; 2]; 10] {
    let a = (w.min(h) * 0.5).min(w / 2.0);
    let cy = h / 2.0;
    [[0.0, cy], [a, 0.0], [a, h * 0.25], [w - a, h * 0.25], [w - a, 0.0], [w, cy], [w - a, h], [w - a, h * 0.75], [a, h * 0.75], [a, h]]
}

/// A box with an arrow out of its right side.
fn arrow_callout(w: f32, h: f32) -> [[f32; 2]; 11] {
    let ss = w.min(h);
    let (s, hw, hl) = (ss * 0.125, ss * 0.25, ss * 0.25);
    let bw = w * 0.65;
    let cy = h / 2.0;
    [
        [0.0, 0.0],
        [bw, 0.0],
        [bw, cy - s],
        [w - hl, cy - s],
        [w - hl, cy - hw],
        [w, cy],
        [w - hl, cy + hw],
        [w - hl, cy + s],
        [bw, cy + s],
        [bw, h],
        [0.0, h],
    ]
}

/// Arrow: Curved Right: a band curving down the left side, its head at the bottom pointing right.
fn curved_right(w: f32, h: f32) -> Vec<Piece> {
    let ss = w.min(h);
    let t = ss * 0.25;
    let hw = ss * 0.25;
    let (cx, cy) = (w, h * 0.45);
    let (ro, ri) = ((w, h * 0.45), ((w - t).max(0.0), (h * 0.45 - t).max(0.0)));
    let end = 112.0f32;
    let mut b = B::new();
    b.arc(cx, cy, ro.0, ro.1, 270.0, end);
    let mid = |a: f32, k: f32| {
        let q = a.to_radians();
        let (rx, ry) = ((ro.0 + ri.0) / 2.0 + k, (ro.1 + ri.1) / 2.0 + k);
        [cx + rx * q.cos(), cy + ry * q.sin()]
    };
    let o = mid(end, hw);
    b.l(o[0], o[1]);
    let tip = mid(end - 28.0, 0.0);
    b.l(tip[0].min(w), tip[1].min(h));
    let i = mid(end, -hw);
    b.l(i[0], i[1]);
    b.arc(cx, cy, ri.0, ri.1, end, 270.0);
    b.z();
    b.done()
}

/// A document: a box whose bottom edge waves.
fn document(b: &mut B, x: f32, y: f32, w: f32, h: f32) {
    b.z();
    b.m(x, y);
    b.l(x + w, y);
    b.l(x + w, y + h * 0.8);
    b.c(x + w * 0.72, y + h * 0.66, x + w * 0.35, y + h * 1.06, x, y + h * 0.86);
    b.z();
}

/// A cloud of bumps round an ellipse filling the box.
fn cloud(b: &mut B, x: f32, y: f32, w: f32, h: f32) {
    let n = 10;
    let (cx, cy) = (x + w / 2.0, y + h / 2.0);
    let (rx, ry) = (w * 0.4, h * 0.38);
    let at = |k: usize| {
        let a = (-100.0 + 360.0 * k as f32 / n as f32).to_radians();
        let wob = if k.is_multiple_of(2) { 1.0 } else { 0.92 };
        [cx + rx * wob * a.cos(), cy + ry * wob * a.sin()]
    };
    let first = at(0);
    b.z();
    b.m(first[0], first[1]);
    for k in 0..n {
        let (p, q) = (at(k), at(k + 1));
        let out = |pt: [f32; 2]| {
            let (dx, dy) = ((pt[0] - cx) / rx, (pt[1] - cy) / ry);
            let len = (dx * dx + dy * dy).sqrt().max(1e-6);
            [pt[0] + dx / len * w * 0.16, pt[1] + dy / len * h * 0.16]
        };
        let (c1, c2) = (out(p), out(q));
        b.c(c1[0], c1[1], c2[0], c2[1], q[0], q[1]);
    }
    b.z();
}

/// A brace, its point toward the middle: at `x` (its outer edge; `mirror` faces it left), `bw`
/// wide, the corners rounded by `r`, `h` tall.
fn brace(b: &mut B, x: f32, bw: f32, r: f32, h: f32, mirror: bool) {
    let s = if mirror { -1.0 } else { 1.0 };
    let r = r.min(h / 4.0);
    let mut sub = B::new();
    // drawn facing right (opening right), then mirrored
    sub.m(bw, 0.0);
    sub.arc(bw, r, bw / 2.0, r, 270.0, 180.0);
    sub.l(bw / 2.0, h / 2.0 - r);
    sub.arc(0.0, h / 2.0 - r, bw / 2.0, r, 0.0, 90.0);
    sub.arc(0.0, h / 2.0 + r, bw / 2.0, r, 270.0, 360.0);
    sub.l(bw / 2.0, h - r);
    sub.arc(bw, h - r, bw / 2.0, r, 180.0, 90.0);
    sub.line_piece();
    let moved = map(sub.pieces, |p| [x + s * p[0], p[1]]);
    b.pieces.extend(moved);
}

/// Ribbon: Tilted Up (`curved`: Curved and Tilted Up): a panel raised in the middle, its tails
/// behind it either side, notched at their ends.
fn ribbon(w: f32, h: f32, curved: bool) -> Vec<Piece> {
    let (x0, x1) = (w * 0.2, w * 0.8);
    let (t0, t1) = (w * 0.25, w * 0.75);
    let mut b = B::new();
    let bend = |p: [f32; 2]| -> [f32; 2] {
        if curved {
            let u = p[0] / w * 2.0 - 1.0;
            [p[0], 0.85 * p[1] + 0.15 * h * u * u]
        } else {
            p
        }
    };
    // straight edges cut into short ones, so a bend curves them
    let poly = |b: &mut B, pts: &[[f32; 2]]| {
        let mut all = Vec::new();
        for (i, p) in pts.iter().enumerate() {
            let q = pts.get((i + 1) % pts.len()).copied().unwrap_or(*p);
            let n = if curved { 8 } else { 1 };
            for k in 0..n {
                let t = k as f32 / n as f32;
                all.push(bend([p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t]));
            }
        }
        b.poly(&all);
    };
    poly(&mut b, &[[0.0, h * 0.25], [t0, h * 0.25], [t0, h], [0.0, h], [w * 0.1, h * 0.625]]);
    poly(&mut b, &[[w, h * 0.25], [w * 0.9, h * 0.625], [w, h], [t1, h], [t1, h * 0.25]]);
    b.piece(Paint::Fill, true);
    poly(&mut b, &[[x0, h * 0.75], [t0, h * 0.75], [t0, h]]);
    poly(&mut b, &[[x1, h * 0.75], [t1, h], [t1, h * 0.75]]);
    b.piece(Paint::DarkenLess, true);
    poly(&mut b, &[[x0, 0.0], [x1, 0.0], [x1, h * 0.75], [x0, h * 0.75]]);
    b.done()
}

/// Scroll: Vertical: a sheet with a roll across its top and its bottom, curled at the ends.
fn scroll(w: f32, h: f32) -> Vec<Piece> {
    let r = w.min(h) * 0.0625;
    let mut b = B::new();
    b.rect(r, 0.0, w - r, 2.0 * r, [r; 4], [false; 4]);
    b.fill();
    b.rect(0.0, r, w - r, h - 2.0 * r, [0.0; 4], [false; 4]);
    b.fill();
    b.rect(0.0, h - 2.0 * r, w - r, 2.0 * r, [r; 4], [false; 4]);
    b.fill();
    b.ellipse(w - r, r, r / 2.0, r / 2.0);
    b.ellipse(r, h - r, r / 2.0, r / 2.0);
    b.piece(Paint::DarkenLess, true);
    b.pieces
}

/// The first point of a piece and the direction it leaves in, and its last point and the
/// direction it arrives from.
fn ends(p: &Piece) -> Option<(([f32; 2], [f32; 2]), ([f32; 2], [f32; 2]))> {
    let mut pts: Vec<[f32; 2]> = Vec::new();
    for s in &p.segs {
        match s {
            Seg::Move(a) | Seg::Line(a) => pts.push(*a),
            Seg::Cubic(a, b, c) => pts.extend([*a, *b, *c]),
            Seg::Close => {}
        }
    }
    pts.dedup();
    let (first, second) = (*pts.first()?, *pts.get(1)?);
    let (last, before) = (*pts.last()?, *pts.get(pts.len().checked_sub(2)?)?);
    Some(((first, second), (last, before)))
}

/// Arrowheads `len` long at the start and end of the first line piece.
fn arrowheads(pieces: &[Piece], start: bool, end: bool, len: f32) -> Vec<Piece> {
    let Some(line) = pieces.iter().find(|p| p.paint == Paint::None) else { return vec![] };
    let Some(((s0, s1), (e0, e1))) = ends(line) else { return vec![] };
    let head = |tip: [f32; 2], from: [f32; 2]| {
        let (dx, dy) = (tip[0] - from[0], tip[1] - from[1]);
        let d = (dx * dx + dy * dy).sqrt().max(1e-6);
        let (ux, uy) = (dx / d, dy / d);
        let base = [tip[0] - ux * len, tip[1] - uy * len];
        let half = len * 0.45;
        Piece {
            segs: vec![
                Seg::Move(tip),
                Seg::Line([base[0] - uy * half, base[1] + ux * half]),
                Seg::Line([base[0] + uy * half, base[1] - ux * half]),
                Seg::Close,
            ],
            paint: Paint::Ink,
            stroke: false,
        }
    };
    let mut out = Vec::new();
    if start {
        out.push(head(s0, s1));
    }
    if end {
        out.push(head(e0, e1));
    }
    out
}

/// The bounds (left, top, right, bottom) of what `pieces` draw.
pub fn bounds(pieces: &[Piece]) -> [f32; 4] {
    let mut b = [f32::MAX, f32::MAX, f32::MIN, f32::MIN];
    for q in pieces.iter().flat_map(flatten).flatten() {
        b = [b[0].min(q[0]), b[1].min(q[1]), b[2].max(q[0]), b[3].max(q[1])];
    }
    if b[0] > b[2] { [0.0; 4] } else { b }
}

/// A piece's subpaths as polylines (curves cut into short lines), for drawing where curves can't be.
pub fn flatten(p: &Piece) -> Vec<Vec<[f32; 2]>> {
    let mut out: Vec<Vec<[f32; 2]>> = Vec::new();
    let mut cur: Vec<[f32; 2]> = Vec::new();
    for s in &p.segs {
        match s {
            Seg::Move(a) => {
                if cur.len() > 1 {
                    out.push(std::mem::take(&mut cur));
                }
                cur = vec![*a];
            }
            Seg::Line(a) => cur.push(*a),
            Seg::Cubic(c1, c2, e) => {
                let s0 = cur.last().copied().unwrap_or(*c1);
                for k in 1..=12 {
                    let t = k as f32 / 12.0;
                    let u = 1.0 - t;
                    let f = |i: usize| u * u * u * s0[i] + 3.0 * u * u * t * c1[i] + 3.0 * u * t * t * c2[i] + t * t * t * e[i];
                    cur.push([f(0), f(1)]);
                }
            }
            Seg::Close => {
                if cur.len() > 1 {
                    out.push(std::mem::take(&mut cur));
                }
            }
        }
    }
    if cur.len() > 1 {
        out.push(cur);
    }
    // no zero-length steps (where an arc starts at the point the path is at): a stroke would
    // turn them into spikes
    for poly in &mut out {
        poly.dedup_by(|a, b| (a[0] - b[0]).abs() < 1e-4 && (a[1] - b[1]).abs() < 1e-4);
        while poly.len() > 2 && poly.first().zip(poly.last()).is_some_and(|(a, b)| (a[0] - b[0]).abs() < 1e-4 && (a[1] - b[1]).abs() < 1e-4) {
            poly.pop();
        }
    }
    out.retain(|p| p.len() > 1);
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_gallery_shape_is_drawn_finite_and_near_its_box() {
        let mut seen = std::collections::HashSet::new();
        for (band, shapes) in GALLERY {
            for (label, name) in *shapes {
                assert!(seen.insert((*band, *name)), "{name} twice in {band}");
                for (w, h) in [(100.0, 60.0), (40.0, 90.0), (1.0, 1.0), (500.0, 500.0)] {
                    let pieces = preset(name, w, h).unwrap_or_else(|| panic!("{label} ({name}) isn't drawn"));
                    assert!(!pieces.is_empty(), "{name}");
                    assert!(bounds(&pieces).iter().all(|v| v.is_finite()), "{name}");
                    // arrowheads stand out of the box at a line's ends, as they do on a line
                    let body: Vec<Piece> = pieces.iter().filter(|p| p.paint != Paint::Ink).cloned().collect();
                    let b = bounds(&body);
                    // callouts reach out of their box to what they point at; the rest stay near it
                    let slack = if name.contains("allout") { 0.6 } else { 0.12 };
                    assert!(
                        b[0] >= -w * slack - 0.01
                            && b[1] >= -h * slack - 0.01
                            && b[2] <= w * (1.0 + slack) + 0.01
                            && b[3] <= h * (1.0 + slack) + 0.01,
                        "{name} at {w}x{h}: {b:?}"
                    );
                    for p in &pieces {
                        assert!(matches!(p.segs.first(), Some(Seg::Move(_))), "{name}: a piece starts with a move");
                    }
                }
            }
        }
        assert_eq!(GALLERY.iter().map(|(_, s)| s.len()).sum::<usize>(), 9 + 9 + 42 + 27 + 6 + 28 + 20 + 16);
    }

    #[test]
    fn hostile_sizes_and_unknown_names() {
        assert!(preset("notAShape", 10.0, 10.0).is_none());
        assert!(!is_preset("rect;drop"));
        for (w, h) in [(f32::NAN, 5.0), (-3.0, 0.0), (f32::INFINITY, 1e30)] {
            let p = preset("star5", w, h).unwrap();
            assert!(bounds(&p).iter().all(|v| v.is_finite()));
        }
    }

    #[test]
    fn arrows_ride_on_their_lines() {
        let (g, s, e) = split_arrows("bentConnector3DoubleArrow");
        assert_eq!((g, s, e), ("bentConnector3", true, true));
        assert_eq!(split_arrows("arrow"), ("line", false, true));
        assert_eq!(split_arrows("rightArrow"), ("rightArrow", false, false));
        let p = preset("curvedConnector3Arrow", 100.0, 50.0).unwrap();
        assert_eq!(p.iter().filter(|p| p.paint == Paint::Ink).count(), 1);
        let p = preset("doubleArrow", 100.0, 50.0).unwrap();
        assert_eq!(p.iter().filter(|p| p.paint == Paint::Ink).count(), 2);
    }

    #[test]
    fn shading_darkens_and_lightens() {
        assert_eq!(shade([100, 100, 100], Paint::Darken), [60, 60, 60]);
        assert_eq!(shade([100, 100, 100], Paint::Lighten), [162, 162, 162]);
        assert_eq!(shade([100, 100, 100], Paint::Fill), [100, 100, 100]);
        // a cube's top is lighter than its front, its side darker
        let p = preset("cube", 50.0, 50.0).unwrap();
        let paints: Vec<Paint> = p.iter().map(|p| p.paint).collect();
        assert_eq!(paints, [Paint::Fill, Paint::LightenLess, Paint::Darken]);
    }

    #[test]
    fn flattening_keeps_the_ends() {
        let p = preset("ellipse", 20.0, 10.0).unwrap();
        let polys = flatten(&p[0]);
        assert_eq!(polys.len(), 1);
        // a closed outline starts at its first point and doesn't repeat it, nor any other
        assert_eq!(polys[0][0], [20.0, 5.0]);
        assert!(polys[0].windows(2).all(|w| w[0] != w[1]) && polys[0].last() != polys[0].first());
        assert!(polys[0].len() > 40);
        // the moon's arcs meet without a zero-length step
        let moon = flatten(&preset("moon", 100.0, 70.0).unwrap()[0]);
        assert!(moon[0].windows(2).all(|w| w[0] != w[1]));
    }
}
