package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.ui.res.stringResource
import io.github.artcraftandroid.gridcraft.R
import io.github.artcraftandroid.gridcraft.ui.uiText
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
import androidx.compose.foundation.layout.heightIn
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
    "tableInsert" to "Insert",
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
    "tableDelete" to "Delete",
    "tableOptions" to "Style Options",
    "tableStyles" to "Table Styles",
    "chartType" to "Type",
    "chartLayout" to "Layout",
    "chartElements" to "Elements",
    "chartColors" to "Colors",
    "arrange" to "Arrange",
    "pictures" to "Pictures",
    "recommendedCharts" to "Recommended",
    "formatAsTable" to "Format as Table",
    "tabColor" to "Tab Color",
    "pictureStyles" to "Styles",
    "pictureEffects" to "Picture Effects",
    "penColors" to "More Colors",
    "pen" to "Pen",
    "addPen" to "Add Pen",
    "accountingFormats" to "Accounting",
    "borderStyle" to "Line Style",
    "borderColor" to "Line Color",
    "pictureReset" to "Reset",
    "pivotLayout" to "Layout Options",
    "outlineSettings" to "Outline Settings",
    "pivotOptions" to "PivotTable Options",
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

private fun sizeLabel(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else s.toString()

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
                Text(f?.font?.let { if (body != null) stringResource(R.string.body_font, it) else it } ?: "Font", color = GridColors.OnSurface, fontSize = 16.sp, maxLines = 1)
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
        PaletteRow("Format as Table", LucideIcons.TableProperties.glyph(), onClick = { vm.pushPanel("formatAsTable") })
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
        PaletteRow("Table", LucideIcons.Table.glyph(), onClick = {
            vm.collapsePalette()
            vm.proposeTable()
        })
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
                Text(uiText("Add"), color = GridColors.OnSurface, fontSize = 16.sp)
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
        PaletteRow("Paste Names", LucideIcons.ClipboardPaste.glyph(), onClick = vm::showPasteNames, chevron = true)
        PaletteRow("Calculate Now", LucideIcons.Calculator.glyph(), onClick = { vm.command("formulas.calculateNow") })
    }
}

