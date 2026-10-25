//! Picture Styles: the gallery's styles as the phone app makes each, a frame, a fill behind the
//! picture, effects and 3-D settings. GridCraft draws the frame, shadows, reflections and soft
//! edges; the fill behind and the 3-D (bevels, lighting, a camera) are kept as DrawingML and
//! written back, a camera's turn drawn flat.

use std::fmt::Write as _;

use gridcraft_model::{BoxAlign, Color, Compound, FrameStyle, LineCap, LineJoin, PictureLook, PictureShape, Reflection, Shadow};

/// The gallery in order, three to a row: the name shown and what `picture.style` takes.
pub const PICTURE_STYLES: [(&str, &str); 33] = [
    ("Simple Frame, White", "simpleFrameWhite"),
    ("Beveled Matte, White", "beveledMatteWhite"),
    ("Thick White Matte, Bevel", "thickWhiteMatteBevel"),
    ("Simple Frame, Black", "simpleFrameBlack"),
    ("Moderate Frame, Black", "moderateFrameBlack"),
    ("Thick Matte, Black", "thickMatteBlack"),
    ("Compound Frame, Black", "compoundFrameBlack"),
    ("Double Frame, Black", "doubleFrameBlack"),
    ("Thick Double Frame, Black", "thickDoubleFrameBlack"),
    ("Soft Edge Rectangle", "softEdgeRectangle"),
    ("Moderate Frame, White", "moderateFrameWhite"),
    ("Metal Frame", "metalFrame"),
    ("Center Shadow Rectangle", "centerShadowRectangle"),
    ("Drop Shadow Rectangle", "dropShadowRectangle"),
    ("Perspective Shadow, Left", "perspectiveShadowLeft"),
    ("Soft Edge Oval", "softEdgeOval"),
    ("Metal Oval", "metalOval"),
    ("Beveled Oval, Black", "beveledOvalBlack"),
    ("Reflected Rounded Rectangle", "reflectedRoundedRectangle"),
    ("Reflected Bevel, White", "reflectedBevelWhite"),
    ("Reflected Bevel, Black", "reflectedBevelBlack"),
    ("Rounded White Rectangle, Glow", "roundedWhiteRectangleGlow"),
    ("Rounded Diagonal Corner, White", "roundedDiagonalCornerWhite"),
    ("Snip Diagonal Corner, White", "snipDiagonalCornerWhite"),
    ("Rotated, White", "rotatedWhite"),
    ("Relaxed Perspective, White", "relaxedPerspectiveWhite"),
    ("Perspective Shadow, White", "perspectiveShadowWhite"),
    ("Bevel Rectangle", "bevelRectangle"),
    ("Bevel Perspective", "bevelPerspective"),
    ("Metal Rounded Rectangle", "metalRoundedRectangle"),
    ("Reflected Perspective Right", "reflectedPerspectiveRight"),
    ("Perspective Rectangle, Bevel", "perspectiveRectangleBevel"),
    ("Bevel Perspective Left, White", "bevelPerspectiveLeftWhite"),
];

/// A solid fill behind the picture, `rgb` darkened by `shade` thousandths of a percent kept (0:
/// none).
fn backdrop(rgb: u32, shade: u32) -> String {
    if shade == 0 {
        format!("<a:solidFill><a:srgbClr val=\"{rgb:06X}\"/></a:solidFill>")
    } else {
        format!("<a:solidFill><a:srgbClr val=\"{rgb:06X}\"><a:shade val=\"{shade}\"/></a:srgbClr></a:solidFill>")
    }
}

/// A `rot` element (latitude, longitude, revolution in 60000ths of a degree), or none.
fn rot(r: Option<(i64, i64, i64)>) -> String {
    r.map_or_else(String::new, |(lat, lon, rev)| format!("<a:rot lat=\"{lat}\" lon=\"{lon}\" rev=\"{rev}\"/>"))
}

/// A 3-D scene: its camera (preset, field of view when not 0, turn) and light rig.
fn scene(camera: &str, fov: i64, camera_rot: Option<(i64, i64, i64)>, rig: &str, dir: &str, rig_rot: Option<(i64, i64, i64)>) -> String {
    let mut s = format!("<a:scene3d><a:camera prst=\"{camera}\"");
    if fov != 0 {
        let _ = write!(s, " fov=\"{fov}\"");
    }
    match camera_rot {
        Some(_) => {
            let _ = write!(s, ">{}</a:camera>", rot(camera_rot));
        }
        None => s.push_str("/>"),
    }
    match rig_rot {
        Some(_) => {
            let _ = write!(s, "<a:lightRig rig=\"{rig}\" dir=\"{dir}\">{}</a:lightRig></a:scene3d>", rot(rig_rot));
        }
        None => {
            let _ = write!(s, "<a:lightRig rig=\"{rig}\" dir=\"{dir}\"/></a:scene3d>");
        }
    }
    s
}

