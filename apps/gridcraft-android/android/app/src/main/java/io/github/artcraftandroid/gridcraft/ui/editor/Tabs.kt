package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.core.Across
import io.github.artcraftandroid.gridcraft.core.Down
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/** The titles of the palette's pages. */
internal val PANEL_TITLES = mapOf(
    "font" to "Font",
    "size" to "Font Size",
    "borders" to "Borders",
    "fillColor" to "Fill Color",
    "fontColor" to "Font Color",
    "cellStyles" to "Cell Styles",
    "numberFormat" to "Number Format",
    "insertDelete" to "Insert & Delete",
    "cellSize" to "Format Cell Size",
    "clear" to "Clear",
    "autoSum" to "AutoSum",
    "sortFilter" to "Sort & Filter",
    "freeze" to "Freeze Panes",
    "fn:Financial" to "Financial",
    "fn:Logical" to "Logical",
    "fn:Text" to "Text",
    "fn:DateTime" to "Date & Time",
    "fn:Lookup" to "Lookup & Reference",
    "fn:MathTrig" to "Math & Trig",
    "fn:Statistical" to "Statistical",
    "fn:Engineering" to "Engineering",
    "fn:Information" to "Information",
    "fn:Database" to "Database",
    "fn:Recent" to "Recently Used",
    "insertChart" to "Chart",
    "specialFormats" to "Special",
    "fn:Compatibility" to "Compatibility",
    "shapes" to "Shapes",
    "dateFormats" to "Date",
    "timeFormats" to "Time",
    "currencyFormats" to "Currency",
    "share" to "Share",
    "chartStyle" to "Style",
    "shapeFill" to "Fill",
    "shapeSize" to "Size",
    "altText" to "Alt Text",
    "cropPicture" to "Crop Picture",
    "picturePosition" to "Picture Position",
    "cropPosition" to "Crop Position",
    "shapeOutline" to "Outline",
    "shapeStyles" to "Shape Styles",
    "changeShape" to "Change Shape",
    "tableStyles" to "Table Styles",
    "tableAltText" to "Alt Text",
    "chartType" to "Type",
    "chartTypeRecommended" to "Recommended",
    "chartLayout" to "Layout",
    "chartElements" to "Elements",
    "chartElement:axes" to "Axes",
    "chartElement:axisTitles" to "Axis Titles",
    "chartElement:title" to "Chart Title",
    "chartElement:labels" to "Data Labels",
    "chartElement:table" to "Data Table",
    "chartElement:errorBars" to "Error Bars",
    "chartElement:gridlines" to "Gridlines",
    "chartElement:legend" to "Legend",
    "chartElement:lines" to "Lines",
    "chartElement:trendline" to "Trendline",
    "chartElement:upDownBars" to "Up/Down Bars",
    "chartColors" to "Color",
    "arrange" to "Arrange",
    "pictures" to "Pictures",
    "recommendedCharts" to "Recommended",
    "tabColor" to "Tab Color",
    "moveSheet" to "Move Sheet",
    "pictureStyles" to "Styles",
    "pictureEffects" to "Picture Effects",
    "pictureShadow" to "Shadow",
    "pictureReflection" to "Reflection",
    "penColors" to "More Colors",
    "pen" to "Pen",
    "addPen" to "Add Pen",
    "accountingFormats" to "Accounting",
    "borderStyle" to "Line Style",
    "borderColor" to "Line Color",
    "pictureReset" to "Reset",
    "pivotLayout" to "Layout Options",
    "pivotStyles" to "Quick Styles",
    "textDirection" to "Text Direction",
    "numberNegatives" to "Number",
    "picturesOver" to "Place over Cells",
    "links" to "Link",
    "picturesInCell" to "Place in Cell",
    "currencySymbols" to "Choose Symbol",
    "moreSymbols:currency" to "More Symbols",
    "moreSymbols:accounting" to "More Symbols",
)

/** Fonts the font list offers: the common ones, which the app draws with their open twins. */
private val FONTS = listOf("Calibri", "Aptos", "Arial", "Cambria", "Courier New", "Times New Roman", "Verdana", "Georgia", "Tahoma", "Trebuchet MS")

private val SIZES = listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 22f, 24f, 26f, 28f, 36f, 48f, 72f)

