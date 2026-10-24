//! DrawingML spreadsheet drawings (`xl/drawings/drawingN.xml`): anchors, pictures, shapes and
//! chart frames.

use std::fmt::Write as _;

use gridcraft_core::{CellRef, MAX_COLS, MAX_ROWS};
use gridcraft_model::{
    Anchor, AnchorMode, Color, Crop, GradientStop, Image, PictureLook, PictureShape, Shape, ShapeFormat, ShapeKind, ShapeTextAlign, ShapeTextAnchor,
    Sheet, Theme, Turn,
};

use crate::IoError;
use crate::read::Ctx;
use crate::tables::EMU_PER_PX;
use crate::xml::{El, esc, esc_attr};

mod ink;

// ---------------------------------------------------------------- reading

fn emu_px(v: Option<i64>) -> f32 {
    v.map(|e| (e as f64 / EMU_PER_PX).clamp(-1e7, 1e7) as f32).unwrap_or(0.0)
}

fn marker(e: &El) -> (CellRef, f32, f32) {
    let num = |n: &str| e.child(n).and_then(|c| c.text.trim().parse::<i64>().ok());
    let col = num("col").unwrap_or(0).clamp(0, MAX_COLS as i64 - 1) as u32;
    let row = num("row").unwrap_or(0).clamp(0, MAX_ROWS as i64 - 1) as u32;
    (CellRef::new(row, col), emu_px(num("colOff")).max(0.0), emu_px(num("rowOff")).max(0.0))
}

fn ext_px(e: Option<&El>) -> (f32, f32) {
    match e {
        Some(x) => (emu_px(x.attr_i64("cx")).max(0.0), emu_px(x.attr_i64("cy")).max(0.0)),
        None => (0.0, 0.0),
    }
}

fn read_anchor(a: &El, sheet: &Sheet) -> Option<Anchor> {
    match a.name.as_str() {
        "twoCellAnchor" => {
            let (cell, dx, dy) = marker(a.child("from")?);
            let (to, tdx, tdy) = a.child("to").map(marker).unwrap_or((cell, dx, dy));
            let x1 = sheet.col_left(cell.col) + dx as f64;
            let y1 = sheet.row_top(cell.row) + dy as f64;
            let x2 = sheet.col_left(to.col) + tdx as f64;
            let y2 = sheet.row_top(to.row) + tdy as f64;
            // Excel stores "Move and size with cells" as a plain twoCellAnchor, "Move but don't
            // size" as editAs="oneCell", and "Don't move or size" as editAs="absolute".
            let mode = match a.attr("editAs") {
                Some("absolute") => AnchorMode::Absolute,
                Some("oneCell") => AnchorMode::MoveOnly,
                _ => AnchorMode::MoveAndSize,
            };
            Some(Anchor { cell, dx, dy, width: (x2 - x1).max(0.0) as f32, height: (y2 - y1).max(0.0) as f32, mode })
        }
        "oneCellAnchor" => {
            let (cell, dx, dy) = marker(a.child("from")?);
            let (width, height) = ext_px(a.child("ext"));
            Some(Anchor { cell, dx, dy, width, height, mode: AnchorMode::MoveOnly })
        }
        "absoluteAnchor" => {
            let pos = a.child("pos");
            let x = emu_px(pos.and_then(|p| p.attr_i64("x"))).max(0.0) as f64;
            let y = emu_px(pos.and_then(|p| p.attr_i64("y"))).max(0.0) as f64;
            let col = sheet.col_at(x);
            let row = sheet.row_at(y);
            let (width, height) = ext_px(a.child("ext"));
            Some(Anchor {
                cell: CellRef::new(row, col),
                dx: (x - sheet.col_left(col)).max(0.0) as f32,
                dy: (y - sheet.row_top(row)).max(0.0) as f32,
                width,
                height,
                mode: AnchorMode::Absolute,
            })
        }
        _ => None,
    }
}

/// Colour of a DrawingML colour choice inside `e` (e.g. a `solidFill`).
pub fn dml_color(e: &El) -> Option<Color> {
    let (c, base) = if let Some(c) = e.child("srgbClr") {
        (c, Color::from_hex(c.attr("val")?)?)
    } else if let Some(c) = e.child("sysClr") {
        (c, Color::from_hex(c.attr("lastClr")?)?)
    } else if let Some(c) = e.child("schemeClr") {
        (c, scheme_color(c.attr("val")?)?)
    } else {
        let c = e.child("prstClr")?;
        (c, preset_color(c.attr("val")?)?)
    };
    // its lighter or darker version, as a tint
    Some(match (base, modifier_tint(c)) {
        (_, 0) => base,
        (Color::Theme(i, _), t) => Color::Theme(i, t),
        (Color::Rgb(v), t) => {
            let [r, g, b] = gridcraft_model::style::apply_tint([(v >> 16) as u8, (v >> 8) as u8, v as u8], f64::from(t) / 1000.0);
            Color::rgb(r, g, b)
        }
        (Color::Auto, _) => base,
    })
}