@Composable
private fun DataTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Convert to Number", LucideIcons.Hash.glyph(), onClick = { vm.command("formulas.convertToNumber") })
        PaletteRow("Outline Settings", LucideIcons.Settings.glyph(), onClick = { vm.pushPanel("outlineSettings") }, chevron = true)
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
        Text(uiText("Sheet View"), color = GridColors.Disabled, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
        // the current view's name, at the left edge like a field
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().height(ROW + 1.dp).background(GridColors.Surface).bottomEdge(GridColors.Divider).padding(start = 16.dp, end = 15.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(uiText("Default"), color = GridColors.Disabled, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
        CheckPaletteRow("Show Objects", sv?.showObjects != false) { vm.command("view.showObjects", json("on" to (sv?.showObjects == false))) }
        CheckPaletteRow("Show Outline Symbols", sv?.showOutlineSymbols != false) { vm.command("view.showOutlineSymbols", json("on" to (sv?.showOutlineSymbols == false))) }
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
internal fun CheckPaletteRow(label: String, checked: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().heightIn(min = ROW + 1.dp).background(GridColors.Surface).bottomEdge(GridColors.Divider).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).background(if (checked) GridColors.OnHeader else GridColors.Pressed, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { if (checked) GlyphIcon(LucideIcons.Check.glyph(), GridColors.Surface, size = 18.dp) }
        androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        Text(uiText(label), color = GridColors.OnSurface, fontSize = 16.sp, maxLines = 2, modifier = Modifier.weight(1f))
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
                Text(uiText("No items available."), color = GridColors.OnSurface, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
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
                Text(uiText("Select the data to chart."), color = GridColors.OnSurface, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
            }
            ChartTiles(vm, vm.chartRecommendations.map { (label, type, sub) -> label to "$type|$sub" })
        }
        "formatAsTable" -> TableStyleGallery(vm, current = null) { style -> vm.formatAsTable(style) }
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
            PaletteRow("Insert", LucideIcons.Plus.glyph(), onClick = { vm.pushPanel("tableInsert") }, chevron = true)
            PaletteRow("Delete", LucideIcons.Minus.glyph(), onClick = { vm.pushPanel("tableDelete") }, chevron = true)
            Band()
            PaletteRow("Style Options", null, onClick = { vm.pushPanel("tableOptions") }, chevron = true)
            Band()
            PaletteRow("Convert to Range", LucideIcons.Table.glyph(), onClick = { vm.command("table.convertToRange") })
            Band()
            PaletteRow("Table Styles", LucideIcons.Paintbrush.glyph(), onClick = { vm.pushPanel("tableStyles") }, chevron = true)
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
            PaletteRow("Arrange", LucideIcons.Layers.glyph(), onClick = { vm.pushPanel("arrange") }, chevron = true)
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
            LaunchedEffect(vm.info?.revision, vm.selection?.activeRow, vm.selection?.activeCol) { vm.loadPivotDetailsAvailability() }
            PaletteRow("Show Details", LucideIcons.Table.glyph(), onClick = vm::showPivotDetails, enabled = vm.pivotInfo?.enableShowDetails == true && vm.pivotDetailsAvailable)
            PaletteRow("PivotTable Options", LucideIcons.Settings.glyph(), onClick = { vm.pushPanel("pivotOptions") }, chevron = true)
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
            PaletteRow("Arrange", LucideIcons.Layers.glyph(), onClick = { vm.pushPanel("arrange") }, chevron = true)
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

/** What every object's tab ends with: Arrange, then its Height and Width. */
@Composable
private fun ObjectRows(vm: AppViewModel) {
    PaletteRow("Arrange", LucideIcons.Layers.glyph(), onClick = { vm.pushPanel("arrange") }, chevron = true)
    Band()
    PaletteRow("Size", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("shapeSize") }, chevron = true)
    PaletteRow("Alt Text", LucideIcons.ScanText.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
}

@Composable
private fun ContextPanel(vm: AppViewModel, panel: String): Boolean {
    when (panel) {
        "outlineSettings" -> Scrolling {
            val view = vm.sheetView
            CheckPaletteRow("Summary Rows Below Detail", view?.summaryBelow != false) { vm.command("data.outlineSettings", json("summaryBelow" to (view?.summaryBelow == false))) }
            CheckPaletteRow("Summary Columns Right of Detail", view?.summaryRight != false) { vm.command("data.outlineSettings", json("summaryRight" to (view?.summaryRight == false))) }
        }
        "pivotOptions" -> Scrolling {
            val pivot = vm.pivotInfo
            if (pivot != null) CheckPaletteRow("Enable Show Details", pivot.enableShowDetails) {
                vm.command("pivot.options", json("enableShowDetails" to !pivot.enableShowDetails))
            }
        }
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
        "pictureStyles" -> Scrolling {
            for ((label, preset) in listOf(
                "None" to "none", "White Frame" to "whiteFrame", "Black Frame" to "blackFrame", "Shadow" to "shadow",
                "Rounded" to "rounded", "Rounded with Shadow" to "roundedShadow", "Oval" to "oval",
            )) ChoiceRow(label, false, onClick = { vm.stylePicture(org.json.JSONObject().put("preset", preset)) })
        }
        "pictureEffects" -> Scrolling {
            val on = vm.selectedObject?.shadow == true
            SectionBand("Shadow")
            ChoiceRow("No Shadow", !on, onClick = { vm.stylePicture(org.json.JSONObject().put("shadow", false)) })
            ChoiceRow("Drop Shadow", on, onClick = { vm.stylePicture(org.json.JSONObject().put("shadow", true)) })
        }
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
        "chartType" -> Scrolling {
            for ((label, spec) in listOf(
                "Clustered Column" to "column|", "Stacked Column" to "column|stacked", "100% Stacked Column" to "column|stacked100",
                "Clustered Bar" to "bar|", "Stacked Bar" to "bar|stacked", "100% Stacked Bar" to "bar|stacked100",
                "Line" to "line|", "Stacked Line" to "line|stacked", "100% Stacked Line" to "line|stacked100", "Line with Markers" to "line|markers",
                "Stacked Line with Markers" to "line|stackedMarkers", "100% Stacked Line with Markers" to "line|stacked100Markers",
                "Pie" to "pie|", "Pie of Pie" to "pie|pieOfPie", "Bar of Pie" to "pie|barOfPie", "Doughnut" to "doughnut|", "Area" to "area|", "Stacked Area" to "area|stacked", "100% Stacked Area" to "area|stacked100",
                "Scatter" to "scatter|", "Scatter with Straight Lines and Markers" to "scatter|lines", "Scatter with Straight Lines" to "scatter|linesOnly",
                "Bubble" to "bubble|", "Radar" to "radar|", "Radar with Markers" to "radar|markers", "Filled Radar" to "radar|filled",
            )) {
                ChoiceRow(label, false, onClick = {
                    val (t, sub) = spec.split('|')
                    val p = org.json.JSONObject().put("type", t)
                    if (sub.isNotEmpty()) p.put("subtype", sub)
                    vm.chartCommand("chart.changeType", p)
                })
            }
        }
        "tableInsert" -> Scrolling {
            ChoiceRow("Insert Rows Above", false, onClick = { vm.tableInsert("above") })
            ChoiceRow("Insert Rows Below", false, onClick = { vm.tableInsert("below") })
            ChoiceRow("Insert Columns to the Left", false, onClick = { vm.tableInsert("left") })
            ChoiceRow("Insert Columns to the Right", false, onClick = { vm.tableInsert("right") })
        }
        "tableDelete" -> Scrolling {
            ChoiceRow("Delete Rows", false, onClick = { vm.tableInsert("deleteRows") })
            ChoiceRow("Delete Columns", false, onClick = { vm.tableInsert("deleteColumns") })
            ChoiceRow("Delete Table", false, onClick = { vm.command("table.delete") })
        }
        "tableOptions" -> Scrolling {
            val t = vm.tableInfo
            for ((label, pair) in listOf(
                "Header Row" to ("table.headerRow" to (t?.headerRow == true)),
                "Totals Row" to ("table.totalRow" to (t?.totalsRow == true)),
                "First Column" to ("table.firstColumn" to (t?.firstCol == true)),
                "Last Column" to ("table.lastColumn" to (t?.lastCol == true)),
                "Banded Rows" to ("table.bandedRows" to (t?.bandedRows == true)),
                "Banded Columns" to ("table.bandedColumns" to (t?.bandedCols == true)),
            )) {
                val (id, on) = pair
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable { vm.command(id, json("on" to !on)) },
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(on, onCheckedChange = { vm.command(id, json("on" to it)) }, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = GridColors.Accent))
                    Text(uiText(label), color = GridColors.OnSurface, fontSize = 16.sp)
                }
            }
        }
        "tableStyles" -> TableStyleGallery(vm, current = vm.tableInfo?.style) { style -> vm.command("table.style", json("style" to style)) }
        "chartStyle" -> Scrolling {
            for (n in 1..8) ChoiceRow(stringResource(R.string.chart_style_number, n), false, onClick = { vm.chartCommand("chart.set", org.json.JSONObject().put("style", n)) })
        }
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
        "chartLayout" -> Scrolling {
            for (n in 1..6) ChoiceRow(stringResource(R.string.chart_layout_number, n), false, onClick = { vm.chartCommand("chart.quickLayout", org.json.JSONObject().put("layout", n)) })
        }
        "chartElements" -> Scrolling {
            LaunchedEffect(vm.selectedObject?.id) { vm.loadChartInfo() }
            val ci = vm.chartInfo
            fun set(element: String, on: Boolean, extra: org.json.JSONObject.() -> Unit = {}) =
                vm.chartCommand("chart.addElement", org.json.JSONObject().put("element", element).put("on", on).apply(extra))
            SectionBand("Axis Titles")
            ChoiceRow("Primary Horizontal and Vertical", ci?.axisTitles == true, onClick = { set("axisTitles", ci?.axisTitles != true) })
            val kind = ci?.kind.orEmpty()
            val lineLike = kind.startsWith("Line")
            SectionBand("Chart Title")
            ChoiceRow("None", ci?.title == false, onClick = { set("title", false) })
            ChoiceRow("Above Chart", ci?.title == true && ci.titleOverlay != true, onClick = { set("title", true) { put("position", "above") } })
            ChoiceRow("Centered Overlay", ci?.title == true && ci.titleOverlay == true, onClick = { set("title", true) { put("position", "overlay") } })
            SectionBand("Data Labels")
            ChoiceRow("None", ci?.dataLabels == false, onClick = { set("dataLabels", false) })
            val positions = labelPositions(kind)
            if (positions.isEmpty()) {
                ChoiceRow("Show", ci?.dataLabels == true, onClick = { set("dataLabels", true) })
            } else {
                for ((label, pos) in positions) {
                    // automatic is the kind's usual place, listed first
                    val on = ci?.dataLabels == true && (ci.labelPosition == pos || (ci.labelPosition == "auto" && pos == positions.first().second))
                    ChoiceRow(label, on, onClick = { set("dataLabels", true) { put("position", pos) } })
                }
            }
            if (kind.startsWith("Column") || lineLike || kind.startsWith("Area")) {
                SectionBand("Data Table")
                ChoiceRow("None", ci?.dataTable == false, onClick = { set("dataTable", false) })
                ChoiceRow("With Legend Keys", ci?.dataTable == true, onClick = { set("dataTable", true) })
            }
            if (kind.startsWith("Column") || kind.startsWith("Bar") || lineLike || kind.startsWith("Scatter")) {
                SectionBand("Error Bars")
                for ((label, type) in listOf("None" to "none", "Standard Error" to "standardError", "Percentage" to "percentage", "Standard Deviation" to "standardDeviation")) {
                    ChoiceRow(label, ci?.errorBars == type, onClick = { if (type == "none") set("errorBars", false) else set("errorBars", true) { put("type", type) } })
                }
            }
            SectionBand("Gridlines")
            ChoiceRow("Primary Major Horizontal", ci?.gridlines == true, onClick = { set("gridlines", ci?.gridlines != true) })
            SectionBand("Legend")
            for ((label, pos) in listOf("None" to "none", "Right" to "right", "Top" to "top", "Left" to "left", "Bottom" to "bottom")) {
                ChoiceRow(label, ci?.legend == pos, onClick = { if (pos == "none") set("legend", false) else set("legend", true) { put("position", pos) } })
            }
            if (lineLike || kind.startsWith("Area")) {
                SectionBand("Lines")
                val lines = listOf("None" to "none", "Drop Lines" to "drop") + if (lineLike) listOf("High-Low Lines" to "highLow") else emptyList()
                for ((label, type) in lines) {
                    ChoiceRow(label, ci?.lines == type, onClick = { if (type == "none") set("lines", false) else set("lines", true) { put("type", type) } })
                }
            }
            SectionBand("Trendline")
            for ((label, type) in listOf("None" to "none", "Linear" to "linear", "Exponential" to "exponential", "Linear Forecast" to "linearForecast", "Moving Average" to "movingAverage")) {
                ChoiceRow(label, ci?.trendline == type, onClick = {
                    if (type == "none") set("trendline", false) else set("trendline", true) { put("type", type); put("replace", true) }
                })
            }
            if (lineLike) {
                SectionBand("Up/Down Bars")
                ChoiceRow("None", ci?.upDownBars == false, onClick = { set("upDownBars", false) })
                ChoiceRow("Up/Down Bars", ci?.upDownBars == true, onClick = { set("upDownBars", true) })
            }
        }
        "chartColors" -> Scrolling {
            for ((label, palette) in listOf("Colorful" to "colorful", "Monochromatic" to "monochrome") + (1..6).map { "Accent $it" to "accent$it" }) {
                ChoiceRow(label, false, onClick = { vm.chartCommand("chart.changeColors", org.json.JSONObject().put("palette", palette)) })
            }
        }
        else -> return false
    }
    return true
}

/** Data label positions a chart of this kind offers, its automatic one first; none for a kind without. */
private fun labelPositions(kind: String): List<Pair<String, String>> = when (kind) {
    "ColumnClustered", "BarClustered" -> listOf("Outside End" to "outsideEnd", "Center" to "center", "Inside End" to "insideEnd", "Inside Base" to "insideBase")
    "ColumnStacked", "ColumnStacked100", "BarStacked", "BarStacked100" -> listOf("Center" to "center", "Inside End" to "insideEnd", "Inside Base" to "insideBase")
    "Line", "LineMarkers", "LineStacked", "Scatter", "ScatterLines", "Bubble" ->
        listOf("Above" to "above", "Center" to "center", "Left" to "left", "Right" to "right", "Below" to "below")
    "Pie" -> listOf("Center" to "center", "Inside End" to "insideEnd", "Outside End" to "outsideEnd", "Best Fit" to "bestFit")
    else -> emptyList()
}

/** The table styles as small tables of their colours, three to a row; the current one marked. */
@Composable
private fun TableStyleGallery(vm: AppViewModel, current: String?, apply: (String) -> Unit) {
    LaunchedEffect(Unit) { vm.loadTableStyles() }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize().padding(8.dp),
    ) {
        items(vm.tableStyles.size) { i ->
            val st = vm.tableStyles[i]
            androidx.compose.foundation.layout.Box(
                Modifier.padding(6.dp).height(64.dp)
                    .then(if (st.name == current) Modifier.border(2.dp, GridColors.Accent) else Modifier)
                    .clickable { apply(st.name) }
                    .padding(4.dp),
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val rows = 5
                    val h = size.height / rows
                    for (r in 0 until rows) {
                        val c = when {
                            r == 0 -> st.header
                            r % 2 == 1 -> st.band
                            else -> st.plain
                        }
                        drawRect(androidx.compose.ui.graphics.Color(c), androidx.compose.ui.geometry.Offset(0f, r * h), androidx.compose.ui.geometry.Size(size.width, h))
                        drawLine(androidx.compose.ui.graphics.Color(st.line), androidx.compose.ui.geometry.Offset(0f, (r + 1) * h), androidx.compose.ui.geometry.Offset(size.width, (r + 1) * h), 1f)
                    }
                    drawRect(androidx.compose.ui.graphics.Color(st.headerText), androidx.compose.ui.geometry.Offset(size.width * 0.1f, h * 0.35f), androidx.compose.ui.geometry.Size(size.width * 0.35f, h * 0.3f))
                }
            }
        }
    }
}
