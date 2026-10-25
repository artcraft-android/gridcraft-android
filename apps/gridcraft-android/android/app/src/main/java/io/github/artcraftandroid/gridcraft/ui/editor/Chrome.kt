package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons

/** A tappable icon of the bars, named [label] for screen readers. */
@Composable
fun BarButton(
    glyph: Glyph,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 42.dp,
    height: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    pressed: Boolean = false,
    disabledTint: Color = GridColors.Disabled,
    label: String? = null,
) {
    Box(
        modifier
            .size(width, height)
            .background(if (pressed) GridColors.Pressed else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            // what a screen reader says for it, the phone app's name for the button
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, if (enabled) tint else disabledTint, size = iconSize)
    }
}

/** A one-pixel line along the bottom edge. */
fun Modifier.bottomEdge(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
}

/** A one-pixel line along the top edge. */
fun Modifier.topEdge(color: Color): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
}

/** The bars' height, and their buttons' width. */
private val BAR_HEIGHT = 56.dp
private val BAR_BUTTON = 48.dp

/** Editing: close, the workbook's name, Undo (while there is something to undo), Share, and ⋯ (its sheet: [MoreSheet]). */
@Composable
fun EditorHeader(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth().height(BAR_HEIGHT).background(GridColors.StatusBand).bottomEdge(GridColors.BarEdge), verticalAlignment = Alignment.CenterVertically) {
        BarButton(LucideIcons.X.glyph(), GridColors.OnBar, vm::closeWorkbook, width = BAR_BUTTON, height = BAR_HEIGHT, label = "Back")
        Text(vm.fileName, color = GridColors.OnBar, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        // Undo shows while there is something to undo, as the phone app's header has it
        if (vm.info?.canUndo == true) BarButton(LucideIcons.Undo2.glyph(), GridColors.OnBar, vm::undo, width = BAR_BUTTON, height = BAR_HEIGHT, label = "Undo")
        Box {
            var share by remember { mutableStateOf(false) }
            BarButton(LucideIcons.Share2.glyph(), GridColors.OnBar, { share = true }, width = BAR_BUTTON, height = BAR_HEIGHT, label = "Share")
            DropdownMenu(share, onDismissRequest = { share = false }) {
                DropdownMenuItem(text = { Text("Share Workbook") }, onClick = {
                    share = false
                    vm.share("xlsx")
                })
                DropdownMenuItem(text = { Text("Share as PDF") }, onClick = {
                    share = false
                    vm.share("pdf")
                })
            }
        }
        BarButton(LucideIcons.Ellipsis.glyph(), GridColors.OnBar, { vm.moreOpen = true }, width = BAR_BUTTON, height = BAR_HEIGHT, label = "More Options")
    }
}

/** The header's ⋯: a sheet from the bottom with Draw with Pen, Share as PDF, Save, Save As, Print, then Settings. */
@Composable
fun MoreSheet(vm: AppViewModel) {
    val top = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    @Composable
    fun item(label: String, glyph: Glyph, action: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).clickable {
                vm.moreOpen = false
                action()
            }.padding(start = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(glyph, GridColors.OnSettings, size = 24.dp)
            Spacer(Modifier.width(29.dp))
            Text(label, color = GridColors.OnSettings, fontSize = 16.sp)
        }
    }
    Column(Modifier.fillMaxWidth().shadow(8.dp, top).background(GridColors.Sheet, top)) {
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 4.dp).background(GridColors.Handle, RoundedCornerShape(2.dp)))
        }
        item("Draw with Pen", LucideIcons.PenTool.glyph(), vm::drawWithPen)
        item("Share as PDF", LucideIcons.FileText.glyph()) { vm.share("pdf") }
        item("Save", LucideIcons.Save.glyph(), vm::save)
        item("Save As", LucideIcons.FilePenLine.glyph(), vm::saveCopy)
        item("Print", LucideIcons.Printer.glyph()) { vm.dialog = "print" }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GridColors.Divider))
        item("Settings", LucideIcons.Settings.glyph(), vm::openSettings)
    }
}

/**
 * The formula bar, along the bottom over the command bar: fx, then what the active cell holds. A
 * tap edits it there; while editing, a cross cancels and a tick enters (the keyboard's Enter
 * enters and moves down). The arrow shows more lines, upwards.
 */
@Composable
fun FormulaBar(vm: AppViewModel) {
    val sel = vm.selection
    val focus = remember { FocusRequester() }
    val expanded = vm.formulaExpanded
    val lines = if (expanded) 6 else 1
    Row(
        Modifier.fillMaxWidth().then(if (expanded) Modifier.heightIn(min = 40.dp, max = 160.dp) else Modifier.height(40.dp)).background(GridColors.FormulaFill).topEdge(GridColors.BarEdge).bottomEdge(GridColors.BarLine),
        verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically,
    ) {
        // fx: a formula, its function picked from the Formulas tab's categories
        Text(
            "fx",
            color = GridColors.OnBar,
            fontSize = 19.sp,
            fontStyle = FontStyle.Italic,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(48.dp).height(40.dp).clickable(enabled = vm.selectedObject == null) { vm.insertFunction() }.wrapContentHeight(),
        )
        Box(Modifier.width(1.dp).height(40.dp).background(GridColors.BarEdge))
        Spacer(Modifier.width(10.dp))
        if (vm.editing && vm.keypad) {
            // the number keypad types here: the text with a caret at its end
            Row(Modifier.weight(1f).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(vm.editText, color = GridColors.OnSettings, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.width(2.dp).height(22.dp).background(GridColors.Accent))
                Spacer(Modifier.weight(1f))
            }
            vm.pickerKind?.let { kind ->
                BarButton((if (kind == "time") LucideIcons.Clock else LucideIcons.Calendar).glyph(), GridColors.OnBar, { vm.dialog = "pickDateTime" }, width = 40.dp, height = 40.dp, label = "Pick a Date or Time")
            }
            BarButton(LucideIcons.Keyboard.glyph(), GridColors.OnBar, { vm.useKeypad(false) }, width = 40.dp, height = 40.dp, label = "Keyboard")
            BarButton(LucideIcons.X.glyph(), GridColors.OnBar, vm::cancelEdit, width = 40.dp, height = 40.dp, label = "Cancel")
            BarButton(LucideIcons.Check.glyph(), GridColors.Accent, { vm.commitEdit("none") }, width = 40.dp, height = 40.dp, label = "Enter")
        } else if (vm.editing) {
            val ime = LocalSoftwareKeyboardController.current
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            // the palette open over a formula (fx, a function category): its list, not the keyboard,
            // which comes back when the palette closes
            var hid by remember { mutableStateOf(false) }
            LaunchedEffect(vm.paletteOpen) {
                if (vm.paletteOpen) {
                    kotlinx.coroutines.delay(120)
                    ime?.hide()
                    hid = true
                } else if (hid) {
                    hid = false
                    runCatching { focus.requestFocus() }
                    ime?.show()
                }
            }
            BasicTextField(
                value = vm.editValue,
                // expanded, the field wraps; Enter still enters (a new line in a cell is not typed here)
                onValueChange = { v -> if (expanded && v.text.count { it == '\n' } > vm.editValue.text.count { it == '\n' }) vm.commitEdit("down") else vm.editValue = v },
                singleLine = !expanded,
                maxLines = lines,
                textStyle = TextStyle(color = GridColors.OnSettings, fontSize = 16.sp),
                cursorBrush = SolidColor(GridColors.Accent),
                visualTransformation = if (vm.editText.startsWith("=")) RefColorsTransformation else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.commitEdit("down") }),
                modifier = Modifier.weight(1f).then(if (expanded) Modifier.padding(vertical = 11.dp) else Modifier).focusRequester(focus),
            )
            vm.pickerKind?.let { kind ->
                BarButton((if (kind == "time") LucideIcons.Clock else LucideIcons.Calendar).glyph(), GridColors.OnBar, { vm.dialog = "pickDateTime" }, width = 40.dp, height = 40.dp, label = "Pick a Date or Time")
            }
            BarButton(Glyph.Letters("123"), GridColors.OnBar, { vm.useKeypad(true) }, width = 40.dp, height = 40.dp, iconSize = 22.dp, label = "Number Keys")
            BarButton(LucideIcons.X.glyph(), GridColors.OnBar, vm::cancelEdit, width = 40.dp, height = 40.dp, label = "Cancel")
            BarButton(LucideIcons.Check.glyph(), GridColors.Accent, { vm.commitEdit("none") }, width = 40.dp, height = 40.dp, label = "Enter")
        } else {
            // with an object selected the bar holds no cell, as the phone app's: only its hint
            val objectSelected = vm.selectedObject != null
            val shown = if (objectSelected) "" else sel?.formula.orEmpty()
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clickable(enabled = !objectSelected) { vm.beginEdit() }.then(if (expanded) Modifier.padding(vertical = 11.dp) else Modifier),
                contentAlignment = if (expanded) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (shown.isEmpty()) {
                    Text("Type text or a formula", color = GridColors.OnBar.copy(alpha = 0.6f), fontSize = 16.sp, maxLines = 1)
                } else {
                    Text(shown, color = GridColors.OnSettings, fontSize = 16.sp, maxLines = lines, overflow = TextOverflow.Ellipsis)
                }
            }
            BarButton(if (expanded) LucideIcons.ChevronDown.glyph() else LucideIcons.ChevronUp.glyph(), GridColors.OnBar, { vm.formulaExpanded = !expanded }, width = 48.dp, height = 40.dp, iconSize = 18.dp, label = "Expand Formula Bar")
        }
    }
}

