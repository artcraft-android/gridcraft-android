package io.github.artcraftandroid.gridcraft.ui.editor

import io.github.artcraftandroid.gridcraft.ui.uiText
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.core.Swatch
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons
import io.github.artcraftandroid.gridcraft.ui.Motion
import kotlin.math.roundToInt
import org.json.JSONObject

/** The palette's height, its rows (47 dp and a 1 dp line), list rows and group bands. */
val PALETTE_HEIGHT = 419.dp
internal val ROW = 47.dp
internal val BAND = 8.dp

/** The tabs the palette offers, in order. */
val PALETTE_TABS = listOf("Home", "Insert", "Draw", "Formulas", "Data", "Review", "View")

/**
 * The expanded command surface: a tab of commands, or one of their pages. A page comes in from
 * the side as the one it leaves slides a little the other way and fades; another tab's commands
 * come up from below as the last tab's go up.
 */
@Composable
fun PaletteSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // each tab's scroll, kept while its pages are open: Back comes back to the same place
    val tabScrolls = androidx.compose.runtime.remember { mutableMapOf<String, androidx.compose.foundation.ScrollState>() }
    AnimatedContent(
        targetState = vm.panels,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            // deeper comes in from the end side; back, from the start side
            val dir = (if (targetState.size >= initialState.size) 1 else -1) * (if (rtl) -1 else 1)
            val inSpec = tween<IntOffset>(Motion.BASIC, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp)
            val outSpec = tween<IntOffset>(Motion.QUICK, easing = Motion.EaseInSharp)
            (slideInHorizontally(inSpec) { (60 * density).roundToInt() * dir } + fadeIn(tween(Motion.BASIC, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp))) togetherWith
                (slideOutHorizontally(outSpec) { -(40 * density).roundToInt() * dir } + fadeOut(tween(Motion.QUICK, easing = Motion.EaseInSharp)))
        },
        label = "palettePages",
    ) { panels ->
        val panel = panels.lastOrNull()
        Column(Modifier.fillMaxSize().background(GridColors.Surface)) {
            if (panel == null && vm.tellMeOpen) {
                TellMeSheet(vm, Modifier.weight(1f))
            } else if (panel == null) {
                TabHeader(vm, vm.paletteTab)
                AnimatedContent(
                    targetState = vm.paletteTab,
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                    transitionSpec = {
                        (slideInVertically(tween(Motion.SHORT, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp)) { TAB_IN_PX } + fadeIn(tween(Motion.SHORT, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp))) togetherWith
                            (slideOutVertically(tween(Motion.SHORT, easing = Motion.EaseInSharp)) { -TAB_OUT_PX } + fadeOut(tween(Motion.SHORT, easing = Motion.EaseInSharp)))
                    },
                    label = "paletteTabs",
                ) { tab ->
                    val scroll = tabScrolls.getOrPut(tab) { androidx.compose.foundation.ScrollState(0) }
                    Box(Modifier.fillMaxSize().background(GridColors.Surface)) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalTabScroll provides scroll) { if (!ContextRows(vm, tab)) TabRows(vm, tab) }
                    }
                }
            } else if (vm.pageAlone && panels.size == 1) {
                PageHeader(vm, panelTitle(panel))
                Box(Modifier.weight(1f).fillMaxWidth()) { PanelBody(vm, panel) }
            } else {
                PanelHeader(panelTitle(panel), onBack = vm::popPanel, onClose = vm::collapsePalette)
                Box(Modifier.weight(1f).fillMaxWidth()) { PanelBody(vm, panel) }
            }
        }
    }
}

/** A page's title: its name, or a function's own for its help page. */
private fun panelTitle(panel: String): String = PANEL_TITLES[panel] ?: panel.removePrefix(FN_HELP).removePrefix(CHART_FAMILY)

/** How far another tab's commands come up from, and how far the last tab's go up (pixels). */
private const val TAB_IN_PX = 200
private const val TAB_OUT_PX = 187

/** Puts a popup at its anchor's start, centred on it from top to bottom (kept on screen). */
private object CentredOnAnchor : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): IntOffset {
        val y = anchorBounds.center.y - popupContentSize.height / 2
        return IntOffset(anchorBounds.left, y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height)))
    }
}

