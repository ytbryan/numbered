package com.numbered.app.ui

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.util.Locale

/** Age precision is a device display preference, separate from exported life data. */
class AgeDisplayStore(context: Context) {
    private val preferences = context.getSharedPreferences("age_display", Context.MODE_PRIVATE)
    private val mutableDecimalEnabled = MutableStateFlow(preferences.getBoolean("decimal_enabled", true))
    val decimalEnabled = mutableDecimalEnabled.asStateFlow()

    fun setDecimalEnabled(enabled: Boolean) {
        preferences.edit { putBoolean("decimal_enabled", enabled) }
        mutableDecimalEnabled.value = enabled
    }
}

internal fun formatAgeTenths(tenths: Int, decimalEnabled: Boolean, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = if (decimalEnabled) 1 else 0
        maximumFractionDigits = minimumFractionDigits
    }.format(if (decimalEnabled) tenths / 10.0 else (tenths / 10).toDouble())