/// The opacity (`a:alpha`, 0 to 1) of the colour choice inside `e`, when it has one.
fn dml_alpha(e: &El) -> Option<f32> {
    let c = ["srgbClr", "sysClr", "schemeClr", "prstClr"].into_iter().find_map(|t| e.child(t))?;
    c.child("alpha").and_then(|a| a.attr_f64("val")).filter(|v| v.is_finite()).map(|v| (v / 100_000.0).clamp(0.0, 1.0) as f32)
}

/// A gradient fill's stops in order (`a:gsLst`, at most ten) and its angle in degrees (`a:lin ang`;
/// 90, top to bottom, when it has none).
fn read_gradient(g: &El) -> (Vec<GradientStop>, f32) {
    let mut stops: Vec<GradientStop> = g
        .child("gsLst")
        .map(|l| {
            l.kids("gs")
                .filter_map(|gs| {
                    let pos = gs.attr_f64("pos").filter(|v| v.is_finite())?;
                    Some(GradientStop { pos: (pos / 100_000.0).clamp(0.0, 1.0) as f32, color: dml_color(gs)? })
                })
                .take(10)
                .collect()
        })
        .unwrap_or_default();
    stops.sort_by(|a, b| a.pos.total_cmp(&b.pos));
    let angle = g.child("lin").and_then(|l| l.attr_f64("ang")).filter(|v| v.is_finite()).map_or(90.0, |a| (a / 60_000.0).rem_euclid(360.0) as f32);
    (stops, angle)
}

/// A colour's modifiers as one tint in thousandths: `lumMod` alone darkens (75%: -250) and
/// `lumMod` with `lumOff` lightens (60% and 40%: +400), just as a tint does; `shade` and `tint`
/// mix with black or white in linear light, given as the tint that comes closest for a typical
/// colour.
fn modifier_tint(c: &El) -> i16 {
    let val = |tag: &str| c.child(tag).and_then(|m| m.attr_f64("val")).filter(|v| v.is_finite()).map(|v| (v / 100_000.0).clamp(0.0, 1.0));
    let t = if let Some(off) = val("lumOff").filter(|o| *o > 0.0) {
        off
    } else if let Some(m) = val("lumMod") {
        m - 1.0
    } else if let Some(s) = val("shade") {
        s.powf(1.0 / 2.2) - 1.0
    } else if let Some(t) = val("tint") {
        1.0 - t.powf(1.0 / 2.2)
    } else {
        0.0
    };
    (t * 1000.0).round().clamp(-1000.0, 1000.0) as i16
}

/// A preset colour by its name (`a:prstClr`; Excel writes `white` and `black` for a text box), the
/// common ones.
fn preset_color(name: &str) -> Option<Color> {
    let rgb = match name {
        "black" => 0x000000,
        "white" => 0xFFFFFF,
        "red" => 0xFF0000,
        "green" => 0x008000,
        "lime" => 0x00FF00,
        "blue" => 0x0000FF,
        "yellow" => 0xFFFF00,
        "cyan" | "aqua" => 0x00FFFF,
        "magenta" | "fuchsia" => 0xFF00FF,
        "gray" | "grey" => 0x808080,
        "silver" => 0xC0C0C0,
        "orange" => 0xFFA500,
        "purple" => 0x800080,
        "navy" => 0x000080,
        "maroon" => 0x800000,
        "teal" => 0x008080,
        "olive" => 0x808000,
        _ => return None,
    };
    Some(Color::Rgb(rgb))
}

/// A theme colour by its scheme name (`accent1`, `tx1`…).
fn scheme_color(name: &str) -> Option<Color> {
    let i = match name {
        "bg1" | "lt1" => 0,
        "tx1" | "dk1" => 1,
        "bg2" | "lt2" => 2,
        "tx2" | "dk2" => 3,
        "accent1" => 4,
        "accent2" => 5,
        "accent3" => 6,
        "accent4" => 7,
        "accent5" => 8,
        "accent6" => 9,
        "hlink" => 10,
        "folHlink" => 11,
        _ => return None,
    };
    Some(Color::Theme(i, 0))
}

fn mime_for(name: &str) -> &'static str {
    let ext = name.rsplit('.').next().unwrap_or("").to_ascii_lowercase();
    match ext.as_str() {
        "png" => "image/png",
        "jpg" | "jpeg" | "jpe" => "image/jpeg",
        "gif" => "image/gif",
        "bmp" => "image/bmp",
        "tif" | "tiff" => "image/tiff",
        "svg" => "image/svg+xml",
        "emf" => "image/x-emf",
        "wmf" => "image/x-wmf",
        "webp" => "image/webp",
        _ => "application/octet-stream",
    }
}

