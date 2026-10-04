package com.numbered.app

import android.content.Context
import com.numbered.app.ui.AgeDisplayStore
import com.numbered.app.ui.formatAgeTenths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgeDisplayTest {
    @Test fun defaultAndSelectionSurviveReopening() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("age_display", Context.MODE_PRIVATE).edit().clear().commit()
        val store = AgeDisplayStore(context)
        assertTrue(store.decimalEnabled.value)
        store.setDecimalEnabled(false)
        assertFalse(store.decimalEnabled.value)
        assertFalse(AgeDisplayStore(context).decimalEnabled.value)
        store.setDecimalEnabled(true)
        assertTrue(AgeDisplayStore(context).decimalEnabled.value)
    }

    @Test fun precisionKeepsTrailingZeroAndNeverRoundsUpWholeYears() {
        assertEquals("32.0", formatAgeTenths(320, true, Locale.US))
        assertEquals("32.9", formatAgeTenths(329, true, Locale.US))
        assertEquals("32", formatAgeTenths(329, false, Locale.US))
        assertEquals("32,4", formatAgeTenths(324, true, Locale.GERMANY))
    }
}
