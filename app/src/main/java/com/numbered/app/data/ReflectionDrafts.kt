package com.numbered.app.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/** Unfinished reflections stay on this phone until the week is closed. */
class ReflectionDrafts(context: Context) {
    private val preferences = context.getSharedPreferences("reflection_drafts", Context.MODE_PRIVATE)

    fun read(week: LocalDate): String? = preferences.getString(week.toString(), null)

    fun save(week: LocalDate, note: String) {
        preferences.edit { putString(week.toString(), note) }
    }

    fun clear(week: LocalDate) {
        preferences.edit { remove(week.toString()) }
    }

    fun clearAll() {
        preferences.edit { clear() }
    }
}