pub fn read_drawing(cx: &mut Ctx<'_>, part: &str, sheet: &mut Sheet) -> Result<(), IoError> {
    let Some(x) = cx.optional_xml(part)? else { return Ok(()) };
    let rels = cx.pkg.rels(part)?;
    for a in &x.children {
        let Some(anchor) = read_anchor(a, sheet) else { continue };
        // Objects in markup-compatibility blocks: the first choice (chartex frames), else the
        // fallback.
        let objs: Vec<&El> = a
            .children
            .iter()
            .flat_map(|o| match o.name.as_str() {
                "AlternateContent" => o
                    .child("Choice")
                    .filter(|c| c.child("graphicFrame").is_some())
                    .or_else(|| o.child("Fallback"))
                    .map(|c| c.children.iter().collect())
                    .unwrap_or_default(),
                _ => vec![o],
            })
            .collect();
        for obj in objs {
            match obj.name.as_str() {
                "pic" => {
                    let Some(rid) = obj.path(&["blipFill", "blip"]).and_then(|b| b.attr("embed")) else { continue };
                    let Some(rel) = rels.iter().find(|r| r.id == rid && !r.external) else {
                        cx.warn("a linked (not embedded) picture was skipped");
                        continue;
                    };
                    let target = rel.target.clone();
                    let Some(data) = cx.pkg.read(&target)? else {
                        cx.warn(format!("missing picture {target}"));
                        continue;
                    };
                    let alt = obj.path(&["nvPicPr", "cNvPr"]).and_then(|c| c.attr("descr")).unwrap_or("").to_string();
                    let id = cx.next_object_id();
                    let turn = read_turn(obj.child("spPr"));
                    // a:srcRect l/t/r/b are thousandths of a percent cut from each side
                    let crop = obj.path(&["blipFill", "srcRect"]).map_or(Crop::default(), |r| {
                        let side = |k: &str| r.attr_i64(k).map_or(0.0, |v| (v as f32 / 100_000.0).clamp(0.0, 0.99));
                        Crop { left: side("l"), top: side("t"), right: side("r"), bottom: side("b") }
                    });
                    let look = read_look(obj.child("spPr"));
                    sheet.images.push(Image { id, anchor, data, mime: mime_for(&target).to_string(), alt, turn, crop, look });
                }
                "graphicFrame" => {
                    let Some(gd) = obj.path(&["graphic", "graphicData"]) else { continue };
                    let uri = gd.attr("uri").unwrap_or("");
                    if let Some(rid) = gd.child("chart").and_then(|c| c.attr("id")) {
                        let Some(rel) = rels.iter().find(|r| r.id == rid) else { continue };
                        let target = rel.target.clone();
                        if let Some(cx_xml) = cx.optional_xml(&target)? {
                            let chartex = uri.contains("chartex");
                            match if chartex { crate::chartex::read_chartex(&cx_xml) } else { crate::chart::read_chart(&cx_xml) } {
                                Some(mut ch) => {
                                    ch.id = cx.next_object_id();
                                    ch.anchor = anchor;
                                    ch.alt = obj.path(&["nvGraphicFramePr", "cNvPr"]).and_then(|c| c.attr("descr")).unwrap_or("").to_string();
                                    sheet.charts.push(ch);
                                }
                                None => cx.warn(format!("chart {target} has no supported plot and was skipped")),
                            }
                        }
                    } else if uri.contains("chartex") || uri.contains("chartEx") {
                        cx.warn("a histogram, waterfall, treemap… chart without its chart part was skipped");
                    } else {
                        cx.warn("an embedded object (SmartArt, OLE…) was skipped");
                    }
                }
                "sp" | "cxnSp" => {
                    if let Some(s) = read_shape(obj, anchor) {
                        let id = cx.next_object_id();
                        sheet.shapes.push(Shape { id, ..s });
                    }
                }
                "grpSp" => cx.warn("grouped shapes are not supported and were skipped"),
                _ => {}
            }
        }
    }
    Ok(())
}

/// The drawing name an icon is saved under, before the icon's own name (`Icon chart`).
const ICON_PREFIX: &str = "Icon ";

