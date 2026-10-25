package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.core.Across
import io.github.artcraftandroid.gridcraft.core.Down
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons

/**
 * A command Tell Me finds: its name as the phone app's Tell Me lists it, the tab it is on, other
 * words people use for it, whether it opens a page (a chevron), and what picking it does. As
 * there, a result is the control itself: lit while on, split with an arrow to its choices, a list's
 * current choice under its name, a field or a tick box; greyed while it doesn't apply.
 */
internal class TellMeItem(
    val label: String,
    val tab: String,
    val glyph: Glyph?,
    val words: String = "",
    val opens: Boolean = false,
    val enabled: (AppViewModel) -> Boolean = { true },
    val on: ((AppViewModel) -> Boolean)? = null,
    /** A split button's arrow: its choices. */
    val more: ((AppViewModel) -> Unit)? = null,
    val control: TellMeControl? = null,
    /** An icon that shows the current choice (a colour button's bar), in place of [glyph]. */
    val look: ((AppViewModel) -> Glyph)? = null,
    val run: (AppViewModel) -> Unit,
)

/** How a result shows when it isn't a button. */
internal sealed interface TellMeControl {
    /** A list's current choice under its name; a tap opens the list. */
    class Choice(val value: (AppViewModel) -> String) : TellMeControl

    /** A field under its name, set on the keyboard's Done. */
    class Field(val value: (AppViewModel) -> String, val lines: Int = 1, val set: (AppViewModel, String) -> Unit) : TellMeControl

    /** A tick box. */
    class Check(val checked: (AppViewModel) -> Boolean) : TellMeControl
}

/** What a result needs before it applies: the selected object's kind, a table, several objects… */
private val CHART: (AppViewModel) -> Boolean = { it.selectedObject?.kind == "chart" }
private val PICTURE: (AppViewModel) -> Boolean = { it.selectedObject?.kind == "image" }
private val SHAPE: (AppViewModel) -> Boolean = { it.selectedObject?.kind == "shape" && it.selectedObject?.shape != "icon" }
private val GRAPHIC: (AppViewModel) -> Boolean = { it.selectedObject?.kind == "shape" && it.selectedObject?.shape == "icon" }
private val OBJECT: (AppViewModel) -> Boolean = { it.selectedObject != null }
private val TURNS: (AppViewModel) -> Boolean = { it.selectedObject != null && it.selectedObject?.kind != "chart" }
private val STACKED: (AppViewModel) -> Boolean = { it.selectedObject != null && it.objects.size > 1 }
private val TABLE: (AppViewModel) -> Boolean = { it.tableInfo != null }
private val FILTERED: (AppViewModel) -> Boolean = { it.sheetView?.filteredRows != null }
private val OUTLINED: (AppViewModel) -> Boolean = { (it.sheetView?.rowLevels?.toInt() ?: 0) + (it.sheetView?.colLevels?.toInt() ?: 0) > 0 }
private val NEVER: (AppViewModel) -> Boolean = { false }

/** Panes frozen at a cell (not just the top row or first column), as Freeze Panes is lit. */
private fun frozenPanes(vm: AppViewModel): Boolean {
    val rows = vm.sheetView?.frozenRows ?: 0u
    val cols = vm.sheetView?.frozenCols ?: 0u
    return (rows > 0u || cols > 0u) && !(rows == 1u && cols == 0u) && !(rows == 0u && cols == 1u)
}

private fun page(label: String, tab: String, panel: String, glyph: Glyph?, words: String = "", enabled: (AppViewModel) -> Boolean = { true }) =
    TellMeItem(label, tab, glyph, words, opens = true, enabled = enabled) { it.showPanel(tab, panel) }

private fun cmd(label: String, tab: String, id: String, glyph: Glyph?, words: String = "", params: String = "{}", enabled: (AppViewModel) -> Boolean = { true }) =
    TellMeItem(label, tab, glyph, words, enabled = enabled) { it.command(id, params) }

/** A command that is lit while [on]. */
private fun toggle(label: String, tab: String, glyph: Glyph?, words: String = "", enabled: (AppViewModel) -> Boolean = { true }, on: (AppViewModel) -> Boolean, run: (AppViewModel) -> Unit) =
    TellMeItem(label, tab, glyph, words, enabled = enabled, on = on, run = run)

/** A split button: the left part applies, the arrow opens [panel]. */
private fun split(label: String, tab: String, panel: String, glyph: Glyph?, words: String = "", enabled: (AppViewModel) -> Boolean = { true }, on: ((AppViewModel) -> Boolean)? = null, run: (AppViewModel) -> Unit) =
    TellMeItem(label, tab, glyph, words, enabled = enabled, on = on, more = { it.showPanel(tab, panel) }, run = run)

/** A table style option, lit while on. */
private fun tableOption(label: String, id: String, words: String, on: (io.github.artcraftandroid.gridcraft.core.TableInfo) -> Boolean) =
    toggle(label, "Table", null, words, TABLE, { vm -> vm.tableInfo?.let(on) == true }) { vm ->
        vm.command(id, json("on" to (vm.tableInfo?.let(on) != true)))
    }

/** Picks that close the palette first, then act on the sheet. */
private fun away(label: String, tab: String, glyph: Glyph?, words: String = "", act: (AppViewModel) -> Unit) =
    TellMeItem(label, tab, glyph, words) { vm ->
        vm.collapsePalette()
        act(vm)
    }