internal fun sizeLabel(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else s.toString()

@Composable
internal fun TabRows(vm: AppViewModel, tab: String) {
    when (tab) {
        "Home" -> HomeTab(vm)
        "Insert" -> InsertTab(vm)
        "Draw" -> DrawTab(vm)
        "Formulas" -> FormulasTab(vm)
        "Data" -> DataTab(vm)
        "Review" -> ReviewTab(vm)
        "View" -> ViewTab(vm)
    }
}

@Composable
private fun HomeTab(vm: AppViewModel) {
    val f = vm.format
    LaunchedEffect(vm.info?.revision) { vm.loadThemeFonts() }
    Scrolling {
        // the font and its size share a row
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().height(ROW + 1.dp).background(GridColors.Surface).bottomEdge(GridColors.Divider),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(ROW).clickable { vm.pushPanel("font") }.padding(start = 16.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                // the theme's body font is named as such
                val body = vm.themeFonts.getOrNull(1)?.takeIf { it.equals(f?.font, ignoreCase = true) }
                Text(f?.font?.let { if (body != null) "$it (Body)" else it } ?: "Font", color = GridColors.OnSurface, fontSize = 16.sp, maxLines = 1)
            }
            Box(Modifier.height(ROW).clickable { vm.pushPanel("size") }.padding(horizontal = 24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(sizeLabel(f?.size ?: 11f), color = GridColors.OnSurface, fontSize = 16.sp)
            }
        }
        CellRow(
            Triple(Glyph.Letters("B", weight = FontWeight.Bold), f?.bold == true) { vm.command("home.bold") },
            Triple(Glyph.Letters("I", italic = true, family = androidx.compose.ui.text.font.FontFamily.Serif), f?.italic == true) { vm.command("home.italic") },
            Triple(Glyph.Letters("U", decoration = TextDecoration.Underline), f?.underline == true) { vm.command("home.underline") },
            Triple(Glyph.Letters("ab", decoration = TextDecoration.LineThrough), f?.strike == true) { vm.command("home.strikethrough") },
        )
        PaletteRow("Borders", Glyph.Border("all"), onClick = { vm.pushPanel("borders") }, chevron = true)
        // a tap applies the colour chosen last; the arrow opens the colours
        PaletteRow("Fill Color", Glyph.WithBar(LucideIcons.PaintBucket.glyph(), Color(vm.lastFill.second)), onClick = vm::applyLastFill, onOpen = { vm.pushPanel("fillColor") })
        PaletteRow("Font Color", Glyph.WithBar(Glyph.Letters("A"), Color(vm.lastFontColor.second)), onClick = vm::applyLastFontColor, onOpen = { vm.pushPanel("fontColor") })
        Band()
        CellRow(
            Triple(Glyph.CellLines("top"), f?.down == Down.TOP) { vm.command("home.alignTop") },
            Triple(Glyph.CellLines("middle"), f?.down == Down.CENTER) { vm.command("home.alignMiddle") },
            Triple(Glyph.CellLines("bottom"), f?.down == Down.BOTTOM) { vm.command("home.alignBottom") },
        )
        CellRow(
            Triple(LucideIcons.AlignLeft.glyph(), f?.across == Across.LEFT) { vm.command("home.alignLeft") },
            Triple(LucideIcons.AlignCenter.glyph(), f?.across == Across.CENTER) { vm.command("home.alignCenter") },
            Triple(LucideIcons.AlignRight.glyph(), f?.across == Across.RIGHT) { vm.command("home.alignRight") },
        )
        PaletteRow("Merge & Center", LucideIcons.Columns2.glyph(), onClick = { vm.command(if (f?.merged == true) "home.unmergeCells" else "home.mergeCenter") }, pressed = f?.merged == true)
        PaletteRow("Wrap Text", LucideIcons.TextCursorInput.glyph(), onClick = { vm.command("home.wrapText", json("on" to (f?.wrap != true))) }, pressed = f?.wrap == true)
        PaletteRow("Text Direction", LucideIcons.Pilcrow.glyph(), onClick = { vm.pushPanel("textDirection") }, chevron = true)
        Band()
        PaletteRow("Format Painter", LucideIcons.Paintbrush.glyph(), onClick = vm::startFormatPainter, pressed = vm.formatPainting)
        PaletteRow("Cell Styles", LucideIcons.SwatchBook.glyph(), onClick = { vm.pushPanel("cellStyles") }, chevron = true)
        PaletteRow("Number Format", LucideIcons.Hash.glyph(), onClick = { vm.pushPanel("numberFormat") }, chevron = true)
        Band()
        // a table at once in the default style, as the phone app makes it; its tab follows
        PaletteRow("Format as Table", LucideIcons.TableProperties.glyph(), onClick = { vm.makeTable("home.formatAsTable") })
        // greyed until the active cell is in a table
        PaletteRow("Cards View", LucideIcons.GalleryVerticalEnd.glyph(), onClick = vm::openCards, enabled = vm.tableInfo != null)
        Band()
        PaletteRow("Insert & Delete", LucideIcons.Table.glyph(), onClick = { vm.pushPanel("insertDelete") }, chevron = true)
        PaletteRow("Format Cell Size", LucideIcons.ArrowLeftRight.glyph(), onClick = { vm.pushPanel("cellSize") }, chevron = true)
        PaletteRow("Clear", LucideIcons.Eraser.glyph(), onClick = { vm.pushPanel("clear") }, chevron = true)
        Band()
        PaletteRow("AutoSum", LucideIcons.Sigma.glyph(), onClick = { vm.pushPanel("autoSum") }, chevron = true)
        PaletteRow("Sort & Filter", LucideIcons.ArrowUpDown.glyph(), onClick = { vm.pushPanel("sortFilter") }, chevron = true)
        Band()
        PaletteRow("Find", LucideIcons.Search.glyph(), onClick = vm::openFind)
        PaletteRow("Select Range", LucideIcons.SquareDashedMousePointer.glyph(), onClick = {
            vm.collapsePalette()
            vm.dialog = "goTo"
        })
    }
}

@Composable
private fun InsertTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Table", LucideIcons.Table.glyph(), onClick = { vm.makeTable("insert.table") })
        PaletteRow("Pictures", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("pictures") }, chevron = true)
        PaletteRow("Shapes", LucideIcons.Shapes.glyph(), onClick = { vm.pushPanel("shapes") }, chevron = true)
        PaletteRow("Text Box", LucideIcons.Type.glyph(), onClick = { vm.insertShape("textBox") })
        PaletteRow("Recommended", LucideIcons.ChartColumn.glyph(), onClick = { vm.pushPanel("recommendedCharts") }, chevron = true)
        PaletteRow("Chart", LucideIcons.ChartColumn.glyph(), onClick = { vm.pushPanel("insertChart") }, chevron = true)
        PaletteRow("Comment", LucideIcons.MessageSquarePlus.glyph(), onClick = {
            vm.collapsePalette()
            vm.dialog = "comment"
        })
        PaletteRow("Checkbox", LucideIcons.SquareCheck.glyph(), onClick = { vm.command("insert.checkbox") })
        PaletteRow("Link", LucideIcons.Link.glyph(), onClick = { vm.pushPanel("links") }, chevron = true)
    }
}

/**
 * Draw: drawing on or off, the eraser, then the strip of pens and Add. A pen tapped is taken; the
 * pen in hand tapped again opens its colours and thickness.
 */
@Composable
private fun DrawTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Draw with Mouse or Touch", LucideIcons.Pointer.glyph(), onClick = { if (vm.drawTool == "pen") vm.chooseTool("") else vm.pickPen(vm.penIndex) }, pressed = vm.drawTool == "pen")
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(GridColors.Divider)) {
            Box(
                Modifier.width(64.dp).height(ROW).background(if (vm.drawTool == "eraser") GridColors.Pressed else GridColors.Surface).clickable { vm.chooseTool(if (vm.drawTool == "eraser") "" else "eraser") },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { GlyphIcon(LucideIcons.Eraser.glyph(), GridColors.OnStrip, size = 26.dp) }
        }
        Band()
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            androidx.compose.foundation.layout.Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                vm.pens.forEachIndexed { i, pen ->
                    val held = vm.drawTool == "pen" && vm.penIndex == i
                    PenButton(pen, held) { if (held) vm.pushPanel("pen") else vm.pickPen(i) }
                }
            }
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxHeight().clickable { vm.pushPanel("addPen") }.padding(start = 12.dp, end = 15.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                GlyphIcon(LucideIcons.Plus.glyph(), GridColors.Accent, size = 24.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Text("Add", color = GridColors.OnSurface, fontSize = 16.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                GlyphIcon(LucideIcons.ChevronRight.glyph(), GridColors.OnStrip, size = 20.dp)
            }
        }
    }
}

/** A pen of the strip, drawn standing in its colour; the one in hand stands higher. */
@Composable
private fun PenButton(pen: io.github.artcraftandroid.gridcraft.ui.Pen, held: Boolean, onClick: () -> Unit) {
    val ink = Color(android.graphics.Color.parseColor(pen.color))
    val body = GridColors.OnStrip
    androidx.compose.foundation.Canvas(Modifier.width(56.dp).fillMaxHeight().clickable(onClick = onClick)) {
        val u = size.width / 56f
        val cx = size.width / 2
        val w = (if (pen.kind == "highlighter") 22f else 16f) * u
        val top = (if (held) 4f else 16f) * u
        val tip = 14f * u
        // the tip: a point for a pen or pencil, a slanted chisel for a highlighter
        val path = androidx.compose.ui.graphics.Path().apply {
            if (pen.kind == "highlighter") {
                moveTo(cx - w / 2, top + tip)
                lineTo(cx - w / 4, top + 3 * u)
                lineTo(cx + w / 4, top)
                lineTo(cx + w / 2, top + tip)
            } else {
                moveTo(cx - w / 2, top + tip)
                lineTo(cx - 2 * u, top)
                lineTo(cx + 2 * u, top)
                lineTo(cx + w / 2, top + tip)
            }
            close()
        }
        val line = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * u)
        drawPath(path, if (pen.kind == "pencil") Color(0xFFE8C48A) else Color(0xFF1A1A1A))
        if (pen.kind != "highlighter") drawRect(ink, androidx.compose.ui.geometry.Offset(cx - 2 * u, top), androidx.compose.ui.geometry.Size(4 * u, 5 * u))
        drawPath(path, body, style = line)
        // the barrel in the ink's colour, under a band of the outline's
        val barrel = androidx.compose.ui.geometry.Offset(cx - w / 2, top + tip)
        val barrelSize = androidx.compose.ui.geometry.Size(w, size.height - top - tip)
        drawRect(ink, barrel, barrelSize)
        drawRect(body, barrel, androidx.compose.ui.geometry.Size(w, 3 * u))
        drawRect(body, barrel, barrelSize, style = line)
    }
}