/// A 3-D shape: its depth and contour widths (EMU, 0: none), its material ("" the usual), its
/// top bevel (width and height in EMU, -1 left out; its kind, "" the usual), and depth and contour
/// colours.
fn sp3d(extrusion_h: i64, contour_w: i64, material: &str, bevel: Option<(i64, i64, &str)>, extrusion: Option<u32>, contour: Option<u32>) -> String {
    let mut s = String::from("<a:sp3d");
    if extrusion_h != 0 {
        let _ = write!(s, " extrusionH=\"{extrusion_h}\"");
    }
    if contour_w != 0 {
        let _ = write!(s, " contourW=\"{contour_w}\"");
    }
    if !material.is_empty() {
        let _ = write!(s, " prstMaterial=\"{material}\"");
    }
    s.push('>');
    if let Some((w, h, prst)) = bevel {
        s.push_str("<a:bevelT");
        if w >= 0 {
            let _ = write!(s, " w=\"{w}\"");
        }
        if h >= 0 {
            let _ = write!(s, " h=\"{h}\"");
        }
        if !prst.is_empty() {
            let _ = write!(s, " prst=\"{prst}\"");
        }
        s.push_str("/>");
    }
    if let Some(c) = extrusion {
        let _ = write!(s, "<a:extrusionClr><a:srgbClr val=\"{c:06X}\"/></a:extrusionClr>");
    }
    if let Some(c) = contour {
        let _ = write!(s, "<a:contourClr><a:srgbClr val=\"{c:06X}\"/></a:contourClr>");
    }
    s.push_str("</a:sp3d>");
    s
}

