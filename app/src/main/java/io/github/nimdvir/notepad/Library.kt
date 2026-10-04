package io.github.nimdvir.notepad

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryEntry(
    val uri: Uri,
    val name: String,
    val lastOpened: Long,
    val lastEdited: Long? = null,
    /** Opened through "Open with" without lasting access: may need reopening from the other app. */
    val temporary: Boolean = false,
)

data class FavoriteFile(val uri: Uri, val name: String)

/** A folder picked with the system folder picker; [treeUri] carries lasting access to everything inside. */
data class FavoriteFolder(val treeUri: Uri, val name: String)

data class LibraryData(
    val history: List<HistoryEntry> = emptyList(),
    val favoriteFiles: List<FavoriteFile> = emptyList(),
    val favoriteFolders: List<FavoriteFolder> = emptyList(),
)

/** Favorites and the last [MAX_HISTORY] files, stored as JSON in the app's private files. */
class Library(private val file: File) {

    fun load(): LibraryData {
        if (!file.exists()) return LibraryData()
        return runCatching { parse(JSONObject(file.readText())) }.getOrDefault(LibraryData())
    }

    private fun parse(root: JSONObject): LibraryData =
        LibraryData(
            history = root.optJSONArray("history").objects().map {
                HistoryEntry(
                    uri = Uri.parse(it.getString("uri")),
                    name = it.getString("name"),
                    lastOpened = it.optLong("opened"),
                    lastEdited = if (it.has("edited")) it.getLong("edited") else null,
                    temporary = it.optBoolean("temporary"),
                )
            },
            favoriteFiles = root.optJSONArray("files").objects().map {
                FavoriteFile(Uri.parse(it.getString("uri")), it.getString("name"))
            },
            favoriteFolders = root.optJSONArray("folders").objects().map {
                FavoriteFolder(Uri.parse(it.getString("uri")), it.getString("name"))
            },
        )

    fun save(data: LibraryData) {
        val root = JSONObject()
            .put("history", JSONArray(data.history.map {
                JSONObject().put("uri", it.uri.toString()).put("name", it.name).put("opened", it.lastOpened)
                    .apply {
                        it.lastEdited?.let { t -> put("edited", t) }
                        if (it.temporary) put("temporary", true)
                    }
            }))
            .put("files", JSONArray(data.favoriteFiles.map {
                JSONObject().put("uri", it.uri.toString()).put("name", it.name)
            }))
            .put("folders", JSONArray(data.favoriteFolders.map {
                JSONObject().put("uri", it.treeUri.toString()).put("name", it.name)
            }))
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
        }
    }

    companion object {
        const val MAX_HISTORY = 50

        fun recordOpened(data: LibraryData, uri: Uri, name: String, now: Long, temporary: Boolean): LibraryData {
            val old = data.history.firstOrNull { it.uri == uri }
            val entry = HistoryEntry(uri, name, now, old?.lastEdited, temporary)
            return data.copy(history = (listOf(entry) + data.history.filter { it.uri != uri }).take(MAX_HISTORY))
        }

        fun recordEdited(data: LibraryData, uri: Uri, name: String, now: Long): LibraryData {
            val old = data.history.firstOrNull { it.uri == uri }
            val entry = HistoryEntry(uri, name, old?.lastOpened ?: now, now, old?.temporary ?: false)
            return data.copy(history = (listOf(entry) + data.history.filter { it.uri != uri }).take(MAX_HISTORY))
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
