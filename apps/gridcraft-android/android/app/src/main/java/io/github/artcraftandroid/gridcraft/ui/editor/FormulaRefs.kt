package io.github.artcraftandroid.gridcraft.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * A cell reference in a formula being typed: where it is in the text, the block it covers
 * (0-based) and its colour. The same reference keeps one colour wherever it appears.
 */
data class FormulaRef(
    val start: Int,
    val end: Int,
    val firstRow: Int,
    val firstCol: Int,
    val lastRow: Int,
    val lastCol: Int,
    val color: Color,
)

private val REF_COLORS = listOf(
    Color(0xFF2F6FDE), Color(0xFFD13438), Color(0xFF8A3FB8), Color(0xFF1E8A4C),
    Color(0xFFCA6A0A), Color(0xFF0F8A8A), Color(0xFFC2399C),
)

// A1 or A1:B2, with or without $, not part of a name, a function or another sheet's reference
private val REF = Regex("""(?<![A-Za-z0-9_.!'"$])\$?([A-Za-z]{1,3})\$?([0-9]{1,7})(?::\$?([A-Za-z]{1,3})\$?([0-9]{1,7}))?(?![A-Za-z0-9_(!])""")

private fun column(letters: String): Int = letters.uppercase().fold(0) { n, ch -> n * 26 + (ch - 'A' + 1) } - 1

/** The references on this sheet in a formula (`=` first), outside its quoted strings. */
fun formulaRefs(text: String): List<FormulaRef> {
    if (!text.startsWith("=")) return emptyList()
    // the spans of string literals, where nothing is a reference
    val quoted = ArrayList<IntRange>()
    var open = -1
    for ((i, ch) in text.withIndex()) {
        if (ch != '"') continue
        if (open < 0) open = i else {
            quoted.add(open..i)
            open = -1
        }
    }
    if (open >= 0) quoted.add(open until text.length)
    val colors = HashMap<String, Color>()
    val out = ArrayList<FormulaRef>()
    for (m in REF.findAll(text)) {
        if (quoted.any { m.range.first in it }) continue
        val g = m.groupValues
        val c0 = column(g[1])
        val r0 = g[2].toIntOrNull()?.minus(1) ?: continue
        val c1 = if (g[3].isNotEmpty()) column(g[3]) else c0
        val r1 = if (g[4].isNotEmpty()) g[4].toIntOrNull()?.minus(1) ?: continue else r0
        if (c0 !in 0..16383 || c1 !in 0..16383 || r0 !in 0..1048575 || r1 !in 0..1048575) continue
        val key = m.value.replace("$", "").uppercase()
        val color = colors.getOrPut(key) { REF_COLORS[colors.size % REF_COLORS.size] }
        out.add(FormulaRef(m.range.first, m.range.last + 1, minOf(r0, r1), minOf(c0, c1), maxOf(r0, r1), maxOf(c0, c1), color))
    }
    return out
}

/** The formula bar's text with its references in their colours. */
object RefColorsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val refs = formulaRefs(text.text)
        if (refs.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val b = AnnotatedString.Builder(text)
        for (r in refs) b.addStyle(SpanStyle(color = r.color), r.start, r.end)
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/**
 * The function call the cursor is in, in a formula being typed: its name (upper case) and which
 * argument the cursor is on (0-based). Brackets without a function name and quoted text count
 * for nesting but name nothing.
 */
fun callAt(text: String, cursor: Int): Pair<String, Int>? {
    if (!text.startsWith("=")) return null
    val calls = ArrayList<Pair<String, Int>?>()
    var quoted = false
    val end = cursor.coerceIn(0, text.length)
    for (i in 0 until end) {
        val ch = text[i]
        if (ch == '"') {
            quoted = !quoted
            continue
        }
        if (quoted) continue
        when (ch) {
            '(' -> calls.add(NAME_BEFORE.find(text.substring(0, i))?.value?.uppercase()?.let { it to 0 })
            ')' -> if (calls.isNotEmpty()) calls.removeAt(calls.size - 1)
            ',' -> calls.lastOrNull()?.let { calls[calls.size - 1] = it.first to it.second + 1 }
        }
    }
    return calls.lastOrNull { it != null }
}

private val NAME_BEFORE = Regex("""[A-Za-z_][A-Za-z0-9._]*$""")

/** A signature's arguments: `SUM(number1, [number2], ...)` → number1, [number2], ... */
fun signatureArgs(signature: String): List<String> =
    signature.substringAfter('(', "").substringBeforeLast(')').split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Which of [args] argument [index] is shown on: past the end, the repeating one before `...`. */
fun argumentShown(args: List<String>, index: Int): Int {
    if (args.isEmpty()) return -1
    var i = index.coerceAtMost(args.size - 1)
    if (args[i] == "..." && i > 0) i -= 1
    return i
}