/**
 * The command bar floating over the bottom band, as the phone app has it: the sheet (a tap brings
 * back the sheets bar), Find, Apply Filter, Fill Color, and ⋯, which opens the palette.
 */
@Composable
fun CommandBar(vm: AppViewModel) {
    val info = vm.info ?: return
    val shape = RoundedCornerShape(24.dp)
    // typing in a shape: Bold, Italic, Underline and ⋯, as the phone app's bar then
    val typing = vm.selectedObject?.takeIf { vm.editingShapeText == it.id.toLong() }
    if (typing != null) {
        Box(Modifier.fillMaxWidth().background(GridColors.StatusBand).padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
            Row(Modifier.height(48.dp).shadow(3.dp, shape).clip(shape).background(GridColors.Bar).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BarButton(Letters.Bold, GridColors.OnPill, { vm.shapeTextFormat("bold", !typing.textBold) }, pressed = typing.textBold, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Bold")
                BarButton(Letters.Italic, GridColors.OnPill, { vm.shapeTextFormat("italic", !typing.textItalic) }, pressed = typing.textItalic, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Italic")
                BarButton(Letters.Underline, GridColors.OnPill, { vm.shapeTextFormat("underline", !typing.textUnderline) }, pressed = typing.textUnderline, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Underline")
                BarButton(LucideIcons.Ellipsis.glyph(), GridColors.OnPill, vm::togglePalette, width = 52.dp, height = 48.dp, iconSize = 22.dp, label = "More Options")
            }
        }
        return
    }
    // as the phone app's: a selected shape or chart has its own buttons, and the sheet chip
    // shows only its icon
    val o = vm.selectedObject
    val mode = when {
        o == null -> "cell"
        o.kind == "chart" -> "chart"
        o.kind == "image" -> "image"
        o.kind == "shape" && o.shape != "icon" && o.shape != "ink" -> "shape"
        else -> "cell"
    }
    Box(Modifier.fillMaxWidth().background(GridColors.StatusBand).padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.height(48.dp).shadow(3.dp, shape).clip(shape).background(GridColors.Bar), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier.fillMaxHeight().clickable { vm.showSheetsBar(true) }.semantics { contentDescription = "Sheet Tabs" }.padding(start = 14.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(LucideIcons.Layers.glyph(), GridColors.OnPill, size = 20.dp)
                    if (mode == "cell") {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            info.sheets.getOrNull(info.activeSheet.toInt())?.name.orEmpty(),
                            color = GridColors.OnPill,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 132.dp),
                        )
                    }
                }
            }
            Box(Modifier.width(1.dp).height(24.dp).background(GridColors.BarLine))
            BarButton(LucideIcons.Search.glyph(), GridColors.OnPill, vm::openFind, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Find")
            when (mode) {
                "shape" -> {
                    // a line has no fill and keeps its outline
                    val line = o?.isLine == true
                    BarButton(LucideIcons.PaintBucket.glyph(), GridColors.OnPill, { vm.openPage("shapeFill") }, enabled = !line, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Fill")
                    BarButton(LucideIcons.Shapes.glyph(), GridColors.OnPill, { vm.openPage("changeShape") }, enabled = !line, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Change Shape")
                }
                "chart" -> {
                    BarButton(LucideIcons.ChartColumn.glyph(), GridColors.OnPill, { vm.openPage("chartType") }, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Type")
                    BarButton(LucideIcons.ListPlus.glyph(), GridColors.OnPill, { vm.openPage("chartElements") }, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Elements")
                }
                "image" -> {
                    BarButton(LucideIcons.Crop.glyph(), GridColors.OnPill, vm::toggleCrop, pressed = vm.cropping, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Crop")
                    BarButton(LucideIcons.SwatchBook.glyph(), GridColors.OnPill, { vm.openPage("pictureStyles") }, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Styles")
                }
                else -> {
                    BarButton(LucideIcons.ListFilter.glyph(), GridColors.OnPill, vm::applyFilterButton, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = if (vm.sheetView?.autofilter == true) "Sort & Filter" else "Apply Filter")
                    BarButton(LucideIcons.PaintBucket.glyph(), GridColors.OnPill, { vm.openPage("fillColor") }, width = 48.dp, height = 48.dp, iconSize = 22.dp, label = "Fill Color")
                }
            }
            BarButton(LucideIcons.Ellipsis.glyph(), GridColors.OnPill, vm::togglePalette, width = 52.dp, height = 48.dp, iconSize = 22.dp, label = "More Options")
        }
    }
}

/**
 * The sheets bar: a toggle back to the command bar, the visible sheets' tabs (the current one light
 * with an accent line under it, a tab colour along the top), and Add Sheet. A long press on a tab
 * opens its menu.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SheetsBar(vm: AppViewModel) {
    val info = vm.info ?: return
    Row(Modifier.fillMaxWidth().height(48.dp).background(GridColors.SheetsBar).topEdge(GridColors.BarEdge), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(45.dp).fillMaxHeight().background(GridColors.SheetsToggle).clickable { vm.showSheetsBar(false) }.semantics { contentDescription = "Close Sheets Bar" },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(LucideIcons.Layers.glyph(), GridColors.OnBar, size = 22.dp)
        }
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            info.sheets.forEachIndexed { i, tab ->
                if (tab.hidden) return@forEachIndexed
                val current = i == info.activeSheet.toInt()
                Box(
                    Modifier.widthIn(min = 104.dp).fillMaxHeight()
                        .background(if (current) GridColors.TabCurrent else Color.Transparent)
                        .drawBehind {
                            if (current) drawRect(GridColors.Accent, Offset(0f, size.height - 3.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()))
                            tab.tabColor?.let { drawRect(Color(it), Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }
                        }
                        .combinedClickable(onClick = { vm.activateSheet(i) }, onLongClick = {
                            vm.activateSheet(i)
                            vm.tabMenu = i
                        })
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tab.protected) {
                            GlyphIcon(LucideIcons.Lock.glyph(), if (current) GridColors.OnTabCurrent else GridColors.OnBar, size = 14.dp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(tab.name, color = if (current) GridColors.OnTabCurrent else GridColors.OnBar, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                    }
                    if (vm.tabMenu == i) TabMenu(vm, i)
                }
            }
        }
        BarButton(LucideIcons.Plus.glyph(), GridColors.OnBar, vm::addSheet, width = 45.dp, height = 48.dp, label = "Add Sheet")
    }
}

/**
 * A sheet's menu, a small bar over its tab: Rename, Color (the colours, in a box), Duplicate, Hide
 * and Delete Sheet (with another sheet to show), and Move (before which sheet, or to the end).
 */
@Composable
private fun TabMenu(vm: AppViewModel, i: Int) {
    val info = vm.info ?: return
    var page by remember(i) { mutableStateOf<String?>(null) }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.roundToPx() }
    val visible = info.sheets.count { !it.hidden }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize, layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize) =
                // centred over the tab, kept off the screen's edges
                androidx.compose.ui.unit.IntOffset(
                    (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)),
                    (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0),
                )
        },
        onDismissRequest = { vm.tabMenu = null },
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        val shape = RoundedCornerShape(6.dp)
        when (page) {
            null -> Row(Modifier.shadow(6.dp, shape).background(GridColors.StatusBand, shape).clip(shape).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                @Composable
                fun item(label: String, last: Boolean = false, onClick: () -> Unit) {
                    Box(Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = GridColors.OnPill, fontSize = 16.sp)
                    }
                    if (!last) Box(Modifier.width(1.dp).height(24.dp).background(GridColors.BarLine))
                }
                item("Rename") {
                    vm.tabMenu = null
                    vm.renaming = i
                }
                item("Color") { page = "color" }
                item("Duplicate") { vm.duplicateSheet(i) }
                if (visible > 1) item("Hide") { vm.hideSheet(i) }
                item("Move", last = visible <= 1) { page = "move" }
                if (visible > 1) item("Delete Sheet", last = true) { vm.deleteSheet(i) }
            }
            else -> Column(Modifier.width(if (page == "color") 320.dp else 240.dp).shadow(6.dp, shape).background(GridColors.StatusBand, shape).clip(shape)) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    BarButton(LucideIcons.ChevronLeft.glyph(), GridColors.OnPill, { page = null }, width = 48.dp, height = 44.dp, iconSize = 20.dp, label = "Back")
                    Text(if (page == "color") "Color" else "Move", color = GridColors.OnPill, fontSize = 17.sp)
                }
                if (page == "color") {
                    Box(Modifier.fillMaxWidth().height(360.dp)) {
                        ColorGridPanel(vm, "No Color") { c ->
                            vm.tabMenu = null
                            vm.command("sheet.tabColor", "{\"sheet\": $i, \"color\": $c}")
                        }
                    }
                } else {
                    SectionBand("Before sheet")
                    for ((k, t) in info.sheets.withIndex()) {
                        if (t.hidden) continue
                        Box(Modifier.fillMaxWidth().height(44.dp).clickable { vm.moveSheetBefore(i, k) }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                            Text(t.name, color = GridColors.OnPill, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(44.dp).clickable { vm.moveSheetBefore(i, null) }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text("(move to end)", color = GridColors.OnPill, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

/**
 * The cell menu over the selection (a tap on it, or a long press): cut, copy, paste and its
 * options, Comment, Clear, Fill, Edit, each set off by a line, as the phone app draws it; it
 * scrolls when wider than the screen.
 */
@Composable
fun CellMenu(vm: AppViewModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    var pasteOptions by remember { mutableStateOf(false) }
    // Paste and its options are greyed until something has been copied
    LaunchedEffect(Unit) { vm.loadCanPaste() }
    @Composable
    fun line() = Box(Modifier.width(1.dp).height(24.dp).background(GridColors.BarLine))
    Row(
        modifier.shadow(6.dp, shape).background(GridColors.StatusBand, shape).clip(shape).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.link != null) {
            MenuText("Open Link") { vm.openLink() }
            line()
        }
        BarButton(LucideIcons.Scissors.glyph(), GridColors.OnPill, { vm.copy(cut = true) }, width = 38.dp, height = 36.dp, iconSize = 20.dp, label = "Cut")
        line()
        BarButton(LucideIcons.Copy.glyph(), GridColors.OnPill, { vm.copy(cut = false) }, width = 38.dp, height = 36.dp, iconSize = 20.dp, label = "Copy")
        line()
        BarButton(LucideIcons.ClipboardPaste.glyph(), GridColors.OnPill, { vm.paste() }, enabled = vm.canPaste, width = 38.dp, height = 36.dp, iconSize = 20.dp, label = "Paste")
        line()
        Box {
            BarButton(LucideIcons.ChevronDown.glyph(), GridColors.OnPill, { pasteOptions = true }, enabled = vm.canPaste, width = 38.dp, height = 36.dp, iconSize = 18.dp, label = "Paste Options")
            DropdownMenu(expanded = pasteOptions, onDismissRequest = { pasteOptions = false }) {
                for ((label, what) in listOf("Paste" to "all", "Paste Values" to "values", "Paste Formulas" to "formulas", "Paste Formatting" to "formats")) {
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        pasteOptions = false
                        vm.pasteSpecial(what)
                    })
                }
            }
        }
        line()
        MenuText("Comment") {
            vm.cellMenu = false
            vm.dialog = "comment"
        }
        line()
        MenuText("Clear") {
            vm.cellMenu = false
            vm.command("edit.clearContents")
        }
        line()
        MenuText("Fill") {
            vm.cellMenu = false
            vm.fillMode = true
        }
        line()
        MenuText("Edit") {
            vm.cellMenu = false
            vm.beginEdit()
        }
    }
}

@Composable
private fun MenuText(label: String, onClick: () -> Unit) {
    Box(Modifier.height(36.dp).clickable(onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = GridColors.OnPill, fontSize = 16.sp)
    }
}

/** Rename a sheet: its name in a field, then OK. */
@Composable
fun RenameDialog(vm: AppViewModel, index: Int) {
    val old = vm.info?.sheets?.getOrNull(index)?.name.orEmpty()
    var name by remember(index) { mutableStateOf(old) }
    AlertDialog(
        onDismissRequest = { vm.renaming = null },
        title = { Text("Rename Sheet") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(31) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { vm.renameSheet(index, name) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { vm.renaming = null }) { Text("Cancel") } },
    )
}

/** Find: what to look for, how many matches and which one, the previous and next match, close. */
@Composable
fun FindBar(vm: AppViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().background(GridColors.FindBar)) {
        Row(Modifier.fillMaxWidth().height(BAR_HEIGHT).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FindField(vm.findText, { vm.findText = it }, "Find", LucideIcons.Search, ImeAction.Search, { vm.runFind() }, Modifier.weight(1f).focusRequester(focus)) {
                if (vm.findHits.isNotEmpty()) {
                    Text("${vm.findIndex + 1} of ${vm.findHits.size}", color = GridColors.FindHint, fontSize = 14.sp)
                } else if (vm.findStatus.isNotEmpty()) {
                    Text(vm.findStatus, color = GridColors.FindHint, fontSize = 14.sp, maxLines = 1)
                }
            }
            BarButton(LucideIcons.Settings.glyph(), if (vm.findOptionsOpen) GridColors.Accent else GridColors.OnBar, { vm.findOptionsOpen = !vm.findOptionsOpen }, width = 40.dp, height = BAR_HEIGHT, label = "Find Options")
            BarButton(LucideIcons.Replace.glyph(), if (vm.replaceOpen) GridColors.Accent else GridColors.OnBar, { vm.replaceOpen = !vm.replaceOpen }, width = 40.dp, height = BAR_HEIGHT, label = "Replace")
            BarButton(LucideIcons.ChevronUp.glyph(), GridColors.OnBar, { vm.findStep(false) }, enabled = vm.findHits.isNotEmpty(), width = 40.dp, height = BAR_HEIGHT, label = "Previous Result")
            BarButton(LucideIcons.ChevronDown.glyph(), GridColors.OnBar, { vm.findStep(true) }, enabled = vm.findHits.isNotEmpty(), width = 40.dp, height = BAR_HEIGHT, label = "Next Result")
            BarButton(LucideIcons.X.glyph(), GridColors.OnBar, vm::closeFind, width = 40.dp, height = BAR_HEIGHT, label = "Close Find")
        }
        if (vm.replaceOpen) {
            Row(Modifier.fillMaxWidth().height(BAR_HEIGHT).padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FindField(vm.replaceText, { vm.replaceText = it }, "Replace", LucideIcons.Replace, ImeAction.Done, { vm.replaceOne() }, Modifier.weight(1f)) {}
                TextButton(onClick = { vm.replaceOne() }, enabled = vm.findText.isNotEmpty()) { Text("Replace", color = GridColors.Accent) }
                TextButton(onClick = { vm.replaceAll() }, enabled = vm.findText.isNotEmpty()) { Text("All", color = GridColors.Accent) }
            }
        }
        if (vm.findOptionsOpen) {
            Column(Modifier.fillMaxWidth().background(GridColors.Sheet).bottomEdge(GridColors.BarEdge)) {
                FindOption("Search the whole workbook", vm.findInWorkbook) { vm.findInWorkbook = it; vm.runFind() }
                FindOption("Match case", vm.findMatchCase) { vm.findMatchCase = it; vm.runFind() }
                FindOption("Match entire cell contents", vm.findWholeCell) { vm.findWholeCell = it; vm.runFind() }
            }
        }
    }
}

/** A rounded field of the find bar: an icon, the hint while empty, the text, and what trails it. */
@Composable
private fun FindField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: ImeAction,
    onAction: () -> Unit,
    modifier: Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(modifier.height(40.dp).background(GridColors.FindField, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(icon.glyph(), GridColors.FindHint, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, color = GridColors.FindHint, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = GridColors.OnSettings, fontSize = 16.sp),
                cursorBrush = SolidColor(GridColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = action),
                keyboardActions = KeyboardActions(onAny = { onAction() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing()
    }
}

/** An option of Find and Replace: a label and a switch. */
@Composable
private fun FindOption(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).clickable { onChange(!on) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GridColors.OnSettings, fontSize = 16.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = GridColors.Accent),
        )
    }
}

/** The active cell's note or comment thread: who wrote it, the text, the replies; Reply, Resolve, Delete. */
@Composable
fun CommentCard(vm: AppViewModel) {
    val c = vm.comment ?: return
    var reply by remember(c) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(GridColors.Sheet).topEdge(GridColors.BarEdge).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(c.author.ifBlank { "Comment" }, color = GridColors.OnSettings, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(c.text, color = GridColors.OnSurface, fontSize = 15.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        for (r in c.replies.takeLast(3)) {
            Spacer(Modifier.height(6.dp))
            Text(r.author, color = GridColors.OnSettings, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(r.text, color = GridColors.OnSurface, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.threaded) {
                Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.CenterStart) {
                    if (reply.isEmpty()) Text("Reply", color = GridColors.FindHint, fontSize = 15.sp)
                    BasicTextField(
                        value = reply,
                        onValueChange = { reply = it },
                        singleLine = true,
                        textStyle = TextStyle(color = GridColors.OnSettings, fontSize = 15.sp),
                        cursorBrush = SolidColor(GridColors.Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            vm.reply(reply)
                            reply = ""
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BarButton(LucideIcons.Check.glyph(), if (c.resolved) GridColors.Accent else GridColors.OnBar, vm::resolveComment, width = 44.dp, height = 40.dp, label = "Resolve Thread")
            } else {
                Spacer(Modifier.weight(1f))
            }
            BarButton(LucideIcons.X.glyph(), GridColors.OnBar, vm::deleteComment, width = 44.dp, height = 40.dp, label = "Delete Comment")
        }
    }
}

/** New Comment and Insert Link: a field or two, then OK. */
@Composable
fun EditorDialog(vm: AppViewModel) {
    when (vm.dialog) {
        "pickDateTime" -> DateTimePicker(vm)
        "customSort" -> CustomSortDialog(vm)
        "entryAlert" -> {
            val a = vm.entryAlert ?: return
            AlertDialog(
                onDismissRequest = { vm.answerEntryAlert("cancel") },
                title = { Text(a.title.ifEmpty { "Data Validation" }) },
                text = { Text(if (a.style == "warning") "${a.message}\n\nContinue?" else a.message, color = GridColors.OnSurface, fontSize = 15.sp) },
                confirmButton = {
                    when (a.style) {
                        "stop" -> TextButton(onClick = { vm.answerEntryAlert("retry") }) { Text("Retry") }
                        "warning" -> Row {
                            TextButton(onClick = { vm.answerEntryAlert("allow") }) { Text("Yes") }
                            TextButton(onClick = { vm.answerEntryAlert("retry") }) { Text("No") }
                        }
                        else -> TextButton(onClick = { vm.answerEntryAlert("allow") }) { Text("OK") }
                    }
                },
                dismissButton = { TextButton(onClick = { vm.answerEntryAlert("cancel") }) { Text("Cancel") } },
            )
        }
        "print" -> PrintOptions(vm)
        "filterRule" -> {
            val rule = vm.filterRule ?: return
            var a by remember { mutableStateOf(if (rule == "top10") "10" else "") }
            var b by remember { mutableStateOf("") }
            val title = mapOf(
                "eq" to "Equals", "ne" to "Does Not Equal", "gt" to "Greater Than", "ge" to "Greater Than Or Equal To", "lt" to "Less Than",
                "le" to "Less Than Or Equal To", "between" to "Between", "top10" to "Top 10", "begins" to "Begins With", "ends" to "Ends With",
                "contains" to "Contains", "notContains" to "Does Not Contain",
            )[rule] ?: "Filter"
            AlertDialog(
                onDismissRequest = { vm.dialog = null },
                title = { Text(title) },
                text = {
                    Column {
                        OutlinedTextField(value = a, onValueChange = { a = it.take(255) }, singleLine = true, label = { Text(if (rule == "top10") "Show the top" else "Value") })
                        if (rule == "between") {
                            Text("and", color = GridColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
                            OutlinedTextField(value = b, onValueChange = { b = it.take(255) }, singleLine = true, label = { Text("Value") })
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { vm.applyFilterRule(rule, a.trim(), b.trim()) }, enabled = a.isNotBlank() && (rule != "between" || b.isNotBlank())) { Text("OK") } },
                dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
            )
        }
        "comment" -> {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { vm.dialog = null },
                title = { Text("New Comment") },
                text = { OutlinedTextField(value = text, onValueChange = { text = it.take(32767) }, minLines = 2) },
                confirmButton = { TextButton(onClick = { vm.newComment(text) }) { Text("Post") } },
                dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
            )
        }
        "goTo" -> {
            var ref by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { vm.dialog = null },
                title = { Text("Go To") },
                text = {
                    OutlinedTextField(
                        value = ref,
                        onValueChange = { ref = it },
                        singleLine = true,
                        label = { Text("Reference or name") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.goTo(ref) }),
                    )
                },
                confirmButton = { TextButton(onClick = { vm.goTo(ref) }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
            )
        }
        "rowHeight", "columnWidth" -> {
            val rows = vm.dialog == "rowHeight"
            var value by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { vm.dialog = null },
                title = { Text(if (rows) "Row Height" else "Column Width") },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.take(8) },
                        singleLine = true,
                        label = { Text(if (rows) "Points" else "Characters") },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.setSize(rows, value) }),
                    )
                },
                confirmButton = { TextButton(onClick = { vm.setSize(rows, value) }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
            )
        }
        "closeUnsaved" -> AlertDialog(
            onDismissRequest = { vm.dialog = null },
            title = { Text("Save changes?") },
            text = {
                Text(
                    when {
                        vm.hasFile -> "AutoSave is off. Save your changes before closing?"
                        vm.readOnlyFile -> "This file can't be changed here. Save your changes as a copy?"
                        else -> "This workbook hasn't been saved to a file yet."
                    },
                )
            },
            confirmButton = { TextButton(onClick = vm::saveAndClose) { Text(if (vm.readOnlyFile) "Save a Copy" else "Save") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.dialog = null }) { Text("Cancel") }
                    TextButton(onClick = { vm.closeWorkbook(force = true) }) { Text("Don't Save") }
                }
            },
        )
        "link" -> {
            var target by remember { mutableStateOf(vm.link.orEmpty()) }
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { vm.dialog = null },
                title = { Text("Insert Link") },
                text = {
                    Column {
                        OutlinedTextField(value = target, onValueChange = { target = it }, singleLine = true, label = { Text("Address") })
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Display text") })
                    }
                },
                confirmButton = { TextButton(onClick = { vm.insertLink(target, text) }) { Text("Insert") } },
                dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
            )
        }
    }
}

