package com.numbered.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.numbered.app.data.BackupFormat
import com.numbered.app.data.Commitment
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.Profile
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.WeekReview
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.CommitmentStatus.Carried
import com.numbered.app.domain.CommitmentStatus.Done
import com.numbered.app.domain.CommitmentStatus.Open
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Seeds no profile, so the app opens on setup. */
@Retention(AnnotationRetention.RUNTIME)
annotation class FreshInstall

/** Overrides the default Thursday 1 October 2026. */
@Retention(AnnotationRetention.RUNTIME)
annotation class OnDate(val date: String)

@Retention(AnnotationRetention.RUNTIME)
annotation class Gentle

/**
 * Drives the real app on seeded data and renders each screen to app/build/screens for review.
 * Most captures use a 366dp-wide phone at 145% text, the size the app must look right at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w366dp-h813dp-xxhdpi", fontScale = 1.45f)
class ScreenCaptureTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val defaultToday = LocalDate.of(2026, 10, 1)

    private fun clockOn(today: LocalDate) = Clock.fixed(today.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant(), zone)

    @get:Rule(order = 0)
    val seed = object : ExternalResource() {
        private lateinit var description: Description
        private lateinit var database: NumberedDatabase

        override fun apply(base: Statement, description: Description): Statement {
            this.description = description
            return super.apply(base, description)
        }

        override fun before() {
            val today = description.getAnnotation(OnDate::class.java)?.date?.let(LocalDate::parse) ?: defaultToday
            val clock = clockOn(today)
            val app = RuntimeEnvironment.getApplication() as NumberedApp
            database = NumberedDatabase.inMemory(app)
            app.replaceContainer(AppContainer(database, clock))
            if (description.getAnnotation(FreshInstall::class.java) == null) {
                runBlocking { seed(database, today, gentle = description.getAnnotation(Gentle::class.java) != null) }
            }
        }

        override fun after() {
            database.close()
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val files = TemporaryFolder()

    private fun millis(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private suspend fun seed(db: NumberedDatabase, today: LocalDate, gentle: Boolean) {
        val thisWeek = today.with(DayOfWeek.MONDAY)
        val started = LocalDate.of(2026, 1, 5)
        db.profiles().upsert(
            Profile(
                birthDate = LocalDate.of(1989, 12, 2),
                horizonYears = 80,
                firstDayOfWeek = DayOfWeek.MONDAY,
                gentle = gentle,
                startedOn = started,
            ),
        )
        val commitments = db.commitments()
        suspend fun add(week: LocalDate, title: String, status: CommitmentStatus, doneDay: Long = 2, carriedFrom: LocalDate? = null) {
            commitments.insert(
                Commitment(
                    weekStart = week,
                    title = title,
                    status = status,
                    createdAt = millis(week, 9),
                    resolvedAt = if (status == Open) null else millis(week.plusDays(doneDay)),
                    carriedFrom = carriedFrom,
                ),
            )
        }
        // A varied, deterministic history from the first week until the week before last.
        var week = started
        var n = 0
        val lastWeek = thisWeek.minusWeeks(1)
        while (week < lastWeek) {
            when (n % 7) {
                0, 3, 5 -> repeat(3) { add(week, "Past goal ${n}-$it", Done) }
                1, 4 -> {
                    add(week, "Past goal $n-a", Done)
                    add(week, "Past goal $n-b", Done)
                    add(week, "Past goal $n-c", Carried)
                }
                2 -> add(week, "Past goal $n-a", Done)
                6 -> Unit
            }
            if (n % 7 != 6) db.reviews().upsert(WeekReview(week, if (n % 2 == 0) "A steady week." else "", millis(week.plusDays(6), 20)))
            week = week.plusWeeks(1)
            n++
        }
        add(lastWeek, "Run three user sessions", Done)
        add(lastWeek, "Finish the grant draft", Carried, doneDay = 6)
        add(lastWeek, "Draft the conference talk", Open)
        add(thisWeek, "Renew passport photos", Done, doneDay = 1)
        add(thisWeek, "Finish the grant draft", Open, carriedFrom = lastWeek)
        add(thisWeek.plusWeeks(1), "Book flights to Osaka", Open)
        add(thisWeek.plusWeeks(9), "Mum's 70th birthday dinner", Open)
        add(thisWeek.plusWeeks(30), "Japan trip", Open)

        val someday = db.someday()
        val now = millis(today, 10)
        val weekMillis = 7 * 86_400_000L
        someday.insert(SomedayItem(title = "Read Middlemarch", createdAt = now - 15 * weekMillis))
        someday.insert(SomedayItem(title = "Set up a home server", createdAt = now - 20 * weekMillis))
        someday.insert(SomedayItem(title = "Write to Mr Tan", createdAt = now - 5 * weekMillis))
        someday.insert(SomedayItem(title = "Learn to sail", createdAt = now - 2 * weekMillis))
        someday.insert(SomedayItem(title = "Build a bookshelf", createdAt = now - 86_400_000L))
        someday.insert(SomedayItem(title = "Learn the violin", createdAt = now - 30 * weekMillis, letGoAt = now - 3 * weekMillis))
        someday.insert(SomedayItem(title = "Start a podcast", createdAt = now - 40 * weekMillis, letGoAt = now - 9 * weekMillis))
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowViews(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(instance) as List<View>).toList()
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val dir = File(System.getProperty("user.dir"), "build/screens").apply { mkdirs() }
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Dialogs and sheets live in their own windows, so draw every attached window in order.
        // Robolectric does not lay windows out, so apply each one's dim and centring the way a phone would.
        windowViews().filter { it.isAttachedToWindow && it.width > 0 }.forEach { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams
            if (view !== root && params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                canvas.drawColor(Color.argb((params.dimAmount * 255).toInt(), 0, 0, 0))
            }
            val centred = view !== root && params != null && params.gravity == Gravity.CENTER
            val location = IntArray(2).also(view::getLocationOnScreen)
            canvas.save()
            if (centred) {
                canvas.translate((root.width - view.width) / 2f, (root.height - view.height) / 2f)
            } else {
                canvas.translate(location[0].toFloat(), location[1].toFloat())
            }
            view.draw(canvas)
            canvas.restore()
        }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Data arrives from Room on background threads, so wait for it the way a person would. */
    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(text: String) {
        awaitText(text)
        compose.onAllNodesWithText(text).onFirst().performClick()
    }

    /** Scrolls a plain scrolling column to [text], or asks a lazy list to compose it first. */
    private fun scrollTo(text: String) {
        compose.waitForIdle()
        if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodesWithText(text).onFirst().performScrollTo()
        } else {
            compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        }
    }

    private fun awaitGone(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    /** Plays the system file picker: checks what the app asked for and hands back [file]. */
    private fun answerFilePicker(action: String, file: File): Intent {
        compose.waitForIdle()
        val activity = shadowOf(compose.activity)
        val request = activity.nextStartedActivityForResult
        assertEquals(action, request.intent.action)
        compose.runOnUiThread {
            activity.receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
        return request.intent
    }

    private fun openSettings() {
        tap("Life")
        compose.onNodeWithContentDescription("Settings").performClick()
        awaitText("Weeks start on Monday")
    }

    @Test fun thisWeek() {
        awaitText("Week 1,923 of 4,175")
        compose.onNodeWithText("Week 1,922 is still open").assertIsDisplayed()
        capture("this-week")
        scrollTo("Review")
        capture("this-week-bottom")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 1.0f)
    fun thisWeekAtDefaultSize() {
        awaitText("Week 1,923 of 4,175")
        capture("this-week-default-size")
    }

    @Test @Config(qualifiers = "w366dp-h813dp-night-xxhdpi", fontScale = 1.45f)
    fun thisWeekDark() {
        awaitText("Week 1,923 of 4,175")
        capture("this-week-dark")
    }

    @Test @OnDate("2026-10-03")
    fun weekendOffersTheClose() {
        awaitText("Week 1,923 of 4,175")
        scrollTo("Close the week")
        compose.onNodeWithText("Close the week").assertIsDisplayed()
        capture("this-week-weekend")
    }

    @Test fun addingFillsTheLastSquare() {
        tap("Add another")
        compose.onNodeWithText("From Someday").assertIsDisplayed()
        capture("add-sheet")
        compose.onNode(hasSetTextAction()).performTextInput("Call the bank")
        compose.onNodeWithText("Add").performClick()
        compose.waitForIdle()
        awaitText("Call the bank")
        awaitGone("Add another")
        capture("this-week-full")
    }

    @Test fun closingLastWeekCarriesIntoThisWeek() {
        tap("Close")
        compose.onNodeWithText("Draft the conference talk").assertIsDisplayed()
        capture("close-week")
        scrollTo("Close week")
        tap("Close week")
        compose.onNodeWithText("Choose what happens to 1 unfinished commitment.").assertIsDisplayed()
        capture("close-week-error")
        tap("This week")
        compose.onNode(hasSetTextAction()).performTextInput("Ran all three sessions.")
        tap("Close week")
        compose.waitForIdle()
        awaitText("Week 1,923 of 4,175")
        awaitGone("Week 1,922 is still open")
        awaitText("Draft the conference talk")
        capture("after-close")
    }

    @Test fun lifeGrid() {
        tap("Life")
        awaitText("36.8 years · 1,922 weeks lived")
        awaitText("Week 1,923 · age 36")
        capture("life")
        compose.onNodeWithContentDescription("Life grid", substring = true).performTouchInput {
            click(Offset(width * 0.6f, height * 0.3f))
        }
        awaitGone("Week 1,923 · age 36")
        capture("life-selected")
        compose.onNodeWithContentDescription("Next week").performClick()
        compose.onNode(hasText("· age", substring = true)).performClick()
        awaitText("This week was lived before you started Numbered.")
        capture("week-detail-before")
    }

    @Test fun currentWeekDetail() {
        tap("Life")
        tap("Week 1,923 · age 36")
        awaitText("Renew passport photos")
        capture("week-detail-current")
    }

    @Test fun pastWeekDetail() {
        tap("Life")
        awaitText("Week 1,923 · age 36")
        // Three quick taps with no waiting in between must still move three weeks.
        compose.onNodeWithContentDescription("Previous week").performClick()
        compose.onNodeWithContentDescription("Previous week").performClick()
        compose.onNodeWithContentDescription("Previous week").performClick()
        awaitText("Week 1,920 · age 36")
        capture("life-previous")
        compose.onNodeWithText("Week 1,920 · age 36").performClick()
        capture("week-detail-past")
    }

    @Test @Config(qualifiers = "w366dp-h813dp-night-xxhdpi", fontScale = 1.45f)
    fun lifeGridDark() {
        tap("Life")
        capture("life-dark")
    }

    @Test @Gentle
    fun lifeGridGentle() {
        awaitText("Week 1,923")
        compose.onAllNodesWithText("Week 1,923 of 4,175").assertCountEquals(0)
        tap("Life")
        capture("life-gentle")
    }

    @Test fun someday() {
        tap("Someday")
        awaitText("Still worth a square?")
        capture("someday")
        tap("Keep")
        compose.waitForIdle()
        scrollTo("Let go · 2")
        tap("Let go · 2")
        scrollTo("Start a podcast")
        capture("someday-let-go")
    }

    @Test fun settings() {
        openSettings()
        capture("settings")
    }

    @Test fun importInSettingsReplacesEverything() {
        // Typing into a dialog's text field never lets Compose go idle under Robolectric, so the
        // export dialog is covered by BackupTest, and this test starts from an exported file.
        val export = files.newFile("export.json")
        runBlocking {
            val container = (RuntimeEnvironment.getApplication() as NumberedApp).container
            export.writeText(BackupFormat.encode(container.repository.snapshot()!!))
        }
        openSettings()
        scrollTo("Import a copy")
        capture("settings-data")
        tap("Import a copy")
        answerFilePicker(Intent.ACTION_OPEN_DOCUMENT, export)
        awaitText("Replace everything here?")
        compose.onNodeWithText("The copy saved on Oct 1 will replace everything on this phone.", substring = true).assertIsDisplayed()
        capture("import-replace")
        tap("Replace")
        awaitText("Imported your weeks")
        awaitGone("Replace everything here?")
    }

    @Test fun importRefusesOtherFiles() {
        openSettings()
        scrollTo("Import a copy")
        tap("Import a copy")
        answerFilePicker(Intent.ACTION_OPEN_DOCUMENT, files.newFile("notes.txt").apply { writeText("Groceries: eggs, rice") })
        awaitText("That file is not a Numbered export.")
        compose.onAllNodesWithText("Replace everything here?").assertCountEquals(0)
        capture("import-refused")
    }

    @Test @FreshInstall
    fun restoringOnANewPhone() {
        val copy = files.newFile("numbered-2026-09-30.json")
        val other = NumberedDatabase.inMemory(RuntimeEnvironment.getApplication())
        runBlocking {
            seed(other, defaultToday, gentle = false)
            copy.writeText(BackupFormat.encode(NumberedRepository(other, clockOn(defaultToday.minusDays(1))).snapshot()!!))
        }
        other.close()

        awaitText("Choose your birth date")
        scrollTo("Restore from a file")
        capture("onboarding-bottom")
        tap("Restore from a file")
        answerFilePicker(Intent.ACTION_OPEN_DOCUMENT, copy)
        awaitText("Restore your weeks?")
        compose.onNodeWithText("This copy was saved on Sep 30.").assertIsDisplayed()
        compose.onNodeWithText("37 weeks planned · 32 weeks closed · 5 waiting in Someday").assertIsDisplayed()
        capture("onboarding-restore")
        tap("Restore")
        awaitText("Week 1,923 of 4,175")
        compose.onNodeWithText("Week 1,922 is still open").assertIsDisplayed()
        capture("after-restore")
    }

    @Test @FreshInstall
    fun onboarding() {
        awaitText("Choose your birth date")
        capture("onboarding")
        scrollTo("Start")
        tap("Start")
        compose.onNodeWithText("Choose your birth date to place this week on your grid.").assertExists()
        capture("onboarding-error")
        tap("Choose your birth date")
        capture("onboarding-date-picker")
    }
}
