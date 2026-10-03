package com.numbered.app.ui.week

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WeekProgressStyle { Off, Circle, Bars }

/** The week visual is a device preference, separate from exported life data. */
class WeekProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("week_progress", Context.MODE_PRIVATE)
    private val mutableStyle = MutableStateFlow(read())
    val style = mutableStyle.asStateFlow()

    fun select(style: WeekProgressStyle) {
        preferences.edit { putString("style", style.name) }
        mutableStyle.value = style
    }

    private fun read(): WeekProgressStyle = WeekProgressStyle.entries.firstOrNull {
        it.name == preferences.getString("style", null)
    } ?: WeekProgressStyle.Off
}
