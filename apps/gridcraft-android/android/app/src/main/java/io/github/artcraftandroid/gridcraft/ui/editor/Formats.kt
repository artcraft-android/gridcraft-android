package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.core.StyleTile
import io.github.artcraftandroid.gridcraft.core.TileLine
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import io.github.artcraftandroid.gridcraft.ui.LucideIcons
import org.json.JSONArray

/** A function's help page: this, then its name. */
internal const val FN_HELP = "fnHelp:"

// ---- Borders ----

/** The border presets, four to a row, as the phone app lays them out. */
private val BORDER_PRESETS = listOf(
    "All Borders" to "all", "Outside Borders" to "outside", "Thick Outside Borders" to "thickOutside", "Top and Bottom Border" to "topBottom",
    "Bottom Border" to "bottom", "Top Border" to "top", "Left Border" to "left", "Right Border" to "right",
    "Thick Bottom Border" to "thickBottom", "Top and Thick Bottom Border" to "topThickBottom", "Bottom Double Border" to "doubleBottom",
    "Top and Double Bottom Border" to "topDoubleBottom",
)

/** Borders: the presets as a grid of drawn icons, Clear All, then Line Style and Line Color. */
@Composable
internal fun BordersPanel(vm: AppViewModel) {
    Scrolling {
        for (row in BORDER_PRESETS.chunked(4)) {
            Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(GridColors.Divider)) {
                for ((label, preset) in row) {
                    Box(
                        Modifier.weight(1f).height(ROW).clickable { vm.applyBorder(preset) }.semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.Border(preset), GridColors.OnStrip, size = 26.dp)
                    }
                }
            }
        }
        PaletteRow("Clear All", Glyph.Border("none"), onClick = { vm.applyBorder("none") })
        PaletteRow("Line Style", Glyph.LineWeights, onClick = { vm.pushPanel("borderStyle") }, chevron = true)
        PaletteRow("Line Color", Glyph.WithBar(LucideIcons.Pencil.glyph(), vm.borderColorArgb?.let { Color(it) } ?: Color.Black), onClick = { vm.pushPanel("borderColor") }, chevron = true)
    }
}

/** The line styles in the phone app's order, No Border first. */
private val LINE_STYLES = listOf(
    "No Border" to "none", "Thin" to "thin", "Hair" to "hair", "Dotted" to "dotted", "Dashed" to "dashed", "Dash Dot" to "dashDot",
    "Dash Dot Dot" to "dashDotDot", "Double" to "double", "Medium" to "medium", "Medium Dashed" to "mediumDashed",
    "Medium Dash Dot" to "mediumDashDot", "Medium Dash Dot Dot" to "mediumDashDotDot", "Slant Dash Dot" to "slantDashDot", "Thick" to "thick",
)

/** Line Style: a white tile per style with the line drawn on it; the current one in an accent band. */
@Composable
internal fun LineStylePanel(vm: AppViewModel) {
    Scrolling {
        Spacer(Modifier.height(3.dp))
        for ((label, style) in LINE_STYLES) {
            val on = vm.borderStyle == style
            Box(
                Modifier.fillMaxWidth().height(41.dp).background(if (on) GridColors.ChosenBand else Color.Transparent)
                    .clickable {
                        vm.borderStyle = style
                        vm.popPanel()
                    }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 30.dp, vertical = 2.5.dp),
            ) {
                Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                    if (style == "none") {
                        Text(label, color = GridColors.LineInk, fontSize = 19.sp)
                    } else {
                        Canvas(Modifier.fillMaxWidth().height(12.dp)) { lineSample(style) }
                    }
                }
            }
        }
    }
}

/** A border style drawn across the tile, its dashes and weight much as a cell shows them. */
private fun DrawScope.lineSample(style: String) {
    val d = density
    val y = size.height / 2f
    val x0 = 0f
    val x1 = size.width
    fun line(width: Float, dash: FloatArray? = null, dy: Float = 0f, color: Color = GridColors.LineInk) =
        drawLine(color, Offset(x0, y + dy * d), Offset(x1, y + dy * d), width * d, pathEffect = dash?.let { PathEffect.dashPathEffect(FloatArray(it.size) { i -> it[i] * d }) })
    when (style) {
        "hair" -> line(0.7f, color = Color(0xFF8A8A8A))
        "dotted" -> line(1f, floatArrayOf(1f, 1.5f))
        "dashed" -> line(1f, floatArrayOf(4f, 2f))
        "dashDot" -> line(1f, floatArrayOf(7f, 2.5f, 2f, 2.5f))
        "dashDotDot" -> line(1f, floatArrayOf(7f, 2.5f, 2f, 2.5f, 2f, 2.5f))
        "double" -> {
            line(0.8f, dy = -1.5f)
            line(0.8f, dy = 1.5f)
        }
        "medium" -> line(2f)
        "mediumDashed" -> line(2f, floatArrayOf(7f, 3f))
        "mediumDashDot" -> line(2f, floatArrayOf(9f, 3f, 2.5f, 3f))
        "mediumDashDotDot" -> line(2f, floatArrayOf(9f, 3f, 2.5f, 3f, 2.5f, 3f))
        "slantDashDot" -> line(2.2f, floatArrayOf(9f, 1.5f, 3.5f, 1.5f))
        "thick" -> line(3f)
        else -> line(1f)
    }
}