/** A tick box and its label, the whole row tappable. */
@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = GridColors.Accent),
        )
        Text(label, color = GridColors.OnSettings, fontSize = 16.sp)
    }
}

/** Custom Sort: the block, its headings, and sort levels (a column and an order each), the first first. */
@Composable
private fun CustomSortDialog(vm: AppViewModel) {
    val pv = vm.sortProposal ?: return
    var header by remember { mutableStateOf(pv.header) }
    val levels = remember { mutableStateListOf(pv.columns.firstOrNull()?.first.orEmpty() to "asc") }
    // a column goes by its heading when the first row is headings, else by its letter
    val columns = pv.columns.map { (letter, heading) -> (if (header && heading.isNotBlank()) heading else "Column $letter") to letter }
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text("Custom Sort") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(pv.range, color = GridColors.OnSurface, fontSize = 16.sp)
                CheckRow("My data has headers", header) { header = it }
                for ((i, level) in levels.withIndex()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Picker(if (i == 0) "Sort by" else "Then by", columns, level.first) { levels[i] = it to level.second }
                        }
                        if (levels.size > 1) BarButton(LucideIcons.X.glyph(), GridColors.OnSurface, { levels.removeAt(i) }, width = 40.dp, height = 48.dp, label = "Remove Level")
                    }
                    Spacer(Modifier.height(4.dp))
                    Picker("Order", listOf("A to Z, smallest to largest" to "asc", "Z to A, largest to smallest" to "desc"), level.second) { levels[i] = level.first to it }
                }
                if (levels.size < 64 && columns.isNotEmpty()) {
                    TextButton(onClick = { levels.add(columns.first().second to "asc") }) { Text("Add Level") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.customSort(pv.range, header, levels.toList()) }, enabled = columns.isNotEmpty()) { Text("OK") } },
        dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
    )
}

