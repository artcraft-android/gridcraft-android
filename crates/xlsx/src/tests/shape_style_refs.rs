//! Shapes that take their colours from their style's references (`xdr:style`), as Excel inserts
//! them, and colours with modifiers (`lumMod`, `lumOff`, `shade`).

use gridcraft_model::{Color, ShapeKind};

use super::minimal;
use crate::read_xlsx;

fn anchor(obj: &str) -> String {
    format!(
        r#"<xdr:twoCellAnchor><xdr:from><xdr:col>1</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>1</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>4</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>10</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>{obj}<xdr:clientData/></xdr:twoCellAnchor>"#
    )
}

fn sp(sp_pr: &str, style: &str) -> String {
    anchor(&format!(
        r#"<xdr:sp macro="" textlink=""><xdr:nvSpPr><xdr:cNvPr id="2" name="Shape 1"/><xdr:cNvSpPr/></xdr:nvSpPr><xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1828800"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom>{sp_pr}</xdr:spPr>{style}</xdr:sp>"#
    ))
}

/// The style Excel gives a shape it inserts: a 15% shade of accent 1 around accent 1, light text.
const FILLED: &str = r#"<xdr:style><a:lnRef idx="2"><a:schemeClr val="accent1"><a:shade val="15000"/></a:schemeClr></a:lnRef><a:fillRef idx="1"><a:schemeClr val="accent1"/></a:fillRef><a:effectRef idx="0"><a:schemeClr val="accent1"/></a:effectRef><a:fontRef idx="minor"><a:schemeClr val="lt1"/></a:fontRef></xdr:style>"#;

fn shapes(objs: &[String]) -> Vec<gridcraft_model::Shape> {
    let drawing = format!(
        r#"<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">{}</xdr:wsDr>"#,
        objs.concat()
    );
    let rels = r#"<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>"#;
    let bytes = minimal(
        r#"<sheetData/><drawing r:id="rId1"/>"#,
        &[("xl/worksheets/_rels/sheet1.xml.rels", rels), ("xl/drawings/drawing1.xml", &drawing)],
        "",
        "",
    );
    read_xlsx(&bytes).unwrap().0.sheet(0).unwrap().shapes.clone()
}