fn read_shape(obj: &El, anchor: Anchor) -> Option<Shape> {
    let sp = obj.child("spPr")?;
    if let Some(shape) = ink::read(obj, anchor) {
        return Some(shape);
    }
    let name = obj.path(&["nvSpPr", "cNvPr"]).and_then(|c| c.attr("name")).unwrap_or("");
    if let Some(icon) = name.strip_prefix(ICON_PREFIX).filter(|n| !n.trim().is_empty() && obj.child("txBody").is_none()) {
        let mut format = ShapeFormat { turn: read_turn(Some(sp)), ..Default::default() };
        format.alt = obj.path(&["nvSpPr", "cNvPr"]).and_then(|c| c.attr("descr")).unwrap_or("").to_string();
        let fill = sp.child("solidFill").and_then(dml_color).unwrap_or_default();
        return Some(Shape { id: 0, kind: ShapeKind::Icon, anchor, fill, line: Color::Auto, text: icon.chars().take(64).collect(), format });
    }
    let prst = sp.child("prstGeom").and_then(|g| g.attr("prst")).unwrap_or("rect");
    let tx_box = obj.path(&["nvSpPr", "cNvSpPr"]).is_some_and(|c| c.flag("txBox", false));
    let ln = sp.child("ln");
    let end_at = |tag: &str| ln.and_then(|l| l.child(tag)).is_some_and(|t| t.attr("type").is_some_and(|v| v != "none"));
    let (head, tail) = (end_at("headEnd"), end_at("tailEnd"));
    let kind = match prst {
        _ if tx_box => ShapeKind::TextBox,
        "rect" => ShapeKind::Rectangle,
        "roundRect" => ShapeKind::RoundedRectangle,
        "ellipse" => ShapeKind::Ellipse,
        "triangle" => ShapeKind::Triangle,
        // a line with an arrowhead at either end is an arrow (which ends have them is kept below)
        "line" | "straightConnector1" if head || tail => ShapeKind::Arrow,
        "line" | "straightConnector1" => ShapeKind::Line,
        _ if ShapeFormat::clean_preset(prst).is_empty() => ShapeKind::Rectangle,
        _ => ShapeKind::Preset,
    };
    // what the shape doesn't set comes from its style's references (`xdr:style`), as Excel
    // inserts shapes: a reference to style 0 is none
    let style_ref = |tag: &str| obj.path(&["style", tag]);
    let style_color = |tag: &str| style_ref(tag).filter(|r| r.attr_i64("idx").is_some_and(|i| i > 0)).and_then(dml_color);
    let solid = sp.child("solidFill");
    let fill = match solid.and_then(dml_color) {
        Some(c) => c,
        None if sp.child("noFill").is_some() => Color::Auto,
        // a gradient shows as its first stop's colour
        None => sp.path(&["gradFill", "gsLst", "gs"]).and_then(dml_color).or_else(|| style_color("fillRef")).unwrap_or_default(),
    };
    let ln_fill = ln.and_then(|l| l.child("solidFill")).and_then(dml_color);
    let ln_none = ln.is_some_and(|l| l.child("noFill").is_some());
    let line = ln_fill.or_else(|| if ln_none { None } else { style_color("lnRef") }).unwrap_or_default();
    let text = obj
        .child("txBody")
        .map(|tb| {
            tb.kids("p").map(|p| p.kids("r").filter_map(|r| r.child("t")).map(|t| t.text.as_str()).collect::<String>()).collect::<Vec<_>>().join("\n")
        })
        .unwrap_or_default();
    let mut format = read_text_format(obj.child("txBody"));
    format.fill_transparency = solid.and_then(dml_alpha).map_or(0.0, |a| 1.0 - a);
    if solid.is_none()
        && let Some(g) = sp.child("gradFill")
    {
        (format.gradient, format.gradient_angle) = read_gradient(g);
    }
    format.no_line = ln_none || (ln_fill.is_none() && style_ref("lnRef").is_some_and(|r| r.attr_i64("idx") == Some(0)));
    if format.text_color.is_none() {
        format.text_color = style_ref("fontRef").and_then(dml_color);
    }
    format.turn = read_turn(Some(sp));
    format.alt = obj.path(&["nvSpPr", "cNvPr"]).and_then(|c| c.attr("descr")).unwrap_or("").to_string();
    if kind == ShapeKind::Preset {
        format.preset = ShapeFormat::clean_preset(prst);
    }
    Some(Shape { id: 0, kind, anchor, fill, line, text, format }.with_arrows(head, tail))
}

/// A shape's text formatting: vertical anchor (`a:bodyPr anchor`), the first paragraph's
/// alignment and direction (`a:pPr algn`, `rtl`) and the first run's properties (`a:rPr`: size,
/// bold, italic, fill, outline, `a:latin` typeface).
fn read_text_format(body: Option<&El>) -> ShapeFormat {
    let mut f = ShapeFormat::default();
    let Some(body) = body else { return f };
    f.anchor = body.child("bodyPr").and_then(|b| b.attr("anchor")).map(|a| match a {
        "ctr" => ShapeTextAnchor::Middle,
        "b" => ShapeTextAnchor::Bottom,
        _ => ShapeTextAnchor::Top,
    });
    let ppr = body.kids("p").next().and_then(|p| p.child("pPr"));
    f.align = ppr.and_then(|p| p.attr("algn")).map(|a| match a {
        "ctr" => ShapeTextAlign::Center,
        "r" => ShapeTextAlign::Right,
        _ => ShapeTextAlign::Left,
    });
    f.rtl = ppr.is_some_and(|p| p.flag("rtl", false));
    let rpr = body.kids("p").flat_map(|p| p.kids("r")).find_map(|r| r.child("rPr"));
    if let Some(r) = rpr {
        // `sz` is in hundredths of a point (100..=400000 in the schema).
        f.font_size = r.attr_f64("sz").filter(|v| v.is_finite()).map(|v| (v / 100.0).clamp(1.0, 4000.0) as f32);
        f.bold = r.flag("b", false);
        f.italic = r.flag("i", false);
        f.font = r.child("latin").and_then(|l| l.attr("typeface")).filter(|t| !t.is_empty() && !t.starts_with('+')).map(str::to_string);
        f.text_color = r.child("solidFill").and_then(dml_color);
        f.text_outline = r.child("ln").and_then(|l| {
            let c = l.child("solidFill").and_then(dml_color)?;
            // Width in EMU (12700 per point); Excel's default hairline is 0.75 pt.
            let w = l.attr_f64("w").filter(|w| w.is_finite()).map_or(0.75, |w| (w / 12700.0).clamp(0.0, 100.0)) as f32;
            Some((c, w))
        });
    }
    f
}