@Composable
private fun FormulasTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("AutoSum", LucideIcons.Sigma.glyph(), onClick = { vm.pushPanel("autoSum") }, chevron = true)
        PaletteRow("Recently Used", LucideIcons.History.glyph(), onClick = { vm.pushPanel("fn:Recent") }, chevron = true)
        for ((cat, label, icon) in listOf(
            Triple("Financial", "Financial", LucideIcons.Banknote), Triple("Logical", "Logical", LucideIcons.CircleHelp),
            Triple("Text", "Text", LucideIcons.Type), Triple("DateTime", "Date & Time", LucideIcons.CalendarClock),
            Triple("Lookup", "Lookup & Reference", LucideIcons.Search), Triple("MathTrig", "Math & Trig", LucideIcons.Calculator),
            Triple("Statistical", "Statistical", LucideIcons.ChartColumn), Triple("Engineering", "Engineering", LucideIcons.Settings),
            Triple("Information", "Information", LucideIcons.Info), Triple("Database", "Database", LucideIcons.Database),
            Triple("Compatibility", "Compatibility", LucideIcons.Archive),
        )) {
            PaletteRow(label, icon.glyph(), onClick = { vm.pushPanel("fn:$cat") }, chevron = true)
        }
        Band()
        PaletteRow("Calculate Now", LucideIcons.Calculator.glyph(), onClick = { vm.command("formulas.calculateNow") })
    }
}

@Composable
private fun DataTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Show Detail", LucideIcons.Plus.glyph(), onClick = { vm.command("data.showDetail") })
        PaletteRow("Hide Detail", LucideIcons.Minus.glyph(), onClick = { vm.command("data.hideDetail") })
    }
}

@Composable
private fun ReviewTab(vm: AppViewModel) {
    LaunchedEffect(vm.info?.revision) { vm.loadHasNotes() }
    Scrolling {
        PaletteRow("Comment", LucideIcons.MessageSquarePlus.glyph(), onClick = {
            vm.collapsePalette()
            vm.dialog = "comment"
        })
        PaletteRow("Show Comments", LucideIcons.MessageSquare.glyph(), onClick = { vm.showComments(!vm.commentsOpen) }, pressed = vm.commentsOpen)
        // greyed until the workbook has a note to convert
        PaletteRow("Convert All Notes", LucideIcons.StickyNote.glyph(), onClick = { vm.command("review.convertToComments") }, enabled = vm.hasNotes)
    }
}

