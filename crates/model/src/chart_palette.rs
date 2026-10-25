//! The chart Color page's palettes: the theme colours a chart's series take one after another,
//! and how each is made lighter or darker (DrawingML's `lumMod`/`lumOff`, `tint` and `shade`).
//! Palettes are known by the numbers files give them (`cs:colorStyle id`).

/// A change to a theme colour, in thousandths of a percent (100000: all of it).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ColorMod {
    /// Luminance times this.
    LumMod(u32),
    /// Luminance plus this.
    LumOff(u32),
    /// This much of the colour, the rest white.
    Tint(u32),
    /// This much of the colour, the rest black.
    Shade(u32),
}

/// The Color page in order: Colorful 1-4, then Monochromatic 1-13, as (id, name shown).
pub const PALETTES: [(u16, &str); 17] = [
    (10, "Colorful Palette 1"),
    (11, "Colorful Palette 2"),
    (12, "Colorful Palette 3"),
    (13, "Colorful Palette 4"),
    (14, "Monochromatic Palette 1"),
    (15, "Monochromatic Palette 2"),
    (16, "Monochromatic Palette 3"),
    (17, "Monochromatic Palette 4"),
    (18, "Monochromatic Palette 5"),
    (19, "Monochromatic Palette 6"),
    (20, "Monochromatic Palette 7"),
    (21, "Monochromatic Palette 8"),
    (22, "Monochromatic Palette 9"),
    (23, "Monochromatic Palette 10"),
    (24, "Monochromatic Palette 11"),
    (25, "Monochromatic Palette 12"),
    (26, "Monochromatic Palette 13"),
];

/// The automatic palette, which a chart without one uses.
pub const AUTOMATIC: u16 = 10;

/// Theme slots (`Theme::colors`): the text colour and the accents.
const DK1: usize = 1;
const ACCENT1: usize = 4;

/// How the colourful palettes vary their colours once each has been used: as they are, then
/// darker and lighter in turn.
const COLORFUL_VARIATIONS: [&[ColorMod]; 9] = [
    &[],
    &[ColorMod::LumMod(60000)],
    &[ColorMod::LumMod(80000), ColorMod::LumOff(20000)],
    &[ColorMod::LumMod(80000)],
    &[ColorMod::LumMod(60000), ColorMod::LumOff(40000)],
    &[ColorMod::LumMod(50000)],
    &[ColorMod::LumMod(70000), ColorMod::LumOff(30000)],
    &[ColorMod::LumMod(70000)],
    &[ColorMod::LumMod(50000), ColorMod::LumOff(50000)],
];

/// The grey palette: the text colour lightened by these, in turn.
const GREY_TINTS: [u32; 7] = [88500, 55000, 75000, 98500, 30000, 60000, 80000];

/// Series `i` of `n` in palette `id`: its theme slot and the changes to it; `None` for a palette
/// that isn't one of [`PALETTES`].
pub fn series_color(id: u16, i: usize, n: usize) -> Option<(usize, Vec<ColorMod>)> {
    let cycle = |slots: &[usize]| {
        let k = slots.len();
        (slots[i % k], COLORFUL_VARIATIONS[(i / k) % COLORFUL_VARIATIONS.len()].to_vec())
    };
    Some(match id {
        10 => cycle(&[4, 5, 6, 7, 8, 9]),
        11 => cycle(&[4, 6, 8]),
        12 => cycle(&[5, 7, 9]),
        13 => cycle(&[9, 8, 7]),
        14..=19 => (ACCENT1 + usize::from(id - 14), within(i, n, false)),
        20 => (DK1, vec![ColorMod::Tint(GREY_TINTS[i % GREY_TINTS.len()])]),
        21..=26 => (ACCENT1 + usize::from(id - 21), within(i, n, true)),
        _ => return None,
    })
}

/// A monochromatic palette's series spread from half shade to half tint (light to dark when
/// `reversed`): six series take shade 50%, 70%, 90%, tint 90%, 70%, 50%.
fn within(i: usize, n: usize, reversed: bool) -> Vec<ColorMod> {
    if n < 2 {
        return vec![];
    }
    let i = i.min(n - 1);
    let k = if reversed { n - 1 - i } else { i };
    // -0.5 (darkest) to +0.5 (lightest), in thousandths of a percent
    let v = -50_000 + (100_000 * k as i64) / (n as i64 - 1);
    match v {
        0 => vec![],
        v if v < 0 => vec![ColorMod::Shade((100_000 + v) as u32)],
        v => vec![ColorMod::Tint((100_000 - v) as u32)],
    }
}

