package io.github.artcraftandroid.gridcraft.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.artcraftandroid.gridcraft.GridcraftApplication
import io.github.artcraftandroid.gridcraft.core.CellFormat
import io.github.artcraftandroid.gridcraft.core.CellRange
import io.github.artcraftandroid.gridcraft.core.ColorChoices
import io.github.artcraftandroid.gridcraft.core.ChartPrim
import io.github.artcraftandroid.gridcraft.core.CommentView
import io.github.artcraftandroid.gridcraft.core.SheetObject
import io.github.artcraftandroid.gridcraft.core.SelectionView
import io.github.artcraftandroid.gridcraft.core.SheetView
import io.github.artcraftandroid.gridcraft.core.Viewport
import io.github.artcraftandroid.gridcraft.core.WorkbookInfo
import io.github.artcraftandroid.gridcraft.engine.Engine
import io.github.artcraftandroid.gridcraft.engine.RecentFile
import io.github.artcraftandroid.gridcraft.engine.RecentFiles
import io.github.artcraftandroid.gridcraft.engine.WorkbookFiles
import io.github.artcraftandroid.gridcraft.engine.userMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Start, Editor, Settings }

/** The size the chart galleries' previews are drawn at (chart units), scaled to their tiles. */
const val CHART_PREVIEW_W = 380f
const val CHART_PREVIEW_H = 234.6f

/** The size the Shapes gallery's tiles are drawn at (shape points), scaled to their tiles. */
const val SHAPE_TILE = 44f

/** The grid's visible block, in sheet units from the sheet's top left corner. */
data class GridWindow(val x: Double, val y: Double, val width: Double, val height: Double)

