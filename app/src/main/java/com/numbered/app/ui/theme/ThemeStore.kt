package com.numbered.app.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeOption {
    FocusLight,
    WarmPaper,
    DeepInk,
    SoftSage,
    HighContrast,
}

/** A visual preference stays on this device and does not change exported life data. */
class ThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val mutableSelection = MutableStateFlow(read())
    val selection = mutableSelection.asStateFlow()

    fun select(theme: ThemeOption) {
        preferences.edit { putString(KEY, theme.name) }
        mutableSelection.value = theme
    }

    private fun read(): ThemeOption = ThemeOption.entries.firstOrNull {
        it.name == preferences.getString(KEY, null)
    } ?: ThemeOption.FocusLight

    private companion object {
        const val KEY = "selection"
    }
}