/** The tab switcher's list: every tab (and the selected object's), the current one marked. */
@Composable
fun TabSwitcher(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier.width(IntrinsicSize.Max).widthIn(min = 112.dp).shadow(6.dp)) {
        for (tab in PALETTE_TABS + listOfNotNull(vm.contextTab)) {
            val current = tab == vm.paletteTab
            Box(
                Modifier.fillMaxWidth().height(ROW).background(if (current) GridColors.PopupCurrent else GridColors.PopupFill).clickable { vm.showTab(tab) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(uiText(tab), color = if (current) GridColors.OnPopupCurrent else GridColors.OnPopup, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
            }
        }
    }
}

@Composable
private fun TabHeader(vm: AppViewModel, tab: String) {
    val info = vm.info
    Row(
        Modifier.fillMaxWidth().height(ROW).background(GridColors.Strip).topEdge(GridColors.StripMore).bottomEdge(GridColors.StripMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(Modifier.widthIn(min = 112.dp).fillMaxHeight().clickable { vm.toggleTabs() }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText(tab), color = GridColors.Accent, fontSize = 18.sp, maxLines = 1)
                Spacer(Modifier.width(12.dp).weight(1f, fill = false))
                GlyphIcon(LucideIcons.ChevronsUpDown.glyph(), GridColors.SwitchIcon, size = 20.dp)
                Spacer(Modifier.width(10.dp))
            }
            // the tab list opens centred on this button, over the grid and the palette alike
            if (vm.tabsOpen) {
                androidx.compose.ui.window.Popup(
                    popupPositionProvider = CentredOnAnchor,
                    onDismissRequest = { vm.tabsOpen = false },
                    properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                ) { TabSwitcher(vm) }
            }
        }
        Box(Modifier.width(1.dp).height(32.dp).background(GridColors.SwitcherDivider))
        Spacer(Modifier.weight(1f))
        BarButton(LucideIcons.Lightbulb.glyph(), GridColors.HeaderIcon, { vm.tellMeOpen = true }, width = 48.dp, height = ROW, label = "Tell me")
        BarButton(LucideIcons.Undo2.glyph(), GridColors.HeaderIcon, vm::undo, enabled = info?.canUndo == true, width = 48.dp, height = ROW, disabledTint = GridColors.HeaderIconDisabled, label = "Undo")
        BarButton(LucideIcons.Redo2.glyph(), GridColors.HeaderIcon, vm::redo, enabled = info?.canRedo == true, width = 48.dp, height = ROW, disabledTint = GridColors.HeaderIconDisabled, label = "Redo")
        BarButton(Glyph.Triangle(up = false), GridColors.CollapseIcon, vm::collapsePalette, width = 43.dp, height = ROW, label = "Collapse more options")
    }
}

/** A page opened on its own: its title where the tab's name goes (no Back), then Tell Me, Undo, Redo and collapse. */
@Composable
private fun PageHeader(vm: AppViewModel, title: String) {
    val info = vm.info
    Row(
        Modifier.fillMaxWidth().height(ROW).background(GridColors.Strip).topEdge(GridColors.StripMore).bottomEdge(GridColors.StripMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(uiText(title), color = GridColors.Accent, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 16.dp))
        BarButton(LucideIcons.Lightbulb.glyph(), GridColors.HeaderIcon, vm::tellMeFromPage, width = 48.dp, height = ROW, label = "Tell me")
        BarButton(LucideIcons.Undo2.glyph(), GridColors.HeaderIcon, vm::undo, enabled = info?.canUndo == true, width = 48.dp, height = ROW, disabledTint = GridColors.HeaderIconDisabled, label = "Undo")
        BarButton(LucideIcons.Redo2.glyph(), GridColors.HeaderIcon, vm::redo, enabled = info?.canRedo == true, width = 48.dp, height = ROW, disabledTint = GridColors.HeaderIconDisabled, label = "Redo")
        BarButton(Glyph.Triangle(up = false), GridColors.CollapseIcon, vm::collapsePalette, width = 43.dp, height = ROW, label = "Collapse more options")
    }
}

@Composable
private fun PanelHeader(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW).background(GridColors.Strip).topEdge(GridColors.StripMore).bottomEdge(GridColors.StripMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(LucideIcons.ChevronLeft.glyph(), GridColors.BackIcon, onBack, width = 56.dp, height = ROW, label = "Back")
        Box(Modifier.width(1.dp).height(32.dp).background(GridColors.SwitcherDivider))
        Text(uiText(title), color = GridColors.Accent, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 16.dp))
        BarButton(Glyph.Triangle(up = false), GridColors.CollapseIcon, onClose, width = 43.dp, height = ROW, label = "Collapse more options")
    }
}

@Composable
internal fun Band() {
    Spacer(Modifier.fillMaxWidth().height(BAND).background(GridColors.Divider))
}

/**
 * A row of the palette: an icon at 16 dp, a label at 50 dp (at 16 dp with no [inset], as in the
 * function lists), a chevron when it drills in.
 */