/** A field that opens a list of choices. */
@Composable
private fun Picker(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Box {
        OutlinedTextField(
            value = options.firstOrNull { it.second == value }?.first.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { GlyphIcon(LucideIcons.ChevronDown.glyph(), GridColors.OnSurface, size = 18.dp) },
            modifier = Modifier.fillMaxWidth(),
        )
        // a clear layer over the field, so a tap opens the list rather than a keyboard
        Box(Modifier.matchParentSize().clickable {
            // the keyboard goes, so the dialog stays put under the list
            focus.clearFocus()
            open = true
        })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((text, v) in options) DropdownMenuItem(text = { Text(text) }, onClick = {
                onPick(v)
                open = false
            })
        }
    }
}

/**
 * Sort & Filter for a column, as the phone app's sheet: Sort Ascending and Descending side by side;
 * Sheet View (greyed: views shared in the cloud); then Filter by, with Clear All, over its tabs:
 * Items (a search, Select All, the values: ticking one shows only the ticked ones, at once, none
 * ticked meaning no filter), Number or Text (the rules, each opening its field in place) and, when
 * the column's cells have fills, Color.
 */
@Composable
fun FilterSheet(vm: AppViewModel) {
    val values = vm.filterValues
    val at = vm.filterAt
    val filtered = values.any { !it.shown } || vm.viewport?.cells?.any { it.row == at?.first && it.col == at.second && it.filtered } == true
    // ticked: the values shown when the column is filtered by its values; none when it isn't
    var ticked by remember(at, values) { mutableStateOf(if (values.any { !it.shown }) values.filter { it.shown }.map { it.text }.toSet() else emptySet()) }
    var tab by remember(at) { mutableStateOf("items") }
    var query by remember(at) { mutableStateOf("") }
    val numeric = vm.filterNumeric
    val pick = { next: Set<String> ->
        ticked = next
        vm.filterToValues(next)
    }
    val card = RoundedCornerShape(8.dp)
    val top = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    // one height whichever tab is up, as the phone app's sheet
    Column(Modifier.fillMaxWidth().height(540.dp).shadow(8.dp, top).background(GridColors.Sheet, top)) {
        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 4.dp).background(GridColors.Handle, RoundedCornerShape(2.dp)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
            SortButton("Sort Ascending", true, Modifier.weight(1f)) { vm.sortFilterColumn(true) }
            SortButton("Sort Descending", false, Modifier.weight(1f)) { vm.sortFilterColumn(false) }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().height(48.dp).background(GridColors.Card, card).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(LucideIcons.Eye.glyph(), GridColors.Disabled, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text("Sheet View", color = GridColors.Disabled, fontSize = 16.sp, modifier = Modifier.weight(1f))
            GlyphIcon(LucideIcons.ChevronRight.glyph(), GridColors.Disabled, size = 20.dp)
        }
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 32.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Filter by", color = GridColors.OnSettings, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { pick(emptySet()) }, enabled = filtered) { Text("Clear All", color = if (filtered) GridColors.Accent else GridColors.Disabled, fontSize = 16.sp) }
        }
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(34.dp).background(GridColors.Segment, RoundedCornerShape(17.dp))) {
            val tabs = listOf("items" to "Items", "rules" to if (numeric) "Number" else "Text") +
                if (vm.filterFills.any { it.color != null }) listOf("color" to "Color") else emptyList()
            for ((key, label) in tabs) {
                val on = tab == key
                Box(Modifier.weight(1f).fillMaxHeight().background(if (on) GridColors.SegmentOn else Color.Transparent, RoundedCornerShape(17.dp)).clickable { tab = key }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (on) Color.White else GridColors.OnSettings, fontSize = 15.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == "items") {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).background(GridColors.CardSearch, card).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(LucideIcons.Search.glyph(), GridColors.SettingsValue, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search", color = GridColors.SettingsValue, fontSize = 16.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = GridColors.OnSettings, fontSize = 16.sp), cursorBrush = SolidColor(GridColors.Accent), modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(12.dp))
            val any = ticked.isNotEmpty()
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).background(GridColors.Card, card).clickable { pick(if (any) emptySet() else values.map { it.text }.toSet()) }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (any) "Clear All" else "Select All", color = GridColors.Accent, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            val list = values.filter { query.isBlank() || it.text.contains(query.trim(), ignoreCase = true) }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().background(GridColors.Card, card)) {
                items(list.size) { k ->
                    val v = list[k]
                    FilterRow(v.text, v.text in ticked) { pick(if (v.text in ticked) ticked - v.text else ticked + v.text) }
                }
            }
        } else if (tab == "color") {
            FilterColors(vm)
        } else {
            FilterRules(vm, numeric)
        }
    }
}

