//! Table styles: how a cell inside a table looks, by the familiar `TableStyleLight1…21 /
//! Medium1…28 / Dark1…11` names (so files round-trip), in the workbook's theme colours.

use gridcraft_core::{CellRef, RangeRef};
use gridcraft_model::*;

/// (family, number) from a style name, e.g. `TableStyleMedium2` → ("Medium", 2).
pub fn parse_style_name(name: &str) -> (&'static str, u32) {
    let rest = name.strip_prefix("TableStyle").unwrap_or(name);
    for fam in ["Light", "Medium", "Dark"] {
        if let Some(n) = rest.strip_prefix(fam) {
            let k = n.parse::<u32>().unwrap_or(2).max(1); // the gallery starts at 1; 0 would underflow `n - 1`
            return (fam, k);
        }
    }
    ("Medium", 2)
}

/// A line of a table style: how it is drawn and its colour.
type Line = Option<(BorderStyle, Color)>;

/// What one part of a table style gives its cells: a fill, a text colour, bold, and lines along
/// the part's edges and between its rows and columns. Unset leaves what parts under it give.
#[derive(Clone, Copy, Debug, Default)]
pub struct Part {
    pub fill: Option<Color>,
    pub font: Option<Color>,
    pub bold: bool,
    pub top: Line,
    pub bottom: Line,
    pub left: Line,
    pub right: Line,
    pub inside_h: Line,
    pub inside_v: Line,
}

impl Part {
    fn outline(mut self, l: Line) -> Self {
        (self.top, self.bottom, self.left, self.right) = (l, l, l, l);
        self
    }
}

/// A table style's parts, laid over each other in this order: the whole table, the banded rows,
/// the first and last columns, then the header and total rows.
#[derive(Clone, Copy, Debug, Default)]
pub struct TableLook {
    pub whole: Part,
    pub band: Part,
    pub first_col: Part,
    pub last_col: Part,
    pub header: Part,
    pub total: Part,
}

