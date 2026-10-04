package com.numbered.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TitleTab { Week, Life, Someday }

enum class TitleFont { Clean, Book, Mono }

enum class TitleWeight { Regular, Medium, Bold }

enum class TitleTreatment { Ink, Accent, Warm, Dawn, Dusk }

data class TitleStyle(
    val font: TitleFont = TitleFont.Clean,
    val weight: TitleWeight = TitleWeight.Medium,
    val treatment: TitleTreatment = TitleTreatment.Ink,
)

class TitleStyleStore(context: Context) {
    private val preferences = context.getSharedPreferences("title_styles", Context.MODE_PRIVATE)
    private val mutableStyles = MutableStateFlow(TitleTab.entries.associateWith(::read))
    val styles: StateFlow<Map<TitleTab, TitleStyle>> = mutableStyles.asStateFlow()

    fun select(tab: TitleTab, style: TitleStyle) {
        preferences.edit()
            .putString("${tab.name}_font", style.font.name)
            .putString("${tab.name}_weight", style.weight.name)
            .putString("${tab.name}_treatment", style.treatment.name)
            .apply()
        mutableStyles.value = mutableStyles.value + (tab to style)
    }

    private fun read(tab: TitleTab) = TitleStyle(
        font = enumValueOrDefault(preferences.getString("${tab.name}_font", null), TitleFont.Clean),
        weight = enumValueOrDefault(preferences.getString("${tab.name}_weight", null), TitleWeight.Medium),
        treatment = enumValueOrDefault(preferences.getString("${tab.name}_treatment", null), TitleTreatment.Ink),
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