// ---------------------------------------------------------------- writing

/// The opening `<xdr:twoCellAnchor>` for a model anchor, carrying Excel's `editAs` when the mode
/// is not the default: omitted for "Move and size with cells", `editAs="oneCell"` for "Move but
/// don't size", `editAs="absolute"` for "Don't move or size". (Excel writes pictures this way;
/// the standalone `oneCellAnchor`/`absoluteAnchor` elements are read on import but not written.)
/// Returns the opening tag and the matching closing tag.
fn anchor_open(sheet: &Sheet, a: &Anchor) -> (String, &'static str) {
    let px = |v: f64| ((v.max(0.0)) * EMU_PER_PX).round() as i64;
    let x1 = sheet.col_left(a.cell.col) + a.dx as f64;
    let y1 = sheet.row_top(a.cell.row) + a.dy as f64;
    let x2 = x1 + a.width.max(0.0) as f64;
    let y2 = y1 + a.height.max(0.0) as f64;
    let tc = sheet.col_at(x2);
    let tr = sheet.row_at(y2);
    let tdx = x2 - sheet.col_left(tc);
    let tdy = y2 - sheet.row_top(tr);
    let edit = match a.mode {
        AnchorMode::MoveAndSize => "",
        AnchorMode::MoveOnly => " editAs=\"oneCell\"",
        AnchorMode::Absolute => " editAs=\"absolute\"",
    };
    let open = format!(
        "<xdr:twoCellAnchor{edit}><xdr:from><xdr:col>{}</xdr:col><xdr:colOff>{}</xdr:colOff><xdr:row>{}</xdr:row><xdr:rowOff>{}</xdr:rowOff></xdr:from><xdr:to><xdr:col>{tc}</xdr:col><xdr:colOff>{}</xdr:colOff><xdr:row>{tr}</xdr:row><xdr:rowOff>{}</xdr:rowOff></xdr:to>",
        a.cell.col,
        px(a.dx as f64),
        a.cell.row,
        px(a.dy as f64),
        px(tdx),
        px(tdy)
    );
    (open, "</xdr:twoCellAnchor>")
}

fn xfrm(a: &Anchor, tag: &str) -> String {
    xfrm_turned(a, tag, &Turn::default())
}

/// `a:xfrm` with the object's turn: `rot` in 60000ths of a degree, `flipH`, `flipV`.
fn xfrm_turned(a: &Anchor, tag: &str, t: &Turn) -> String {
    let e = |v: f32| ((v.max(0.0) as f64) * EMU_PER_PX).round() as i64;
    let mut attrs = String::new();
    let rot = (f64::from(t.rotation).rem_euclid(360.0) * 60000.0).round() as i64;
    if rot != 0 && rot < 21_600_000 {
        let _ = write!(attrs, " rot=\"{rot}\"");
    }
    if t.flip_h {
        attrs.push_str(" flipH=\"1\"");
    }
    if t.flip_v {
        attrs.push_str(" flipV=\"1\"");
    }
    format!("<{tag}{attrs}><a:off x=\"0\" y=\"0\"/><a:ext cx=\"{}\" cy=\"{}\"/></{tag}>", e(a.width), e(a.height))
}

/// ` descr="…"` for an object's alt text (nothing when it has none).
fn descr_attr(alt: &str) -> String {
    if alt.is_empty() { String::new() } else { format!(" descr=\"{}\"", esc_attr(alt)) }
}

/// A picture's frame and shadow inside its `spPr` (`a:ln`, `a:effectLst`).
fn look_xml(l: &PictureLook, theme: &Theme) -> String {
    let mut s = String::new();
    if let Some((c, w)) = l.border
        && let Some(c) = srgb(&c, theme)
    {
        let emu = ((if w.is_finite() { w.clamp(0.25, 100.0) } else { 1.0 }) * 12700.0).round() as i64;
        let _ = write!(s, "<a:ln w=\"{emu}\"><a:solidFill>{c}</a:solidFill></a:ln>");
    }
    if l.shadow {
        s.push_str("<a:effectLst><a:outerShdw blurRad=\"50800\" dist=\"38100\" dir=\"2700000\" algn=\"tl\" rotWithShape=\"0\"><a:prstClr val=\"black\"><a:alpha val=\"40000\"/></a:prstClr></a:outerShdw></a:effectLst>");
    }
    s
}

