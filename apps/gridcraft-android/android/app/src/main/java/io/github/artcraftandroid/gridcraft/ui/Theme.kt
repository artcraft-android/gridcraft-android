package io.github.artcraftandroid.gridcraft.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The app's colours, light and dark (the system's theme picks). Greys and layout colours are
 * taken from the phone apps' shared design; the accent is GridCraft's own green (its app icon's)
 * and a lighter tint of it for dark, not the reference app's brand colours.
 */
object GridColors {
    /** The dark colours are in use ([GridcraftTheme] follows the system). */
    var dark by mutableStateOf(false)
        internal set

    private fun pick(light: Long, dark: Long) = Color(if (this.dark) dark else light)

    /** Tab and panel titles, their icons, the selection. */
    val Accent get() = pick(0xFF1F9D55, 0xFF5BC98C)

    /** Small coloured parts of icons (list bullets, the strip's more arrow). */
    val AccentLight get() = pick(0xFF2BB065, 0xFF5BC98C)
    val AccentDisabled get() = pick(0xFF94D2AF, 0xFF2F5A43)
    /** The band behind a chosen tile (Line Style, Cell Styles): GridCraft's green at full strength in both themes. */
    val ChosenBand get() = pick(0xFF1F9D55, 0xFF1F9D55)

    /** The tab list: its rows, the current tab's row, and their labels. */
    val PopupFill get() = pick(0xFF1F9D55, 0xFF212121)
    val PopupCurrent get() = pick(0xFFF3F3F3, 0xFF000000)
    val OnPopup = Color(0xFFFFFFFF)
    val OnPopupCurrent get() = pick(0xFF187A42, 0xFF5BC98C)

    /** The palette header's Tell Me, Undo and Redo. */
    val HeaderIcon get() = pick(0xFF1F9D55, 0xFFCACACA)

    /**
     * The palette header's tab-switch arrows, collapse arrow and Back: the accent in light; in
     * dark white, light grey and grey, as the phone app draws them.
     */
    val SwitchIcon get() = pick(0xFF1F9D55, 0xFFFFFFFF)
    val CollapseIcon get() = pick(0xFF187A42, 0xFFE8E8E8)
    val BackIcon get() = pick(0xFF1F9D55, 0xFF6E6E6E)
    val HeaderIconDisabled get() = pick(0xFF94D2AF, 0xFF575757)

    /** Bottom sheets (the header's overflow). */
    val Sheet get() = pick(0xFFFFFFFF, 0xFF1F1F1F)

    /** The floating toolbar's icons, and the circle behind a button that is on. */
    val OnPill get() = pick(0xFF242424, 0xFFFFFFFF)
    val PillOn get() = pick(0xFFDADADA, 0xFF575757)

    /** Bars at the top, the floating toolbar and pill. */
    val Bar get() = pick(0xFFFFFFFF, 0xFF292929)
    val BarEdge get() = pick(0xFFE4E4E4, 0xFF303030)

    /** The landing page: the account circle and its figure, the current bottom tab's pill. */
    val Avatar = Color(0xFFE6E6E6)
    val OnAvatar = Color(0xFF242424)
    val NavPill get() = pick(0xFFD3EBDD, 0xFF0E4A2B)

    /** Behind the status bar, the header, and the band the command bar floats on. */
    val StatusBand get() = pick(0xFFFFFFFF, 0xFF212121)

    /** The formula bar along the bottom, and the line under it (also the command bar's divider). */
    val FormulaFill get() = pick(0xFFFFFFFF, 0xFF000000)
    val BarLine get() = pick(0xFFE4E4E4, 0xFF4D4D4D)

    /** Text and icons on the bars and in the palette. */
    val OnBar get() = pick(0xFF505050, 0xFFE1E1E1)
    val Disabled get() = pick(0xFFB9B9B9, 0xFF666666)

    /** The palette's header. */
    val Strip get() = pick(0xFFE6E6E6, 0xFF212121)
    val OnStrip get() = pick(0xFF3A3A38, 0xFFD4D4D4)
    val StripMore get() = pick(0xFFD2D2D2, 0xFF303030)
    val HeaderDivider get() = pick(0xFFC5C5C5, 0xFF303030)

    /** The palette's lists. */
    val Surface get() = pick(0xFFFFFFFF, 0xFF141414)
    val OnSurface get() = pick(0xFF505050, 0xFFE1E1E1)
    val Divider get() = pick(0xFFF3F3F3, 0xFF292929)
    val Section get() = pick(0xFFE6E6E6, 0xFF404040)
    val Pressed get() = pick(0xFFD2D2D2, 0xFF303030)

    /**
     * The grid: cells, gridlines, the headers and their lines, a header in the selection. The
     * sheet stays light in the dark theme, as the phone app keeps it; only its headers go dark.
     */
    val Cells = Color(0xFFFFFFFF)
    val GridLine get() = pick(0xFFE1E1E1, 0xFFDADCDD)
    val HeaderFill get() = pick(0xFFF3F3F3, 0xFF000000)
    val HeaderLine get() = pick(0xFFD4D4D4, 0xFF212121)
    val OnHeader get() = pick(0xFF616161, 0xFF8E8E8E)
    val HeaderSelected get() = pick(0xFFD3EBDD, 0xFF212121)
    val OnHeaderSelected get() = pick(0xFF187A42, 0xFF8E8E8E)

    /** Text typed into a cell, and the selection's edge, handles and marks, on the light sheet. */
    val OnCells = Color(0xFF242424)
    val SheetAccent = Color(0xFF1F9D55)
    /** Number samples shown red (negative numbers in red), and the lines drawn on Line Style's white tiles. */
    val SampleRed = Color(0xFFE5483D)
    val LineInk = Color(0xFF3C3C3C)

    /** The outline band beside the headers, its level and −/+ buttons, and their digits and lines. */
    val OutlineBand get() = pick(0xFFFFFFFF, 0xFFF1F1F1)
    val OutlineButton = Color(0xFFD2D2D2)
    val OutlineInk get() = pick(0xFF616161, 0xFF333333)

    /** Sheet tabs: the aggregate bar's band; the sheets bar, its toggle's cell, the current tab and its label. */
    val TabBar get() = pick(0xFFF3F3F3, 0xFF1F1F1F)
    val SheetsBar get() = pick(0xFFF3F3F3, 0xFF141414)
    val SheetsToggle get() = pick(0xFFFFFFFF, 0xFF212121)
    val TabCurrent = Color(0xFFFFFFFF)
    val OnTabCurrent = Color(0xFF424242)

    /** A colour swatch's edge. */
    val SwatchEdge get() = pick(0xFFC8C8C8, 0xFF666666)

    /** The filter sheet: its handle, the boxes on it, the search field, the tabs and the chosen one. */
    val Handle get() = pick(0xFFC8C8C8, 0xFF404040)
    val Card get() = pick(0xFFF0F0F0, 0xFF404040)
    val CardSearch get() = pick(0xFFF5F5F5, 0xFF303030)
    val Segment get() = pick(0xFFE6E6E6, 0xFF3D3D3D)
    val SegmentOn = Color(0xFF1F9D55)
    /** The Shapes gallery's drawings: a shape's fill and outline (lines in the fill colour). */
    val ShapeTileFill get() = pick(0xFF2E5F80, 0xFF2E5F80)
    val ShapeTileLine get() = pick(0xFF0F2A3D, 0xFF0F2A3D)

    /** The Color tab's fill swatches: their frame, and the chosen one's (on white in both themes). */
    val FillFrame = Color(0xFFA9A9A9)
    val FillFrameChosen = Color(0xFF3A714A)

    /** Settings: the page behind them, section titles' rules. */
    val SettingsBack get() = pick(0xFFFFFFFF, 0xFF000000)
    val OnSettings get() = pick(0xFF242424, 0xFFE1E1E1)
    val FilteredRow get() = pick(0xFF1F5FC9, 0xFF6EA8FF)
    val SettingsValue get() = pick(0xFF616161, 0xFFACACAC)
    val PageBorder get() = pick(0xFFC6C6C6, 0xFF303030)

    /** Find: its bar, the field in it, the hint, and the matches (the selected one darker). */
    val FindBar get() = pick(0xFFE6E6E6, 0xFF303030)
    val FindField get() = pick(0xFFFFFFFF, 0xFF212121)
    val FindHint get() = pick(0xFF8A8A8A, 0xFFACACAC)
    val MatchCurrent get() = pick(0xFFC7BA64, 0xFF867836)
    val MatchOther get() = pick(0xFFFFEE80, 0xFF4D4A2A)

    /** Find settings: an unticked box, its edge, and the line under Replace. */
    val CheckOff get() = pick(0xFFFFFFFF, 0xFF6E6E6E)
    val CheckEdge get() = pick(0xFF8A8A8A, 0xFF666666)
    val SettingsLine get() = pick(0xFFC6C6C6, 0xFF969696)

    /** Over the pages behind a bottom sheet. */
    val Scrim = Color(0x52000000)

    val Selection = Color(0x261F9D55)
    val SwitcherDivider get() = pick(0xFFCDCDCD, 0xFF303030)
    val Caret get() = pick(0xFF000000, 0xFFFFFFFF)
    val FontColorDefault = Color(0xFFFF0000)
}

/** The app's theme: [choice] is `system` (the phone's), `light` or `dark`. */
@Composable
fun GridcraftTheme(choice: String = "system", content: @Composable () -> Unit) {
    val dark = when (choice) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    // set before anything below reads a colour
    if (GridColors.dark != dark) GridColors.dark = dark
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = GridColors.Accent, surface = GridColors.Surface, onSurface = GridColors.OnSurface)
        } else {
            lightColorScheme(primary = GridColors.Accent, surface = GridColors.Surface, onSurface = GridColors.OnSurface)
        },
    ) {
        // pressed controls turn grey, as in the reference app, rather than rippling; text is set
        // without the extra letter spacing Material adds
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides PressedIndication,
            androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.LocalTextStyle.current.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            content = content,
        )
    }
}
