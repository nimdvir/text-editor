package io.github.nimdvir.notepad

import android.content.Context

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

/** How Split view arranges the text and the formatted view. */
enum class SplitLayout(val label: String) {
    AUTO("Automatic (by orientation)"),
    STACKED("Top and bottom"),
    SIDE_BY_SIDE("Side by side"),
}

data class ViewSettings(
    val wordWrap: Boolean = true,
    val statusBar: Boolean = true,
    val monospace: Boolean = true,
    val fontSizeSp: Float = DEFAULT_FONT_SIZE,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val autoSave: Boolean = false,
    val syncScroll: Boolean = true,
    val markdownToolbar: Boolean = true,
    val splitLayout: SplitLayout = SplitLayout.AUTO,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 16f
        const val MIN_FONT_SIZE = 8f
        const val MAX_FONT_SIZE = 48f
    }
}

/** Small settings, kept in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("notepad", Context.MODE_PRIVATE)

    fun loadViewSettings() = ViewSettings(
        wordWrap = sp.getBoolean("wordWrap", true),
        statusBar = sp.getBoolean("statusBar", true),
        monospace = sp.getBoolean("monospace", true),
        fontSizeSp = sp.getFloat("fontSize", ViewSettings.DEFAULT_FONT_SIZE),
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
        autoSave = sp.getBoolean("autoSave", false),
        syncScroll = sp.getBoolean("syncScroll", true),
        markdownToolbar = sp.getBoolean("markdownToolbar", true),
        splitLayout = runCatching { SplitLayout.valueOf(sp.getString("splitLayout", null)!!) }
            .getOrDefault(SplitLayout.AUTO),
    )

    fun saveViewSettings(s: ViewSettings) {
        sp.edit()
            .putBoolean("wordWrap", s.wordWrap)
            .putBoolean("statusBar", s.statusBar)
            .putBoolean("monospace", s.monospace)
            .putFloat("fontSize", s.fontSizeSp)
            .putString("theme", s.theme.name)
            .putBoolean("autoSave", s.autoSave)
            .putBoolean("syncScroll", s.syncScroll)
            .putBoolean("markdownToolbar", s.markdownToolbar)
            .putString("splitLayout", s.splitLayout.name)
            .apply()
    }
}
