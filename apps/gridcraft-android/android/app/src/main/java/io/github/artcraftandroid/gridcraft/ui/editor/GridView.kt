package io.github.artcraftandroid.gridcraft.ui.editor

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.artcraftandroid.gridcraft.ui.LucideIcons
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import io.github.artcraftandroid.gridcraft.core.Across
import io.github.artcraftandroid.gridcraft.core.CellRange
import io.github.artcraftandroid.gridcraft.core.CellView
import io.github.artcraftandroid.gridcraft.core.Down
import io.github.artcraftandroid.gridcraft.core.Edge
import io.github.artcraftandroid.gridcraft.core.Line
import io.github.artcraftandroid.gridcraft.core.Stroke as BorderStroke
import io.github.artcraftandroid.gridcraft.core.Viewport
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.GridWindow
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The row headers' width and the column headers' height, in dp. */
private const val ROW_HEADER_DP = 36f
private const val COL_HEADER_DP = 24f

/** One outline level's lane, in dp: wide enough for a finger, as the phone app's are (its buttons fill most of it). */
private const val OUTLINE_LANE_DP = 34f

/** One sheet unit (a 96-dpi pixel of the file) is this many dp at 100% zoom: the phone app draws a 15 pt row 17 dp high. */
private const val UNIT_DP = 0.85f

/** How close to a selection handle a touch takes it, in dp. */
private const val HANDLE_REACH_DP = 28f

/** How far the phone app's rotate handle sits above a selected shape, and its move handle below. */
private const val ROTATE_HANDLE_DP = 34f
private const val MOVE_HANDLE_DP = 41f

/** The selection's frame and handles, grey as the phone app draws them for objects. */
private val HANDLE_LINE = Color(0xFF5F5F5F)

/** The line holding [pos] (sheet units) among [lines], or the nearest one. */
private fun lineAt(lines: List<Line>, pos: Double): Line? {
    if (lines.isEmpty()) return null
    for (l in lines) if (pos < l.start + l.size) return l
    return lines.last()
}

/**
 * The worksheet: the visible cells, their gridlines, the row and column headers, the selection
 * and its handles. A drag scrolls (and flings), two fingers zoom from 10% to 400%, a tap selects
 * a cell (a header its row or column, the corner everything), a double tap edits, and the round
 * handles at the selection's corners extend it.
 */