/** A View setting's tick box, turned the other way. */
private fun viewSetting(label: String, id: String, words: String, on: (AppViewModel) -> Boolean) =
    TellMeItem(label, "View", null, words, control = TellMeControl.Check(on)) { vm -> vm.command(id, json("on" to !on(vm))) }

/** The function categories of the Formulas tab, with the panel each opens. */
private val FUNCTION_CATEGORIES = listOf(
    "Financial" to "Financial", "Logical" to "Logical", "Text" to "Text", "DateTime" to "Date & Time",
    "Lookup" to "Lookup & Reference", "MathTrig" to "Math & Trig", "Statistical" to "Statistical",
    "Engineering" to "Engineering", "Information" to "Information", "Database" to "Database", "Compatibility" to "Compatibility",
)

/** Function categories as the phone app's Tell Me names them (the others by their own names). */
private val TELL_ME_FUNCTION_NAMES = mapOf(
    "Financial" to "Financial Functions",
    "Text" to "Text Function",
    "Statistical" to "Statistical Function",
    "Engineering" to "Engineering Functions",
)

/** The palette's commands Tell Me finds, by the names the phone app's Tell Me gives them, tab by tab. */
internal val TELL_ME_ITEMS: List<TellMeItem> = listOf(
    // ---- Home
    TellMeItem("Font", "Home", null, "typeface family", control = TellMeControl.Choice { vm ->
        val f = vm.format?.font.orEmpty()
        if (vm.themeFonts.getOrNull(1)?.equals(f, ignoreCase = true) == true) "$f (Body)" else f
    }) { it.showPanel("Home", "font") },
    TellMeItem("Font Size", "Home", null, "bigger smaller larger text size", control = TellMeControl.Choice { vm -> sizeLabel(vm.format?.size ?: 11f) }) { it.showPanel("Home", "size") },
    toggle("Bold", "Home", Glyph.Letters("B", weight = FontWeight.Bold), "strong heavy", on = { it.format?.bold == true }) { it.command("home.bold") },
    toggle("Italic", "Home", Glyph.Letters("I", italic = true, family = androidx.compose.ui.text.font.FontFamily.Serif), "slanted oblique", on = { it.format?.italic == true }) { it.command("home.italic") },
    toggle("Underline", "Home", Glyph.Letters("U", decoration = TextDecoration.Underline), on = { it.format?.underline == true }) { it.command("home.underline") },
    toggle("Strikethrough", "Home", Glyph.Letters("ab", decoration = TextDecoration.LineThrough), "cross out", on = { it.format?.strike == true }) { it.command("home.strikethrough") },
) + BORDER_PRESETS.map { (label, preset) ->
    TellMeItem(label, "Home", Glyph.Border(preset), "borders lines edges box outline") { it.applyBorder(preset) }
} + listOf(
    TellMeItem("No Border", "Home", Glyph.Border("none"), "borders lines clear remove") { it.applyBorder("none") },
    page("Border Line Style", "Home", "borderStyle", Glyph.LineWeights, "borders weight dashed dotted thick"),
    page("Border Line Color", "Home", "borderColor", null, "borders colour"),
    TellMeItem("Fill Color", "Home", null, "background shading colour highlight", more = { it.showPanel("Home", "fillColor") }, look = { Glyph.WithBar(LucideIcons.PaintBucket.glyph(), Color(it.lastFill.second)) }) { it.applyLastFill() },
    TellMeItem("Font Color", "Home", null, "text colour", more = { it.showPanel("Home", "fontColor") }, look = { Glyph.WithBar(Glyph.Letters("A"), Color(it.lastFontColor.second)) }) { it.applyLastFontColor() },
    toggle("Align Top", "Home", Glyph.CellLines("top"), "vertical alignment", on = { it.format?.down == Down.TOP }) { it.command("home.alignTop") },
    toggle("Align Middle", "Home", Glyph.CellLines("middle"), "vertical alignment centre center", on = { it.format?.down == Down.CENTER }) { it.command("home.alignMiddle") },
    toggle("Align Bottom", "Home", Glyph.CellLines("bottom"), "vertical alignment", on = { it.format?.down == Down.BOTTOM }) { it.command("home.alignBottom") },
    toggle("Align Left", "Home", LucideIcons.AlignLeft.glyph(), "alignment", on = { it.format?.across == Across.LEFT }) { it.command("home.alignLeft") },
    toggle("Center", "Home", LucideIcons.AlignCenter.glyph(), "centre alignment", on = { it.format?.across == Across.CENTER }) { it.command("home.alignCenter") },
    toggle("Align Right", "Home", LucideIcons.AlignRight.glyph(), "alignment", on = { it.format?.across == Across.RIGHT }) { it.command("home.alignRight") },
    toggle("Merge & Center", "Home", LucideIcons.Columns2.glyph(), "combine join unmerge cells", on = { it.format?.merged == true }) { vm ->
        vm.command(if (vm.format?.merged == true) "home.unmergeCells" else "home.mergeCenter")
    },
    toggle("Wrap Text", "Home", LucideIcons.TextCursorInput.glyph(), "lines fit", on = { it.format?.wrap == true }) { vm -> vm.command("home.wrapText", json("on" to (vm.format?.wrap != true))) },
) + listOf("Set Text Direction to Context" to "context", "Left-to-Right Text Direction" to "ltr", "Right-to-Left Text Direction" to "rtl").mapIndexed { i, (label, direction) ->
    toggle(label, "Home", listOf(LucideIcons.Pilcrow, LucideIcons.PilcrowRight, LucideIcons.PilcrowLeft)[i].glyph(), "reading order", on = { (it.format?.readingOrder?.toInt() ?: 0) == i }) {
        it.command("home.textDirection", json("direction" to direction))
    }
} + listOf(
    toggle("Format Painter", "Home", LucideIcons.Paintbrush.glyph(), "copy formatting brush", on = { it.formatPainting }) { vm ->
        vm.showTab("Home")
        vm.startFormatPainter()
    },
    page("Cell Styles", "Home", "cellStyles", LucideIcons.SwatchBook.glyph(), "heading title good bad neutral total"),
    cmd("Increase Decimal", "Home", "home.increaseDecimal", LucideIcons.DecimalsArrowRight.glyph(), "decimal places number format"),
    cmd("Decrease Decimal", "Home", "home.decreaseDecimal", LucideIcons.DecimalsArrowLeft.glyph(), "decimal places round number format"),
    cmd("Comma Style", "Home", "home.comma", Glyph.Letters("❜"), "thousands separator number format"),
    // each number format by name, put on at once; the split ones' arrows open their kinds
    toggle("General", "Home", Glyph.Letters("123"), "number format", on = { it.format?.numberKind == "General" }) { it.command("home.numberFormat", """{"format": "General"}""") },
    split("Number", "Home", "numberNegatives", Glyph.Letters("12"), "number format decimal negative", on = { it.format?.numberKind == "Number" }) { it.command("home.numberFormat", """{"format": "Number"}""") },
    split("Currency", "Home", "currencyFormats", LucideIcons.Banknote.glyph(), "number format money", on = { it.format?.numberKind == "Currency" }) { it.setNumberCode(it.currencySymbol.currencyCodes()[0]) },
    TellMeItem("Choose Symbol", "Home", null, "currency money number format", control = TellMeControl.Choice { vm -> "${vm.currencySymbol.symbol}   ${vm.currencySymbol.place}" }) { it.showPanel("Home", "currencySymbols") },
    page("More Currency Symbols", "Home", "moreSymbols:currency", null, "money number format"),
    split("Accounting Format", "Home", "accountingFormats", LucideIcons.Receipt.glyph(), "number format money", on = { it.format?.numberKind == "Accounting" }) { it.setNumberCode(it.currencySymbol.accountingCode()) },
    page("More Accounting Symbols", "Home", "moreSymbols:accounting", null, "currency number format"),
    split("Date", "Home", "dateFormats", LucideIcons.CalendarDays.glyph(), "number format day", on = { it.format?.numberKind == "Date" }) { it.command("home.numberFormat", """{"format": "Short Date"}""") },
    split("Time", "Home", "timeFormats", LucideIcons.Clock3.glyph(), "number format hour", on = { it.format?.numberKind == "Time" }) { it.command("home.numberFormat", """{"format": "Time"}""") },
    toggle("Percentage", "Home", LucideIcons.Percent.glyph(), "number format percent", on = { it.format?.numberKind == "Percentage" }) { it.command("home.numberFormat", """{"format": "Percentage"}""") },
    toggle("Fraction", "Home", Glyph.Letters("½"), "number format", on = { it.format?.numberKind == "Fraction" }) { it.command("home.numberFormat", """{"format": "Fraction"}""") },
    toggle("Scientific", "Home", Glyph.Letters("10²"), "number format exponent", on = { it.format?.numberKind == "Scientific" }) { it.command("home.numberFormat", """{"format": "Scientific"}""") },
    toggle("Text", "Home", Glyph.Letters("ab"), "number format", on = { it.format?.numberKind == "Text" }) { it.command("home.numberFormat", """{"format": "Text"}""") },
    split("Special Format", "Home", "specialFormats", LucideIcons.Hash.glyph(), "number format zip phone social security") { it.setNumberCode(SPECIAL_FORMATS[0].second) },
    TellMeItem("Choose Location", "Home", null, "special format language", control = TellMeControl.Choice { "English (United States)" }) { it.showPanel("Home", "specialFormats") },
    TellMeItem("Cards View", "Home", LucideIcons.GalleryVerticalEnd.glyph(), "table rows records") { it.openCards() },
    cmd("Insert Rows", "Home", "home.insertCells", LucideIcons.BetweenHorizontalStart.glyph(), "add row", """{"shift": "row"}"""),
    cmd("Insert Columns", "Home", "home.insertCells", LucideIcons.BetweenVerticalStart.glyph(), "add column", """{"shift": "column"}"""),
    cmd("Insert & Shift Right", "Home", "home.insertCells", LucideIcons.SquareArrowRight.glyph(), "add cells", """{"shift": "right"}"""),
    cmd("Insert & Shift Down", "Home", "home.insertCells", LucideIcons.SquareArrowDown.glyph(), "add cells", """{"shift": "down"}"""),
    cmd("Delete Rows", "Home", "home.deleteCells", LucideIcons.ListX.glyph(), "remove row", """{"shift": "row"}"""),
    cmd("Delete Columns", "Home", "home.deleteCells", LucideIcons.Grid2x2X.glyph(), "remove column", """{"shift": "column"}"""),
    cmd("Delete & Shift Left", "Home", "home.deleteCells", LucideIcons.SquareArrowLeft.glyph(), "remove cells", """{"shift": "left"}"""),
    cmd("Delete & Shift Up", "Home", "home.deleteCells", LucideIcons.SquareArrowUp.glyph(), "remove cells", """{"shift": "up"}"""),
    TellMeItem("Row Height", "Home", null, "size tall format cell size", control = TellMeControl.Field({ vm -> vm.format?.rowHeight?.let { "%.1f".format(it) }.orEmpty() }) { vm, t -> vm.setSize(true, t) }) { },
    cmd("AutoFit Row Height", "Home", "home.autofitRowHeight", LucideIcons.UnfoldVertical.glyph(), "fit size tall format cell size"),
    TellMeItem("Column Width", "Home", null, "size wide format cell size", control = TellMeControl.Field({ vm -> vm.format?.colWidth?.let { "%.2f".format(it) }.orEmpty() }) { vm, t -> vm.setSize(false, t) }) { },
    TellMeItem("AutoFit Column Width", "Home", LucideIcons.UnfoldHorizontal.glyph(), "fit size wide format cell size") { vm ->
        val r = vm.selection?.ranges?.lastOrNull()
        if (r != null) vm.autofitColumns(r.firstCol, r.lastCol) else vm.command("home.autofitColumnWidth")
    },
    cmd("Hide Rows", "Home", "home.hideRows", null, "format cell size"),
    cmd("Hide Columns", "Home", "home.hideColumns", null, "format cell size"),
    cmd("Clear All", "Home", "edit.clearAll", LucideIcons.Eraser.glyph(), "delete erase remove"),
    cmd("Clear Contents", "Home", "edit.clearContents", LucideIcons.Delete.glyph(), "delete erase remove values"),
    cmd("Clear Formats", "Home", "edit.clearFormats", LucideIcons.RemoveFormatting.glyph(), "delete erase remove formatting"),
) + listOf("Sum" to "SUM", "Average" to "AVERAGE", "Count Numbers" to "COUNT", "Max" to "MAX", "Min" to "MIN").map { (label, fn) ->
    cmd(label, "Home", "formulas.autoSum", if (fn == "SUM") LucideIcons.Sigma.glyph() else null, "autosum total function formula", """{"function": "$fn"}""")
} + listOf(
    cmd("Sort Ascending", "Home", "data.sortAscending", LucideIcons.ArrowDownAZ.glyph(), "order a to z smallest sort & filter"),
    cmd("Sort Descending", "Home", "data.sortDescending", LucideIcons.ArrowDownZA.glyph(), "order z to a largest sort & filter"),
    toggle("Add or Remove Filters", "Home", LucideIcons.Funnel.glyph(), "autofilter sort & filter buttons", on = { it.sheetView?.autofilter == true }) { it.command("data.filter") },
    cmd("Clear All Filters", "Home", "data.clearFilter", LucideIcons.FunnelX.glyph(), "sort & filter", enabled = FILTERED),
    cmd("Reapply All", "Home", "data.reapply", LucideIcons.RefreshCw.glyph(), "filters sort & filter", enabled = FILTERED),
    TellMeItem("Find", "Home", LucideIcons.Search.glyph(), "search look for") { it.openFind() },
    TellMeItem("Replace", "Home", LucideIcons.Replace.glyph(), "find substitute change") { vm ->
        vm.openFind()
        vm.replaceOpen = true
    },
    away("Fill", "Home", null, "fill handle drag series copy down right") { it.fillMode = true },
    away("Select Cell or Range", "Home", LucideIcons.SquareDashedMousePointer.glyph(), "go to reference jump") { it.dialog = "goTo" },
    // ---- the sheet and the workbook
    TellMeItem("Copy", "Home", LucideIcons.Copy.glyph(), "duplicate clipboard") { vm ->
        vm.collapsePalette()
        vm.copy(cut = false)
    },
    TellMeItem("Cut", "Home", LucideIcons.Scissors.glyph(), "move clipboard") { vm ->
        vm.collapsePalette()
        vm.copy(cut = true)
    },
    TellMeItem("Paste", "Home", LucideIcons.ClipboardPaste.glyph(), "clipboard", enabled = { it.canPaste }) { vm ->
        vm.collapsePalette()
        vm.paste()
    },
    TellMeItem("Undo", "Home", LucideIcons.Undo2.glyph(), "back revert", enabled = { it.info?.canUndo == true }) { it.undo() },
    TellMeItem("Redo", "Home", LucideIcons.Redo2.glyph(), "again", enabled = { it.info?.canRedo == true }) { it.redo() },
    away("Add a Line Break", "Home", null, "new line enter wrap") { vm ->
        if (vm.editing) vm.editText = vm.editText + "\n" else vm.beginEdit(vm.selection?.formula.orEmpty() + "\n")
    },
    away("Rename Sheet", "Home", null, "name tab") { it.renaming = it.info?.activeSheet?.toInt() ?: 0 },
    TellMeItem("Insert Worksheet", "Home", null, "new sheet add tab") { it.addSheet() },
    TellMeItem("Duplicate Sheet", "Home", null, "copy tab") { it.duplicateSheet(it.info?.activeSheet?.toInt() ?: 0) },
    TellMeItem("Delete", "Home", null, "remove sheet tab", enabled = { (it.info?.sheets?.count { s -> !s.hidden } ?: 0) > 1 }) { it.deleteSheet(it.info?.activeSheet?.toInt() ?: 0) },
    page("Move Sheet", "Home", "moveSheet", null, "order sheets tabs"),
    TellMeItem("Tab Color", "Home", null, "sheet colour", more = { it.chooseTabColor(it.info?.activeSheet?.toInt() ?: 0) }) { it.applyLastTabColor() },
    // ---- Insert
    TellMeItem("Insert Table", "Insert", LucideIcons.Table.glyph(), "list range format as table") { vm ->
        vm.showTab("Insert")
        vm.makeTable("insert.table")
    },
    away("Insert Pictures", "Insert", LucideIcons.Image.glyph(), "photo image gallery") { it.requestPicture("photo", inCell = false) },
    away("Insert Pictures from Camera", "Insert", LucideIcons.Camera.glyph(), "photo image") { it.requestPicture("camera", inCell = false) },
    away("Insert Cell Picture from file", "Insert", LucideIcons.Image.glyph(), "photo image in cell") { it.requestPicture("file", inCell = true) },
    away("Insert Picture from Camera in Cell", "Insert", LucideIcons.Camera.glyph(), "photo image") { it.requestPicture("camera", inCell = true) },
    page("Insert a Shape", "Insert", "shapes", LucideIcons.Shapes.glyph(), "rectangle oval arrow line"),
    TellMeItem("Insert Text Box", "Insert", LucideIcons.Type.glyph()) { it.insertShape("textBox") },
    page("Insert Recommended Chart", "Insert", "recommendedCharts", LucideIcons.ChartColumn.glyph(), "graph"),
    // ---- Insert: the chart families, each opening its gallery
) + CHART_FAMILIES.map { (name, icon, _) -> page(name, "Insert", CHART_FAMILY + name, Glyph.Chart(icon), "chart graph") } + listOf(
    away("Insert Comment", "Insert", LucideIcons.MessageSquarePlus.glyph(), "new note remark") { it.dialog = "comment" },
    cmd("Checkbox", "Insert", "insert.checkbox", LucideIcons.SquareCheck.glyph(), "tick box"),
    page("Insert Hyperlink", "Insert", "links", LucideIcons.Link.glyph(), "web address url"),
    away("Insert a Link", "Insert", LucideIcons.Link.glyph(), "hyperlink web address url") { it.dialog = "link" },
    // ---- Draw
    toggle("Draw with Pen", "Draw", LucideIcons.PenTool.glyph(), "ink handwriting sketch", on = { it.drawTool == "pen" }) { it.drawWithPen() },
    toggle("Draw with Mouse or Touch", "Draw", LucideIcons.Pointer.glyph(), "ink pen handwriting sketch", on = { it.drawTool == "pen" }) { vm ->
        vm.showTab("Draw")
        vm.pickPen(vm.penIndex)
    },
    toggle("Pen", "Draw", null, "ink", on = { it.drawTool == "pen" && it.pens.getOrNull(it.penIndex)?.kind == "pen" }) { vm ->
        vm.showTab("Draw")
        vm.pickPen(vm.pens.indexOfFirst { p -> p.kind == "pen" }.coerceAtLeast(0))
    },
    toggle("Highlighter", "Draw", null, "ink marker", on = { it.drawTool == "pen" && it.pens.getOrNull(it.penIndex)?.kind == "highlighter" }) { vm ->
        vm.showTab("Draw")
        val i = vm.pens.indexOfFirst { p -> p.kind == "highlighter" }
        if (i >= 0) vm.pickPen(i) else vm.addPen("highlighter")
    },
    page("Ink Color", "Draw", "pen", null, "pen colour"),
    page("Thickness", "Draw", "pen", null, "pen ink width"),
    toggle("Stroke Eraser", "Draw", LucideIcons.Eraser.glyph(), "ink erase", on = { it.drawTool == "eraser" }) { vm ->
        vm.showTab("Draw")
        vm.chooseTool("eraser")
    },
    // ---- Formulas
    page("Recently Used", "Formulas", "fn:Recent", null, "functions formulas"),
) + FUNCTION_CATEGORIES.map { (cat, label) -> page(TELL_ME_FUNCTION_NAMES[label] ?: label, "Formulas", "fn:$cat", null, "functions formulas") } + listOf(
    cmd("Calculate Now", "Formulas", "formulas.calculateNow", LucideIcons.Calculator.glyph(), "recalculate refresh"),
    // ---- Data
    cmd("Show Detail", "Data", "data.showDetail", LucideIcons.Plus.glyph(), "expand outline group", enabled = OUTLINED),
    cmd("Hide Detail", "Data", "data.hideDetail", LucideIcons.Minus.glyph(), "collapse outline group", enabled = OUTLINED),
    cmd("Refresh", "Pivot Table", "pivot.refresh", LucideIcons.RefreshCw.glyph(), "pivot data", enabled = { it.pivotInfo != null }),
    // ---- Review
    TellMeItem("Show Comments", "Review", LucideIcons.MessageSquare.glyph(), "notes remarks") { it.showComments(!it.commentsOpen) },
    cmd("Convert All Notes", "Review", "review.convertToComments", LucideIcons.StickyNote.glyph(), "comments", enabled = { it.hasNotes }),
    // ---- View: Sheet View is greyed, as the phone app greys it signed out
    TellMeItem("Sheet View", "View", null, "default", enabled = NEVER, control = TellMeControl.Choice { "Default" }) { },
    TellMeItem("New Sheet View", "View", LucideIcons.Eye.glyph(), enabled = NEVER) { },
    TellMeItem("Exit Sheet View", "View", LucideIcons.EyeOff.glyph(), enabled = NEVER) { },
    TellMeItem("Keep Sheet View", "View", LucideIcons.Save.glyph(), enabled = NEVER) { },
    TellMeItem("Sheet View Options", "View", LucideIcons.List.glyph(), "settings", enabled = NEVER) { },
    TellMeItem("Show Formula Bar", "View", null, "hide", control = TellMeControl.Check { it.showFormulaBar }) { it.showFormulaBar = !it.showFormulaBar },
    viewSetting("Show Gridlines", "view.gridlines", "hide lines") { it.sheetView?.gridlines == true },
    viewSetting("Show Headings", "view.headings", "hide row column numbers letters") { it.sheetView?.headings == true },
    toggle("Toggle Sheet Right-to-Left Direction", "View", null, "arabic hebrew", on = { it.sheetView?.rightToLeft == true }) { vm ->
        vm.command("view.rightToLeft", json("on" to (vm.sheetView?.rightToLeft != true)))
    },
    TellMeItem("Zoom In", "View", LucideIcons.ZoomIn.glyph(), "bigger magnify") { it.zoom = (it.zoom + 0.1f).coerceAtMost(4f) },
    TellMeItem("Zoom Out", "View", LucideIcons.ZoomOut.glyph(), "smaller") { it.zoom = (it.zoom - 0.1f).coerceAtLeast(0.1f) },
    toggle("Freeze Panes", "View", LucideIcons.PanelsTopLeft.glyph(), "lock unfreeze", on = ::frozenPanes) { vm ->
        vm.command(if (frozenPanes(vm)) "view.unfreezePanes" else "view.freezePanes")
    },
    toggle("Freeze Top Row", "View", null, "lock freeze panes", on = { it.sheetView?.frozenRows == 1u && it.sheetView?.frozenCols == 0u }) { vm ->
        vm.command(if (vm.sheetView?.frozenRows == 1u && vm.sheetView?.frozenCols == 0u) "view.unfreezePanes" else "view.freezeTopRow")
    },
    toggle("Freeze First Column", "View", null, "lock freeze panes", on = { it.sheetView?.frozenRows == 0u && it.sheetView?.frozenCols == 1u }) { vm ->
        vm.command(if (vm.sheetView?.frozenRows == 0u && vm.sheetView?.frozenCols == 1u) "view.unfreezePanes" else "view.freezeFirstColumn")
    },
    // ---- Table, greyed outside a table
    cmd("Insert Rows Above", "Table", "table.insert", LucideIcons.BetweenHorizontalStart.glyph(), "add row table", """{"where": "above"}""", enabled = { it.tableInfo != null && it.tableInfo?.inHeader != true }),
    cmd("Insert Rows Below", "Table", "table.insert", LucideIcons.BetweenHorizontalEnd.glyph(), "add row table", """{"where": "below"}""", enabled = TABLE),
    cmd("Insert Columns to the Left", "Table", "table.insert", LucideIcons.BetweenVerticalStart.glyph(), "add column table", """{"where": "left"}""", enabled = TABLE),
    cmd("Insert Columns to the Right", "Table", "table.insert", LucideIcons.BetweenVerticalEnd.glyph(), "add column table", """{"where": "right"}""", enabled = TABLE),
    tableOption("Toggle Header Row", "table.headerRow", "table style options") { it.headerRow },
    tableOption("Total Row", "table.totalRow", "table style options sum") { it.totalsRow },
    tableOption("First Column", "table.firstColumn", "table style options") { it.firstCol },
    tableOption("Show Last Column", "table.lastColumn", "table style options") { it.lastCol },
    tableOption("Banded Rows", "table.bandedRows", "table style options stripes") { it.bandedRows },
    tableOption("Banded Columns", "table.bandedColumns", "table style options stripes") { it.bandedCols },
    cmd("Convert to Range", "Table", "table.convertToRange", LucideIcons.Table.glyph(), "table cells", enabled = TABLE),
    // ---- Chart, greyed until a chart is selected
    page("Change Chart Layout", "Chart", "chartLayout", LucideIcons.LayoutTemplate.glyph(), "quick layout", enabled = CHART),
    page("Change Chart Colors", "Chart", "chartColors", LucideIcons.SwatchBook.glyph(), "colour palette", enabled = CHART),
    page("Change Chart Style", "Chart", "chartStyle", LucideIcons.WandSparkles.glyph(), "format", enabled = CHART),
    TellMeItem("Switch Row/Column", "Chart", LucideIcons.ArrowLeftRight.glyph(), "data series", enabled = CHART) { it.chartCommand("chart.switchRowColumn") },
    // ---- Picture, Shape and Graphics
    page("Picture Styles", "Picture", "pictureStyles", LucideIcons.SwatchBook.glyph(), "image frame", enabled = PICTURE),
    page("Shadow Picture Effect", "Picture", "pictureShadow", LucideIcons.Image.glyph(), "image effects", enabled = PICTURE),
    page("Picture Reflection", "Picture", "pictureReflection", LucideIcons.FlipVertical2.glyph(), "image effects", enabled = PICTURE),
    toggle("Crop Picture", "Picture", LucideIcons.Crop.glyph(), "image trim", enabled = PICTURE, on = { it.cropping }) { vm ->
        vm.showTab("Picture")
        vm.toggleCrop()
    },
    TellMeItem("Restore Original Picture Size and Rotation", "Picture", null, "reset image", enabled = PICTURE) { it.resetPictureSize() },
    TellMeItem("Remove All Picture Formatting Changes", "Picture", null, "reset image", enabled = PICTURE) { it.resetPicture() },
    page("Change Shape", "Shape", "changeShape", LucideIcons.Shapes.glyph(), "outline", enabled = { (SHAPE(it) && it.selectedObject?.isLine != true) || PICTURE(it) }),
    TellMeItem("Shape Fill", "Shape", null, "colour", enabled = { SHAPE(it) && it.selectedObject?.isLine != true }, more = { it.showPanel("Shape", "shapeFill") }, look = ::fillLook) { it.applyLastShapeFill() },
    split("Shape Quick Styles", "Shape", "shapeStyles", Glyph.Letters("Abc"), "shape styles", enabled = SHAPE) { vm -> vm.recentShapeStyles.firstOrNull()?.let(vm::applyShapeStyle) },
    TellMeItem("Graphics Fill", "Graphics", null, "icon colour", enabled = GRAPHIC, more = { it.showPanel("Graphics", "shapeFill") }, look = ::fillLook) { it.applyLastShapeFill() },
    TellMeItem("Graphics Outline", "Graphics", null, "icon line", enabled = GRAPHIC, more = { it.showPanel("Graphics", "shapeOutline") }, look = ::outlineLook) { it.applyLastShapeOutline() },
    page("Change Outline Color", "Shape", "shapeOutline", LucideIcons.Pencil.glyph(), "shape line colour", enabled = SHAPE),
    // ---- any object
    TellMeItem("Bring Forward", "Shape", null, "arrange order", enabled = STACKED) { it.arrangeObject(forward = true, all = false) },
    TellMeItem("Bring to Front", "Shape", null, "arrange order", enabled = STACKED) { it.arrangeObject(forward = true, all = true) },
    TellMeItem("Send Backward", "Shape", null, "arrange order", enabled = STACKED) { it.arrangeObject(forward = false, all = false) },
    TellMeItem("Send to Back", "Shape", null, "arrange order", enabled = STACKED) { it.arrangeObject(forward = false, all = true) },
    TellMeItem("Rotate Right 90°", "Shape", null, "arrange turn", enabled = TURNS) { it.rotateObject(90.0, null) },
    TellMeItem("Rotate Left 90°", "Shape", null, "arrange turn", enabled = TURNS) { it.rotateObject(-90.0, null) },
    TellMeItem("Flip Vertical", "Shape", null, "arrange mirror", enabled = TURNS) { it.rotateObject(0.0, "vertical") },
    TellMeItem("Flip Horizontal", "Shape", null, "arrange mirror", enabled = TURNS) { it.rotateObject(0.0, "horizontal") },
    TellMeItem("Height", "Shape", null, "size", enabled = OBJECT, control = TellMeControl.Field({ vm -> inches(vm.selectedObject?.height ?: 0.0) }) { vm, t ->
        val o = vm.selectedObject
        val v = t.replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull()
        if (o != null && v != null && v > 0) vm.sizeObject(v, o.width / 96.0)
    }) { },
    TellMeItem("Width", "Shape", null, "size", enabled = OBJECT, control = TellMeControl.Field({ vm -> inches(vm.selectedObject?.width ?: 0.0) }) { vm, t ->
        val o = vm.selectedObject
        val v = t.replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull()
        if (o != null && v != null && v > 0) vm.sizeObject(o.height / 96.0, v)
    }) { },
    TellMeItem("Insert Alt Text", "Shape", null, "description screen reader accessibility", enabled = OBJECT, control = TellMeControl.Field({ vm -> vm.selectedObject?.alt.orEmpty() }, lines = 4) { vm, t ->
        if (vm.selectedObject != null) vm.setAltText(t)
    }) { },
    // ---- the file
    TellMeItem("Save", "File", LucideIcons.Save.glyph(), "keep store") { it.save() },
    TellMeItem("Rename or Save", "File", LucideIcons.FilePenLine.glyph(), "save as copy name") { it.saveCopy() },
    away("Print", "File", LucideIcons.Printer.glyph(), "paper") { it.dialog = "print" },
    TellMeItem("Share", "File", LucideIcons.Share2.glyph(), "send workbook") { it.share("xlsx") },
    TellMeItem("Share as PDF", "File", LucideIcons.FileText.glyph(), "send export") { it.share("pdf") },
    TellMeItem("New File", "File", LucideIcons.CirclePlus.glyph(), "blank workbook create") { vm ->
        vm.startTab = "New"
        vm.closeWorkbook()
    },
    TellMeItem("Open File", "File", LucideIcons.FolderOpen.glyph(), "browse workbook") { vm ->
        vm.startTab = "Open"
        vm.closeWorkbook()
    },
    TellMeItem("Close File", "File", LucideIcons.X.glyph(), "exit workbook") { it.closeWorkbook() },
    TellMeItem("Manage Settings", "File", LucideIcons.Settings.glyph(), "options preferences") { it.openSettings() },
)