/// The gallery's styles as families of seven (Light 1-7, 8-14, 15-21; Medium 1-7, 8-14, 15-21,
/// 22-28; Dark 1-7), the first of each in the text colour's greys and the rest in accents 1-6,
/// and Dark 8-11 in pairs of colours. Laid out from how the reference draws each style.
pub fn look(style: &str) -> TableLook {
    let (fam, n) = parse_style_name(style);
    let k = (n - 1) % 7; // 0: the greys, 1-6: accent k
    let grey = k == 0;
    let acc = |tint: i16| Color::Theme(3 + k as u8, tint);
    let white = Color::Theme(0, 0);
    let black = Color::Theme(1, 0);
    // the greys: the light colour darkened, the text colour lightened
    let lt = |darker: i16| Color::Theme(0, -darker);
    let dk = |lighter: i16| Color::Theme(1, lighter);
    let thin = |c: Color| Some((BorderStyle::Thin, c));
    let medium = |c: Color| Some((BorderStyle::Medium, c));
    let thick = |c: Color| Some((BorderStyle::Thick, c));
    let double = |c: Color| Some((BorderStyle::Double, c));
    let bold = Part { bold: true, ..Part::default() };
    let full = if grey { black } else { acc(0) };
    let band80 = if grey { lt(150) } else { acc(800) };
    let band60 = if grey { lt(350) } else { acc(600) };
    match (fam, (n - 1) / 7) {
        // lines only: the header and the total row underlined and overlined, the text darker
        ("Light", 0) => TableLook {
            whole: Part { font: Some(if grey { black } else { acc(-250) }), top: thin(full), bottom: thin(full), ..Part::default() },
            band: Part { fill: Some(band80), ..Part::default() },
            first_col: bold,
            last_col: bold,
            header: Part { bold: true, bottom: thin(full), ..Part::default() },
            total: Part { bold: true, top: thin(full), ..Part::default() },
        },
        // a filled header, a box round the table, the rows ruled
        ("Light", 1) => TableLook {
            whole: Part::default().outline(thin(full)),
            band: Part { top: thin(full), bottom: thin(full), ..Part::default() },
            first_col: bold,
            last_col: bold,
            header: Part { fill: Some(full), font: Some(white), bold: true, ..Part::default() },
            total: Part { bold: true, top: double(full), ..Part::default() },
        },
        // a grid, a heavier line under the header
        ("Light", _) => TableLook {
            whole: Part { inside_h: thin(full), inside_v: thin(full), ..Part::default() }.outline(thin(full)),
            band: Part { fill: Some(band80), ..Part::default() },
            first_col: bold,
            last_col: bold,
            header: Part { bold: true, bottom: medium(full), ..Part::default() },
            total: Part { bold: true, top: double(full), ..Part::default() },
        },
        // a filled header over light ruled bands
        ("Medium", 0) => {
            let line = if grey { black } else { acc(400) };
            TableLook {
                whole: Part { inside_h: thin(line), ..Part::default() }.outline(thin(line)),
                band: Part { fill: Some(band80), ..Part::default() },
                first_col: bold,
                last_col: bold,
                header: Part { fill: Some(full), font: Some(white), bold: true, ..Part::default() },
                total: Part { bold: true, top: double(full), ..Part::default() },
            }
        }
        // filled throughout, white rules; the header, first and last columns and total row solid
        ("Medium", 1) => {
            let solid = Part { fill: Some(full), font: Some(white), bold: true, ..Part::default() };
            TableLook {
                whole: Part { fill: Some(band80), inside_h: thin(white), inside_v: thin(white), ..Part::default() },
                band: Part { fill: Some(band60), ..Part::default() },
                first_col: solid,
                last_col: solid,
                header: Part { bottom: thick(white), ..solid },
                total: Part { top: thick(white), ..solid },
            }
        }
        // grey bands framed by a solid header and solid first and last columns
        ("Medium", 2) => {
            let solid = Part { fill: Some(full), font: Some(white), bold: true, ..Part::default() };
            let inside = if grey { thin(black) } else { None };
            TableLook {
                whole: Part { top: medium(black), bottom: medium(black), inside_h: inside, inside_v: inside, ..Part::default() },
                band: Part { fill: Some(lt(150)), ..Part::default() },
                first_col: solid,
                last_col: solid,
                header: Part { bottom: medium(black), ..solid },
                total: Part { bold: true, top: double(black), ..Part::default() },
            }
        }
        // light all over, a grid in a lighter shade
        ("Medium", _) => {
            let line = if grey { black } else { acc(400) };
            TableLook {
                whole: Part { fill: Some(band80), inside_h: thin(line), inside_v: thin(line), ..Part::default() }.outline(thin(line)),
                band: Part { fill: Some(band60), ..Part::default() },
                first_col: bold,
                last_col: bold,
                header: Part { bold: true, ..Part::default() },
                total: Part { bold: true, top: medium(full), ..Part::default() },
            }
        }
        // dark: a black header, the colour and a darker shade of it, white rules
        ("Dark", 0) => {
            let (body, darker, darkest) = if grey { (dk(450), dk(250), dk(150)) } else { (acc(0), acc(-250), acc(-500)) };
            TableLook {
                whole: Part { fill: Some(body), font: Some(white), ..Part::default() },
                band: Part { fill: Some(darker), ..Part::default() },
                first_col: Part { fill: Some(darker), bold: true, right: medium(white), ..Part::default() },
                last_col: Part { fill: Some(darker), bold: true, left: medium(white), ..Part::default() },
                header: Part { fill: Some(black), font: Some(white), bold: true, bottom: medium(white), ..Part::default() },
                total: Part { fill: Some(darkest), font: Some(white), bold: true, top: medium(white), ..Part::default() },
            }
        }
        // a header in one colour over light bands of another
        _ => {
            // Dark 8-11: (header, body) as theme slots
            let (head, body) = match n {
                9 => (5, 4),
                10 => (7, 6),
                11 => (9, 8),
                _ => (1, 0),
            };
            let (b80, b60) = if body == 0 { (lt(150), lt(350)) } else { (Color::Theme(body, 800), Color::Theme(body, 600)) };
            TableLook {
                whole: Part { fill: Some(b80), ..Part::default() },
                band: Part { fill: Some(b60), ..Part::default() },
                first_col: bold,
                last_col: bold,
                header: Part { fill: Some(Color::Theme(head, 0)), font: Some(white), bold: true, ..Part::default() },
                total: Part { bold: true, top: double(black), ..Part::default() },
            }
        }
    }
}

