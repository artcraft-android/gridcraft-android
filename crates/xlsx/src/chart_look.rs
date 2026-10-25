//! A chart's Style page style as DrawingML: the fills, lines and text properties the chart part
//! carries for each element, so other apps draw the chart the way the style looks.

use std::fmt::Write;

use gridcraft_model::chart_palette::{self, ColorMod};
use gridcraft_model::chart_style::{ChartStyle, Effect, Marker, Paint, Shade, Slot, Stroke, Type};

/// `shade` as a colour element; the series' own colour is `series` (a `schemeClr` or `srgbClr`).
fn color(shade: &Shade, series: &str) -> String {
    match shade.slot {
        Slot::Theme(i) => chart_palette::scheme_xml(i, shade.mods),
        Slot::Series => with_mods(series, shade.mods),
    }
}

/// A colour element with more changes put inside it.
fn with_mods(el: &str, mods: &[ColorMod]) -> String {
    if mods.is_empty() {
        return el.to_string();
    }
    let extra = chart_palette::scheme_xml(0, mods);
    let extra = extra.trim_start_matches("<a:schemeClr val=\"lt1\">").trim_end_matches("</a:schemeClr>");
    match el.strip_suffix("/>") {
        // `<a:schemeClr val="accent1"/>`
        Some(open) => {
            let tag = open.trim_start_matches('<').split(' ').next().unwrap_or("a:schemeClr");
            format!("{open}>{extra}</{tag}>")
        }
        // `<a:schemeClr val="accent1"><a:shade …/></a:schemeClr>`
        None => match el.rfind("</") {
            Some(at) => format!("{}{extra}{}", &el[..at], &el[at..]),
            None => el.to_string(),
        },
    }
}

/// A fill as DrawingML.
fn fill(paint: &Paint, series: &str) -> String {
    match paint {
        Paint::None => "<a:noFill/>".into(),
        Paint::Solid(c) => format!("<a:solidFill>{}</a:solidFill>", color(c, series)),
        Paint::Gradient { stops, from } => {
            let mut s = String::from("<a:gradFill rotWithShape=\"1\"><a:gsLst>");
            for (at, c) in *stops {
                let _ = write!(s, "<a:gs pos=\"{at}\">{}</a:gs>", color(c, series));
            }
            s.push_str("</a:gsLst>");
            match from {
                None => s.push_str("<a:lin ang=\"5400000\" scaled=\"0\"/>"),
                Some((x, y)) => {
                    let _ =
                        write!(s, "<a:path path=\"circle\"><a:fillToRect l=\"{x}\" t=\"{y}\" r=\"{}\" b=\"{}\"/></a:path>", 100_000 - x, 100_000 - y);
                }
            }
            s.push_str("</a:gradFill>");
            s
        }
        Paint::Leftward(stops) => {
            let mut s = String::from("<a:gradFill rotWithShape=\"1\"><a:gsLst>");
            for (at, c) in *stops {
                let _ = write!(s, "<a:gs pos=\"{at}\">{}</a:gs>", color(c, series));
            }
            s.push_str("</a:gsLst><a:lin ang=\"10800000\" scaled=\"0\"/></a:gradFill>");
            s
        }
        Paint::Pattern { preset, fg, bg } => format!(
            "<a:pattFill prst=\"{}\"><a:fgClr>{}</a:fgClr><a:bgClr>{}</a:bgClr></a:pattFill>",
            preset.preset(),
            color(fg, series),
            color(bg, series)
        ),
    }
}

fn line(stroke: Option<&Stroke>, series: &str) -> String {
    match stroke {
        Some(l) => format!("<a:ln w=\"{}\"><a:solidFill>{}</a:solidFill></a:ln>", (l.width * 12700.0).round() as i64, color(&l.color, series)),
        None => "<a:ln><a:noFill/></a:ln>".into(),
    }
}

/// A part's `c:spPr`: its fill, its line and its effect.
fn sp_pr(paint: &Paint, stroke: Option<&Stroke>, effect: Effect, series: &str) -> String {
    let fx = match effect {
        Effect::None => String::new(),
        Effect::InnerShadow => format!("<a:effectLst><a:innerShdw blurRad=\"114300\">{series}</a:innerShdw></a:effectLst>"),
        Effect::Glow => {
            format!("<a:effectLst><a:glow rad=\"63500\">{}</a:glow></a:effectLst>", with_mods(series, gridcraft_model::chart_style::GLOW.mods))
        }
        Effect::Shadow(blur) => format!(
            "<a:effectLst><a:outerShdw blurRad=\"{}\" sx=\"102000\" sy=\"102000\" algn=\"ctr\" rotWithShape=\"0\"><a:prstClr val=\"black\"><a:alpha val=\"20000\"/></a:prstClr></a:outerShdw></a:effectLst>",
            i64::from(blur) * 12700
        ),
    };
    format!("<c:spPr>{}{}{fx}</c:spPr>", fill(paint, series), line(stroke, series))
}

/// Run properties for text in `ty`.
pub(crate) fn def_rpr(ty: &Type) -> String {
    let mut a = format!("sz=\"{}\" b=\"{}\"", (ty.size * 100.0).round() as i64, u8::from(ty.bold));
    if ty.caps {
        a.push_str(" cap=\"all\"");
    }
    if ty.spacing != 0 {
        let _ = write!(a, " spc=\"{}\"", ty.spacing);
    }
    format!("<a:defRPr {a}><a:solidFill>{}</a:solidFill></a:defRPr>", color(&ty.color, ""))
}