@Composable
fun GridView(vm: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(Size.Zero) }
    var fling by remember { mutableStateOf<Job?>(null) }
    val sv = vm.sheetView
    val headings = sv?.headings != false
    // the row numbers' column widens for long numbers (row 100000 and on), as the last row on screen needs
    val digits = ((vm.viewport?.rows?.lastOrNull()?.index?.toLong() ?: 0L) + 1).toString().length
    // the outline band of grouped rows (left of the row numbers) and columns (above the letters):
    // a lane per level, and one for the level button that shows everything
    val lane = OUTLINE_LANE_DP * density
    val rowLevels = if (headings) sv?.rowLevels?.toInt() ?: 0 else 0
    val colLevels = if (headings) sv?.colLevels?.toInt() ?: 0 else 0
    val rowBand = if (rowLevels > 0) (rowLevels + 1) * lane else 0f
    val colBand = if (colLevels > 0) (colLevels + 1) * lane else 0f
    val rowHeaderW = if (headings) rowBand + maxOf(ROW_HEADER_DP, digits * 6.9f + 8f) * density else 0f
    val colHeaderH = if (headings) colBand + COL_HEADER_DP * density else 0f
    val unit = UNIT_DP * density * vm.zoom
    // frozen rows and columns stay at the top and left; the rest scrolls from their edge
    val fr = sv?.frozenRows ?: 0u
    val fc = sv?.frozenCols ?: 0u
    val fw = (vm.frozenW * unit).toFloat()
    val fh = (vm.frozenH * unit).toFloat()

    fun mainW() = ((size.width - rowHeaderW - fw) / unit).toDouble()
    fun mainH() = ((size.height - colHeaderH - fh) / unit).toDouble()

    // the active cell scrolls into view when Enter moves it off the screen
    LaunchedEffect(vm.revealTick) {
        if (vm.revealTick == 0) return@LaunchedEffect
        val sel = vm.selection ?: return@LaunchedEffect
        val (left, top) = vm.cellTopLeft(sel.activeRow, sel.activeCol) ?: return@LaunchedEffect
        val (right, bottom) = vm.cellTopLeft(sel.activeRow + 1u, sel.activeCol + 1u) ?: return@LaunchedEffect
        if (sel.activeCol >= fc) {
            if (left < vm.scrollX) vm.scrollX = left else if (right > vm.scrollX + mainW()) vm.scrollX = right - mainW()
        }
        if (sel.activeRow >= fr) {
            if (top < vm.scrollY) vm.scrollY = top else if (bottom > vm.scrollY + mainH()) vm.scrollY = bottom - mainH()
        }
    }

    // the icons on the sheet, drawn with the app's own icon set
    val iconPainters = HashMap<String, androidx.compose.ui.graphics.vector.VectorPainter>()
    for (name in vm.objects.filter { it.kind == "shape" && it.shape == "icon" }.map { it.text.lowercase() }.distinct().sorted()) {
        androidx.compose.runtime.key(name) {
            sheetIcon(name)?.let { iconPainters[name] = androidx.compose.ui.graphics.vector.rememberVectorPainter(it) }
        }
    }

    // a new object: scroll so all of it shows (its top left first when it is bigger than the view)
    LaunchedEffect(vm.objectRevealTick) {
        if (vm.objectRevealTick == 0) return@LaunchedEffect
        val o = vm.selectedObject ?: return@LaunchedEffect
        if (o.x + o.width > vm.scrollX + mainW()) vm.scrollX = minOf(o.x, o.x + o.width - mainW()).coerceAtLeast(vm.frozenW)
        if (o.x < vm.scrollX) vm.scrollX = o.x.coerceAtLeast(vm.frozenW)
        if (o.y + o.height > vm.scrollY + mainH()) vm.scrollY = minOf(o.y, o.y + o.height - mainH()).coerceAtLeast(vm.frozenH)
        if (o.y < vm.scrollY) vm.scrollY = o.y.coerceAtLeast(vm.frozenH)
    }

    // tell the model what is on screen, so it fetches the cells there
    LaunchedEffect(vm.scrollX, vm.scrollY, vm.zoom, size, vm.info?.revision, vm.info?.activeSheet, vm.frozenW, vm.frozenH) {
        if (size.width > 0f && size.height > 0f) vm.showWindow(GridWindow(vm.scrollX, vm.scrollY, mainW(), mainH()))
    }

    // sheet positions to the screen: in the scrolling part, and in the frozen rows or columns
    fun screenX(s: Double) = (rowHeaderW + fw + (s - vm.scrollX) * unit).toFloat()
    fun screenY(s: Double) = (colHeaderH + fh + (s - vm.scrollY) * unit).toFloat()
    fun fixedX(s: Double) = (rowHeaderW + s * unit).toFloat()
    fun fixedY(s: Double) = (colHeaderH + s * unit).toFloat()

    /** The frozen rows' and columns' lines (from whichever frozen viewport has them). */
    fun frozenRowLines(): List<Line> = (vm.frozenCorner ?: vm.frozenTop)?.rows.orEmpty()
    fun frozenColLines(): List<Line> = (vm.frozenCorner ?: vm.frozenLeft)?.cols.orEmpty()

    /** The row and column under a point of the cells. */
    fun rowAt(py: Float): Line? =
        if (fr > 0u && py < colHeaderH + fh) lineAt(frozenRowLines(), ((py - colHeaderH) / unit).toDouble())
        else vm.viewport?.let { lineAt(it.rows, vm.scrollY + (py - colHeaderH - fh) / unit) }
    fun colAt(px: Float): Line? =
        if (fc > 0u && px < rowHeaderW + fw) lineAt(frozenColLines(), ((px - rowHeaderW) / unit).toDouble())
        else vm.viewport?.let { lineAt(it.cols, vm.scrollX + (px - rowHeaderW - fw) / unit) }

    /** What a tap in the outline band runs: a level button, or a group's − or + button. */
    fun outlineTap(p: Offset): Pair<String, String>? {
        if (p.x < rowBand && p.y >= colBand) {
            val k = (p.x / lane).toInt() + 1
            if (p.y <= colHeaderH) return if (k <= rowLevels + 1) "data.showLevels" to json("rows" to k) else null
            val r = rowAt(p.y) ?: return null
            val g = sv?.rowGroups?.firstOrNull { it.level.toInt() == k && it.end + 1u == r.index } ?: return null
            return (if (g.collapsed) "data.showDetail" else "data.hideDetail") to json("rows" to "${g.start.toLong() + 1}:${g.end.toLong() + 1}", "level" to k)
        }
        if (p.y < colBand && p.x >= rowBand) {
            val k = (p.y / lane).toInt() + 1
            if (p.x <= rowHeaderW) return if (k <= colLevels + 1) "data.showLevels" to json("cols" to k) else null
            val c = colAt(p.x) ?: return null
            val g = sv?.colGroups?.firstOrNull { it.level.toInt() == k && it.end + 1u == c.index } ?: return null
            return (if (g.collapsed) "data.showDetail" else "data.hideDetail") to json("cols" to columnLetters(g.start.toInt()) + ":" + columnLetters(g.end.toInt()), "level" to k)
        }
        return null
    }

    fun scrollBy(dx: Float, dy: Float) {
        vm.scrollX = (vm.scrollX - dx / unit).coerceAtLeast(vm.frozenW)
        vm.scrollY = (vm.scrollY - dy / unit).coerceAtLeast(vm.frozenH)
    }

    /** Where a row's or column's edge is on screen: [end] its far edge. */
    fun rowEdge(i: UInt, end: Boolean): Float? {
        val frozen = i < fr
        val l = (if (frozen) frozenRowLines() else vm.viewport?.rows.orEmpty()).firstOrNull { it.index == i } ?: return null
        val s = if (end) l.start + l.size else l.start
        return if (frozen) fixedY(s) else screenY(s)
    }
    fun colEdge(i: UInt, end: Boolean): Float? {
        val frozen = i < fc
        val l = (if (frozen) frozenColLines() else vm.viewport?.cols.orEmpty()).firstOrNull { it.index == i } ?: return null
        val s = if (end) l.start + l.size else l.start
        return if (frozen) fixedX(s) else screenX(s)
    }

    /** The selection's corner handles on screen: top left and bottom right. */
    fun handles(): Pair<Offset, Offset>? {
        val r = vm.selection?.ranges?.lastOrNull() ?: return null
        val tl = Offset(colEdge(r.firstCol, false) ?: return null, rowEdge(r.firstRow, false) ?: return null)
        val br = Offset(colEdge(r.lastCol, true) ?: return null, rowEdge(r.lastRow, true) ?: return null)
        return tl to br
    }

    /** Fill mode's arrows: the middle of the selection's right edge and of its bottom edge. */
    fun fillHandles(): Pair<Offset, Offset>? {
        val (tl, br) = handles() ?: return null
        return Offset(br.x, (tl.y + br.y) / 2) to Offset((tl.x + br.x) / 2, br.y)
    }

    // a hardware keyboard's keys go to the grid while nothing else types
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(vm.editing, vm.findOpen, vm.dialog) {
        if (!vm.editing && !vm.findOpen && vm.dialog == null) runCatching { focus.requestFocus() }
    }
    Box(modifier) {
    Canvas(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent { e -> e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && vm.gridKey(e) }
            .focusable()
            .onSizeChanged { size = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(vm.viewport, vm.frozenCorner, vm.frozenTop, vm.frozenLeft, vm.selection, unit, fw, fh) {
                detectTapGestures(
                    onLongPress = { p ->
                        if (p.x > rowHeaderW && p.y > colHeaderH) {
                            val r = rowAt(p.y) ?: return@detectTapGestures
                            val c = colAt(p.x) ?: return@detectTapGestures
                            val inside = vm.selection?.ranges?.any { r.index in it.firstRow..it.lastRow && c.index in it.firstCol..it.lastCol } == true
                            if (!inside) vm.tapCell(r.index, c.index)
                            vm.cellMenu = true
                        }
                    },
                    onDoubleTap = { p ->
                        // a double tap on a header's edge fits that column or row (all the selected
                        // ones when it is among them) to what it holds
                        val edgeReach = 10f * density
                        val whole = vm.selection?.ranges?.lastOrNull()
                        if (p.y <= colHeaderH && p.y > colBand && p.x > rowHeaderW) {
                            val c = colAt(p.x - edgeReach)?.takeIf { abs((colEdge(it.index, true) ?: -1e9f) - p.x) < edgeReach }
                            if (c != null) {
                                val all = whole != null && whole.firstRow == 0u && whole.lastRow + 1u >= (sv?.maxRows ?: 1u) && c.index in whole.firstCol..whole.lastCol
                                val (a, b) = if (all) whole!!.firstCol to whole.lastCol else c.index to c.index
                                vm.autofitColumns(a, b)
                                return@detectTapGestures
                            }
                        }
                        if (p.x <= rowHeaderW && p.x > rowBand && p.y > colHeaderH) {
                            val r = rowAt(p.y - edgeReach)?.takeIf { abs((rowEdge(it.index, true) ?: -1e9f) - p.y) < edgeReach }
                            if (r != null) {
                                val all = whole != null && whole.firstCol == 0u && whole.lastCol + 1u >= (sv?.maxCols ?: 1u) && r.index in whole.firstRow..whole.lastRow
                                val (a, b) = if (all) whole!!.firstRow to whole.lastRow else r.index to r.index
                                vm.command("home.autofitRowHeight", json("rows" to "${a.toLong() + 1}:${b.toLong() + 1}"))
                                return@detectTapGestures
                            }
                        }
                        if (p.x > rowHeaderW && p.y > colHeaderH) {
                            val r = rowAt(p.y) ?: return@detectTapGestures
                            val c = colAt(p.x) ?: return@detectTapGestures
                            val active = vm.selection
                            if (active?.activeRow == r.index && active.activeCol == c.index) vm.beginEdit() else vm.tapCell(r.index, c.index)
                        }
                    },
                    onTap = { p ->
                        // the filter sheet up: a tap on the sheet puts it away
                        if (vm.filterAt != null) {
                            vm.closeFilter()
                            return@detectTapGestures
                        }
                        val menuWasUp = vm.cellMenu
                        vm.cellMenu = false
                        vm.fillMode = false
                        // the outline band: its buttons, nothing else
                        if ((p.x < rowBand && p.y > colBand) || (p.y < colBand && p.x > rowBand) || (p.x < rowBand && p.y < colBand)) {
                            outlineTap(p)?.let { (id, params) -> vm.command(id, params) }
                            return@detectTapGestures
                        }
                        if (p.x > rowHeaderW + fw && p.y > colHeaderH + fh) {
                            val sx = vm.scrollX + (p.x - rowHeaderW - fw) / unit
                            val sy = vm.scrollY + (p.y - colHeaderH - fh) / unit
                            val hit = vm.objects.lastOrNull { sx >= it.x && sx <= it.x + it.width && sy >= it.y && sy <= it.y + it.height }
                            if (hit != null) {
                                vm.selectObject(hit)
                                return@detectTapGestures
                            }
                        }
                        when {
                            p.x <= rowHeaderW && p.y <= colHeaderH -> vm.command("edit.selectAll")
                            p.y <= colHeaderH -> colAt(p.x)?.let { c ->
                                vm.selectRange(CellRange(0u, c.index, (sv?.maxRows ?: 1u) - 1u, c.index))
                            }
                            p.x <= rowHeaderW -> rowAt(p.y)?.let { r ->
                                vm.selectRange(CellRange(r.index, 0u, r.index, (sv?.maxCols ?: 1u) - 1u))
                            }
                            else -> {
                                val r = rowAt(p.y) ?: return@detectTapGestures
                                val c = colAt(p.x) ?: return@detectTapGestures
                                // the drop-down beside a cell with a validation list opens the list
                                if (vm.validation?.items?.isNotEmpty() == true) {
                                    val sel = vm.selection
                                    val ar = sel?.activeRow
                                    val right = sel?.let { colEdge(it.activeCol, true) }
                                    val top = ar?.let { rowEdge(it, false) }
                                    val bottom = ar?.let { rowEdge(it, true) }
                                    if (right != null && top != null && bottom != null && p.x in right..(right + 26f * density) && p.y in top..bottom) {
                                        vm.pickingList = true
                                        return@detectTapGestures
                                    }
                                }
                                // typing a formula: the tapped cell's reference goes into it
                                if (vm.pointing) {
                                    vm.pointAtCell(r.index, c.index)
                                    return@detectTapGestures
                                }
                                // a checkbox cell: a tap ticks or unticks it, and selects it
                                val tapped = (if (r.index < fr) vm.frozenTop else vm.viewport)?.cells?.firstOrNull { it.row == r.index && it.col == c.index }
                                if (tapped?.checkbox != null) {
                                    vm.tapCell(r.index, c.index)
                                    vm.command("cell.toggleCheckbox", json("cell" to columnLetters(c.index.toInt()) + (r.index.toLong() + 1)))
                                    return@detectTapGestures
                                }
                                // a filter's drop-down button opens the filter sheet
                                val cell = (if (r.index < fr) vm.frozenTop else vm.viewport)?.cells?.firstOrNull { it.row == r.index && it.col == c.index }
                                val right = colEdge(c.index, true)
                                if (cell?.filterButton == true && right != null && p.x > right - 22f * density) {
                                    vm.openFilter(r.index, c.index)
                                    return@detectTapGestures
                                }
                                // a tap on the selection shows the cell menu; another puts it away
                                val inside = vm.selection?.ranges?.any { r.index in it.firstRow..it.lastRow && c.index in it.firstCol..it.lastCol } == true
                                if (inside && !vm.editing && !vm.formatPainting && vm.drawTool.isEmpty()) {
                                    vm.cellMenu = !menuWasUp
                                    // any cell tapped: the floating command bar takes the sheets bar's place
                                    vm.showSheetsBar(false)
                                    return@detectTapGestures
                                }
                                vm.tapCell(r.index, c.index)
                            }
                        }
                    },
                )
            }
            .pointerInput(unit, rowHeaderW, colHeaderH, fw, fh) {
                val decay = splineBasedDecay<Float>(this)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fling?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // typing a formula: the handle on the reference tapped into it stretches it to a block
                    val stretch = vm.pointedBlock?.takeIf { vm.pointedStretchable }?.let { b ->
                        val x = colEdge(b.lastCol, true)
                        val y = rowEdge(b.lastRow, true)
                        if (x != null && y != null && hypot(down.position.x - x, down.position.y - y) < HANDLE_REACH_DP * density) b else null
                    }
                    if (stretch != null) {
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.pressed } ?: break
                            val r = rowAt(ch.position.y)
                            val c = colAt(ch.position.x)
                            if (r != null && c != null) vm.stretchPointed(r.index, c.index)
                            ch.consume()
                        }
                        return@awaitEachGesture
                    }
                    // Fill: a drag on an arrow fills the selection's series right or down
                    val fillFrom = vm.selection?.ranges?.lastOrNull()?.takeIf { vm.fillMode }
                    val fillDown = fillHandles()?.takeIf { fillFrom != null }?.let { (rightPt, downPt) ->
                        val near = HANDLE_REACH_DP * density
                        when {
                            hypot(down.position.x - rightPt.x, down.position.y - rightPt.y) < near -> false
                            hypot(down.position.x - downPt.x, down.position.y - downPt.y) < near -> true
                            else -> null
                        }
                    }
                    if (fillFrom != null && fillDown != null) {
                        down.consume()
                        var target: CellRange = fillFrom
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.pressed } ?: break
                            if (fillDown) {
                                rowAt(ch.position.y)?.let { r -> target = fillFrom.copy(lastRow = maxOf(fillFrom.lastRow, r.index)) }
                            } else {
                                colAt(ch.position.x)?.let { c -> target = fillFrom.copy(lastCol = maxOf(fillFrom.lastCol, c.index)) }
                            }
                            vm.fillPreview = target
                            ch.consume()
                        }
                        vm.fillPreview = null
                        if (target != fillFrom) vm.autoFill(fillFrom, target)
                        return@awaitEachGesture
                    }
                    // a touch on a selection handle drags that corner
                    val hs = handles()
                    val reach = HANDLE_REACH_DP * density
                    val handle = when {
                        hs == null -> 0
                        hypot(down.position.x - hs.second.x, down.position.y - hs.second.y) < reach -> 2
                        hypot(down.position.x - hs.first.x, down.position.y - hs.first.y) < reach -> 1
                        else -> 0
                    }
                    // a touch on a header's edge resizes that column or row
                    val edgeReach = 10f * density
                    val resizeCol = if (down.position.y <= colHeaderH && down.position.y > colBand && down.position.x > rowHeaderW) {
                        val c = colAt(down.position.x - edgeReach)
                        c?.takeIf { abs((colEdge(it.index, true) ?: -1e9f) - down.position.x) < edgeReach }
                    } else null
                    val resizeRow = if (down.position.x <= rowHeaderW && down.position.x > rowBand && down.position.y > colHeaderH) {
                        val r = rowAt(down.position.y - edgeReach)
                        r?.takeIf { abs((rowEdge(it.index, true) ?: -1e9f) - down.position.y) < edgeReach }
                    } else null
                    var resized = 0f
                    // a drag along the column (or row) headers selects the columns (rows) it crosses
                    val headerCol = if (resizeCol == null && down.position.y <= colHeaderH && down.position.y > colBand && down.position.x > rowHeaderW) colAt(down.position.x) else null
                    val headerRow = if (resizeRow == null && down.position.x <= rowHeaderW && down.position.x > rowBand && down.position.y > colHeaderH) rowAt(down.position.y) else null
                    // drawing: the pen leaves ink, the eraser takes it away
                    if (vm.drawTool.isNotEmpty() && down.position.x > rowHeaderW + fw && down.position.y > colHeaderH + fh) {
                        fun sheetAt(p: Offset) = Offset((vm.scrollX + (p.x - rowHeaderW - fw) / unit).toFloat(), (vm.scrollY + (p.y - colHeaderH - fh) / unit).toFloat())
                        if (vm.drawTool == "eraser") {
                            val s0 = sheetAt(down.position)
                            vm.eraseAt(s0.x.toDouble(), s0.y.toDouble())
                        } else {
                            vm.stroke = listOf(sheetAt(down.position))
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.pressed } ?: break
                            val s1 = sheetAt(ch.position)
                            if (vm.drawTool == "eraser") vm.eraseAt(s1.x.toDouble(), s1.y.toDouble()) else vm.stroke = vm.stroke + s1
                            ch.consume()
                        }
                        if (vm.drawTool != "eraser") vm.finishStroke()
                        return@awaitEachGesture
                    }
                    // the selected object: a touch inside it (or on its move handle below) moves it,
                    // on one of its eight handles resizes it from that side, on its rotate handle
                    // above turns it, as the phone app's handles do
                    val obj = vm.selectedObject
                    val objLeft = obj?.let { screenX(it.x) } ?: 0f
                    val objTop = obj?.let { screenY(it.y) } ?: 0f
                    val objW = obj?.let { (it.width * unit).toFloat() } ?: 0f
                    val objH = obj?.let { (it.height * unit).toFloat() } ?: 0f
                    // a line has only its two ends (no rotate or move handle)
                    val ends = obj?.takeIf { it.isLine }?.lineEnds
                    val turnable = obj != null && obj.kind != "chart" && ends == null && vm.editingShapeText == null && !vm.cropping
                    val centre = Offset(objLeft + objW / 2, objTop + objH / 2)
                    // a turned object's handles turn with it: touches are taken back into its frame
                    val turnRad = Math.toRadians((obj?.rotation ?: 0f).toDouble())
                    val (tc, ts) = kotlin.math.cos(turnRad).toFloat() to kotlin.math.sin(turnRad).toFloat()
                    fun unturn(v: Offset) = Offset(v.x * tc + v.y * ts, -v.x * ts + v.y * tc)
                    val at = centre + unturn(down.position - centre)
                    val onRotate = turnable && hypot(at.x - centre.x, at.y - (objTop - ROTATE_HANDLE_DP * density)) < reach
                    val onMoveHandle = turnable && hypot(at.x - centre.x, at.y - (objTop + objH + MOVE_HANDLE_DP * density)) < reach
                    val grip = if (obj == null || onRotate || onMoveHandle) null else {
                        (ends ?: listOf(0f, 0.5f, 1f).flatMap { fx -> listOf(0f, 0.5f, 1f).map { fy -> fx to fy } }.filter { it != 0.5f to 0.5f })
                            .minByOrNull { (fx, fy) -> hypot(at.x - (objLeft + objW * fx), at.y - (objTop + objH * fy)) }
                            ?.takeIf { (fx, fy) -> hypot(at.x - (objLeft + objW * fx), at.y - (objTop + objH * fy)) < reach }
                    }
                    val onCorner = grip != null
                    val inside = obj != null && (onMoveHandle || (at.x in objLeft..(objLeft + objW) && at.y in objTop..(objTop + objH)))
                    val startAngle = Math.toDegrees(kotlin.math.atan2((down.position.y - centre.y).toDouble(), (down.position.x - centre.x).toDouble())).toFloat()
                    var objMove = Offset.Zero
                    val fixed = vm.selection?.ranges?.lastOrNull()
                    var lastCell: Pair<UInt, UInt>? = null
                    var moved = false
                    var zoomed = false
                    var travel = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            zoomed = true
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            val c = event.calculateCentroid()
                            if (z != 1f || pan != Offset.Zero) {
                                // zoom about the fingers' centre: the cell under it stays there
                                val u0 = UNIT_DP * density * vm.zoom
                                val beforeX = vm.scrollX + (c.x - rowHeaderW) / u0 - vm.frozenW
                                val beforeY = vm.scrollY + (c.y - colHeaderH) / u0 - vm.frozenH
                                vm.zoom = (vm.zoom * z).coerceIn(0.1f, 4f)
                                val u = UNIT_DP * density * vm.zoom
                                vm.scrollX = (beforeX - (c.x - rowHeaderW) / u + vm.frozenW - pan.x / u).coerceAtLeast(vm.frozenW)
                                vm.scrollY = (beforeY - (c.y - colHeaderH) / u + vm.frozenH - pan.y / u).coerceAtLeast(vm.frozenH)
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val ch = pressed.first()
                        val d = ch.positionChange()
                        travel += d.getDistance()
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        if (!moved && travel > touchSlop) moved = true
                        if (moved && !zoomed && onRotate) {
                            val a = Math.toDegrees(kotlin.math.atan2((ch.position.y - centre.y).toDouble(), (ch.position.x - centre.x).toDouble())).toFloat()
                            vm.objectTurn = a - startAngle
                            ch.consume()
                            continue
                        }
                        if (moved && !zoomed && obj != null && grip != null && vm.cropping && obj.kind == "image") {
                            // crop mode: the mark dragged cuts the picture from its side; what stays
                            // keeps its size and place
                            objMove += unturn(d)
                            val c = obj.crop.takeIf { it.size == 4 && it[2] > 0f && it[3] > 0f } ?: listOf(0f, 0f, 1f, 1f)
                            val (wholeW, wholeH) = objW / c[2] to objH / c[3]
                            val (fx, fy) = grip
                            var (l, t, cw, chh) = c
                            when (fx) {
                                0f -> { val nl = (c[0] + objMove.x / wholeW).coerceIn(0f, c[0] + c[2] - 0.02f); cw = c[2] - (nl - c[0]); l = nl }
                                1f -> cw = (c[2] + objMove.x / wholeW).coerceIn(0.02f, 1f - c[0])
                            }
                            when (fy) {
                                0f -> { val nt = (c[1] + objMove.y / wholeH).coerceIn(0f, c[1] + c[3] - 0.02f); chh = c[3] - (nt - c[1]); t = nt }
                                1f -> chh = (c[3] + objMove.y / wholeH).coerceIn(0.02f, 1f - c[1])
                            }
                            vm.cropPreview = listOf(l, t, cw, chh)
                            vm.objectDrag = Offset((l - c[0]) * wholeW, (t - c[1]) * wholeH) to Offset((cw - c[2]) * wholeW, (chh - c[3]) * wholeH)
                            ch.consume()
                            continue
                        }
                        if (moved && !zoomed && obj != null && (onCorner || inside)) {
                            // a move goes as the finger does; a resize along the object's own sides
                            objMove += if (grip != null) unturn(d) else d
                            vm.objectDrag = grip?.let { (fx, fy) ->
                                // the dragged side moves; the opposite one stays
                                val mx = if (fx == 0f) objMove.x.coerceAtMost(objW - 1f) else 0f
                                val my = if (fy == 0f) objMove.y.coerceAtMost(objH - 1f) else 0f
                                val dw = when (fx) { 0f -> -mx; 1f -> objMove.x.coerceAtLeast(1f - objW); else -> 0f }
                                val dh = when (fy) { 0f -> -my; 1f -> objMove.y.coerceAtLeast(1f - objH); else -> 0f }
                                Offset(mx, my) to Offset(dw, dh)
                            } ?: (objMove to Offset.Zero)
                            ch.consume()
                            continue
                        }
                        if (moved && !zoomed && (resizeCol != null || resizeRow != null)) {
                            resized += if (resizeCol != null) d.x else d.y
                            vm.resizePreview = Triple(resizeCol != null, (resizeCol ?: resizeRow)!!.index, resized / unit)
                            ch.consume()
                            continue
                        }
                        if (moved && !zoomed && (headerCol != null || headerRow != null)) {
                            val maxRow = (sv?.maxRows ?: 1u) - 1u
                            val maxCol = (sv?.maxCols ?: 1u) - 1u
                            if (headerCol != null) {
                                colAt(ch.position.x.coerceAtLeast(rowHeaderW + 1f))?.let { c ->
                                    if (lastCell != (0u to c.index)) {
                                        lastCell = 0u to c.index
                                        vm.selectRange(CellRange(0u, minOf(headerCol.index, c.index), maxRow, maxOf(headerCol.index, c.index)))
                                    }
                                }
                            } else if (headerRow != null) {
                                rowAt(ch.position.y.coerceAtLeast(colHeaderH + 1f))?.let { r ->
                                    if (lastCell != (r.index to 0u)) {
                                        lastCell = r.index to 0u
                                        vm.selectRange(CellRange(minOf(headerRow.index, r.index), 0u, maxOf(headerRow.index, r.index), maxCol))
                                    }
                                }
                            }
                            ch.consume()
                            continue
                        }
                        if (moved && !zoomed) {
                            if (handle != 0 && fixed != null) {
                                val r = rowAt(ch.position.y)
                                val c = colAt(ch.position.x)
                                if (r != null && c != null && lastCell != (r.index to c.index)) {
                                    lastCell = r.index to c.index
                                    val range = if (handle == 2) {
                                        CellRange(fixed.firstRow, fixed.firstCol, r.index, c.index)
                                    } else {
                                        CellRange(r.index, c.index, fixed.lastRow, fixed.lastCol)
                                    }
                                    vm.selectRange(normalised(range))
                                }
                            } else {
                                scrollBy(d.x, d.y)
                            }
                            ch.consume()
                        }
                    }
                    if (onRotate) {
                        vm.rotateObject(vm.objectTurn ?: 0f)
                        return@awaitEachGesture
                    }
                    val cut = vm.cropPreview
                    if (cut != null) {
                        vm.objectDrag = null
                        vm.cropPicture(cut[0].toDouble(), cut[1].toDouble(), (1f - cut[0] - cut[2]).toDouble().coerceAtLeast(0.0), (1f - cut[1] - cut[3]).toDouble().coerceAtLeast(0.0))
                        return@awaitEachGesture
                    }
                    if (obj != null && (onCorner || inside)) {
                        val drag = vm.objectDrag
                        vm.objectDrag = null
                        if (moved && drag != null) {
                            val (m, s) = drag
                            vm.placeObject(obj.x + m.x / unit, obj.y + m.y / unit, obj.width + s.x / unit, obj.height + s.y / unit)
                        }
                        return@awaitEachGesture
                    }
                    if (resizeCol != null || resizeRow != null) {
                        vm.resizePreview = null
                        if (moved) {
                            val line = (resizeCol ?: resizeRow)!!
                            val size = (line.size + resized / unit).coerceIn(2f, 1000f)
                            if (resizeCol != null) vm.setColumnWidth(line.index, size) else vm.setRowHeight(line.index, size)
                        }
                        return@awaitEachGesture
                    }
                    if (moved && !zoomed && handle == 0) {
                        val vel = tracker.calculateVelocity()
                        val speed = hypot(vel.x, vel.y).coerceAtMost(11000f)
                        if (speed > 200f) {
                            val (ux, uy) = vel.x / hypot(vel.x, vel.y) to vel.y / hypot(vel.x, vel.y)
                            fling = scope.launch {
                                var last = 0f
                                AnimationState(0f, speed).animateDecay(decay) {
                                    val step = value - last
                                    last = value
                                    scrollBy(step * ux, step * uy)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        drawRect(GridColors.Cells)
        val v = vm.viewport ?: return@Canvas
        val grid = sv?.gridlines != false
        val pictures = vm.cellPictures
        val sheet = vm.info?.activeSheet
        val cellPicture = { row: UInt, col: UInt -> sheet?.let { pictures[Triple(it, row, col)] } }
        val refs = if (vm.editing) formulaRefs(vm.editText) else emptyList()
        val x0 = rowHeaderW + fw
        val y0 = colHeaderH + fh
        clipRect(x0, y0, size.width, size.height) {
            drawCells(context, v, unit, ::screenX, ::screenY, grid, density, cellPicture)
            // the cell selection goes under the objects floating over the sheet
            drawSelection(vm, v, ::screenX, ::screenY, density)
            drawFormulaRefs(refs, v, ::screenX, ::screenY, density)
            drawObjects(vm, unit, ::screenX, ::screenY, density, iconPainters)
        }
        vm.frozenTop?.takeIf { fr > 0u }?.let { t ->
            clipRect(x0, colHeaderH, size.width, y0) {
                drawRect(GridColors.Cells, Offset(x0, colHeaderH), Size(size.width - x0, fh))
                drawCells(context, t, unit, ::screenX, ::fixedY, grid, density, cellPicture)
                drawSelection(vm, t, ::screenX, ::fixedY, density)
                drawFormulaRefs(refs, t, ::screenX, ::fixedY, density)
            }
        }
        vm.frozenLeft?.takeIf { fc > 0u }?.let { l ->
            clipRect(rowHeaderW, y0, x0, size.height) {
                drawRect(GridColors.Cells, Offset(rowHeaderW, y0), Size(fw, size.height - y0))
                drawCells(context, l, unit, ::fixedX, ::screenY, grid, density, cellPicture)
                drawSelection(vm, l, ::fixedX, ::screenY, density)
                drawFormulaRefs(refs, l, ::fixedX, ::screenY, density)
            }
        }
        vm.frozenCorner?.takeIf { fr > 0u && fc > 0u }?.let { c ->
            clipRect(rowHeaderW, colHeaderH, x0, y0) {
                drawRect(GridColors.Cells, Offset(rowHeaderW, colHeaderH), Size(fw, fh))
                drawCells(context, c, unit, ::fixedX, ::fixedY, grid, density, cellPicture)
                drawSelection(vm, c, ::fixedX, ::fixedY, density)
                drawFormulaRefs(refs, c, ::fixedX, ::fixedY, density)
            }
        }
        // the drop-down beside the active cell when it has a validation list
        if (vm.validation?.items?.isNotEmpty() == true && !vm.editing) {
            val sel = vm.selection
            val right = sel?.let { colEdge(it.activeCol, true) }
            val top = sel?.let { rowEdge(it.activeRow, false) }
            val bottom = sel?.let { rowEdge(it.activeRow, true) }
            if (right != null && top != null && bottom != null) {
                val w = 24f * density
                val h = (bottom - top).coerceAtMost(28f * density)
                drawRect(GridColors.Cells, Offset(right + 2f, top), Size(w, h))
                drawRect(GridColors.HeaderLine, Offset(right + 2f, top), Size(w, h), style = Stroke(1f))
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(right + 2f + w * 0.28f, top + h * 0.4f)
                    lineTo(right + 2f + w * 0.72f, top + h * 0.4f)
                    lineTo(right + 2f + w * 0.5f, top + h * 0.65f)
                    close()
                }
                drawPath(path, GridColors.OnHeader)
            }
        }
        // the handle that stretches the reference just tapped into the formula
        vm.pointedBlock?.takeIf { vm.pointedStretchable }?.let { b ->
            val x = colEdge(b.lastCol, true)
            val y = rowEdge(b.lastRow, true)
            if (x != null && y != null) {
                val color = refs.lastOrNull()?.color ?: GridColors.SheetAccent
                drawCircle(GridColors.Cells, 6f * density, Offset(x, y))
                drawCircle(color, 6f * density, Offset(x, y), style = Stroke(1.5f * density))
            }
        }
        // the text being typed shows in its cell too, over the cells to its right when longer
        if (vm.editing && vm.dialog == null) {
            val sel = vm.selection
            val left = sel?.let { colEdge(it.activeCol, false) }
            val right = sel?.let { colEdge(it.activeCol, true) }
            val top = sel?.let { rowEdge(it.activeRow, false) }
            val bottom = sel?.let { rowEdge(it.activeRow, true) }
            if (left != null && right != null && top != null && bottom != null) {
                val f = vm.format
                val p = editPaint
                p.typeface = CellFonts.typeface(context, f?.font ?: "Calibri", f?.bold == true, f?.italic == true)
                p.textSize = ((f?.size ?: 11f).coerceIn(1f, 409f) * 96f / 72f * unit).coerceAtLeast(1f)
                p.color = GridColors.OnCells.toArgb()
                val pad = 2f * unit
                val text = vm.editText
                val end = maxOf(right, (left + 2 * pad + p.measureText(text)).coerceAtMost(size.width))
                drawRect(GridColors.Cells, Offset(left, top), Size(end - left, bottom - top))
                drawRect(GridColors.SheetAccent, Offset(left, top), Size(end - left, bottom - top), style = Stroke(2f * density))
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    nc.save()
                    nc.clipRect(left, top, end, bottom)
                    var x = left + pad
                    val y = bottom - pad - p.descent()
                    var at = 0
                    for (r in refs + null) {
                        val to = r?.start ?: text.length
                        if (to > at) {
                            p.color = GridColors.OnCells.toArgb()
                            nc.drawText(text, at, to, x, y, p)
                            x += p.measureText(text, at, to)
                        }
                        if (r == null) break
                        p.color = r.color.toArgb()
                        nc.drawText(text, r.start, r.end, x, y, p)
                        x += p.measureText(text, r.start, r.end)
                        at = r.end
                    }
                    nc.restore()
                }
            }
        }
        // the stroke being drawn
        val pts = vm.stroke
        if (pts.size >= 2) {
            val ink = Color(android.graphics.Color.parseColor(vm.penColor)).let { if (vm.penWidth > 8f) it.copy(alpha = 0.45f) else it }
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(screenX(pts[0].x.toDouble()), screenY(pts[0].y.toDouble()))
                for (i in 1 until pts.size) lineTo(screenX(pts[i].x.toDouble()), screenY(pts[i].y.toDouble()))
            }
            drawPath(path, ink, style = Stroke(vm.penWidth * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        // a column or row being resized: a line where its edge will be
        vm.resizePreview?.let { (col, index, delta) ->
            if (col) {
                colEdge(index, true)?.let { x -> drawLine(GridColors.SheetAccent, Offset(x + delta * unit, 0f), Offset(x + delta * unit, size.height), 1.5f * density) }
            } else {
                rowEdge(index, true)?.let { y -> drawLine(GridColors.SheetAccent, Offset(0f, y + delta * unit), Offset(size.width, y + delta * unit), 1.5f * density) }
            }
        }
        // Fill: the block a drag would fill, dashed, and the arrows to drag
        if (vm.fillMode) {
            vm.fillPreview?.let { t ->
                val l = colEdge(t.firstCol, false)
                val tp = rowEdge(t.firstRow, false)
                if (l != null && tp != null) {
                    val r = colEdge(t.lastCol, true) ?: size.width
                    val b = rowEdge(t.lastRow, true) ?: size.height
                    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density))
                    drawRect(GridColors.SheetAccent, Offset(l, tp), Size(r - l, b - tp), style = Stroke(2f * density, pathEffect = dash))
                }
            }
            fillHandles()?.let { (rightPt, downPt) ->
                drawFillArrow(rightPt, true, density)
                drawFillArrow(downPt, false, density)
            }
        }
        // the freeze lines
        if (fr > 0u) drawLine(GridColors.HeaderLine, Offset(rowHeaderW, y0), Offset(size.width, y0), 1.5f * density)
        if (fc > 0u) drawLine(GridColors.HeaderLine, Offset(x0, colHeaderH), Offset(x0, size.height), 1.5f * density)
        if (headings) {
            val colPanes = buildList {
                if (fc > 0u) add(HeaderPane(frozenColLines(), ::fixedX, rowHeaderW, x0))
                add(HeaderPane(v.cols, ::screenX, x0, size.width))
            }
            val rowPanes = buildList {
                if (fr > 0u) add(HeaderPane(frozenRowLines(), ::fixedY, colHeaderH, y0))
                add(HeaderPane(v.rows, ::screenY, y0, size.height))
            }
            drawHeaders(vm, rowHeaderW, colHeaderH, rowBand, colBand, colPanes, rowPanes, density)
            if (sv != null && (rowBand > 0f || colBand > 0f)) drawOutline(sv, rowBand, colBand, rowHeaderW, colHeaderH, lane, colPanes, rowPanes, density)
        }
    }
    // a shape's text typed in place
    vm.selectedObject?.takeIf { it.kind == "shape" && vm.editingShapeText == it.id.toLong() }?.let { o ->
        ShapeTextEditor(vm, o, screenX(o.x), screenY(o.y), (o.width * unit).toFloat(), (o.height * unit).toFloat(), unit)
    }
    // the selected object's menu, just above it (below it when it is at the top), only while it
    // shows and the palette is closed, as the phone app hides it under an open palette
    vm.selectedObject?.takeIf { vm.objectDrag == null && vm.editingShapeText == null && !vm.cropping && !vm.paletteOpen }?.let { o ->
        val left = screenX(o.x)
        val top = screenY(o.y)
        val w = (o.width * unit).toFloat()
        val h = (o.height * unit).toFloat()
        val onScreen = left < size.width && top < size.height && left + w > rowHeaderW && top + h > colHeaderH
        if (onScreen) {
            var menuSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            // over a shape's rotate handle, as the phone app keeps it clear
            val gap = (if (o.kind != "chart") 48f else 8f) * density
            val above = top - menuSize.height - gap
            val y = if (above >= colHeaderH) above else minOf(top + h + gap, size.height - menuSize.height - gap)
            val x = (left + w / 2 - menuSize.width / 2).coerceIn(0f, (size.width - menuSize.width).coerceAtLeast(0f))
            ObjectMenu(
                vm,
                Modifier.onSizeChanged { menuSize = it }.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.coerceAtLeast(0f).toInt()) },
            )
        }
    }
    // the cell menu, just above the selection (below it when that is at the top), as the phone app shows it
    if (vm.cellMenu) {
        var menuSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val (tl, br) = handles() ?: (Offset(rowHeaderW, colHeaderH) to Offset(size.width, colHeaderH))
        val top = tl.y.coerceIn(colHeaderH, size.height)
        val bottom = br.y.coerceIn(colHeaderH, size.height)
        val gap = 8f * density
        // it may cover the column letters, as the phone app's does
        val above = top - menuSize.height - gap
        val y = if (above >= 0f) above else minOf(bottom + gap, size.height - menuSize.height - gap)
        val mid = (tl.x.coerceIn(rowHeaderW, size.width) + br.x.coerceIn(rowHeaderW, size.width)) / 2
        val x = (mid - menuSize.width / 2).coerceIn(0f, (size.width - menuSize.width).coerceAtLeast(0f))
        CellMenu(vm, Modifier.onSizeChanged { menuSize = it }.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.coerceAtLeast(0f).toInt()) })
    }
    }
}

