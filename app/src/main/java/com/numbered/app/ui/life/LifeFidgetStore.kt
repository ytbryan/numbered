package com.numbered.app.ui.life

import android.content.Context
import androidx.core.content.edit
import com.numbered.app.ui.week.WeekFidgetStrength
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LifeFidgetStyle { Off, WeekPop, RippleField, CometTrail, DominoRow }

/** Device-only preference for playful, non-editing interactions in Life's Week view. */
class LifeFidgetStore(context: Context) {
    private val preferences = context.getSharedPreferences("life_fidget", Context.MODE_PRIVATE)
    private val mutableStyle = MutableStateFlow(
        preferences.getString("style", null)?.let { saved ->
            LifeFidgetStyle.entries.firstOrNull { it.name == saved }
        } ?: LifeFidgetStyle.Off,
    )
    val style = mutableStyle.asStateFlow()
    private val mutableStrength = MutableStateFlow(
        preferences.getString("strength", null)?.let { saved ->
            WeekFidgetStrength.entries.firstOrNull { it.name == saved }
        } ?: WeekFidgetStrength.Balanced,
    )
    val strength = mutableStrength.asStateFlow()

    fun setStyle(style: LifeFidgetStyle) {
        preferences.edit { putString("style", style.name) }
        mutableStyle.value = style
    }

    fun setStrength(strength: WeekFidgetStrength) {
        preferences.edit { putString("strength", strength.name) }
        mutableStrength.value = strength
    }
}