@Composable
private fun ViewTab(vm: AppViewModel) {
    val sv = vm.sheetView
    Scrolling {
        // Sheet View: shared views of a workbook in the cloud; the phone app greys them signed out,
        // as GridCraft always is
        Text("Sheet View", color = GridColors.Disabled, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
        // the current view's name, at the left edge like a field
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().height(ROW + 1.dp).background(GridColors.Surface).bottomEdge(GridColors.Divider).padding(start = 16.dp, end = 15.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Default", color = GridColors.Disabled, fontSize = 16.sp, modifier = Modifier.weight(1f))
            GlyphIcon(LucideIcons.ChevronRight.glyph(), GridColors.Disabled, size = 20.dp)
        }
        PaletteRow("Keep", LucideIcons.Save.glyph(), onClick = {}, enabled = false)
        PaletteRow("Exit", LucideIcons.EyeOff.glyph(), onClick = {}, enabled = false)
        PaletteRow("New", LucideIcons.Eye.glyph(), onClick = {}, enabled = false)
        PaletteRow("Options", LucideIcons.List.glyph(), onClick = {}, enabled = false)
        Band()
        CheckPaletteRow("Formula Bar", vm.showFormulaBar) { vm.showFormulaBar = !vm.showFormulaBar }
        CheckPaletteRow("Gridlines", sv?.gridlines == true) { vm.command("view.gridlines", json("on" to (sv?.gridlines != true))) }
        CheckPaletteRow("Headings", sv?.headings == true) { vm.command("view.headings", json("on" to (sv?.headings != true))) }
        CheckPaletteRow("Sheet Right-to-Left", sv?.rightToLeft == true) { vm.command("view.rightToLeft", json("on" to (sv?.rightToLeft != true))) }
        Band()
        PaletteRow("Zoom In", LucideIcons.ZoomIn.glyph(), onClick = { vm.zoom = (vm.zoom + 0.1f).coerceAtMost(4f) })
        PaletteRow("Zoom Out", LucideIcons.ZoomOut.glyph(), onClick = { vm.zoom = (vm.zoom - 0.1f).coerceAtLeast(0.1f) })
        Band()
        PaletteRow("Freeze Panes", LucideIcons.PanelsTopLeft.glyph(), onClick = { vm.pushPanel("freeze") }, chevron = true)
    }
}

/** A setting's row with a tick box, as the View tab shows them. */
@Composable
internal fun CheckPaletteRow(label: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().height(ROW + 1.dp).background(GridColors.Surface).bottomEdge(GridColors.Divider).clickable(enabled = enabled, onClick = onClick).padding(start = 16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).background(if (checked) GridColors.OnHeader else GridColors.Pressed, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { if (checked) GlyphIcon(LucideIcons.Check.glyph(), GridColors.Surface, size = 18.dp) }
        androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        Text(label, color = if (enabled) GridColors.OnSurface else GridColors.Disabled, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
internal fun PanelBody(vm: AppViewModel, panel: String) {
    val f = vm.format
    if (ContextPanel(vm, panel)) return
    if (panel.startsWith("fn:")) {
        FunctionsPanel(vm, panel.removePrefix("fn:"))
        return
    }
    if (panel.startsWith(CHART_FAMILY)) {
        ChartGalleryPanel(vm, panel.removePrefix(CHART_FAMILY))
        return
    }
    if (panel.startsWith(CHART_TYPE_FAMILY)) {
        ChartGalleryPanel(vm, panel.removePrefix(CHART_TYPE_FAMILY), change = true)
        return
    }
    if (panel.startsWith(FN_HELP)) {
        FunctionHelpPanel(vm, panel.removePrefix(FN_HELP))
        return
    }
    when (panel) {
        "font" -> Scrolling {
            LaunchedEffect(Unit) { vm.loadThemeFonts() }
            // the workbook theme's fonts first, headings then body (named as such)
            if (vm.themeFonts.size == 2) {
                SectionBand("Theme Fonts")
                for ((name, role) in vm.themeFonts.zip(listOf(null, "(Body)"))) {
                    ChoiceRow(name, false, onClick = { vm.command("home.fontName", json("name" to name)) }, trailing = role)
                }
                SectionBand("All Fonts")
            }
            for (name in FONTS) ChoiceRow(name, name.equals(f?.font, ignoreCase = true), onClick = { vm.command("home.fontName", json("name" to name)) })
        }
        "size" -> Scrolling {
            for (s in SIZES) ChoiceRow(sizeLabel(s), f?.size == s, onClick = { vm.command("home.fontSize", json("size" to s.toDouble())) })
        }
        "borders" -> BordersPanel(vm)
        "penColors" -> {
            // the pen takes plain colours: the swatches' own, as #RRGGBB
            val choices = vm.colors
            if (choices != null) Scrolling {
                fun hex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)
                SectionBand("Theme Colors")
                SwatchGrid(choices.theme.flatten(), perRow = 10, cellW = 39.dp) {
                    vm.changePen(color = hex(it.argb))
                    vm.popPanel()
                }
                SectionBand("Standard Colors")
                SwatchGrid(choices.standard, perRow = 10, cellW = 39.dp) {
                    vm.changePen(color = hex(it.argb))
                    vm.popPanel()
                }
            }
        }
        "pen" -> {
            val pen = vm.pens.getOrNull(vm.penIndex)
            if (pen != null) Scrolling {
                val highlighter = pen.kind == "highlighter"
                SectionBand("Colors")
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    for (c in if (highlighter) listOf("#FFFF00", "#00FF00", "#00FFFF", "#FF00FF", "#FF8C00", "#FF0000") else listOf("#000000", "#E02020", "#1F5FC9", "#00A651", "#7030A0", "#FF8C00")) {
                        val on = pen.color.equals(c, ignoreCase = true)
                        Box(
                            Modifier.padding(6.dp).size(36.dp).background(if (on) GridColors.Pressed else Color.Transparent, androidx.compose.foundation.shape.CircleShape).clickable { vm.changePen(color = c) },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) {
                            Box(Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(c)), androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
                PaletteRow("More Colors", LucideIcons.Palette.glyph(), onClick = { vm.pushPanel("penColors") }, chevron = true)
                SectionBand("Thickness")
                for ((label, w) in if (highlighter) listOf("Thin" to 10f, "Medium" to 12f, "Thick" to 16f, "Extra Thick" to 24f) else if (pen.kind == "pencil") listOf("Thin" to 0.5f, "Medium" to 1f, "Thick" to 2f, "Extra Thick" to 4f) else listOf("Thin" to 1f, "Medium" to 2f, "Thick" to 4f, "Extra Thick" to 8f)) {
                    ChoiceRow(label, pen.width == w, onClick = { vm.changePen(width = w) })
                }
                if (vm.pens.size > 1) {
                    Band()
                    PaletteRow("Delete Pen", LucideIcons.Trash2.glyph(), onClick = {
                        vm.deletePen()
                        vm.popPanel()
                    })
                }
            }
        }
        "addPen" -> Scrolling {
            for ((label, kind) in listOf("Pen" to "pen", "Pencil" to "pencil", "Highlighter" to "highlighter")) {
                ChoiceRow(label, false, onClick = {
                    vm.addPen(kind)
                    vm.popPanel()
                })
            }
        }
        "accountingFormats" -> AccountingPanel(vm)
        "borderStyle" -> LineStylePanel(vm)
        "borderColor" -> ColorGridPanel(vm, "Automatic", onPick = { vm.borderColorArgb = it }) { c ->
            vm.borderColor = if (c == "\"auto\"" || c == "null") null else c
            vm.popPanel()
        }
        "fillColor" -> ColorGridPanel(vm, "No Fill") { c -> vm.setFill(c) }
        "tabColor" -> ColorGridPanel(vm, "No Color") { c -> vm.setTabColor(c) }
        // as the phone app's Move Sheet page: before which sheet the current one goes, or to the end
        "moveSheet" -> Scrolling {
            val info = vm.info
            val current = info?.activeSheet?.toInt() ?: 0
            SectionBand("Before sheet")
            for ((k, t) in info?.sheets.orEmpty().withIndex()) {
                if (!t.hidden) PaletteRow(t.name, null, onClick = { vm.moveSheetBefore(current, k) }, inset = false)
            }
            PaletteRow("(move to end)", null, onClick = { vm.moveSheetBefore(current, null) }, inset = false)
        }
        "fontColor" -> ColorGridPanel(vm, "Automatic") { c -> vm.setFontColor(c) }
        "pictures" -> Scrolling {
            PaletteRow("Place over Cells", LucideIcons.Images.glyph(), onClick = { vm.pushPanel("picturesOver") }, chevron = true)
            PaletteRow("Place in Cell", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("picturesInCell") }, chevron = true)
        }
        "links" -> Scrolling {
            PaletteRow("Insert Link...", null, onClick = {
                vm.collapsePalette()
                vm.dialog = "link"
            }, inset = false)
            if (vm.recentLinks.isEmpty()) {
                Text("No items available.", color = GridColors.OnSurface, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
            }
            for (url in vm.recentLinks) PaletteRow(url, LucideIcons.Link.glyph(), onClick = { vm.insertLink(url, "") })
        }
        "picturesOver" -> Scrolling {
            PaletteRow("Photos", null, onClick = { vm.requestPicture("photo", inCell = false) }, inset = false)
            PaletteRow("Camera", null, onClick = { vm.requestPicture("camera", inCell = false) }, inset = false)
        }
        "picturesInCell" -> Scrolling {
            PaletteRow("From File...", null, onClick = { vm.requestPicture("file", inCell = true) }, inset = false)
            PaletteRow("Camera", null, onClick = { vm.requestPicture("camera", inCell = true) }, inset = false)
        }
        "recommendedCharts" -> Scrolling {
            LaunchedEffect(vm.selection) { vm.loadChartRecommendations() }
            if (vm.chartRecommendations.isEmpty()) {
                Text("Select the data to chart.", color = GridColors.OnSurface, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
            }
            ChartTiles(vm, vm.chartRecommendations.map { (label, type, sub) -> label to "$type|$sub" })
        }
        "textDirection" -> Scrolling {
            val order = f?.readingOrder?.toInt() ?: 0
            for ((i, label) in listOf("Context", "Left-to-Right", "Right-to-Left").withIndex()) {
                val icon = listOf(LucideIcons.Pilcrow, LucideIcons.PilcrowRight, LucideIcons.PilcrowLeft)[i]
                PaletteRow(label, icon.glyph(), onClick = { vm.command("home.textDirection", json("direction" to listOf("context", "ltr", "rtl")[i])) }, pressed = order == i)
            }
        }
        "cellStyles" -> CellStylesPanel(vm)
        "numberFormat" -> NumberFormatPanel(vm)
        "numberNegatives" -> NumberNegativesPanel(vm)
        "currencySymbols" -> CurrencySymbolsPanel(vm)
        "moreSymbols:currency" -> MoreSymbolsPanel(vm, forAccounting = false)
        "moreSymbols:accounting" -> MoreSymbolsPanel(vm, forAccounting = true)
        "specialFormats" -> SpecialPanel(vm)
        "insertChart" -> ChartFamiliesPanel(vm)
        "insertDelete" -> Scrolling {
            fun insert(shift: String) = vm.command("home.insertCells", json("shift" to shift))
            fun delete(shift: String) = vm.command("home.deleteCells", json("shift" to shift))
            PaletteRow("Insert & Shift Right", LucideIcons.SquareArrowRight.glyph(), onClick = { insert("right") })
            PaletteRow("Insert & Shift Down", LucideIcons.SquareArrowDown.glyph(), onClick = { insert("down") })
            PaletteRow("Insert Rows", LucideIcons.BetweenHorizontalStart.glyph(), onClick = { insert("row") })
            PaletteRow("Insert Columns", LucideIcons.BetweenVerticalStart.glyph(), onClick = { insert("column") })
            PaletteRow("Delete & Shift Left", LucideIcons.SquareArrowLeft.glyph(), onClick = { delete("left") })
            PaletteRow("Delete & Shift Up", LucideIcons.SquareArrowUp.glyph(), onClick = { delete("up") })
            PaletteRow("Delete Rows", LucideIcons.ListX.glyph(), onClick = { delete("row") })
            PaletteRow("Delete Columns", LucideIcons.Grid2x2X.glyph(), onClick = { delete("column") })
        }
        "cellSize" -> CellSizePanel(vm)
        "dateFormats" -> SamplesPanel(vm, time = false)
        "timeFormats" -> SamplesPanel(vm, time = true)
        "currencyFormats" -> CurrencyPanel(vm)
        "clear" -> Scrolling {
            PaletteRow("Clear All", LucideIcons.Eraser.glyph(), onClick = { vm.command("edit.clearAll") })
            PaletteRow("Clear Formats", LucideIcons.RemoveFormatting.glyph(), onClick = { vm.command("edit.clearFormats") })
            PaletteRow("Clear", LucideIcons.Delete.glyph(), onClick = { vm.command("edit.clearContents") })
        }
        "autoSum" -> Scrolling {
            for ((label, fn) in listOf("Sum" to "SUM", "Average" to "AVERAGE", "Count Numbers" to "COUNT", "Max" to "MAX", "Min" to "MIN")) {
                PaletteRow(label, if (fn == "SUM") LucideIcons.Sigma.glyph() else null, onClick = { vm.command("formulas.autoSum", json("function" to fn)) })
            }
        }
        "sortFilter" -> Scrolling {
            val sv = vm.sheetView
            // a filter that hides rows can be cleared or applied again
            val filtering = sv?.filteredRows != null
            PaletteRow("Sort Ascending", LucideIcons.ArrowDownAZ.glyph(), onClick = { vm.command("data.sortAscending") })
            PaletteRow("Sort Descending", LucideIcons.ArrowDownZA.glyph(), onClick = { vm.command("data.sortDescending") })
            PaletteRow("Show Filter Buttons", LucideIcons.Funnel.glyph(), onClick = { vm.command("data.filter") }, pressed = sv?.autofilter == true)
            PaletteRow("Clear All", LucideIcons.FunnelX.glyph(), onClick = { vm.command("data.clearFilter") }, enabled = filtering)
            PaletteRow("Reapply All", LucideIcons.RefreshCw.glyph(), onClick = { vm.command("data.reapply") }, enabled = filtering)
        }
        "shapes" -> ShapesPanel(vm)
        "changeShape" -> ShapesPanel(vm, change = true)
        "freeze" -> Scrolling {
            // each choice shows when it is what the sheet has; choosing it again unfreezes
            val sv = vm.sheetView
            val rows = sv?.frozenRows ?: 0u
            val cols = sv?.frozenCols ?: 0u
            val top = rows == 1u && cols == 0u
            val first = rows == 0u && cols == 1u
            val panes = (rows > 0u || cols > 0u) && !top && !first
            ChoiceRow("Freeze Panes", panes, onClick = { vm.command(if (panes) "view.unfreezePanes" else "view.freezePanes") })
            ChoiceRow("Freeze Top Row", top, onClick = { vm.command(if (top) "view.unfreezePanes" else "view.freezeTopRow") })
            ChoiceRow("Freeze First Column", first, onClick = { vm.command(if (first) "view.unfreezePanes" else "view.freezeFirstColumn") })
        }
    }
}

/** A function category's functions; one starts a formula with it in the formula bar. */
@Composable
private fun FunctionsPanel(vm: AppViewModel, category: String) {
    var list by remember(category) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(category) {
        val e = vm.engine ?: return@LaunchedEffect
        val query = if (category == "Recent") "{}" else """{"category": "$category"}"""
        val json = runCatching { e.command { execute("formulas.functions", query) } }.getOrNull() ?: return@LaunchedEffect
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return@LaunchedEffect
        val all = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { it.optString("name") to it.optString("signature") } }
        list = if (category == "Recent") vm.recentFunctions.mapNotNull { n -> all.firstOrNull { it.first == n } } else all
    }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp)) { Text("…", color = GridColors.OnSurface, fontSize = 16.sp, fontStyle = FontStyle.Italic) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.first }) { (name, _) ->
            FunctionRow(name, onPick = {
                vm.noteFunction(name)
                vm.collapsePalette()
                if (vm.editing && vm.editText.startsWith("=")) vm.editText = vm.editText + name + "(" else vm.beginEdit("=$name(")
            }, onHelp = { vm.pushPanel(FN_HELP + name) })
        }
    }
}

/** The selected object's tab: a chart's type, layout, elements and colours; Delete for any object. */
@Composable
internal fun ContextRows(vm: AppViewModel, tab: String): Boolean {
    when (tab) {
        "Chart" -> Scrolling {
            // as the phone app's Chart tab: Type | Layout, Elements | Color, Style | Switch
            // Row/Column | Arrange | Size, Alt Text
            PaletteRow("Type", LucideIcons.ChartColumn.glyph(), onClick = { vm.pushPanel("chartType") }, chevron = true)
            Band()
            PaletteRow("Layout", LucideIcons.LayoutTemplate.glyph(), onClick = { vm.pushPanel("chartLayout") }, chevron = true)
            PaletteRow("Elements", LucideIcons.ListPlus.glyph(), onClick = { vm.pushPanel("chartElements") }, chevron = true)
            Band()
            PaletteRow("Color", LucideIcons.SwatchBook.glyph(), onClick = { vm.pushPanel("chartColors") }, chevron = true)
            PaletteRow("Style", LucideIcons.WandSparkles.glyph(), onClick = { vm.pushPanel("chartStyle") }, chevron = true)
            Band()
            PaletteRow("Switch Row/Column", LucideIcons.ArrowLeftRight.glyph(), onClick = { vm.chartCommand("chart.switchRowColumn") })
            Band()
            ObjectRows(vm)
        }
        "Table" -> Scrolling {
            // as the phone app's Table tab: rows and columns in and out of the table (no row above
            // its header), the style options lit while on, then Convert to Range, Alt Text and the
            // styles
            val t = vm.tableInfo
            fun insert(where: String) = vm.command("table.insert", json("where" to where))
            PaletteRow("Insert Row Above", LucideIcons.BetweenHorizontalStart.glyph(), onClick = { insert("above") }, enabled = t?.inHeader != true)
            PaletteRow("Insert Row Below", LucideIcons.BetweenHorizontalEnd.glyph(), onClick = { insert("below") })
            PaletteRow("Insert Column Left", LucideIcons.BetweenVerticalStart.glyph(), onClick = { insert("left") })
            PaletteRow("Insert Column Right", LucideIcons.BetweenVerticalEnd.glyph(), onClick = { insert("right") })
            PaletteRow("Delete Rows", LucideIcons.ListX.glyph(), onClick = { vm.command("table.deleteRows") })
            PaletteRow("Delete Columns", LucideIcons.Grid2x2X.glyph(), onClick = { vm.command("table.deleteColumns") })
            PaletteRow("Delete Table", LucideIcons.Trash2.glyph(), onClick = { vm.command("table.delete") })
            Band()
            for ((label, glyph, option) in listOf(
                Triple("Header Row", LucideIcons.PanelTop.glyph(), "table.headerRow" to (t?.headerRow == true)),
                Triple("Total Row", LucideIcons.Sigma.glyph(), "table.totalRow" to (t?.totalsRow == true)),
                Triple("First Column", LucideIcons.PanelLeft.glyph(), "table.firstColumn" to (t?.firstCol == true)),
                Triple("Last Column", LucideIcons.PanelRight.glyph(), "table.lastColumn" to (t?.lastCol == true)),
                Triple("Banded Rows", LucideIcons.Rows3.glyph(), "table.bandedRows" to (t?.bandedRows == true)),
                Triple("Banded Columns", LucideIcons.Columns3.glyph(), "table.bandedColumns" to (t?.bandedCols == true)),
            )) {
                val (id, on) = option
                PaletteRow(label, glyph, onClick = { vm.command(id, json("on" to !on)) }, pressed = on)
            }
            Band()
            PaletteRow("Convert to Range", LucideIcons.Table.glyph(), onClick = { vm.command("table.convertToRange") })
            PaletteRow("Alt Text", LucideIcons.ScanText.glyph(), onClick = { vm.pushPanel("tableAltText") }, chevron = true)
            Band()
            PaletteRow("Table Styles", LucideIcons.TableProperties.glyph(), onClick = { vm.pushPanel("tableStyles") }, chevron = true)
        }
        "Shape" -> Scrolling {
            // as the phone app's Shape tab: Fill and Outline put on the colour chosen last (the bar
            // under the icon), their arrows open the colours; Size opens the height and width. A
            // line or connector keeps its outline and has no fill.
            val line = vm.selectedObject?.isLine == true
            PaletteRow("Insert", LucideIcons.Shapes.glyph(), onClick = { vm.pushPanel("shapes") }, chevron = true)
            Band()
            PaletteRow("Change Shape", LucideIcons.Shapes.glyph(), onClick = { vm.pushPanel("changeShape") }, chevron = true, enabled = !line)
            Band()
            // the left part applies the latest style, the arrow opens the gallery
            PaletteRow("Shape Styles", Glyph.Letters("Abc"), onClick = { vm.recentShapeStyles.firstOrNull()?.let(vm::applyShapeStyle) }, onOpen = { vm.pushPanel("shapeStyles") })
            Band()
            PaletteRow(
                "Fill",
                Glyph.WithBar(LucideIcons.PaintBucket.glyph(), if (line) GridColors.Disabled else Color(vm.shapeFillChoice?.second ?: 0xFFED7D31.toInt())),
                onClick = vm::applyLastShapeFill,
                onOpen = { vm.pushPanel("shapeFill") },
                enabled = !line,
            )
            PaletteRow("Outline", Glyph.WithBar(LucideIcons.Pencil.glyph(), Color(vm.shapeOutlineChoice?.second ?: 0xFF4472C4.toInt())), onClick = vm::applyLastShapeOutline, onOpen = { vm.pushPanel("shapeOutline") })
            Band()
            PaletteRow("Arrange", LucideIcons.Layers.glyph(), onClick = { vm.pushPanel("arrange") }, chevron = true, enabled = vm.objects.size > 1)
            Band()
            PaletteRow("Size", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("shapeSize") }, chevron = true)
        }
        "Graphics" -> Scrolling {
            PaletteRow("Fill", LucideIcons.Palette.glyph(), onClick = { vm.pushPanel("shapeFill") }, chevron = true)
            PaletteRow("Outline", LucideIcons.SquareDashed.glyph(), onClick = { vm.pushPanel("shapeOutline") }, chevron = true)
            Band()
            ObjectRows(vm)
        }
        "Pivot Table" -> Scrolling {
            PaletteRow("Layout Options", LucideIcons.LayoutTemplate.glyph(), onClick = { vm.pushPanel("pivotLayout") }, chevron = true)
            PaletteRow("Quick Styles", LucideIcons.Paintbrush.glyph(), onClick = { vm.pushPanel("pivotStyles") }, chevron = true)
            Band()
            PaletteRow("Refresh Data", null, onClick = { vm.command("pivot.refresh") })
        }
        "Picture" -> Scrolling {
            // as the phone app's Picture tab: Styles, Picture Effects | Arrange | Crop Picture (the
            // left part crops on the picture, the arrow its positions) | Change Shape (the
            // outline it is cut to) | Size, Alt Text | Reset
            PaletteRow("Styles", LucideIcons.SwatchBook.glyph(), onClick = { vm.pushPanel("pictureStyles") }, chevron = true)
            PaletteRow("Picture Effects", LucideIcons.WandSparkles.glyph(), onClick = { vm.pushPanel("pictureEffects") }, chevron = true)
            Band()
            PaletteRow("Arrange", LucideIcons.Layers.glyph(), onClick = { vm.pushPanel("arrange") }, chevron = true, enabled = vm.objects.size > 1)
            Band()
            PaletteRow("Crop Picture", LucideIcons.Crop.glyph(), onClick = vm::toggleCrop, pressed = vm.cropping, onOpen = { vm.pushPanel("cropPicture") })
            Band()
            PaletteRow("Change Shape", LucideIcons.Shapes.glyph(), onClick = { vm.pushPanel("changeShape") }, chevron = true)
            Band()
            PaletteRow("Size", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("shapeSize") }, chevron = true)
            PaletteRow("Alt Text", LucideIcons.ScanText.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
            Band()
            PaletteRow("Reset", null, onClick = { vm.pushPanel("pictureReset") }, chevron = true)
        }
        else -> return false
    }
    return true
}

