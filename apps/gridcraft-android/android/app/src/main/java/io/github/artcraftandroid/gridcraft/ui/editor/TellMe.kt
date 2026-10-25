package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons

/**
 * A command Tell Me finds: its name as the palette shows it, the tab it is on, other words people
 * use for it, whether it opens a page (a chevron), and what picking it does.
 */
internal class TellMeItem(
    val label: String,
    val tab: String,
    val glyph: Glyph?,
    val words: String = "",
    val opens: Boolean = false,
    val run: (AppViewModel) -> Unit,
)

private fun page(label: String, tab: String, panel: String, glyph: Glyph?, words: String = "") =
    TellMeItem(label, tab, glyph, words, opens = true) { it.showPanel(tab, panel) }

private fun cmd(label: String, tab: String, id: String, glyph: Glyph?, words: String = "", params: String = "{}") =
    TellMeItem(label, tab, glyph, words) { it.command(id, params) }

/** Picks that close the palette first, then act on the sheet. */
private fun away(label: String, tab: String, glyph: Glyph?, words: String = "", act: (AppViewModel) -> Unit) =
    TellMeItem(label, tab, glyph, words) { vm ->
        vm.collapsePalette()
        act(vm)
    }

/** A View setting's tick box, turned the other way. */
private fun viewSetting(label: String, id: String, words: String, on: (AppViewModel) -> Boolean) =
    TellMeItem(label, "View", null, words) { vm -> vm.command(id, json("on" to !on(vm))) }

/** The function categories of the Formulas tab, with the panel each opens. */
private val FUNCTION_CATEGORIES = listOf(
    "Financial" to "Financial", "Logical" to "Logical", "Text" to "Text", "DateTime" to "Date & Time",
    "Lookup" to "Lookup & Reference", "MathTrig" to "Math & Trig", "Statistical" to "Statistical",
    "Engineering" to "Engineering", "Information" to "Information", "Database" to "Database", "Compatibility" to "Compatibility",
)

