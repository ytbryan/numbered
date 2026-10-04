package com.numbered.app.ui.week

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WeekFidgetStyle {
    Off,
    SoftPress,
    PebbleWave,
    ElasticWeek,
    RollingNumbers,
    MechanicalRotation,
    MagneticSnap,
    BreathingTrail,
}

enum class WeekFidgetStrength { Gentle, Balanced, Strong, Extreme }

/** Device-only preference for the playful, non-editing week-strip interaction. */
class WeekFidgetStore(context: Context) {
    private val preferences = context.getSharedPreferences("week_fidget", Context.MODE_PRIVATE)
    private val mutableStyle = MutableStateFlow(
        preferences.getString("style", null)?.let { saved ->
            WeekFidgetStyle.entries.firstOrNull { it.name == saved }
        } ?: if (preferences.getBoolean("enabled", false)) WeekFidgetStyle.SoftPress else WeekFidgetStyle.Off,
    )
    val style = mutableStyle.asStateFlow()
    private val mutableStrength = MutableStateFlow(
        preferences.getString("strength", null)?.let { saved ->
            WeekFidgetStrength.entries.firstOrNull { it.name == saved }
        } ?: WeekFidgetStrength.Balanced,
    )
    val strength = mutableStrength.asStateFlow()

    fun setStyle(style: WeekFidgetStyle) {
        preferences.edit {
            putString("style", style.name)
            remove("enabled")
        }
        mutableStyle.value = style
    }

    fun setStrength(strength: WeekFidgetStrength) {
        preferences.edit { putString("strength", strength.name) }
        mutableStrength.value = strength
    }
}
