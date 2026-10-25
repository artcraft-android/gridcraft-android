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
    // Monochromatic Palette 8 on two series: light to dark
    assert!(xml.contains("<a:solidFill><a:schemeClr val=\"accent1\"><a:tint val=\"50000\"/></a:schemeClr></a:solidFill>"), "{xml}");
    assert!(xml.contains("<a:solidFill><a:schemeClr val=\"accent1\"><a:shade val=\"50000\"/></a:schemeClr></a:solidFill>"), "{xml}");
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
