package com.numbered.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.domain.CloseChoice
import com.numbered.app.widget.ThisWeekWidgetUi
import com.numbered.app.widget.ToggleDone
import com.numbered.app.widget.WidgetContent
import com.numbered.app.widget.WidgetItem
import com.numbered.app.widget.WidgetState
import com.numbered.app.widget.loadWidgetState
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = NumberedDatabase.inMemory(app)
    private val thisWeek = LocalDate.of(2026, 9, 28)

    @After fun close() = db.close()

    private fun containerAt(dateTime: String, setUp: Boolean = true): AppContainer {
        val clock = Clock.fixed(LocalDateTime.parse(dateTime).atZone(zone).toInstant(), zone)
        val container = AppContainer(app, db, clock)
        (app as NumberedApp).replaceContainer(container)
        if (setUp) {
            runBlocking {
                if (container.repository.currentProfile() == null) {
                    container.repository.saveProfile(LocalDate.of(1989, 12, 2), 80, gentle = false, firstDayOfWeek = DayOfWeek.MONDAY)
                }
            }
        }
        return container
    }

    private fun load(container: AppContainer) = runBlocking { loadWidgetState(container.repository, container.clock) }

    @Test fun theWidgetFollowsTheSameRulesAsThisWeek() {
        assertNull("Before setup there is no week to show", load(containerAt("2026-10-01T10:00", setUp = false)))

        val thursday = containerAt("2026-10-01T10:00")
        runBlocking {
            thursday.repository.addCommitment(thisWeek, "Renew passport photos")
            thursday.repository.addCommitment(thisWeek, "Finish the grant draft")
            thursday.repository.addCommitment(thisWeek.minusWeeks(1), "Last week's")
            thursday.repository.setDone(thursday.repository.week(thisWeek).first().first().id, true)
        }
        val midWeek = load(thursday)!!
        assertEquals(4, midWeek.daysLeft)
        assertEquals(listOf("Renew passport photos" to true, "Finish the grant draft" to false), midWeek.items.map { it.title to it.done })
        assertEquals(1, midWeek.squaresLeft)
        assertFalse(midWeek.offerClose)

        val sunday = containerAt("2026-10-04T19:00")
        assertTrue(load(sunday)!!.offerClose)
        runBlocking { sunday.repository.closeWeek(thisWeek, "Done.", emptyMap<Long, CloseChoice>(), thisWeek.plusWeeks(1)) }
        assertFalse("A closed week is not offered again", load(sunday)!!.offerClose)
    }

    @Test fun tickingFromTheWidgetMarksItDone() {
        val container = containerAt("2026-10-01T10:00")
        val id = runBlocking {
            container.repository.addCommitment(thisWeek, "Call the bank")
            container.repository.week(thisWeek).first().single().id
        }
        val noWidget = object : GlanceId {}
        runBlocking {
            ToggleDone().onAction(app, noWidget, actionParametersOf(ToggleDone.CommitmentId to id, ToggleableStateKey to true))
        }
        assertTrue(load(container)!!.items.single().done)
        runBlocking {
            ToggleDone().onAction(app, noWidget, actionParametersOf(ToggleDone.CommitmentId to id, ToggleableStateKey to false))
        }
        assertFalse(load(container)!!.items.single().done)
    }

    @Test fun appLockBlocksPreviouslyCreatedWidgetActions() {
        val container = containerAt("2026-10-01T10:00")
        val entry = runBlocking {
            container.repository.addCommitment(thisWeek, "Private commitment")
            container.repository.week(thisWeek).first().single()
        }
        container.appLock.setEnabled(true)
        runBlocking {
            ToggleDone().onAction(app, object : GlanceId {}, actionParametersOf(ToggleDone.CommitmentId to entry.id, ToggleableStateKey to true))
        }
        assertEquals(entry, runBlocking { container.repository.week(thisWeek).first().single() })
    }

    @Test fun appLockRedactsEvenAPreviouslyLoadedWidget() = runGlanceAppWidgetUnitTest {
        setContext(app)
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        provideComposable {
            WidgetContent(WidgetState(thisWeek, 1, listOf(WidgetItem(1, "Private commitment", false)), true), locked = true)
        }
        onNode(hasText("Numbered is locked. Tap to unlock.")).assertExists()
        onNode(hasText("Private commitment")).assertDoesNotExist()
        onNode(hasText("Close the week →")).assertDoesNotExist()
    }

    @Test fun theContentOffersAddingAndClosing() = runGlanceAppWidgetUnitTest {
        setContext(app)
        setAppWidgetSize(DpSize(300.dp, 200.dp))
        provideComposable {
            WidgetContent(
                WidgetState(
                    weekStart = thisWeek,
                    daysLeft = 1,
                    items = listOf(WidgetItem(1, "Renew passport photos", true), WidgetItem(2, "Finish the grant draft", false)),
                    offerClose = true,
                ),
            )
        }
        onNode(hasText("Last day")).assertExists()
        onNode(hasText("Last day")).assertExists()
        onNode(hasText("+ Add another")).assertExists()
        onNode(hasText("Close the week →")).assertExists()
    }

    /** Renders the real widget, as a launcher would, to app/build/screens for review. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test fun renderTheWidget() {
        val container = containerAt("2026-10-04T19:00")
        runBlocking {
            container.repository.addCommitment(thisWeek, "Renew passport photos")
            container.repository.addCommitment(thisWeek, "Finish the grant draft")
            container.repository.addCommitment(thisWeek, "Call Mum about the Osaka trip and the long weekend")
            container.repository.setDone(container.repository.week(thisWeek).first().first().id, true)
        }
        // About a 4 by 2 cell widget on a phone.
        val size = DpSize(320.dp, 180.dp)
        val state = runBlocking { loadWidgetState(container.repository, container.clock) }
        val remote = runBlocking { GlanceRemoteViews().compose(app, size) { ThisWeekWidgetUi(state) }.remoteViews }
        val density = app.resources.displayMetrics.density
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        val host = FrameLayout(app)
        val view = remote.apply(app, host)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // A wallpaper-like backdrop, so the rounded corners show.
        bitmap.eraseColor(0xFF3A4A5A.toInt())
        view.draw(Canvas(bitmap))
        val dir = File(System.getProperty("user.dir"), "build/screens").apply { mkdirs() }
        File(dir, "widget.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
