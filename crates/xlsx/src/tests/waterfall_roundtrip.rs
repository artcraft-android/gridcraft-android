//! A waterfall's totals, connector lines, gap, style and axis title survive a save, and its
//! points are left for the style part to colour.

use gridcraft_model::{Anchor, Chart, ChartElements, ChartKind, LegendPos, Series, Sheet, Workbook};
use std::sync::Arc;

use crate::{read_xlsx, write_xlsx};

#[test]
fn waterfall_totals_lines_gap_and_style_round_trip() {
    let mut wb = Workbook::default();
    let mut s = Sheet::new("Data");
    s.charts.push(Chart {
        depth: Default::default(),
        alt: String::new(),
        elements: ChartElements { style: 397, no_connectors: true, gap_width: Some(25), ..Default::default() },
        id: 1,
        kind: ChartKind::Waterfall,
        anchor: Anchor { cell: gridcraft_core::CellRef::new(0, 3), width: 480.0, height: 300.0, ..Anchor::default() },
        title: Some("Cash".into()),
        series: vec![Series {
            name: Some("Data!$B$1".into()),
            categories: Some("Data!$A$2:$A$7".into()),
            values: "Data!$B$2:$B$7".into(),
            totals: vec![5],
            ..Default::default()
        }],
        legend: LegendPos::Top,
        data_labels: true,
        gridlines: true,
        style: 2,
        x_title: Some("Axis Title".into()),
        y_title: None,
        source: None,
        by_rows: false,
        pivot: None,
    });
    wb.sheets = vec![Arc::new(s)];
    let bytes = write_xlsx(&wb).unwrap();
    let (back, _rep) = read_xlsx(&bytes).unwrap();
    let c = &back.sheets[0].charts[0];
    assert_eq!(c.series[0].totals, [5]);
    assert!(c.elements.no_connectors);
    assert_eq!((c.elements.gap_width, c.elements.style), (Some(25), 397));
    assert_eq!(c.x_title.as_deref(), Some("Axis Title"));
    assert_eq!(c.series[0].color, None, "its points take the palette's colours");
}

/// A treemap's label text and parent banners survive a save.
#[test]
fn treemap_labels_round_trip() {
    let mut wb = Workbook::default();
    let mut s = Sheet::new("Data");
    let elements = ChartElements {
        style: 413,
        parent_banner: true,
        label_text: gridcraft_model::LabelText::of(false, true, false, false),
        label_pos: gridcraft_model::LabelPos::InsideEnd,
        ..Default::default()
    };
    s.charts.push(Chart {
        depth: Default::default(),
        alt: String::new(),
        elements,
        id: 1,
        kind: ChartKind::Treemap,
        anchor: Anchor { cell: gridcraft_core::CellRef::new(0, 3), width: 480.0, height: 300.0, ..Anchor::default() },
        title: Some("T".into()),
        series: vec![Series {
            name: Some("Data!$B$1".into()),
            categories: Some("Data!$A$2:$A$7".into()),
            values: "Data!$B$2:$B$7".into(),
            ..Default::default()
        }],
        legend: LegendPos::Bottom,
        data_labels: true,
        gridlines: false,
        style: 2,
        x_title: None,
        y_title: None,
        source: None,
        by_rows: false,
        pivot: None,
    });
    wb.sheets = vec![Arc::new(s)];
    let (back, _rep) = read_xlsx(&write_xlsx(&wb).unwrap()).unwrap();
    let c = &back.sheets[0].charts[0];
    assert!(c.data_labels && c.elements.parent_banner);
    assert_eq!(c.elements.label_text, gridcraft_model::LabelText::of(false, true, false, false));
    assert_eq!((c.elements.style, c.elements.label_pos), (413, gridcraft_model::LabelPos::InsideEnd));
}

/// A box chart's means, gap and value tick marks, and a funnel's hidden axis and gap, survive a save.
#[test]
fn box_and_funnel_elements_round_trip() {
    let chart = |id: u32, kind: ChartKind, elements: ChartElements| Chart {
        depth: Default::default(),
        alt: String::new(),
        elements,
        id,
        kind,
        anchor: Anchor { cell: gridcraft_core::CellRef::new(id * 16, 3), width: 480.0, height: 300.0, ..Anchor::default() },
        title: Some("T".into()),
        series: vec![Series {
            name: Some("Data!$B$1".into()),
            categories: Some("Data!$A$2:$A$7".into()),
            values: "Data!$B$2:$B$7".into(),
            ..Default::default()
        }],
        legend: LegendPos::None,
        data_labels: true,
        gridlines: true,
        style: 2,
        x_title: None,
        y_title: None,
        source: None,
        by_rows: false,
        pivot: None,
    };
    let mut wb = Workbook::default();
    let mut s = Sheet::new("Data");
    let boxed = ChartElements {
        style: 409,
        box_mean_line: true,
        box_no_mean_marker: true,
        val_ticks: true,
        minor_val_gridlines: true,
        gap_width: Some(110),
        label_pos: gridcraft_model::LabelPos::Right,
        ..Default::default()
    };
    s.charts.push(chart(1, ChartKind::BoxWhisker, boxed));
    s.charts.push(chart(2, ChartKind::Funnel, ChartElements { style: 424, hide_cat_axis: true, gap_width: Some(15), ..Default::default() }));
    wb.sheets = vec![Arc::new(s)];
    let (back, _rep) = read_xlsx(&write_xlsx(&wb).unwrap()).unwrap();
    let (b, f) = (&back.sheets[0].charts[0].elements, &back.sheets[0].charts[1].elements);
    assert!(b.box_mean_line && b.box_no_mean_marker && b.val_ticks && b.minor_val_gridlines);
    assert_eq!((b.gap_width, b.style, b.label_pos), (Some(110), 409, gridcraft_model::LabelPos::Right));
    assert!(f.hide_cat_axis && f.no_cat_ticks);
    assert_eq!((f.gap_width, f.style), (Some(15), 424));
}