/// The style a table gives a cell (merged over the cell's own style: explicit fills and text
/// colours win).
pub fn table_cell_style(wb: &Workbook, sheet: usize, t: &Table, c: CellRef) -> Style {
    let own = wb.sheet(sheet).map(|sh| wb.styles.get(sh.style_id(c)).clone()).unwrap_or_default();
    // no style (the gallery's None): the cells as they are
    if t.style.is_empty() {
        return own;
    }
    let lk = look(&t.style);
    let r = t.range;
    let is_header = t.header_row && c.row == r.start.row;
    let is_total = t.totals_row && c.row == r.end.row;
    let data_row = c.row.saturating_sub(r.start.row + t.header_row as u32);
    let data_col = c.col - r.start.col;
    let mut fill = None;
    let mut font = None;
    let mut bold = false;
    let mut b = Borders::default();
    // a part over `area` (rows r0..=r1, columns c0..=c1) on this cell
    let mut apply = |p: &Part, r0: u32, r1: u32, c0: u32, c1: u32| {
        fill = p.fill.or(fill);
        font = p.font.or(font);
        bold |= p.bold;
        let set = |slot: &mut BorderLine, l: Line| {
            if let Some((style, color)) = l {
                *slot = BorderLine { style, color };
            }
        };
        set(&mut b.top, if c.row == r0 { p.top } else { p.inside_h });
        set(&mut b.bottom, if c.row == r1 { p.bottom } else { p.inside_h });
        set(&mut b.left, if c.col == c0 { p.left } else { p.inside_v });
        set(&mut b.right, if c.col == c1 { p.right } else { p.inside_v });
    };
    apply(&lk.whole, r.start.row, r.end.row, r.start.col, r.end.col);
    if !is_header && !is_total && t.banded_rows && data_row.is_multiple_of(2) {
        apply(&lk.band, c.row, c.row, r.start.col, r.end.col);
    }
    if !is_header && !is_total && t.banded_cols && data_col.is_multiple_of(2) {
        apply(&Part { fill: lk.band.fill, ..Part::default() }, c.row, c.row, c.col, c.col);
    }
    if t.first_col && c.col == r.start.col {
        apply(&lk.first_col, r.start.row, r.end.row, c.col, c.col);
    }
    if t.last_col && c.col == r.end.col {
        apply(&lk.last_col, r.start.row, r.end.row, c.col, c.col);
    }
    if is_header {
        apply(&lk.header, c.row, c.row, r.start.col, r.end.col);
    }
    if is_total {
        apply(&lk.total, c.row, c.row, r.start.col, r.end.col);
    }
    // the header's and total row's lines, and the first and last columns', win on the edges they
    // share with their neighbours
    let line = |slot: &mut BorderLine, l: Line| {
        if let Some((style, color)) = l {
            *slot = BorderLine { style, color };
        }
    };
    if t.header_row && !is_total && c.row == r.start.row + 1 {
        line(&mut b.top, lk.header.bottom);
    }
    if t.totals_row && !is_header && c.row + 1 == r.end.row {
        line(&mut b.bottom, lk.total.top);
    }
    if t.first_col && c.col == r.start.col + 1 && !is_header {
        line(&mut b.left, lk.first_col.right);
    }
    if t.last_col && c.col + 1 == r.end.col && !is_header {
        line(&mut b.right, lk.last_col.left);
    }
    let mut s = own.clone();
    if own.fill.pattern == PatternType::None
        && let Some(f) = fill
    {
        s.fill = Fill::solid(f);
    }
    if own.font.color == Color::Auto
        && let Some(f) = font
    {
        s.font.color = f;
    }
    s.font.bold |= bold;
    for (mine, theirs) in
        [(&mut s.borders.top, b.top), (&mut s.borders.bottom, b.bottom), (&mut s.borders.left, b.left), (&mut s.borders.right, b.right)]
    {
        if mine.is_none() && !theirs.is_none() {
            *mine = theirs;
        }
    }
    s
}

/// A style as the gallery shows it: a sample table five cells square (the header, three data rows
/// and the total row, with the first and last columns and banded rows on), its cells' looks row
/// by row in `wb`'s theme.
pub fn sample(wb: &Workbook, style: &str) -> Vec<Style> {
    let theme = wb.theme.clone();
    let mut wb = Workbook::new();
    wb.theme = theme;
    let t = Table {
        id: 0,
        name: String::new(),
        range: RangeRef::new(CellRef::new(0, 0), CellRef::new(4, 4)),
        header_row: true,
        totals_row: true,
        columns: vec![],
        style: style.to_string(),
        banded_rows: true,
        banded_cols: false,
        first_col: true,
        last_col: true,
        filter_button: false,
        filter: vec![],
        table_type: None,
        connection_id: None,
        alt_text: String::new(),
    };
    (0..5).flat_map(|r| (0..5).map(move |c| CellRef::new(r, c))).map(|c| table_cell_style(&wb, 0, &t, c)).collect()
}