/** Fill and Outline's icons, over the colour each puts on next (as the Shape tab shows them). */
private fun fillLook(vm: AppViewModel) = Glyph.WithBar(LucideIcons.PaintBucket.glyph(), Color(vm.shapeFillChoice?.second ?: 0xFFED7D31.toInt()))

private fun outlineLook(vm: AppViewModel) = Glyph.WithBar(LucideIcons.Pencil.glyph(), Color(vm.shapeOutlineChoice?.second ?: 0xFF4472C4.toInt()))

/** An object's size as the Size page shows it: `2.5 in` (from sheet units, 96 to the inch). */
private fun inches(units: Double) = String.format(java.util.Locale.US, "%.2f", units / 96.0).trimEnd('0').trimEnd('.') + " in"

/**
 * Suggested Actions, shown before anything is typed, as the phone app shows a few things to ask
 * for in quotes: each in our words, with the command it runs.
 */
internal val TELL_ME_SUGGESTED = listOf(
    "comment on this cell" to "Insert Comment",
    "make a table" to "Insert Table",
    "keep the top row in view" to "Freeze Top Row",
    "send this workbook" to "Share",
    "fill the cell with color" to "Fill Color",
)

/**
 * The items matching [query]: names starting with it first, then names containing it, then
 * those whose other words start with each word typed.
 */
