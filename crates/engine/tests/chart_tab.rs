//! The phone's Chart tab: the Layout gallery's eleven layouts and the Elements switches.

use gridcraft_engine::Session;
use gridcraft_engine::model::{Chart, LabelPos, LabelText, LegendPos};
use serde_json::json;

/// A clustered column chart of two series over two categories.
fn session() -> Session {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1", "S2"], ["a", 1, 2], ["b", 3, 4]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:C3", "type": "column"})).unwrap();
    s
}

fn chart(s: &Session) -> Chart {
    s.doc().unwrap().wb.active().unwrap().charts[0].clone()
}

fn layout(s: &mut Session, n: u32) -> Chart {
    s.execute("chart.quickLayout", json!({"layout": n})).unwrap();
    chart(s)
}

#[test]
fn each_layout_sets_what_the_reference_saves() {
    let mut s = session();
    let c = layout(&mut s, 2);
    assert_eq!(c.legend, LegendPos::Top);
    assert!(c.data_labels && !c.gridlines && c.elements.hide_val_axis);
    assert_eq!((c.elements.gap_width, c.elements.overlap), (None, Some(-25)));
    let c = layout(&mut s, 3);
    assert!(!c.elements.hide_val_axis && c.gridlines && !c.data_labels);
    assert_eq!((c.elements.gap_width, c.elements.overlap), (Some(75), Some(-25)));
    let c = layout(&mut s, 4);
    assert_eq!(c.title.as_deref(), Some(""), "no title");
    assert_eq!((c.elements.gap_width, c.elements.overlap), (Some(75), None));
    let c = layout(&mut s, 5);
    assert!(c.elements.data_table && !c.elements.data_table_no_keys);
    assert_eq!((c.legend, c.x_title.is_some(), c.y_title.is_some()), (LegendPos::None, false, true));
    assert_eq!(c.title.as_deref(), Some("Chart Title"));
    let c = layout(&mut s, 6);
    assert!(c.data_labels && c.elements.last_point_labels && !c.elements.data_table);
    let c = layout(&mut s, 7);
    assert!(!c.data_labels && !c.elements.last_point_labels);
    assert!(c.elements.minor_val_gridlines && c.gridlines && !c.elements.data_table);
    assert_eq!((c.x_title.is_some(), c.y_title.is_some(), c.elements.gap_width), (true, true, Some(300)));
    let c = layout(&mut s, 8);
    assert_eq!((c.gridlines, c.elements.minor_val_gridlines, c.elements.gap_width), (false, false, Some(0)));
    let c = layout(&mut s, 10);
    assert_eq!((c.elements.label_pos, c.elements.gap_width, c.elements.overlap), (LabelPos::InsideEnd, Some(75), Some(40)));
    assert!(c.x_title.is_none() && c.y_title.is_none());
    let c = layout(&mut s, 1);
    assert_eq!((c.legend, c.data_labels, c.gridlines), (LegendPos::Right, false, true));
    assert_eq!((c.elements.gap_width, c.elements.overlap, c.elements.label_pos), (None, None, LabelPos::Auto));
    assert!(s.execute("chart.quickLayout", json!({"layout": 12})).is_err());
}

#[test]
fn a_layout_keeps_typed_titles() {
    let mut s = session();
    s.execute("chart.set", json!({"title": "Sales"})).unwrap();
    layout(&mut s, 9);
    s.execute("chart.set", json!({"yTitle": "Units"})).unwrap();
    let c = layout(&mut s, 6);
    assert_eq!((c.title.as_deref(), c.y_title.as_deref()), (Some("Sales"), Some("Units")));
}

#[test]
fn elements_turn_axes_gridlines_and_keys_on_and_off() {
    let mut s = session();
    let set = |s: &mut Session, p: serde_json::Value| s.execute("chart.addElement", p).unwrap();
    set(&mut s, json!({"element": "axis", "which": "category", "on": false}));
    set(&mut s, json!({"element": "gridlines", "which": "majorCategory"}));
    set(&mut s, json!({"element": "gridlines", "which": "minorValue"}));
    set(&mut s, json!({"element": "dataTable", "keys": false}));
    let e = chart(&s).elements;
    assert!(e.hide_cat_axis && !e.hide_val_axis && e.cat_gridlines && e.minor_val_gridlines && !e.minor_cat_gridlines);
    assert!(e.data_table && e.data_table_no_keys);
    set(&mut s, json!({"element": "axisTitles", "which": "value"}));
    let c = chart(&s);
    assert!(c.x_title.is_none() && c.y_title.is_some());
    set(&mut s, json!({"element": "dataTable", "on": false}));
    set(&mut s, json!({"element": "axis", "which": "category"}));
    let e = chart(&s).elements;
    assert!(!e.data_table && !e.data_table_no_keys && !e.hide_cat_axis);
    assert!(s.execute("chart.addElement", json!({"element": "gridlines", "which": "sideways"})).is_err());
}