/**
 * The outline band over the headers' outer edge: the level buttons (1 shows only the top
 * summaries, the last everything), a bracket along each open group ending in its − button on the
 * summary line below (or right of) it, and a + for a closed group.
 */
private fun DrawScope.drawOutline(
    sv: io.github.artcraftandroid.gridcraft.core.SheetView,
    rowBand: Float,
    colBand: Float,
    rowHeaderW: Float,
    colHeaderH: Float,
    lane: Float,
    colPanes: List<HeaderPane>,
    rowPanes: List<HeaderPane>,
    density: Float,
) {
    val ink = GridColors.OutlineInk
    val box = lane * 0.88f
    val stroke = 1f * density
    // a filled square with no border, as the phone app draws its outline buttons
    fun button(cx: Float, cy: Float, label: String) {
        drawRect(GridColors.OutlineButton, Offset(cx - box / 2, cy - box / 2), Size(box, box))
        val arm = box * 0.24f
        when (label) {
            "-" -> drawLine(ink, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke * 1.4f)
            "+" -> {
                drawLine(ink, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke * 1.4f)
                drawLine(ink, Offset(cx, cy - arm), Offset(cx, cy + arm), stroke * 1.4f)
            }
            else -> drawIntoCanvas { c ->
                val p = textPaint
                p.typeface = android.graphics.Typeface.DEFAULT
                p.textSize = box * 0.85f
                p.isUnderlineText = false
                p.isStrikeThruText = false
                p.color = ink.toArgb()
                c.nativeCanvas.drawText(label, cx - p.measureText(label) / 2, cy - (p.ascent() + p.descent()) / 2, p)
            }
        }
    }
    if (rowBand > 0f) {
        drawRect(GridColors.OutlineBand, Offset(0f, 0f), Size(rowBand, size.height))
        drawLine(GridColors.HeaderLine, Offset(rowBand - 0.5f, colBand), Offset(rowBand - 0.5f, size.height), 1f)
        for (k in 1..sv.rowLevels.toInt() + 1) button((k - 0.5f) * lane, (colBand + colHeaderH) / 2, k.toString())
        for (pane in rowPanes) {
            val lo = pane.lines.firstOrNull()?.index ?: continue
            val hi = pane.lines.last().index
            val at = pane.lines.associateBy { it.index }
            clipRect(0f, pane.from, rowBand, pane.to) {
                for (g in sv.rowGroups) {
                    if (g.end + 1u < lo || g.start > hi) continue
                    val x = (g.level.toInt() - 0.5f) * lane
                    val summary = at[g.end + 1u]?.let { pane.at(it.start + it.size / 2) }
                    if (!g.collapsed) {
                        val top = at[g.start]?.let { pane.at(it.start) + 2f * density } ?: pane.from
                        val bottom = summary?.let { it - box / 2 } ?: at[g.end]?.let { pane.at(it.start + it.size) } ?: pane.to
                        if (bottom > top) {
                            drawLine(ink, Offset(x, top), Offset(x, bottom), stroke)
                            if (at[g.start] != null) drawLine(ink, Offset(x, top), Offset(x + lane * 0.35f, top), stroke)
                        }
                    }
                    if (summary != null) button(x, summary, if (g.collapsed) "+" else "-")
                }
            }
        }
    }
    if (colBand > 0f) {
        drawRect(GridColors.OutlineBand, Offset(0f, 0f), Size(size.width, colBand))
        drawLine(GridColors.HeaderLine, Offset(rowBand, colBand - 0.5f), Offset(size.width, colBand - 0.5f), 1f)
        for (k in 1..sv.colLevels.toInt() + 1) button((rowBand + rowHeaderW) / 2, (k - 0.5f) * lane, k.toString())
        for (pane in colPanes) {
            val lo = pane.lines.firstOrNull()?.index ?: continue
            val hi = pane.lines.last().index
            val at = pane.lines.associateBy { it.index }
            clipRect(pane.from, 0f, pane.to, colBand) {
                for (g in sv.colGroups) {
                    if (g.end + 1u < lo || g.start > hi) continue
                    val y = (g.level.toInt() - 0.5f) * lane
                    val summary = at[g.end + 1u]?.let { pane.at(it.start + it.size / 2) }
                    if (!g.collapsed) {
                        val left = at[g.start]?.let { pane.at(it.start) + 2f * density } ?: pane.from
                        val right = summary?.let { it - box / 2 } ?: at[g.end]?.let { pane.at(it.start + it.size) } ?: pane.to
                        if (right > left) {
                            drawLine(ink, Offset(left, y), Offset(right, y), stroke)
                            if (at[g.start] != null) drawLine(ink, Offset(left, y), Offset(left, y + lane * 0.35f), stroke)
                        }
                    }
                    if (summary != null) button(summary, y, if (g.collapsed) "+" else "-")
                }
            }
        }
    }
    if (rowBand > 0f && colBand > 0f) drawRect(GridColors.OutlineBand, Offset(0f, 0f), Size(rowBand, colBand))
}