internal fun tellMeMatches(query: String, items: List<TellMeItem> = TELL_ME_ITEMS): List<TellMeItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val words = q.split(' ').filter { it.isNotEmpty() }
    fun rank(item: TellMeItem): Int? {
        val name = item.label.lowercase()
        val all = (name + " " + item.words.lowercase()).split(' ', '-', '&').filter { it.isNotEmpty() }
        return when {
            name.startsWith(q) -> 0
            name.contains(q) -> 1
            words.all { w -> all.any { it.startsWith(w) } } -> 2
            else -> null
        }
    }
    return items.mapNotNull { item -> rank(item)?.let { item to it } }.sortedBy { it.second }.map { it.first }
}

/**
 * Tell Me's search field, under its results: a light bulb, the field, and clear once something is
 * typed. As in the phone app the keyboard waits for a tap on the field.
 */
@Composable
private fun TellMeField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(GridColors.Strip).padding(horizontal = 8.dp, vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp).background(GridColors.FormulaFill).border(1.dp, GridColors.TellMeEdge), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { GlyphIcon(LucideIcons.Lightbulb.glyph(), GridColors.HeaderIcon, size = 22.dp) }
            Box(Modifier.weight(1f).padding(start = 4.dp, end = 4.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search for a command", color = GridColors.Disabled, fontSize = 16.sp, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = GridColors.OnSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(GridColors.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) BarButton(LucideIcons.X.glyph(), GridColors.HeaderIcon, { onQuery("") }, width = 32.dp, height = 32.dp, label = "Clear Search")
        }
    }
}