/** What every object's tab ends with: Arrange (greyed for the sheet's only object, as the phone app greys it), then its Height and Width. */
@Composable
private fun ObjectRows(vm: AppViewModel) {
    PaletteRow("Arrange", LucideIcons.Layers.glyph(), onClick = { vm.pushPanel("arrange") }, chevron = true, enabled = vm.objects.size > 1)
    Band()
    PaletteRow("Size", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("shapeSize") }, chevron = true)
    PaletteRow("Alt Text", LucideIcons.ScanText.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
}

@Composable
private fun ContextPanel(vm: AppViewModel, panel: String): Boolean {
    when (panel) {
        "pivotLayout" -> Scrolling {
            val pv = vm.pivotInfo
            SectionBand("Grand Totals")
            ChoiceRow("Rows", pv?.grandTotalsRows == true, onClick = { vm.command("pivot.grandTotals", json("rows" to (pv?.grandTotalsRows != true))) })
            ChoiceRow("Columns", pv?.grandTotalsCols == true, onClick = { vm.command("pivot.grandTotals", json("cols" to (pv?.grandTotalsCols != true))) })
            SectionBand("Subtotals")
            for ((label, mode) in listOf("None" to "none", "Top" to "top", "Bottom" to "bottom")) {
                ChoiceRow(label, pv?.subtotals == mode, onClick = { vm.command("pivot.subtotals", json("mode" to mode)) })
            }
            SectionBand("Report Layout")
            for ((label, layout) in listOf("Show in Compact Form" to "compact", "Show in Outline Form" to "outline", "Show in Tabular Form" to "tabular")) {
                ChoiceRow(label, pv?.layout == layout, onClick = { vm.command("pivot.reportLayout", json("layout" to layout)) })
            }
            SectionBand("Style Options")
            ChoiceRow("Headers", pv?.showHeaders == true, onClick = { vm.command("pivot.layout", json("showHeaders" to (pv?.showHeaders != true))) })
        }
        "pivotStyles" -> Scrolling {
            val current = vm.pivotInfo?.style
            for (kind in listOf("Light", "Medium", "Dark")) {
                SectionBand(kind)
                for (n in 1..28) {
                    val name = "PivotStyle$kind$n"
                    ChoiceRow("$kind $n", current == name, onClick = { vm.command("pivot.style", json("style" to name)) })
                }
            }
        }
        "pictureStyles" -> PictureStyleGallery(vm)
        // as the phone app's: Shadow and Reflection, each a gallery
        "pictureEffects" -> Scrolling {
            PaletteRow("Shadow", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("pictureShadow") }, chevron = true)
            PaletteRow("Reflection", LucideIcons.FlipVertical2.glyph(), onClick = { vm.pushPanel("pictureReflection") }, chevron = true)
        }
        "pictureShadow" -> EffectGallery(vm, reflection = false)
        "pictureReflection" -> EffectGallery(vm, reflection = true)
        "pictureReset" -> Scrolling {
            ChoiceRow("Reset Picture", false, onClick = { vm.resetPicture() })
            ChoiceRow("Reset Size", false, onClick = { vm.resetPictureSize() })
        }
        "arrange" -> Scrolling {
            ChoiceRow("Bring Forward", false, onClick = { vm.arrangeObject(forward = true, all = false) })
            ChoiceRow("Bring to Front", false, onClick = { vm.arrangeObject(forward = true, all = true) })
            ChoiceRow("Send Backward", false, onClick = { vm.arrangeObject(forward = false, all = false) })
            ChoiceRow("Send to Back", false, onClick = { vm.arrangeObject(forward = false, all = true) })
            // charts don't turn
            if (vm.selectedObject?.kind != "chart") {
                SectionBand("Rotate")
                ChoiceRow("Rotate Right 90°", false, onClick = { vm.rotateObject(90.0, null) })
                ChoiceRow("Rotate Left 90°", false, onClick = { vm.rotateObject(-90.0, null) })
                ChoiceRow("Flip Vertical", vm.selectedObject?.flipV == true, onClick = { vm.rotateObject(0.0, "vertical") })
                ChoiceRow("Flip Horizontal", vm.selectedObject?.flipH == true, onClick = { vm.rotateObject(0.0, "horizontal") })
            }
        }
        // as the phone app's Type: Recommended, then Insert › Chart's families
        "chartType" -> ChartFamiliesPanel(vm, change = true)
        "chartTypeRecommended" -> Scrolling {
            LaunchedEffect(vm.chartInfo?.source) { vm.loadChartRecommendations(vm.chartInfo?.source.orEmpty()) }
            ChartTiles(vm, vm.chartRecommendations.map { (label, type, sub) -> label to "$type|$sub" }, change = true)
        }
        "tableStyles" -> TableStylesPanel(vm)
        "tableAltText" -> TableAltTextPanel(vm)
        "chartStyle" -> ChartStylesPanel(vm)
        "shapeFill" -> ColorGridPanel(vm, "No Fill") { c -> vm.setShapeFill(c) }
        "shapeOutline" -> ColorGridPanel(vm, "No Outline") { c -> vm.setShapeOutline(c) }
        "shapeSize" -> ObjectSizePanel(vm)
        "altText" -> AltTextPanel(vm)
        "cropPicture" -> Scrolling {
            PaletteRow("Picture Position", null, onClick = { vm.pushPanel("picturePosition") }, chevron = true)
            PaletteRow("Crop Position", null, onClick = { vm.pushPanel("cropPosition") }, chevron = true)
        }
        "picturePosition" -> PicturePositionPanel(vm)
        "cropPosition" -> CropPositionPanel(vm)
        "shapeStyles" -> ShapeStylesPanel(vm)
        "chartLayout" -> ChartLayoutsPanel(vm)
        "chartElements" -> ChartElementsMenu(vm)
        "chartColors" -> ChartColorsPanel(vm)
        else -> if (panel.startsWith(CHART_ELEMENT)) ChartElementPanel(vm, panel.removePrefix(CHART_ELEMENT)) else return false
    }
    return true
}