@Composable
internal fun PaletteRow(
    label: String,
    glyph: Glyph?,
    onClick: () -> Unit,
    chevron: Boolean = false,
    pressed: Boolean = false,
    onOpen: (() -> Unit)? = null,
    iconTint: Color = GridColors.OnStrip,
    height: Dp = ROW,
    enabled: Boolean = true,
    trailing: String? = null,
    inset: Boolean = true,
) {
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) LucideIcons.ChevronLeft else LucideIcons.ChevronRight
    Row(Modifier.fillMaxWidth().height(height + 1.dp).background(if (pressed) GridColors.Pressed else GridColors.Surface).bottomEdge(GridColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(16.dp))
            if (inset || glyph != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { if (glyph != null) GlyphIcon(glyph, if (enabled) iconTint else GridColors.Disabled) }
                Spacer(Modifier.width(10.dp))
            }
            Text(uiText(label), color = if (enabled) GridColors.OnSurface else GridColors.Disabled, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(uiText(trailing), color = GridColors.OnStrip, fontSize = 15.sp, maxLines = 1)
                Spacer(Modifier.width(16.dp))
            }
            if (chevron && onOpen == null) {
                GlyphIcon(forward.glyph(), if (enabled) GridColors.OnStrip else GridColors.Disabled, size = 20.dp)
                Spacer(Modifier.width(15.dp))
            }
        }
        if (onOpen != null) {
            Box(Modifier.width(1.dp).height(25.dp).background(GridColors.Divider))
            Box(Modifier.width(50.dp).fillMaxHeight().clickable(enabled = enabled, onClick = onOpen), contentAlignment = Alignment.Center) {
                GlyphIcon(forward.glyph(), if (enabled) GridColors.OnStrip else GridColors.Disabled, size = 20.dp)
            }
        }
    }
}

/** A row of equal cells (the toggles, the alignments). */
@Composable
internal fun CellRow(vararg cells: Triple<Glyph, Boolean, () -> Unit>) {
    Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(GridColors.Divider)) {
        for ((glyph, on, click) in cells) {
            Box(Modifier.weight(1f).height(ROW).background(if (on) GridColors.Pressed else GridColors.Surface).clickable(onClick = click), contentAlignment = Alignment.Center) {
                GlyphIcon(glyph, GridColors.OnStrip, size = 26.dp)
            }
        }
    }
}

/** The scroll of the tab being shown, which its list keeps (pages scroll on their own). */
internal val LocalTabScroll = androidx.compose.runtime.compositionLocalOf<androidx.compose.foundation.ScrollState?> { null }

@Composable
internal fun Scrolling(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(LocalTabScroll.current ?: rememberScrollState()), content = content)
}

internal fun json(vararg pairs: Pair<String, Any>): String = JSONObject().apply { for ((k, v) in pairs) put(k, v) }.toString()

@Composable
internal fun SectionBand(title: String) {
    Box(Modifier.fillMaxWidth().height(26.dp).background(GridColors.Section).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
        Text(uiText(title), color = GridColors.OnSurface, fontSize = 14.sp)
    }
}

/** A row of a list page: a tick when it is the current choice, then its label. */
@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, trailing: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(ROW + 1.dp).background(if (selected) GridColors.Pressed else GridColors.Surface).bottomEdge(GridColors.Divider).clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(43.dp), contentAlignment = Alignment.Center) { if (selected) GlyphIcon(LucideIcons.Check.glyph(), GridColors.Accent, size = 20.dp) }
        Text(uiText(label), color = if (enabled) GridColors.OnSurface else GridColors.Disabled, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) Text(uiText(trailing), color = GridColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.padding(end = 16.dp))
    }
}

/** A grid of colour swatches, [perRow] across the width with hairline gaps, each with a thin edge, as the phone app draws them. */
@Composable
internal fun SwatchGrid(swatches: List<Swatch>, perRow: Int, @Suppress("UNUSED_PARAMETER") cellW: Dp, onPick: (Swatch) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
        for (row in swatches.chunked(perRow)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)) {
                for (s in row) {
                    Box(Modifier.weight(1f).height(40.dp).background(Color(s.argb)).border(1.dp, GridColors.SwatchEdge).clickable { onPick(s) })
                }
                // a short last row keeps the cells' width
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The colour grid for fills, font colours and lines: the theme's shades, the standard colours,
 * then [none]. [onPick] hears how the picked colour looks (null for [none]).
 */
@Composable
internal fun ColorGridPanel(vm: AppViewModel, none: String, onPick: (Int?) -> Unit = {}, apply: (String) -> Unit) {
    val choices = vm.colors ?: return
    Scrolling {
        SectionBand("Theme Colors")
        SwatchGrid(choices.theme.flatten(), perRow = 10, cellW = 39.dp) {
            onPick(it.argb)
            apply(it.color)
        }
        SectionBand("Standard Colors")
        SwatchGrid(choices.standard, perRow = 10, cellW = 39.dp) {
            onPick(it.argb)
            apply(it.color)
        }
        // No Fill or No Color: a white swatch before it; Automatic: a black one, as the phone app shows them
        val automatic = none == "Automatic"
        Row(
            Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(GridColors.Divider).clickable {
                onPick(null)
                apply(if (automatic) "\"auto\"" else "\"none\"")
            }.padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp).background(if (automatic) Color.Black else Color.White).border(1.dp, GridColors.SwatchEdge))
            Spacer(Modifier.width(10.dp))
            Text(uiText(none), color = GridColors.OnSurface, fontSize = 16.sp)
        }
    }
}