/// A style by what `picture.style` takes (see [`PICTURE_STYLES`]).
pub fn picture_style(id: &str) -> Option<PictureLook> {
    Some(match id {
        // Simple Frame, White
        "simpleFrameWhite" => PictureLook {
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            border: Some((Color::Rgb(0xFFFFFF), 7.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.4,
                blur: 4.330709,
                distance: 1.417323,
                direction: 90.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "twoPt", "t", Some((0, 0, 7200000)))
                + &sp3d(0, 0, "", Some((25400, 19050, "")), None, Some(0xFFFFFF)),
            ..Default::default()
        },
        // Beveled Matte, White
        "beveledMatteWhite" => PictureLook {
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            border: Some((Color::Rgb(0xFFFFFF), 15.0)),
            frame: FrameStyle { cap: Some(LineCap::Round), compound: Compound::Single, solid: false, join: None },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.41,
                blur: 3.937008,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "twoPt", "t", Some((0, 0, 7800000)))
                + &sp3d(0, 6350, "", Some((50800, 16510, "")), None, Some(0xC0C0C0)),
            ..Default::default()
        },
        // Thick White Matte, Bevel
        "thickWhiteMatteBevel" => PictureLook {
            border: Some((Color::Rgb(0xFFFFFF), 28.0)),
            frame: FrameStyle { cap: Some(LineCap::Round), compound: Compound::Single, solid: false, join: None },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.41,
                blur: 3.937008,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "twoPt", "t", Some((0, 0, 7800000)))
                + &sp3d(0, 6350, "", Some((50800, 16510, "")), None, Some(0xC0C0C0)),
            ..Default::default()
        },
        // Simple Frame, Black
        "simpleFrameBlack" => PictureLook {
            border: Some((Color::Rgb(0x000000), 3.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: true, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.43,
                blur: 4.0,
                distance: 3.0,
                direction: 45.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Moderate Frame, Black
        "moderateFrameBlack" => PictureLook {
            border: Some((Color::Rgb(0x000000), 10.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.4,
                blur: 4.5,
                distance: 4.0,
                direction: 45.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Thick Matte, Black
        "thickMatteBlack" => PictureLook {
            backdrop_xml: backdrop(0x000000, 95000),
            border: Some((Color::Rgb(0x000000), 35.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.4,
                blur: 20.0,
                distance: 15.0,
                direction: 45.0,
                scale_x: 100.0,
                scale_y: 90.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::BottomLeft,
                turns: false,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Compound Frame, Black
        "compoundFrameBlack" => PictureLook {
            border: Some((Color::Rgb(0x000000), 7.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::ThickThin, solid: true, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: true,
                color: Color::Rgb(0x000000),
                opacity: 1.0,
                blur: 6.0,
                distance: 0.0,
                direction: 0.0,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Double Frame, Black
        "doubleFrameBlack" => PictureLook {
            border: Some((Color::Rgb(0x000000), 18.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::ThickThin, solid: true, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: true,
                color: Color::Rgb(0x000000),
                opacity: 1.0,
                blur: 6.0,
                distance: 0.0,
                direction: 0.0,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Thick Double Frame, Black
        "thickDoubleFrameBlack" => PictureLook {
            border: Some((Color::Rgb(0x000000), 35.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::ThickThin, solid: true, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: true,
                color: Color::Rgb(0x000000),
                opacity: 1.0,
                blur: 6.0,
                distance: 0.0,
                direction: 0.0,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Soft Edge Rectangle
        "softEdgeRectangle" => PictureLook { no_frame: true, soft_edge: Some(8.858268), ..Default::default() },
        // Moderate Frame, White
        "moderateFrameWhite" => PictureLook {
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            border: Some((Color::Rgb(0xFFFFFF), 15.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.4,
                blur: 4.330709,
                distance: 1.417323,
                direction: 90.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "twoPt", "t", Some((0, 0, 7200000)))
                + &sp3d(0, 0, "", Some((25400, 19050, "")), None, Some(0xFFFFFF)),
            ..Default::default()
        },
        // Metal Frame
        "metalFrame" => PictureLook {
            border: Some((Color::Rgb(0xC8C6BD), 15.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: true, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.43,
                blur: 20.0,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::BottomLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("perspectiveFront", 5400000, None, "threePt", "t", Some((0, 0, 2100000)))
                + &sp3d(25400, 0, "", Some((304800, 152400, "hardEdge")), Some(0x000000), None),
            ..Default::default()
        },
        // Center Shadow Rectangle
        "centerShadowRectangle" => PictureLook {
            no_frame: true,
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.7,
                blur: 15.0,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Drop Shadow Rectangle
        "dropShadowRectangle" => PictureLook {
            no_frame: true,
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x333333),
                opacity: 0.65,
                blur: 23.0,
                distance: 11.0,
                direction: 45.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Perspective Shadow, Left
        "perspectiveShadowLeft" => PictureLook {
            no_frame: true,
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.2,
                blur: 6.0,
                distance: 7.5,
                direction: 175.0,
                scale_x: 97.0,
                scale_y: 23.0,
                skew_x: 15.0,
                skew_y: 0.0,
                align: BoxAlign::BottomRight,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "twoPt", "t", Some((0, 0, 7800000))) + &sp3d(0, 6350, "", None, None, Some(0xC0C0C0)),
            ..Default::default()
        },
        // Soft Edge Oval
        "softEdgeOval" => PictureLook { shape: PictureShape::Oval, no_frame: true, soft_edge: Some(8.858268), ..Default::default() },
        // Metal Oval
        "metalOval" => PictureLook {
            shape: PictureShape::Oval,
            border: Some((Color::Rgb(0xC8C6BD), 15.0)),
            frame: FrameStyle { cap: Some(LineCap::Round), compound: Compound::Single, solid: true, join: None },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 1.0,
                blur: 10.0,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::BottomLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("perspectiveFront", 5400000, None, "threePt", "t", Some((0, 0, 19200000)))
                + &sp3d(25400, 0, "", Some((304800, 152400, "hardEdge")), Some(0x000000), None),
            ..Default::default()
        },
        // Beveled Oval, Black
        "beveledOvalBlack" => PictureLook {
            shape: PictureShape::Oval,
            border: Some((Color::Rgb(0x333333), 5.0)),
            frame: FrameStyle { cap: Some(LineCap::Round), compound: Compound::Single, solid: false, join: None },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.22,
                blur: 30.0,
                distance: 23.0,
                direction: 90.0,
                scale_x: -80.0,
                scale_y: -18.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::Bottom,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "contrasting", "t", Some((0, 0, 3000000)))
                + &sp3d(0, 7620, "", Some((95250, 31750, "")), None, Some(0x333333)),
            ..Default::default()
        },
        // Reflected Rounded Rectangle
        "reflectedRoundedRectangle" => PictureLook {
            shape: PictureShape::RoundedRectangle,
            adjust: vec![("adj".into(), 8594)],
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            no_frame: true,
            reflection: Some(Reflection {
                blur: 1.0,
                start: 0.38,
                end: 0.0,
                end_pos: 0.28,
                distance: 0.393701,
                direction: 90.0,
                scale_y: -100.0,
                align: BoxAlign::BottomLeft,
                turns: false,
            }),
            ..Default::default()
        },
        // Reflected Bevel, White
        "reflectedBevelWhite" => PictureLook {
            shape: PictureShape::RoundedRectangle,
            adjust: vec![("adj".into(), 4167)],
            backdrop_xml: backdrop(0xFFFFFF, 0),
            border: Some((Color::Rgb(0xEAEAEA), 6.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            reflection: Some(Reflection {
                blur: 1.0,
                start: 0.33,
                end: 0.0,
                end_pos: 0.28,
                distance: 0.393701,
                direction: 90.0,
                scale_y: -100.0,
                align: BoxAlign::BottomLeft,
                turns: false,
            }),
            three_d_xml: scene("orthographicFront", 0, None, "threePt", "t", Some((0, 0, 2700000)))
                + &sp3d(0, 6350, "", Some((-1, 38100, "")), None, Some(0xC0C0C0)),
            ..Default::default()
        },
        // Reflected Bevel, Black
        "reflectedBevelBlack" => PictureLook {
            shape: PictureShape::RoundedRectangle,
            adjust: vec![("adj".into(), 4167)],
            backdrop_xml: backdrop(0xFFFFFF, 0),
            border: Some((Color::Rgb(0x292929), 6.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            reflection: Some(Reflection {
                blur: 1.0,
                start: 0.28,
                end: 0.0,
                end_pos: 0.28,
                distance: 0.393701,
                direction: 90.0,
                scale_y: -100.0,
                align: BoxAlign::BottomLeft,
                turns: false,
            }),
            three_d_xml: scene("orthographicFront", 0, None, "threePt", "t", Some((0, 0, 2700000)))
                + &sp3d(0, 0, "", Some((-1, 38100, "")), None, Some(0xC0C0C0)),
            ..Default::default()
        },
        // Rounded White Rectangle, Glow
        "roundedWhiteRectangleGlow" => PictureLook {
            shape: PictureShape::RoundedRectangle,
            border: Some((Color::Rgb(0xFFFFFF), 7.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.43,
                blur: 20.0,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Rounded Diagonal Corner, White
        "roundedDiagonalCornerWhite" => PictureLook {
            shape: PictureShape::Preset("round2DiagRect".into()),
            adjust: vec![("adj1".into(), 16667), ("adj2".into(), 0)],
            border: Some((Color::Rgb(0xFFFFFF), 7.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.43,
                blur: 20.0,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            ..Default::default()
        },
        // Snip Diagonal Corner, White
        "snipDiagonalCornerWhite" => PictureLook {
            shape: PictureShape::Preset("snip2DiagRect".into()),
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            border: Some((Color::Rgb(0xFFFFFF), 7.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.45,
                blur: 7.0,
                distance: 0.0,
                direction: 0.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "twoPt", "t", Some((0, 0, 7200000)))
                + &sp3d(0, 0, "", Some((25400, 19050, "")), None, Some(0xFFFFFF)),
            ..Default::default()
        },
        // Rotated, White
        "rotatedWhite" => PictureLook {
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            border: Some((Color::Rgb(0xFFFFFF), 15.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.3,
                blur: 5.11811,
                distance: 4.0,
                direction: 215.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 3.25,
                skew_y: 2.416667,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, Some((0, 0, 360000)), "twoPt", "t", Some((0, 0, 7200000)))
                + &sp3d(0, 12700, "", Some((25400, 19050, "")), None, Some(0x969696)),
            ..Default::default()
        },
        // Relaxed Perspective, White
        "relaxedPerspectiveWhite" => PictureLook {
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            border: Some((Color::Rgb(0xFDFDFD), 8.0)),
            frame: FrameStyle { cap: Some(LineCap::Square), compound: Compound::Single, solid: false, join: Some(LineJoin::Miter(800000)) },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.2,
                blur: 4.5,
                distance: 2.952756,
                direction: 126.0,
                scale_x: 100.0,
                scale_y: 98.0,
                skew_x: 1.833333,
                skew_y: 3.333333,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("perspectiveRelaxed", 0, Some((18960000, 0, 0)), "twoPt", "t", Some((0, 0, 7200000)))
                + &sp3d(0, 0, "matte", Some((22860, 12700, "")), None, Some(0xFFFFFF)),
            ..Default::default()
        },
        // Perspective Shadow, White
        "perspectiveShadowWhite" => PictureLook {
            border: Some((Color::Rgb(0xFFFFFF), 10.0)),
            frame: FrameStyle { cap: Some(LineCap::Round), compound: Compound::Single, solid: false, join: None },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.2,
                blur: 6.0,
                distance: 7.5,
                direction: 175.0,
                scale_x: 97.0,
                scale_y: 23.0,
                skew_x: 15.0,
                skew_y: 0.0,
                align: BoxAlign::BottomRight,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "twoPt", "t", Some((0, 0, 7800000)))
                + &sp3d(0, 6350, "", Some((50800, 16510, "")), None, Some(0xC0C0C0)),
            ..Default::default()
        },
        // Bevel Rectangle
        "bevelRectangle" => PictureLook {
            shape: PictureShape::RoundedRectangle,
            adjust: vec![("adj".into(), 16667)],
            no_frame: true,
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.4,
                blur: 6.0,
                distance: 3.0,
                direction: 130.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("orthographicFront", 0, None, "contrasting", "t", Some((0, 0, 4200000)))
                + &sp3d(0, 0, "plastic", Some((381000, 114300, "relaxedInset")), None, Some(0x969696)),
            ..Default::default()
        },
        // Bevel Perspective
        "bevelPerspective" => PictureLook {
            shape: PictureShape::RoundedRectangle,
            adjust: vec![("adj".into(), 16667)],
            no_frame: true,
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.3,
                blur: 12.0,
                distance: 0.944882,
                direction: 15.0,
                scale_x: 100.0,
                scale_y: 98.0,
                skew_x: 1.833333,
                skew_y: 3.333333,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("perspectiveRelaxed", 0, Some((19800000, 1200000, 20820000)), "threePt", "t", None)
                + &sp3d(0, 6350, "matte", Some((101600, 101600, "")), None, Some(0x969696)),
            ..Default::default()
        },
        // Metal Rounded Rectangle
        "metalRoundedRectangle" => PictureLook {
            shape: PictureShape::RoundedRectangle,
            adjust: vec![("adj".into(), 11111)],
            border: Some((Color::Rgb(0xC8C6BD), 15.0)),
            frame: FrameStyle { cap: Some(LineCap::Round), compound: Compound::Single, solid: true, join: None },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.45,
                blur: 8.0,
                distance: 4.0,
                direction: 120.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("perspectiveFront", 5400000, None, "threePt", "t", Some((0, 0, 19200000)))
                + &sp3d(25400, 0, "", Some((304800, 152400, "hardEdge")), Some(0xFFFFFF), None),
            ..Default::default()
        },
        // Reflected Perspective Right
        "reflectedPerspectiveRight" => PictureLook {
            no_frame: true,
            reflection: Some(Reflection {
                blur: 1.0,
                start: 0.3,
                end: 0.0,
                end_pos: 0.3,
                distance: 0.393701,
                direction: 90.0,
                scale_y: -100.0,
                align: BoxAlign::BottomLeft,
                turns: false,
            }),
            three_d_xml: scene("perspectiveContrastingLeftFacing", 0, Some((300000, 19800000, 0)), "threePt", "t", Some((0, 0, 2700000)))
                + &sp3d(0, 0, "", Some((63500, 50800, "")), None, None),
            ..Default::default()
        },
        // Perspective Rectangle, Bevel
        "perspectiveRectangleBevel" => PictureLook {
            no_frame: true,
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.3,
                blur: 12.0,
                distance: 0.944882,
                direction: 15.0,
                scale_x: 100.0,
                scale_y: 98.0,
                skew_x: 1.833333,
                skew_y: 3.333333,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("perspectiveRelaxed", 0, Some((19800000, 1200000, 20820000)), "threePt", "t", None)
                + &sp3d(0, 6350, "matte", Some((101600, 101600, "")), None, Some(0x969696)),
            ..Default::default()
        },
        // Bevel Perspective Left, White
        "bevelPerspectiveLeftWhite" => PictureLook {
            backdrop_xml: backdrop(0xFFFFFF, 85000),
            border: Some((Color::Rgb(0xFFFFFF), 15.0)),
            frame: FrameStyle { cap: Some(LineCap::Round), compound: Compound::Single, solid: false, join: None },
            shadow: Some(Shadow {
                inner: false,
                color: Color::Rgb(0x000000),
                opacity: 0.33,
                blur: 2.85,
                distance: 1.0,
                direction: 190.0,
                scale_x: 100.0,
                scale_y: 100.0,
                skew_x: 0.0,
                skew_y: 0.0,
                align: BoxAlign::TopLeft,
                turns: false,
                ..Shadow::default()
            }),
            three_d_xml: scene("perspectiveContrastingLeftFacing", 0, Some((540000, 2100000, 0)), "soft", "t", None)
                + &sp3d(0, 12700, "matte", Some((63500, 50800, "")), None, Some(0xC0C0C0)),
            ..Default::default()
        },
        _ => return None,
    })
}