/** The Elements submenus' panels: `chartElement:axes` and so on. */
private const val CHART_ELEMENT = "chartElement:"

/**
 * Chart › Elements, as the phone app's menu: a row for each kind of element the chart can have,
 * each opening its choices.
 */
@Composable
private fun ChartElementsMenu(vm: AppViewModel) {
    LaunchedEffect(vm.selectedObject?.id) { vm.loadChartInfo() }
    val ci = vm.chartInfo
    val kind = ci?.kind.orEmpty()
    val axes = ci?.hasAxes != false
    val lineLike = kind.startsWith("Line")
    Scrolling {
        fun row(label: String, glyph: Glyph, key: String) = @Composable { PaletteRow(label, glyph, onClick = { vm.pushPanel(CHART_ELEMENT + key) }, chevron = true) }
        val rows = buildList {
            if (axes) add(row("Axes", LucideIcons.Axis3d.glyph(), "axes"))
            if (axes) add(row("Axis Titles", LucideIcons.Type.glyph(), "axisTitles"))
            add(row("Chart Title", LucideIcons.Heading.glyph(), "title"))
            add(row("Data Labels", LucideIcons.Tag.glyph(), "labels"))
            if (kind.startsWith("Column") || lineLike || kind.startsWith("Area")) add(row("Data Table", LucideIcons.Table2.glyph(), "table"))
            if (kind.startsWith("Column") || kind.startsWith("Bar") || lineLike || kind.startsWith("Scatter")) add(row("Error Bars", LucideIcons.ChartCandlestick.glyph(), "errorBars"))
            if (axes) add(row("Gridlines", LucideIcons.Grid3x3.glyph(), "gridlines"))
            add(row("Legend", LucideIcons.List.glyph(), "legend"))
            if (lineLike || kind.startsWith("Area")) add(row("Lines", LucideIcons.ChartLine.glyph(), "lines"))
            if (axes) add(row("Trendline", LucideIcons.TrendingUp.glyph(), "trendline"))
            if (lineLike) add(row("Up/Down Bars", LucideIcons.ChartCandlestick.glyph(), "upDownBars"))
        }
        for (r in rows) r()
    }
}

