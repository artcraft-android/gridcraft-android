use super::*;

fn cell(v: &Viewport, row: u32, col: u32) -> Option<&CellView> {
    v.cells.iter().find(|c| c.row == row && c.col == col)
}

#[test]
fn a_new_session_has_a_blank_workbook() {
    let m = MobileSession::new();
    let i = m.info();
    assert_eq!(i.sheets.len(), 1);
    assert_eq!(i.sheets[0].name, "Sheet1");
    assert!(!i.dirty && !i.can_undo);
    let v = m.sheet_view();
    assert_eq!((v.used_rows, v.used_cols, v.frozen_rows), (0, 0, 0));
    assert_eq!(m.selection().name_box, "A1");
}

#[test]
fn object_visibility_reaches_the_android_snapshot_and_undo_restores_it() {
    let m = MobileSession::new();
    m.execute("insert.shape".into(), json!({"kind": "rectangle"}).to_string()).unwrap();
    assert!(!m.objects().is_empty());
    m.execute("view.showObjects".into(), json!({"on": false}).to_string()).unwrap();
    assert!(!m.sheet_view().show_objects);
    assert!(m.objects().is_empty());
    m.execute("edit.undo".into(), "{}".into()).unwrap();
    assert!(m.sheet_view().show_objects);
    assert!(!m.objects().is_empty());
}

#[test]
fn outline_settings_move_summary_controls_without_losing_the_groups() {
    let m = MobileSession::new();
    m.execute("data.group".into(), json!({"rows":"2:5"}).to_string()).unwrap();
    m.execute("data.group".into(), json!({"cols":"B:C"}).to_string()).unwrap();
    m.execute("data.outlineSettings".into(), json!({"summaryBelow":false,"summaryRight":false}).to_string()).unwrap();
    let view = m.sheet_view();
    assert!(!view.summary_below && !view.summary_right);
    assert_eq!(view.row_groups[0].summary, Some(0));
    assert_eq!(view.col_groups[0].summary, Some(0));
    m.execute("view.showOutlineSymbols".into(), json!({"on":false}).to_string()).unwrap();
    assert!(!m.sheet_view().show_outline_symbols);
    assert!(!m.sheet_view().row_groups.is_empty());
}

#[test]
fn numeric_text_can_be_converted_and_undone_through_the_android_api() {
    let m = MobileSession::new();
    m.commit("'123".into(), "none".into()).unwrap();
    assert!(!cell(&m.viewport(0, 0, 0, 0, 0.0), 0, 0).unwrap().numeric);
    m.execute("formulas.convertToNumber".into(), "{}".into()).unwrap();
    assert!(cell(&m.viewport(0, 0, 0, 0, 0.0), 0, 0).unwrap().numeric);
    m.execute("edit.undo".into(), "{}".into()).unwrap();
    assert!(!cell(&m.viewport(0, 0, 0, 0, 0.0), 0, 0).unwrap().numeric);
}

#[test]
fn defined_names_can_be_listed_and_pasted_as_a_two_column_text_table() {
    let m = MobileSession::new();
    m.execute("formulas.defineName".into(), json!({"name":"Sales","refersTo":"=Sheet1!$B$2:$B$4"}).to_string()).unwrap();
    let names: Json = serde_json::from_str(&m.execute("formulas.pasteName".into(), "{}".into()).unwrap()).unwrap();
    assert_eq!(names["names"][0]["name"], "Sales");
    assert_eq!(m.sheet_view().used_rows, 0, "opening the picker changes no cells");
    m.execute("formulas.pasteName".into(), json!({"list":true}).to_string()).unwrap();
    let view = m.viewport(0, 0, 0, 1, 0.0);
    assert_eq!(cell(&view, 0, 0).unwrap().text, "Sales");
    assert_eq!(cell(&view, 0, 1).unwrap().text, "=Sheet1!$B$2:$B$4");
    assert!(!cell(&view, 0, 1).unwrap().numeric);
}

#[test]
fn pivot_details_enablement_and_source_records_are_available_on_android() {
    let m = MobileSession::new();
    m.execute("range.setValues".into(), json!({"range":"A1","values":[["Region","Sales"],["East",10],["East",20],["West",5]]}).to_string()).unwrap();
    m.execute("insert.pivotTable".into(), json!({"source":"Sheet1!A1:B4","rows":["Region"],"values":["Sales"]}).to_string()).unwrap();
    let viewport = m.viewport(0, 0, 20, 5, 0.0);
    let value = viewport.cells.iter().find(|c| c.numeric).unwrap();
    m.select(value.row, value.col, false).unwrap();
    assert!(m.pivot_info().unwrap().enable_show_details);
    assert!(m.pivot_can_show_details());
    m.execute("pivot.options".into(), json!({"enableShowDetails":false}).to_string()).unwrap();
    assert!(!m.pivot_info().unwrap().enable_show_details);
    assert!(!m.pivot_can_show_details());
    m.execute("pivot.options".into(), json!({"enableShowDetails":true}).to_string()).unwrap();
    let before = m.info().sheets.len();
    let details: Json = serde_json::from_str(&m.execute("pivot.showDetails".into(), "{}".into()).unwrap()).unwrap();
    assert!(details["records"].as_u64().unwrap() > 0);
    assert_eq!(m.info().sheets.len(), before + 1);
}