/** The phone app's Tell Me shows this many results at most. */
private const val TELL_ME_SHOWN = 5

/**
 * Tell Me, in the palette's place: the matches (none when nothing matches), or Suggested Actions,
 * over the search field; only as high as they are, so the sheet shows above, as the phone app's.
 * A command closes the palette; a page, a list or a split button's arrow opens on it.
 */
@Composable
internal fun TellMeSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    // what applies first, the greyed after, as the phone app lists them
    val matches = tellMeMatches(query).sortedBy { !it.enabled(vm) }.take(TELL_ME_SHOWN)
    val keyboard = LocalSoftwareKeyboardController.current
    // what the results show: the body font's name, whether there are notes to convert or cells to paste
    LaunchedEffect(vm.info?.revision) {
        vm.loadThemeFonts()
        vm.loadHasNotes()
        vm.loadCanPaste()
    }
    val pick = { item: TellMeItem ->
        keyboard?.hide()
        vm.tellMeOpen = false
        if (!item.opens && item.control == null) vm.collapsePalette()
        item.run(vm)
    }
    val open = { item: TellMeItem ->
        keyboard?.hide()
        vm.tellMeOpen = false
        item.more?.invoke(vm)
        Unit
    }
    val set = { item: TellMeItem, text: String ->
        keyboard?.hide()
        (item.control as? TellMeControl.Field)?.set?.invoke(vm, text)
        Unit
    }
    Column(modifier.background(GridColors.Surface)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = PALETTE_HEIGHT)) {
            if (query.isBlank()) {
                val byName = TELL_ME_ITEMS.associateBy { it.label }
                val suggested = TELL_ME_SUGGESTED.mapNotNull { (phrase, label) -> byName[label]?.let { phrase to it } }
                item {
                    Box(Modifier.fillMaxWidth().height(26.dp).background(GridColors.Strip).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text("Suggested Actions", color = GridColors.OnSurface, fontSize = 14.sp)
                    }
                }
                items(suggested.size) { i ->
                    val (phrase, target) = suggested[i]
                    PaletteRow("\"$phrase\"", null, onClick = { pick(target) }, divider = false)
                }
            } else {
                items(matches.size) { i -> TellMeRow(vm, matches[i], pick, open, set) }
            }
        }
        TellMeField(query, { query = it }, { matches.firstOrNull { it.enabled(vm) && it.control !is TellMeControl.Field }?.let(pick) })
    }
}