/** One of the Elements submenus: its choices with the current ones lit (the axes and gridlines are switches). */
@Composable
private fun ChartElementPanel(vm: AppViewModel, which: String) {
    LaunchedEffect(vm.selectedObject?.id) { vm.loadChartInfo() }
    val ci = vm.chartInfo
    val kind = ci?.kind.orEmpty()
    fun set(element: String, on: Boolean, extra: org.json.JSONObject.() -> Unit = {}) =
        vm.chartCommand("chart.addElement", org.json.JSONObject().put("element", element).put("on", on).apply(extra))
    @Composable
    fun choice(label: String, glyph: Glyph, on: Boolean, onClick: () -> Unit) = PaletteRow(label, glyph, onClick = onClick, pressed = on)
    // the horizontal axis is a bar chart's value axis, any other chart's category (or X) axis
    val bar = ci?.horizontal == true
    val (hAxis, vAxis) = if (bar) "value" to "category" else "category" to "value"
    fun shown(axis: String) = if (axis == "category") ci?.catAxis != false else ci?.valAxis != false
    fun titled(axis: String) = if (axis == "category") ci?.catAxisTitle == true else ci?.valAxisTitle == true
    val none = LucideIcons.X.glyph()
    Scrolling {
        when (which) {
            "axes" -> {
                choice("Primary Horizontal", LucideIcons.MoveHorizontal.glyph(), shown(hAxis)) { set("axis", !shown(hAxis)) { put("which", hAxis) } }
                choice("Primary Vertical", LucideIcons.MoveVertical.glyph(), shown(vAxis)) { set("axis", !shown(vAxis)) { put("which", vAxis) } }
            }
            "axisTitles" -> {
                choice("Primary Horizontal", LucideIcons.MoveHorizontal.glyph(), titled(hAxis)) { set("axisTitles", !titled(hAxis)) { put("which", hAxis) } }
                choice("Primary Vertical", LucideIcons.MoveVertical.glyph(), titled(vAxis)) { set("axisTitles", !titled(vAxis)) { put("which", vAxis) } }
            }
            "title" -> {
                choice("None", none, ci?.title == false) { set("title", false) }
                choice("Above Chart", LucideIcons.PanelTop.glyph(), ci?.title == true && ci.titleOverlay != true) { set("title", true) { put("position", "above") } }
                choice("Centered Overlay", LucideIcons.Heading.glyph(), ci?.title == true && ci.titleOverlay == true) { set("title", true) { put("position", "overlay") } }
            }
            "labels" -> {
                choice("None", none, ci?.dataLabels == false) { set("dataLabels", false) }
                val positions = labelPositions(kind)
                if (positions.isEmpty()) {
                    choice("Show", LucideIcons.Tag.glyph(), ci?.dataLabels == true) { set("dataLabels", true) }
                } else {
                    val auto = autoLabelPosition(kind)
                    for ((label, pos) in positions) {
                        val on = ci?.dataLabels == true && (ci.labelPosition == pos || (ci.labelPosition == "auto" && pos == auto))
                        choice(label, LucideIcons.Tag.glyph(), on) { set("dataLabels", true) { put("position", pos) } }
                    }
                }
            }
            "table" -> {
                choice("None", none, ci?.dataTable == false) { set("dataTable", false) }
                choice("With Legend Keys", LucideIcons.Table2.glyph(), ci?.dataTable == true && ci.dataTableKeys) { set("dataTable", true) { put("keys", true) } }
                choice("No Legend Keys", LucideIcons.Table.glyph(), ci?.dataTable == true && !ci.dataTableKeys) { set("dataTable", true) { put("keys", false) } }
            }
            "errorBars" -> for ((label, type) in listOf("None" to "none", "Standard Error" to "standardError", "Percentage" to "percentage", "Standard Deviation" to "standardDeviation")) {
                choice(label, if (type == "none") none else LucideIcons.ChartCandlestick.glyph(), ci?.errorBars == type) {
                    if (type == "none") set("errorBars", false) else set("errorBars", true) { put("type", type) }
                }
            }
            "gridlines" -> {
                // horizontal lines run across the value axis of a column chart, across the categories of a bar chart
                val (hMajor, vMajor) = if (bar) "majorCategory" to "majorValue" else "majorValue" to "majorCategory"
                val (hMinor, vMinor) = if (bar) "minorCategory" to "minorValue" else "minorValue" to "minorCategory"
                fun lit(w: String) = when (w) {
                    "majorValue" -> ci?.gridlines == true
                    "majorCategory" -> ci?.catGridlines == true
                    "minorValue" -> ci?.minorValGridlines == true
                    else -> ci?.minorCatGridlines == true
                }
                for ((label, w) in listOf("Primary Major Horizontal" to hMajor, "Primary Major Vertical" to vMajor, "Primary Minor Horizontal" to hMinor, "Primary Minor Vertical" to vMinor)) {
                    val glyph = if (label.endsWith("Horizontal")) LucideIcons.Rows3.glyph() else LucideIcons.Columns3.glyph()
                    choice(label, glyph, lit(w)) { set("gridlines", !lit(w)) { put("which", w) } }
                }
            }
            "legend" -> for ((label, pos) in listOf("None" to "none", "Right" to "right", "Top" to "top", "Left" to "left", "Bottom" to "bottom")) {
                val glyph = when (pos) {
                    "none" -> none
                    "right" -> LucideIcons.PanelRight.glyph()
                    "top" -> LucideIcons.PanelTop.glyph()
                    "left" -> LucideIcons.PanelLeft.glyph()
                    else -> LucideIcons.PanelBottom.glyph()
                }
                choice(label, glyph, ci?.legend == pos) { if (pos == "none") set("legend", false) else set("legend", true) { put("position", pos) } }
            }
            "lines" -> {
                val lines = listOf("None" to "none", "Drop Lines" to "drop") + if (kind.startsWith("Line")) listOf("High-Low Lines" to "highLow") else emptyList()
                for ((label, type) in lines) {
                    choice(label, if (type == "none") none else LucideIcons.ChartLine.glyph(), ci?.lines == type) {
                        if (type == "none") set("lines", false) else set("lines", true) { put("type", type) }
                    }
                }
            }
            "trendline" -> for ((label, type) in listOf("None" to "none", "Linear" to "linear", "Exponential" to "exponential", "Linear Forecast" to "linearForecast", "Moving Average" to "movingAverage")) {
                val glyph = when (type) {
                    "none" -> none
                    "exponential" -> LucideIcons.ChartSpline.glyph()
                    "movingAverage" -> LucideIcons.Activity.glyph()
                    else -> LucideIcons.TrendingUp.glyph()
                }
                choice(label, glyph, ci?.trendline == type) {
                    if (type == "none") set("trendline", false) else set("trendline", true) { put("type", type); put("replace", true) }
                }
            }
            "upDownBars" -> {
                choice("None", none, ci?.upDownBars == false) { set("upDownBars", false) }
                choice("Up/Down Bars", LucideIcons.ChartCandlestick.glyph(), ci?.upDownBars == true) { set("upDownBars", true) }
            }
        }
    }
}