/** A Fill arrow: a round button pointing right or down. */
private fun DrawScope.drawFillArrow(at: Offset, right: Boolean, density: Float) {
    val r = 11f * density
    drawCircle(GridColors.SheetAccent, r, at)
    drawCircle(Color.White, r, at, style = Stroke(1.5f * density))
    val a = r * 0.45f
    val tri = androidx.compose.ui.graphics.Path().apply {
        if (right) {
            moveTo(at.x + a, at.y)
            lineTo(at.x - a * 0.6f, at.y - a)
            lineTo(at.x - a * 0.6f, at.y + a)
        } else {
            moveTo(at.x, at.y + a)
            lineTo(at.x - a, at.y - a * 0.6f)
            lineTo(at.x + a, at.y - a * 0.6f)
        }
        close()
    }
    drawPath(tri, Color.White)
}

/** A run of header lines on screen: the lines, where they go, and the stretch they're clipped to. */
private class HeaderPane(val lines: List<Line>, val at: (Double) -> Float, val from: Float, val to: Float)

/** A block with its first corner top left. */
private fun normalised(r: CellRange) = CellRange(
    minOf(r.firstRow, r.lastRow),
    minOf(r.firstCol, r.lastCol),
    maxOf(r.firstRow, r.lastRow),
    maxOf(r.firstCol, r.lastCol),
)

private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
private val editPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

private fun DrawScope.drawCells(
    context: android.content.Context,
    v: Viewport,
    unit: Float,
    screenX: (Double) -> Float,
    screenY: (Double) -> Float,
    gridlines: Boolean,
    density: Float,
    cellPicture: (UInt, UInt) -> androidx.compose.ui.graphics.ImageBitmap?,
) {
    val rows = v.rows
    val cols = v.cols
    if (rows.isEmpty() || cols.isEmpty()) return
    val rowOf = HashMap<UInt, Line>(rows.size * 2)
    for (r in rows) rowOf[r.index] = r
    val colOf = HashMap<UInt, Line>(cols.size * 2)
    for (c in cols) colOf[c.index] = c
    val top = screenY(rows.first().start)
    val bottom = screenY(rows.last().start + rows.last().size)
    val left = screenX(cols.first().start)
    val right = screenX(cols.last().start + cols.last().size)
    // merged blocks: where they reach, gridlines don't
    val merges = v.merges

    fun mergeOf(row: UInt, col: UInt) = merges.firstOrNull { row in it.firstRow..it.lastRow && col in it.firstCol..it.lastCol }

    fun cellRect(c: CellView): FloatArray? {
        val m = mergeOf(c.row, c.col)
        val r0 = rowOf[c.row] ?: return null
        val c0 = colOf[c.col] ?: return null
        val r1 = m?.let { rowOf[it.lastRow] } ?: r0
        val c1 = m?.let { colOf[it.lastCol] } ?: c0
        // a block reaching past the viewport ends at its edge (the next fetch draws the rest)
        return floatArrayOf(screenX(c0.start), screenY(r0.start), screenX(c1.start + c1.size), screenY(r1.start + r1.size))
    }

    // fills
    for (c in v.cells) {
        val fill = c.fill ?: continue
        val r = cellRect(c) ?: continue
        drawRect(Color(fill), Offset(r[0], r[1]), Size(r[2] - r[0], r[3] - r[1]))
    }
    // gridlines, broken where a fill or a merged block covers them
    if (gridlines) {
        val grid = GridColors.GridLine
        for (c in cols) {
            val x = screenX(c.start + c.size) - 0.5f
            drawLine(grid, Offset(x, top), Offset(x, bottom), 1f)
        }
        for (r in rows) {
            val y = screenY(r.start + r.size) - 0.5f
            drawLine(grid, Offset(left, y), Offset(right, y), 1f)
        }
        val cover = GridColors.Cells
        for (m in merges) {
            val r0 = rowOf[m.firstRow] ?: continue
            val c0 = colOf[m.firstCol] ?: continue
            val r1 = rowOf[m.lastRow] ?: rows.last()
            val c1 = colOf[m.lastCol] ?: cols.last()
            val x0 = screenX(c0.start) + 0.5f
            val y0 = screenY(r0.start) + 0.5f
            drawRect(cover, Offset(x0, y0), Size(screenX(c1.start + c1.size) - x0 - 1f, screenY(r1.start + r1.size) - y0 - 1f))
        }
        for (c in v.cells) {
            val fill = c.fill ?: continue
            val r = cellRect(c) ?: continue
            drawRect(Color(fill), Offset(r[0], r[1]), Size(r[2] - r[0], r[3] - r[1]))
        }
    }
    // data bars
    for (c in v.cells) {
        val bar = c.bar ?: continue
        val r = cellRect(c) ?: continue
        val pad = 2f * density
        val x0 = r[0] + pad
        val w = (r[2] - r[0] - 2 * pad) * bar.fraction
        val h = r[3] - r[1] - 2 * pad
        if (w <= 0f || h <= 0f) continue
        val col = Color(bar.color)
        if (bar.gradient) {
            // shaded off to clear at its end (white on a light sheet, not a glare on a dark one), with an edge in its colour
            drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(col, col.copy(alpha = 0.12f)), x0, x0 + w), Offset(x0, r[1] + pad), Size(w, h))
            drawRect(col, Offset(x0, r[1] + pad), Size(w, h), style = Stroke(1f))
        } else {
            drawRect(col, Offset(x0, r[1] + pad), Size(w, h))
        }
    }
    // text
    val occupied = HashSet<Long>(v.cells.size * 2)
    for (c in v.cells) if (c.text.isNotEmpty()) occupied.add((c.row.toLong() shl 20) or c.col.toLong())
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        for (c in v.cells) {
            if (c.text.isEmpty()) continue
            val r = cellRect(c) ?: continue
            drawCellText(nc, context, c, r, unit, occupied, cols, screenX)
        }
        // borders
        for (c in v.cells) {
            val r = cellRect(c) ?: continue
            edge(nc, c.top, r[0], r[1], r[2], r[1], density)
            edge(nc, c.bottom, r[0], r[3], r[2], r[3], density)
            edge(nc, c.left, r[0], r[1], r[0], r[3], density)
            edge(nc, c.right, r[2], r[1], r[2], r[3], density)
        }
    }
    // sparklines, in their cells' own space
    if (v.sparklines.isNotEmpty()) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (sp in v.sparklines) {
                val r = rowOf[sp.row] ?: continue
                val c = colOf[sp.col] ?: continue
                nc.save()
                nc.translate(screenX(c.start), screenY(r.start))
                nc.scale(unit, unit)
                for (pr in sp.prims) drawPrim(nc, pr)
                nc.restore()
            }
        }
    }
    // a comment's corner (red for a note, purple for a thread); a filter's drop-down button
    for (c in v.cells) {
        if (!c.comment && !c.filterButton && c.checkbox == null && !c.picture) continue
        val r = cellRect(c) ?: continue
        if (c.picture) cellPicture(c.row, c.col)?.let { img ->
            // a picture placed in the cell: fitted inside it, keeping its shape, in the middle
            val pad = 2f * density
            val w = r[2] - r[0] - 2 * pad
            val h = r[3] - r[1] - 2 * pad
            if (w > 1f && h > 1f && img.width > 0 && img.height > 0) {
                val s = minOf(w / img.width, h / img.height)
                val dw = (img.width * s).toInt().coerceAtLeast(1)
                val dh = (img.height * s).toInt().coerceAtLeast(1)
                drawImage(
                    img,
                    dstOffset = androidx.compose.ui.unit.IntOffset(((r[0] + r[2] - dw) / 2).toInt(), ((r[1] + r[3] - dh) / 2).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(dw, dh),
                )
            }
        }
        if (c.comment) {
            val t = 6f * density
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(r[2] - t, r[1])
                lineTo(r[2], r[1])
                lineTo(r[2], r[1] + t)
                close()
            }
            drawPath(path, Color(if (c.threadedComment) 0xFF8855C8 else 0xFFD13438))
        }
        c.checkbox?.let { on ->
            // a checkbox cell: a box in the middle, ticked or not
            val side = minOf(r[3] - r[1] - 4f, r[2] - r[0] - 4f, 16f * density).coerceAtLeast(4f)
            val x0 = (r[0] + r[2]) / 2 - side / 2
            val y0 = (r[1] + r[3]) / 2 - side / 2
            val radius = androidx.compose.ui.geometry.CornerRadius(side * 0.18f)
            if (on) {
                drawRoundRect(GridColors.SheetAccent, Offset(x0, y0), Size(side, side), radius)
                val tick = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x0 + side * 0.22f, y0 + side * 0.52f)
                    lineTo(x0 + side * 0.42f, y0 + side * 0.72f)
                    lineTo(x0 + side * 0.78f, y0 + side * 0.3f)
                }
                drawPath(tick, Color.White, style = Stroke(side * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            } else {
                drawRoundRect(GridColors.Cells, Offset(x0, y0), Size(side, side), radius)
                drawRoundRect(GridColors.OnHeader, Offset(x0, y0), Size(side, side), radius, style = Stroke(1.2f * density))
            }
        }
        if (c.filterButton) {
            val side = minOf(r[3] - r[1] - 2f, 16f * density)
            val x0 = r[2] - side - 1f
            val y0 = r[3] - side - 1f
            drawRect(GridColors.Cells, Offset(x0, y0), Size(side, side))
            drawRect(GridColors.HeaderLine, Offset(x0, y0), Size(side, side), style = Stroke(1f))
            val path = androidx.compose.ui.graphics.Path().apply {
                if (c.filtered) {
                    // a funnel: the column is filtered
                    moveTo(x0 + side * 0.2f, y0 + side * 0.25f)
                    lineTo(x0 + side * 0.8f, y0 + side * 0.25f)
                    lineTo(x0 + side * 0.57f, y0 + side * 0.52f)
                    lineTo(x0 + side * 0.57f, y0 + side * 0.8f)
                    lineTo(x0 + side * 0.43f, y0 + side * 0.72f)
                    lineTo(x0 + side * 0.43f, y0 + side * 0.52f)
                } else {
                    moveTo(x0 + side * 0.25f, y0 + side * 0.4f)
                    lineTo(x0 + side * 0.75f, y0 + side * 0.4f)
                    lineTo(x0 + side * 0.5f, y0 + side * 0.68f)
                }
                close()
            }
            drawPath(path, GridColors.OnHeader)
        }
    }
}

/**
 * Turned text: -90..90 degrees (positive rises to the right from the cell's bottom, negative
 * falls to the right from its top), or 255 for letters stacked one under another. It may run
 * over the cells beside it, not above or below its row.
 */
private fun drawTurnedText(nc: android.graphics.Canvas, c: CellView, p: TextPaint, left: Float, top: Float, right: Float, bottom: Float, pad: Float, across: Across) {
    val text = c.text
    val fm = p.fontMetrics
    val asc = -fm.ascent
    val desc = fm.descent
    nc.save()
    val rot = c.rotation.toInt()
    if (rot == 255) {
        nc.clipRect(left, top, right, bottom)
        val widest = text.maxOfOrNull { p.measureText(it.toString()) } ?: 0f
        val cx = when (across) {
            Across.CENTER, Across.CENTER_ACROSS -> (left + right) / 2
            Across.RIGHT -> right - pad - widest / 2
            else -> left + pad + widest / 2
        }
        var y = top + pad + asc
        for (ch in text.take(500)) {
            val one = ch.toString()
            nc.drawText(one, cx - p.measureText(one) / 2, y, p)
            y += asc + desc
        }
        nc.restore()
        return
    }
    val deg = rot.coerceIn(-90, 90).toFloat()
    val th = Math.toRadians(kotlin.math.abs(deg).toDouble())
    val sin = kotlin.math.sin(th).toFloat()
    val cos = kotlin.math.cos(th).toFloat()
    val w = p.measureText(text)
    val boxW = w * cos + (asc + desc) * sin
    val bx = when (across) {
        Across.CENTER, Across.CENTER_ACROSS -> left + (right - left - boxW) / 2
        Across.RIGHT -> right - pad - boxW
        else -> left + pad
    }
    nc.clipRect(left - boxW, top, right + boxW, bottom)
    if (deg > 0) {
        nc.translate(bx + asc * sin, bottom - pad - desc * cos)
        nc.rotate(-deg)
    } else {
        nc.translate(bx + desc * sin, top + pad + asc * cos)
        nc.rotate(-deg)
    }
    nc.drawText(text, 0f, 0f, p)
    nc.restore()
}