/// Gallery list of style names.
pub fn gallery() -> Vec<String> {
    let mut v = Vec::new();
    for n in 1..=21 {
        v.push(format!("TableStyleLight{n}"));
    }
    for n in 1..=28 {
        v.push(format!("TableStyleMedium{n}"));
    }
    for n in 1..=11 {
        v.push(format!("TableStyleDark{n}"));
    }
    v
}

/// Editing a table's header row renames the column, as in Excel: an empty or repeated header
/// gets a unique name written back into the cell, and structured references follow the rename.
pub(crate) fn sync_headers(cx: &mut crate::cmd::Ctx) {
    if cx.wb.sheets.iter().all(|s| s.tables.is_empty()) {
        return;
    }
    // (sheet, table name, table range, old column name, new column name)
    let mut renames: Vec<(usize, String, RangeRef, String, String)> = Vec::new();
    let heads: Vec<(usize, RangeRef)> = (0..cx.wb.sheets.len())
        .flat_map(|si| cx.wb.sheet(si).into_iter().flat_map(move |sh| sh.tables.iter().filter(|t| t.header_row).map(move |t| (si, t.range))))
        .map(|(si, r)| (si, RangeRef::new(r.start, CellRef::new(r.start.row, r.end.col))))
        .collect();
    // A structural edit can move anything into a header row: then every header is checked.
    let changed: std::collections::BTreeSet<(usize, CellRef)> = if cx.structural {
        heads.iter().flat_map(|(s, r)| r.iter().map(move |c| (*s, c))).collect()
    } else {
        cx.changed.iter().filter(|(si, c)| heads.iter().any(|(s, r)| s == si && r.contains(*c))).copied().collect()
    };
    // Per table: lower-case column name → column, and the next number to try for a repeated name
    // (both kept up to date, so a wide header row stays linear).
    type Names = (std::collections::HashMap<String, usize>, std::collections::HashMap<String, usize>);
    let mut names: std::collections::HashMap<(usize, usize), Names> = std::collections::HashMap::new();
    for (si, c) in changed {
        let Some(sh) = cx.wb.sheet(si) else { continue };
        let Some(ti) = sh.tables.iter().position(|t| t.header_row && c.row == t.range.start.row && t.range.contains(c)) else { continue };
        let Some(t) = sh.tables.get(ti) else { continue };
        let col = (c.col - t.range.start.col) as usize;
        let Some(old) = t.columns.get(col).map(|x| x.name.clone()) else { continue };
        // A text header names its column as it is: a number format only changes how it looks (an
        // accounting format pads text with spaces), and the file writes the name into the cell.
        let value = sh.value(c);
        let text = match value.as_text() {
            Some(t) => t.to_string(),
            None => crate::display::cell_text(&cx.wb, sh, c),
        };
        // Headers hold constant text: a formula (or a spilled value) there becomes its text.
        let is_text = value.is_text() && sh.cell(c).is_some_and(|x| x.formula.is_none());
        if text == old && is_text {
            continue;
        }
        let (taken, next) = names
            .entry((si, ti))
            .or_insert_with(|| (t.columns.iter().enumerate().map(|(i, x)| (x.name.to_lowercase(), i)).collect(), Default::default()));
        if taken.get(&old.to_lowercase()) == Some(&col) {
            taken.remove(&old.to_lowercase());
        }
        let base = if text.trim().is_empty() { format!("Column{}", col + 1) } else { text.clone() };
        let k = next.entry(base.to_lowercase()).or_insert(2);
        let mut name = base.clone();
        while taken.contains_key(&name.to_lowercase()) {
            name = format!("{base}{k}");
            *k += 1;
        }
        taken.insert(name.to_lowercase(), col);
        let (table, range) = (t.name.clone(), t.range);
        let Some(sh) = cx.wb.sheet_mut(si) else { continue };
        if let Some(x) = sh.tables.get_mut(ti).and_then(|t| t.columns.get_mut(col)) {
            x.name = name.clone();
        }
        // Headers are text: a number typed there, or a generated name, is written back as text.
        if name != text || !is_text {
            let style = sh.style_id(c);
            sh.cells.set(c, Cell { value: gridcraft_core::Value::text(name.as_str()), style, ..Default::default() });
        }
        if !old.eq_ignore_ascii_case(&name) {
            renames.push((si, table, range, old, name));
        }
    }
    if renames.is_empty() {
        return;
    }
    // Structured references follow the new names.
    for i in 0..cx.wb.sheets.len() {
        let formulas: Vec<(CellRef, std::sync::Arc<Formula>)> =
            cx.wb.sheets.get(i).map(|s| s.cells.iter().filter_map(|(c, x)| x.formula.clone().map(|f| (c, f))).collect()).unwrap_or_default();
        for (c, f) in formulas {
            let Some(e) = f.expr() else { continue };
            let mut hit = false;
            let ne = e.map(&mut |x| match x {
                gridcraft_formula::Expr::Struct(mut st) => {
                    for (si, table, range, old, name) in &renames {
                        let this = if st.table.is_empty() { i == *si && range.contains(c) } else { st.table.eq_ignore_ascii_case(table) };
                        if this {
                            for end in [&mut st.col_start, &mut st.col_end].into_iter().flatten() {
                                if end.eq_ignore_ascii_case(old) {
                                    *end = name.clone();
                                    hit = true;
                                }
                            }
                        }
                    }
                    gridcraft_formula::Expr::Struct(st)
                }
                o => o,
            });
            if hit && let Some(cell) = cx.wb.sheet_mut(i).and_then(|s| s.cells.get_mut(c)) {
                cell.formula = Some(std::sync::Arc::new(Formula::from_expr(ne)));
            }
        }
    }
    cx.structural = true;
}

