package com.numbered.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextReplacement
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
import org.junit.Assert.assertNull
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
annotation class WeekStarts(val day: DayOfWeek)

@Retention(AnnotationRetention.RUNTIME)
annotation class Gentle

/** Seeds the usual history, then opens the app this many weeks later, as if back from time away. */
@Retention(AnnotationRetention.RUNTIME)
annotation class AwayFor(val weeks: Int)

/**
 * Drives the real app on seeded data and renders each screen to app/build/screens for review.
 * Most captures use a 366dp-wide phone at 145% text, the size the app must look right at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w366dp-h813dp-xxhdpi", fontScale = 1.45f)
class ScreenCaptureTest {
    private val zone = ZoneId.of("Asia/Singapore")

    private companion object {
        /** Weekly lines for the seeded history, in order. */
        val SEED_LINES = listOf(
            "A steady week.",
            "Quiet week. Slept well.",
            "Booked the Osaka trip at last.",
            "Mum's checkup went fine.",
            "Shipped the grant outline.",
            "Too many meetings, still finished the draft.",
            "Long walk by the reservoir on Sunday.",
            "Started planning Osaka with Mei.",
            "Hard week, but kept the three.",
            "Osaka hotel sorted.",
        )
    }
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
            val away = description.getAnnotation(AwayFor::class.java)?.weeks?.toLong() ?: 0
            val clock = clockOn(today.plusWeeks(away))
            val app = RuntimeEnvironment.getApplication() as NumberedApp
            database = NumberedDatabase.inMemory(app)
            app.replaceContainer(AppContainer(app, database, clock))
            if (description.getAnnotation(FreshInstall::class.java) == null) {
                runBlocking {
                    seed(
                        database,
                        today,
                        gentle = description.getAnnotation(Gentle::class.java) != null,
                        firstDay = description.getAnnotation(WeekStarts::class.java)?.day ?: DayOfWeek.MONDAY,
                    )
                }
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

    private suspend fun seed(db: NumberedDatabase, today: LocalDate, gentle: Boolean, firstDay: DayOfWeek = DayOfWeek.MONDAY) {
        val thisWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDay))
        val started = LocalDate.of(2026, 1, 5)
        db.profiles().upsert(
            Profile(
                birthDate = LocalDate.of(1989, 12, 2),
                horizonYears = 80,
                firstDayOfWeek = firstDay,
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
            if (n % 7 != 6) db.reviews().upsert(WeekReview(week, if (n % 2 == 0) SEED_LINES[n / 2 % SEED_LINES.size] else "", millis(week.plusDays(6), 20)))
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

    @Test fun aChapterFromStartToGrid() {
        tap("Life")
        scrollTo("Chapters")
        awaitText("Open any week to start one there.")
        tap("Week 1,923 · age 36")
        tap("Start a chapter here")
        awaitText("New chapter")
        compose.onNode(hasSetTextAction()).performTextInput("Moved to Singapore")
        tap("Still going")
        capture("chapter-new")
        tap("Save")
        awaitGone("New chapter")
        awaitText("Start a chapter here")
        compose.onNodeWithText("Moved to Singapore").assertIsDisplayed()
        capture("week-detail-chapter")
        compose.onNodeWithContentDescription("Back").performClick()
        scrollTo("Moved to Singapore")
        compose.onNodeWithText("Age 36 · Sep 2026 – now").assertIsDisplayed()
        capture("life-chapters")

        tap("Moved to Singapore")
        awaitText("Edit chapter")
        compose.onNodeWithContentDescription("Delete chapter").performClick()
        tap("Delete")
        awaitText("Your life")
        scrollTo("Chapters")
        compose.onAllNodesWithText("Moved to Singapore").assertCountEquals(0)
    }

    @Test fun linesGatherEveryWeeklyNote() {
        tap("Life")
        compose.onNodeWithContentDescription("Your lines").performClick()
        awaitText("2026")
        capture("lines")
        compose.onNodeWithText("32\u00A0weeks closed · 77\u00A0things done · 16\u00A0lines").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("osaka")
        awaitText("4\u00A0lines")
        compose.onAllNodesWithText("Quiet week. Slept well.").assertCountEquals(0)
        capture("lines-search")
        compose.onAllNodesWithText("Osaka", substring = true).onFirst().performClick()
        awaitText("Age 36")
        capture("lines-open-week")
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

    @Test @OnDate("2026-10-02") @WeekStarts(DayOfWeek.SUNDAY)
    fun todayIsClearAcrossAMonthBoundaryWithASundayStart() {
        awaitText("This week")
        compose.onNodeWithText("Day 6 of 7").assertDoesNotExist()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Today · Friday, Oct 2", useUnmergedTree = true).fetchSemanticsNodes().size >= 2
        }
        val today = compose.onAllNodesWithContentDescription("Today · Friday, Oct 2", useUnmergedTree = true).onLast().fetchSemanticsNode()
        val first = compose.onNodeWithContentDescription("Sunday, Sep 27", useUnmergedTree = true).fetchSemanticsNode()
        val last = compose.onNodeWithContentDescription("Saturday, Oct 3", useUnmergedTree = true).fetchSemanticsNode()
        org.junit.Assert.assertTrue(today.boundsInRoot.left > first.boundsInRoot.right)
        org.junit.Assert.assertTrue(today.boundsInRoot.right <= last.boundsInRoot.left)
        capture("this-week-today")
        tap("Life")
        compose.onNodeWithContentDescription("Today · Friday, Oct 2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2026 · week 39 of 52").assertIsDisplayed()
        awaitText("Week 1,923 · age 36")
        capture("life-today")
    }

    @Test fun reflectionDraftSurvivesLeavingAndReopeningTheWeek() {
        tap("Close")
        compose.onNode(hasSetTextAction()).performTextInput("An unfinished reflection.")
        compose.onNodeWithContentDescription("Back").performClick()
        tap("Close")
        compose.onNodeWithText("An unfinished reflection.").assertIsDisplayed()
        capture("close-week-draft")
        val container = (RuntimeEnvironment.getApplication() as NumberedApp).container
        val lastWeek = defaultToday.with(DayOfWeek.MONDAY).minusWeeks(1)
        assertEquals("An unfinished reflection.", com.numbered.app.data.ReflectionDrafts(compose.activity).read(lastWeek))
        tap("This week")
        scrollTo("Close week")
        tap("Close week")
        awaitText("Week 1,923 of 4,175")
        awaitGone("Week 1,922 is still open")
        assertNull(container.drafts.read(lastWeek))
    }

    @Test fun largerGridSelectsTheCorrectWeekAndJumpsBetweenYears() {
        tap("Life")
        tap("Larger weeks")
        compose.onNodeWithText("2026").assertIsDisplayed()
        capture("life-larger")
        compose.onNodeWithContentDescription("Weeks in 2026", substring = true).performTouchInput {
            click(Offset(width / 14f, 10f))
        }
        val first = com.numbered.app.ui.life.yearWindow(
            com.numbered.app.domain.LifeCalendar(LocalDate.of(1989, 12, 2), 80, DayOfWeek.MONDAY), 2026, 4175,
        ).first
        awaitText("Week ${java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(first + 1)} · age 36")
        compose.onNodeWithContentDescription("Previous year").performClick()
        awaitText("2025")
        tap("Jump to year")
        tap("2024")
        compose.onNodeWithContentDescription("Weeks in 2024", substring = true).assertExists()
        capture("life-year-jump")
        tap("This week")
        awaitText("Week 1,923 · age 36")
        tap("Whole life")
        compose.onNodeWithContentDescription("Life grid", substring = true).assertExists()
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

    @Test fun somedaySearchFindsLetGoIdeasAndClears() {
        tap("Someday")
        awaitText("Still worth a square?")
        compose.onNodeWithContentDescription("Search ideas").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("  VIOLIN  ")
        awaitText("Learn the violin")
        compose.onNodeWithText("Learn the violin").assertIsDisplayed()
        compose.onNodeWithText("Learn to sail").assertDoesNotExist()
        capture("someday-search")
        compose.onNode(hasSetTextAction()).performTextReplacement("no such idea")
        awaitText("No matching ideas")
        compose.onNodeWithContentDescription("Clear search").performClick()
        awaitText("Still worth a square?")
        compose.onNodeWithContentDescription("Search ideas").performClick()
        compose.onAllNodesWithText("Learn to sail").assertCountEquals(2)
    }

    @Test fun somedaySortChangesTheVisibleOrder() {
        val container = (RuntimeEnvironment.getApplication() as NumberedApp).container
        runBlocking {
            val now = container.clock.millis()
            container.database.someday().insert(SomedayItem(title = "Sort older", createdAt = now - 172_800_000L))
            container.database.someday().insert(SomedayItem(title = "Sort newer", createdAt = now - 86_400_000L))
        }
        tap("Someday")
        awaitText("Still worth a square?")
        compose.onNodeWithContentDescription("Search ideas").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Sort ")
        awaitText("Sort older")
        fun top(title: String) = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue(top("Sort older") < top("Sort newer"))
        compose.onNodeWithContentDescription("Sort ideas").performClick()
        tap("Newest first")
        compose.waitForIdle()
        org.junit.Assert.assertTrue(top("Sort newer") < top("Sort older"))
        capture("someday-sorted")
    }

    @Test fun globalSearchOpensNotesCommitmentsAndLetGoIdeas() {
        awaitText("Renew passport photos")
        compose.onNodeWithContentDescription("Search everything").performClick()
        awaitText("Search your weeks and ideas.")
        compose.onNode(hasSetTextAction()).performTextInput("  grant  ")
        awaitText("Commitments · 2")
        awaitText("Weekly notes ·")
        capture("search-everything")
        tap("Finish the grant draft")
        awaitText("Week 1,923")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("A steady week.")
        awaitText("Weekly notes ·")
        val noteResult = hasText("A steady week.") and !hasSetTextAction()
        compose.waitUntil(5_000) { compose.onAllNodes(noteResult).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(noteResult).onFirst().performClick()
        awaitText("“A steady week.”")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("VIOLIN")
        awaitText("Learn the violin")
        tap("Learn the violin")
        awaitText("Let go · 1")
        compose.onNode(hasText("Learn the violin") and !hasSetTextAction()).assertIsDisplayed()
        capture("search-open-someday")
    }

    @Test fun carryHistoryConnectsRenamedEntriesAndOpensTheirWeeks() {
        val container = (RuntimeEnvironment.getApplication() as NumberedApp).container
        val start = LocalDate.of(2026, 9, 7)
        runBlocking {
            val repository = container.repository
            // These historical weeks already have seeded plans; make room in this isolated test.
            for (week in listOf(start, start.plusWeeks(1))) {
                container.database.commitments().week(week).forEach { container.database.commitments().delete(it.id) }
            }
            assertEquals(com.numbered.app.data.PlanResult.Ok, repository.addCommitment(start, "Original talk"))
            val original = container.database.commitments().week(start).single { it.title == "Original talk" }
            repository.carry(original.id, start.plusWeeks(1))
            val middle = container.database.commitments().week(start.plusWeeks(1)).single { it.carriedFromId == original.id }
            repository.rename(middle.id, "Revised talk")
            repository.carry(middle.id, LocalDate.of(2026, 9, 28))
        }
        awaitText("Revised talk")
        scrollTo("Revised talk")
        compose.onNodeWithContentDescription("More options for Revised talk").performClick()
        tap("Carry-over history")
        awaitText("Carried 2 times")
        compose.onNodeWithText("Original talk").assertIsDisplayed()
        capture("carry-history")
        compose.onNode(hasText("Sep 7", substring = true)).performClick()
        awaitText("Week 1,920")
        awaitText("Original talk")
        compose.onNodeWithContentDescription("More options for Original talk").performClick()
        tap("Carry-over history")
        awaitText("Carried 2 times")
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

    @Test fun importSavesARecoveryCopyAndItCanBeRestored() {
        val container = (RuntimeEnvironment.getApplication() as NumberedApp).container
        val export = files.newFile("empty-copy.json")
        val original = runBlocking { container.repository.snapshot()!! }
        export.writeText(BackupFormat.encode(original.copy(commitments = emptyList(), someday = emptyList(), reviews = emptyList(), chapters = emptyList())))
        container.drafts.save(defaultToday.with(DayOfWeek.MONDAY), "Old draft")
        openSettings()
        scrollTo("Import a copy")
        tap("Import a copy")
        answerFilePicker(Intent.ACTION_OPEN_DOCUMENT, export)
        awaitText("Replace everything here?")
        tap("Replace")
        awaitText("Imported your weeks")
        awaitGone("Replace everything here?")
        assertEquals(emptyList<com.numbered.app.data.Commitment>(), runBlocking { container.repository.snapshot()!!.commitments })
        assertEquals(original, runBlocking { container.backupSafety.readRecovery() })
        assertNull(container.drafts.read(defaultToday.with(DayOfWeek.MONDAY)))
        scrollTo("Restore previous data")
        capture("settings-recovery")
        tap("Restore previous data")
        awaitText("Replace everything here?")
        capture("recovery-confirm")
        tap("Replace")
        awaitGone("Replace everything here?")
        assertEquals(original, runBlocking { container.repository.snapshot()!! })
    }

    @Test fun turningOnTheCloseReminder() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        openSettings()
        scrollTo("Reminders")
        compose.onNodeWithContentDescription("Close the week").performClick()
        awaitText("Sunday at 7:00")
        val alarms = shadowOf(RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java))
        compose.waitUntil(5_000) { alarms.scheduledAlarms.isNotEmpty() }
        capture("settings-reminders")
        compose.onNodeWithContentDescription("Change the time for Close the week").performClick()
        awaitText("Remind me at")
        capture("reminder-time")
    }

    @Test @AwayFor(weeks = 3)
    fun catchingUpAfterThreeWeeksAway() {
        awaitText("3 weeks to close")
        compose.onNodeWithText("3 unfinished across them", substring = true).assertIsDisplayed()
        capture("this-week-catch-up")
        tap("Catch up")
        awaitText("Week 1,922 · ")
        capture("catch-up")
        // Bring one thing into this week, then let the rest go in one tap.
        compose.onAllNodesWithText("This week").onFirst().performClick()
        tap("Let go")
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Away in Osaka.")
        scrollTo("Close 3 weeks")
        capture("catch-up-decided")
        tap("Close 3 weeks")
        awaitText("Closed 3 weeks")
        awaitText("Draft the conference talk")
        compose.onAllNodesWithText("3 weeks to close").assertCountEquals(0)
        capture("after-catch-up")
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
