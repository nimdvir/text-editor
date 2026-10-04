package io.github.nimdvir.notepad

import android.content.Context
import android.net.Uri

data class RecentFile(val uri: Uri, val name: String)

data class ViewSettings(
    val wordWrap: Boolean = true,
    val statusBar: Boolean = true,
    val monospace: Boolean = true,
    val fontSizeSp: Float = DEFAULT_FONT_SIZE,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 16f
        const val MIN_FONT_SIZE = 8f
        const val MAX_FONT_SIZE = 48f
    }
}

/** Small settings and the recent-files list, kept in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("notepad", Context.MODE_PRIVATE)

    fun loadViewSettings() = ViewSettings(
        wordWrap = sp.getBoolean("wordWrap", true),
        statusBar = sp.getBoolean("statusBar", true),
        monospace = sp.getBoolean("monospace", true),
        fontSizeSp = sp.getFloat("fontSize", ViewSettings.DEFAULT_FONT_SIZE),
    )

    fun saveViewSettings(s: ViewSettings) {
        sp.edit()
            .putBoolean("wordWrap", s.wordWrap)
            .putBoolean("statusBar", s.statusBar)
            .putBoolean("monospace", s.monospace)
            .putFloat("fontSize", s.fontSizeSp)
            .apply()
    }

    fun loadRecent(): List<RecentFile> =
        sp.getString("recent", "").orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size == 2) RecentFile(Uri.parse(parts[0]), parts[1]) else null
        }

    fun saveRecent(list: List<RecentFile>) {
        sp.edit().putString("recent", list.joinToString("\n") { "${it.uri}\t${it.name}" }).apply()
    }
}