/// `rgb` with `mods` applied: luminance changes in HSL, tint and shade in linear light.
pub fn apply(rgb: [u8; 3], mods: &[ColorMod]) -> [u8; 3] {
    let mut c = rgb;
    for m in mods {
        c = match *m {
            ColorMod::LumMod(v) => crate::style::with_luminance(c, |l| l * f64::from(v) / 100_000.0),
            ColorMod::LumOff(v) => crate::style::with_luminance(c, |l| l + f64::from(v) / 100_000.0),
            ColorMod::Tint(v) => linear_mix(c, f64::from(v) / 100_000.0, 1.0),
            ColorMod::Shade(v) => linear_mix(c, f64::from(v) / 100_000.0, 0.0),
        };
    }
    c
}

/// `amount` of the colour, the rest `to` (white 1, black 0), mixed in linear light.
fn linear_mix(rgb: [u8; 3], amount: f64, to: f64) -> [u8; 3] {
    let amount = amount.clamp(0.0, 1.0);
    rgb.map(|v| {
        let s = f64::from(v) / 255.0;
        let lin = if s <= 0.04045 { s / 12.92 } else { ((s + 0.055) / 1.055).powf(2.4) };
        let mixed = lin * amount + to * (1.0 - amount);
        let s = if mixed <= 0.003_130_8 { mixed * 12.92 } else { 1.055 * mixed.powf(1.0 / 2.4) - 0.055 };
        (s.clamp(0.0, 1.0) * 255.0).round() as u8
    })
}

/// DrawingML for a series colour: the theme colour named as charts name it, with its changes.
pub fn scheme_xml(slot: usize, mods: &[ColorMod]) -> String {
    let name = match slot {
        0 => "lt1".to_string(),
        1 => "dk1".to_string(),
        2 => "lt2".to_string(),
        3 => "dk2".to_string(),
        s => format!("accent{}", s.saturating_sub(3).min(6)),
    };
    if mods.is_empty() {
        return format!("<a:schemeClr val=\"{name}\"/>");
    }
    let mut s = format!("<a:schemeClr val=\"{name}\">");
    for m in mods {
        let (tag, v) = match *m {
            ColorMod::LumMod(v) => ("lumMod", v),
            ColorMod::LumOff(v) => ("lumOff", v),
            ColorMod::Tint(v) => ("tint", v),
            ColorMod::Shade(v) => ("shade", v),
        };
        s.push_str(&format!("<a:{tag} val=\"{v}\"/>"));
    }
    s.push_str("</a:schemeClr>");
    s
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn six_series_take_the_saved_colours() {
        // as the reference saves each palette for a chart of six series
        let mods = |id, i| series_color(id, i, 6).unwrap();
        assert_eq!(mods(11, 3), (4, vec![ColorMod::LumMod(60000)]));
        assert_eq!(mods(12, 0), (5, vec![]));
        assert_eq!(mods(13, 5), (7, vec![ColorMod::LumMod(60000)]));
        assert_eq!(
            (0..6).map(|i| mods(14, i).1).collect::<Vec<_>>(),
            [
                vec![ColorMod::Shade(50000)],
                vec![ColorMod::Shade(70000)],
                vec![ColorMod::Shade(90000)],
                vec![ColorMod::Tint(90000)],
                vec![ColorMod::Tint(70000)],
                vec![ColorMod::Tint(50000)],
            ]
        );
        assert_eq!(mods(26, 0), (9, vec![ColorMod::Tint(50000)]));
        assert_eq!(mods(20, 3), (1, vec![ColorMod::Tint(98500)]));
        assert_eq!(series_color(10, 7, 12).unwrap(), (5, vec![ColorMod::LumMod(60000)]));
        assert!(series_color(9, 0, 1).is_none());
    }

    #[test]
    fn tint_and_shade_mix_in_linear_light() {
        assert_eq!(apply([0, 0, 0], &[ColorMod::Tint(50000)]), [188, 188, 188]);
        assert_eq!(apply([255, 255, 255], &[ColorMod::Shade(50000)]), [188, 188, 188]);
        assert_eq!(apply([10, 20, 30], &[]), [10, 20, 30]);
        assert_eq!(scheme_xml(4, &[ColorMod::Shade(50000)]), "<a:schemeClr val=\"accent1\"><a:shade val=\"50000\"/></a:schemeClr>");
    }
}