/// A picture's outline, frame and shadow from its `spPr`.
fn read_look(sp: Option<&El>) -> PictureLook {
    let Some(sp) = sp else { return PictureLook::default() };
    let shape = match sp.child("prstGeom").and_then(|g| g.attr("prst")) {
        Some("roundRect") => PictureShape::RoundedRectangle,
        Some("ellipse") => PictureShape::Oval,
        _ => PictureShape::Rectangle,
    };
    let border = sp.child("ln").and_then(|ln| {
        let c = ln.child("solidFill").and_then(dml_color)?;
        let w = ln.attr_i64("w").map_or(0.75, |w| (w as f32 / 12700.0).clamp(0.25, 100.0));
        Some((c, w))
    });
    let shadow = sp.path(&["effectLst", "outerShdw"]).is_some();
    PictureLook { shape, border, shadow }
}

/// `a:srcRect` for a cropped picture (nothing when it isn't).
fn src_rect(c: &Crop) -> String {
    if c.is_none() {
        return String::new();
    }
    let v = |f: f32| ((if f.is_finite() { f.clamp(0.0, 0.99) } else { 0.0 }) * 100_000.0).round() as i64;
    format!("<a:srcRect l=\"{}\" t=\"{}\" r=\"{}\" b=\"{}\"/>", v(c.left), v(c.top), v(c.right), v(c.bottom))
}

/// An object's turn from its `spPr/a:xfrm`.
fn read_turn(sp_pr: Option<&El>) -> Turn {
    let Some(x) = sp_pr.and_then(|sp| sp.child("xfrm")) else { return Turn::default() };
    let rotation = x.attr_i64("rot").map_or(0.0, |r| (r.rem_euclid(21_600_000) as f64 / 60000.0) as f32);
    Turn { rotation, flip_h: x.flag("flipH", false), flip_v: x.flag("flipV", false) }
}

pub fn srgb(c: &Color, theme: &Theme) -> Option<String> {
    let [r, g, b] = c.resolve(theme)?;
    Some(format!("<a:srgbClr val=\"{r:02X}{g:02X}{b:02X}\"/>"))
}

/// `a:srgbClr` for a colour seen `alpha` solid (0 to 1; an `a:alpha` when less than 1).
fn srgb_alpha(c: &Color, theme: &Theme, alpha: f32) -> Option<String> {
    let [r, g, b] = c.resolve(theme)?;
    let a = if alpha.is_finite() { alpha.clamp(0.0, 1.0) } else { 1.0 };
    Some(if a < 1.0 {
        format!("<a:srgbClr val=\"{r:02X}{g:02X}{b:02X}\"><a:alpha val=\"{}\"/></a:srgbClr>", (a * 100_000.0).round() as i64)
    } else {
        format!("<a:srgbClr val=\"{r:02X}{g:02X}{b:02X}\"/>")
    })
}