/** Data label positions a chart of this kind offers, in the phone app's order; none for a kind without. */
private fun labelPositions(kind: String): List<Pair<String, String>> = when (kind) {
    "ColumnClustered", "BarClustered" -> listOf("Center" to "center", "Inside End" to "insideEnd", "Inside Base" to "insideBase", "Outside End" to "outsideEnd")
    "ColumnStacked", "ColumnStacked100", "BarStacked", "BarStacked100" -> listOf("Center" to "center", "Inside End" to "insideEnd", "Inside Base" to "insideBase")
    "Line", "LineMarkers", "LineStacked", "Scatter", "ScatterLines", "Bubble" ->
        listOf("Center" to "center", "Left" to "left", "Right" to "right", "Above" to "above", "Below" to "below")
    "Pie" -> listOf("Center" to "center", "Inside End" to "insideEnd", "Outside End" to "outsideEnd", "Best Fit" to "bestFit")
    else -> emptyList()
}

/** Where a kind's labels go when no position is set: lit as that choice. */
private fun autoLabelPosition(kind: String): String = when (kind) {
    "ColumnClustered", "BarClustered" -> "outsideEnd"
    "ColumnStacked", "ColumnStacked100", "BarStacked", "BarStacked100" -> "center"
    "Pie" -> "bestFit"
    else -> "above"
}