#[test]
fn shapes_take_what_they_leave_out_from_their_style() {
    let connector = anchor(
        r#"<xdr:cxnSp macro=""><xdr:nvCxnSpPr><xdr:cNvPr id="3" name="Connector 2"/><xdr:cNvCxnSpPr/></xdr:nvCxnSpPr><xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="1828800" cy="1828800"/></a:xfrm><a:prstGeom prst="straightConnector1"><a:avLst/></a:prstGeom><a:ln><a:tailEnd type="triangle"/></a:ln></xdr:spPr><xdr:style><a:lnRef idx="1"><a:schemeClr val="accent1"/></a:lnRef><a:fillRef idx="0"><a:schemeClr val="accent1"/></a:fillRef><a:effectRef idx="0"><a:schemeClr val="accent1"/></a:effectRef><a:fontRef idx="minor"><a:schemeClr val="tx1"/></a:fontRef></xdr:style></xdr:cxnSp>"#,
    );
    let got = shapes(&[
        sp("", FILLED),
        connector,
        // its own colours win over the style's
        sp(r#"<a:solidFill><a:srgbClr val="FF0000"/></a:solidFill><a:ln><a:noFill/></a:ln>"#, FILLED),
        // a reference to style 0 is none
        sp("", &FILLED.replace(r#"lnRef idx="2""#, r#"lnRef idx="0""#).replace(r#"fillRef idx="1""#, r#"fillRef idx="0""#)),
    ]);
    assert_eq!(got.len(), 4);

    let filled = &got[0];
    assert_eq!(filled.fill, Color::Theme(4, 0));
    // a 15% shade: the nearest tint
    assert_eq!(filled.line, Color::Theme(4, -578));
    assert!(!filled.format.no_line);
    assert_eq!(filled.format.text_color, Some(Color::Theme(0, 0)));

    let line = &got[1];
    assert_eq!(line.kind, ShapeKind::Arrow);
    assert_eq!(line.fill, Color::Auto);
    assert_eq!(line.line, Color::Theme(4, 0));

    let own = &got[2];
    assert_eq!(own.fill, Color::Rgb(0xFF0000));
    assert!(own.format.no_line);

    let none = &got[3];
    assert_eq!(none.fill, Color::Auto);
    assert!(none.format.no_line);
}

#[test]
fn colour_modifiers_are_read_as_tints() {
    let fill = |clr: &str| sp(&format!(r#"<a:solidFill>{clr}</a:solidFill>"#), "");
    let got = shapes(&[
        fill(r#"<a:schemeClr val="accent2"><a:lumMod val="60000"/><a:lumOff val="40000"/></a:schemeClr>"#),
        fill(r#"<a:schemeClr val="accent2"><a:lumMod val="75000"/></a:schemeClr>"#),
        fill(r#"<a:srgbClr val="808080"><a:lumMod val="50000"/></a:srgbClr>"#),
        fill(r#"<a:schemeClr val="tx1"/>"#),
        fill(r#"<a:prstClr val="white"/>"#),
    ]);
    assert_eq!(got[0].fill, Color::Theme(5, 400));
    assert_eq!(got[1].fill, Color::Theme(5, -250));
    assert_eq!(got[2].fill, Color::Rgb(0x404040));
    assert_eq!(got[3].fill, Color::Theme(1, 0));
    assert_eq!(got[4].fill, Color::Rgb(0xFFFFFF));
}

#[test]
fn see_through_and_gradient_fills_round_trip() {
    let no_style = r#"<xdr:style><a:lnRef idx="0"><a:scrgbClr r="0" g="0" b="0"/></a:lnRef><a:fillRef idx="0"><a:scrgbClr r="0" g="0" b="0"/></a:fillRef><a:effectRef idx="0"><a:scrgbClr r="0" g="0" b="0"/></a:effectRef><a:fontRef idx="minor"><a:schemeClr val="lt1"/></a:fontRef></xdr:style>"#;
    let got = shapes(&[
        // Excel's Semitransparent and Gradient Fill presets
        sp(r#"<a:solidFill><a:schemeClr val="accent1"><a:alpha val="50000"/></a:schemeClr></a:solidFill><a:ln><a:noFill/></a:ln>"#, no_style),
        sp(
            r#"<a:gradFill flip="none" rotWithShape="1"><a:gsLst><a:gs pos="0"><a:schemeClr val="accent2"><a:lumMod val="67000"/></a:schemeClr></a:gs><a:gs pos="48000"><a:schemeClr val="accent2"><a:lumMod val="97000"/><a:lumOff val="3000"/></a:schemeClr></a:gs><a:gs pos="100000"><a:schemeClr val="accent2"><a:lumMod val="60000"/><a:lumOff val="40000"/></a:schemeClr></a:gs></a:gsLst><a:lin ang="16200000" scaled="1"/><a:tileRect/></a:gradFill><a:ln><a:noFill/></a:ln>"#,
            no_style,
        ),
    ]);
    let semi = &got[0].format;
    assert_eq!((got[0].fill, semi.fill_transparency, semi.no_line), (Color::Theme(4, 0), 0.5, true));
    assert_eq!(semi.text_color, Some(Color::Theme(0, 0)));
    let g = &got[1].format;
    let stops: Vec<(f32, Color)> = g.gradient.iter().map(|s| (s.pos, s.color)).collect();
    assert_eq!(stops, vec![(0.0, Color::Theme(5, -330)), (0.48, Color::Theme(5, 30)), (1.0, Color::Theme(5, 400))]);
    assert_eq!(g.gradient_angle, 270.0);

    // written back: the alpha and the gradient's stops and angle
    let mut sheet = gridcraft_model::Sheet::new("Sheet1");
    sheet.shapes = got.clone();
    let wb = gridcraft_model::Workbook { sheets: vec![std::sync::Arc::new(sheet)], ..Default::default() };
    let (back, _) = read_xlsx(&crate::write_xlsx(&wb).unwrap()).unwrap();
    let back = &back.sheet(0).unwrap().shapes;
    assert_eq!(back[0].format.fill_transparency, 0.5);
    assert_eq!(back[1].format.gradient.len(), 3);
    assert_eq!(back[1].format.gradient_angle, 270.0);
    assert!(back[1].format.gradient.iter().zip(&g.gradient).all(|(a, b)| a.pos == b.pos));
}
