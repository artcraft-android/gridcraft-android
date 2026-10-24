package io.github.artcraftandroid.gridcraft.ui.editor

import android.content.Context
import android.graphics.Typeface

/**
 * Typefaces for cell text. Workbooks name the fonts they were written with; the app ships open
 * fonts with the same widths as the common ones (Carlito for Calibri, Caladea for Cambria,
 * Liberation for Arial, Times New Roman and Courier New) so columns fit their text as written.
 * Any other font is asked of the system, which falls back to its sans serif.
 */
object CellFonts {
    private val cache = HashMap<String, Typeface>()

    /** The shipped twin of a font a workbook asks for (its file name stem), if any. */
    private fun twin(name: String): String? = when (name.trim().lowercase()) {
        "calibri", "calibri light", "aptos", "aptos narrow", "aptos display" -> "Carlito"
        "cambria" -> "Caladea"
        "arial", "helvetica", "arial narrow", "liberation sans" -> "LiberationSans"
        "times new roman", "times", "liberation serif" -> "LiberationSerif"
        "courier new", "courier", "liberation mono" -> "LiberationMono"
        else -> null
    }

    fun typeface(context: Context, name: String, bold: Boolean, italic: Boolean): Typeface {
        val key = "$name|$bold|$italic"
        cache[key]?.let { return it }
        val style = when {
            bold && italic -> "BoldItalic"
            bold -> "Bold"
            italic -> "Italic"
            else -> "Regular"
        }
        val tf = twin(name)?.let { stem ->
            runCatching { Typeface.createFromAsset(context.assets, "docfonts/$stem-$style.ttf") }.getOrNull()
        } ?: Typeface.create(
            Typeface.create(name, Typeface.NORMAL),
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
        cache[key] = tf
        return tf
    }
}

/** Column letters for a 0-based column: 0 → A, 25 → Z, 26 → AA. */
fun columnLetters(col: Int): String {
    var n = col + 1
    val sb = StringBuilder()
    while (n > 0) {
        val r = (n - 1) % 26
        sb.append('A' + r)
        n = (n - 1) / 26
    }
    return sb.reverse().toString()
}
