package com.example

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color

enum class SortMode { NAME, SIZE, DATE }

enum class AccentPreset(val label: String, val dark: Color, val light: Color) {
    BLUE("Синий", Color(0xFF82B1FF), Color(0xFF005AC1)),
    GREEN("Зелёный", Color(0xFF81C995), Color(0xFF2E7D32)),
    PURPLE("Фиолетовый", Color(0xFFCE93D8), Color(0xFF7B1FA2)),
    ORANGE("Оранжевый", Color(0xFFFFB74D), Color(0xFFE65100))
}

enum class StartLocation(val key: String, val label: String) {
    STORAGE("storage", "Внутреннее хранилище"),
    DOWNLOADS("downloads", "Загрузки"),
    ROOT("root", "Корень системы (/)")
}

object AppPreferences {
    private const val PREFS = "app_prefs"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_ACCENT = "accent_preset"
    private const val KEY_SORT = "sort_mode"
    private const val KEY_SHOW_HIDDEN = "show_hidden"
    private const val KEY_START_LOCATION = "start_location"
    private const val KEY_EDITOR_FONT_SIZE = "editor_font_size"
    private const val KEY_COMPACT_LIST = "compact_list"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadTheme(context: Context) {
        CleanMinimalismTheme.isDarkTheme = prefs(context).getBoolean(KEY_DARK_THEME, true)
        val accent = AccentPreset.entries.getOrElse(
            prefs(context).getInt(KEY_ACCENT, 0)
        ) { AccentPreset.BLUE }
        CleanMinimalismTheme.accentPreset = accent
    }

    fun isDarkTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_THEME, true)

    fun setDarkTheme(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_THEME, value).apply()
        CleanMinimalismTheme.isDarkTheme = value
    }

    fun getAccent(context: Context): AccentPreset =
        AccentPreset.entries.getOrElse(prefs(context).getInt(KEY_ACCENT, 0)) { AccentPreset.BLUE }

    fun setAccent(context: Context, preset: AccentPreset) {
        prefs(context).edit().putInt(KEY_ACCENT, preset.ordinal).apply()
        CleanMinimalismTheme.accentPreset = preset
    }

    fun getSortMode(context: Context): SortMode =
        SortMode.entries.getOrElse(prefs(context).getInt(KEY_SORT, 0)) { SortMode.NAME }

    fun setSortMode(context: Context, mode: SortMode) {
        prefs(context).edit().putInt(KEY_SORT, mode.ordinal).apply()
    }

    fun showHiddenFiles(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_HIDDEN, false)

    fun setShowHiddenFiles(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()
    }

    fun getStartLocation(context: Context): StartLocation {
        val key = prefs(context).getString(KEY_START_LOCATION, StartLocation.STORAGE.key)
        return StartLocation.entries.find { it.key == key } ?: StartLocation.STORAGE
    }

    fun setStartLocation(context: Context, location: StartLocation) {
        prefs(context).edit().putString(KEY_START_LOCATION, location.key).apply()
    }

    fun getEditorFontSize(context: Context): Int =
        prefs(context).getInt(KEY_EDITOR_FONT_SIZE, 14).coerceIn(10, 24)

    fun setEditorFontSize(context: Context, size: Int) {
        prefs(context).edit().putInt(KEY_EDITOR_FONT_SIZE, size.coerceIn(10, 24)).apply()
    }

    fun isCompactList(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPACT_LIST, false)

    fun setCompactList(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_COMPACT_LIST, value).apply()
    }
}