private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

private const val CF_RED = 0xFFD13B2F.toInt()
private const val CF_YELLOW = 0xFFF2B82E.toInt()
private const val CF_GREEN = 0xFF2E9E4F.toInt()
private const val CF_GRAY = 0xFF8A8A8A.toInt()
private const val CF_DARK = 0xFF3C3C3C.toInt()
private const val CF_GOLD = 0xFFF5B400.toInt()
private const val CF_BLUE = 0xFF2B7CD3.toInt()
private const val CF_LIGHT = 0xFFD0D0D0.toInt()

/**
 * A conditional format's icon, drawn: arrows (coloured or grey), triangles, round lights (with
 * or without a rim), signs, symbols in or out of a circle, flags, stars, signal bars, quarters
 * and boxes. Coloured sets go from red (low) through yellow to green (high).
 */
internal fun drawCfIcon(nc: android.graphics.Canvas, x: Float, y: Float, s: Float, set: String, idx: Int) {
    val n = set.takeWhile { it.isDigit() }.toIntOrNull()?.coerceIn(3, 5) ?: 3
    val i = idx.coerceIn(0, n - 1)
    val lower = set.lowercase()
    val cx = x + s / 2
    val cy = y + s / 2
    val h = s / 2
    val p = iconPaint
    val tone = when (i) {
        n - 1 -> CF_GREEN
        0 -> CF_RED
        else -> CF_YELLOW
    }
    fun fill(color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
    }
    fun line(color: Int, w: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = color
    }
    fun shape(vararg pts: Float) = android.graphics.Path().apply {
        moveTo(pts[0], pts[1])
        for (k in 2 until pts.size step 2) lineTo(pts[k], pts[k + 1])
        close()
    }
    // the symbol sets' tick (high), exclamation mark (between) and cross (low)
    fun mark(color: Int, scale: Float) {
        line(color, s * 0.13f)
        val r = h * scale
        when (i) {
            n - 1 -> nc.drawPath(android.graphics.Path().apply { moveTo(cx - r * 0.6f, cy); lineTo(cx - r * 0.15f, cy + r * 0.5f); lineTo(cx + r * 0.65f, cy - r * 0.5f) }, p)
            0 -> {
                nc.drawLine(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f, p)
                nc.drawLine(cx + r * 0.5f, cy - r * 0.5f, cx - r * 0.5f, cy + r * 0.5f, p)
            }
            else -> {
                nc.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.15f, p)
                nc.drawPoint(cx, cy + r * 0.6f, p)
            }
        }
    }
    when {
        "arrow" in lower -> {
            val deg = when (n) {
                3 -> floatArrayOf(-90f, 0f, 90f)
                4 -> floatArrayOf(-90f, -45f, 45f, 90f)
                else -> floatArrayOf(-90f, -45f, 0f, 45f, 90f)
            }[i]
            fill(if ("gray" in lower) CF_GRAY else tone)
            nc.save()
            nc.translate(cx, cy)
            nc.rotate(-deg)
            // pointing right before it turns: a head and a shaft
            nc.drawPath(shape(h, 0f, 0f, -h * 0.85f, 0f, h * 0.85f), p)
            nc.drawRect(-h * 0.9f, -h * 0.32f, 0.05f * h, h * 0.32f, p)
            nc.restore()
        }
        "triangle" in lower -> when (i) {
            n - 1 -> {
                fill(CF_GREEN)
                nc.drawPath(shape(cx, cy - h * 0.8f, cx + h * 0.85f, cy + h * 0.6f, cx - h * 0.85f, cy + h * 0.6f), p)
            }
            0 -> {
                fill(CF_RED)
                nc.drawPath(shape(cx, cy + h * 0.8f, cx - h * 0.85f, cy - h * 0.6f, cx + h * 0.85f, cy - h * 0.6f), p)
            }
            else -> {
                fill(CF_YELLOW)
                nc.drawRect(cx - h * 0.8f, cy - h * 0.22f, cx + h * 0.8f, cy + h * 0.22f, p)
            }
        }
        "signs" in lower -> when (i) {
            n - 1 -> {
                fill(CF_GREEN)
                nc.drawCircle(cx, cy, h * 0.85f, p)
            }
            0 -> {
                fill(CF_RED)
                nc.drawPath(shape(cx, cy - h * 0.9f, cx + h * 0.9f, cy, cx, cy + h * 0.9f, cx - h * 0.9f, cy), p)
            }
            else -> {
                fill(CF_YELLOW)
                nc.drawPath(shape(cx, cy - h * 0.85f, cx + h * 0.9f, cy + h * 0.75f, cx - h * 0.9f, cy + h * 0.75f), p)
            }
        }
        "symbols2" in lower -> mark(tone, 1f)
        "symbols" in lower -> {
            fill(tone)
            nc.drawCircle(cx, cy, h - 0.5f, p)
            mark(android.graphics.Color.WHITE, 0.75f)
        }
        "flag" in lower -> {
            line(CF_DARK, s * 0.09f)
            nc.drawLine(x + s * 0.22f, y + s * 0.1f, x + s * 0.22f, y + s * 0.95f, p)
            fill(tone)
            nc.drawPath(shape(x + s * 0.26f, y + s * 0.1f, x + s * 0.9f, y + s * 0.32f, x + s * 0.26f, y + s * 0.56f), p)
        }
        "star" in lower -> {
            val star = android.graphics.Path()
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) h * 0.95f else h * 0.42f
                val a = Math.toRadians(-90.0 + k * 36.0)
                val px = cx + r * kotlin.math.cos(a).toFloat()
                val py = cy + r * kotlin.math.sin(a).toFloat()
                if (k == 0) star.moveTo(px, py) else star.lineTo(px, py)
            }
            star.close()
            fill(CF_GOLD)
            when (i) {
                n - 1 -> nc.drawPath(star, p)
                0 -> {}
                else -> {
                    nc.save()
                    nc.clipRect(x, y, cx, y + s)
                    nc.drawPath(star, p)
                    nc.restore()
                }
            }
            line(CF_GOLD, s * 0.06f)
            nc.drawPath(star, p)
        }
        "trafficlights" in lower || "redtoblack" in lower -> {
            val color = when {
                "redtoblack" in lower -> intArrayOf(CF_DARK, 0xFF8C8C8C.toInt(), 0xFFF4A6A6.toInt(), CF_RED)[i.coerceAtMost(3)]
                n == 4 -> intArrayOf(CF_DARK, CF_RED, CF_YELLOW, CF_GREEN)[i]
                else -> tone
            }
            if (lower.endsWith("2")) {
                // rimmed: a dark square behind the light
                fill(CF_DARK)
                nc.drawRoundRect(x, y, x + s, y + s, s * 0.2f, s * 0.2f, p)
                fill(color)
                nc.drawCircle(cx, cy, h * 0.68f, p)
            } else {
                fill(color)
                nc.drawCircle(cx, cy, h - 0.5f, p)
            }
        }
        "rating" in lower -> {
            // signal bars: 4 Ratings light one to four, 5 Ratings none to four
            val on = if (n == 5) i else i + 1
            val bw = s / 6f
            for (k in 0 until 4) {
                val bh = s * (0.3f + 0.7f * k / 3f)
                val left = x + bw * 0.2f + k * bw * 1.4f
                fill(if (k < on) CF_BLUE else CF_LIGHT)
                nc.drawRect(left, y + s - bh, left + bw, y + s, p)
            }
        }
        "quarter" in lower -> {
            val r = h * 0.85f
            line(CF_DARK, s * 0.08f)
            nc.drawCircle(cx, cy, r, p)
            fill(CF_DARK)
            if (i == n - 1) nc.drawCircle(cx, cy, r, p) else if (i > 0) nc.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 90f * i, true, p)
        }
        "boxes" in lower -> {
            val g = s * 0.08f
            val b = (s - 3 * g) / 2
            for (k in 0 until 4) {
                val bx = x + g + (k % 2) * (b + g)
                val by = y + g + (k / 2) * (b + g)
                fill(if (k < i) CF_BLUE else CF_LIGHT)
                nc.drawRect(bx, by, bx + b, by + b, p)
            }
        }
        else -> {
            fill(tone)
            nc.drawCircle(cx, cy, h - 0.5f, p)
        }
    }
}

private fun edge(nc: android.graphics.Canvas, e: Edge?, x0: Float, y0: Float, x1: Float, y1: Float, density: Float) {
    e ?: return
    linePaint.color = e.color
    linePaint.strokeWidth = when (e.stroke) {
        BorderStroke.HAIR -> 1f
        BorderStroke.MEDIUM, BorderStroke.MEDIUM_DASHED, BorderStroke.MEDIUM_DASH_DOT, BorderStroke.MEDIUM_DASH_DOT_DOT, BorderStroke.SLANT_DASH_DOT -> 1.4f * density
        BorderStroke.THICK -> 2.2f * density
        else -> maxOf(1f, 0.7f * density)
    }
    linePaint.pathEffect = when (e.stroke) {
        BorderStroke.DASHED, BorderStroke.MEDIUM_DASHED -> DashPathEffect(floatArrayOf(4f * density, 2f * density), 0f)
        BorderStroke.DOTTED, BorderStroke.HAIR -> DashPathEffect(floatArrayOf(1f * density, 1f * density), 0f)
        BorderStroke.DASH_DOT, BorderStroke.MEDIUM_DASH_DOT, BorderStroke.SLANT_DASH_DOT -> DashPathEffect(floatArrayOf(4f * density, 1.5f * density, 1.5f * density, 1.5f * density), 0f)
        BorderStroke.DASH_DOT_DOT, BorderStroke.MEDIUM_DASH_DOT_DOT -> DashPathEffect(floatArrayOf(4f * density, 1.5f * density, 1.5f * density, 1.5f * density, 1.5f * density, 1.5f * density), 0f)
        else -> null
    }
    if (e.stroke == BorderStroke.DOUBLE) {
        val gap = 1.2f * density
        val (dx, dy) = if (y0 == y1) 0f to gap else gap to 0f
        nc.drawLine(x0 - dx, y0 - dy, x1 - dx, y1 - dy, linePaint)
        nc.drawLine(x0 + dx, y0 + dy, x1 + dx, y1 + dy, linePaint)
    } else {
        nc.drawLine(x0, y0, x1, y1, linePaint)
    }
}

/**
 * A cell's text in its rectangle [r] (left, top, right, bottom on screen). Text that doesn't wrap
 * runs on over empty neighbours (to the right when it sits left, both ways when centred, to the
 * left when it sits right); numbers stay in their cell.
 */