/// A part's `c:txPr` for text in `ty`, turned a quarter turn when `turned`.
fn tx_pr(ty: &Type, turned: bool) -> String {
    let rot = if turned { " rot=\"-5400000\" vert=\"horz\"" } else { "" };
    format!("<c:txPr><a:bodyPr{rot}/><a:lstStyle/><a:p><a:pPr>{}</a:pPr><a:endParaRPr lang=\"en-US\"/></a:p></c:txPr>", def_rpr(ty))
}

/// The axis labels' and legend's type: 9 pt in the style's text colour.
fn body(st: &ChartStyle) -> Type {
    Type { size: 9.0, bold: false, caps: false, spacing: 0, color: st.text }
}

/// The chart area's spPr and txPr, after `c:chart`.
pub(crate) fn area(st: &ChartStyle) -> String {
    let edge = st.area_line.map(|c| Stroke { color: c, width: 0.75 });
    format!("{}{}", sp_pr(&st.area, edge.as_ref(), Effect::None, ""), tx_pr(&body(st), false))
}

/// The plot area's spPr, last in `c:plotArea`.
pub(crate) fn plot(st: &ChartStyle) -> String {
    sp_pr(&st.plot, None, Effect::None, "")
}

/// A series' spPr, its colour `series`.
pub(crate) fn series(st: &ChartStyle, series: &str) -> String {
    sp_pr(&st.series, st.series_line.as_ref(), st.effect, series)
}

/// A line series' spPr in the style: its line's width, colour and compound, its glow, the
/// series' colour `series`.
pub(crate) fn line_series(st: &ChartStyle, series: &str) -> String {
    let (cap, cmpd) = if st.line_double { ("flat", " cmpd=\"dbl\"") } else { ("rnd", "") };
    let fx = if st.effect == Effect::Glow {
        format!("<a:effectLst><a:glow rad=\"63500\">{}</a:glow></a:effectLst>", with_mods(series, gridcraft_model::chart_style::GLOW.mods))
    } else {
        String::new()
    };
    let dots = if st.line_dots { "<a:prstDash val=\"sysDot\"/>" } else { "" };
    format!(
        "<c:spPr><a:ln w=\"{}\" cap=\"{cap}\"{cmpd}><a:solidFill>{}</a:solidFill>{dots}<a:round/></a:ln>{fx}</c:spPr>",
        (st.line.width * 12700.0).round() as i64,
        color(&st.line.color, series)
    )
}

/// A style's markers on a line, the series' colour `series`.
pub(crate) fn marker(mk: &Marker, series: &str) -> String {
    let edge = line(mk.edge.as_ref(), series);
    format!(
        "<c:marker><c:symbol val=\"{}\"/><c:size val=\"{}\"/><c:spPr><a:solidFill>{}</a:solidFill>{edge}</c:spPr></c:marker>",
        if mk.diamond { "diamond" } else { "circle" },
        mk.size.clamp(2, 72),
        color(&mk.fill, series)
    )
}

/// Data labels' spPr and txPr (before `c:dLblPos`).
pub(crate) fn labels(st: &ChartStyle) -> String {
    let fill = st.label_box.map_or_else(|| "<a:noFill/>".to_string(), |c| format!("<a:solidFill>{}</a:solidFill>", color(&c, "")));
    format!("<c:spPr>{fill}<a:ln><a:noFill/></a:ln></c:spPr>{}", tx_pr(&st.labels, st.label_turn))
}

/// Gridlines' spPr: major or minor.
pub(crate) fn gridlines(st: &ChartStyle, minor: bool) -> String {
    let c = if minor { st.minor_grid } else { st.grid };
    sp_pr(&Paint::None, Some(&Stroke { color: c, width: 0.75 }), Effect::None, "")
}

/// An axis's spPr (its line: the category axis's, none for the value axis) and txPr; the
/// category labels in capitals with the style's spacing.
pub(crate) fn axis(st: &ChartStyle, category: bool) -> String {
    let line = if category { st.axis_line } else { None };
    let mut ty = body(st);
    if category {
        ty.size = st.category_size;
        if let Some(spc) = st.category_caps {
            ty.caps = true;
            ty.spacing = spc;
        }
    }
    format!("{}{}", sp_pr(&Paint::None, line.as_ref(), Effect::None, ""), tx_pr(&ty, false))
}

/// The legend's spPr and txPr.
pub(crate) fn legend(st: &ChartStyle) -> String {
    format!("{}{}", sp_pr(&st.legend_fill, None, Effect::None, ""), tx_pr(&body(st), false))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn changes_go_inside_the_series_colour() {
        let a = "<a:schemeClr val=\"accent1\"/>";
        assert_eq!(with_mods(a, &[ColorMod::Alpha(70000)]), "<a:schemeClr val=\"accent1\"><a:alpha val=\"70000\"/></a:schemeClr>");
        let b = "<a:schemeClr val=\"accent1\"><a:shade val=\"65000\"/></a:schemeClr>";
        assert_eq!(
            with_mods(b, &[ColorMod::Alpha(70000)]),
            "<a:schemeClr val=\"accent1\"><a:shade val=\"65000\"/><a:alpha val=\"70000\"/></a:schemeClr>"
        );
        assert_eq!(with_mods("<a:srgbClr val=\"FF0000\"/>", &[]), "<a:srgbClr val=\"FF0000\"/>");
    }
}