// ---- Cell Styles ----

/** The cell styles the phone app offers, in its groups. */
private val CELL_STYLE_GROUPS = listOf(
    "Good, Bad and Neutral" to listOf("Normal", "Bad", "Good", "Neutral"),
    "Data and Model" to listOf("Calculation", "Check Cell", "Explanatory Text", "Input", "Linked Cell", "Note", "Output", "Warning Text"),
    "Titles and Headings" to listOf("Heading 1", "Heading 2", "Heading 3", "Heading 4", "Title", "Total"),
    "Themed Cell Styles" to listOf(20, 40, 60).flatMap { p -> (1..6).map { "$p% - Accent$it" } } + (1..6).map { "Accent$it" },
)

/** Cell Styles: a gallery of small tiles, each drawn in its style, six to a row. */
@Composable
internal fun CellStylesPanel(vm: AppViewModel) {
    var tiles by remember { mutableStateOf<Map<String, StyleTile>>(emptyMap()) }
    // again after a style is applied, so the band follows the cell
    LaunchedEffect(vm.format) {
        val e = vm.engine ?: return@LaunchedEffect
        tiles = e.view { cellStyleTiles(CELL_STYLE_GROUPS.flatMap { it.second }) }.associateBy { it.name }
    }
    Scrolling {
        for ((title, names) in CELL_STYLE_GROUPS) {
            SectionBand(title)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp)) {
                for (row in names.chunked(6)) {
                    Row(Modifier.fillMaxWidth().height(52.dp)) {
                        for (name in row) {
                            val t = tiles[name]
                            Box(
                                Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                                    // the active cell's style sits in a band
                                    .background(if (t?.chosen == true) GridColors.ChosenBand else Color.Transparent)
                                    .clickable { vm.command("home.cellStyle", json("name" to name)) }.semantics { contentDescription = name },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (t != null) StyleTileBox(t, tileLabel(name))
                            }
                        }
                        repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** What a tile says: a themed style its share of the accent, the others "Aa". */
private fun tileLabel(name: String): String = when {
    name.contains("% - Accent") -> name.substringBefore(" -")
    name.startsWith("Accent") -> "100%"
    else -> "Aa"
}

@Composable
private fun StyleTileBox(t: StyleTile, label: String) {
    Box(
        Modifier.size(49.dp, 36.dp).background(t.fill?.let { Color(it) } ?: Color.White).drawBehind {
            t.around?.let { boxLine(it) }
            t.top?.let { edgeLine(it, top = true) }
            t.bottom?.let { edgeLine(it, top = false) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color(t.text),
            fontSize = (13f * (t.size / 11f).coerceIn(0.85f, 1.45f)).sp,
            fontWeight = if (t.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (t.italic) FontStyle.Italic else FontStyle.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A tile line's width in dp. */
private fun tileWeight(style: String): Float = when (style) {
    "thick" -> 3f
    "medium", "mediumDashed", "mediumDashDot", "mediumDashDotDot", "slantDashDot" -> 2f
    else -> 1f
}

private fun DrawScope.boxLine(l: TileLine) {
    val c = Color(l.argb)
    val d = density
    if (l.style == "double") {
        drawRect(c, Offset(0.5f * d, 0.5f * d), Size(size.width - d, size.height - d), style = Stroke(d))
        drawRect(c, Offset(3f * d, 3f * d), Size(size.width - 6f * d, size.height - 6f * d), style = Stroke(d))
    } else {
        val w = tileWeight(l.style) * d
        drawRect(c, Offset(w / 2f, w / 2f), Size(size.width - w, size.height - w), style = Stroke(w))
    }
}

private fun DrawScope.edgeLine(l: TileLine, top: Boolean) {
    val c = Color(l.argb)
    val d = density
    if (l.style == "double") {
        for (o in listOf(0.5f, 3f)) {
            val y = if (top) o * d else size.height - o * d
            drawLine(c, Offset(0f, y), Offset(size.width, y), d)
        }
    } else {
        val w = tileWeight(l.style) * d
        val y = if (top) w / 2f else size.height - w / 2f
        drawLine(c, Offset(0f, y), Offset(size.width, y), w)
    }
}

// ---- Number Format ----

/** Number Format: decimals and comma style in a row, then the formats, five of them with pages of their own. */
@Composable
internal fun NumberFormatPanel(vm: AppViewModel) {
    val kind = vm.format?.numberKind
    fun named(format: String) = vm.command("home.numberFormat", json("format" to format))
    Scrolling {
        CellRow(
            Triple(LucideIcons.DecimalsArrowRight.glyph(), false) { vm.command("home.increaseDecimal") },
            Triple(LucideIcons.DecimalsArrowLeft.glyph(), false) { vm.command("home.decreaseDecimal") },
            // a heavy comma, set in the middle of the cell
            Triple(Glyph.Letters("❜"), false) { vm.command("home.comma") },
        )
        PaletteRow("General", Glyph.Letters("123"), onClick = { named("General") }, pressed = kind == "General")
        PaletteRow("Number", Glyph.Letters("12"), onClick = { named("Number") }, onOpen = { vm.pushPanel("numberNegatives") }, pressed = kind == "Number")
        PaletteRow("Currency", LucideIcons.Banknote.glyph(), onClick = { vm.setNumberCode(vm.currencySymbol.currencyCodes()[0]) }, onOpen = { vm.pushPanel("currencyFormats") }, pressed = kind == "Currency")
        PaletteRow("Accounting", LucideIcons.Receipt.glyph(), onClick = { vm.setNumberCode(vm.currencySymbol.accountingCode()) }, onOpen = { vm.pushPanel("accountingFormats") }, pressed = kind == "Accounting")
        PaletteRow("Date", LucideIcons.CalendarDays.glyph(), onClick = { named("Short Date") }, onOpen = { vm.pushPanel("dateFormats") }, pressed = kind == "Date")
        PaletteRow("Time", LucideIcons.Clock3.glyph(), onClick = { named("Time") }, onOpen = { vm.pushPanel("timeFormats") }, pressed = kind == "Time")
        PaletteRow("Percentage", LucideIcons.Percent.glyph(), onClick = { named("Percentage") }, pressed = kind == "Percentage")
        PaletteRow("Fraction", Glyph.Letters("½"), onClick = { named("Fraction") }, pressed = kind == "Fraction")
        PaletteRow("Scientific", Glyph.Letters("10²"), onClick = { named("Scientific") }, pressed = kind == "Scientific")
        PaletteRow("Text", Glyph.Letters("ab"), onClick = { named("Text") }, pressed = kind == "Text")
        PaletteRow("Special", LucideIcons.Hash.glyph(), onClick = { vm.setNumberCode(SPECIAL_FORMATS[0].second) }, onOpen = { vm.pushPanel("specialFormats") })
    }
}

/** A sample of a format, right-aligned (or at the start), in red when negatives show red. */
@Composable
private fun SampleRow(text: String, selected: Boolean, red: Boolean = false, end: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(41.dp).background(if (selected) GridColors.Pressed else GridColors.Surface).bottomEdge(GridColors.Divider).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(text, color = if (red) GridColors.SampleRed else GridColors.OnSurface, fontSize = 16.sp, maxLines = 1)
    }
}

/** The four ways to show negative numbers: a minus, red, parentheses, red parentheses. */
private val NUMBER_NEGATIVES = listOf("0.00", "0.00;[Red]0.00", "0.00_);(0.00)", "0.00_);[Red](0.00)")

/** Number's page: how negative numbers show. */
@Composable
internal fun NumberNegativesPanel(vm: AppViewModel) {
    val code = vm.format?.numberCode
    Scrolling {
        SectionBand("Negative Numbers")
        for ((i, c) in NUMBER_NEGATIVES.withIndex()) {
            SampleRow(listOf("-1234.10", "1234.10", "(1234.10)", "(1234.10)")[i], code == c, red = i % 2 == 1) { vm.setNumberCode(c) }
        }
    }
}

/** A currency symbol and the place it stands for (`$` English (United States)), before or after the number. */
data class CurrencySymbol(val symbol: String, val place: String, val before: Boolean = true) {
    private val quoted get() = "\"" + symbol.replace("\"", "") + "\""

    /** Currency with each of the four negative forms. */
    fun currencyCodes(): List<String> {
        val q = quoted
        return if (before) {
            listOf("$q#,##0.00", "$q#,##0.00;[Red]$q#,##0.00", "$q#,##0.00_);($q#,##0.00)", "$q#,##0.00_);[Red]($q#,##0.00)")
        } else {
            listOf("#,##0.00 $q", "#,##0.00 $q;[Red]#,##0.00 $q", "#,##0.00 ${q}_);(#,##0.00 $q)", "#,##0.00 ${q}_);[Red](#,##0.00 $q)")
        }
    }

    /** How the four look. */
    fun samples(): List<String> = if (before) {
        listOf("-${symbol}1,234.10", "${symbol}1,234.10", "(${symbol}1,234.10)", "(${symbol}1,234.10)")
    } else {
        listOf("-1,234.10 $symbol", "1,234.10 $symbol", "(1,234.10 $symbol)", "(1,234.10 $symbol)")
    }

    /** Accounting with this symbol: lined up at the cell's edge, zero as a dash. */
    fun accountingCode(): String {
        val q = quoted
        return if (before) {
            "_($q* #,##0.00_);_($q* \\(#,##0.00\\);_($q* \"-\"??_);_(@_)"
        } else {
            "_-* #,##0.00 ${q}_-;-* #,##0.00 ${q}_-;_-* \"-\"?? ${q}_-;_-@_-"
        }
    }

    val label get() = "$symbol   $place"
}

/** The symbols the lists offer first. */
internal val COMMON_SYMBOLS = listOf(
    CurrencySymbol("$", "English (United States)"),
    CurrencySymbol("£", "English (United Kingdom)"),
    CurrencySymbol("€", "Euro (€ 123)"),
    CurrencySymbol("¥", "Chinese (Simplified, China)"),
    CurrencySymbol("CHF", "French (Switzerland)"),
)

/** Every place the phone knows a currency for, by symbol: More Symbols. */
private val MORE_SYMBOLS: List<CurrencySymbol> by lazy {
    java.util.Locale.getAvailableLocales().asSequence()
        .filter { it.country.length == 2 }
        .mapNotNull { loc ->
            val cur = runCatching { java.util.Currency.getInstance(loc) }.getOrNull() ?: return@mapNotNull null
            val symbol = cur.getSymbol(loc).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val prefix = (java.text.NumberFormat.getCurrencyInstance(loc) as? java.text.DecimalFormat)?.positivePrefix.orEmpty()
            CurrencySymbol(symbol, loc.displayName, before = prefix.isNotBlank())
        }
        .distinctBy { it.symbol to it.place }
        .sortedWith(compareBy({ it.symbol }, { it.place }))
        .toList()
}

/** Currency's page: the four negative forms with the chosen symbol, then the symbol. */
@Composable
internal fun CurrencyPanel(vm: AppViewModel) {
    val code = vm.format?.numberCode
    val cs = vm.currencySymbol
    Scrolling {
        SectionBand("Negative Numbers")
        for ((i, c) in cs.currencyCodes().withIndex()) SampleRow(cs.samples()[i], code == c, red = i % 2 == 1) { vm.setNumberCode(c) }
        Text("Choose Symbol", color = GridColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
        PaletteRow(cs.label, null, onClick = { vm.pushPanel("currencySymbols") }, chevron = true, inset = false)
        PaletteRow("More Symbols", null, onClick = { vm.pushPanel("moreSymbols:currency") }, chevron = true, inset = false)
    }
}

/** Choose Symbol: the common symbols; picking one gives the cell Currency with it. */
@Composable
internal fun CurrencySymbolsPanel(vm: AppViewModel) {
    Scrolling {
        for (cs in COMMON_SYMBOLS) SampleRow(cs.label, cs == vm.currencySymbol, end = false) { vm.chooseCurrency(cs) }
    }
}

/** Accounting's page: the common symbols, then More Symbols. */
@Composable
internal fun AccountingPanel(vm: AppViewModel) {
    val code = vm.format?.numberCode
    Scrolling {
        for (cs in COMMON_SYMBOLS) SampleRow(cs.label, code == cs.accountingCode(), end = false) { vm.setNumberCode(cs.accountingCode()) }
        PaletteRow("More Symbols", null, onClick = { vm.pushPanel("moreSymbols:accounting") }, chevron = true, inset = false)
    }
}

/** More Symbols, for Currency or for Accounting ([forAccounting]). */
@Composable
internal fun MoreSymbolsPanel(vm: AppViewModel, forAccounting: Boolean) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(MORE_SYMBOLS, key = { it.symbol + "|" + it.place }) { cs ->
            SampleRow(cs.label, false, end = false) {
                if (forAccounting) vm.setNumberCode(cs.accountingCode()) else vm.chooseCurrency(cs)
            }
        }
    }
}

/** The date formats, as samples of March 14, 2012 (a star: it follows the phone's region). */
private val DATE_FORMATS = listOf(
    "*3/14/2012" to "m/d/yyyy", "*Wednesday, March 14, 2012" to "[$-F800]dddd, mmmm dd, yyyy", "2012-03-14" to "yyyy-mm-dd;@",
    "3/14" to "m/d;@", "3/14/12" to "m/d/yy;@", "03/14/12" to "mm/dd/yy;@", "14-Mar" to "d-mmm;@", "14-Mar-12" to "d-mmm-yy;@",
    "14-Mar-12" to "dd-mmm-yy;@", "Mar-12" to "mmm-yy;@", "March-12" to "mmmm-yy;@", "March 14, 2012" to "mmmm d, yyyy;@",
    "3/14/12 1:30 PM" to "[$-409]m/d/yy h:mm AM/PM;@", "3/14/12 13:30" to "m/d/yy h:mm;@", "M" to "mmmmm;@", "M-12" to "mmmmm-yy;@",
    "3/14/2012" to "m/d/yyyy;@", "14-Mar-2012" to "d-mmm-yyyy;@",
)

/** The time formats, as samples of 1:30:55.2 PM (and 37 hours for elapsed time). */
private val TIME_FORMATS = listOf(
    "*1:30:55 PM" to "[$-F400]h:mm:ss AM/PM", "13:30" to "h:mm;@", "1:30 PM" to "[$-409]h:mm AM/PM;@", "13:30:55" to "h:mm:ss;@",
    "1:30:55 PM" to "[$-409]h:mm:ss AM/PM;@", "30:55.2" to "mm:ss.0;@", "37:30:55" to "[h]:mm:ss;@",
    "3/14/12 1:30 PM" to "[$-409]m/d/yy h:mm AM/PM;@", "3/14/12 13:30" to "m/d/yy h:mm;@",
)

/** Date's or Time's page: each format as a sample. */
@Composable
internal fun SamplesPanel(vm: AppViewModel, time: Boolean) {
    val code = vm.format?.numberCode
    Scrolling {
        for ((sample, c) in if (time) TIME_FORMATS else DATE_FORMATS) SampleRow(sample, code == c) { vm.setNumberCode(c) }
    }
}

internal val SPECIAL_FORMATS = listOf(
    "ZIP Code" to "00000", "ZIP Code + 4" to "00000-0000",
    "Phone Number" to "[<=9999999]###-####;(###) ###-####", "Social Security Number" to "000-00-0000",
)

/** Special's page: the formats for the United States, the place they are for. */
@Composable
internal fun SpecialPanel(vm: AppViewModel) {
    val code = vm.format?.numberCode
    Scrolling {
        for ((label, c) in SPECIAL_FORMATS) SampleRow(label, code == c, end = false) { vm.setNumberCode(c) }
        Text("Location", color = GridColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
        PaletteRow("English (United States)", null, onClick = {}, inset = false)
    }
}

// ---- Format Cell Size ----

/** Format Cell Size: the row's height and the column's width in fields, each with its AutoFit. */
@Composable
internal fun CellSizePanel(vm: AppViewModel) {
    val f = vm.format
    Scrolling {
        SizeField("Row Height", f?.rowHeight?.let { "%.1f".format(it) }.orEmpty()) { vm.setSize(true, it) }
        PaletteRow("AutoFit Row Height", LucideIcons.UnfoldVertical.glyph(), onClick = { vm.command("home.autofitRowHeight") })
        SizeField("Column Width", f?.colWidth?.let { "%.2f".format(it) }.orEmpty()) { vm.setSize(false, it) }
        PaletteRow("AutoFit Column Width", LucideIcons.UnfoldHorizontal.glyph(), onClick = {
            val r = vm.selection?.ranges?.lastOrNull()
            if (r != null) vm.autofitColumns(r.firstCol, r.lastCol) else vm.command("home.autofitColumnWidth")
        })
    }
}

/** A labelled number field with a clear button; Done on the keyboard sets it. */
@Composable
private fun SizeField(label: String, current: String, onSet: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Text(label, color = GridColors.OnSurface, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(34.dp).border(1.dp, GridColors.Divider), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                text,
                { text = it.take(8) },
                singleLine = true,
                textStyle = TextStyle(color = GridColors.OnSettings, fontSize = 16.sp),
                cursorBrush = SolidColor(GridColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSet(text) }),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Box(Modifier.size(34.dp).clickable { text = "" }.semantics { contentDescription = "Clear" }, contentAlignment = Alignment.Center) {
                GlyphIcon(LucideIcons.X.glyph(), GridColors.OnStrip, size = 20.dp)
            }
        }
    }
}

/** Size: the selected object's height and width in inches (`1.25 in`), each applied on the keyboard's Done. */
@Composable
internal fun ObjectSizePanel(vm: AppViewModel) {
    val o = vm.selectedObject ?: return
    fun shown(units: Double) = String.format(java.util.Locale.US, "%.2f", units / 96.0).trimEnd('0').trimEnd('.') + " in"
    fun parsed(s: String) = s.replace(',', '.').filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it > 0 }
    Scrolling {
        SizeField("Height", shown(o.height)) { t -> parsed(t)?.let { vm.sizeObject(it, o.width / 96.0) } }
        SizeField("Width", shown(o.width)) { t -> parsed(t)?.let { vm.sizeObject(o.height / 96.0, it) } }
    }
}

// ---- Function help ----

/** A function's help page: its name, its arguments, what it does. */
@Composable
internal fun FunctionHelpPanel(vm: AppViewModel, name: String) {
    var help by remember(name) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(name) {
        val e = vm.engine ?: return@LaunchedEffect
        val json = runCatching { e.command { execute("formulas.functions", "{}") } }.getOrNull() ?: return@LaunchedEffect
        val a = runCatching { JSONArray(json) }.getOrNull() ?: return@LaunchedEffect
        help = (0 until a.length()).asSequence().mapNotNull { a.optJSONObject(it) }.firstOrNull { it.optString("name") == name }?.let {
            it.optString("signature").substringAfter('(', "").removeSuffix(")") to it.optString("description")
        }
    }
    Scrolling {
        Column(Modifier.padding(start = 21.dp, end = 12.dp, top = 24.dp)) {
            Text(name, color = GridColors.OnSettings, fontSize = 18.sp, modifier = Modifier.background(GridColors.Section, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            val (args, description) = help ?: return@Column
            Text(args, color = GridColors.OnSettings, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(GridColors.Divider))
            Text(description, color = GridColors.OnSurface, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

/** A function to pick: its name inserts it; the arrow opens its help. */
@Composable
internal fun FunctionRow(name: String, onPick: () -> Unit, onHelp: () -> Unit) {
    PaletteRow(name, null, onClick = onPick, onOpen = onHelp, inset = false)
}

// ---- Charts ----

/** Insert › Chart's families, each with its icon and its kinds (`type|subtype`) in bands. */
private val CHART_FAMILIES: List<Triple<String, String, List<Pair<String?, List<Pair<String, String>>>>>> = listOf(
    Triple(
        "Column", "column",
        listOf(
            null to listOf(
                "Clustered Column" to "column|", "Stacked Column" to "column|stacked", "100% Stacked Column" to "column|stacked100",
                "3-D Clustered Column" to "column|3d", "3-D Stacked Column" to "column|3dStacked", "3-D 100% Stacked Column" to "column|3dStacked100",
                "3-D Column" to "column|3dColumn",
            ),
        ),
    ),
    Triple(
        "Line", "line",
        listOf(
            null to listOf(
                "Line" to "line|", "Stacked Line" to "line|stacked", "100% Stacked Line" to "line|stacked100", "Line with Markers" to "line|markers",
                "Stacked Line with Markers" to "line|stackedMarkers", "100% Stacked Line with Markers" to "line|stacked100Markers", "3-D Line" to "line|3d",
            ),
        ),
    ),
    Triple("Pie", "pie", listOf(null to listOf("Pie" to "pie|", "3-D Pie" to "pie|3d", "Pie of Pie" to "pie|pieOfPie", "Bar of Pie" to "pie|barOfPie", "Doughnut" to "doughnut|"))),
    Triple(
        "Bar", "bar",
        listOf(
            null to listOf(
                "Clustered Bar" to "bar|", "Stacked Bar" to "bar|stacked", "100% Stacked Bar" to "bar|stacked100",
                "3-D Clustered Bar" to "bar|3d", "3-D Stacked Bar" to "bar|3dStacked", "3-D 100% Stacked Bar" to "bar|3dStacked100",
            ),
        ),
    ),
    Triple(
        "Area", "area",
        listOf(
            null to listOf(
                "Area" to "area|", "Stacked Area" to "area|stacked", "100% Stacked Area" to "area|stacked100",
                "3-D Area" to "area|3d", "3-D Stacked Area" to "area|3dStacked", "3-D 100% Stacked Area" to "area|3dStacked100",
            ),
        ),
    ),
    Triple(
        "X Y (Scatter)", "scatter",
        listOf(
            "Scatter" to listOf(
                "Scatter" to "scatter|", "Scatter with Smooth Lines and Markers" to "scatter|smoothMarkers", "Scatter with Smooth Lines" to "scatter|smooth",
                "Scatter with Straight Lines and Markers" to "scatter|lines", "Scatter with Straight Lines" to "scatter|linesOnly",
            ),
            "Bubble" to listOf("Bubble" to "bubble|", "3-D Bubble" to "bubble|3d"),
        ),
    ),
    Triple("Treemap", "treemap", listOf(null to listOf("Treemap" to "treemap|"))),
    Triple("Sunburst", "sunburst", listOf(null to listOf("Sunburst" to "sunburst|"))),
    Triple("Histogram", "histogram", listOf(null to listOf("Histogram" to "histogram|", "Pareto" to "histogram|pareto"))),
    Triple("Box and Whisker", "boxWhisker", listOf(null to listOf("Box and Whisker" to "boxWhisker|"))),
    Triple("Waterfall", "waterfall", listOf(null to listOf("Waterfall" to "waterfall|"))),
    Triple("Funnel", "funnel", listOf(null to listOf("Funnel" to "funnel|"))),
    Triple(
        "Other", "other",
        listOf(
            "Surface" to listOf(
                "3-D Surface" to "surface|", "Wireframe 3-D Surface" to "surface|wireframe", "Contour" to "surface|contour",
                "Wireframe Contour" to "surface|wireframeContour",
            ),
            "Radar" to listOf("Radar" to "radar|", "Radar with Markers" to "radar|markers", "Filled Radar" to "radar|filled"),
        ),
    ),
)

/** The page name of a chart family's gallery. */
internal const val CHART_FAMILY = "chartFamily:"

/** Insert › Chart: the families, each opening its gallery. */
@Composable
internal fun ChartFamiliesPanel(vm: AppViewModel) {
    Scrolling {
        for ((name, icon, _) in CHART_FAMILIES) PaletteRow(name, Glyph.Chart(icon), onClick = { vm.pushPanel(CHART_FAMILY + name) }, chevron = true, iconTint = GridColors.OnSurface)
    }
}

/** A chart family's gallery: a preview of each kind, two to a row, under their bands. */
@Composable
internal fun ChartGalleryPanel(vm: AppViewModel, family: String) {
    val sections = CHART_FAMILIES.firstOrNull { it.first == family }?.third ?: return
    Scrolling {
        for ((band, kinds) in sections) {
            if (band != null) SectionBand(band)
            ChartTiles(vm, kinds)
        }
    }
}

/** Preview tiles, two to a row; a tap inserts that kind of chart over the selection. */
@Composable
internal fun ChartTiles(vm: AppViewModel, kinds: List<Pair<String, String>>) {
    Column(Modifier.padding(18.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        for (row in kinds.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                for ((label, kind) in row) {
                    ChartTile(vm, kind, Modifier.weight(1f).semantics { contentDescription = label }) {
                        val type = kind.substringBefore('|')
                        val sub = kind.substringAfter('|', "")
                        val p = org.json.JSONObject().put("type", type)
                        if (sub.isNotEmpty()) p.put("subtype", sub)
                        vm.insertObject("insert.chart", p.toString())
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChartTile(vm: AppViewModel, kind: String, modifier: Modifier, onClick: () -> Unit) {
    var prims by remember(kind) { mutableStateOf<List<io.github.artcraftandroid.gridcraft.core.ChartPrim>>(emptyList()) }
    LaunchedEffect(kind) { prims = vm.chartPreview(kind) }
    val w = io.github.artcraftandroid.gridcraft.ui.CHART_PREVIEW_W
    val h = io.github.artcraftandroid.gridcraft.ui.CHART_PREVIEW_H
    Canvas(modifier.aspectRatio(w / h).background(Color.White).clickable(onClick = onClick)) {
        val nc = drawContext.canvas.nativeCanvas
        nc.save()
        nc.scale(size.width / w, size.height / h)
        for (p in prims) drawPrim(nc, p)
        nc.restore()
    }
}

// ---- Shape Styles ----

/**
 * Shape Styles' theme rows: each colour's outline on white, its fill (darker outline), its fill
 * outlined in white, then its fill with dark text, and with light text twice.
 */
private val STYLE_ROWS = listOf("coloredOutline", "coloredFill", "lightOutline", "subtleEffect", "moderateEffect", "intenseEffect")

/**
 * The Presets rows: see-through with coloured text, the same with a coloured outline, a
 * half-see-through fill, a solid fill and a gradient fill (the last three without an outline).
 */
private val PRESET_ROWS = listOf("transparent", "transparentOutline", "semitransparent", "solid", "gradient")

/** How solid the Semitransparent style's fill is (its colour half see-through). */
private const val SEMI_OPACITY = 0.5f

/** What a screen reader says for a style tile, as the phone app words it: `Colored Fill - Accent 1`. */
private fun styleName(key: String): String {
    val style = when (key.substringBefore(':')) {
        "coloredOutline" -> "Colored Outline"
        "coloredFill" -> "Colored Fill"
        "lightOutline" -> "Light 1 Outline, Colored Fill"
        "subtleEffect" -> "Subtle Effect"
        "moderateEffect" -> "Moderate Effect"
        "intenseEffect" -> "Intense Effect"
        "transparent" -> "Transparent"
        "transparentOutline" -> "Transparent, Colored Outline"
        "semitransparent" -> "Semitransparent"
        "solid" -> "Colored Fill"
        else -> "Gradient Fill"
    }
    val color = when (val c = key.substringAfter(':')) {
        "dark1" -> "Dark 1"
        else -> "Accent " + c.removePrefix("accent")
    }
    val noOutline = key.substringBefore(':') in setOf("semitransparent", "solid", "gradient")
    return "$style - $color" + if (noOutline) ", No Outline" else ""
}

/** The colours across: the dark text colour, then the six accents. */
private val STYLE_COLORS = listOf("dark1", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6")

/**
 * How a style tile looks: its fill (null: none), its outline (null: none), its text colour; a
 * gradient fill runs from [fill] lightened at the top to [fill] at the bottom.
 */
private data class TileLook(val fill: Color?, val edge: Color?, val text: Color, val gradient: Boolean = false)

/** Shape Styles: Recent Styles, then the theme's styles and the presets, seven to a row; a tap gives the selected shape that style. */
@Composable
internal fun ShapeStylesPanel(vm: AppViewModel) {
    // the theme's colours as the colour menus have them: light 1, dark 1, light 2, dark 2, the accents
    val theme = vm.colors?.theme?.firstOrNull() ?: return
    fun color(i: Int) = Color(theme.getOrNull(i)?.argb ?: 0xFF000000.toInt())
    val light = color(0)
    val dark = color(1)
    fun look(key: String): TileLook {
        val c = when (val name = key.substringAfter(':')) {
            "dark1" -> dark
            else -> color(3 + (name.removePrefix("accent").toIntOrNull() ?: 1))
        }
        val darker = Color(c.red / 2, c.green / 2, c.blue / 2)
        return when (key.substringBefore(':')) {
            "coloredOutline" -> TileLook(light, c, dark)
            "lightOutline" -> TileLook(c, light, light)
            "subtleEffect" -> TileLook(c, null, dark)
            "moderateEffect", "intenseEffect", "solid" -> TileLook(c, null, light)
            "transparent" -> TileLook(null, null, c)
            "transparentOutline" -> TileLook(null, c, c)
            "semitransparent" -> TileLook(c.copy(alpha = SEMI_OPACITY), null, light)
            "gradient" -> TileLook(c, null, light, gradient = true)
            else -> TileLook(c, darker, light)
        }
    }
    // as the phone app lays them out: 18 dp in from the sides and from the bands, seven tiles to a
    // row spread across, 4 dp apart
    @Composable
    fun Rows(rows: List<List<String>>) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (keys in rows) StyleRow(keys, ::look) { vm.applyShapeStyle(it) }
        }
    }
    Scrolling {
        SectionBand("Recent Styles")
        Rows(listOf(vm.recentShapeStyles))
        SectionBand("Theme Styles")
        Rows(STYLE_ROWS.map { row -> STYLE_COLORS.map { "$row:$it" } })
        SectionBand("Presets")
        Rows(PRESET_ROWS.map { row -> STYLE_COLORS.map { "$row:$it" } })
    }
}

/** A row of seven style tiles: a rounded square in the style's fill and outline, "Abc" in its text colour. */
@Composable
private fun StyleRow(keys: List<String>, look: (String) -> TileLook, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (key in keys) {
            val l = look(key)
            Box(Modifier.weight(1f).height(52.dp).clickable { onPick(key) }.semantics { contentDescription = styleName(key) }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(44.dp)) {
                    val r = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
                    if (l.fill == null || l.fill.alpha < 1f) {
                        // a checkerboard: what shows through
                        val n = 6
                        val cell = size.width / n
                        for (i in 0 until n) for (j in 0 until n) {
                            drawRect(if ((i + j) % 2 == 0) Color(0xFF8A8A8A) else Color(0xFF5C5C5C), Offset(i * cell, j * cell), Size(cell, cell))
                        }
                    }
                    if (l.fill != null && l.gradient) {
                        // as the style: lighter at the top, the colour just below the middle, darker at the bottom
                        val c = l.fill
                        val top = Color(c.red + (1 - c.red) * 0.4f, c.green + (1 - c.green) * 0.4f, c.blue + (1 - c.blue) * 0.4f)
                        val bottom = Color(c.red * 0.67f, c.green * 0.67f, c.blue * 0.67f)
                        drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to top, 0.52f to c, 1f to bottom), cornerRadius = r)
                    } else if (l.fill != null) {
                        drawRoundRect(l.fill, cornerRadius = r)
                    }
                    l.edge?.let { drawRoundRect(it, cornerRadius = r, style = Stroke(3.dp.toPx())) }
                }
                Text("Abc", color = l.text, fontSize = 17.sp)
            }
        }
    }
}

// ---- Shapes ----

/** The names of the shapes Recent Shapes starts with that the bands name otherwise or not at all. */
private val RECENT_LABELS = mapOf("textBox" to "Insert a Text Box")

/**
 * Shapes: the gallery's bands, eight tiles to a row, each shape drawn on its tile; a tap inserts
 * that shape, or ([change]) gives the selected shape its outline (lines and text boxes left out).
 */
@Composable
internal fun ShapesPanel(vm: AppViewModel, change: Boolean = false) {
    var bands by remember { mutableStateOf<List<io.github.artcraftandroid.gridcraft.core.ShapeBand>>(emptyList()) }
    LaunchedEffect(Unit) { bands = vm.shapeGallery() }
    val all = if (change) {
        bands.filter { it.name != "Lines" }.map { b -> b.name to b.shapes.filter { it.kind != "textBox" }.map { it.label to it.kind } }
    } else {
        // the shapes inserted lately, newest first, named as the bands name them
        val label = bands.flatMap { it.shapes }.associate { it.kind to it.label } + RECENT_LABELS
        val recent = vm.recentShapes.mapNotNull { k -> label[k]?.let { it to k } }
        listOf("Recent Shapes" to recent) + bands.map { b -> b.name to b.shapes.map { it.label to it.kind } }
    }
    Scrolling {
        for ((band, tiles) in all) {
            SectionBand(band)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                for (row in tiles.chunked(8)) {
                    Row(Modifier.fillMaxWidth().height(57.dp)) {
                        for ((label, kind) in row) {
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clickable { if (change) vm.changeShape(kind) else vm.insertShape(kind) }.semantics { contentDescription = label },
                                contentAlignment = Alignment.Center,
                            ) { ShapeTile(vm, kind) }
                        }
                        repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A gallery tile's drawing: the shape in the accent blue with a dark outline (lines in the blue). */
@Composable
private fun ShapeTile(vm: AppViewModel, kind: String) {
    if (kind == "textBox") {
        Canvas(Modifier.size(40.dp)) { textBoxSample(GridColors.ShapeTileFill) }
        return
    }
    var pieces by remember(kind) { mutableStateOf<List<io.github.artcraftandroid.gridcraft.core.ShapePiece>>(emptyList()) }
    LaunchedEffect(kind) { pieces = vm.shapePreview(kind) }
    Canvas(Modifier.size(44.dp)) {
        val k = size.width / io.github.artcraftandroid.gridcraft.ui.SHAPE_TILE
        drawPieces(pieces, 0f, 0f, k, k, GridColors.ShapeTileFill, GridColors.ShapeTileLine, 1.3f * density, openInk = GridColors.ShapeTileFill)
    }
}

/** Insert a Text Box's drawing: a box with lines of text beside an A. */
private fun DrawScope.textBoxSample(ink: Color) {
    val w = size.width
    val h = size.height
    drawRect(ink, Offset(w * 0.08f, h * 0.1f), Size(w * 0.84f, h * 0.8f), style = Stroke(1.3f * density))
    for (i in 0..2) {
        val y = h * (0.36f + i * 0.17f)
        drawLine(ink, Offset(w * (if (i == 0) 0.36f else 0.2f), y), Offset(w * 0.8f, y), 1.2f * density)
    }
    drawLine(ink, Offset(w * 0.18f, h * 0.42f), Offset(w * 0.24f, h * 0.26f), 1.2f * density)
    drawLine(ink, Offset(w * 0.24f, h * 0.26f), Offset(w * 0.3f, h * 0.42f), 1.2f * density)
}