private fun drawCellText(
    nc: android.graphics.Canvas,
    context: android.content.Context,
    c: CellView,
    r: FloatArray,
    unit: Float,
    occupied: Set<Long>,
    cols: List<Line>,
    screenX: (Double) -> Float,
) {
    val p = textPaint
    p.typeface = CellFonts.typeface(context, c.font, c.bold, c.italic)
    // points to sheet units (96 per inch), then to pixels
    p.textSize = (c.size.coerceIn(1f, 409f) * 96f / 72f * unit).coerceAtLeast(1f)
    p.color = c.color
    p.isUnderlineText = c.underline > 0u
    p.isStrikeThruText = c.strike
    val pad = 2f * unit
    val indent = c.indent.toFloat() * 9f * unit
    val across = when (c.across) {
        Across.GENERAL -> if (c.numeric) Across.RIGHT else if (c.centred) Across.CENTER else Across.LEFT
        else -> c.across
    }
    var left = r[0]
    val right = r[2]
    val top = r[1]
    val bottom = r[3]
    // a conditional format's icon sits at the left; the text starts after it
    c.icon?.let { ic ->
        val s = 12f * unit
        drawCfIcon(nc, left + 2f * unit, (top + bottom) / 2 - s / 2, s, ic.set, ic.index.toInt())
        left += 15f * unit
    }
    // turned text (Orientation)
    if (c.rotation.toInt() != 0) {
        drawTurnedText(nc, c, p, left, top, right, bottom, pad, across)
        return
    }
    nc.save()
    if (c.wrap || c.across == Across.JUSTIFY || c.across == Across.DISTRIBUTED) {
        val width = (right - left - 2 * pad - indent).toInt().coerceAtLeast(1)
        val align = when (across) {
            Across.CENTER, Across.CENTER_ACROSS -> Layout.Alignment.ALIGN_CENTER
            Across.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val layout = StaticLayout.Builder.obtain(c.text, 0, c.text.length, p, width).setAlignment(align).setIncludePad(false).build()
        val h = layout.height.toFloat()
        val y = when (c.down) {
            Down.TOP -> top + pad
            Down.CENTER, Down.JUSTIFY, Down.DISTRIBUTED -> top + (bottom - top - h) / 2
            Down.BOTTOM -> bottom - pad - h
        }
        nc.clipRect(left, top, right, bottom)
        nc.translate(left + pad + if (across == Across.RIGHT) 0f else indent, y)
        layout.draw(nc)
        nc.restore()
        return
    }
    var text = if (c.repeat != null && c.text.isEmpty()) c.repeat!! else c.text
    var w = p.measureText(text)
    // shrink to fit: the text comes down in size until it fits its cell
    if (c.shrink && w > 0f && w + 2 * pad + indent > right - left) {
        p.textSize = (p.textSize * (right - left - 2 * pad - indent).coerceAtLeast(1f) / w).coerceAtLeast(1f)
        w = p.measureText(text)
    }
    // a number or date too wide for its cell shows as ####, as Excel shows it
    if (c.numeric && !c.shrink && c.repeat == null && w + 2 * pad + indent > right - left) {
        val hash = p.measureText("#").coerceAtLeast(0.1f)
        text = "#".repeat(((right - left - 2 * pad) / hash).toInt().coerceIn(1, 255))
        w = p.measureText(text)
    }
    val fm = p.fontMetrics
    val baseline = when (c.down) {
        Down.TOP -> top + pad - fm.ascent
        Down.CENTER, Down.JUSTIFY, Down.DISTRIBUTED -> (top + bottom) / 2 - (fm.ascent + fm.descent) / 2
        Down.BOTTOM -> bottom - pad - fm.descent
    }
    // how far the text may run: over empty neighbours, for text that isn't a number
    var clipLeft = left
    var clipRight = right
    val key = c.row.toLong() shl 20
    if (!c.numeric && w + 2 * pad + indent > right - left) {
        val idx = cols.indexOfFirst { it.index == c.col }
        if (across == Across.LEFT || across == Across.CENTER || across == Across.FILL) {
            var i = idx + 1
            while (i in cols.indices && clipRight - left < w + 2 * pad + indent && !occupied.contains(key or cols[i].index.toLong())) {
                clipRight = screenX(cols[i].start + cols[i].size)
                i++
            }
        }
        if (across == Across.RIGHT || across == Across.CENTER) {
            var i = idx - 1
            while (i >= 0 && right - clipLeft < w + 2 * pad + indent && !occupied.contains(key or cols[i].index.toLong())) {
                clipLeft = screenX(cols[i].start)
                i--
            }
        }
    }
    val x = when (across) {
        Across.RIGHT -> right - pad - indent - w
        Across.CENTER, Across.CENTER_ACROSS -> (left + right) / 2 - w / 2
        else -> left + pad + indent
    }
    nc.clipRect(clipLeft, top, clipRight, bottom)
    if (across == Across.FILL && w > 0f) {
        // the text repeated across the cell
        var xx = left + pad
        while (xx < right) {
            nc.drawText(text, xx, baseline, p)
            xx += w
        }
    } else {
        nc.drawText(text, x, baseline, p)
    }
    nc.restore()
}

private val primPaint = Paint(Paint.ANTI_ALIAS_FLAG)

/**
 * The charts, pictures and shapes over the cells. A chart is drawn from its laid-out steps,
 * scaled from its own units to the screen, on its own white area.
 */
private fun DrawScope.drawObjects(
    vm: AppViewModel,
    unit: Float,
    screenX: (Double) -> Float,
    screenY: (Double) -> Float,
    density: Float,
    icons: Map<String, androidx.compose.ui.graphics.vector.VectorPainter>,
) {
    val sel = vm.selectedObject
    for (o in vm.objects) {
        val dragged = sel != null && o.kind == sel.kind && o.id == sel.id
        val drag = if (dragged) vm.objectDrag else null
        val left = screenX(o.x) + (drag?.first?.x ?: 0f)
        val top = screenY(o.y) + (drag?.first?.y ?: 0f)
        val w = (o.width * unit).toFloat() + (drag?.second?.x ?: 0f)
        val h = (o.height * unit).toFloat() + (drag?.second?.y ?: 0f)
        if (left > size.width || top > size.height || left + w < 0f || top + h < 0f) continue
        // a turned object: rotated about its centre, then mirrored
        val centre = Offset(left + w / 2, top + h / 2)
        val turn = o.rotation + (if (dragged) vm.objectTurn ?: 0f else 0f)
        withTransform({
            if (turn != 0f) rotate(turn, centre)
            if (o.flipH || o.flipV) scale(if (o.flipH) -1f else 1f, if (o.flipV) -1f else 1f, centre)
        }) {
        when (o.kind) {
            "chart" -> {
                drawRect(Color.White, Offset(left, top), Size(w, h))
                drawRect(Color(0xFFBFBFBF), Offset(left, top), Size(w, h), style = Stroke(1f))
                val prims = vm.chartDrawings[o.index].orEmpty()
                drawIntoCanvas { canvas ->
                    val nc = canvas.nativeCanvas
                    nc.save()
                    nc.clipRect(left, top, left + w, top + h)
                    nc.translate(left, top)
                    nc.scale(unit, unit)
                    for (pr in prims) drawPrim(nc, pr)
                    nc.restore()
                }
            }
            "image" -> {
                val img = vm.pictures[o.index]
                // the part shown of a cropped picture
                val c = (if (dragged) vm.cropPreview else null) ?: o.crop.takeIf { it.size == 4 } ?: listOf(0f, 0f, 1f, 1f)
                drawPictureObject(o, left, top, w, h, 96f / 72f * unit, turn) { drawPicturePart(img, c, left, top, w, h) }
            }
            else -> if (o.shape == "icon") {
                icons[o.text.lowercase()]?.let { painter ->
                    translate(left, top) {
                        with(painter) { draw(Size(w, h), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(o.fill?.let { Color(it) } ?: Color.Black)) }
                    }
                }
            } else {
                drawShape(o, left, top, w, h, density, withText = vm.editingShapeText != o.id.toLong())
            }
        }
        }
        if (dragged) withTransform({ if (turn != 0f) rotate(turn, centre) }) {
            // as the phone app: a thin grey frame, eight white handles ringed in grey, and for a
            // shape or picture a rotate handle above and a move handle below on stems, all
            // turned with the object; a line has only a handle at each end
            val ends = o.takeIf { it.isLine }?.lineEnds
            if (ends == null) drawRect(HANDLE_LINE, Offset(left, top), Size(w, h), style = Stroke(1f * density))
            val r = 5.5f * density
            val line = Stroke(1.5f * density)
            if (o.kind != "chart" && ends == null && vm.editingShapeText == null && !vm.cropping) {
                val cx = left + w / 2
                val rot = Offset(cx, top - ROTATE_HANDLE_DP * density)
                val mov = Offset(cx, top + h + MOVE_HANDLE_DP * density)
                drawLine(HANDLE_LINE, Offset(cx, top), Offset(cx, rot.y + 10f * density), 1.5f * density)
                drawLine(HANDLE_LINE, Offset(cx, top + h), Offset(cx, mov.y - 12f * density), 1.5f * density)
                rotateGlyph(rot, 9f * density, density)
                moveGlyph(mov, 12f * density, density)
            }
            for ((fx, fy) in ends ?: listOf(0f, 0.5f, 1f).flatMap { fx -> listOf(0f, 0.5f, 1f).map { fy -> fx to fy } }.filter { it != 0.5f to 0.5f }) {
                val pt = Offset(left + w * fx, top + h * fy)
                drawCircle(Color.White, r, pt)
                drawCircle(HANDLE_LINE, r, pt, style = line)
            }
            // crop mode: black marks inside the corners and along the sides, as the phone app's
            if (vm.cropping && o.kind == "image") {
                val (len, th) = 12f * density to 4f * density
                for (fx in listOf(0f, 0.5f, 1f)) for (fy in listOf(0f, 0.5f, 1f)) {
                    if (fx == 0.5f && fy == 0.5f) continue
                    val x = left + w * fx
                    val y = top + h * fy
                    val ix = if (fx == 0f) 1f else -1f
                    val iy = if (fy == 0f) 1f else -1f
                    if (fx != 0.5f && fy != 0.5f) {
                        drawRect(Color.Black, Offset(minOf(x, x + ix * len), minOf(y, y + iy * th)), Size(len, th))
                        drawRect(Color.Black, Offset(minOf(x, x + ix * th), minOf(y, y + iy * len)), Size(th, len))
                    } else if (fx == 0.5f) {
                        drawRect(Color.Black, Offset(x - len, minOf(y, y + iy * th)), Size(2 * len, th))
                    } else {
                        drawRect(Color.Black, Offset(minOf(x, x + ix * th), y - len), Size(th, 2 * len))
                    }
                }
            }
        }
    }
}

/** The rotate handle: a circle of radius [r] about [c], open on the right, an arrowhead turning clockwise at its end. */
private fun DrawScope.rotateGlyph(c: Offset, r: Float, density: Float) {
    val stroke = Stroke(2f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawCircle(Color.White, r + 3f * density, c)
    val start = 30f
    val sweep = 290f
    drawArc(HANDLE_LINE, startAngle = start, sweepAngle = sweep, useCenter = false, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = stroke)
    // where the arc ends: its clockwise tangent and outward normal frame the head
    val e = Math.toRadians((start + sweep).toDouble())
    val (cos, sin) = kotlin.math.cos(e).toFloat() to kotlin.math.sin(e).toFloat()
    val end = Offset(c.x + r * cos, c.y + r * sin)
    val along = Offset(-sin, cos)
    val out = Offset(cos, sin)
    val h = 5f * density
    val head = androidx.compose.ui.graphics.Path().apply {
        val tip = end + along * h
        moveTo(tip.x, tip.y)
        lineTo(end.x + out.x * h * 0.9f, end.y + out.y * h * 0.9f)
        lineTo(end.x - out.x * h * 0.9f, end.y - out.y * h * 0.9f)
        close()
    }
    drawPath(head, HANDLE_LINE)
}

/** The move handle: four arrows out from [c], [r] long, hollow heads, on a white ground. */
private fun DrawScope.moveGlyph(c: Offset, r: Float, density: Float) {
    drawCircle(Color.White, r + 2f * density, c)
    val stroke = Stroke(1.5f * density, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    val head = 4.5f * density
    for (dir in listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))) {
        val tip = c + dir * r
        val base = c + dir * (r - head)
        val side = Offset(-dir.y, dir.x) * head
        drawLine(HANDLE_LINE, c + dir * (2f * density), base, 1.5f * density)
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(base.x + side.x, base.y + side.y)
            lineTo(base.x - side.x, base.y - side.y)
            close()
        }
        drawPath(p, HANDLE_LINE, style = stroke)
    }
}

/**
 * A shape's pieces from the bridge, scaled by ([sx], [sy]) from ([left], [top]): each filled
 * even-odd in [fill] (or a shade of it, or [line] for arrowheads) and outlined in [line] (an open
 * line in [openInk] when given).
 */
internal fun DrawScope.drawPieces(
    pieces: List<io.github.artcraftandroid.gridcraft.core.ShapePiece>,
    left: Float,
    top: Float,
    sx: Float,
    sy: Float,
    fill: Color?,
    line: Color?,
    width: Float,
    openInk: Color? = null,
    gradient: Brush? = null,
) {
    for (p in pieces) {
        val (path, closed) = piecePath(p, left, top, sx, sy)
        val paint = when (p.paint) {
            "none" -> null
            "ink" -> openInk ?: line ?: fill
            else -> fill?.let { shade(it, p.paint) }
        }
        if (gradient != null && p.paint == "fill" && closed) drawPath(path, gradient) else if (paint != null && closed) drawPath(path, paint)
        val ink = if (p.paint == "none") openInk ?: line else line
        if (p.stroke && ink != null) drawPath(path, ink, style = Stroke(width, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * A picture as the sheet shows it, in the box ([left], [top], [w], [h]): cut to its outline (a
 * gallery shape's filled pieces after Change Shape: none for a line, which shows nothing of it),
 * with its reflection and a shadow cast outside it under it, a shadow inside it, soft edges and
 * its frame. [ptPx]: a point on screen; [turn]: how far it is drawn turned (a shadow that
 * doesn't turn with it undoes it); [picture] draws the picture itself into the box.
 */
internal fun DrawScope.drawPictureObject(o: io.github.artcraftandroid.gridcraft.core.SheetObject, left: Float, top: Float, w: Float, h: Float, ptPx: Float, turn: Float, picture: DrawScope.() -> Unit) {
    val centre = Offset(left + w / 2, top + h / 2)
    val outline = androidx.compose.ui.graphics.Path().apply {
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        when {
            o.shape == "preset" -> {
                val sx = w / o.width.toFloat().coerceAtLeast(1e-3f)
                val sy = h / o.height.toFloat().coerceAtLeast(1e-3f)
                for (p in o.pieces) addPath(piecePath(p, left, top, sx, sy).first)
            }
            o.shape == "roundedRectangle" -> addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, androidx.compose.ui.geometry.CornerRadius(minOf(w, h) / 6f)))
            o.shape == "ellipse" -> addOval(androidx.compose.ui.geometry.Rect(left, top, left + w, top + h))
            else -> addRect(androidx.compose.ui.geometry.Rect(left, top, left + w, top + h))
        }
    }
    o.reflection?.let { r -> drawReflection(r, outline, left, top, w, h, ptPx, picture) }
    o.shadow?.takeIf { !it.inner }?.let { sh -> drawOuterShadow(sh, outline, left, top, w, h, shadowMove(sh, ptPx, turn, o.flipH, o.flipV), ptPx) }
    // a style's 3-D camera may tilt the picture: drawn flat, turned about its centre
    withTransform({ if (o.viewTurn != 0f) rotate(o.viewTurn, centre) }) {
        val inside: DrawScope.() -> Unit = {
            picture()
            o.shadow?.takeIf { it.inner }?.let { sh -> drawInnerShadow(sh, outline, left, top, w, h, shadowMove(sh, ptPx, turn, o.flipH, o.flipV), ptPx) }
        }
        if (o.softEdge > 0f) drawSoftEdged(outline, o.softEdge * ptPx, left, top, w, h, inside) else clipPath(outline) { inside() }
        o.line?.let { frame -> drawFrame(outline, Color(frame), (o.lineWidth * ptPx).coerceAtLeast(1f), o.lineParts, o.lineMiter, left, top, w, h) }
    }
}

/**
 * A picture's frame along its [outline]: one line [width] wide centred on the edge, or a frame's
 * several lines ([parts]: start and end fraction pairs across the width, from the outside in),
 * each drawn on the outline grown or shrunk to its place; corners mitred or rounded.
 */
private fun DrawScope.drawFrame(outline: androidx.compose.ui.graphics.Path, color: Color, width: Float, parts: List<Float>, miter: Boolean, left: Float, top: Float, w: Float, h: Float) {
    val join = if (miter) androidx.compose.ui.graphics.StrokeJoin.Miter else androidx.compose.ui.graphics.StrokeJoin.Round
    if (parts.size < 4) {
        drawPath(outline, color, style = Stroke(width, join = join))
        return
    }
    val centre = Offset(left + w / 2, top + h / 2)
    for (i in 0 until parts.size / 2) {
        val (a, b) = parts[2 * i] to parts[2 * i + 1]
        // how far this line's middle lies outside the frame's middle line
        val out = (0.5f - (a + b) / 2) * width
        withTransform({ scale((w + 2 * out) / w.coerceAtLeast(1f), (h + 2 * out) / h.coerceAtLeast(1f), centre) }) {
            drawPath(outline, color, style = Stroke((b - a) * width / ((w + 2 * out) / w.coerceAtLeast(1f)).coerceAtLeast(0.1f), join = join))
        }
    }
}

/**
 * A picture's soft edges: what [content] draws, fading out over [radius] inside its [outline]
 * (an inset, blurred copy of the outline keeps it).
 */
private fun DrawScope.drawSoftEdged(outline: androidx.compose.ui.graphics.Path, radius: Float, left: Float, top: Float, w: Float, h: Float, content: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(left - 2f, top - 2f, left + w + 2f, top + h + 2f), androidx.compose.ui.graphics.Paint())
        clipPath(outline) { content() }
        val inset = (radius / 2).coerceAtMost(minOf(w, h) / 2 - 1f).coerceAtLeast(0f)
        val m = android.graphics.Matrix().apply {
            setScale((w - 2 * inset) / w.coerceAtLeast(1f), (h - 2 * inset) / h.coerceAtLeast(1f), left + w / 2, top + h / 2)
        }
        val mask = android.graphics.Path(outline.asAndroidPath()).apply { transform(m) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
            if (inset > 0f) maskFilter = android.graphics.BlurMaskFilter(inset, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawPath(mask, paint)
        canvas.restore()
    }
}

/** The part [c] (left, top, width, height fractions) of [img] shown in the box; grey while it loads. */
private fun DrawScope.drawPicturePart(img: androidx.compose.ui.graphics.ImageBitmap?, c: List<Float>, left: Float, top: Float, w: Float, h: Float) {
    if (img == null) {
        drawRect(Color(0xFFE0E0E0), Offset(left, top), Size(w, h))
        return
    }
    val sx = (c[0] * img.width).toInt().coerceIn(0, img.width - 1)
    val sy = (c[1] * img.height).toInt().coerceIn(0, img.height - 1)
    val sw = (c[2] * img.width).toInt().coerceIn(1, img.width - sx)
    val sh = (c[3] * img.height).toInt().coerceIn(1, img.height - sy)
    drawImage(img, srcOffset = androidx.compose.ui.unit.IntOffset(sx, sy), srcSize = androidx.compose.ui.unit.IntSize(sw, sh),
        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()), dstSize = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)))
}

/**
 * How far a shadow moves on screen, in the picture's own frame (it is drawn turned [turn]
 * degrees and mirrored): one that doesn't turn with the picture keeps the sheet's direction.
 */
private fun shadowMove(sh: io.github.artcraftandroid.gridcraft.core.ObjectShadow, ptPx: Float, turn: Float, flipH: Boolean, flipV: Boolean): Offset {
    val v = Offset(sh.dx * ptPx, sh.dy * ptPx)
    if (sh.turns) return v
    val a = Math.toRadians(-turn.toDouble())
    val (ca, sa) = kotlin.math.cos(a).toFloat() to kotlin.math.sin(a).toFloat()
    val r = Offset(v.x * ca - v.y * sa, v.x * sa + v.y * ca)
    return Offset(if (flipH) -r.x else r.x, if (flipV) -r.y else r.y)
}

/** A paint in a shadow's colour, blurred as it is. */
private fun shadowPaint(sh: io.github.artcraftandroid.gridcraft.core.ObjectShadow, ptPx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = sh.color
    if (sh.blur > 0f) maskFilter = android.graphics.BlurMaskFilter(sh.blur * ptPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
}

/**
 * A shadow cast outside a picture: its [outline] scaled and leaned about the shadow's align
 * point (a perspective shadow), moved [move] and blurred.
 */
internal fun DrawScope.drawOuterShadow(sh: io.github.artcraftandroid.gridcraft.core.ObjectShadow, outline: androidx.compose.ui.graphics.Path, left: Float, top: Float, w: Float, h: Float, move: Offset, ptPx: Float) {
    val ax = left + w * sh.alignX
    val ay = top + h * sh.alignY
    val m = android.graphics.Matrix().apply {
        setTranslate(-ax, -ay)
        postScale(sh.scaleX / 100f, sh.scaleY / 100f)
        postSkew(kotlin.math.tan(Math.toRadians(sh.skewX.toDouble())).toFloat(), kotlin.math.tan(Math.toRadians(sh.skewY.toDouble())).toFloat())
        postTranslate(ax + move.x, ay + move.y)
    }
    val path = android.graphics.Path(outline.asAndroidPath()).apply { transform(m) }
    drawIntoCanvas { it.nativeCanvas.drawPath(path, shadowPaint(sh, ptPx)) }
}

/**
 * A shadow inside a picture (the caller clips to its [outline]): the outside, moved against
 * [move] and blurred, so the edges the shadow's direction points to darken (as the phone app
 * draws "Inside: Bottom Right" along the bottom and right).
 */
internal fun DrawScope.drawInnerShadow(sh: io.github.artcraftandroid.gridcraft.core.ObjectShadow, outline: androidx.compose.ui.graphics.Path, left: Float, top: Float, w: Float, h: Float, move: Offset, ptPx: Float) {
    val pad = sh.blur * ptPx * 2f + kotlin.math.abs(move.x) + kotlin.math.abs(move.y) + 4f
    val outside = android.graphics.Path().apply {
        fillType = android.graphics.Path.FillType.EVEN_ODD
        addRect(left - pad, top - pad, left + w + pad, top + h + pad, android.graphics.Path.Direction.CW)
        addPath(outline.asAndroidPath())
        offset(-move.x, -move.y)
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(outside, shadowPaint(sh, ptPx)) }
}

/**
 * A picture's reflection: [picture] drawn turned over below the box, [r]'s distance down, cut to
 * its [outline] and fading from its start to its end opacity over its share of the height.
 */
internal fun DrawScope.drawReflection(r: io.github.artcraftandroid.gridcraft.core.ObjectReflection, outline: androidx.compose.ui.graphics.Path, left: Float, top: Float, w: Float, h: Float, ptPx: Float, picture: DrawScope.() -> Unit) {
    val gap = r.distance * ptPx
    val reach = h * r.endPos.coerceIn(0f, 1f)
    if (reach < 1f) return
    val y0 = top + h + gap
    drawIntoCanvas { canvas ->
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(left - 2f, y0, left + w + 2f, y0 + reach), androidx.compose.ui.graphics.Paint())
        withTransform({ scale(1f, -1f, pivot = Offset(left, top + h + gap / 2f)) }) {
            clipPath(outline) { picture() }
        }
        // past the layer's edges too, so no row at its rim is left unfaded
        drawRect(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = r.start.coerceIn(0f, 1f)), 1f to Color.Black.copy(alpha = r.end.coerceIn(0f, 1f)), startY = y0, endY = y0 + reach),
            Offset(left - 4f, y0 - 2f),
            Size(w + 8f, reach + 4f),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
        canvas.restore()
    }
}

