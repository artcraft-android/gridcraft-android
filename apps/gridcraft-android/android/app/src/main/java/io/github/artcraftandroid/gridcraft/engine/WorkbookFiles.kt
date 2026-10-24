package io.github.artcraftandroid.gridcraft.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Workbooks the user opens and saves through the system's pickers and other apps' URIs. */
object WorkbookFiles {
    /** What the app opens: workbooks and templates, older and binary workbooks, ODS, CSV and TSV. */
    val OPEN_TYPES = arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        "application/vnd.ms-excel.sheet.macroEnabled.12",
        "application/vnd.ms-excel.template.macroEnabled.12",
        "application/vnd.ms-excel.sheet.binary.macroEnabled.12",
        "application/vnd.ms-excel",
        "application/vnd.oasis.opendocument.spreadsheet",
        "text/csv",
        "text/comma-separated-values",
        "text/tab-separated-values",
    )

    const val XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** The name a URI shows the user, else its last path segment. */
    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Book.xlsx"
    }

    /**
     * A copy of the workbook at [uri] in the app's cache, under its own name (the engine reads the
     * format from the extension and the content). One folder per open, so names never clash.
     */
    fun copyToCache(context: Context, uri: Uri, name: String): File {
        val dir = File(context.cacheDir, "open/${System.nanoTime()}").apply { mkdirs() }
        val safe = name.replace(Regex("""[\/:*?"<>|\u0000]"""), "_").ifBlank { "Book.xlsx" }
        val file = File(dir, safe)
        val input = context.contentResolver.openInputStream(uri) ?: error("The workbook can't be read.")
        input.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
        return file
    }

    /** Write [file] over the workbook at [uri]; false when the app may not write there. */
    fun write(context: Context, uri: Uri, file: File): Boolean = runCatching {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: return false
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
        true
    }.getOrDefault(false)

    /** Remove the copies of earlier opens (called at start-up). */
    fun clearCache(context: Context) {
        runCatching { File(context.cacheDir, "open").deleteRecursively() }
    }
}