/**
 * The app's state and what it does: the open workbook, the grid's viewport, the selection and the
 * formula bar, the palette. Every edit goes to the engine as a command and the state is read back.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    var engine by mutableStateOf<Engine?>(null)
        private set
    var screen by mutableStateOf(Screen.Start)
    var message by mutableStateOf<String?>(null)

    var info by mutableStateOf<WorkbookInfo?>(null)
        private set
    var sheetView by mutableStateOf<SheetView?>(null)
        private set
    var selection by mutableStateOf<SelectionView?>(null)
        private set
    var format by mutableStateOf<CellFormat?>(null)
        private set
    var viewport by mutableStateOf<Viewport?>(null)
        private set
    var colors by mutableStateOf<ColorChoices?>(null)
        private set

    /** Frozen panes: the frozen rows' height and columns' width (sheet units), and their cells. */
    var frozenW by mutableStateOf(0.0)
        private set
    var frozenH by mutableStateOf(0.0)
        private set
    var frozenTop by mutableStateOf<Viewport?>(null)
        private set
    var frozenLeft by mutableStateOf<Viewport?>(null)
        private set
    var frozenCorner by mutableStateOf<Viewport?>(null)
        private set

    /** The grid's scroll position (sheet units) and zoom (1 = 100%). */
    var scrollX by mutableStateOf(0.0)
    var scrollY by mutableStateOf(0.0)
    var zoom by mutableFloatStateOf(1f)

    /** The formula bar is editing the active cell; what it holds. */
    var editing by mutableStateOf(false)
        private set
    /** What the formula bar holds, with its cursor; [editText] sets it with the cursor at the end. */
    var editValue by mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))
    var editText: String
        get() = editValue.text
        set(v) {
            editValue = androidx.compose.ui.text.input.TextFieldValue(v, androidx.compose.ui.text.TextRange(v.length))
        }

    /** Every function's name and signature, for the suggestions while a formula is typed. */
    private var functionList = listOf<Pair<String, String>>()

    /** Functions whose names start with the word being typed in a formula (at most eight). */
    val suggestions: List<Pair<String, String>>
        get() {
            if (!editing || !editText.startsWith("=")) return emptyList()
            val word = Regex("[A-Za-z][A-Za-z0-9.]*$").find(editText)?.value ?: return emptyList()
            // a cell reference such as B12 isn't a function's start
            if (Regex("^[A-Za-z]{1,3}[0-9]+$").matches(word)) return emptyList()
            return functionList.filter { it.first.startsWith(word, ignoreCase = true) }.take(8)
        }

    /** The function the cursor is in while a formula is typed: its name, arguments and the current one. */
    val signatureTip: Triple<String, List<String>, Int>?
        get() {
            if (!editing) return null
            val (name, index) = io.github.artcraftandroid.gridcraft.ui.editor.callAt(editValue.text, editValue.selection.end) ?: return null
            val signature = functionList.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second ?: return null
            val args = io.github.artcraftandroid.gridcraft.ui.editor.signatureArgs(signature)
            return Triple(name, args, io.github.artcraftandroid.gridcraft.ui.editor.argumentShown(args, index))
        }

    /** Put function [name] in place of the word being typed, with its opening bracket. */
    fun takeSuggestion(name: String) {
        val word = Regex("[A-Za-z][A-Za-z0-9.]*$").find(editText)?.value ?: ""
        editText = editText.dropLast(word.length) + name + "("
    }

    /** Typing with the number keypad instead of the phone's keyboard. */
    var keypad by mutableStateOf(false)
        private set

    /** The command palette: open, its tab, the pages open over it, the tab list. */
    var paletteOpen by mutableStateOf(false)
    var paletteTab by mutableStateOf("Home")
    var panels by mutableStateOf(listOf<String>())
        private set
    var tabsOpen by mutableStateOf(false)

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    /** Settings: the theme (`system`, `light`, `dark`) and AutoSave. */
    var themeChoice by mutableStateOf(prefs.getString("theme", "system") ?: "system")
        private set
    var autoSaveOn by mutableStateOf(prefs.getBoolean("autoSave", true))
        private set

    fun chooseTheme(choice: String) {
        themeChoice = choice
        prefs.edit().putString("theme", choice).apply()
    }

    fun chooseAutoSave(on: Boolean) {
        autoSaveOn = on
        prefs.edit().putBoolean("autoSave", on).apply()
    }

    /** Where Settings was opened from: closing it goes back there. */
    private var settingsFrom = Screen.Start

    fun openSettings() {
        moreOpen = false
        settingsFrom = screen
        screen = Screen.Settings
    }

    fun closeSettings() {
        screen = settingsFrom
    }

    /** The header's ⋯ sheet is up. */
    var moreOpen by mutableStateOf(false)

    /** Draw with Pen (the header's ⋯): the Draw tab, with the pen in hand. */
    fun drawWithPen() {
        moreOpen = false
        if (editing) commitEdit("none")
        showTab("Draw")
        paletteOpen = true
        pickPen(penIndex)
    }

    /** The cell menu (a long press) is up; the sheet whose tab menu is up; a sheet being renamed. */
    var cellMenu by mutableStateOf(false)

    /** Paste Special from the cell menu's paste options: `all`, `values`, `formulas` or `formats`. */
    /** The workbook theme's fonts, headings then body, read as the font list opens. */
    var themeFonts by mutableStateOf<List<String>>(emptyList())
        private set

    fun loadThemeFonts() = launchEngine { e -> themeFonts = e.command { themeFonts() } }

    /** Something has been copied or cut in the workbook, so the cell menu's Paste is on. */
    var canPaste by mutableStateOf(false)
        private set

    fun loadCanPaste() = launchEngine { e -> canPaste = e.command { disabledReason("edit.pasteSpecial") } == null }

    fun pasteSpecial(what: String) = launchEngine { e ->
        cellMenu = false
        e.command { execute("edit.pasteSpecial", org.json.JSONObject().put("what", what).toString()) }
        refresh(e)
    }

    /** The colours Fill Color and Font Color put on with one tap (the JSON colour and its ARGB): the last ones chosen. */
    var lastFill by mutableStateOf("\"#FFFF00\"" to 0xFFFFFF00.toInt())
        private set
    var lastFontColor by mutableStateOf("\"#FF0000\"" to 0xFFFF0000.toInt())
        private set

    private fun swatchArgb(color: String): Int? =
        colors?.let { c -> (c.theme.flatten() + c.standard).firstOrNull { it.color == color }?.argb }

    fun setFill(color: String) {
        swatchArgb(color)?.let { lastFill = color to it }
        command("home.fillColor", "{\"color\": $color}")
    }

    fun setFontColor(color: String) {
        swatchArgb(color)?.let { lastFontColor = color to it }
        command("home.fontColor", "{\"color\": $color}")
    }

    fun applyLastFill() = command("home.fillColor", "{\"color\": ${lastFill.first}}")

    fun applyLastFontColor() = command("home.fontColor", "{\"color\": ${lastFontColor.first}}")

    /** Format Painter: the selection's formats are picked up, and the next cell tapped takes them. */
    var formatPainting by mutableStateOf(false)
        private set

    fun startFormatPainter() = launchEngine { e ->
        e.command { execute("edit.formatPainter", "{}") }
        formatPainting = true
        collapsePalette()
    }

    /** Format as Table: the block around the selection becomes a table in [style]. */
    fun formatAsTable(style: String) = launchEngine { e ->
        e.command { execute("home.formatAsTable", org.json.JSONObject().put("style", style).toString()) }
        refresh(e)
    }

    /** Recommended charts for the selection: their names, types and subtypes, best first. */
    var chartRecommendations by mutableStateOf(listOf<Triple<String, String, String>>())
        private set

    fun loadChartRecommendations() = launchEngine { e ->
        val a = runCatching { org.json.JSONArray(e.command { execute("insert.chartRecommendations", "{}") }) }.getOrNull() ?: org.json.JSONArray()
        chartRecommendations = (0 until minOf(a.length(), 20)).map { i ->
            val o = a.getJSONObject(i)
            Triple(o.optString("label"), o.optString("type"), o.optString("subtype"))
        }
    }

    /** The workbook has notes, so Convert All Notes has work to do. */
    var hasNotes by mutableStateOf(false)
        private set

    fun loadHasNotes() = launchEngine { e -> hasNotes = e.view { hasNotes() } }

    /** Fill (from the cell menu): arrows on the selection's right and bottom edges drag its series that way. */
    var fillMode by mutableStateOf(false)

    /** The block a Fill drag would fill, while the finger is down. */
    var fillPreview by mutableStateOf<CellRange?>(null)

    /** Fill [to] from [from]: series continue, other cells copy (`edit.autoFill`). */
    fun autoFill(from: CellRange, to: CellRange) {
        fillMode = false
        fun a1(r: CellRange): String {
            val l = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(r.firstCol.toInt()) + (r.firstRow.toLong() + 1)
            return l + ":" + io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(r.lastCol.toInt()) + (r.lastRow.toLong() + 1)
        }
        command("edit.autoFill", org.json.JSONObject().put("source", a1(from)).put("target", a1(to)).toString())
    }
    var tabMenu by mutableStateOf<Int?>(null)

    /**
     * The bottom shows the sheets bar (the tabs, the phone app's way to switch sheets) rather than
     * the floating command bar: so when a workbook opens, until a cell is picked; the command bar's
     * sheet chip brings it back and its toggle goes back.
     */
    var sheetsBar by mutableStateOf(true)
        private set

    fun showSheetsBar(on: Boolean) {
        sheetsBar = on
        tabMenu = null
    }

    /** Move sheet [index] before sheet [before], or to the end when [before] is null. */
    fun moveSheetBefore(index: Int, before: Int?) {
        tabMenu = null
        val n = info?.sheets?.size ?: return
        val to = when {
            before == null -> n - 1
            index < before -> before - 1
            else -> before
        }
        if (to != index) command("sheet.move", "{\"sheet\": $index, \"to\": $to}")
    }
    var renaming by mutableStateOf<Int?>(null)

    /** The charts, pictures and shapes over the sheet; each chart's drawing and each picture. */
    var objects by mutableStateOf(listOf<SheetObject>())
        private set
    var chartDrawings by mutableStateOf(mapOf<UInt, List<ChartPrim>>())
        private set
    var pictures by mutableStateOf(mapOf<UInt, androidx.compose.ui.graphics.ImageBitmap>())
        private set

    /** Pictures placed in cells, decoded small, by sheet, row and column; an edit starts them afresh. */
    var cellPictures by mutableStateOf(mapOf<Triple<UInt, UInt, UInt>, androidx.compose.ui.graphics.ImageBitmap>())
        private set
    private var cellPicturesRevision: ULong? = null

    /** The chart, picture or shape selected (a tap on it), and where a drag is taking it. */
    var selectedObject by mutableStateOf<SheetObject?>(null)
        private set
    var objectDrag by mutableStateOf<Pair<androidx.compose.ui.geometry.Offset, androidx.compose.ui.geometry.Offset>?>(null)

    /** The selected object being turned by its rotate handle: the degrees turned so far. */
    var objectTurn by mutableStateOf<Float?>(null)

    /** The rotate handle let go: the selected shape or picture turns [by] degrees clockwise. */
    fun rotateObject(by: Float) {
        val o = selectedObject ?: return
        objectTurn = null
        if (o.kind == "chart" || by == 0f) return
        command("arrange.rotate", org.json.JSONObject().put("kind", if (o.kind == "image") "image" else "shape").put("id", o.id.toLong()).put("by", by.toDouble()).toString())
    }

    /** Drawing: the tool (`` to select, `pen`, `eraser`), the pen's colour and width, the stroke being drawn (sheet units). */
    var drawTool by mutableStateOf("")
        private set
    var penColor by mutableStateOf("#1F5FC9")
        private set
    var penWidth by mutableStateOf(2f)
        private set
    var stroke by mutableStateOf(listOf<androidx.compose.ui.geometry.Offset>())

    fun chooseTool(tool: String) {
        drawTool = tool
        if (tool.isNotEmpty() && editing) commitEdit("none")
        selectObject(null)
    }

    fun choosePen(color: String, width: Float = penWidth) {
        penColor = color
        penWidth = width
        drawTool = "pen"
    }

    /**
     * The Draw tab's pens, left to right: a black pen, a red pen and a yellow highlighter to start,
     * then those added; kept between runs.
     */
    var pens by mutableStateOf(
        prefs.getString("pens", null)?.split(';')?.mapNotNull { Pen.parse(it) }?.ifEmpty { null } ?: DEFAULT_PENS,
    )
        private set

    /** Which of [pens] is in hand. */
    var penIndex by mutableIntStateOf(0)
        private set

    /** Take pen [i] and draw with it. */
    fun pickPen(i: Int) {
        val pen = pens.getOrNull(i) ?: return
        penIndex = i
        chooseTool("pen")
        choosePen(pen.color, pen.width)
    }

    /** Change the pen in hand's colour or width. */
    fun changePen(color: String? = null, width: Float? = null) {
        val pen = pens.getOrNull(penIndex) ?: return
        val changed = pen.copy(color = color ?: pen.color, width = width ?: pen.width)
        savePens(pens.toMutableList().also { it[penIndex] = changed })
        pickPen(penIndex)
    }

    /** Add a pen of [kind] at the end of the strip, and take it. */
    fun addPen(kind: String) {
        savePens(pens + Pen.of(kind))
        pickPen(pens.size - 1)
    }

    /** Take the pen in hand away (one always stays). */
    fun deletePen() {
        if (pens.size <= 1) return
        savePens(pens.filterIndexed { i, _ -> i != penIndex })
        pickPen(penIndex.coerceAtMost(pens.size - 1))
    }

    private fun savePens(list: List<Pen>) {
        pens = list
        prefs.edit().putString("pens", list.joinToString(";") { "${it.kind}|${it.color}|${it.width}" }).apply()
    }

    /** Keep the stroke just drawn as ink. */
    fun finishStroke() {
        val pts = stroke
        stroke = emptyList()
        if (pts.size < 2) return
        val a = org.json.JSONArray()
        for (p in pts) a.put(org.json.JSONArray().put(p.x.toDouble()).put(p.y.toDouble()))
        command("draw.stroke", org.json.JSONObject().put("points", a).put("color", penColor).put("width", penWidth.toDouble()).toString())
    }

    /** The eraser touched (`x`, `y`): the ink there goes. */
    fun eraseAt(x: Double, y: Double) {
        val hit = objects.lastOrNull { it.kind == "shape" && it.shape == "ink" && x >= it.x - 4 && x <= it.x + it.width + 4 && y >= it.y - 4 && y <= it.y + it.height + 4 } ?: return
        objects = objects - hit
        command("object.delete", org.json.JSONObject().put("kind", "shape").put("id", hit.id.toLong()).toString())
    }

    /** The active cell's validation (its list to pick from, its input message); the list is up. */
    var validation by mutableStateOf<io.github.artcraftandroid.gridcraft.core.ValidationView?>(null)
        private set
    var pickingList by mutableStateOf(false)

    /** The cell's validation list items that start with what is being typed (at most eight). */
    val listSuggestions: List<String>
        get() {
            val typed = editText
            if (!editing || typed.isEmpty() || typed.startsWith("=")) return emptyList()
            val items = validation?.items ?: return emptyList()
            return items.filter { it.startsWith(typed, ignoreCase = true) && it != typed }.take(8)
        }

    /** The active cell holds dates or times (its number format's kind), for the picker. */
    val pickerKind: String?
        get() = when (format?.numberKind) {
            "Date" -> "date"
            "Time" -> "time"
            else -> null
        }

    /** A date or time from the picker: typed into the cell and entered. */
    fun pickDateTime(text: String) {
        dialog = null
        if (!editing) beginEdit("")
        editText = text
        commitEdit("none")
    }

    /** Put a value picked from the validation list in the active cell. */
    fun pickValue(v: String) {
        pickingList = false
        editText = v
        editing = true
        commitEdit("none")
    }

    /** The aggregate bar's figures for a selection of several cells. */
    var stats by mutableStateOf<io.github.artcraftandroid.gridcraft.core.SelectionStats?>(null)
        private set

    /** The reference a tap put at the end of the formula being typed (a next tap replaces it). */
    private var pointed: String? = null

    /** A formula is being typed and its end takes a reference: after `=`, an operator, `(` or `,`. */
    val pointing: Boolean
        get() = editing && editText.startsWith("=") && (pointed != null && editText.endsWith(pointed!!) || editText.last() in "=+-*/^&(,:<>;")

    /** A tap on a cell while a formula is typed: its reference goes into the formula. */
    fun pointAt(ref: String) {
        val last = pointed
        editText = if (last != null && editText.endsWith(last)) editText.dropLast(last.length) + ref else editText + ref
        pointed = ref
    }

    /** The cell the reference tapped into a formula starts at, and the block it reaches now. */
    private var pointedFrom: Pair<UInt, UInt>? = null
    var pointedBlock by mutableStateOf<io.github.artcraftandroid.gridcraft.core.CellRange?>(null)
        private set

    /** The tapped reference is still the formula's end, so its handle can stretch it. */
    val pointedStretchable: Boolean
        get() = editing && pointedBlock != null && pointed?.let { editText.endsWith(it) } == true

    private fun cellName(row: UInt, col: UInt) = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(col.toInt()) + (row.toLong() + 1)

    fun pointAtCell(row: UInt, col: UInt) {
        pointedFrom = row to col
        pointedBlock = io.github.artcraftandroid.gridcraft.core.CellRange(row, col, row, col)
        pointAt(cellName(row, col))
    }

    /** The reference's handle dragged to (`row`, `col`): the reference becomes the block between. */
    fun stretchPointed(row: UInt, col: UInt) {
        val (r0, c0) = pointedFrom ?: return
        if (!pointedStretchable) return
        val block = io.github.artcraftandroid.gridcraft.core.CellRange(minOf(r0, row), minOf(c0, col), maxOf(r0, row), maxOf(c0, col))
        pointedBlock = block
        val one = block.firstRow == block.lastRow && block.firstCol == block.lastCol
        pointAt(if (one) cellName(r0, c0) else cellName(block.firstRow, block.firstCol) + ":" + cellName(block.lastRow, block.lastCol))
    }

    /** The table the active cell is in. */
    var tableInfo by mutableStateOf<io.github.artcraftandroid.gridcraft.core.TableInfo?>(null)
        private set

    /** The contextual tab: the selected object's (Chart, Picture, Shape), else Table in a table. */
    val contextTab: String?
        get() = when (selectedObject?.kind) {
            "chart" -> "Chart"
            "image" -> "Picture"
            "shape" -> if (selectedObject?.shape == "icon") "Graphics" else "Shape"
            else -> if (pivotInfo != null) "Pivot Table" else if (tableInfo != null) "Table" else null
        }

    fun selectObject(o: SheetObject?) {
        if (editing) commitEdit("none")
        selectedObject = o
        cellMenu = false
        // a selected object brings up the floating bar (its own buttons), as the phone app does
        if (o != null) sheetsBar = false
        cropping = false
        if (o != null && paletteOpen) showTab(contextTab ?: paletteTab)
        if (o == null && paletteTab in setOf("Chart", "Picture", "Shape", "Graphics")) paletteTab = contextTab ?: "Home"
    }

    /** Put the selected object at (`x`, `y`), `w` × `h` (sheet units): its anchor cell and the offset in it. */
    fun placeObject(x: Double, y: Double, w: Double, h: Double) = launchEngine { e ->
        val o = selectedObject ?: return@launchEngine
        val cell = e.view { cellAt(maxOf(0.0, x), maxOf(0.0, y)) }
        val (cl, rt) = e.view { colLeft(cell.col) to rowTop(cell.row) }
        val at = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(cell.col.toInt()) + (cell.row.toLong() + 1)
        val p = org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong()).put("at", at)
            .put("dx", maxOf(0.0, x) - cl).put("dy", maxOf(0.0, y) - rt).put("width", w.coerceAtLeast(8.0)).put("height", h.coerceAtLeast(8.0))
        e.command { execute("object.move", p.toString()) }
        refresh(e)
    }

    /** Bring Forward / to Front, Send Backward / to Back for the selected object. */
    fun arrangeObject(forward: Boolean, all: Boolean) {
        val o = selectedObject ?: return
        val p = org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong()).put(if (forward) "toFront" else "toBack", all)
        command(if (forward) "arrange.bringForward" else "arrange.sendBackward", p.toString())
    }

    // ---- data tools ----

    /** Custom Sort's block: its range, whether its first row is headings, each column's letter and heading. */
    data class SortProposal(val range: String, val header: Boolean, val columns: List<Pair<String, String>>)

    var sortProposal by mutableStateOf<SortProposal?>(null)
        private set

    fun openCustomSort() = launchEngine { e ->
        val o = org.json.JSONObject(e.command { execute("data.sort", "{\"preview\": true}") })
        val cols = o.optJSONArray("columns") ?: org.json.JSONArray()
        sortProposal = SortProposal(
            o.optString("range"),
            o.optBoolean("header"),
            (0 until cols.length()).map { i -> cols.getJSONObject(i).let { it.optString("letter") to it.optString("heading") } },
        )
        collapsePalette()
        dialog = "customSort"
    }

    /** Sort the block by each level in turn: a column letter and `asc` or `desc`. */
    fun customSort(range: String, header: Boolean, levels: List<Pair<String, String>>) {
        dialog = null
        val keys = org.json.JSONArray(levels.map { (col, order) -> org.json.JSONObject().put("column", col).put("order", order) })
        command("data.sort", org.json.JSONObject().put("range", range).put("header", header).put("keys", keys).toString())
    }

    /** Picture Styles and Effects: a preset, or one thing changed (`shadow`, `shape`, `border`). */
    fun stylePicture(params: org.json.JSONObject) {
        val o = selectedObject ?: return
        command("picture.style", params.put("id", o.id.toLong()).toString())
    }

    /** Crop: fractions cut from the selected picture's sides. */
    fun cropPicture(left: Double, top: Double, right: Double, bottom: Double) {
        val o = selectedObject ?: return
        cropPreview = null
        command("picture.crop", org.json.JSONObject().put("id", o.id.toLong()).put("left", left).put("top", top).put("right", right).put("bottom", bottom).toString())
    }

    /** Crop Picture: the selected picture shows its crop marks, dragged to cut it, as the phone app's crop mode. */
    var cropping by mutableStateOf(false)
        private set

    fun toggleCrop() {
        cropping = selectedObject?.kind == "image" && !cropping
    }

    /** The cut being dragged: the part shown as fractions of the whole picture (left, top, width, height). */
    var cropPreview by mutableStateOf<List<Float>?>(null)

    /** The selected picture's crop as fractions of the whole: left, top, width, height shown. */
    private fun cropOf(o: SheetObject) = o.crop.takeIf { it.size == 4 && it[2] > 0f && it[3] > 0f } ?: listOf(0f, 0f, 1f, 1f)

    /** Picture Position: the whole picture [width] or [height] wide in inches, the part shown kept. */
    fun setPictureSize(width: Double? = null, height: Double? = null) {
        val o = selectedObject ?: return
        val c = cropOf(o)
        val w = width?.let { it * c[2] } ?: (o.width / 96.0)
        val h = height?.let { it * c[3] } ?: (o.height / 96.0)
        sizeObject(h, w)
    }

    /** Crop Position: the part shown [key] (`width`, `height`, `left`, `top`) set to [inches], the picture's size kept. */
    fun setCropPosition(key: String, inches: Double) {
        val o = selectedObject ?: return
        val c = cropOf(o)
        val (wholeW, wholeH) = o.width / 96.0 / c[2] to o.height / 96.0 / c[3]
        var (l, t, cw, ch) = c.map { it.toDouble() }
        when (key) {
            "width" -> cw = (inches / wholeW).coerceIn(0.01, 1.0 - l)
            "height" -> ch = (inches / wholeH).coerceIn(0.01, 1.0 - t)
            "left" -> l = (inches / wholeW).coerceIn(0.0, 1.0 - cw)
            "top" -> t = (inches / wholeH).coerceIn(0.0, 1.0 - ch)
        }
        cropPicture(l, t, (1.0 - l - cw).coerceAtLeast(0.0), (1.0 - t - ch).coerceAtLeast(0.0))
    }

    /** Reset Picture: no turn, no flip. */
    fun resetPicture() {
        val o = selectedObject ?: return
        command("arrange.rotate", org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong()).put("reset", true).toString())
    }

    /** Reset Size: the picture at its own size (a pixel to a sheet unit), where it is. */
    fun resetPictureSize() = launchEngine { e ->
        val o = selectedObject ?: return@launchEngine
        val size = e.view { imageSize(o.index) } ?: return@launchEngine
        placeObject(o.x, o.y, size[0].toDouble(), size[1].toDouble())
    }

    /** Rotate the selected shape or picture by [degrees], or flip it (`horizontal`, `vertical`). */
    fun rotateObject(degrees: Double, flip: String?) {
        val o = selectedObject ?: return
        val p = org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong())
        if (flip != null) p.put("flip", flip) else p.put("by", degrees)
        command("arrange.rotate", p.toString())
    }

    /** The selected object's height and width, in inches (96 sheet units each). */
    fun sizeObject(heightIn: Double, widthIn: Double) {
        val o = selectedObject ?: return
        dialog = null
        placeObject(o.x, o.y, widthIn.coerceIn(0.1, 100.0) * 96.0, heightIn.coerceIn(0.1, 100.0) * 96.0)
    }

    /** The selected picture's alt text. */
    /** An object was copied or cut, for the object menu's Paste. */
    var canPasteObject by mutableStateOf(false)
        private set

    /** The object menu's Cut or Copy: the selected object onto the workbook's clipboard. */
    fun copyObject(cut: Boolean) = launchEngine { e ->
        val o = selectedObject ?: return@launchEngine
        val p = org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong()).toString()
        e.command { execute(if (cut) "object.cut" else "object.copy", p) }
        canPasteObject = true
        if (cut) selectedObject = null
        refresh(e)
    }

    /** The object menu's Paste: the copied object again, a sixth of an inch off the selected one. */
    fun pasteObject() = launchEngine { e ->
        val o = selectedObject
        val p = org.json.JSONObject()
        if (o != null) p.put("x", o.x).put("y", o.y)
        val before = objects.map { it.kind to it.id }.toSet()
        e.command { execute("object.paste", p.toString()) }
        refresh(e)
        objects.lastOrNull { (it.kind to it.id) !in before }?.let { selectObject(it) }
    }

    /** Alt Text's "Mark as decorative" for the selected object, keeping its text. */
    fun setDecorative(on: Boolean, text: String) {
        val o = selectedObject ?: return
        command("object.altText", org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong()).put("text", text).put("decorative", on).toString())
    }

    fun setAltText(text: String) {
        val o = selectedObject ?: return
        dialog = null
        command("object.altText", org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong()).put("text", text).toString())
    }

    fun deleteObject() {
        val o = selectedObject ?: return
        selectedObject = null
        if (paletteTab in setOf("Chart", "Picture", "Shape", "Graphics")) paletteTab = "Home"
        command("object.delete", org.json.JSONObject().put("kind", o.kind).put("id", o.id.toLong()).toString())
    }

    /**
     * The colours the Shape tab's Fill and Outline put on with one tap (the JSON colour and its
     * ARGB): the last ones chosen, at first the theme's second and first accents.
     */
    private var lastShapeFill by mutableStateOf<Pair<String, Int>?>(null)
    private var lastShapeOutline by mutableStateOf<Pair<String, Int>?>(null)

    private fun accent(i: Int): Pair<String, Int>? = colors?.theme?.firstOrNull()?.getOrNull(3 + i)?.let { it.color to it.argb }

    val shapeFillChoice: Pair<String, Int>? get() = lastShapeFill ?: accent(2)
    val shapeOutlineChoice: Pair<String, Int>? get() = lastShapeOutline ?: accent(1)

    /** The Shape tab's Fill or Outline pages: put [color] on the selected shape and keep it for the one-tap part. */
    fun setShapeFill(color: String) {
        swatchArgb(color)?.let { lastShapeFill = color to it }
        shapeFormat(fill = color)
    }

    fun setShapeOutline(color: String) {
        swatchArgb(color)?.let { lastShapeOutline = color to it }
        shapeFormat(line = color)
    }

    fun applyLastShapeFill() {
        shapeFillChoice?.let { shapeFormat(fill = it.first) }
    }

    fun applyLastShapeOutline() {
        shapeOutlineChoice?.let { shapeFormat(line = it.first) }
    }

    /** The selected shape's fill or outline (a colour command's JSON value, `"none"` for none). */
    fun shapeFormat(fill: String? = null, line: String? = null) {
        val o = selectedObject ?: return
        val p = org.json.JSONObject().put("id", o.id.toLong())
        fill?.let { p.put("fill", org.json.JSONTokener(it).nextValue()) }
        line?.let { p.put("line", org.json.JSONTokener(it).nextValue()) }
        command("shape.format", p.toString())
    }

    /** Shape Styles' Recent Styles: the styles applied lately (`style:colour`, see `shape.style`), newest first. */
    var recentShapeStyles by mutableStateOf(
        prefs.getString("recentShapeStyles", null)?.split(',')?.filter { it.contains(':') }?.takeIf { it.isNotEmpty() }
            ?: listOf("dark1", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6").map { "coloredFill:$it" },
    )
        private set

    /** Shape Styles: give the selected shape style [key] (`style:colour`) and put it first among the recent ones. */
    fun applyShapeStyle(key: String) {
        val o = selectedObject ?: return
        // the gallery closes on a pick, back to the Shape tab
        if (panels.lastOrNull() == "shapeStyles") popPanel()
        command("shape.style", org.json.JSONObject().put("id", o.id.toLong()).put("style", key.substringBefore(':')).put("color", key.substringAfter(':')).toString())
        recentShapeStyles = (listOf(key) + recentShapeStyles.filter { it != key }).take(7)
        prefs.edit().putString("recentShapeStyles", recentShapeStyles.joinToString(",")).apply()
    }

    /**
     * Change Shape: the selected shape takes [kind]'s outline (a Shapes gallery name), or the
     * selected picture is cut to it.
     */
    fun changeShape(kind: String) {
        val o = selectedObject ?: return
        popPanel()
        if (o.kind == "image") {
            command("picture.style", org.json.JSONObject().put("id", o.id.toLong()).put("shape", kind).toString())
        } else {
            command("shape.change", org.json.JSONObject().put("id", o.id.toLong()).put("kind", kind).toString())
        }
    }

    /**
     * The shape whose text is being typed in place (its id), as the phone app edits a shape's
     * text, and whether the keyboard comes up with it (Edit Text) or waits for a tap (a new text
     * box).
     */
    var editingShapeText by mutableStateOf<Long?>(null)
        private set
    var shapeTextKeyboard = true
        private set

    /** Edit Text: type the selected shape's text in place. */
    fun startShapeText(keyboard: Boolean = true) {
        val o = selectedObject?.takeIf { it.kind == "shape" } ?: return
        shapeTextKeyboard = keyboard
        cellMenu = false
        editingShapeText = o.id.toLong()
    }

    /** Stop typing in the shape (Back): its text is kept as the editor closes. */
    fun stopShapeText() {
        editingShapeText = null
    }

    /** The in-place editor closed: shape [o] keeps [text]. */
    fun endShapeText(o: SheetObject, text: String) {
        if (editingShapeText == o.id.toLong()) editingShapeText = null
        if (text != o.text) command("shape.setText", org.json.JSONObject().put("id", o.id.toLong()).put("text", text).toString())
    }

    /** The editing bar's Bold, Italic and Underline: the shape's text turned on or off. */
    fun shapeTextFormat(key: String, on: Boolean) {
        val o = selectedObject ?: return
        command("shape.textFormat", org.json.JSONObject().put("id", o.id.toLong()).put(key, on).toString())
    }

    /** Run a chart command on the selected chart. */
    /** What the selected chart shows (for the Elements menus). */
    var chartInfo by mutableStateOf<io.github.artcraftandroid.gridcraft.core.ChartInfo?>(null)
        private set

    fun loadChartInfo() = launchEngine { e ->
        val o = selectedObject
        chartInfo = if (o?.kind == "chart") e.view { chartInfo(o.id) } else null
    }

    fun chartCommand(id: String, params: org.json.JSONObject = org.json.JSONObject()) {
        val o = selectedObject ?: return
        launchEngine { e ->
            e.command { execute(id, params.put("chart", o.id.toLong()).toString()) }
            refresh(e)
            chartInfo = e.view { chartInfo(o.id) }
        }
    }

    /** Insert or delete whole rows or columns at the active cell (`above`, `below`, `left`, `right`). */
    fun tableInsert(where: String) {
        val sel = selection ?: return
        val row = sel.activeRow.toLong() + 1
        val col = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(sel.activeCol.toInt())
        val next = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(sel.activeCol.toInt() + 1)
        when (where) {
            "above" -> command("home.insertRows", org.json.JSONObject().put("rows", "$row:$row").toString())
            "below" -> command("home.insertRows", org.json.JSONObject().put("rows", "${row + 1}:${row + 1}").toString())
            "left" -> command("home.insertColumns", org.json.JSONObject().put("cols", "$col:$col").toString())
            "right" -> command("home.insertColumns", org.json.JSONObject().put("cols", "$next:$next").toString())
            "deleteRows" -> command("home.deleteRows", org.json.JSONObject().put("rows", "$row:$row").toString())
            "deleteColumns" -> command("home.deleteColumns", org.json.JSONObject().put("cols", "$col:$col").toString())
        }
    }

    /** The table styles with their colours, loaded the first time the gallery opens. */
    var tableStyles by mutableStateOf(listOf<io.github.artcraftandroid.gridcraft.core.TableStylePreview>())
        private set

    fun loadTableStyles() = launchEngine { e ->
        if (tableStyles.isEmpty()) tableStyles = e.view { tableStyles() }
    }

    /** The filter sheet: the column it filters (its header cell) and that column's values. */
    var filterAt by mutableStateOf<Pair<UInt, UInt>?>(null)
        private set
    var filterValues by mutableStateOf(listOf<io.github.artcraftandroid.gridcraft.core.FilterValue>())
    /** The filter column's fills, No Fill first, for the Color tab (shown when a cell has a fill). */
    var filterFills by mutableStateOf(listOf<io.github.artcraftandroid.gridcraft.core.FilterFill>())
        private set

    /** The active cell's note or comment thread, and its link. */
    var comment by mutableStateOf<CommentView?>(null)
        private set
    var link by mutableStateOf<String?>(null)
        private set

    /** A dialog over the editor: `comment` (a new comment) or `link` (Insert Link). */
    var dialog by mutableStateOf<String?>(null)

    /** The PivotTable the active cell is in. */
    var pivotInfo by mutableStateOf<io.github.artcraftandroid.gridcraft.core.PivotInfo?>(null)
        private set

    /** Borders' Line Style and Line Color: what the border presets draw with (and how the colour looks, for its icon). */
    var borderStyle by mutableStateOf("thin")
    var borderColor by mutableStateOf<String?>(null)
    var borderColorArgb by mutableStateOf<Int?>(null)

    /** The chart galleries' previews, by kind (`type|subtype`), drawn once. */
    private val chartPreviews = mutableMapOf<String, List<io.github.artcraftandroid.gridcraft.core.ChartPrim>>()

    suspend fun chartPreview(kind: String): List<io.github.artcraftandroid.gridcraft.core.ChartPrim> {
        chartPreviews[kind]?.let { return it }
        val e = engine ?: return emptyList()
        return e.view { chartPreview(kind, CHART_PREVIEW_W, CHART_PREVIEW_H) }.also { chartPreviews[kind] = it }
    }

    /** The Shapes gallery's bands, and its tiles' drawings by shape, made once. */
    private var shapeBands: List<io.github.artcraftandroid.gridcraft.core.ShapeBand>? = null
    private val shapePreviews = mutableMapOf<String, List<io.github.artcraftandroid.gridcraft.core.ShapePiece>>()

    suspend fun shapeGallery(): List<io.github.artcraftandroid.gridcraft.core.ShapeBand> {
        shapeBands?.let { return it }
        val e = engine ?: return emptyList()
        return e.view { shapeGallery() }.also { shapeBands = it }
    }

    suspend fun shapePreview(kind: String): List<io.github.artcraftandroid.gridcraft.core.ShapePiece> {
        shapePreviews[kind]?.let { return it }
        val e = engine ?: return emptyList()
        return e.view { shapePreview(kind, SHAPE_TILE, SHAPE_TILE) }.also { shapePreviews[kind] = it }
    }

    /** Currency's chosen symbol (Choose Symbol), which its negative-number forms use. */
    var currencySymbol by mutableStateOf(io.github.artcraftandroid.gridcraft.ui.editor.COMMON_SYMBOLS[0])
        private set

    /** Give the selection number format [code]. */
    fun setNumberCode(code: String) = command("home.numberFormat", org.json.JSONObject().put("code", code).toString())

    /** Choose Currency's symbol, and give the selection Currency with it. */
    fun chooseCurrency(symbol: io.github.artcraftandroid.gridcraft.ui.editor.CurrencySymbol) {
        currencySymbol = symbol
        setNumberCode(symbol.currencyCodes()[0])
        popPanel()
    }

    /** A border preset with the chosen line style and colour. */
    fun applyBorder(preset: String) {
        val p = org.json.JSONObject().put("preset", preset)
        // presets that name their own weight (thick, double) keep it
        if (borderStyle != "thin" && preset in setOf("all", "outside", "bottom", "top", "left", "right", "topBottom")) p.put("style", borderStyle)
        borderColor?.let { p.put("color", org.json.JSONTokener(it).nextValue()) }
        command("home.borders", p.toString())
    }

    /** The comments pane (Review > Show Comments) and the sheet's comments it lists. */
    var commentsOpen by mutableStateOf(false)
        private set
    var commentList by mutableStateOf(listOf<io.github.artcraftandroid.gridcraft.core.CommentItem>())
        private set

    fun showComments(on: Boolean) = launchEngine { e ->
        commentsOpen = on
        if (on) {
            paletteOpen = false
            commentList = e.view { comments() }
        }
    }

    /** A comment picked in the pane: go to its cell. */
    fun goToCell(row: UInt, col: UInt) = launchEngine { e ->
        val ref = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(col.toInt()) + (row.toLong() + 1)
        e.command { execute("selection.set", org.json.JSONObject().put("range", ref).toString()) }
        refresh(e)
        revealTick++
    }

    /** The formula bar shows its text over several lines. */
    var formulaExpanded by mutableStateOf(false)

    /** View > Formula Bar and Sheet Tabs: shown or not. */
    var showFormulaBar by mutableStateOf(true)

    /** Functions picked lately, newest first: the Formulas tab's Recently Used. */
    var recentFunctions by mutableStateOf((prefs.getString("recentFunctions", "") ?: "").split(',').filter { it.isNotBlank() })
        private set

    fun noteFunction(name: String) {
        recentFunctions = (listOf(name) + recentFunctions.filter { it != name }).take(10)
        prefs.edit().putString("recentFunctions", recentFunctions.joinToString(",")).apply()
    }

    /** Insert Table's prompt: the block it would cover and whether its first row holds headers. */
    var tableProposal by mutableStateOf<Pair<String, Boolean>?>(null)

    /** Insert Table: the engine's block and header guess, for the prompt to confirm. */
    fun proposeTable() = launchEngine { e ->
        val out = e.command { execute("insert.table", "{\"preview\": true}") }
        val o = org.json.JSONObject(out)
        tableProposal = o.optString("range") to o.optBoolean("header", true)
        dialog = "table"
    }

    fun insertTable(range: String, header: Boolean) {
        dialog = null
        tableProposal = null
        command("insert.table", org.json.JSONObject().put("range", range).put("header", header).toString())
    }

    /** Links the activity opens (http, https and mailto only). */
    val linkRequests = Channel<String>(Channel.UNLIMITED)

    /** A column (true) or row being resized by its header: its number and how much it grew. */
    var resizePreview by mutableStateOf<Triple<Boolean, UInt, Float>?>(null)

    /** Find: the bar is up, what it looks for, the matches (sheet name and cell), the current one. */
    var findOpen by mutableStateOf(false)
    var findText by mutableStateOf("")
    var findHits by mutableStateOf(listOf<Pair<String, String>>())
        private set
    var findIndex by mutableStateOf(-1)
        private set

    /** Replace: its row is up, what replaces the matches; the options and their pane; a status. */
    var replaceOpen by mutableStateOf(false)
    var replaceText by mutableStateOf("")
    var findOptionsOpen by mutableStateOf(false)
    var findInWorkbook by mutableStateOf(false)
    var findMatchCase by mutableStateOf(false)
    var findWholeCell by mutableStateOf(false)
    var findStatus by mutableStateOf("")
        private set

    /** Bumped when the active cell should be scrolled into view (a move by Enter, a jump). */
    var revealTick by mutableStateOf(0)
        private set

    /** The workbook's own file, when it came from one the app may write back to. */
    private var fileUri: Uri? = null
    private var fileWritable = false
    var fileName by mutableStateOf("Book")
        private set

    var recent by mutableStateOf(listOf<RecentFile>())
        private set
    private val recents = RecentFiles(File(app.filesDir, "recent.json"))

    /** The activity picks a photo (`photo`) or takes one (`camera`) for Insert › Pictures. */
    val pictureRequests = Channel<String>(Channel.CONFLATED)

    /** Files the activity shares (a content URI and its type). */
    val shareRequests = Channel<Pair<Uri, String>>(Channel.CONFLATED)

    /** Save As asks the activity for a place (the suggested name). */
    val saveAsRequests = Channel<String>(Channel.CONFLATED)

    private var window: GridWindow? = null
    private var viewJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            WorkbookFiles.clearCache(app)
            recent = withContext(Dispatchers.IO) { recents.list() }
            val e = (app as GridcraftApplication).engine.await()
            e.onSuccess { engine = it }.onFailure { message = "The spreadsheet engine couldn't start: ${it.userMessage}" }
            e.getOrNull()?.let { eng ->
                val json = runCatching { eng.command { execute("formulas.functions", "{}") } }.getOrNull()
                val a = json?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }
                functionList = (0 until (a?.length() ?: 0)).mapNotNull { i -> a?.optJSONObject(i)?.let { it.optString("name") to it.optString("signature") } }
            }
        }
    }

    fun tell(text: String) {
        message = text
    }

    private fun launchEngine(block: suspend (Engine) -> Unit) {
        viewModelScope.launch {
            // a workbook opened from another app can arrive while the engine is still starting:
            // it waits for the engine rather than being dropped
            val e = engine ?: getApplication<GridcraftApplication>().engine.await().getOrNull() ?: return@launch
            try {
                block(e)
            } catch (t: Throwable) {
                message = t.userMessage
            }
        }
    }

    /** Read the workbook's state back after an edit. */
    private suspend fun refresh(e: Engine) {
        val i = e.command { info() }
        val v = e.command { sheetView() }
        val s = e.command { selection() }
        val f = e.command { cellFormat() }
        comment = e.command { comment() }
        link = e.command { link() }
        tableInfo = e.command { tableInfo() }
        pivotInfo = e.command { pivotInfo() }
        stats = e.view { selectionStats() }
        validation = e.command { validation() }
        if (commentsOpen) commentList = e.view { comments() }
        val sheetChanged = info?.activeSheet != i.activeSheet || info?.documentId != i.documentId
        val freezeChanged = sheetView?.frozenRows != v.frozenRows || sheetView?.frozenCols != v.frozenCols
        info = i
        sheetView = v
        if (sheetChanged || freezeChanged) {
            val (w, h) = e.view { colLeft(v.frozenCols) to rowTop(v.frozenRows) }
            frozenW = if (v.frozenCols > 0u) w else 0.0
            frozenH = if (v.frozenRows > 0u) h else 0.0
            if (!sheetChanged) {
                scrollX = scrollX.coerceAtLeast(frozenW)
                scrollY = scrollY.coerceAtLeast(frozenH)
            }
        }
        selection = s
        format = f
        if (sheetChanged) {
            scrollX = frozenW
            scrollY = frozenH
            zoom = (v.zoom.toFloat() / 100f).coerceIn(0.1f, 4f)
            colors = e.command { colorChoices() }
        }
        requestViewport(force = true)
        loadObjects(e, sheetChanged)
        if (i.dirty) scheduleAutoSave()
    }

    /** The objects over the active sheet, their charts laid out and their pictures decoded. */
    private suspend fun loadObjects(e: Engine, sheetChanged: Boolean) {
        val list = e.view { objects() }
        objects = list
        selectedObject = selectedObject?.let { s -> list.firstOrNull { it.kind == s.kind && it.id == s.id } }
        chartDrawings = list.filter { it.kind == "chart" }.associate { o ->
            o.index to e.view { chartPrims(o.index, o.width.toFloat(), o.height.toFloat()) }
        }
        if (sheetChanged) pictures = emptyMap()
        val missing = list.filter { it.kind == "image" && it.index !in pictures }
        if (missing.isNotEmpty()) {
            val decoded = missing.mapNotNull { o ->
                val bytes = e.view { imageBytes(o.index) } ?: return@mapNotNull null
                val bmp = withContext(Dispatchers.Default) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } ?: return@mapNotNull null
                o.index to bmp.asImageBitmap()
            }
            pictures = pictures + decoded
        }
    }

    /** AutoSave: 2.5 s after the edits pause, the workbook goes back to its own file. */
    private fun scheduleAutoSave() {
        val uri = fileUri ?: return
        if (!fileWritable || !autoSaveOn) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(2500)
            saveTo(uri)
        }
    }

    /** Leaving the app saves at once. */
    fun autoSaveNow() {
        val uri = fileUri ?: return
        if (!fileWritable || !autoSaveOn || info?.dirty != true) return
        autoSaveJob?.cancel()
        saveTo(uri)
    }

    // ---- files ----

    fun newWorkbook(sample: String = "") = launchEngine { e ->
        e.command { newWorkbook(sample) }
        fileUri = null
        fileWritable = false
        fileName = e.command { info() }.title
        editing = false
        refresh(e)
        screen = Screen.Editor
        sheetsBar = true
    }

    fun open(uri: Uri, writable: Boolean) = launchEngine { e ->
        val app = getApplication<Application>()
        val name = withContext(Dispatchers.IO) { WorkbookFiles.displayName(app, uri) }
        val file = withContext(Dispatchers.IO) { WorkbookFiles.copyToCache(app, uri, name) }
        e.command { open(file.absolutePath, null) }
        fileUri = uri
        fileWritable = writable
        fileName = name.substringBeforeLast('.', name)
        editing = false
        refresh(e)
        screen = Screen.Editor
        sheetsBar = true
        val place = withContext(Dispatchers.IO) { RecentFiles.placeOf(app, uri) }
        val entry = RecentFile(uri.toString(), name, System.currentTimeMillis(), place)
        recent = withContext(Dispatchers.IO) {
            recents.touch(entry)
            recents.list()
        }
    }

    /**
     * Open a recent workbook. A file shared or opened from another app came with a one-time
     * grant that ends when this app is reinstalled or the other app lets it go: then it can't be
     * read again from here, so it leaves the list and the user is told how to get it back.
     */
    fun openRecent(r: RecentFile) {
        val app = getApplication<Application>()
        val uri = Uri.parse(r.uri)
        viewModelScope.launch {
            val readable = withContext(Dispatchers.IO) {
                try {
                    app.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (_: SecurityException) {
                    false
                } catch (_: java.io.IOException) {
                    false
                }
            }
            if (readable) {
                // written back to only when the app may write there (a file picked with Open
                // keeps that right; one handed over to view does not)
                val writable = app.checkCallingOrSelfUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                open(uri, writable = writable)
            } else {
                message = "Open ${r.name} again to edit it."
                removeRecent(r.uri)
            }
        }
    }

    fun starRecent(uri: String, on: Boolean) {
        viewModelScope.launch {
            recent = withContext(Dispatchers.IO) {
                recents.star(uri, on)
                recents.list()
            }
        }
    }

    fun removeRecent(uri: String) {
        viewModelScope.launch {
            recent = withContext(Dispatchers.IO) {
                recents.remove(uri)
                recents.list()
            }
        }
    }

    /** Save to the workbook's own file, or ask where when it has none. */
    fun save() {
        val uri = fileUri
        if (uri == null || !fileWritable) {
            saveAsRequests.trySend("$fileName.xlsx")
            return
        }
        saveTo(uri)
    }

    /** Save a copy elsewhere: Save As's picker. */
    fun saveCopy() {
        saveAsRequests.trySend("$fileName.xlsx")
    }

    /** Save the workbook as xlsx to [uri]. */
    fun saveTo(uri: Uri) = launchEngine { e ->
        val app = getApplication<Application>()
        val tmp = File(app.cacheDir, "save/${System.nanoTime()}/book.xlsx").apply { parentFile?.mkdirs() }
        e.command { save(tmp.absolutePath) }
        val ok = withContext(Dispatchers.IO) { WorkbookFiles.write(app, uri, tmp).also { tmp.parentFile?.deleteRecursively() } }
        if (!ok) {
            // the file can't be written (it was handed over to view, or moved): AutoSave stops
            // trying, and closing asks before the changes go
            if (uri == fileUri) fileWritable = false
            message = "This file can't be changed here. Save a copy to keep your changes."
            return@launchEngine
        }
        fileUri = uri
        fileWritable = true
        fileName = withContext(Dispatchers.IO) { WorkbookFiles.displayName(app, uri) }.substringBeforeLast('.')
        info = e.command { info() }
        if (closeAfterSave) closeWorkbook(force = true)
    }

    /** Close the workbook; one with changes and no file of its own asks first (`closeUnsaved`). */
    fun closeWorkbook(force: Boolean = false) {
        if (!force && info?.dirty == true && (fileUri == null || !fileWritable || !autoSaveOn)) {
            dialog = "closeUnsaved"
            return
        }
        if (!force) autoSaveNow()
        dialog = null
        editing = false
        paletteOpen = false
        panels = emptyList()
        findOpen = false
        cellMenu = false
        closeAfterSave = false
        screen = Screen.Start
    }

    /** The workbook has a file of its own the app may write. */
    val hasFile: Boolean get() = fileUri != null && fileWritable

    /** The workbook came from a file this app may read but not write (handed over to view). */
    val readOnlyFile: Boolean get() = fileUri != null && !fileWritable

    /** Save As was asked for by closing: the workbook closes once it is saved. */
    private var closeAfterSave = false

    fun saveAndClose() {
        dialog = null
        closeAfterSave = true
        save()
    }

    // ---- commands ----

    /** Run engine command [id] (params as a JSON object) on the selection, then read the state back. */
    fun command(id: String, params: String = "{}") = launchEngine { e ->
        e.command { execute(id, params) }
        refresh(e)
    }

    fun undo() = command("edit.undo")

    fun redo() = command("edit.redo")

    // ---- the grid ----

    /** The grid shows [w]; fetch the viewport for it (and a margin round it) when it moved. */
    fun showWindow(w: GridWindow) {
        val old = window
        window = w
        if (old != null && covered(w)) return
        requestViewport(force = false)
    }

    /** The current viewport reaches over [w] (with what it holds), so it needn't be fetched again. */
    private fun covered(w: GridWindow): Boolean {
        val v = viewport ?: return false
        val (r0, r1) = v.rows.firstOrNull() to v.rows.lastOrNull()
        val (c0, c1) = v.cols.firstOrNull() to v.cols.lastOrNull()
        if (r0 == null || r1 == null || c0 == null || c1 == null) return false
        if (v.revision != info?.revision || v.sheet != info?.activeSheet) return false
        return r0.start <= w.y && r1.start + r1.size >= w.y + w.height && c0.start <= w.x && c1.start + c1.size >= w.x + w.width
    }

    private fun requestViewport(force: Boolean) {
        val e = engine ?: return
        val w = window ?: return
        viewJob?.cancel()
        viewJob = viewModelScope.launch {
            // a margin of half a screen on every side, so small scrolls need nothing new
            val mx = w.width / 2
            val my = w.height / 2
            val first = e.view { cellAt(maxOf(0.0, w.x - mx), maxOf(0.0, w.y - my)) }
            val last = e.view { cellAt(w.x + w.width + mx, w.y + w.height + my) }
            val v = runCatching { e.view { viewport(first.row, first.col, last.row + 1u, last.col + 1u, 0f) } }.getOrNull() ?: return@launch
            // the frozen rows over the visible columns, the frozen columns beside the visible rows
            val sv = sheetView
            val fr = sv?.frozenRows ?: 0u
            val fc = sv?.frozenCols ?: 0u
            frozenTop = if (fr > 0u) e.view { viewport(0u, first.col, fr - 1u, last.col + 1u, 0f) } else null
            frozenLeft = if (fc > 0u) e.view { viewport(first.row, 0u, last.row + 1u, fc - 1u, 0f) } else null
            frozenCorner = if (fr > 0u && fc > 0u) e.view { viewport(0u, 0u, fr - 1u, fc - 1u, 0f) } else null
            if (force || viewport == null || v != viewport) viewport = v
            loadCellPictures(e, listOfNotNull(v, frozenTop, frozenLeft, frozenCorner))
        }
    }

    /** Decode the pictures in the cells of [views] that aren't yet (a few dozen at a time). */
    private suspend fun loadCellPictures(e: Engine, views: List<Viewport>) {
        val i = info ?: return
        if (i.revision != cellPicturesRevision) {
            cellPictures = emptyMap()
            cellPicturesRevision = i.revision
        }
        val missing = views.flatMap { it.cells }.filter { it.picture && Triple(i.activeSheet, it.row, it.col) !in cellPictures }.distinctBy { it.row to it.col }.take(48)
        if (missing.isEmpty()) return
        val decoded = missing.mapNotNull { c ->
            val bytes = e.view { cellPicture(c.row, c.col) } ?: return@mapNotNull null
            val bmp = withContext(Dispatchers.Default) { decodeSmall(bytes, 512) } ?: return@mapNotNull null
            Triple(i.activeSheet, c.row, c.col) to bmp.asImageBitmap()
        }
        cellPictures = cellPictures + decoded
    }

    /** A picture decoded at most about [side] pixels on its longer side. */
    private fun decodeSmall(bytes: ByteArray, side: Int): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Where row [row] and column [col] start, for jumps (Go To, a new selection off screen). */
    suspend fun cellTopLeft(row: UInt, col: UInt): Pair<Double, Double>? {
        val e = engine ?: return null
        return e.view { colLeft(col) to rowTop(row) }
    }

    fun tapCell(row: UInt, col: UInt, extend: Boolean = false) = launchEngine { e ->
        if (editing) commitEdit("none")
        // a cell picked: the floating command bar takes the sheets bar's place
        sheetsBar = false
        // Format Painter: the cell tapped takes the formats picked up
        if (formatPainting) {
            formatPainting = false
            val ref = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(col.toInt()) + (row.toLong() + 1)
            e.command { execute("edit.formatPainter", org.json.JSONObject().put("apply", ref).toString()) }
            refresh(e)
        }
        if (selectedObject != null) selectObject(null)
        selection = e.command { select(row, col, extend) }
        format = e.command { cellFormat() }
        comment = e.command { comment() }
        link = e.command { link() }
        tableInfo = e.command { tableInfo() }
        pivotInfo = e.command { pivotInfo() }
        stats = e.view { selectionStats() }
        validation = e.command { validation() }
        pickingList = false
        if ((tableInfo == null && paletteTab == "Table") || (pivotInfo == null && paletteTab == "Pivot Table")) paletteTab = "Home"
    }

    fun selectRange(range: CellRange) = launchEngine { e ->
        if (editing) commitEdit("none")
        selection = e.command { selectRange(range) }
        format = e.command { cellFormat() }
        stats = e.view { selectionStats() }
    }

    fun activateSheet(index: Int) = launchEngine { e ->
        if (editing) commitEdit("none")
        e.command { activateSheet(index.toUInt()) }
        refresh(e)
    }

    fun addSheet() = command("home.insertSheet", """{"before": ${(info?.sheets?.size ?: 0)}}""")

    // ---- the formula bar ----

    /** Start editing the active cell, from what it holds or from [initial] (typing replaces it). */
    fun beginEdit(initial: String? = null) {
        if (selection?.editable == false) {
            message = "The cell is on a protected sheet."
            return
        }
        editText = initial ?: selection?.formula.orEmpty()
        pointed = null
        pointedFrom = null
        pointedBlock = null
        editing = true
        paletteOpen = false
        cellMenu = false
        // a figure (or a number format's cell) is typed with the number keypad
        val numeric = Regex("^[-+]?[0-9.,]+%?$")
        keypad = initial == null && (numeric.matches(editText) || (editText.isEmpty() && format?.numberKind !in setOf(null, "General", "Text")))
    }

    /** fx: start a formula in the active cell and show the function categories. */
    fun insertFunction() {
        if (!editing) beginEdit("=") else if (!editText.startsWith("=")) editText = "=$editText"
        keypad = false
        paletteOpen = true
        paletteTab = "Formulas"
        panels = emptyList()
    }

    fun useKeypad(on: Boolean) {
        keypad = on
    }

    fun keypadType(text: String) {
        editText += text
    }

    fun keypadBackspace() {
        if (editText.isNotEmpty()) editText = editText.dropLast(1)
    }

    fun cancelEdit() {
        editing = false
        keypad = false
        editText = ""
    }

    /** A data validation alert for an entry, waiting for an answer, and the entry it is about. */
    var entryAlert by mutableStateOf<io.github.artcraftandroid.gridcraft.core.EntryAlert?>(null)
        private set
    private var pendingEntry: Pair<String, String>? = null

    /**
     * Put what the formula bar holds in the active cell and move on ([then]: down, right, none).
     * An entry the cell's data validation doesn't allow gets its alert first, unless [allowed].
     */
    fun commitEdit(then: String = "down", allowed: Boolean = false) {
        val e = engine ?: return
        val text = editText
        editing = false
        keypad = false
        val at = selection?.let { it.activeRow to it.activeCol }
        viewModelScope.launch {
            try {
                // checked and put in as one step, before any tap that follows moves the selection
                val (alert, done) = e.command {
                    val a = if (allowed) null else checkEntry(text)
                    a to (if (a == null) commit(text, then) else null)
                }
                if (alert != null) {
                    pendingEntry = text to then
                    entryAlert = alert
                    dialog = "entryAlert"
                    // the alert is about this cell: a tap elsewhere doesn't move on from it
                    at?.let { (r, c) -> selection = e.command { select(r, c, false) } }
                    return@launch
                }
                selection = done ?: return@launch
                refresh(e)
                revealTick++
            } catch (t: Throwable) {
                message = t.userMessage
            }
        }
    }

    /**
     * The answer to an entry's alert: `retry` edits it again, `allow` puts it in (a warning's Yes,
     * an information's OK), `cancel` drops it and leaves the cell as it was.
     */
    fun answerEntryAlert(answer: String) {
        val (text, then) = pendingEntry ?: return
        dialog = null
        entryAlert = null
        pendingEntry = null
        when (answer) {
            "retry" -> beginEdit(text)
            "allow" -> {
                editText = text
                commitEdit(then, allowed = true)
            }
            else -> editText = ""
        }
    }

    /** AutoFit Column Width for columns `first..last`, measured with the fonts the grid draws in. */
    fun autofitColumns(first: UInt, last: UInt) = launchEngine { e ->
        val texts = e.view { fitTexts(first, last) }
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val widths = HashMap<UInt, Float>()
        for (t in texts) {
            paint.typeface = io.github.artcraftandroid.gridcraft.ui.editor.CellFonts.typeface(getApplication(), t.font, t.bold, t.italic)
            // measured large and scaled down: small sizes are hinted narrower than the zoomed grid draws
            paint.textSize = 200f
            val w = paint.measureText(t.text) * t.size.coerceIn(1f, 409f) * 96f / 72f / 200f + t.extra
            if (w > (widths[t.col] ?: 0f)) widths[t.col] = w
        }
        val measured = org.json.JSONObject()
        for ((c, w) in widths) measured.put(c.toString(), kotlin.math.ceil(w.toDouble()))
        val letters = { i: Int -> io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(i) }
        val params = org.json.JSONObject().put("cols", letters(first.toInt()) + ":" + letters(last.toInt())).put("widths", measured)
        e.command { execute("home.autofitColumnWidth", params.toString()) }
        refresh(e)
    }

    fun setColumnWidth(col: UInt, width: Float) {
        val letters = io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(col.toInt())
        command("home.columnWidth", org.json.JSONObject().put("cols", "$letters:$letters").put("width", width.toDouble()).toString())
    }

    fun setRowHeight(row: UInt, height: Float) {
        val n = row.toLong() + 1
        command("home.rowHeight", org.json.JSONObject().put("rows", "$n:$n").put("height", height.toDouble()).toString())
    }

    // ---- inserting ----

    /** PDFs for the system's print dialog: the file and the job's name. */
    val printRequests = Channel<Pair<File, String>>(Channel.CONFLATED)

    /**
     * Print: [what] is `selection`, `active` or `workbook`; [fit] `actual`, `page`, `columns` or
     * `rows`; [orientation] `auto`, `portrait` or `landscape`; [paper] a paper name. The engine
     * lays the pages out; the system's print dialog prints them.
     */
    fun print(what: String, fit: String, orientation: String, paper: String, ignorePrintArea: Boolean) = launchEngine { e ->
        dialog = null
        val p = org.json.JSONObject().put("fit", fit).put("orientation", orientation).put("paper", paper)
        when (what) {
            "workbook" -> p.put("sheets", "all")
            "selection" -> selection?.ranges?.lastOrNull()?.let { r ->
                p.put("range", rangeA1(r))
            }
            else -> if (ignorePrintArea) sheetView?.let { v ->
                if (v.usedRows > 0u && v.usedCols > 0u) p.put("range", "A1:" + io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(v.usedCols.toInt() - 1) + v.usedRows)
            }
        }
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "print").apply { mkdirs() }
        val file = File(dir, "print.pdf")
        p.put("path", file.absolutePath)
        e.command { execute("file.print", p.toString()) }
        printRequests.trySend(file to fileName.ifBlank { "Workbook" })
    }

    private fun rangeA1(r: io.github.artcraftandroid.gridcraft.core.CellRange): String {
        val letters = { c: UInt -> io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(c.toInt()) }
        return letters(r.firstCol) + (r.firstRow.toLong() + 1) + ":" + letters(r.lastCol) + (r.lastRow.toLong() + 1)
    }

    /** Bumped when the selected object should be scrolled into view (a new one). */
    var objectRevealTick by mutableStateOf(0)
        private set

    /** Insert a chart, picture or shape; the new object comes in selected and in view. */
    fun insertObject(id: String, params: String, then: () -> Unit = {}) = launchEngine { e ->
        val before = objects.map { it.kind to it.id }.toSet()
        e.command { execute(id, params) }
        refresh(e)
        objects.lastOrNull { (it.kind to it.id) !in before }?.let { o ->
            selectObject(o)
            objectRevealTick++
            then()
        }
    }

    /** Where the next picture goes: over the cells, or in the active cell (Pictures › Place in Cell). */
    private var pictureInCell = false

    /** Ask for a picture ([kind]: `photo`, `camera` or `file`) to place over the cells or [inCell]. */
    fun requestPicture(kind: String, inCell: Boolean) {
        pictureInCell = inCell
        collapsePalette()
        pictureRequests.trySend(kind)
    }

    /** Insert a picture's encoded bytes at the active cell: over the cells, or in it. */
    fun insertPicture(bytes: ByteArray) {
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val mime = if (bytes.size > 3 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
        val p = org.json.JSONObject().put("base64", b64).put("mime", mime)
        if (pictureInCell) {
            pictureInCell = false
            command("insert.picture", p.put("placement", "cell").toString())
        } else {
            insertObject("insert.picture", p.toString())
        }
    }

    /** Recent Shapes: the gallery shapes inserted lately (their kinds), newest first, eight of them. */
    var recentShapes by mutableStateOf(
        prefs.getString("recentShapes", null)?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: listOf("textBox", "line", "arrow", "rect", "ellipse", "roundRect", "triangle", "bentConnector3"),
    )
        private set

    /** Insert shape [kind] as the phone app does: 0.7 in (67.2 sheet units) into the view from its top left. */
    fun insertShape(kind: String) {
        panels = emptyList()
        recentShapes = (listOf(kind) + recentShapes.filter { it != kind }).take(8)
        prefs.edit().putString("recentShapes", recentShapes.joinToString(",")).apply()
        val p = org.json.JSONObject().put("kind", kind).put("x", scrollX + 67.2).put("y", scrollY + 67.2)
        // a new text box goes straight into typing, the keyboard waiting for a tap, as the phone app's
        insertObject("insert.shape", p.toString()) { if (kind == "textBox") startShapeText(keyboard = false) }
    }

    // ---- sharing ----

    /** Share the workbook as `xlsx` or `pdf` (a PDF of the active sheet) with another app. */
    fun share(format: String) = launchEngine { e ->
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "share").apply { mkdirs() }
        val safe = fileName.map { if (it in "/:*?<>|" || it.code < 32 || it == '"' || it.code == 92) '_' else it }.joinToString("").ifBlank { "Book" }
        val file = File(dir, "$safe.$format")
        // a copy for the other app; the workbook keeps its own file and its unsaved state
        if (format == "pdf") {
            e.command { execute("file.exportPdf", org.json.JSONObject().put("path", file.absolutePath).toString()) }
        } else {
            val out = e.command { execute("file.saveBytes", "{\"format\": \"xlsx\"}") }
            val b64 = org.json.JSONObject(out).optString("base64")
            withContext(Dispatchers.IO) { file.writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT)) }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", file)
        shareRequests.trySend(uri to if (format == "pdf") "application/pdf" else WorkbookFiles.XLSX_TYPE)
    }

    // ---- a hardware keyboard ----

    /** Move the selection by (`dr`, `dc`) cells; with [extend] stretch it (Shift+arrow). */
    fun moveSelection(dr: Int, dc: Int, extend: Boolean = false) = launchEngine { e ->
        if (selectedObject != null) selectObject(null)
        e.command { execute("selection.move", org.json.JSONObject().put("dr", dr).put("dc", dc).put("extend", extend).toString()) }
        selection = e.command { selection() }
        format = e.command { cellFormat() }
        comment = e.command { comment() }
        link = e.command { link() }
        tableInfo = e.command { tableInfo() }
        pivotInfo = e.command { pivotInfo() }
        stats = e.view { selectionStats() }
        validation = e.command { validation() }
        revealTick++
    }

    /** A key on a hardware keyboard while the grid has the focus; true when it was used. */
    fun gridKey(key: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val k = key.key
        val shift = key.isShiftPressed
        val ctrl = key.isCtrlPressed
        if (editing) {
            // keys that come before the formula bar has the focus (fast typing) still go into the edit
            if (keypad) return false
            val ch = key.utf16CodePoint
            when {
                k == androidx.compose.ui.input.key.Key.Enter || k == androidx.compose.ui.input.key.Key.NumPadEnter -> commitEdit(if (shift) "up" else "down")
                k == androidx.compose.ui.input.key.Key.Escape -> cancelEdit()
                k == androidx.compose.ui.input.key.Key.Backspace -> editText = editText.dropLast(1)
                ch > 31 && !ctrl && !key.isAltPressed -> editText += String(Character.toChars(ch))
                else -> return false
            }
            return true
        }
        when {
            ctrl && k == androidx.compose.ui.input.key.Key.Z -> undo()
            ctrl && k == androidx.compose.ui.input.key.Key.Y -> redo()
            ctrl && k == androidx.compose.ui.input.key.Key.C -> copy(cut = false)
            ctrl && k == androidx.compose.ui.input.key.Key.X -> copy(cut = true)
            ctrl && k == androidx.compose.ui.input.key.Key.V -> paste()
            ctrl && k == androidx.compose.ui.input.key.Key.B -> command("home.bold")
            ctrl && k == androidx.compose.ui.input.key.Key.I -> command("home.italic")
            ctrl && k == androidx.compose.ui.input.key.Key.U -> command("home.underline")
            ctrl && k == androidx.compose.ui.input.key.Key.F -> openFind()
            k == androidx.compose.ui.input.key.Key.DirectionUp -> moveSelection(-1, 0, shift)
            k == androidx.compose.ui.input.key.Key.DirectionDown -> moveSelection(1, 0, shift)
            k == androidx.compose.ui.input.key.Key.DirectionLeft -> moveSelection(0, -1, shift)
            k == androidx.compose.ui.input.key.Key.DirectionRight -> moveSelection(0, 1, shift)
            k == androidx.compose.ui.input.key.Key.Enter || k == androidx.compose.ui.input.key.Key.NumPadEnter -> moveSelection(if (shift) -1 else 1, 0)
            k == androidx.compose.ui.input.key.Key.Tab -> moveSelection(0, if (shift) -1 else 1)
            k == androidx.compose.ui.input.key.Key.F2 -> beginEdit()
            k == androidx.compose.ui.input.key.Key.Delete || k == androidx.compose.ui.input.key.Key.Backspace ->
                if (selectedObject != null) deleteObject() else command("edit.clearContents")
            else -> {
                val ch = key.utf16CodePoint
                if (ch <= 31 || ctrl || key.isAltPressed) return false
                beginEdit(String(Character.toChars(ch)))
                keypad = false
            }
        }
        return true
    }

    // ---- go to, sizes ----

    /** Go to a cell, a block or a defined name (`B12`, `Sheet2!A1:C3`, `Totals`). */
    fun goTo(reference: String) {
        dialog = null
        if (reference.isBlank()) return
        launchEngine { e ->
            e.command { execute("edit.goTo", org.json.JSONObject().put("reference", reference.trim()).toString()) }
            refresh(e)
            revealTick++
        }
    }

    /** Set the selected rows' height (points) or columns' width (characters of the default font). */
    fun setSize(rows: Boolean, value: String) {
        dialog = null
        val v = value.trim().replace(',', '.').toDoubleOrNull() ?: run {
            message = "Type a number."
            return
        }
        if (rows) {
            // points, as the phone shows them; the command takes pixels (96 to the inch)
            command("home.rowHeight", org.json.JSONObject().put("height", v.coerceIn(0.0, 409.0) * 4.0 / 3.0).toString())
        } else {
            command("home.columnWidth", org.json.JSONObject().put("chars", v.coerceIn(0.0, 255.0)).toString())
        }
    }

    // ---- comments and links ----

    fun newComment(text: String) {
        dialog = null
        if (text.isBlank()) return
        command("review.newComment", org.json.JSONObject().put("text", text.trim()).toString())
    }

    fun reply(text: String) {
        if (text.isBlank()) return
        command("review.replyComment", org.json.JSONObject().put("text", text.trim()).toString())
    }

    fun deleteComment() = command("review.deleteComment")

    /** Go to the next (or previous) comment in the workbook. */
    fun goToComment(forward: Boolean) = launchEngine { e ->
        val out = runCatching { e.command { execute(if (forward) "review.nextComment" else "review.previousComment", "{}") } }.getOrNull()
        if (out == null) {
            message = "There are no comments."
            return@launchEngine
        }
        val o = org.json.JSONObject(out)
        val sheet = o.optString("sheet")
        val cell = o.optString("cell")
        val idx = info?.sheets?.indexOfFirst { it.name == sheet } ?: -1
        if (idx >= 0 && idx != info?.activeSheet?.toInt()) e.command { activateSheet(idx.toUInt()) }
        if (cell.isNotEmpty()) e.command { execute("selection.set", org.json.JSONObject().put("range", cell).toString()) }
        refresh(e)
        revealTick++
    }

    fun resolveComment() = command("review.resolveComment", "{\"resolved\": ${comment?.resolved != true}}")

    /** Insert › Link's recent links, newest first (kept on the phone). */
    var recentLinks by mutableStateOf(prefs.getString("recentLinks", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
        private set

    fun insertLink(target: String, text: String) {
        dialog = null
        if (target.isBlank()) return
        val p = org.json.JSONObject().put("target", target.trim())
        if (text.isNotBlank()) p.put("text", text.trim())
        command("insert.link", p.toString())
        recentLinks = (listOf(target.trim()) + recentLinks.filter { it != target.trim() }).take(10)
        prefs.edit().putString("recentLinks", recentLinks.joinToString("\n")).apply()
    }

    /** Open the active cell's link: a web or mail address in another app, a place in the workbook here. */
    fun openLink() {
        cellMenu = false
        val target = link ?: return
        val lower = target.lowercase()
        when {
            lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:") -> linkRequests.trySend(target)
            target.startsWith("#") || target.contains('!') -> {
                val place = target.removePrefix("#")
                val sheet = place.substringBefore('!', "").trim('\'')
                val cell = place.substringAfter('!')
                launchEngine { e ->
                    val idx = info?.sheets?.indexOfFirst { it.name == sheet } ?: -1
                    if (idx >= 0) e.command { activateSheet(idx.toUInt()) }
                    runCatching { e.command { execute("selection.set", org.json.JSONObject().put("range", cell).toString()) } }
                    refresh(e)
                    revealTick++
                }
            }
            else -> message = "Links to other files can't be opened here."
        }
    }

    // ---- the filter sheet ----

    fun openFilter(row: UInt, col: UInt) = launchEngine { e ->
        if (editing) commitEdit("none")
        paletteOpen = false
        cellMenu = false
        filterValues = e.command { filterColumn(row, col) }
        filterFills = e.command { filterFills(col) }
        filterAt = row to col
    }

    fun closeFilter() {
        filterAt = null
        filterValues = emptyList()
        filterFills = emptyList()
    }

    private fun filterLetters(): String? = filterAt?.second?.let { io.github.artcraftandroid.gridcraft.ui.editor.columnLetters(it.toInt()) }

    /** A Number or Text Filters rule waiting for its value(s): `gt`, `between`, `contains`… */
    var filterRule by mutableStateOf<String?>(null)

    /** The column's values read as numbers, so the sheet offers Number Filters (else Text Filters). */
    val filterNumeric: Boolean
        get() {
            val texts = filterValues.map { it.text }.filter { it.isNotBlank() && it != "(Blanks)" }
            if (texts.isEmpty()) return false
            return texts.count { t -> t.filter { it.isDigit() || it in ".-" }.let { d -> d.isNotEmpty() && d.toDoubleOrNull() != null && t.none { ch -> ch.isLetter() } } } * 2 > texts.size
        }

    /**
     * The command bar's filter: AutoFilter on for the active cell's region and its sheet opened on
     * the active column, as the phone app does. With a filter on it is Sort & Filter: the sheet for
     * the active column, or (outside the filter) the filter off again.
     */
    fun applyFilterButton() = launchEngine { e ->
        if (editing) commitEdit("none")
        val wasOn = sheetView?.autofilter == true
        val at = selection
        if (wasOn && at != null) {
            val column = e.command { viewport(0u, at.activeCol, at.activeRow, at.activeCol, 0f) }
            val head = column.cells.filter { it.col == at.activeCol && it.filterButton }.minByOrNull { it.row }
            if (head != null) {
                paletteOpen = false
                cellMenu = false
                filterValues = e.command { filterColumn(head.row, at.activeCol) }
                filterFills = e.command { filterFills(at.activeCol) }
                filterAt = head.row to at.activeCol
                return@launchEngine
            }
        }
        e.command { execute("data.filter", "{}") }
        refresh(e)
        if (wasOn) return@launchEngine
        val sel = selection ?: return@launchEngine
        val column = e.command { viewport(0u, sel.activeCol, sel.activeRow, sel.activeCol, 0f) }
        val head = column.cells.filter { it.col == sel.activeCol && it.filterButton }.minByOrNull { it.row } ?: return@launchEngine
        paletteOpen = false
        cellMenu = false
        filterValues = e.command { filterColumn(head.row, sel.activeCol) }
        filterFills = e.command { filterFills(sel.activeCol) }
        filterAt = head.row to sel.activeCol
    }

    /**
     * The filter sheet's Items: show only the rows whose value is ticked, none ticked meaning no
     * filter, at once; the sheet stays up.
     */
    fun filterToValues(ticked: Set<String>) {
        val col = filterLetters() ?: return
        val p = org.json.JSONObject().put("column", col)
        if (ticked.isEmpty()) {
            p.put("clear", true)
        } else {
            val values = org.json.JSONArray()
            for (v in ticked) if (v != "(Blanks)") values.put(v)
            p.put("values", values).put("blanks", "(Blanks)" in ticked)
        }
        command("data.filterBy", p.toString())
    }

    /** Filter the column by a rule (`gt` 10, `between` 5 and 9, `begins` "N"…); [keepOpen] leaves the filter sheet up. */
    fun applyFilterRule(rule: String, first: String, second: String = "", keepOpen: Boolean = false) {
        val col = filterLetters() ?: return
        val p = org.json.JSONObject().put("column", col)
        fun custom(op: String, v: String, op2: String? = null, v2: String = "") {
            val c = org.json.JSONObject().put("op", op).put("value", v)
            if (op2 != null) c.put("op2", op2).put("value2", v2).put("and", true)
            p.put("custom", c)
        }
        when (rule) {
            "eq" -> custom("=", first)
            "ne" -> custom("<>", first)
            "gt" -> custom(">", first)
            "ge" -> custom(">=", first)
            "lt" -> custom("<", first)
            "le" -> custom("<=", first)
            "between" -> custom(">=", first, "<=", second)
            "begins" -> custom("=", "$first*")
            "ends" -> custom("=", "*$first")
            "contains" -> custom("=", "*$first*")
            "notContains" -> custom("<>", "*$first*")
            "top10" -> p.put("top", org.json.JSONObject().put("count", first.toIntOrNull()?.coerceIn(1, 500) ?: 10))
            "above" -> p.put("aboveAverage", true)
            "below" -> p.put("aboveAverage", false)
            else -> return
        }
        filterRule = null
        dialog = null
        if (!keepOpen) closeFilter()
        command("data.filterBy", p.toString())
    }

    /** The Color tab: show only the rows whose cell has this fill ([param] from the bridge), and close the sheet, as the phone app does. */
    fun filterByFill(param: String) {
        val col = filterLetters() ?: return
        closeFilter()
        val color = runCatching { org.json.JSONTokener(param).nextValue() }.getOrNull() ?: return
        command("data.filterBy", org.json.JSONObject().put("column", col).put("fillColor", color).toString())
    }

    /** Clear Filter From the column. */
    fun clearColumnFilter() {
        val col = filterLetters() ?: return
        closeFilter()
        command("data.filterBy", org.json.JSONObject().put("column", col).put("clear", true).toString())
    }

    fun sortFilterColumn(ascending: Boolean) {
        val col = filterLetters() ?: return
        closeFilter()
        command(if (ascending) "data.sortAscending" else "data.sortDescending", org.json.JSONObject().put("column", col).toString())
    }

    /** Show only rows whose value is ticked in [shown] (all ticked clears the column's filter). */
    fun applyFilter(shown: Set<String>) {
        val col = filterLetters() ?: return
        val all = filterValues.all { it.text in shown }
        closeFilter()
        if (all) {
            command("data.filterBy", org.json.JSONObject().put("column", col).put("clear", true).toString())
            return
        }
        val values = org.json.JSONArray()
        for (v in shown) if (v != "(Blanks)") values.put(v)
        command("data.filterBy", org.json.JSONObject().put("column", col).put("values", values).put("blanks", "(Blanks)" in shown).toString())
    }

    // ---- find ----

    fun openFind() {
        if (editing) commitEdit("none")
        paletteOpen = false
        findOpen = true
    }

    fun closeFind() {
        findOpen = false
        replaceOpen = false
        findOptionsOpen = false
        findHits = emptyList()
        findIndex = -1
        findStatus = ""
    }

    private fun findParams(what: String) = org.json.JSONObject()
        .put("what", what)
        .put("matchCase", findMatchCase)
        .put("wholeCell", findWholeCell)
        .put("within", if (findInWorkbook) "workbook" else "sheet")

    /** Replace the current match, then go to the next one. */
    fun replaceOne() = launchEngine { e ->
        val what = findText
        if (what.isEmpty()) return@launchEngine
        // the current match must be the active cell: go there first when it is not
        val hit = findHits.getOrNull(findIndex)
        if (hit == null) {
            findAll(e, what)
            return@launchEngine
        }
        goToHit(e, findIndex)
        e.command { execute("edit.replace", findParams(what).put("with", replaceText).put("all", false).toString()) }
        refresh(e)
        val at = findIndex
        findAll(e, what, start = at)
        findStatus = if (findHits.isEmpty()) "No more matches" else ""
    }

    /** Replace every match, in the sheet or the whole workbook as the options say. */
    fun replaceAll() = launchEngine { e ->
        val what = findText
        if (what.isEmpty()) return@launchEngine
        val out = e.command { execute("edit.replace", findParams(what).put("with", replaceText).put("all", true).toString()) }
        val n = runCatching { org.json.JSONObject(out).optInt("replaced") }.getOrDefault(0)
        refresh(e)
        findHits = emptyList()
        findIndex = -1
        findStatus = if (n == 1) "Replaced 1 match" else "Replaced $n matches"
    }

    /** Look for [findText] in the whole workbook and go to the first match. */
    fun runFind() = launchEngine { e ->
        val what = findText
        if (what.isEmpty()) {
            findHits = emptyList()
            findIndex = -1
            return@launchEngine
        }
        findAll(e, what)
    }

    /** All matches of [what], going to the one at [start] (or the last when fewer are left). */
    private suspend fun findAll(e: Engine, what: String, start: Int = 0) {
        val out = runCatching {
            e.command { execute("edit.find", findParams(what).put("all", true).toString()) }
        }.getOrNull()
        val list = out?.let { o ->
            val a = runCatching { org.json.JSONObject(o).optJSONArray("results") }.getOrNull()
            (0 until (a?.length() ?: 0)).mapNotNull { i -> a?.optJSONObject(i)?.let { it.optString("sheet") to it.optString("cell") } }
        }.orEmpty()
        findHits = list
        findIndex = if (list.isEmpty()) -1 else start.coerceIn(0, list.size - 1)
        findStatus = if (list.isEmpty()) "No results" else ""
        if (list.isNotEmpty()) goToHit(e, findIndex)
    }

    fun findStep(forward: Boolean) = launchEngine { e ->
        if (findHits.isEmpty()) return@launchEngine
        val n = findHits.size
        findIndex = ((findIndex + if (forward) 1 else -1) % n + n) % n
        goToHit(e, findIndex)
    }

    private suspend fun goToHit(e: Engine, i: Int) {
        val (sheet, cell) = findHits.getOrNull(i) ?: return
        val idx = info?.sheets?.indexOfFirst { it.name == sheet } ?: -1
        if (idx >= 0 && idx != info?.activeSheet?.toInt()) {
            e.command { activateSheet(idx.toUInt()) }
            refresh(e)
        }
        e.command { execute("selection.set", org.json.JSONObject().put("range", cell).toString()) }
        selection = e.command { selection() }
        format = e.command { cellFormat() }
        revealTick++
    }

    // ---- the cell menu and the sheet tabs ----

    /** Copy or cut the selection: into the workbook's clipboard and, as text, the phone's. */
    fun copy(cut: Boolean) = launchEngine { e ->
        val out = e.command { execute(if (cut) "edit.cut" else "edit.copy", "{}") }
        val text = runCatching { org.json.JSONObject(out).optString("text") }.getOrDefault("")
        if (text.isNotEmpty()) {
            val cm = getApplication<Application>().getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("cells", text))
        }
        cellMenu = false
        refresh(e)
    }

    fun paste() = launchEngine { e ->
        cellMenu = false
        e.command { execute("edit.paste", "{}") }
        refresh(e)
    }

    fun renameSheet(index: Int, name: String) {
        renaming = null
        if (name.isBlank()) return
        command("sheet.rename", org.json.JSONObject().put("sheet", index).put("name", name.trim()).toString())
    }

    fun deleteSheet(index: Int) {
        tabMenu = null
        if ((info?.sheets?.count { !it.hidden } ?: 0) <= 1) {
            message = "A workbook must have at least one visible sheet."
            return
        }
        command("home.deleteSheet", "{\"sheet\": $index}")
    }

    fun hideSheet(index: Int) {
        tabMenu = null
        command("sheet.hide", "{\"sheet\": $index}")
    }

    fun unhideSheet(index: Int) {
        tabMenu = null
        command("sheet.unhide", "{\"sheet\": $index}")
    }

    fun duplicateSheet(index: Int) {
        tabMenu = null
        command("sheet.move", "{\"sheet\": $index, \"to\": ${index + 1}, \"copy\": true}")
    }

    /** The visible sheet just left (or right) of sheet [index], if there is one. */
    fun neighbourSheet(index: Int, right: Boolean): Int? {
        val sheets = info?.sheets ?: return null
        val order = if (right) (index + 1 until sheets.size) else (index - 1 downTo 0)
        return order.firstOrNull { !sheets[it].hidden }
    }

    /** Move a sheet one place left or right, past any hidden sheets between. */
    fun moveSheet(index: Int, right: Boolean) {
        tabMenu = null
        val to = neighbourSheet(index, right) ?: return
        command("sheet.move", "{\"sheet\": $index, \"to\": $to}")
    }

    /** The sheet Tab Color is choosing for. */
    var tabColorSheet by mutableStateOf<Int?>(null)
        private set

    fun chooseTabColor(index: Int) {
        tabMenu = null
        tabColorSheet = index
        pushPanelInPalette("tabColor")
    }

    /** [color] as the colour grid gives it: a JSON colour, `"none"` or `"auto"`. */
    fun setTabColor(color: String) {
        val i = tabColorSheet ?: return
        val c = if (color == "\"auto\"") "\"none\"" else color
        command("sheet.tabColor", "{\"sheet\": $i, \"color\": $c}")
    }

    // ---- Cards View ----

    /** Cards View: the table's rows as cards, shown in place of the sheet; the card open in full. */
    var cards by mutableStateOf<io.github.artcraftandroid.gridcraft.core.TableCards?>(null)
        private set
    var cardOpen by mutableStateOf<Int?>(null)

    /** The card field being changed: its index and the text it starts from. */
    var cardEdit by mutableStateOf<Pair<Int, String>?>(null)

    fun openCards() = launchEngine { e ->
        val t = e.command { tableCards() }
        if (t == null) {
            message = "Select a cell in a table to see its rows as cards."
            return@launchEngine
        }
        if (editing) commitEdit("none")
        collapsePalette()
        cardOpen = null
        cards = t
    }

    fun closeCards() {
        cards = null
        cardOpen = null
        cardEdit = null
    }

    /** Change field [field] of the card at sheet row [row]: it starts from what its cell holds (a formula, or the value as typed). */
    fun editCardField(row: UInt, field: Int) = launchEngine { e ->
        val t = cards ?: return@launchEngine
        selection = e.command { select(row, t.firstCol + field.toUInt(), false) }
        cardEdit = field to (selection?.formula ?: "")
    }

    fun setCardValue(row: UInt, field: Int, text: String) = launchEngine { e ->
        val t = cards ?: return@launchEngine
        cardEdit = null
        e.command { select(row, t.firstCol + field.toUInt(), false) }
        selection = e.command { commit(text, "none") }
        refresh(e)
        cards = e.command { tableCards() } ?: t
    }

    // ---- the palette ----

    fun togglePalette() {
        if (editing) commitEdit("none")
        if (!paletteOpen && contextTab != null) paletteTab = contextTab!!
        paletteOpen = !paletteOpen
        panels = emptyList()
        tabsOpen = false
        tellMeOpen = false
        pageAlone = false
    }

    fun collapsePalette() {
        paletteOpen = false
        panels = emptyList()
        tabsOpen = false
        tellMeOpen = false
        pageAlone = false
    }

    fun showTab(tab: String) {
        paletteTab = tab
        panels = emptyList()
        tabsOpen = false
        tellMeOpen = false
        pageAlone = false
    }

    /** The palette shows one page on its own (no tab under it): its title where the tab's name goes, and no Back. */
    var pageAlone by mutableStateOf(false)
        private set

    /** Open the palette on page [p] alone, as the command bar's Fill Color does. */
    fun openPage(p: String) {
        if (editing) commitEdit("none")
        paletteOpen = true
        panels = listOf(p)
        tabsOpen = false
        tellMeOpen = false
        pageAlone = true
    }

    /** Tell Me, the palette's command search, shows in place of the tab's commands. */
    var tellMeOpen by mutableStateOf(false)

    /** Tell Me's last picks (their names), most recent first, kept between runs. */
    var tellMeRecent by mutableStateOf(prefs.getString("tellMeRecent", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
        private set

    fun noteTellMe(label: String) {
        tellMeRecent = (listOf(label) + tellMeRecent.filter { it != label }).take(5)
        prefs.edit().putString("tellMeRecent", tellMeRecent.joinToString("\n")).apply()
    }

    /** The palette on [tab]'s page [panel], as Tell Me opens it. */
    fun showPanel(tab: String, panel: String) {
        showTab(tab)
        panels = listOf(panel)
    }

    fun toggleTabs() {
        tabsOpen = !tabsOpen
    }

    fun pushPanel(p: String) {
        panels = panels + p
        tabsOpen = false
    }

    /** Tell Me from a page opened on its own: the palette's tab view, searching. */
    fun tellMeFromPage() {
        pageAlone = false
        panels = emptyList()
        tellMeOpen = true
    }

    /** Open the palette on its Home tab at page [p] (the cell menu's Insert). */
    fun pushPanelInPalette(p: String) {
        paletteOpen = true
        paletteTab = "Home"
        panels = listOf(p)
        tabsOpen = false
        tellMeOpen = false
    }

    fun popPanel() {
        // a page opened on its own has nothing under it: Back puts the palette away
        if (pageAlone && panels.size <= 1) collapsePalette() else panels = panels.dropLast(1)
    }

    /** After a change the grid redraws soon: a tap on a swatch shows at once. */
    fun refreshSoon() = launchEngine { e ->
        delay(16)
        refresh(e)
    }
}

/** A pen of the Draw tab: `pen`, `pencil` (thin) or `highlighter` (wide, see-through), its colour (#RRGGBB) and width. */
data class Pen(val kind: String, val color: String, val width: Float) {
    companion object {
        /** A new pen of [kind], as Add gives it. */
        fun of(kind: String) = when (kind) {
            "pencil" -> Pen("pencil", "#000000", 1f)
            "highlighter" -> Pen("highlighter", "#FFFF00", 12f)
            else -> Pen("pen", "#000000", 2f)
        }

        /** A pen as kept: `kind|#RRGGBB|width`. */
        fun parse(text: String): Pen? {
            val (kind, color, width) = text.split('|').takeIf { it.size == 3 } ?: return null
            if (kind !in listOf("pen", "pencil", "highlighter") || !Regex("#[0-9A-Fa-f]{6}").matches(color)) return null
            return Pen(kind, color, width.toFloatOrNull()?.coerceIn(0.5f, 48f) ?: return null)
        }
    }
}

private val DEFAULT_PENS = listOf(Pen("pen", "#000000", 2f), Pen("pen", "#E02020", 2f), Pen("highlighter", "#FFFF00", 12f))