/** A piece's path, scaled by ([sx], [sy]) from ([left], [top]), filled even-odd; and whether it closes. */
private fun piecePath(p: io.github.artcraftandroid.gridcraft.core.ShapePiece, left: Float, top: Float, sx: Float, sy: Float): Pair<androidx.compose.ui.graphics.Path, Boolean> {
    val c = p.cmds
    val at = { i: Int -> c.getOrElse(i) { 0f } }
    val x = { i: Int -> left + at(i) * sx }
    val y = { i: Int -> top + at(i) * sy }
    val path = androidx.compose.ui.graphics.Path().apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd }
    var closed = false
    var i = 0
    while (i < c.size) {
        when (c[i].toInt()) {
            0 -> {
                path.moveTo(x(i + 1), y(i + 2))
                i += 3
            }
            1 -> {
                path.lineTo(x(i + 1), y(i + 2))
                i += 3
            }
            2 -> {
                path.cubicTo(x(i + 1), y(i + 2), x(i + 3), y(i + 4), x(i + 5), y(i + 6))
                i += 7
            }
            else -> {
                path.close()
                closed = true
                i += 1
            }
        }
    }
    return path to closed
}

/**
 * A shape's gradient fill over its box on screen: the stops along a line through the box's
 * centre at the gradient's angle (clockwise from left-to-right), as long as the box is across it.
 */
private fun gradientBrush(o: io.github.artcraftandroid.gridcraft.core.SheetObject, left: Float, top: Float, w: Float, h: Float): Brush? {
    if (o.gradientColors.size < 2 || o.gradientStops.size != o.gradientColors.size) return null
    val a = Math.toRadians(o.gradientAngle.toDouble())
    val (dx, dy) = kotlin.math.cos(a).toFloat() to kotlin.math.sin(a).toFloat()
    val half = (kotlin.math.abs(w * dx) + kotlin.math.abs(h * dy)) / 2
    val c = Offset(left + w / 2, top + h / 2)
    val stops = o.gradientStops.zip(o.gradientColors).map { (pos, argb) -> pos.coerceIn(0f, 1f) to Color(argb) }.toTypedArray()
    return Brush.linearGradient(*stops, start = c - Offset(dx * half, dy * half), end = c + Offset(dx * half, dy * half))
}

/** [c] darkened or lightened as a shape's shaded faces are. */
private fun shade(c: Color, paint: String): Color {
    fun dark(k: Float) = Color(c.red * k, c.green * k, c.blue * k, c.alpha)
    fun light(k: Float) = Color(c.red + (1 - c.red) * k, c.green + (1 - c.green) * k, c.blue + (1 - c.blue) * k, c.alpha)
    return when (paint) {
        "darken" -> dark(0.6f)
        "darkenLess" -> dark(0.8f)
        "lighten" -> light(0.4f)
        "lightenLess" -> light(0.2f)
        else -> c
    }
}

private fun DrawScope.drawShape(o: io.github.artcraftandroid.gridcraft.core.SheetObject, left: Float, top: Float, w: Float, h: Float, density: Float, withText: Boolean = true) {
    val fill = o.fill?.let { Color(it) }
    // no outline when the shape has none (ink always has a colour)
    val line = o.line?.let { Color(it) }
    val stroke = Stroke(1.2f * density)
    val gradient = gradientBrush(o, left, top, w, h)
    // the bridge's outline, when it gives one (every shape but text boxes, icons and ink)
    if (o.pieces.isNotEmpty()) {
        drawPieces(o.pieces, left, top, w / o.width.toFloat().coerceAtLeast(1e-3f), h / o.height.toFloat().coerceAtLeast(1e-3f), fill, line, 1.2f * density, gradient = gradient)
    } else when (o.shape) {
        "ellipse" -> {
            fill?.let { drawOval(it, Offset(left, top), Size(w, h)) }
            line?.let { drawOval(it, Offset(left, top), Size(w, h), style = stroke) }
        }
        "triangle" -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(left + w / 2, top)
                lineTo(left + w, top + h)
                lineTo(left, top + h)
                close()
            }
            fill?.let { drawPath(path, it) }
            line?.let { drawPath(path, it, style = stroke) }
        }
        "line", "arrow" -> line?.let { drawLine(it, Offset(left, top), Offset(left + w, top + h), 1.5f * density) }
        "ink" -> {
            val pts = o.text.split(';').mapNotNull { pt -> pt.split(',').takeIf { it.size == 2 }?.let { (a, b) -> a.toFloatOrNull()?.let { x -> b.toFloatOrNull()?.let { y -> x to y } } } }
            val scale = if (o.width > 0) w / o.width.toFloat() else 1f
            // the stroke's width rides in its fill's red byte; past 8 it is a highlighter's
            val inkWidth = ((o.fill ?: 0) shr 16 and 0xFF).coerceAtLeast(1).toFloat()
            val pen = line ?: Color.Black
            val ink = if (inkWidth > 8f) pen.copy(alpha = 0.45f) else pen
            // one path, so a see-through stroke doesn't darken where its pieces meet
            if (pts.size >= 2) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left + pts[0].first * scale, top + pts[0].second * scale)
                    for (i in 1 until pts.size) lineTo(left + pts[i].first * scale, top + pts[i].second * scale)
                }
                drawPath(path, ink, style = Stroke(inkWidth * scale, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
        "roundedRectangle" -> {
            val r = androidx.compose.ui.geometry.CornerRadius(minOf(w, h) * 0.15f)
            fill?.let { drawRoundRect(it, Offset(left, top), Size(w, h), r) }
            line?.let { drawRoundRect(it, Offset(left, top), Size(w, h), r, style = stroke) }
        }
        else -> {
            if (gradient != null) drawRect(gradient, Offset(left, top), Size(w, h)) else fill?.let { drawRect(it, Offset(left, top), Size(w, h)) }
            line?.let { drawRect(it, Offset(left, top), Size(w, h), style = stroke) }
        }
    }
    if (withText && o.shape != "ink" && o.shape != "icon" && o.text.isNotBlank()) {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val p = textPaint
            // sheet pixels to the screen's; 11 pt text, inset a tenth of an inch at the sides and
            // a twentieth at the top and bottom, where the shape's alignment puts it
            val k = if (o.width > 0) w / o.width.toFloat() else density
            val style = (if (o.textBold) android.graphics.Typeface.BOLD else 0) or (if (o.textItalic) android.graphics.Typeface.ITALIC else 0)
            p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, style)
            p.textSize = (if (o.textSize > 0f) o.textSize else 11f) * 96f / 72f * k
            p.color = o.textColor ?: android.graphics.Color.BLACK
            p.isUnderlineText = o.textUnderline
            p.isStrikeThruText = false
            val (inX, inY) = 9.6f * k to 4.8f * k
            val width = (w - 2 * inX).toInt().coerceAtLeast(1)
            val align = when (o.textAlign) {
                "center" -> Layout.Alignment.ALIGN_CENTER
                "right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val layout = StaticLayout.Builder.obtain(o.text, 0, o.text.length, p, width).setAlignment(align).build()
            val y = when (o.textAnchor) {
                "middle" -> top + (h - layout.height) / 2
                "bottom" -> top + h - inY - layout.height
                else -> top + inY
            }
            nc.save()
            nc.clipRect(left, top, left + w, top + h)
            nc.translate(left + inX, y)
            layout.draw(nc)
            nc.restore()
        }
    }
}

/**
 * A shape's text typed in place over the shape ([left], [top], [w] x [h] on screen, [unit] screen
 * pixels to a sheet one), in its own size, weight, colour and place, inside a dashed frame, as the
 * phone app edits it. The text is kept when the editor closes: a tap elsewhere, or Back.
 */
