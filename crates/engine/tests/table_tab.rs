//! The phone's Table tab: rows and columns put in and taken out across the table only, and the
//! table growing to take in a row or column added at its edge.

use gridcraft_core::{CellRef, RangeRef};
use gridcraft_engine::Session;
use serde_json::json;

/// A table A1:C4 (a header and three rows) with a note beside it in E2 that must stay put.
fn session() -> Session {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["Item", "Q1", "Q2"], ["a", 1, 2], ["b", 3, 4], ["c", 5, 6]]})).unwrap();
    s.execute("cell.set", json!({"cell": "E2", "input": "beside"})).unwrap();
    s.execute("insert.table", json!({"range": "A1:C4", "header": true})).unwrap();
    s
}

fn at(s: &mut Session, cell: &str) {
    s.execute("selection.set", json!({"range": cell})).unwrap();
}

fn table(s: &Session) -> gridcraft_engine::model::Table {
    s.doc().unwrap().wb.active().unwrap().tables[0].clone()
}

fn text(s: &Session, cell: &str) -> String {
    s.doc().unwrap().wb.active().unwrap().value(CellRef::parse(cell).unwrap()).display()
}

#[test]
fn rows_go_in_across_the_table_only() {
    let mut s = session();
    at(&mut s, "B3");
    s.execute("table.insert", json!({"where": "above"})).unwrap();
    assert_eq!(table(&s).range, RangeRef::parse("A1:C5").unwrap());
    assert_eq!(text(&s, "A4"), "b");
    assert_eq!(text(&s, "E2"), "beside", "cells beside the table stay put");
    // under the last row, the new row joins the table
    at(&mut s, "B5");
    s.execute("table.insert", json!({"where": "below"})).unwrap();
    assert_eq!(table(&s).range, RangeRef::parse("A1:C6").unwrap());
    // never above the header
    at(&mut s, "B1");
    assert!(s.execute("table.insert", json!({"where": "above"})).is_err());
}

#[test]
fn columns_go_in_at_either_edge_and_join_the_table() {
    let mut s = session();
    at(&mut s, "A2");
    s.execute("table.insert", json!({"where": "left"})).unwrap();
    let t = table(&s);
    assert_eq!(t.range, RangeRef::parse("A1:D4").unwrap());
    assert_eq!(t.columns.len(), 4);
    assert_eq!(text(&s, "B1"), "Item");
    assert!(!text(&s, "A1").is_empty(), "the new column has a header");
    at(&mut s, "D2");
    s.execute("table.insert", json!({"where": "right"})).unwrap();
    let t = table(&s);
    assert_eq!(t.range, RangeRef::parse("A1:E4").unwrap());
    assert_eq!(t.columns.len(), 5);
    assert_eq!(text(&s, "G2"), "beside", "the note moves along its row with the cells");
}

#[test]
fn rows_and_columns_come_out_of_the_table_only() {
    let mut s = session();
    at(&mut s, "B3");
    s.execute("table.deleteRows", json!({})).unwrap();
    assert_eq!(table(&s).range, RangeRef::parse("A1:C3").unwrap());
    assert_eq!(text(&s, "A3"), "c");
    assert_eq!(text(&s, "E2"), "beside");
    at(&mut s, "B1");
    assert!(s.execute("table.deleteRows", json!({})).is_err(), "the header row stays");
    at(&mut s, "C2");
    s.execute("table.deleteColumns", json!({})).unwrap();
    let t = table(&s);
    assert_eq!(t.range, RangeRef::parse("A1:B3").unwrap());
    assert_eq!(t.columns.len(), 2);
    // the total row goes as Total Row turns off
    at(&mut s, "A2");
    s.execute("table.totalRow", json!({"on": true})).unwrap();
    at(&mut s, "B4");
    s.execute("table.deleteRows", json!({})).unwrap();
    assert!(!table(&s).totals_row);
    // every column: the table goes
    s.execute("selection.set", json!({"range": "A2:B2"})).unwrap();
    s.execute("table.deleteColumns", json!({})).unwrap();
    assert!(s.doc().unwrap().wb.active().unwrap().tables.is_empty());
}

#[test]
fn a_palette_recolours_every_series() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1", "S2"], ["a", 1, 2], ["b", 3, 4]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:C3", "type": "column"})).unwrap();
    s.execute("chart.changeColors", json!({"palette": "colorful2", "colors": ["#FF0000"]})).unwrap();
    let ch = |s: &Session| s.doc().unwrap().wb.active().unwrap().charts[0].clone();
    assert!(ch(&s).series.iter().all(|se| se.color.is_some()));
    s.execute("chart.changeColors", json!({"palette": "monochromatic8"})).unwrap();
    assert_eq!(ch(&s).elements.palette, 21);
    assert!(ch(&s).series.iter().all(|se| se.color.is_none()));
    s.execute("chart.changeColors", json!({"palette": "colorful1"})).unwrap();
    assert_eq!(ch(&s).elements.palette, 0, "Colorful 1 is the automatic palette");
    assert!(s.execute("chart.changeColors", json!({"palette": "monochromatic14"})).is_err());
}

