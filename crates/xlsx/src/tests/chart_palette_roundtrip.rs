//! A palette picked on the chart Color page: written as the reference writes it (each series'
//! theme colour and its changes, a colour part) and read back.

use std::io::Read;
use std::sync::Arc;

use gridcraft_model::{ChartKind, Series};

use crate::{read_xlsx, write_xlsx};

#[test]
fn a_color_page_palette_is_written_as_the_reference_writes_it_and_read_back() {
    let mut wb = super::trendline_roundtrip::workbook();
    let sh = Arc::make_mut(&mut wb.sheets[0]);
    sh.charts.truncate(1);
    let c = &mut sh.charts[0];
    c.kind = ChartKind::ColumnClustered;
    c.series[0].trendlines.clear();
    c.series[0].color = None;
    c.series.push(Series { values: "Data!$A$2:$A$6".into(), ..Series::default() });
    c.elements.palette = 21;
    let bytes = write_xlsx(&wb).unwrap();
    let xml = &super::trendline_roundtrip::chart_parts(&bytes)[0];
    // Monochromatic Palette 8 on two series: light to dark, as the reference spreads two
    assert!(xml.contains("<a:solidFill><a:schemeClr val=\"accent1\"><a:tint val=\"77000\"/></a:schemeClr></a:solidFill>"), "{xml}");
    assert!(xml.contains("<a:solidFill><a:schemeClr val=\"accent1\"><a:shade val=\"76000\"/></a:schemeClr></a:solidFill>"), "{xml}");
    let mut z = zip::ZipArchive::new(std::io::Cursor::new(bytes.clone())).unwrap();
    let mut colors = String::new();
    z.by_name("xl/charts/colors1.xml").unwrap().read_to_string(&mut colors).unwrap();
    assert!(colors.contains("meth=\"withinLinearReversed\" id=\"21\"><a:schemeClr val=\"accent1\"/></cs:colorStyle>"), "{colors}");
    let (back, _) = read_xlsx(&bytes).unwrap();
    assert_eq!(back.sheets[0].charts[0].elements.palette, 21);
    // the colourful ones list their colours and the ways they vary them
    let two = crate::chart::colors_xml(11).unwrap();
    assert!(two.contains("<a:schemeClr val=\"accent1\"/><a:schemeClr val=\"accent3\"/><a:schemeClr val=\"accent5\"/><cs:variation/><cs:variation><a:lumMod val=\"60000\"/></cs:variation>"), "{two}");
    assert!(crate::chart::colors_xml(20).unwrap().contains("<a:schemeClr val=\"dk1\"/><cs:variation><a:tint val=\"88500\"/></cs:variation>"));
    assert!(crate::chart::colors_xml(0).is_none() && crate::chart::colors_xml(10).is_none());
}

#[test]
fn a_style_page_style_is_written_with_its_looks_and_read_back() {
    let mut wb = super::trendline_roundtrip::workbook();
    let sh = Arc::make_mut(&mut wb.sheets[0]);
    sh.charts.truncate(1);
    let c = &mut sh.charts[0];
    c.kind = ChartKind::ColumnClustered;
    c.series[0].trendlines.clear();
    c.series[0].color = None;
    c.data_labels = true;
    // Style 4: a shaded background, see-through columns edged in white, white bold labels
    c.elements.style = 205;
    let bytes = write_xlsx(&wb).unwrap();
    let xml = &super::trendline_roundtrip::chart_parts(&bytes)[0];
    assert!(xml.contains("<a:solidFill><a:schemeClr val=\"accent1\"><a:alpha val=\"85000\"/></a:schemeClr></a:solidFill>"), "{xml}");
    assert!(xml.contains("<a:path path=\"circle\"><a:fillToRect l=\"50000\" t=\"-80000\" r=\"50000\" b=\"180000\"/></a:path>"), "{xml}");
    assert!(xml.contains("<a:defRPr sz=\"900\" b=\"1\"><a:solidFill><a:schemeClr val=\"lt1\"/></a:solidFill></a:defRPr>"), "{xml}");
    assert!(xml.contains("</c:chart><c:spPr><a:gradFill"), "the chart area's fill follows the chart: {xml}");
    let mut z = zip::ZipArchive::new(std::io::Cursor::new(bytes.clone())).unwrap();
    let mut style = String::new();
    z.by_name("xl/charts/style1.xml").unwrap().read_to_string(&mut style).unwrap();
    assert!(style.contains("id=\"205\""), "{style}");
    let (back, _) = read_xlsx(&bytes).unwrap();
    assert_eq!(back.sheets[0].charts[0].elements.style, 205);
    // no style: no style part
    let sh = Arc::make_mut(&mut wb.sheets[0]);
    sh.charts[0].elements.style = 0;
    let bytes = write_xlsx(&wb).unwrap();
    let z = zip::ZipArchive::new(std::io::Cursor::new(bytes)).unwrap();
    assert!(z.file_names().all(|n| !n.contains("style1.xml")));
}

#[test]
fn a_styled_pie_writes_each_slice() {
    let mut wb = super::trendline_roundtrip::workbook();
    let sh = Arc::make_mut(&mut wb.sheets[0]);
    sh.charts.truncate(1);
    let c = &mut sh.charts[0];
    c.kind = ChartKind::Pie;
    c.series.truncate(1);
    c.series[0].trendlines.clear();
    c.series[0].color = None;
    // Pie Style 2: patterned slices edged in white with a shadow inside
    c.elements.style = 252;
    let bytes = write_xlsx(&wb).unwrap();
    let xml = &super::trendline_roundtrip::chart_parts(&bytes)[0];
    assert!(xml.contains("<c:dPt><c:idx val=\"0\"/><c:bubble3D val=\"0\"/><c:spPr><a:pattFill prst=\"ltUpDiag\">"), "{xml}");
    assert!(xml.contains("<a:ln w=\"19050\"><a:solidFill><a:schemeClr val=\"lt1\"/></a:solidFill></a:ln><a:effectLst><a:innerShdw"), "{xml}");
    let (back, _) = read_xlsx(&bytes).unwrap();
    assert_eq!(back.sheets[0].charts[0].elements.style, 252);
}