/// One drawing object to write.
pub enum Obj<'a> {
    Image(&'a Image, String),
    Chart(&'a gridcraft_model::Chart, String),
    Shape(&'a Shape),
}

/// The drawing part for a sheet's objects (relationship ids already assigned).
pub fn write_drawing(sheet: &Sheet, theme: &Theme, objs: &[Obj<'_>]) -> String {
    let mut s = String::from(
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">",
    );
    for (i, o) in objs.iter().enumerate() {
        let id = i + 2;
        let close_tag;
        match o {
            Obj::Image(img, rid) => {
                let (open, close) = anchor_open(sheet, &img.anchor);
                close_tag = close;
                s.push_str(&open);
                let _ = write!(
                    s,
                    "<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"{id}\" name=\"Picture {}\" descr=\"{}\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr><xdr:blipFill><a:blip r:embed=\"{rid}\"/>{}<a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr>{}<a:prstGeom prst=\"{}\"><a:avLst/></a:prstGeom>{}</xdr:spPr></xdr:pic>",
                    id - 1,
                    esc_attr(&img.alt),
                    src_rect(&img.crop),
                    xfrm_turned(&img.anchor, "a:xfrm", &img.turn),
                    match img.look.shape {
                        PictureShape::Rectangle => "rect",
                        PictureShape::RoundedRectangle => "roundRect",
                        PictureShape::Oval => "ellipse",
                    },
                    look_xml(&img.look, theme)
                );
            }
            Obj::Chart(ch, rid) if crate::chartex::is_chartex(ch.kind) => {
                // Chartex frames come with a fallback for readers that don't know them.
                let (open, close) = anchor_open(sheet, &ch.anchor);
                close_tag = close;
                s.push_str(&open);
                let (prefix, ns) = crate::chartex::requires(ch.kind);
                let _ = write!(
                    s,
                    "<mc:AlternateContent xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"><mc:Choice xmlns:{prefix}=\"{ns}\" Requires=\"{prefix}\"><xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"{id}\" name=\"Chart {}\"{}/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>{}<a:graphic><a:graphicData uri=\"http://schemas.microsoft.com/office/drawing/2014/chartex\"><cx:chart xmlns:cx=\"http://schemas.microsoft.com/office/drawing/2014/chartex\" r:id=\"{rid}\"/></a:graphicData></a:graphic></xdr:graphicFrame></mc:Choice><mc:Fallback><xdr:sp macro=\"\" textlink=\"\"><xdr:nvSpPr><xdr:cNvPr id=\"0\" name=\"\"/><xdr:cNvSpPr><a:spLocks noTextEdit=\"1\"/></xdr:cNvSpPr></xdr:nvSpPr><xdr:spPr>{}<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr><xdr:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"en-US\"/><a:t>This chart type can't be shown by this application.</a:t></a:r></a:p></xdr:txBody></xdr:sp></mc:Fallback></mc:AlternateContent>",
                    id - 1,
                    descr_attr(&ch.alt),
                    xfrm(&ch.anchor, "xdr:xfrm"),
                    xfrm(&ch.anchor, "a:xfrm")
                );
            }
            Obj::Chart(ch, rid) => {
                let (open, close) = anchor_open(sheet, &ch.anchor);
                close_tag = close;
                s.push_str(&open);
                let _ = write!(
                    s,
                    "<xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"{id}\" name=\"Chart {}\"{}/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>{}<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\"><c:chart r:id=\"{rid}\"/></a:graphicData></a:graphic></xdr:graphicFrame>",
                    id - 1,
                    descr_attr(&ch.alt),
                    xfrm(&ch.anchor, "xdr:xfrm")
                );
            }
            Obj::Shape(sh) => {
                let (open, close) = anchor_open(sheet, &sh.anchor);
                close_tag = close;
                s.push_str(&open);
                if let Some(xml) = ink::write(sh, theme, id) {
                    s.push_str(&xml);
                    s.push_str("<xdr:clientData/>");
                    s.push_str(close_tag);
                    continue;
                }
                let (prst, tx) = match sh.kind {
                    ShapeKind::Rectangle => ("rect", false),
                    ShapeKind::RoundedRectangle => ("roundRect", false),
                    ShapeKind::Ellipse => ("ellipse", false),
                    ShapeKind::Triangle => ("triangle", false),
                    ShapeKind::Line | ShapeKind::Arrow => ("line", false),
                    ShapeKind::TextBox => ("rect", true),
                    ShapeKind::Icon | ShapeKind::Ink => ("rect", false),
                    ShapeKind::Preset if !sh.format.preset.is_empty() => (sh.format.preset.as_str(), false),
                    ShapeKind::Preset => ("rect", false),
                };
                let prst = ShapeFormat::clean_preset(prst);
                let prst = if prst.is_empty() { "rect".to_string() } else { prst };
                let _ = write!(
                    s,
                    "<xdr:sp macro=\"\" textlink=\"\"><xdr:nvSpPr><xdr:cNvPr id=\"{id}\" name=\"{}\"{}/><xdr:cNvSpPr{}/></xdr:nvSpPr><xdr:spPr>{}<a:prstGeom prst=\"{prst}\"><a:avLst/></a:prstGeom>",
                    // an icon keeps its name in the drawing's name (`Icon chart`), not as text
                    if sh.kind == ShapeKind::Icon { format!("{ICON_PREFIX}{}", esc_attr(&sh.text)) } else { format!("Shape {}", id - 1) },
                    descr_attr(&sh.format.alt),
                    if tx { " txBox=\"1\"" } else { "" },
                    xfrm_turned(&sh.anchor, "a:xfrm", &sh.format.turn)
                );
                if !sh.format.gradient.is_empty() {
                    s.push_str("<a:gradFill rotWithShape=\"1\"><a:gsLst>");
                    for g in &sh.format.gradient {
                        if let Some(c) = srgb(&g.color, theme) {
                            let _ = write!(s, "<a:gs pos=\"{}\">{c}</a:gs>", (g.pos.clamp(0.0, 1.0) * 100_000.0).round() as i64);
                        }
                    }
                    let ang = (f64::from(sh.format.gradient_angle).rem_euclid(360.0) * 60_000.0).round() as i64;
                    let _ = write!(s, "</a:gsLst><a:lin ang=\"{ang}\" scaled=\"0\"/></a:gradFill>");
                } else if let Some(c) = srgb_alpha(&sh.fill, theme, 1.0 - sh.format.fill_transparency) {
                    let _ = write!(s, "<a:solidFill>{c}</a:solidFill>");
                }
                let line_c = srgb(&sh.line, theme);
                if sh.format.no_line {
                    s.push_str("<a:ln><a:noFill/></a:ln>");
                } else if line_c.is_some() || sh.kind == ShapeKind::Arrow || sh.format.head_end || sh.format.tail_end {
                    s.push_str("<a:ln w=\"9525\">");
                    match line_c {
                        Some(c) => {
                            let _ = write!(s, "<a:solidFill>{c}</a:solidFill>");
                        }
                        None => s.push_str("<a:solidFill><a:srgbClr val=\"000000\"/></a:solidFill>"),
                    }
                    // headEnd before tailEnd, as CT_LineProperties has them
                    if sh.format.head_end {
                        s.push_str("<a:headEnd type=\"triangle\"/>");
                    }
                    if sh.arrow_ends().1 {
                        s.push_str("<a:tailEnd type=\"triangle\"/>");
                    }
                    s.push_str("</a:ln>");
                }
                s.push_str("</xdr:spPr>");
                if !sh.text.is_empty() && sh.kind != ShapeKind::Icon {
                    let f = &sh.format;
                    let anchor = match f.anchor {
                        Some(ShapeTextAnchor::Top) => " anchor=\"t\"",
                        Some(ShapeTextAnchor::Middle) => " anchor=\"ctr\"",
                        Some(ShapeTextAnchor::Bottom) => " anchor=\"b\"",
                        None if sh.kind == ShapeKind::TextBox => " anchor=\"t\"",
                        None => "",
                    };
                    let _ = write!(s, "<xdr:txBody><a:bodyPr wrap=\"square\" rtlCol=\"0\"{anchor}/><a:lstStyle/>");
                    let algn = match f.align {
                        None => "",
                        Some(ShapeTextAlign::Left) => " algn=\"l\"",
                        Some(ShapeTextAlign::Center) => " algn=\"ctr\"",
                        Some(ShapeTextAlign::Right) => " algn=\"r\"",
                    };
                    let rtl = if f.rtl { " rtl=\"1\"" } else { "" };
                    let ppr = if algn.is_empty() && rtl.is_empty() { String::new() } else { format!("<a:pPr{algn}{rtl}/>") };
                    let (rpr_attrs, rpr_body) = run_props(f, theme);
                    for line in sh.text.split('\n') {
                        if line.is_empty() {
                            let _ = write!(s, "<a:p>{ppr}<a:endParaRPr lang=\"en-US\"{rpr_attrs}/></a:p>");
                        } else if rpr_body.is_empty() {
                            let _ = write!(s, "<a:p>{ppr}<a:r><a:rPr lang=\"en-US\"{rpr_attrs}/><a:t>{}</a:t></a:r></a:p>", esc(line));
                        } else {
                            let _ =
                                write!(s, "<a:p>{ppr}<a:r><a:rPr lang=\"en-US\"{rpr_attrs}>{rpr_body}</a:rPr><a:t>{}</a:t></a:r></a:p>", esc(line));
                        }
                    }
                    s.push_str("</xdr:txBody>");
                }
                s.push_str("</xdr:sp>");
            }
        }
        s.push_str("<xdr:clientData/>");
        s.push_str(close_tag);
    }
    s.push_str("</xdr:wsDr>");
    s
}

/// A text run's `a:rPr` attributes (size, bold, italic) and children in schema order (outline,
/// fill, typeface).
fn run_props(f: &ShapeFormat, theme: &Theme) -> (String, String) {
    let mut attrs = String::new();
    if let Some(sz) = f.font_size.filter(|v| v.is_finite()) {
        let _ = write!(attrs, " sz=\"{}\"", (sz.clamp(1.0, 4000.0) * 100.0).round() as u32);
    }
    if f.bold {
        attrs.push_str(" b=\"1\"");
    }
    if f.italic {
        attrs.push_str(" i=\"1\"");
    }
    let mut body = String::new();
    if let Some((c, w)) = &f.text_outline
        && let Some(c) = srgb(c, theme)
    {
        let emu = (w.clamp(0.0, 100.0) * 12700.0).round() as u32;
        let _ = write!(body, "<a:ln w=\"{emu}\"><a:solidFill>{c}</a:solidFill></a:ln>");
    }
    if let Some(c) = f.text_color.as_ref().and_then(|c| srgb(c, theme)) {
        let _ = write!(body, "<a:solidFill>{c}</a:solidFill>");
    }
    if let Some(font) = &f.font {
        let _ = write!(body, "<a:latin typeface=\"{}\"/>", esc(font));
    }
    (attrs, body)
}

/// File extension for an image MIME type.
pub fn image_ext(mime: &str, data: &[u8]) -> &'static str {
    match mime.to_ascii_lowercase().as_str() {
        "image/png" => "png",
        "image/jpeg" | "image/jpg" => "jpeg",
        "image/gif" => "gif",
        "image/bmp" => "bmp",
        "image/tiff" => "tiff",
        "image/svg+xml" => "svg",
        "image/x-emf" => "emf",
        "image/x-wmf" => "wmf",
        "image/webp" => "webp",
        _ => {
            if data.starts_with(b"\x89PNG") {
                "png"
            } else if data.starts_with(&[0xFF, 0xD8]) {
                "jpeg"
            } else if data.starts_with(b"GIF8") {
                "gif"
            } else {
                "png"
            }
        }
    }
}

pub fn image_content_type(ext: &str) -> &'static str {
    match ext {
        "jpeg" => "image/jpeg",
        "gif" => "image/gif",
        "bmp" => "image/bmp",
        "tiff" => "image/tiff",
        "svg" => "image/svg+xml",
        "emf" => "image/x-emf",
        "wmf" => "image/x-wmf",
        "webp" => "image/webp",
        _ => "image/png",
    }
}