#[test]
fn the_header_row_goes_and_comes_back() {
    let mut s = session();
    at(&mut s, "B2");
    s.execute("table.headerRow", json!({"on": false})).unwrap();
    let t = table(&s);
    assert!(!t.header_row);
    assert_eq!(t.range, RangeRef::parse("A2:C4").unwrap());
    assert_eq!(text(&s, "A1"), "", "the header cells are emptied");
    s.execute("table.headerRow", json!({"on": true})).unwrap();
    assert_eq!(table(&s).range, RangeRef::parse("A1:C4").unwrap());
    assert_eq!(text(&s, "A1"), "Item");
    // with something in the row above, the table makes room for its header
    s.execute("table.headerRow", json!({"on": false})).unwrap();
    s.execute("cell.set", json!({"cell": "B1", "input": "kept"})).unwrap();
    at(&mut s, "B2");
    s.execute("table.headerRow", json!({"on": true})).unwrap();
    assert_eq!(table(&s).range, RangeRef::parse("A2:C5").unwrap());
    assert_eq!((text(&s, "B1"), text(&s, "A2"), text(&s, "A3")), ("kept".into(), "Item".into(), "a".into()));
}

#[test]
fn no_style_leaves_the_cells_as_they_are() {
    let mut s = session();
    at(&mut s, "B2");
    s.execute("table.style", json!({"style": ""})).unwrap();
    let d = s.doc().unwrap();
    let sh = d.wb.active().unwrap();
    let st = gridcraft_engine::tables::table_cell_style(&d.wb, 0, &sh.tables[0], CellRef::parse("B1").unwrap());
    assert!(!st.font.bold);
    assert_eq!(st.fill.pattern, gridcraft_engine::model::PatternType::None);
}

#[test]
fn a_table_keeps_its_alt_text_through_the_file() {
    let mut s = session();
    at(&mut s, "B2");
    s.execute("table.altText", json!({"text": "Quarterly sales"})).unwrap();
    assert_eq!(table(&s).alt_text, "Quarterly sales");
    let bytes = gridcraft_xlsx::write_xlsx(&s.doc().unwrap().wb).unwrap();
    let (back, _) = gridcraft_xlsx::read_xlsx(&bytes).unwrap();
    assert_eq!(back.sheets[0].tables[0].alt_text, "Quarterly sales");
}

/// One style of each family as the reference draws it: fills, text and lines in theme colours.
#[test]
fn table_styles_look_as_the_reference_draws_them() {
    use gridcraft_engine::model::{BorderStyle, Color, PatternType};
    let mut s = session();
    at(&mut s, "B2");
    s.execute("table.totalRow", json!({"on": true})).unwrap();
    s.execute("table.firstColumn", json!({"on": true})).unwrap();
    let look = |s: &Session, cell: &str| {
        let d = s.doc().unwrap();
        let sh = d.wb.active().unwrap();
        gridcraft_engine::tables::table_cell_style(&d.wb, 0, &sh.tables[0], CellRef::parse(cell).unwrap())
    };
    let fill = |st: &gridcraft_engine::model::Style| (st.fill.pattern != PatternType::None).then_some(st.fill.fg);
    let style = |s: &mut Session, name: &str| s.execute("table.style", json!({"style": name})).unwrap();
    // Medium 2: a filled header, bands 80% lighter, a double line over the total row
    style(&mut s, "TableStyleMedium2");
    assert_eq!(fill(&look(&s, "B1")), Some(Color::Theme(4, 0)));
    assert_eq!(look(&s, "B1").font.color, Color::Theme(0, 0));
    assert_eq!(fill(&look(&s, "B2")), Some(Color::Theme(4, 800)));
    assert_eq!(fill(&look(&s, "B3")), None);
    assert_eq!((look(&s, "B5").borders.top.style, look(&s, "B5").borders.top.color), (BorderStyle::Double, Color::Theme(4, 0)));
    assert_eq!(look(&s, "B4").borders.bottom.style, BorderStyle::Double, "the total row's line wins the shared edge");
    // Light 15: a grid with a heavier line under the header
    style(&mut s, "TableStyleLight15");
    assert_eq!(look(&s, "B1").borders.bottom.style, BorderStyle::Medium);
    assert_eq!(look(&s, "B3").borders.right.style, BorderStyle::Thin);
    // Medium 9: solid first column and total row, thick white lines
    style(&mut s, "TableStyleMedium9");
    assert_eq!(fill(&look(&s, "A3")), Some(Color::Theme(4, 0)));
    assert_eq!(fill(&look(&s, "B5")), Some(Color::Theme(4, 0)));
    assert_eq!((look(&s, "B1").borders.bottom.style, look(&s, "B1").borders.bottom.color), (BorderStyle::Thick, Color::Theme(0, 0)));
    // Dark 1: greys, white text, a black header
    style(&mut s, "TableStyleDark1");
    assert_eq!(fill(&look(&s, "B1")), Some(Color::Theme(1, 0)));
    assert_eq!(fill(&look(&s, "B3")), Some(Color::Theme(1, 450)));
    assert_eq!(fill(&look(&s, "B5")), Some(Color::Theme(1, 150)));
    assert_eq!(look(&s, "B3").font.color, Color::Theme(0, 0));
    // Dark 9: an orange header over light blue bands
    style(&mut s, "TableStyleDark9");
    assert_eq!(fill(&look(&s, "B1")), Some(Color::Theme(5, 0)));
    assert_eq!(fill(&look(&s, "B2")), Some(Color::Theme(4, 600)));
}