#[test]
fn typed_cells_show_in_the_viewport_with_their_look() {
    let m = MobileSession::new();
    m.commit("Name".into(), "down".into()).unwrap();
    let s = m.commit("1234.5".into(), "right".into()).unwrap();
    assert_eq!((s.active_row, s.active_col), (1, 1), "down after the first, right after the second");
    m.commit("=A2*2".into(), "none".into()).unwrap();
    m.execute("home.bold".into(), r#"{"range": "A1"}"#.into()).unwrap();
    let v = m.viewport(0, 0, 20, 10, 0.0);
    assert_eq!(v.rows.first().map(|l| (l.index, l.start)), Some((0, 0.0)));
    assert_eq!(v.cols.len(), 11);
    let a1 = cell(&v, 0, 0).unwrap();
    assert_eq!((a1.text.as_str(), a1.bold, a1.numeric, a1.auto_color), ("Name", true, false, true));
    let a2 = cell(&v, 1, 0).unwrap();
    assert_eq!((a2.text.as_str(), a2.numeric, a2.across), ("1234.5", true, Across::General));
    assert_eq!(cell(&v, 1, 1).map(|c| c.text.as_str()), Some("2469"));
    assert!(cell(&v, 5, 5).is_none(), "empty cells are left out");
    let i = m.info();
    assert!(i.dirty && i.can_undo && i.revision > 1);
    // the formula bar shows the formula of the active cell
    assert_eq!(m.selection().formula, "=A2*2");
}

#[test]
fn a_general_number_narrows_to_its_column() {
    let m = MobileSession::new();
    m.commit("3.14159265358979".into(), "none".into()).unwrap();
    let wide = m.viewport(0, 0, 0, 0, 7.0);
    let narrow = m.viewport(0, 0, 0, 0, 20.0);
    let (w, n) = (cell(&wide, 0, 0).unwrap().text.clone(), cell(&narrow, 0, 0).unwrap().text.clone());
    assert!(w.len() > n.len(), "{w} vs {n}");
}

#[test]
fn a_sample_workbook_has_fills_borders_and_sheets() {
    let m = MobileSession::new();
    let i = m.new_workbook("sales".into()).unwrap();
    assert!(!i.sheets.is_empty());
    assert_eq!(m.info().document_id, i.document_id);
    let v = m.sheet_view();
    assert!(v.used_rows > 3 && v.used_cols > 2);
    let vp = m.viewport(0, 0, v.used_rows, v.used_cols, 0.0);
    assert!(vp.cells.iter().any(|c| c.fill.is_some()), "a sample has filled cells");
    assert!(vp.cells.iter().any(|c| !c.text.is_empty()));
    // one workbook at a time, and a new blank one is Book1
    m.new_workbook(String::new()).unwrap();
    assert_eq!(m.info().sheets.len(), 1);
    assert_eq!(m.info().title, "Book1");
    assert!(m.new_workbook("no-such-sample".into()).is_err());
    assert_eq!(m.info().sheets.len(), 1, "a failed new leaves a workbook open");
}

#[test]
fn selecting_extends_from_the_anchor_and_headers_select_lines() {
    let m = MobileSession::new();
    m.select(2, 1, false).unwrap();
    let s = m.select(4, 3, true).unwrap();
    assert_eq!(s.ranges, vec![CellRange { first_row: 2, first_col: 1, last_row: 4, last_col: 3 }]);
    assert_eq!((s.active_row, s.active_col), (2, 1));
    let col = m.select_range(CellRange { first_row: 0, first_col: 2, last_row: MAX_ROWS - 1, last_col: 2 }).unwrap();
    assert_eq!(col.ranges[0].last_row, MAX_ROWS - 1);
    // Enter inside a selected block goes to its next cell
    m.select(0, 0, false).unwrap();
    m.select(1, 1, true).unwrap();
    let s = m.commit("x".into(), "down".into()).unwrap();
    assert_eq!((s.active_row, s.active_col), (1, 0));
}

#[test]
fn sheets_are_switched_and_hidden_ones_marked() {
    let m = MobileSession::new();
    m.execute("home.insertSheet".into(), String::new()).unwrap();
    let i = m.info();
    assert_eq!(i.sheets.len(), 2);
    let first = i.sheets.iter().position(|t| t.name == "Sheet1").unwrap();
    let i = m.activate_sheet(first as u32).unwrap();
    assert_eq!(i.active_sheet as usize, first);
    assert!(m.activate_sheet(9).is_err());
}

#[test]
fn geometry_follows_custom_sizes_and_hidden_lines() {
    let m = MobileSession::new();
    let v = m.sheet_view();
    assert_eq!(m.col_left(3), f64::from(v.default_col_width) * 3.0);
    assert_eq!(m.cell_at(f64::from(v.default_col_width) * 2.5, f64::from(v.default_row_height) * 4.5), CellAt { row: 4, col: 2 });
    m.execute("selection.set".into(), r#"{"range": "B1"}"#.into()).unwrap();
    m.execute("home.hideColumns".into(), String::new()).ok();
    let vp = m.viewport(0, 0, 2, 4, 0.0);
    if m.info().can_undo {
        assert!(vp.cols.iter().all(|c| c.index != 1), "a hidden column is left out");
    }
}

#[test]
fn saving_and_opening_round_trips_and_bad_files_are_errors() {
    let dir = std::env::temp_dir().join(format!("gridcraft-android-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("t.xlsx").to_string_lossy().into_owned();
    let m = MobileSession::new();
    m.commit("kept".into(), "none".into()).unwrap();
    let i = m.save(Some(path.clone())).unwrap();
    assert!(!i.dirty && i.path.is_some());
    let m2 = MobileSession::new();
    m2.open(path.clone(), None).unwrap();
    assert_eq!(m2.info().sheets.len(), 1);
    assert_eq!(cell(&m2.viewport(0, 0, 0, 0, 0.0), 0, 0).map(|c| c.text.as_str()), Some("kept"));
    let junk = dir.join("junk.xlsx");
    // a zip signature with nothing after it: a broken workbook, not text
    std::fs::write(&junk, [b"PK".as_slice(), &[3, 4], b"broken"].concat()).unwrap();
    assert!(matches!(m2.open(junk.to_string_lossy().into_owned(), None), Err(MobileError::Open(_))));
    assert!(m2.open(dir.join("missing.xlsx").to_string_lossy().into_owned(), None).is_err());
    assert_eq!(cell(&m2.viewport(0, 0, 0, 0, 0.0), 0, 0).map(|c| c.text.as_str()), Some("kept"), "a failed open keeps the workbook");
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn hostile_calls_never_panic() {
    let m = MobileSession::new();
    let vp = m.viewport(0, 0, u32::MAX, u32::MAX, f32::NAN);
    assert!((vp.rows.len() * vp.cols.len()) as u64 <= MAX_VIEW_CELLS);
    let vp = m.viewport(u32::MAX, u32::MAX, 0, 0, -1.0);
    assert!(vp.rows.len() <= 1);
    assert_eq!(m.cell_at(f64::NAN, f64::INFINITY), CellAt { row: 0, col: 0 });
    assert!(m.row_top(u32::MAX).is_finite());
    assert!(m.select(u32::MAX, u32::MAX, true).is_ok());
    assert!(m.execute("no.such".into(), String::new()).is_err());
    assert!(m.execute("cell.set".into(), "{not json".into()).is_err());
    assert!(m.commit("=1/0".into(), "sideways".into()).is_ok());
    assert!(m.disabled_reason("no.such".into()).is_some());
    assert!(m.disabled_reason("cell.set".into()).is_none());
}

#[test]
fn the_active_cells_format_and_the_colour_grid() {
    let m = MobileSession::new();
    m.commit("12%".into(), "none".into()).unwrap();
    m.execute("home.bold".into(), String::new()).unwrap();
    m.execute("home.alignCenter".into(), String::new()).unwrap();
    let f = m.cell_format();
    assert!(f.bold && !f.italic);
    assert_eq!((f.across, f.number_kind.as_str()), (Across::Center, "Percentage"));
    for (code, kind) in [
        ("General", "General"),
        ("0.00", "Number"),
        ("m/d/yyyy", "Date"),
        ("h:mm AM/PM", "Time"),
        ("0.00E+00", "Scientific"),
        ("# ?/?", "Fraction"),
        ("@", "Text"),
        ("\"$\"#,##0.00", "Currency"),
        ("[Red]0.00", "Number"),
        ("_(\"$\"* #,##0.00_)", "Accounting"),
    ] {
        assert_eq!(number_kind(code), kind, "{code}");
    }
    let c = m.color_choices();
    assert_eq!(c.theme.len(), 6);
    assert!(c.theme.iter().all(|r| r.len() == 10));
    assert_eq!(c.theme[0][0].argb, argb([255, 255, 255]));
    // black's first shade is 90% lighter and white's 10% darker, as the phone app's grid has them
    let grey = |k: usize, i: usize| (c.theme[k][i].argb as u32) & 0xFF;
    assert!(grey(1, 1) > 220 && grey(5, 1) < 40, "{} {}", grey(1, 1), grey(5, 1));
    assert!((225..=235).contains(&grey(1, 0)) && grey(5, 0) < 40, "{} {}", grey(1, 0), grey(5, 0));
    assert_eq!(c.standard.len(), 10);
    // a picked swatch is what the colour command takes
    let pick: Json = serde_json::from_str(&c.theme[2][4].color).unwrap();
    m.execute("home.fillColor".into(), json!({ "color": pick }).to_string()).unwrap();
    let v = m.viewport(0, 0, 0, 0, 0.0);
    assert_eq!(v.cells[0].fill, Some(c.theme[2][4].argb));
}

#[test]
fn comments_and_links_on_the_active_cell() {
    let m = MobileSession::new();
    assert!(m.comment().is_none() && m.link().is_none());
    m.set_author("Ada".into());
    m.execute("review.newComment".into(), json!({ "text": "Check this" }).to_string()).unwrap();
    m.execute("review.replyComment".into(), json!({ "text": "Done" }).to_string()).unwrap();
    let c = m.comment().unwrap();
    assert_eq!((c.author.as_str(), c.text.as_str(), c.threaded), ("Ada", "Check this", true));
    assert_eq!(c.replies, vec![CommentReply { author: "Ada".into(), text: "Done".into() }]);
    let v = m.viewport(0, 0, 0, 0, 0.0);
    assert!(v.cells[0].comment);
    m.execute("insert.link".into(), json!({ "target": "https://example.org", "text": "site" }).to_string()).unwrap();
    assert_eq!(m.link().as_deref(), Some("https://example.org"));
    m.select(3, 3, false).unwrap();
    assert!(m.comment().is_none() && m.link().is_none());
}

#[test]
fn charts_and_shapes_float_over_the_sheet() {
    let m = MobileSession::new();
    assert!(m.objects().is_empty());
    for (i, v) in ["3", "5", "2", "8"].iter().enumerate() {
        m.select(i as u32, 0, false).unwrap();
        m.commit((*v).into(), "none".into()).unwrap();
    }
    m.select_range(CellRange { first_row: 0, first_col: 0, last_row: 3, last_col: 0 }).unwrap();
    m.execute("insert.chart".into(), json!({ "type": "column" }).to_string()).unwrap();
    m.execute("insert.shape".into(), json!({ "kind": "ellipse" }).to_string()).ok();
    let objs = m.objects();
    let chart = objs.iter().find(|o| o.kind == "chart").expect("a chart");
    assert!(chart.width > 10.0 && chart.height > 10.0);
    // moved by its id, it sits at the new cell
    m.execute("object.move".into(), json!({ "kind": "chart", "id": chart.id, "at": "D10", "dx": 0, "dy": 0 }).to_string()).unwrap();
    let moved = m.objects().into_iter().find(|o| o.kind == "chart").unwrap();
    assert_eq!((moved.x, moved.y), (m.col_left(3), m.row_top(9)));
    let prims = m.chart_prims(chart.index, chart.width as f32, chart.height as f32);
    assert!(prims.iter().any(|p| matches!(p, ChartPrim::Rect { fill: Some(_), .. })), "columns");
    assert!(m.chart_prims(99, 100.0, 100.0).is_empty());
    assert!(m.chart_prims(chart.index, f32::NAN, -5.0).len() < 100_000);
    assert!(m.image_bytes(0).is_none());
    // a sparkline in B1 over A1:A4 is drawn in its cell
    m.execute("insert.sparkline".into(), json!({ "range": "A1:A4", "location": "B1", "type": "line" }).to_string()).unwrap();
    let v = m.viewport(0, 0, 3, 3, 0.0);
    let sp = v.sparklines.first().expect("a sparkline");
    assert_eq!((sp.row, sp.col), (0, 1));
    assert!(sp.prims.iter().any(|p| matches!(p, ChartPrim::Line { .. })));
}

#[test]
fn a_filter_column_lists_its_values_and_filters_by_them() {
    let m = MobileSession::new();
    for (i, v) in ["Fruit", "Apple", "Pear", "Apple", ""].iter().enumerate() {
        m.select(i as u32, 0, false).unwrap();
        m.commit((*v).into(), "none".into()).unwrap();
    }
    m.select_range(CellRange { first_row: 0, first_col: 0, last_row: 3, last_col: 0 }).unwrap();
    m.execute("data.filter".into(), String::new()).unwrap();
    assert!(m.viewport(0, 0, 0, 0, 0.0).cells[0].filter_button);
    let values = m.filter_column(0, 0);
    let texts: Vec<&str> = values.iter().map(|v| v.text.as_str()).collect();
    assert_eq!(texts, vec!["Apple", "Pear"]);
    assert!(values.iter().all(|v| v.shown));
    m.execute("data.filterBy".into(), json!({ "column": "A", "values": ["Pear"] }).to_string()).unwrap();
    let values = m.filter_column(0, 0);
    assert_eq!(values.iter().filter(|v| v.shown).count(), 1);
    let v = m.viewport(0, 0, 4, 0, 0.0);
    assert!(v.rows.iter().all(|l| l.index != 1), "Apple's row is filtered out");
    assert!(m.filter_column(u32::MAX, u32::MAX).len() <= 10_000);
}

#[test]
fn a_filter_column_lists_its_fills_and_filters_by_one() {
    let m = MobileSession::new();
    for (i, v) in ["Fruit", "Apple", "Pear", "Plum", "Fig"].iter().enumerate() {
        m.select(i as u32, 0, false).unwrap();
        m.commit((*v).into(), "none".into()).unwrap();
    }
    m.execute("home.fillColor".into(), json!({ "range": "A2:A3", "color": "#FFC000" }).to_string()).unwrap();
    m.execute("home.fillColor".into(), json!({ "range": "A5", "color": { "theme": 5, "tint": 0.6 } }).to_string()).unwrap();
    m.select_range(CellRange { first_row: 0, first_col: 0, last_row: 4, last_col: 0 }).unwrap();
    m.execute("data.filter".into(), String::new()).unwrap();
    m.filter_column(0, 0);
    // no fill first, then the colours as the column has them
    let fills = m.filter_fills(0);
    assert_eq!(fills.len(), 3);
    assert_eq!(fills[0].color, None);
    assert_eq!(fills[1].color, Some(0xFFFF_C000u32 as i32));
    assert!(fills.iter().all(|f| !f.chosen));
    let theme = fills[2].param.clone();
    m.execute("data.filterBy".into(), format!("{{\"column\": \"A\", \"fillColor\": {theme}}}")).unwrap();
    let v = m.viewport(0, 0, 4, 0, 0.0);
    let rows: Vec<u32> = v.rows.iter().map(|l| l.index).collect();
    assert_eq!(rows, vec![0, 4], "only Fig, the themed fill, shows");
    assert!(m.filter_fills(0)[2].chosen);
    // no fill: Plum alone
    m.execute("data.filterBy".into(), json!({ "column": "A", "fillColor": "none" }).to_string()).unwrap();
    let rows: Vec<u32> = m.viewport(0, 0, 4, 0, 0.0).rows.iter().map(|l| l.index).collect();
    assert_eq!(rows, vec![0, 3]);
    assert!(m.execute("data.filterBy".into(), json!({ "column": "A", "fillColor": "#nothex" }).to_string()).is_err());
    assert!(m.filter_fills(u32::MAX).len() <= 64);
}

#[test]
fn a_tables_options_and_the_style_gallery() {
    let m = MobileSession::new();
    assert!(m.table_info().is_none());
    for (i, v) in ["Name", "Ann", "Bob"].iter().enumerate() {
        m.select(i as u32, 0, false).unwrap();
        m.commit((*v).into(), "none".into()).unwrap();
    }
    m.select_range(CellRange { first_row: 0, first_col: 0, last_row: 2, last_col: 0 }).unwrap();
    m.execute("insert.table".into(), String::new()).unwrap();
    let t = m.table_info().expect("a table");
    assert!(t.header_row && t.banded_rows && !t.totals_row);
    m.execute("table.totalRow".into(), json!({ "on": true }).to_string()).unwrap();
    assert!(m.table_info().unwrap().totals_row);
    let styles = m.table_styles();
    assert_eq!(styles.len(), 60);
    let medium2 = styles.iter().find(|s| s.name == "TableStyleMedium2").unwrap();
    assert_ne!(medium2.header, medium2.plain, "a medium style's header is filled");
}

/// Cards View: a table's rows as cards, with its column names; hidden rows left out; nothing
/// outside a table.
#[test]
fn a_table_reads_as_cards() {
    let m = MobileSession::new();
    for (r, row) in [["Name", "Score"], ["Ann", "7"], ["Bob", "1.5"], ["Cy", ""]].iter().enumerate() {
        for (c, v) in row.iter().enumerate() {
            m.select(r as u32 + 1, c as u32 + 1, false).unwrap();
            m.commit((*v).into(), "none".into()).unwrap();
        }
    }
    m.select(0, 0, false).unwrap();
    assert!(m.table_cards().is_none(), "not in a table");
    m.select_range(CellRange { first_row: 1, first_col: 1, last_row: 4, last_col: 2 }).unwrap();
    m.execute("insert.table".into(), String::new()).unwrap();
    m.select(2, 2, false).unwrap();
    let t = m.table_cards().expect("a table");
    assert_eq!(t.fields, ["Name", "Score"]);
    assert_eq!(t.first_col, 1);
    assert_eq!(t.cards.iter().map(|c| c.row).collect::<Vec<_>>(), [2, 3, 4]);
    assert_eq!(t.cards[1].values, ["Bob", "1.5"]);
    assert_eq!(t.cards[2].values, ["Cy", ""]);
    assert!(!t.more);
    m.execute("home.hideRows".into(), json!({ "rows": "4:4" }).to_string()).unwrap();
    m.select(2, 2, false).unwrap();
    assert_eq!(m.table_cards().unwrap().cards.len(), 2, "Bob's row is hidden");
}

#[test]
fn the_theme_names_its_fonts() {
    let m = MobileSession::new();
    let fonts = m.theme_fonts();
    assert_eq!(fonts.len(), 2);
    assert!(fonts.iter().all(|f| !f.is_empty()));
}

#[test]
fn the_aggregate_bar_sums_a_selection() {
    let m = MobileSession::new();
    for (i, v) in ["10", "20", "x"].iter().enumerate() {
        m.select(i as u32, 0, false).unwrap();
        m.commit((*v).into(), "none".into()).unwrap();
    }
    assert!(m.selection_stats().is_none(), "one cell has no bar");
    m.select_range(CellRange { first_row: 0, first_col: 0, last_row: 2, last_col: 0 }).unwrap();
    let st = m.selection_stats().unwrap();
    assert_eq!((st.count, st.numeric, st.sum.as_str(), st.average.as_str()), (3, 2, "30", "15"));
}

#[test]
fn a_validation_list_offers_its_items() {
    let m = MobileSession::new();
    assert!(m.validation().is_none());
    m.execute("data.validation".into(), json!({ "range": "B2", "type": "list", "formula1": "Red,Green,Blue" }).to_string()).unwrap();
    m.select(1, 1, false).unwrap();
    let v = m.validation().expect("a list");
    assert_eq!(v.items, vec!["Red", "Green", "Blue"]);
    m.select(0, 0, false).unwrap();
    assert!(m.validation().is_none());
}

#[test]
fn fit_texts_lists_each_line_with_its_font() {
    let m = MobileSession::new();
    m.execute("cell.set".into(), json!({ "cell": "B2", "input": "one\ntwo" }).to_string()).unwrap();
    m.execute("cell.set".into(), json!({ "cell": "C1", "input": "far" }).to_string()).unwrap();
    let t = m.fit_texts(1, 1);
    assert_eq!(t.iter().map(|f| f.text.as_str()).collect::<Vec<_>>(), vec!["one", "two"]);
    assert!(t.iter().all(|f| f.col == 1 && f.size > 0.0 && f.extra >= 6.0));
    assert!(m.fit_texts(5, 9).is_empty());
}

#[test]
fn comments_are_listed_in_reading_order() {
    let m = MobileSession::new();
    assert!(m.comments().is_empty());
    for (cell, text) in [("C1", "third"), ("A2", "fourth"), ("B1", "second"), ("A1", "first")] {
        m.execute("selection.set".into(), json!({ "range": cell }).to_string()).unwrap();
        m.execute("review.newComment".into(), json!({ "text": text }).to_string()).unwrap();
    }
    let list = m.comments();
    assert_eq!(list.iter().map(|c| c.cell.as_str()).collect::<Vec<_>>(), vec!["A1", "B1", "C1", "A2"]);
    assert_eq!(list[0].text, "first");
}

#[test]
fn right_to_left_text_sits_on_the_right() {
    use gridcraft_engine::display::reads_right_to_left;
    assert!(reads_right_to_left(0, "\u{645}\u{631}\u{62d}\u{628}\u{627}"));
    assert!(!reads_right_to_left(0, "Hello"));
    assert!(!reads_right_to_left(1, "\u{5e9}\u{5dc}\u{5d5}\u{5dd}"));
    assert!(reads_right_to_left(2, "Hello"));
    let m = MobileSession::new();
    m.execute("home.textDirection".into(), json!({ "range": "A1", "direction": "rtl" }).to_string()).unwrap();
    assert_eq!(m.cell_format().reading_order, 2);
}

#[test]
fn a_pivot_table_is_described_from_inside_it() {
    let m = MobileSession::new();
    for (i, (a, b)) in [("Region", "Sales"), ("North", "3"), ("South", "5")].iter().enumerate() {
        m.execute("cell.set".into(), json!({ "cell": format!("A{}", i + 1), "input": a }).to_string()).unwrap();
        m.execute("cell.set".into(), json!({ "cell": format!("B{}", i + 1), "input": b }).to_string()).unwrap();
    }
    m.execute(
        "insert.pivotTable".into(),
        json!({ "source": "Sheet1!$A$1:$B$3", "destination": "Sheet1!D2", "rows": ["Region"], "values": ["Sales"] }).to_string(),
    )
    .unwrap();
    m.select(0, 0, false).unwrap();
    assert!(m.pivot_info().is_none());
    m.execute("selection.set".into(), json!({ "range": "D3" }).to_string()).unwrap();
    let p = m.pivot_info().expect("inside the pivot");
    assert_eq!(p.layout, "compact");
    m.execute("pivot.subtotals".into(), json!({ "mode": "top" }).to_string()).unwrap();
    m.execute("pivot.reportLayout".into(), json!({ "layout": "outline" }).to_string()).unwrap();
    let p = m.pivot_info().expect("still inside");
    assert_eq!((p.layout.as_str(), p.subtotals.as_str()), ("outline", "top"));
}

#[test]
fn a_filtered_column_and_its_rows_are_marked() {
    let m = MobileSession::new();
    for (i, v) in ["Sales", "5", "50", "500"].iter().enumerate() {
        m.execute("cell.set".into(), json!({ "cell": format!("A{}", i + 1), "input": v }).to_string()).unwrap();
    }
    m.execute("selection.set".into(), json!({ "range": "A1" }).to_string()).unwrap();
    m.execute("data.filter".into(), "{}".into()).unwrap();
    assert!(m.sheet_view().filtered_rows.is_none());
    m.execute("data.filterBy".into(), json!({ "column": "A", "custom": { "op": ">", "value": "10" } }).to_string()).unwrap();
    assert_eq!(m.sheet_view().filtered_rows, Some(RowSpan { first: 1, last: 3 }));
    let v = m.viewport(0, 0, 5, 3, 7.0);
    let head = v.cells.iter().find(|c| c.row == 0 && c.col == 0).expect("the header cell");
    assert!(head.filter_button && head.filtered);
}

#[test]
fn chart_info_follows_the_elements() {
    let m = MobileSession::new();
    for (i, v) in ["Sales", "3", "5", "4"].iter().enumerate() {
        m.execute("cell.set".into(), json!({ "cell": format!("A{}", i + 1), "input": v }).to_string()).unwrap();
    }
    m.execute("selection.set".into(), json!({ "range": "A1:A4" }).to_string()).unwrap();
    m.execute("insert.chart".into(), json!({ "type": "column" }).to_string()).unwrap();
    let id = m.objects().iter().find(|o| o.kind == "chart").expect("a chart").id;
    m.execute("chart.addElement".into(), json!({ "chart": id, "element": "legend", "position": "right" }).to_string()).unwrap();
    m.execute("chart.addElement".into(), json!({ "chart": id, "element": "trendline", "type": "linearForecast" }).to_string()).unwrap();
    let info = m.chart_info(id).expect("its info");
    assert_eq!((info.legend.as_str(), info.trendline.as_str()), ("right", "linearForecast"));
    assert!(m.chart_info(id + 1000).is_none());
}

#[test]
fn checkbox_cells_say_whether_they_are_ticked() {
    let m = MobileSession::new();
    m.execute("insert.checkbox".into(), json!({ "range": "A1:A2" }).to_string()).unwrap();
    m.execute("cell.toggleCheckbox".into(), json!({ "cell": "A2" }).to_string()).unwrap();
    let v = m.viewport(0, 0, 3, 2, 7.0);
    let at = |r: u32| v.cells.iter().find(|c| c.row == r && c.col == 0).and_then(|c| c.checkbox);
    assert_eq!((at(0), at(1)), (Some(false), Some(true)));
}

#[test]
fn conditional_formats_are_listed_in_words() {
    let m = MobileSession::new();
    let cf = |range: &str, rule: Json| m.execute("home.conditionalFormat".into(), json!({ "range": range, "rule": rule }).to_string()).unwrap();
    cf("A1:A9", json!({ "type": "cellIs", "operator": "between", "value": 1, "value2": 9 }));
    cf("B1:B9", json!({ "type": "top10", "rank": 5, "percent": true, "preset": "greenFill" }));
    cf("C1:C9", json!({ "type": "colorScale", "colors": ["#FF0000", "#00FF00"] }));
    let rules = m.cond_formats();
    let words: Vec<(&str, &str)> = rules.iter().map(|r| (r.rule.as_str(), r.applies_to.as_str())).collect();
    // the newest rule applies first
    assert_eq!(words, [("Graded Color Scale", "C1:C9"), ("Top 5%", "B1:B9"), ("Cell Value between 1 and 9", "A1:A9")]);
    assert_eq!(rules[0].colors, [0xFFFF_0000u32 as i32, 0xFF00_FF00u32 as i32]);
    assert_eq!((rules[1].fill, rules[1].text), (Some(0xFFC6_EFCEu32 as i32), Some(0xFF00_6100u32 as i32)));
    m.execute("home.manageRules".into(), json!({ "delete": 0 }).to_string()).unwrap();
    assert_eq!(m.cond_formats().len(), 2);
    cf("D1:D9", json!({ "type": "iconSet", "set": "3Flags" }));
    assert_eq!(m.cond_formats()[0].icons, "3Flags");
}

#[test]
fn validation_rules_read_back_as_typed() {
    let m = MobileSession::new();
    let set = |p: Json| m.execute("data.validation".into(), p.to_string()).unwrap();
    set(json!({ "range": "A1:A3", "type": "list", "formula1": "Open,Done", "dropdown": false, "inputTitle": "Status" }));
    set(json!({ "range": "B1", "type": "date", "operator": "between", "formula1": "2026-01-01", "formula2": "2026-12-31", "errorStyle": "warning" }));
    set(json!({ "range": "C1", "type": "time", "operator": "less", "formula1": "17:45" }));
    let at = |row: u32, col: u32| {
        m.select(row, col, false).unwrap();
        m.validation_rule().unwrap()
    };
    let a = at(1, 0);
    assert_eq!((a.kind.as_str(), a.formula1.as_str(), a.dropdown, a.input_title.as_str()), ("list", "Open,Done", false, "Status"));
    let b = at(0, 1);
    assert_eq!((b.formula1.as_str(), b.formula2.as_str(), b.error_style.as_str()), ("2026-01-01", "2026-12-31", "warning"));
    let c = at(0, 2);
    assert_eq!((c.operator.as_str(), c.formula1.as_str()), ("less", "17:45"));
    m.select(5, 5, false).unwrap();
    assert!(m.validation_rule().is_none());
}

#[test]
fn entries_a_rule_refuses_get_its_alert() {
    let m = MobileSession::new();
    m.execute(
        "data.validation".into(),
        json!({ "range": "A1", "type": "whole", "operator": "between", "formula1": "1", "formula2": "10", "errorStyle": "warning", "errorTitle": "Out of range", "errorMessage": "1 to 10, please." })
            .to_string(),
    )
    .unwrap();
    assert!(m.check_entry("7".into()).is_none());
    let alert = m.check_entry("70".into()).unwrap();
    assert_eq!((alert.style.as_str(), alert.title.as_str(), alert.message.as_str()), ("warning", "Out of range", "1 to 10, please."));
    m.select(4, 4, false).unwrap();
    assert!(m.check_entry("70".into()).is_none(), "a cell without a rule takes anything");
}

#[test]
fn outline_groups_come_with_the_sheet_view() {
    let m = MobileSession::new();
    m.execute("data.group".into(), json!({ "rows": "2:5" }).to_string()).unwrap();
    m.execute("data.group".into(), json!({ "rows": "3:4" }).to_string()).unwrap();
    m.execute("data.group".into(), json!({ "cols": "B:C" }).to_string()).unwrap();
    let v = m.sheet_view();
    assert_eq!((v.row_levels, v.col_levels), (2, 1));
    let g = |level: u8, start: u32, end: u32, collapsed: bool| OutlineGroupView { level, start, end, collapsed, summary: Some(end + 1) };
    assert_eq!(v.row_groups, [g(2, 2, 3, false), g(1, 1, 4, false)]);
    assert_eq!(v.col_groups, [g(1, 1, 2, false)]);
    m.execute("data.hideDetail".into(), json!({ "rows": "2:5", "level": 1 }).to_string()).unwrap();
    assert!(m.sheet_view().row_groups.iter().any(|x| x.level == 1 && x.collapsed));
}

#[test]
fn protection_says_when_a_password_guards_it() {
    let m = MobileSession::new();
    m.execute("review.protectSheet".into(), json!({ "password": "pa55" }).to_string()).unwrap();
    let tab = &m.info().sheets[0];
    assert!(tab.protected && tab.password);
    assert!(m.execute("review.unprotectSheet".into(), json!({ "password": "nope" }).to_string()).is_err());
    m.execute("review.unprotectSheet".into(), json!({ "password": "pa55" }).to_string()).unwrap();
    assert!(!m.info().sheets[0].protected);
    m.execute("review.protectWorkbook".into(), json!({ "on": true }).to_string()).unwrap();
    let i = m.info();
    assert!(i.structure_locked && !i.structure_password);
}

#[test]
fn calculation_mode_and_show_formulas_are_reported() {
    let m = MobileSession::new();
    assert_eq!(m.info().calc_mode, "automatic");
    m.execute("formulas.calculationOptions".into(), json!({ "mode": "manual" }).to_string()).unwrap();
    assert_eq!(m.info().calc_mode, "manual");
    assert!(!m.sheet_view().show_formulas);
    m.execute("formulas.showFormulas".into(), json!({ "on": true }).to_string()).unwrap();
    assert!(m.sheet_view().show_formulas);
}

#[test]
fn cell_format_reports_orientation_and_indent() {
    let m = MobileSession::new();
    m.execute("home.orientation".into(), json!({ "angle": "counterclockwise" }).to_string()).unwrap();
    m.execute("home.increaseIndent".into(), "{}".into()).unwrap();
    let f = m.cell_format();
    assert_eq!((f.rotation, f.indent), (45, 1));
    m.execute("home.orientation".into(), json!({ "angle": "vertical" }).to_string()).unwrap();
    assert_eq!(m.cell_format().rotation, 255);
}

#[test]
fn the_name_box_shows_a_name_however_its_range_is_written() {
    let m = MobileSession::new();
    m.execute("formulas.defineName".into(), json!({ "name": "Units", "refersTo": "=Sheet1!$B$1:$B$1048576" }).to_string()).unwrap();
    m.execute("formulas.defineName".into(), json!({ "name": "Block", "refersTo": "=Sheet1!$D$2:$E$4" }).to_string()).unwrap();
    let col = m.select_range(CellRange { first_row: 0, first_col: 1, last_row: MAX_ROWS - 1, last_col: 1 }).unwrap();
    assert_eq!(col.name_box, "Units");
    let block = m.select_range(CellRange { first_row: 1, first_col: 3, last_row: 3, last_col: 4 }).unwrap();
    assert_eq!(block.name_box, "Block");
}

#[test]
fn chart_info_reports_the_new_elements() {
    let m = MobileSession::new();
    m.execute("range.setValues".into(), json!({ "range": "A1", "values": [["", "Jan", "Feb"], ["North", 4, 6], ["South", 5, 3]] }).to_string())
        .unwrap();
    m.execute("insert.chart".into(), json!({ "range": "A1:C3", "type": "line" }).to_string()).unwrap();
    let id = m.objects().into_iter().find(|o| o.kind == "chart").unwrap().id;
    let add = |p: Json| {
        let mut p = p;
        p["chart"] = json!(id);
        m.execute("chart.addElement".into(), p.to_string()).unwrap();
    };
    let before = m.chart_info(id).unwrap();
    assert_eq!(before.kind, "Line");
    assert_eq!((before.label_position.as_str(), before.error_bars.as_str(), before.lines.as_str()), ("auto", "none", "none"));
    add(json!({ "element": "dataLabels", "position": "above" }));
    add(json!({ "element": "errorBars", "type": "standardDeviation" }));
    add(json!({ "element": "lines", "type": "drop" }));
    add(json!({ "element": "dataTable" }));
    add(json!({ "element": "title", "position": "overlay" }));
    let after = m.chart_info(id).unwrap();
    assert_eq!((after.label_position.as_str(), after.error_bars.as_str(), after.lines.as_str()), ("above", "standardDeviation", "drop"));
    assert!(after.data_table && after.title_overlay && !after.up_down_bars);
}

#[test]
fn a_custom_format_previews_the_active_cell() {
    let m = MobileSession::new();
    m.commit("1234.5".into(), "none".into()).unwrap();
    assert_eq!(m.format_preview("#,##0.00".into()), "1,234.50");
    assert_eq!(m.format_preview("0%".into()), "123450%");
    assert_eq!(m.format_preview("0;[Red](0)".into()), "1235");
}

#[test]
fn notes_anywhere_in_the_workbook_are_found() {
    let m = MobileSession::new();
    assert!(!m.has_notes());
    m.execute("review.newComment".into(), json!({ "text": "a thread" }).to_string()).unwrap();
    assert!(!m.has_notes(), "a comment thread is not a note");
    m.execute("home.insertSheet".into(), json!({ "before": 1 }).to_string()).unwrap();
    m.execute("review.newNote".into(), json!({ "cell": "B2", "text": "a note" }).to_string()).unwrap();
    m.activate_sheet(0).unwrap();
    assert!(m.has_notes());
    m.execute("review.convertToComments".into(), "{}".into()).unwrap();
    assert!(!m.has_notes());
}

#[test]
fn the_active_cells_row_height_and_column_width_are_reported() {
    let m = MobileSession::new();
    let f = m.cell_format();
    assert_eq!(f.row_height, 15.0);
    assert!((f.col_width - 8.43).abs() < 0.01, "{}", f.col_width);
    // the command takes pixels (40 px is 30 pt)
    m.execute("home.rowHeight".into(), json!({ "height": 40 }).to_string()).unwrap();
    m.execute("home.columnWidth".into(), json!({ "chars": 20 }).to_string()).unwrap();
    let f = m.cell_format();
    assert_eq!(f.row_height, 30.0);
    assert!((f.col_width - 20.0).abs() < 0.01, "{}", f.col_width);
}

#[test]
fn cell_style_tiles_show_each_styles_look() {
    let m = MobileSession::new();
    let names = ["Good", "Heading 1", "Note", "Total", "40% - Accent2", "No Such Style"].map(String::from).to_vec();
    let t = m.cell_style_tiles(names);
    assert_eq!(t.iter().map(|t| t.name.as_str()).collect::<Vec<_>>(), ["Good", "Heading 1", "Note", "Total", "40% - Accent2"]);
    // Good: a green fill, dark green text, no lines
    assert_eq!((t[0].fill, t[0].text), (Some(argb([0xC6, 0xEF, 0xCE])), argb([0x00, 0x61, 0x00])));
    assert!(t[0].around.is_none() && t[0].bottom.is_none());
    // Heading 1: bold, larger, a thick line under it
    assert!(t[1].bold && t[1].size > 11.0);
    assert_eq!(t[1].bottom.as_ref().map(|l| l.style.as_str()), Some("thick"));
    // Note: a thin box; Total: a thin top and a double bottom
    assert_eq!(t[2].around.as_ref().map(|l| l.style.as_str()), Some("thin"));
    assert_eq!((t[3].top.as_ref().map(|l| l.style.as_str()), t[3].bottom.as_ref().map(|l| l.style.as_str())), (Some("thin"), Some("double")));
    // a themed style's fill follows the theme
    assert!(t[4].fill.is_some());
    // the active cell's style is marked: a plain cell is Normal; one given Good is Good
    let chosen = |m: &MobileSession| {
        m.cell_style_tiles(["Normal", "Good", "Bad"].map(String::from).to_vec()).into_iter().filter(|t| t.chosen).map(|t| t.name).collect::<Vec<_>>()
    };
    assert_eq!(chosen(&m), ["Normal"]);
    m.execute("home.cellStyle".into(), json!({ "name": "Good" }).to_string()).unwrap();
    assert_eq!(chosen(&m), ["Good"]);
}

#[test]
fn a_picture_placed_in_a_cell_shows_in_the_viewport() {
    let m = MobileSession::new();
    // a 1x1 PNG
    let png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
    m.execute("insert.picture".into(), json!({ "placement": "cell", "at": "B2", "base64": png }).to_string()).unwrap();
    let v = m.viewport(0, 0, 5, 5, 0.0);
    assert!(cell(&v, 1, 1).is_some_and(|c| c.picture && c.text.is_empty()), "the cell is drawn with its picture, not its value");
    assert!(cell(&v, 0, 0).is_none_or(|c| !c.picture));
    let bytes = m.cell_picture(1, 1).expect("its bytes");
    assert!(bytes.starts_with(&[0x89, b'P', b'N', b'G']));
    assert_eq!(m.cell_picture(0, 0), None);
}

#[test]
fn chart_previews_are_drawn_for_the_kinds_the_engine_makes() {
    let m = MobileSession::new();
    for kind in [
        "pie|pieOfPie",
        "pie|barOfPie",
        "column|",
        "column|stacked100",
        "line|markers",
        "pie|",
        "bar|stacked",
        "area|",
        "scatter|",
        "bubble|",
        "histogram|",
        "boxWhisker|",
        "waterfall|",
        "funnel|",
        "treemap|",
        "sunburst|",
        "radar|",
        "radar|markers",
        "radar|filled",
        "line|stacked100Markers",
        "area|stacked100",
        "scatter|smooth",
        "scatter|linesOnly",
        "column|3d",
        "column|3dStacked100",
        "column|3dColumn",
        "bar|3dStacked",
        "line|3d",
        "pie|3d",
        "area|3d",
        "area|3dStacked",
        "bubble|3d",
        "histogram|pareto",
        "surface|",
        "surface|wireframe",
        "surface|contour",
        "surface|wireframeContour",
    ] {
        assert!(!m.chart_preview(kind.into(), 240.0, 150.0).is_empty(), "{kind}");
    }
    // the open workbook is left as it was
    assert!(m.objects().is_empty());
    assert!(!m.info().can_undo);
    // hostile sizes are kept in bounds
    assert!(!m.chart_preview("column|".into(), f32::NAN, -5.0).is_empty());
}

#[test]
fn the_shapes_gallery_draws_every_shape_and_shapes_carry_their_outline() {
    let m = MobileSession::new();
    let bands = m.shape_gallery();
    let names: Vec<&str> = bands.iter().map(|b| b.name.as_str()).collect();
    assert_eq!(names, ["Lines", "Rectangles", "Basic Shapes", "Block Arrows", "Equation Shapes", "Flowchart", "Stars and Banners", "Callouts"]);
    for b in &bands {
        for t in &b.shapes {
            let p = m.shape_preview(t.kind.clone(), 44.0, 44.0);
            assert!(!p.is_empty(), "{}", t.label);
            // drawn inside its tile (a curve's control points may stand a little out of it)
            for piece in &p {
                assert!(piece.cmds.iter().all(|v| v.is_finite() && *v >= -11.0 && *v <= 55.0), "{}", t.label);
            }
        }
    }
    assert!(m.shape_preview("notAShape".into(), 44.0, 44.0).is_empty());
    assert!(!m.shape_preview("star5".into(), f32::NAN, -1.0).is_empty());
    // an inserted shape comes with its outline; a text box draws its own
    m.execute("insert.shape".into(), json!({ "kind": "flowChartDecision" }).to_string()).unwrap();
    m.execute("insert.shape".into(), json!({ "kind": "textBox" }).to_string()).unwrap();
    let objs = m.objects();
    let shapes: Vec<&SheetObject> = objs.iter().filter(|o| o.kind == "shape").collect();
    assert_eq!(shapes.len(), 2);
    assert_eq!(shapes[0].shape, "preset");
    assert_eq!(shapes[0].pieces.len(), 1);
    assert_eq!(shapes[0].pieces[0].paint, "fill");
    assert!(shapes[1].pieces.is_empty());
}