/** The Color tab: a swatch for each fill the column's cells have, No Fill first, on a white strip; a tap filters to it. */
@Composable
private fun FilterColors(vm: AppViewModel) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(47.dp).background(GridColors.Card, RoundedCornerShape(8.dp)).padding(start = 11.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).background(Color.White).padding(start = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            for (f in vm.filterFills) {
                val shape = RoundedCornerShape(3.dp)
                val name = f.color?.let { c -> "RGB(${(c shr 16) and 0xFF}, ${(c shr 8) and 0xFF}, ${c and 0xFF})" } ?: "No Fill"
                Box(
                    Modifier.size(58.dp, 35.dp)
                        .border(2.dp, if (f.chosen) GridColors.FillFrameChosen else GridColors.FillFrame, shape)
                        .padding(2.dp)
                        .background(f.color?.let { Color(it) } ?: Color.White, shape)
                        .clickable { vm.filterByFill(f.param) }
                        .semantics { contentDescription = name + if (f.chosen) " Selected" else "" },
                ) {
                    // No Fill: a light X across the white
                    if (f.color == null) androidx.compose.foundation.Canvas(Modifier.size(26.dp, 22.dp).align(Alignment.Center)) {
                        val ink = Color(0xFFD4D4D4)
                        drawLine(ink, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 1.5.dp.toPx())
                        drawLine(ink, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = 1.5.dp.toPx())
                    }
                }
            }
        }
    }
}