/** A result as its control: a button (lit, split, or with a chevron), a list's choice, a field or a tick box. */
@Composable
private fun TellMeRow(vm: AppViewModel, item: TellMeItem, pick: (TellMeItem) -> Unit, open: (TellMeItem) -> Unit, set: (TellMeItem, String) -> Unit) {
    val enabled = item.enabled(vm)
    val ink = if (enabled) GridColors.OnSurface else GridColors.Disabled
    when (val c = item.control) {
        is TellMeControl.Choice -> Column(Modifier.fillMaxWidth()) {
            Text(item.label, color = ink, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
            Row(
                Modifier.fillMaxWidth().height(ROW + 1.dp).background(GridColors.Surface).clickable(enabled = enabled) { pick(item) }.padding(start = 16.dp, end = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.value(vm), color = ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                GlyphIcon(LucideIcons.ChevronRight.glyph(), if (enabled) GridColors.OnStrip else GridColors.Disabled, size = 20.dp)
            }
        }
        is TellMeControl.Field -> SizeField(item.label, c.value(vm), enabled = enabled, lines = c.lines) { set(item, it) }
        is TellMeControl.Check -> CheckPaletteRow(item.label, c.checked(vm), enabled) { pick(item) }
        null -> PaletteRow(
            item.label,
            item.look?.invoke(vm) ?: item.glyph,
            onClick = { pick(item) },
            chevron = item.opens,
            pressed = enabled && item.on?.invoke(vm) == true,
            onOpen = item.more?.let { { open(item) } },
            enabled = enabled,
            divider = false,
        )
    }
}
