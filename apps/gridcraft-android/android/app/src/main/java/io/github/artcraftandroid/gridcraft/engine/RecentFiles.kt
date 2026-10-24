package io.github.artcraftandroid.gridcraft.engine

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A workbook opened before, as the landing page's Recent list shows it. */
data class RecentFile(
    /** Where it was opened from: a `content://` URI the app may read again. */
    val uri: String,
    val name: String,
    /** When it was last opened, ms since the epoch. */
    val opened: Long,
    /** Where it lives: the device or provider, then its folders (`This device » Download`). */
    val place: String,
    /** Starred: it shows under the landing page's star too. */
    val starred: Boolean = false,
)

/**
 * The recent workbooks, newest first, kept in a JSON file in the app's private files (at most
 * [MAX] of them). Reads and writes are small and synchronous; callers stay off the main thread.
 */
class RecentFiles(private val file: File) {
    private val lock = Any()

    fun list(): List<RecentFile> = synchronized(lock) { read() }

    /** [entry] to the top of the list (keeping its star). */
    fun touch(entry: RecentFile) = synchronized(lock) {
        val old = read()
        val starred = old.firstOrNull { it.uri == entry.uri }?.starred ?: entry.starred
        write(listOf(entry.copy(starred = starred)) + old.filter { it.uri != entry.uri })
    }

    fun star(uri: String, on: Boolean) = synchronized(lock) { write(read().map { if (it.uri == uri) it.copy(starred = on) else it }) }

    fun remove(uri: String) = synchronized(lock) { write(read().filter { it.uri != uri }) }

    private fun read(): List<RecentFile> {
        if (!file.isFile) return emptyList()
        return runCatching {
            val a = JSONArray(file.readText())
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                RecentFile(
                    uri = o.optString("uri").ifEmpty { return@mapNotNull null },
                    name = o.optString("name", "Book.xlsx"),
                    opened = o.optLong("opened"),
                    place = o.optString("place"),
                    starred = o.optBoolean("starred"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(list: List<RecentFile>) {
        val a = JSONArray()
        for (r in list.take(MAX)) a.put(JSONObject().put("uri", r.uri).put("name", r.name).put("opened", r.opened).put("place", r.place).put("starred", r.starred))
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(file)
        }
    }

    companion object {
        const val MAX = 50

        /**
         * Where [uri] lives, as the landing page shows it: the device or provider, then its
         * folders (`This device » Download » Sheets`) when the provider tells them.
         */
        fun placeOf(context: android.content.Context, uri: android.net.Uri): String {
            val folders = runCatching { foldersOf(context, uri) }.getOrNull().orEmpty()
            val root = when (uri.authority) {
                "com.google.android.apps.docs.storage" -> "Google Drive"
                "com.android.providers.downloads.documents", "com.android.externalstorage.documents", "media", null -> "This device"
                else -> uri.authority?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() } ?: "This device"
            }
            val shown = if (uri.authority == "com.android.providers.downloads.documents" && folders.isEmpty()) listOf("Download") else folders
            return (listOf(root) + shown).joinToString(" » ")
        }

        /** The folders a phone storage or media URI names, top first. */
        private fun foldersOf(context: android.content.Context, uri: android.net.Uri): List<String> {
            val path = when (uri.authority) {
                // primary:Download/Sheets/Book.xlsx
                "com.android.externalstorage.documents" ->
                    android.provider.DocumentsContract.getDocumentId(uri).substringAfter(':', "").substringBeforeLast('/', "")
                "media" -> context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0).orEmpty() else ""
                }.orEmpty()
                else -> ""
            }
            return path.split('/').filter { it.isNotBlank() }.take(4)
        }
    }
}