#[test]
fn a_pie_has_its_own_seven_layouts() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1"], ["a", 4], ["b", 3]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:B3", "type": "pie", "title": "Pi"})).unwrap();
    let c = layout(&mut s, 1);
    assert_eq!((c.legend, c.data_labels, c.title.as_deref()), (LegendPos::None, true, Some("Pi")));
    assert_eq!(c.elements.label_text, LabelText::of(false, true, false, true), "the category and its share");
    let c = layout(&mut s, 3);
    assert_eq!((c.legend, c.data_labels, c.title.as_deref()), (LegendPos::Bottom, false, Some("")));
    let c = layout(&mut s, 4);
    assert_eq!(c.elements.label_text, LabelText::of(true, true, false, false));
    let c = layout(&mut s, 6);
    assert_eq!((c.legend, c.elements.label_text), (LegendPos::Right, LabelText::of(false, false, false, true)));
    assert!(s.execute("chart.quickLayout", json!({"layout": 8})).is_err(), "seven, not eleven");
}

#[test]
fn a_line_chart_has_twelve_layouts_with_its_lines() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1", "S2"], ["a", 1, 2], ["b", 3, 4]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:C3", "type": "line"})).unwrap();
    let c = layout(&mut s, 1);
    assert!(c.y_title.is_some() && c.x_title.is_none() && c.gridlines, "Layout 1 titles the value axis");
    let c = layout(&mut s, 7);
    assert!(c.elements.drop_lines && !c.elements.high_low_lines && c.x_title.is_some());
    let c = layout(&mut s, 10);
    assert!(!c.elements.drop_lines && c.elements.high_low_lines);
    let c = layout(&mut s, 6);
    assert!(c.elements.last_point_labels && c.elements.label_pos == LabelPos::Right && !c.elements.high_low_lines);
    let c = layout(&mut s, 11);
    assert!(c.elements.hide_cat_axis && c.elements.hide_val_axis && !c.data_labels && c.legend == LegendPos::None);
    let c = layout(&mut s, 8);
    assert_eq!((c.elements.label_pos, c.elements.hide_val_axis, c.elements.hide_cat_axis), (LabelPos::Center, true, false));
    assert!(s.execute("chart.quickLayout", json!({"layout": 12})).is_ok());
    assert!(s.execute("chart.quickLayout", json!({"layout": 13})).is_err());
}

#[test]
fn a_style_sets_its_look_and_its_elements() {
    let mut s = session();
    let style = |s: &mut Session, n: u32| {
        s.execute("chart.style", json!({"style": n})).unwrap();
        chart(s)
    };
    let c = style(&mut s, 2);
    assert_eq!((c.elements.style, c.legend, c.data_labels), (202, LegendPos::Top, true));
    assert!(c.elements.hide_val_axis && c.elements.cat_gridlines && !c.gridlines);
    assert_eq!((c.elements.label_pos, c.elements.gap_width, c.elements.overlap), (LabelPos::OutsideEnd, Some(444), Some(-90)));
    // Style 1 brings the axis and gridlines back and leaves the labels
    let c = style(&mut s, 1);
    assert_eq!((c.elements.style, c.legend, c.data_labels), (201, LegendPos::Bottom, true));
    assert!(!c.elements.hide_val_axis && !c.elements.cat_gridlines && c.gridlines);
    let c = style(&mut s, 11);
    assert!(c.elements.minor_val_gridlines && c.elements.overlap.is_none());
    assert!(s.execute("chart.style", json!({"style": 15})).is_err());
    assert!(s.execute("chart.style", json!({"style": 0})).is_err());
}

#[test]
fn a_line_chart_has_its_own_thirteen_styles() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1", "S2"], ["a", 1, 2], ["b", 3, 4]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:C3", "type": "line"})).unwrap();
    s.execute("chart.style", json!({"style": 3})).unwrap();
    let c = chart(&s);
    assert_eq!((c.elements.style, c.elements.drop_lines, c.gridlines), (230, true, false));
    s.execute("chart.style", json!({"style": 2})).unwrap();
    let c = chart(&s);
    assert_eq!((c.elements.style, c.data_labels, c.elements.label_pos, c.elements.hide_val_axis), (228, true, LabelPos::Center, true));
    assert!(!c.elements.drop_lines, "Style 2 has none");
    s.execute("chart.style", json!({"style": 5})).unwrap();
    let c = chart(&s);
    assert!(c.elements.cat_gridlines && c.elements.minor_cat_gridlines && c.gridlines);
    assert!(s.execute("chart.style", json!({"style": 13})).is_ok());
    assert!(s.execute("chart.style", json!({"style": 14})).is_err(), "thirteen, not fourteen");
}

#[test]
fn a_pie_has_its_own_twelve_styles_and_their_labels() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1"], ["a", 4], ["b", 3]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:B3", "type": "pie"})).unwrap();
    s.execute("chart.style", json!({"style": 1})).unwrap();
    assert!(!chart(&s).data_labels, "Style 1 brings no labels");
    s.execute("chart.style", json!({"style": 9})).unwrap();
    let c = chart(&s);
    assert_eq!((c.elements.style, c.legend, c.data_labels), (259, LegendPos::None, true));
    assert_eq!((c.elements.label_text, c.elements.label_pos), (LabelText::of(false, true, false, true), LabelPos::OutsideEnd));
    assert!(s.execute("chart.style", json!({"style": 12})).is_ok());
    assert!(s.execute("chart.style", json!({"style": 13})).is_err(), "twelve");
}