/** The palette's commands Tell Me searches, tab by tab, with the choices of their pages people look for most. */
internal val TELL_ME_ITEMS: List<TellMeItem> = listOf(
    page("Font", "Home", "font", LucideIcons.Type.glyph(), "typeface family"),
    page("Font Size", "Home", "size", LucideIcons.ALargeSmall.glyph(), "bigger smaller larger text size"),
    cmd("Bold", "Home", "home.bold", Glyph.Letters("B", weight = FontWeight.Bold), "strong heavy"),
    cmd("Italic", "Home", "home.italic", Glyph.Letters("I", italic = true, family = androidx.compose.ui.text.font.FontFamily.Serif), "slanted oblique"),
    cmd("Underline", "Home", "home.underline", Glyph.Letters("U", decoration = TextDecoration.Underline)),
    cmd("Strikethrough", "Home", "home.strikethrough", Glyph.Letters("ab", decoration = TextDecoration.LineThrough), "cross out"),
    page("Borders", "Home", "borders", LucideIcons.SquareDashed.glyph(), "lines outline edges box"),
    page("Fill Color", "Home", "fillColor", LucideIcons.PaintBucket.glyph(), "background shading colour highlight"),
    page("Font Color", "Home", "fontColor", Glyph.Letters("A"), "text colour"),
    cmd("Top Align", "Home", "home.alignTop", Glyph.CellLines("top"), "vertical alignment"),
    cmd("Middle Align", "Home", "home.alignMiddle", Glyph.CellLines("middle"), "vertical alignment centre center"),
    cmd("Bottom Align", "Home", "home.alignBottom", Glyph.CellLines("bottom"), "vertical alignment"),
    cmd("Align Left", "Home", "home.alignLeft", LucideIcons.AlignLeft.glyph(), "alignment"),
    cmd("Center", "Home", "home.alignCenter", LucideIcons.AlignCenter.glyph(), "centre alignment"),
    cmd("Align Right", "Home", "home.alignRight", LucideIcons.AlignRight.glyph(), "alignment"),
    TellMeItem("Merge & Center", "Home", LucideIcons.Columns2.glyph(), "combine join unmerge cells") { vm ->
        vm.command(if (vm.format?.merged == true) "home.unmergeCells" else "home.mergeCenter")
    },
    TellMeItem("Wrap Text", "Home", LucideIcons.TextCursorInput.glyph(), "lines fit") { vm -> vm.command("home.wrapText", json("on" to (vm.format?.wrap != true))) },
    page("Text Direction", "Home", "textDirection", LucideIcons.Pilcrow.glyph(), "right-to-left left-to-right reading order"),
    TellMeItem("Format Painter", "Home", LucideIcons.Paintbrush.glyph(), "copy formatting brush") { vm ->
        vm.showTab("Home")
        vm.startFormatPainter()
    },
    page("Cell Styles", "Home", "cellStyles", LucideIcons.SwatchBook.glyph(), "heading title good bad neutral total"),
    page("Number Format", "Home", "numberFormat", LucideIcons.Hash.glyph(), "currency percentage date time accounting general"),
    cmd("Increase Decimal", "Home", "home.increaseDecimal", null, "decimal places"),
    cmd("Decrease Decimal", "Home", "home.decreaseDecimal", null, "decimal places round"),
    TellMeItem("Format as Table", "Home", LucideIcons.TableProperties.glyph(), "table style banded rows") { vm ->
        vm.showTab("Home")
        vm.makeTable("home.formatAsTable")
    },
    TellMeItem("Cards View", "Home", LucideIcons.GalleryVerticalEnd.glyph(), "table rows records") { it.openCards() },
    page("Insert & Delete", "Home", "insertDelete", LucideIcons.Table.glyph(), "add remove rows columns cells"),
    cmd("Insert Rows", "Home", "home.insertCells", null, "add row", """{"shift": "row"}"""),
    cmd("Insert Columns", "Home", "home.insertCells", null, "add column", """{"shift": "column"}"""),
    cmd("Delete Rows", "Home", "home.deleteCells", null, "remove row", """{"shift": "row"}"""),
    cmd("Delete Columns", "Home", "home.deleteCells", null, "remove column", """{"shift": "column"}"""),
    page("Format Cell Size", "Home", "cellSize", LucideIcons.ArrowLeftRight.glyph(), "row height column width autofit hide unhide"),
    TellMeItem("Row Height", "Home", null, "size tall") { it.dialog = "rowHeight" },
    TellMeItem("Column Width", "Home", null, "size wide") { it.dialog = "columnWidth" },
    TellMeItem("AutoFit Column Width", "Home", null, "fit size wide") { vm ->
        val r = vm.selection?.ranges?.lastOrNull()
        if (r != null) vm.autofitColumns(r.firstCol, r.lastCol) else vm.command("home.autofitColumnWidth")
    },
    cmd("Hide Rows", "Home", "home.hideRows", null),
    cmd("Hide Columns", "Home", "home.hideColumns", null),
    cmd("Unhide Rows", "Home", "home.unhideRows", null, "show"),
    cmd("Unhide Columns", "Home", "home.unhideColumns", null, "show"),
    page("Clear", "Home", "clear", LucideIcons.Eraser.glyph(), "delete erase remove contents formats"),
    page("AutoSum", "Home", "autoSum", LucideIcons.Sigma.glyph(), "sum total add average count max min"),
    page("Sort & Filter", "Home", "sortFilter", LucideIcons.ArrowUpDown.glyph(), "order arrange"),
    cmd("Sort Ascending", "Home", "data.sortAscending", null, "order a to z smallest"),
    cmd("Sort Descending", "Home", "data.sortDescending", null, "order z to a largest"),
    TellMeItem("Custom Sort", "Home", null, "order levels") { it.openCustomSort() },
    cmd("Filter", "Home", "data.filter", LucideIcons.Funnel.glyph(), "autofilter"),
    TellMeItem("Find", "Home", LucideIcons.Search.glyph(), "search look for") { it.openFind() },
    away("Select Range", "Home", LucideIcons.SquareDashedMousePointer.glyph(), "go to reference jump") { it.dialog = "goTo" },
    TellMeItem("Table", "Insert", LucideIcons.Table.glyph(), "list range") { vm ->
        vm.showTab("Insert")
        vm.makeTable("insert.table")
    },
    page("Pictures", "Insert", "pictures", LucideIcons.Image.glyph(), "photo image camera"),
    page("Shapes", "Insert", "shapes", LucideIcons.Shapes.glyph(), "rectangle oval arrow line"),
    TellMeItem("Text Box", "Insert", LucideIcons.Type.glyph()) { it.insertShape("textBox") },
    page("Recommended Charts", "Insert", "recommendedCharts", LucideIcons.ChartColumn.glyph(), "graph"),
    page("Chart", "Insert", "insertChart", LucideIcons.ChartColumn.glyph(), "graph column bar line pie"),
    away("Comment", "Insert", LucideIcons.MessageSquarePlus.glyph(), "new note remark") { it.dialog = "comment" },
    cmd("Checkbox", "Insert", "insert.checkbox", LucideIcons.SquareCheck.glyph(), "tick box"),
    away("Link", "Insert", LucideIcons.Link.glyph(), "hyperlink web address url") { it.dialog = "link" },
    TellMeItem("Draw with Mouse or Touch", "Draw", LucideIcons.Pointer.glyph(), "ink pen handwriting sketch") { vm ->
        vm.showTab("Draw")
        vm.pickPen(vm.penIndex)
    },
    TellMeItem("Add Pen", "Draw", LucideIcons.Plus.glyph(), "ink pencil highlighter", opens = true) { it.showPanel("Draw", "addPen") },
    TellMeItem("Eraser", "Draw", LucideIcons.Eraser.glyph(), "ink erase") { vm ->
        vm.showTab("Draw")
        vm.chooseTool("eraser")
    },
    page("Recently Used Functions", "Formulas", "fn:Recent", null, "formulas"),
) + FUNCTION_CATEGORIES.map { (cat, label) -> page(label, "Formulas", "fn:$cat", null, "functions formulas") } + listOf(
    cmd("Calculate Now", "Formulas", "formulas.calculateNow", LucideIcons.Calculator.glyph(), "recalculate refresh"),
    cmd("Show Detail", "Data", "data.showDetail", LucideIcons.Plus.glyph(), "expand outline group"),
    cmd("Hide Detail", "Data", "data.hideDetail", LucideIcons.Minus.glyph(), "collapse outline group"),
    TellMeItem("Show Comments", "Review", LucideIcons.MessageSquare.glyph(), "notes remarks") { it.showComments(!it.commentsOpen) },
    // it is greyed on the Review tab until the workbook has a note: picking it goes there
    TellMeItem("Convert All Notes", "Review", LucideIcons.StickyNote.glyph(), "comments", opens = true) { it.showTab("Review") },
    TellMeItem("Formula Bar", "View", null, "show hide") { it.showFormulaBar = !it.showFormulaBar },
    viewSetting("Gridlines", "view.gridlines", "show hide lines") { it.sheetView?.gridlines == true },
    viewSetting("Headings", "view.headings", "show hide row column numbers letters") { it.sheetView?.headings == true },
    viewSetting("Sheet Right-to-Left", "view.rightToLeft", "direction arabic hebrew") { it.sheetView?.rightToLeft == true },
    TellMeItem("Zoom In", "View", LucideIcons.ZoomIn.glyph(), "bigger magnify") { it.zoom = (it.zoom + 0.1f).coerceAtMost(4f) },
    TellMeItem("Zoom Out", "View", LucideIcons.ZoomOut.glyph(), "smaller") { it.zoom = (it.zoom - 0.1f).coerceAtLeast(0.1f) },
    page("Freeze Panes", "View", "freeze", LucideIcons.PanelsTopLeft.glyph(), "lock top row first column unfreeze"),
)

