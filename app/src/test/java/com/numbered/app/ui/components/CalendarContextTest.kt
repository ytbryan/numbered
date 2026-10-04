package com.numbered.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarContextTest {
    @Test
    fun colourIntensityBuildsWithContinuousFidgeting() {
        val firstTouch = boostedFidgetColorAmount(glow = 0.42f, energy = 0f)
        val continuedTouch = boostedFidgetColorAmount(glow = 0.42f, energy = 0.5f)
        val sustainedTouch = boostedFidgetColorAmount(glow = 0.42f, energy = 1f)

        assertEquals(0.42f, firstTouch, 0.001f)
        assertTrue(continuedTouch > firstTouch)
        assertTrue(sustainedTouch > continuedTouch)
    }

    @Test
    fun colourIntensityStaysWithinValidRange() {
        assertEquals(0f, boostedFidgetColorAmount(glow = 0f, energy = 1f), 0.001f)
        assertEquals(1f, boostedFidgetColorAmount(glow = 1f, energy = 1f), 0.001f)
    }
}