#[cfg(test)]
mod tests {
    use gridcraft_core::Value;
    use serde_json::json;

    use crate::Session;

    #[test]
    fn editing_a_header_renames_the_column() {
        let mut s = Session::new();
        s.new_workbook();
        s.execute("range.setValues", json!({"range": "A1", "values": [["a", "b", "c"], [1, 2, 3]]})).unwrap();
        s.execute("insert.table", json!({"range": "A1:C2", "header": true})).unwrap();
        s.execute("cell.set", json!({"cell": "E1", "input": "=SUM(Table1[a])"})).unwrap();
        s.execute("cell.set", json!({"cell": "A1", "input": "zz"})).unwrap();
        s.execute("edit.clearContents", json!({"range": "B1"})).unwrap();
        s.execute("cell.set", json!({"cell": "C1", "input": "ZZ"})).unwrap();
        let get = |s: &mut Session, a: &str| s.execute("cell.get", json!({"cell": a})).unwrap();
        assert_eq!(get(&mut s, "E1")["formula"], "=SUM(Table1[zz])");
        assert_eq!(get(&mut s, "E1")["value"], json!(1.0));
        let d = s.doc().unwrap();
        let sh = d.wb.sheet(0).unwrap();
        let names: Vec<&str> = sh.tables[0].columns.iter().map(|c| c.name.as_str()).collect();
        assert_eq!(names, ["zz", "Column2", "ZZ2"]);
        assert_eq!(sh.value(gridcraft_core::CellRef::new(0, 1)), Value::from("Column2"));
        assert_eq!(sh.value(gridcraft_core::CellRef::new(0, 2)), Value::from("ZZ2"));
        s.execute("range.setValues", json!({"range": "A1", "values": [["x", "x", 5]]})).unwrap();
        let sh = s.doc().unwrap().wb.sheet(0).unwrap();
        let names: Vec<&str> = sh.tables[0].columns.iter().map(|c| c.name.as_str()).collect();
        assert_eq!(names, ["x", "x2", "5"]);
        assert_eq!(sh.value(gridcraft_core::CellRef::new(0, 2)), Value::from("5"));
    }

    #[test]
    fn go_to_a_table_selects_its_data() {
        let mut s = Session::new();
        s.new_workbook();
        s.execute("range.setValues", json!({"range": "A1", "values": [["a", "b"], [1, 2], [3, 4]]})).unwrap();
        s.execute("insert.table", json!({"range": "A1:B3", "header": true})).unwrap();
        assert_eq!(s.execute("edit.goTo", json!({"reference": "Table1"})).unwrap()["selection"], "A2:B3");
    }
}

#[cfg(test)]
mod style_tests {
    use super::*;

    #[test]
    fn zero_numbered_styles_do_not_underflow() {
        assert_eq!(parse_style_name("TableStyleLight0"), ("Light", 1));
        assert_eq!(parse_style_name("TableStyleMedium0"), ("Medium", 1));
        for name in ["TableStyleLight0", "TableStyleMedium0", "TableStyleDark0"] {
            let _ = look(name);
        }
    }
}
