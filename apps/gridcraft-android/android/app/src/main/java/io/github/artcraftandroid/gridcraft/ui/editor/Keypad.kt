package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.gridcraft.ui.AppViewModel
import io.github.artcraftandroid.gridcraft.ui.GridColors
import java.text.DecimalFormatSymbols

/** A key of the keypad: what it types (or does), how it looks, how wide it is. */
private data class Key(val label: String, val action: String, val kind: Int = PRIMARY, val span: Int = 1)

private const val PRIMARY = 0
private const val SECONDARY = 1
private const val EMPHASIS = 2

/**
 * The number keypad for typing figures and formulas: two pages of keys, four rows of seven.
 * The first holds the digits, the decimal and thousands separators, the arithmetic, brackets,
 * percent, the currency sign, AutoSum, Tab, Backspace and Enter; the second the other symbols
 * formulas use. `abc` goes back to the phone's keyboard. On a wide screen (landscape, a tablet)
 * the first page has ten keys a row, adding ; < > & ^ !, a space and the arrows (which enter
 * and move left or right).
 */
@Composable
fun Keypad(vm: AppViewModel) {
    // Spreadsheet formulas and numeric keys retain their canonical left-to-right order.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) { KeypadContent(vm) }
}

@Composable
private fun KeypadContent(vm: AppViewModel) {
    var page by remember { mutableIntStateOf(0) }
    val sym = remember { DecimalFormatSymbols.getInstance() }
    val decimal = sym.decimalSeparator.toString()
    val group = sym.groupingSeparator.toString().let { if (it.isBlank() || it == " " || it == " ") "," else it }
    val currency = remember { runCatching { sym.currencySymbol }.getOrDefault("$").take(3) }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = config.screenWidthDp >= 600
    // a short screen (a phone on its side) gets shorter keys, so the grid keeps some rows
    val short = config.screenHeightDp < 500
    val rowHeight = if (short) 40.dp else 52.dp
    val keyHeight = if (short) 34.dp else 46.dp
    val widePage = listOf(
        listOf(
            Key("Tab", "tab", SECONDARY), Key("7", "7"), Key("8", "8"), Key("9", "9"), Key("(", "(", SECONDARY), Key(")", ")", SECONDARY),
            Key("<", "<", SECONDARY), Key(">", ">", SECONDARY), Key(";", ";", SECONDARY), Key("⌫", "back", SECONDARY),
        ),
        listOf(
            Key("Σ", "sum", SECONDARY), Key("4", "4"), Key("5", "5"), Key("6", "6"), Key("×", "*", SECONDARY), Key("÷", "/", SECONDARY),
            Key("%", "%", SECONDARY), Key("&", "&", SECONDARY), Key("^", "^", SECONDARY), Key("!", "!", SECONDARY),
        ),
        listOf(
            Key(":", ":", SECONDARY), Key("1", "1"), Key("2", "2"), Key("3", "3"), Key("+", "+", SECONDARY), Key("−", "-", SECONDARY),
            Key(currency, currency, SECONDARY), Key("=", "=", SECONDARY), Key("←", "left", SECONDARY), Key("→", "right", SECONDARY),
        ),
        listOf(
            Key("abc", "abc", SECONDARY), Key("0", "0", span = 2), Key(decimal, decimal), Key(group, group), Key("␣", " ", SECONDARY, span = 2),
            Key("#+=", "page", SECONDARY), Key("⏎", "enter", EMPHASIS, span = 2),
        ),
    )
    val pages = listOf(
        listOf(
            listOf(Key("Tab", "tab", SECONDARY), Key("7", "7"), Key("8", "8"), Key("9", "9"), Key("(", "(", SECONDARY), Key(")", ")", SECONDARY), Key("⌫", "back", SECONDARY)),
            listOf(Key("Σ", "sum", SECONDARY), Key("4", "4"), Key("5", "5"), Key("6", "6"), Key("×", "*", SECONDARY), Key("÷", "/", SECONDARY), Key("%", "%", SECONDARY)),
            listOf(Key(":", ":", SECONDARY), Key("1", "1"), Key("2", "2"), Key("3", "3"), Key("+", "+", SECONDARY), Key("−", "-", SECONDARY), Key(currency, currency, SECONDARY)),
            listOf(Key("abc", "abc", SECONDARY), Key("0", "0"), Key(decimal, decimal), Key(group, group), Key("=", "=", SECONDARY), Key("#+=", "page", SECONDARY), Key("⏎", "enter", EMPHASIS)),
        ),
        listOf(
            listOf(Key("Tab", "tab", SECONDARY), Key("[", "["), Key("]", "]"), Key("{", "{"), Key("}", "}"), Key("~", "~"), Key("⌫", "back", SECONDARY)),
            listOf(Key("Σ", "sum", SECONDARY), Key("@", "@"), Key("#", "#"), Key("?", "?"), Key("\"", "\""), Key("'", "'"), Key("|", "|")),
            listOf(Key(";", ";", SECONDARY), Key("<", "<"), Key(">", ">"), Key("&", "&"), Key("^", "^"), Key("!", "!"), Key("$", "$")),
            listOf(Key("abc", "abc", SECONDARY), Key("+", "+", SECONDARY), Key("−", "-", SECONDARY), Key("×", "*", SECONDARY), Key("÷", "/", SECONDARY), Key("123", "page", SECONDARY), Key("⏎", "enter", EMPHASIS)),
        ),
    )
    Column(Modifier.fillMaxWidth().background(GridColors.Strip).padding(4.dp)) {
        for (row in if (wide && page == 0) widePage else pages[page]) {
            Row(Modifier.fillMaxWidth().height(rowHeight)) {
                for (k in row) {
                    val fill = when (k.kind) {
                        EMPHASIS -> GridColors.Accent
                        SECONDARY -> GridColors.StripMore
                        else -> GridColors.Surface
                    }
                    Box(
                        Modifier.weight(k.span.toFloat()).padding(3.dp).height(keyHeight).background(fill, RoundedCornerShape(6.dp)).clickable {
                            when (k.action) {
                                "page" -> page = 1 - page
                                "abc" -> vm.useKeypad(false)
                                "back" -> vm.keypadBackspace()
                                "enter" -> vm.commitEdit("down")
                                "tab" -> vm.commitEdit("right")
                                "left" -> vm.commitEdit("left")
                                "right" -> vm.commitEdit("right")
                                "sum" -> vm.keypadType(if (vm.editText.isEmpty()) "=SUM(" else "SUM(")
                                else -> vm.keypadType(k.action)
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k.label, color = if (k.kind == EMPHASIS) Color.White else GridColors.OnSurface, fontSize = if (k.label.length > 2) 15.sp else 20.sp)
                    }
                }
            }
        }
    }
}