/** Sort Ascending or Descending: a large rounded button, an A–Z (or Z–A) arrow over its label. */
@Composable
private fun SortButton(label: String, ascending: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(64.dp).background(GridColors.Card, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (ascending) "A" else "Z", color = GridColors.FilteredRow, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
                Text(if (ascending) "Z" else "A", color = GridColors.OnSettings, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("↓", color = GridColors.OnSettings, fontSize = 18.sp)
        }
        Text(label, color = GridColors.OnSettings, fontSize = 15.sp)
    }
}

/** The Number or Text tab: the rules; one tapped opens its field (two for Between) in place, the keyboard's Done applying it. */
@Composable
private fun FilterRules(vm: AppViewModel, numeric: Boolean) {
    val rules = if (numeric) {
        listOf(
            "Equals..." to "eq", "Does Not Equal..." to "ne", "Greater Than..." to "gt", "Less Than..." to "lt",
            "Greater Than Or Equal To..." to "ge", "Less Than Or Equal To..." to "le", "Between..." to "between", "Top 10..." to "top10",
            "Above Average" to "above", "Below Average" to "below",
        )
    } else {
        listOf("Equals..." to "eq", "Does Not Equal..." to "ne", "Begins With..." to "begins", "Ends With..." to "ends", "Contains..." to "contains", "Does Not Contain..." to "notContains")
    }
    var open by remember { mutableStateOf<String?>(null) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth().background(GridColors.Card, RoundedCornerShape(8.dp))) {
        items(rules.size) { k ->
            val (label, rule) = rules[k]
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clickable {
                        if (rule == "above" || rule == "below") {
                            vm.applyFilterRule(rule, "", keepOpen = true)
                        } else {
                            open = if (open == rule) null else rule
                            first = ""
                            second = ""
                        }
                    }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(label, color = GridColors.OnSettings, fontSize = 16.sp) }
                if (open == rule) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(rule) { runCatching { focus.requestFocus() } }
                    val type = if (numeric || rule == "top10") androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text
                    val apply = {
                        if (first.isNotBlank() && (rule != "between" || second.isNotBlank())) {
                            vm.applyFilterRule(rule, first.trim(), second.trim(), keepOpen = true)
                            open = null
                        }
                    }
                    RuleField(first, { first = it }, type, rule != "between", apply, Modifier.focusRequester(focus))
                    if (rule == "between") {
                        Text("and", color = GridColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                        RuleField(second, { second = it }, type, true, apply, Modifier)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** A rule's value: a field under the rule; [last] makes the keyboard's action Done (which applies). */
@Composable
private fun RuleField(value: String, onChange: (String) -> Unit, type: androidx.compose.ui.text.input.KeyboardType, last: Boolean, onDone: () -> Unit, modifier: Modifier) {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(40.dp).background(GridColors.CardSearch, RoundedCornerShape(6.dp)).bottomEdge(GridColors.Accent).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value, onChange,
            singleLine = true,
            textStyle = TextStyle(color = GridColors.OnSettings, fontSize = 16.sp),
            cursorBrush = SolidColor(GridColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FilterRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onToggle).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(on, onCheckedChange = { onToggle() }, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = GridColors.Accent))
        Text(label, color = GridColors.OnSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The function the cursor is in, its arguments, the one being typed in bold. */
@Composable
fun SignatureTip(vm: AppViewModel) {
    if (vm.suggestions.isNotEmpty()) return
    val (name, args, current) = vm.signatureTip ?: return
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append(name)
        append("(")
        args.forEachIndexed { i, a ->
            if (i > 0) append(", ")
            if (i == current) {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = GridColors.OnSettings))
                append(a)
                pop()
            } else {
                append(a)
            }
        }
        append(")")
    }
    Box(Modifier.fillMaxWidth().background(GridColors.Sheet).bottomEdge(GridColors.BarEdge).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = GridColors.SettingsValue, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Functions matching the word being typed in a formula; a tap puts the function in. */
@Composable
fun FunctionSuggestions(vm: AppViewModel) {
    val list = vm.suggestions
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(GridColors.Sheet).bottomEdge(GridColors.BarEdge)) {
        for ((name, signature) in list) {
            Row(Modifier.fillMaxWidth().height(40.dp).clickable { vm.takeSuggestion(name) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = GridColors.OnSettings, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(120.dp))
                Text(signature.substringAfter('(', "").removeSuffix(")"), color = GridColors.SettingsValue, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The validation list's items matching what is typed in the cell; a tap enters one. */
@Composable
fun ListSuggestions(vm: AppViewModel) {
    val list = vm.listSuggestions
    if (list.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(GridColors.Sheet).bottomEdge(GridColors.BarEdge)) {
        for (item in list) {
            Box(Modifier.fillMaxWidth().height(40.dp).clickable { vm.pickValue(item) }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(item, color = GridColors.OnSettings, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A total row's functions, as the phone app lists them from the cell's drop-down: a dark title
 * over a white list (the function the cell has lit) that scrolls past five or so.
 */
@Composable
fun TotalFunctionMenu(vm: AppViewModel, modifier: Modifier = Modifier) {
    val current = vm.tableInfo?.totalFunction.orEmpty()
    Column(modifier.width(190.dp).shadow(6.dp)) {
        Box(Modifier.fillMaxWidth().height(47.dp).background(GridColors.MenuTitle).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text("Total Function", color = GridColors.OnMenuTitle, fontSize = 19.sp)
        }
        // opened with the cell's own function in view, at the bottom, as the phone app scrolls it
        val scroll = rememberScrollState()
        val density = androidx.compose.ui.platform.LocalDensity.current
        LaunchedEffect(current) {
            val i = TOTAL_FUNCTIONS.indexOfFirst { it.second == current }
            if (i >= 0) scroll.scrollTo(with(density) { ((i + 1) * 32 - 170).dp.roundToPx() }.coerceAtLeast(0))
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).background(GridColors.Cells).verticalScroll(scroll)) {
            for ((label, fn) in TOTAL_FUNCTIONS) {
                Box(
                    Modifier.fillMaxWidth().height(32.dp).background(if (fn == current) GridColors.MenuChosen else GridColors.Cells).clickable { vm.setTotalFunction(fn) }.padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(label, color = GridColors.OnMenu, fontSize = 17.sp, maxLines = 1)
                }
            }
        }
    }
}

/** A total row's functions as the phone app names them, with what `table.totalFunction` takes. */
private val TOTAL_FUNCTIONS = listOf(
    "Average" to "average",
    "Count Items" to "count",
    "Count Numbers" to "countNums",
    "Maximum Value" to "max",
    "Minimum Value" to "min",
    "Sum" to "sum",
    "Standard Deviation" to "stdDev",
    "Variance" to "var",
)

/** Under a table just made: a tick box saying its first row is headers; unticked, it gets a header row of its own. */
@Composable
fun TableHeadersPrompt(vm: AppViewModel, on: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.height(47.dp).shadow(4.dp).background(GridColors.StatusBand).clickable { vm.setTableHeaders(!on) }.padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).background(GridColors.TickBox, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
            if (on) GlyphIcon(LucideIcons.Check.glyph(), GridColors.OnSettings, size = 16.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text("Table has headers", color = GridColors.OnSettings, fontSize = 17.sp)
    }
}

/** Whether the selection's figures show in the formula bar's place: several cells, something in them. */
fun showsAggregates(vm: AppViewModel): Boolean = !vm.editing && (vm.stats?.count ?: 0uL) > 0uL

/**
 * The selection's figures in the formula bar's place, as the phone app shows them for several
 * cells: Sum, Average, Count, Numerical Count, Maximum and Minimum as chips in a row that
 * scrolls; with no numbers, only Count.
 */
@Composable
fun AggregateBar(vm: AppViewModel) {
    val st = vm.stats ?: return
    val chips = buildList {
        if (st.numeric > 0uL) {
            add("Sum: ${st.sum}")
            add("Average: ${st.average}")
        }
        add("Count: ${st.count}")
        if (st.numeric > 0uL) {
            add("Numerical Count: ${st.numeric}")
            add("Maximum: ${st.max}")
            add("Minimum: ${st.min}")
        }
    }
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(GridColors.FormulaFill).topEdge(GridColors.BarEdge).horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(5.dp),
    ) {
        for (c in chips) {
            Box(Modifier.height(29.dp).background(GridColors.Pressed, RoundedCornerShape(2.dp)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(c, color = GridColors.OnSettings, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

/** A validation list to pick the active cell's value from. */
@Composable
fun ValidationSheet(vm: AppViewModel) {
    val items = vm.validation?.items.orEmpty()
    Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).background(GridColors.Sheet).topEdge(GridColors.BarEdge)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(vm.validation?.title?.ifBlank { null } ?: "Choose a value", color = GridColors.OnSettings, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.pickingList = false }) { Text("Cancel", color = GridColors.Accent) }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
            items(items.size) { i ->
                Box(Modifier.fillMaxWidth().height(44.dp).clickable { vm.pickValue(items[i]) }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Text(items[i], color = GridColors.OnSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A validation's input message for the active cell. */
@Composable
fun InputMessage(vm: AppViewModel) {
    val v = vm.validation ?: return
    if (v.message.isBlank() && v.title.isBlank()) return
    Column(Modifier.fillMaxWidth().background(GridColors.Sheet).topEdge(GridColors.BarEdge).padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (v.title.isNotBlank()) Text(v.title, color = GridColors.OnSettings, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (v.message.isNotBlank()) Text(v.message, color = GridColors.OnSurface, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Review > Show Comments: the sheet's comments and notes, the active cell's marked; Previous,
 * Next, Delete Comment and Comment over the list. A comment picked goes to its cell.
 */
@Composable
fun CommentsPane(vm: AppViewModel) {
    val sel = vm.selection
    val here = vm.commentList.any { it.row == sel?.activeRow && it.col == sel.activeCol }
    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).background(GridColors.Sheet).topEdge(GridColors.BarEdge)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Comments", color = GridColors.OnSettings, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            BarButton(LucideIcons.ChevronUp.glyph(), GridColors.OnBar, { vm.goToComment(false) }, enabled = vm.commentList.isNotEmpty(), width = 44.dp, height = 48.dp, label = "Previous Comment")
            BarButton(LucideIcons.ChevronDown.glyph(), GridColors.OnBar, { vm.goToComment(true) }, enabled = vm.commentList.isNotEmpty(), width = 44.dp, height = 48.dp, label = "Next Comment")
            BarButton(LucideIcons.Trash2.glyph(), GridColors.OnBar, vm::deleteComment, enabled = here, width = 44.dp, height = 48.dp, label = "Delete Comment")
            BarButton(LucideIcons.MessageSquarePlus.glyph(), GridColors.OnBar, { vm.dialog = "comment" }, enabled = !here, width = 44.dp, height = 48.dp, label = "New Comment")
            BarButton(LucideIcons.X.glyph(), GridColors.OnBar, { vm.showComments(false) }, width = 44.dp, height = 48.dp, label = "Close Comments")
        }
        if (vm.commentList.isEmpty()) {
            Text("There are no comments on this sheet.", color = GridColors.OnSurface, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
            items(vm.commentList.size) { i ->
                val c = vm.commentList[i]
                val on = c.row == sel?.activeRow && c.col == sel.activeCol
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (on) GridColors.Pressed else Color.Transparent)
                        .clickable { vm.goToCell(c.row, c.col) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.cell, color = GridColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Text(c.author.ifBlank { if (c.threaded) "Comment" else "Note" }, color = GridColors.OnSettings, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (c.resolved) Text("Resolved", color = GridColors.FindHint, fontSize = 12.sp)
                    }
                    Text(c.text, color = GridColors.OnSurface, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (c.replies > 0u) Text(if (c.replies == 1u) "1 reply" else "${c.replies} replies", color = GridColors.FindHint, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * A line, an arrow or a connector: a shape that is all outline and holds no text, or a picture
 * changed to one (nothing of it shows).
 */
internal val io.github.artcraftandroid.gridcraft.core.SheetObject.isLine: Boolean
    get() = (kind == "shape" && (shape == "line" || shape == "arrow" || preset.contains("Connector"))) ||
        (kind == "image" && shape == "preset" && pieces.isEmpty())

/**
 * A line's two ends as fractions of its box (start, end): corner to corner, mirrored as it is;
 * the only handles the phone app gives a line.
 */
internal val io.github.artcraftandroid.gridcraft.core.SheetObject.lineEnds: List<Pair<Float, Float>>
    get() {
        val (sx, sy) = (if (flipH) 1f else 0f) to (if (flipV) 1f else 0f)
        return listOf(sx to sy, (1f - sx) to (1f - sy))
    }

/** The selected object's floating menu over the grid: Cut, Copy, Paste, Delete, Edit Text. */
@Composable
fun ObjectMenu(vm: AppViewModel, modifier: Modifier = Modifier) {
    val o = vm.selectedObject ?: return
    if (vm.objectDrag != null) return
    // drawn as the cell menu is, as the phone app draws both: Cut, Copy and Paste as icons,
    // Delete, and Edit Text for a shape that holds text, each set off by a line
    val shape = RoundedCornerShape(6.dp)
    @Composable
    fun line() = Box(Modifier.width(1.dp).height(24.dp).background(GridColors.BarLine))
    Row(modifier.shadow(6.dp, shape).background(GridColors.StatusBand, shape).clip(shape), verticalAlignment = Alignment.CenterVertically) {
        BarButton(LucideIcons.Scissors.glyph(), GridColors.OnPill, { vm.copyObject(cut = true) }, width = 38.dp, height = 36.dp, iconSize = 20.dp, label = "Cut")
        line()
        BarButton(LucideIcons.Copy.glyph(), GridColors.OnPill, { vm.copyObject(cut = false) }, width = 38.dp, height = 36.dp, iconSize = 20.dp, label = "Copy")
        line()
        BarButton(LucideIcons.ClipboardPaste.glyph(), GridColors.OnPill, vm::pasteObject, enabled = vm.canPasteObject, width = 38.dp, height = 36.dp, iconSize = 20.dp, label = "Paste")
        line()
        MenuText("Delete", vm::deleteObject)
        if (o.shape == "textBox" || (o.kind == "shape" && o.text.isNotEmpty() && !o.isLine && o.shape !in setOf("ink", "icon"))) {
            line()
            MenuText("Edit Text") { vm.startShapeText() }
        }
    }
}

/** Print: what to print, scaling, paper size and orientation, then the system's print dialog. */
@Composable
private fun PrintOptions(vm: AppViewModel) {
    var what by remember { mutableStateOf("active") }
    var fit by remember { mutableStateOf("actual") }
    var paper by remember { mutableStateOf("Letter") }
    var orientation by remember { mutableStateOf("auto") }
    var ignoreArea by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.dialog = null },
        title = { Text("Print") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item { PrintHeading("What to print") }
                items(3) { i ->
                    val (label, key) = listOf("Selection" to "selection", "Active Sheets" to "active", "Entire Workbook" to "workbook")[i]
                    PrintChoice(label, what == key) { what = key }
                }
                item { PrintHeading("Scaling") }
                items(4) { i ->
                    val (label, key) = listOf(
                        "Actual Size" to "actual", "Fit Sheet on One Page" to "page", "Fit All Columns on One Page" to "columns", "Fit All Rows on One Page" to "rows",
                    )[i]
                    PrintChoice(label, fit == key) { fit = key }
                }
                item { PrintHeading("Paper Size") }
                items(PAPERS.size) { i -> PrintChoice(PAPERS[i], paper == PAPERS[i]) { paper = PAPERS[i] } }
                item { PrintHeading("Orientation") }
                items(3) { i ->
                    val (label, key) = listOf("Automatic" to "auto", "Portrait" to "portrait", "Landscape" to "landscape")[i]
                    PrintChoice(label, orientation == key) { orientation = key }
                }
                item {
                    Row(Modifier.fillMaxWidth().clickable { ignoreArea = !ignoreArea }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(ignoreArea, onCheckedChange = { ignoreArea = it }, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = GridColors.Accent))
                        Text("Ignore Print Area", color = GridColors.OnSettings, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.print(what, fit, orientation, paper, ignoreArea) }) { Text("Print") } },
        dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
    )
}

private val PAPERS = listOf("Letter", "Legal", "A4", "A3", "A5", "Tabloid", "Executive", "B4", "B5")

@Composable
private fun PrintHeading(text: String) {
    Text(text, color = GridColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun PrintChoice(label: String, on: Boolean, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(on, onClick = onPick, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = GridColors.Accent))
        Text(label, color = GridColors.OnSettings, fontSize = 15.sp)
    }
}

/** The date or time picker for a date or time cell: what is picked is typed in and entered. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePicker(vm: AppViewModel) {
    if (vm.pickerKind == "time") {
        val now = java.util.Calendar.getInstance()
        val state = androidx.compose.material3.rememberTimePickerState(now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
        AlertDialog(
            onDismissRequest = { vm.dialog = null },
            text = { androidx.compose.material3.TimePicker(state) },
            confirmButton = { TextButton(onClick = { vm.pickDateTime("%d:%02d".format(state.hour, state.minute)) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
        )
    } else {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { vm.dialog = null },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis == null) vm.dialog = null else {
                        // the picker's day is UTC midnight; an ISO date the engine reads as a date
                        val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        vm.pickDateTime(d.toString())
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel") } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
}