@Composable
private fun ShapeTextEditor(vm: AppViewModel, o: io.github.artcraftandroid.gridcraft.core.SheetObject, left: Float, top: Float, w: Float, h: Float, unit: Float) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var value by remember(o.id) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(o.text, androidx.compose.ui.text.TextRange(o.text.length))) }
    val latest by androidx.compose.runtime.rememberUpdatedState(value.text)
    androidx.compose.runtime.DisposableEffect(o.id) { onDispose { vm.endShapeText(o, latest) } }
    androidx.activity.compose.BackHandler { vm.stopShapeText() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(o.id) {
        focus.requestFocus()
        if (!vm.shapeTextKeyboard) keyboard?.hide()
    }
    val sizePx = (if (o.textSize > 0f) o.textSize else 11f) * 96f / 72f * unit
    val style = androidx.compose.ui.text.TextStyle(
        color = o.textColor?.let { Color(it) } ?: Color.Black,
        fontSize = with(density) { sizePx.toSp() },
        fontWeight = if (o.textBold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
        fontStyle = if (o.textItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
        textDecoration = if (o.textUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
        textAlign = when (o.textAlign) {
            "center" -> androidx.compose.ui.text.style.TextAlign.Center
            "right" -> androidx.compose.ui.text.style.TextAlign.End
            else -> androidx.compose.ui.text.style.TextAlign.Start
        },
    )
    val (inX, inY) = with(density) { (9.6f * unit).toDp() to (4.8f * unit).toDp() }
    Box(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .drawBehind {
                drawRect(GridColors.SheetAccent, style = Stroke(1.5f * density.density, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f * density.density, 4f * density.density))))
            }
            .padding(horizontal = inX, vertical = inY),
        contentAlignment = when (o.textAnchor) {
            "middle" -> Alignment.CenterStart
            "bottom" -> Alignment.BottomStart
            else -> Alignment.TopStart
        },
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value,
            { value = it.copy(text = it.text.take(32767)) },
            textStyle = style,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(GridColors.SheetAccent),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** An icon shape's drawing, by the name the workbook keeps (`chart`, `lock`, `pie chart`…). */
private fun sheetIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector? = when (name) {
    "check" -> LucideIcons.Check
    "close" -> LucideIcons.X
    "plus" -> LucideIcons.Plus
    "minus" -> LucideIcons.Minus
    "chart" -> LucideIcons.ChartColumn
    "line chart" -> LucideIcons.ChartLine
    "pie chart" -> LucideIcons.ChartPie
    "scatter" -> LucideIcons.ChartScatter
    "table" -> LucideIcons.Table
    "sum" -> LucideIcons.Sigma
    "filter" -> LucideIcons.Funnel
    "lock" -> LucideIcons.Lock
    "comment" -> LucideIcons.MessageSquare
    "note" -> LucideIcons.StickyNote
    "picture" -> LucideIcons.Image
    "book" -> LucideIcons.BookOpenText
    "folder" -> LucideIcons.FolderOpen
    "search" -> LucideIcons.Search
    "calculator" -> LucideIcons.Calculator
    "link" -> LucideIcons.Link
    "pen" -> LucideIcons.PenTool
    "share" -> LucideIcons.Share2
    "settings" -> LucideIcons.Settings
    "home" -> LucideIcons.House
    "save" -> LucideIcons.Save
    "theme" -> LucideIcons.Palette
    "shapes" -> LucideIcons.Shapes
    "text" -> LucideIcons.Type
    "trace" -> LucideIcons.Workflow
    "validation" -> LucideIcons.ListChecks
    else -> null
}

/** One chart step on a canvas already in the chart's own units. */
internal fun drawPrim(nc: android.graphics.Canvas, pr: io.github.artcraftandroid.gridcraft.core.ChartPrim) {
    val p = primPaint
    p.pathEffect = null
    fun fill(color: Int) {
        p.style = Paint.Style.FILL
        p.color = color
    }
    fun stroke(color: Int, width: Float) {
        p.style = Paint.Style.STROKE
        p.color = color
        p.strokeWidth = width.coerceAtLeast(0.5f)
    }
    fun path(points: List<Float>, close: Boolean): android.graphics.Path {
        val path = android.graphics.Path()
        var i = 0
        while (i + 1 < points.size) {
            if (i == 0) path.moveTo(points[0], points[1]) else path.lineTo(points[i], points[i + 1])
            i += 2
        }
        if (close) path.close()
        return path
    }
    when (pr) {
        is io.github.artcraftandroid.gridcraft.core.ChartPrim.Rect -> {
            val rect = android.graphics.RectF(pr.x, pr.y, pr.x + pr.w, pr.y + pr.h)
            pr.fill?.let {
                fill(it)
                if (pr.radius > 0f) nc.drawRoundRect(rect, pr.radius, pr.radius, p) else nc.drawRect(rect, p)
            }
            pr.stroke?.let {
                stroke(it, pr.strokeWidth)
                if (pr.radius > 0f) nc.drawRoundRect(rect, pr.radius, pr.radius, p) else nc.drawRect(rect, p)
            }
        }
        is io.github.artcraftandroid.gridcraft.core.ChartPrim.Line -> {
            stroke(pr.color, pr.width)
            if (pr.dash) p.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            nc.drawPath(path(pr.points, false), p)
        }
        is io.github.artcraftandroid.gridcraft.core.ChartPrim.Polygon -> {
            val path = path(pr.points, true)
            fill(pr.fill)
            nc.drawPath(path, p)
            pr.stroke?.let {
                stroke(it, pr.strokeWidth)
                nc.drawPath(path, p)
            }
        }
        is io.github.artcraftandroid.gridcraft.core.ChartPrim.Wedge -> {
            // angles from 12 o'clock clockwise, in radians; Android's from 3 o'clock, in degrees
            val start = Math.toDegrees(pr.a0.toDouble()).toFloat() - 90f
            val sweep = Math.toDegrees((pr.a1 - pr.a0).toDouble()).toFloat()
            val outer = android.graphics.RectF(pr.cx - pr.rOuter, pr.cy - pr.rOuter, pr.cx + pr.rOuter, pr.cy + pr.rOuter)
            val path = android.graphics.Path()
            if (pr.rInner > 0f) {
                val inner = android.graphics.RectF(pr.cx - pr.rInner, pr.cy - pr.rInner, pr.cx + pr.rInner, pr.cy + pr.rInner)
                path.arcTo(outer, start, sweep)
                path.arcTo(inner, start + sweep, -sweep)
            } else {
                path.moveTo(pr.cx, pr.cy)
                path.arcTo(outer, start, sweep)
            }
            path.close()
            fill(pr.fill)
            nc.drawPath(path, p)
            pr.stroke?.let {
                stroke(it, pr.strokeWidth)
                nc.drawPath(path, p)
            }
        }
        is io.github.artcraftandroid.gridcraft.core.ChartPrim.Circle -> {
            pr.fill?.let {
                fill(it)
                nc.drawCircle(pr.cx, pr.cy, pr.r, p)
            }
            pr.stroke?.let {
                stroke(it, pr.strokeWidth)
                nc.drawCircle(pr.cx, pr.cy, pr.r, p)
            }
        }
        is io.github.artcraftandroid.gridcraft.core.ChartPrim.Text -> {
            val t = textPaint
            t.typeface = if (pr.bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            t.textSize = pr.size.coerceIn(1f, 200f)
            t.color = pr.color
            t.isUnderlineText = false
            t.isStrikeThruText = false
            val w = t.measureText(pr.text)
            val fm = t.fontMetrics
            val dx = when (pr.across) {
                io.github.artcraftandroid.gridcraft.core.TextAnchor.START -> 0f
                io.github.artcraftandroid.gridcraft.core.TextAnchor.MIDDLE -> -w / 2
                io.github.artcraftandroid.gridcraft.core.TextAnchor.END -> -w
            }
            val dy = when (pr.down) {
                io.github.artcraftandroid.gridcraft.core.TextAnchor.START -> -fm.ascent
                io.github.artcraftandroid.gridcraft.core.TextAnchor.MIDDLE -> -(fm.ascent + fm.descent) / 2
                io.github.artcraftandroid.gridcraft.core.TextAnchor.END -> -fm.descent
            }
            nc.save()
            nc.translate(pr.x, pr.y)
            if (pr.rotation != 0f) nc.rotate(Math.toDegrees(pr.rotation.toDouble()).toFloat())
            nc.drawText(pr.text, dx, dy, t)
            nc.restore()
        }
    }
}

private fun DrawScope.drawSelection(vm: AppViewModel, v: Viewport, screenX: (Double) -> Float, screenY: (Double) -> Float, density: Float) {
    val sel = vm.selection ?: return
    val rows = v.rows
    val cols = v.cols
    if (rows.isEmpty() || cols.isEmpty()) return
    fun rowStart(i: UInt): Double = rows.firstOrNull { it.index >= i }?.start ?: if (i < rows.first().index) rows.first().start else rows.last().start + rows.last().size
    fun rowEnd(i: UInt): Double = rows.lastOrNull { it.index <= i }?.let { it.start + it.size } ?: rows.first().start
    fun colStart(i: UInt): Double = cols.firstOrNull { it.index >= i }?.start ?: if (i < cols.first().index) cols.first().start else cols.last().start + cols.last().size
    fun colEnd(i: UInt): Double = cols.lastOrNull { it.index <= i }?.let { it.start + it.size } ?: cols.first().start
    val accent = GridColors.SheetAccent
    // the active cell stays clear inside its block
    val ar = sel.activeRow
    val ac = sel.activeCol
    val m = v.merges.firstOrNull { ar in it.firstRow..it.lastRow && ac in it.firstCol..it.lastCol }
    val ax0 = screenX(colStart(m?.firstCol ?: ac))
    val ax1 = screenX(colEnd(m?.lastCol ?: ac))
    val ay0 = screenY(rowStart(m?.firstRow ?: ar))
    val ay1 = screenY(rowEnd(m?.lastRow ?: ar))
    for (r in sel.ranges) {
        val x0 = screenX(colStart(r.firstCol))
        val x1 = screenX(colEnd(r.lastCol))
        val y0 = screenY(rowStart(r.firstRow))
        val y1 = screenY(rowEnd(r.lastRow))
        if (x1 <= x0 || y1 <= y0 || (r.firstRow == r.lastRow && r.firstCol == r.lastCol)) continue
        val holds = ar in r.firstRow..r.lastRow && ac in r.firstCol..r.lastCol
        if (!holds) {
            drawRect(GridColors.Selection, Offset(x0, y0), Size(x1 - x0, y1 - y0))
            continue
        }
        // above, below, then either side of the active cell
        val fill = GridColors.Selection
        if (ay0 > y0) drawRect(fill, Offset(x0, y0), Size(x1 - x0, ay0 - y0))
        if (y1 > ay1) drawRect(fill, Offset(x0, ay1), Size(x1 - x0, y1 - ay1))
        if (ax0 > x0) drawRect(fill, Offset(x0, ay0), Size(ax0 - x0, ay1 - ay0))
        if (x1 > ax1) drawRect(fill, Offset(ax1, ay0), Size(x1 - ax1, ay1 - ay0))
    }
    // the current block is outlined, with its handles
    val cur = sel.ranges.lastOrNull() ?: return
    val x0 = screenX(colStart(cur.firstCol))
    val x1 = screenX(colEnd(cur.lastCol))
    val y0 = screenY(rowStart(cur.firstRow))
    val y1 = screenY(rowEnd(cur.lastRow))
    val w = 2f * density
    drawRect(accent, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(w))
    // no handles while the cell is being typed in
    if (vm.editing) return
    val handle = 6f * density
    val rowsHere = rows.first().index..rows.last().index
    val colsHere = cols.first().index..cols.last().index
    if (cur.firstRow in rowsHere && cur.firstCol in colsHere) {
        drawCircle(GridColors.Cells, handle, Offset(x0, y0))
        drawCircle(accent, handle, Offset(x0, y0), style = Stroke(1.5f * density))
    }
    if (cur.lastRow in rowsHere && cur.lastCol in colsHere) {
        drawCircle(GridColors.Cells, handle, Offset(x1, y1))
        drawCircle(accent, handle, Offset(x1, y1), style = Stroke(1.5f * density))
    }
}

/** The blocks a formula being typed refers to, outlined and lightly filled in their colours. */
private fun DrawScope.drawFormulaRefs(refs: List<FormulaRef>, v: Viewport, screenX: (Double) -> Float, screenY: (Double) -> Float, density: Float) {
    val rows = v.rows
    val cols = v.cols
    if (refs.isEmpty() || rows.isEmpty() || cols.isEmpty()) return
    val rr = rows.first().index.toInt()..rows.last().index.toInt()
    val cr = cols.first().index.toInt()..cols.last().index.toInt()
    for (r in refs) {
        if (r.lastRow < rr.first || r.firstRow > rr.last || r.lastCol < cr.first || r.firstCol > cr.last) continue
        val top = rows.firstOrNull { it.index.toInt() >= r.firstRow }?.start ?: continue
        val bottom = rows.lastOrNull { it.index.toInt() <= r.lastRow }?.let { it.start + it.size } ?: continue
        val left = cols.firstOrNull { it.index.toInt() >= r.firstCol }?.start ?: continue
        val right = cols.lastOrNull { it.index.toInt() <= r.lastCol }?.let { it.start + it.size } ?: continue
        val x0 = screenX(left)
        val y0 = screenY(top)
        val w = screenX(right) - x0
        val h = screenY(bottom) - y0
        if (w <= 0f || h <= 0f) continue
        drawRect(r.color.copy(alpha = 0.12f), Offset(x0, y0), Size(w, h))
        drawRect(r.color, Offset(x0, y0), Size(w, h), style = Stroke(1.5f * density))
    }
}

private fun DrawScope.drawHeaders(vm: AppViewModel, rowHeaderW: Float, colHeaderH: Float, rowBand: Float, colBand: Float, colPanes: List<HeaderPane>, rowPanes: List<HeaderPane>, density: Float) {
    val fill = GridColors.HeaderFill
    val line = GridColors.HeaderLine
    drawRect(fill, Offset(0f, 0f), Size(size.width, colHeaderH))
    drawRect(fill, Offset(0f, 0f), Size(rowHeaderW, size.height))
    val sel = vm.selection
    fun colSelected(i: UInt) = sel?.ranges?.any { i in it.firstCol..it.lastCol } == true
    fun rowSelected(i: UInt) = sel?.ranges?.any { i in it.firstRow..it.lastRow } == true
    val p = textPaint
    p.typeface = android.graphics.Typeface.DEFAULT
    p.textSize = 12f * density
    p.isUnderlineText = false
    p.isStrikeThruText = false
    linePaint.color = line.toArgb()
    linePaint.strokeWidth = 1f
    linePaint.pathEffect = null
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        for (pane in colPanes) {
            nc.save()
            nc.clipRect(pane.from, 0f, pane.to, colHeaderH)
            for (c in pane.lines) {
                val x0 = pane.at(c.start)
                val x1 = pane.at(c.start + c.size)
                if (x1 < pane.from || x0 > pane.to) continue
                val on = colSelected(c.index)
                if (on) drawRectNative(nc, x0, 0f, x1, colHeaderH, GridColors.HeaderSelected.toArgb())
                p.color = (if (on) GridColors.OnHeaderSelected else GridColors.OnHeader).toArgb()
                val label = columnLetters(c.index.toInt())
                nc.drawText(label, (x0 + x1) / 2 - p.measureText(label) / 2, (colBand + colHeaderH) / 2 - (p.ascent() + p.descent()) / 2, p)
                nc.drawLine(x1 - 0.5f, 0f, x1 - 0.5f, colHeaderH, linePaint)
            }
            nc.restore()
        }
        for (pane in rowPanes) {
            nc.save()
            nc.clipRect(0f, pane.from, rowHeaderW, pane.to)
            for (r in pane.lines) {
                val y0 = pane.at(r.start)
                val y1 = pane.at(r.start + r.size)
                if (y1 < pane.from || y0 > pane.to) continue
                val on = rowSelected(r.index)
                if (on) drawRectNative(nc, 0f, y0, rowHeaderW, y1, GridColors.HeaderSelected.toArgb())
                // a row a filter shows has its number in blue
                val filtered = vm.sheetView?.filteredRows?.let { r.index in it.first..it.last } == true
                p.color = (if (on) GridColors.OnHeaderSelected else if (filtered) GridColors.FilteredRow else GridColors.OnHeader).toArgb()
                val label = (r.index.toLong() + 1).toString()
                nc.drawText(label, (rowBand + rowHeaderW) / 2 - p.measureText(label) / 2, (y0 + y1) / 2 - (p.ascent() + p.descent()) / 2, p)
                nc.drawLine(0f, y1 - 0.5f, rowHeaderW, y1 - 0.5f, linePaint)
            }
            nc.restore()
        }
    }
    drawLine(line, Offset(0f, colHeaderH - 0.5f), Offset(size.width, colHeaderH - 0.5f), 1f)
    drawLine(line, Offset(rowHeaderW - 0.5f, 0f), Offset(rowHeaderW - 0.5f, size.height), 1f)
    // the select-all corner
    drawRect(fill, Offset(0f, 0f), Size(rowHeaderW - 1f, colHeaderH - 1f))
    val t = 7f * density
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(rowHeaderW - 4f * density, colHeaderH - 4f * density - t)
        lineTo(rowHeaderW - 4f * density, colHeaderH - 4f * density)
        lineTo(rowHeaderW - 4f * density - t, colHeaderH - 4f * density)
        close()
    }
    drawPath(path, GridColors.HeaderLine)
}

private val rectPaint = Paint()

private fun drawRectNative(nc: android.graphics.Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
    rectPaint.color = color
    nc.drawRect(l, t, r, b, rectPaint)
}