/** Shown before anything is typed, after the recent picks. */
private val TELL_ME_SUGGESTED = listOf("Sort & Filter", "Format as Table", "Chart", "AutoSum", "Freeze Panes", "Number Format", "Insert & Delete", "Find")

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

/** Tell Me's header: back, the search field, clear, and collapse. */
@Composable
private fun TellMeHeader(vm: AppViewModel, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Row(
        Modifier.fillMaxWidth().height(ROW).background(GridColors.Strip).topEdge(GridColors.StripMore).bottomEdge(GridColors.StripMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(LucideIcons.ChevronLeft.glyph(), GridColors.BackIcon, { vm.tellMeOpen = false }, width = 56.dp, height = ROW, label = "Back")
        Box(Modifier.width(1.dp).height(32.dp).background(GridColors.SwitcherDivider))
        Box(Modifier.weight(1f).padding(start = 16.dp, end = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search for a command", color = GridColors.Disabled, fontSize = 16.sp, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = GridColors.OnSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(GridColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) BarButton(LucideIcons.X.glyph(), GridColors.HeaderIcon, { onQuery("") }, width = 48.dp, height = ROW, label = "Clear Search")
        BarButton(Glyph.Triangle(up = false), GridColors.CollapseIcon, vm::collapsePalette, width = 43.dp, height = ROW, label = "Collapse more options")
    }
}

/** Tell Me in the palette: the search header over the matches, or the recent and suggested picks. */
@Composable
internal fun TellMeSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { tellMeMatches(query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val pick = { item: TellMeItem ->
        keyboard?.hide()
        vm.noteTellMe(item.label)
        vm.tellMeOpen = false
        item.run(vm)
    }
    Column(modifier.fillMaxSize().background(GridColors.Surface)) {
        TellMeHeader(vm, query, { query = it }, { matches.firstOrNull()?.let(pick) })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (query.isBlank()) {
                val byName = TELL_ME_ITEMS.associateBy { it.label }
                val recent = vm.tellMeRecent.mapNotNull { byName[it] }
                if (recent.isNotEmpty()) {
                    item { SectionBand("Recently Used") }
                    items(recent.size) { i -> TellMeRow(recent[i], pick) }
                }
                val suggested = TELL_ME_SUGGESTED.mapNotNull { byName[it] }.filter { it !in recent }
                item { SectionBand("Suggested") }
                items(suggested.size) { i -> TellMeRow(suggested[i], pick) }
            } else if (matches.isEmpty()) {
                item { Text("Nothing matches “${query.trim()}”.", color = GridColors.OnSurface, fontSize = 15.sp, modifier = Modifier.padding(16.dp)) }
            } else {
                items(matches.size) { i -> TellMeRow(matches[i], pick) }
            }
        }
    }
}

@Composable
private fun TellMeRow(item: TellMeItem, pick: (TellMeItem) -> Unit) {
    PaletteRow(item.label, item.glyph, onClick = { pick(item) }, chevron = item.opens)
}
