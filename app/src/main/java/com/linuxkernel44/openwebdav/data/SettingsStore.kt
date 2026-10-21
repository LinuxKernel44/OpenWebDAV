package com.linuxkernel44.openwebdav.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DARK
    }
}

enum class LanguagePref(val key: String) {
    AUTO("auto"),
    EN("en"),
    FR("fr");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

enum class SortField(val key: String) {
    NAME("name"),
    DATE("date"),
    SIZE("size");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: NAME
    }
}

/** Non-sensitive preferences, exposed as flows so the UI reacts immediately. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _language = MutableStateFlow(LanguagePref.fromKey(prefs.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<LanguagePref> = _language.asStateFlow()

    private val _sortField = MutableStateFlow(SortField.fromKey(prefs.getString(KEY_SORT_FIELD, null)))
    val sortField: StateFlow<SortField> = _sortField.asStateFlow()

    private val _sortAscending = MutableStateFlow(prefs.getBoolean(KEY_SORT_ASC, true))
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    fun setTheme(value: ThemeMode) {
        _theme.value = value
        prefs.edit().putString(KEY_THEME, value.key).apply()
    }

    fun setLanguage(value: LanguagePref) {
        _language.value = value
        prefs.edit().putString(KEY_LANGUAGE, value.key).apply()
    }

    fun setSort(field: SortField, ascending: Boolean) {
        _sortField.value = field
        _sortAscending.value = ascending
        prefs.edit().putString(KEY_SORT_FIELD, field.key).putBoolean(KEY_SORT_ASC, ascending).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LANGUAGE = "language"
        const val KEY_SORT_FIELD = "sort_field"
        const val KEY_SORT_ASC = "sort_ascending"
    }
}