#[test]
fn a_bar_chart_has_ten_layouts() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1", "S2"], ["a", 1, 2], ["b", 3, 4]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:C3", "type": "bar"})).unwrap();
    let c = layout(&mut s, 5);
    assert!(c.elements.data_table && c.y_title.is_none(), "a data table, no value axis title");
    let c = layout(&mut s, 8);
    assert!(c.x_title.is_some() && c.y_title.is_some() && c.gridlines && c.elements.gap_width.is_none(), "the column page's ninth");
    let c = layout(&mut s, 9);
    assert_eq!((c.elements.label_pos, c.elements.overlap), (LabelPos::InsideEnd, Some(40)));
    assert!(s.execute("chart.quickLayout", json!({"layout": 11})).is_err());
}

#[test]
fn a_bar_chart_has_its_own_twelve_styles() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1", "S2"], ["a", 1, 2], ["b", 3, 4]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:C3", "type": "bar"})).unwrap();
    s.execute("chart.quickLayout", json!({"layout": 10})).unwrap();
    assert_eq!(chart(&s).title.as_deref(), Some(""), "no title");
    s.execute("chart.style", json!({"style": 7})).unwrap();
    let c = chart(&s);
    assert_eq!((c.elements.style, c.title.as_deref()), (222, Some("Chart Title")), "Style 7 brings the title in");
    s.execute("chart.style", json!({"style": 3})).unwrap();
    let c = chart(&s);
    assert_eq!((c.data_labels, c.elements.label_pos, c.elements.hide_val_axis), (true, LabelPos::InsideEnd, false));
    assert!(s.execute("chart.style", json!({"style": 13})).is_err(), "twelve");
}

#[test]
fn an_area_chart_has_eight_layouts_and_ten_styles() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1", "S2"], ["a", 1, 2], ["b", 3, 4]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:C3", "type": "area"})).unwrap();
    let c = layout(&mut s, 7);
    assert!(c.elements.drop_lines && c.legend == LegendPos::Right);
    let c = layout(&mut s, 6);
    assert!(c.elements.minor_val_gridlines && c.x_title.is_some() && c.y_title.is_some() && !c.elements.drop_lines);
    assert!(s.execute("chart.quickLayout", json!({"layout": 9})).is_err());
    s.execute("chart.style", json!({"style": 2})).unwrap();
    assert!(chart(&s).elements.drop_lines, "Style 2 brings drop lines");
    s.execute("chart.style", json!({"style": 1})).unwrap();
    assert!(!chart(&s).elements.drop_lines, "Style 1 takes them away");
    s.execute("chart.style", json!({"style": 9})).unwrap();
    assert!(chart(&s).data_labels);
    assert!(s.execute("chart.style", json!({"style": 11})).is_err());
}

#[test]
fn a_scatter_chart_has_eleven_layouts_some_with_trendlines() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["X", "Y"], [1, 2], [2, 3], [3, 5]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:B4", "type": "scatter"})).unwrap();
    let c = layout(&mut s, 9);
    let t = &c.series[0].trendlines;
    assert!(t.len() == 1 && t[0].display_equation && t[0].display_r_squared);
    assert!(c.elements.minor_val_gridlines && c.x_title.is_some());
    let c = layout(&mut s, 3);
    assert!(c.series[0].trendlines.len() == 1 && !c.series[0].trendlines[0].display_equation);
    assert!(c.elements.cat_gridlines && c.elements.minor_cat_gridlines, "X gridlines too");
    let c = layout(&mut s, 2);
    assert!(c.series[0].trendlines.is_empty() && c.elements.hide_val_axis && c.data_labels);
    assert_eq!(c.elements.label_text, LabelText::of(true, true, false, false), "X and Y");
    assert!(s.execute("chart.quickLayout", json!({"layout": 12})).is_err());
}

#[test]
fn a_doughnut_has_the_pie_layouts_and_ten_styles_with_their_holes() {
    let mut s = Session::new();
    s.new_workbook();
    s.execute("range.setValues", json!({"range": "A1", "values": [["", "S1"], ["a", 4], ["b", 3]]})).unwrap();
    s.execute("insert.chart", json!({"range": "A1:B3", "type": "doughnut"})).unwrap();
    assert_eq!(layout(&mut s, 2).legend, LegendPos::Top);
    s.execute("chart.style", json!({"style": 1})).unwrap();
    assert_eq!(chart(&s).elements.hole_size, Some(75));
    s.execute("chart.style", json!({"style": 9})).unwrap();
    let c = chart(&s);
    assert_eq!((c.elements.style, c.elements.hole_size, c.legend), (260, Some(50), LegendPos::None));
    assert!(s.execute("chart.style", json!({"style": 11})).is_err(), "ten");
}
