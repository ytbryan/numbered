package com.numbered.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.numbered.app.data.Commitment
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.Profile
import com.numbered.app.domain.CommitmentStatus
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The weekly loop on a real Android system: close last week with one line, plan this week, and keep
 * it all across the activity being recreated. The app runs on a fresh in-memory database, so a
 * phone's own weeks are never read or changed.
 */
@RunWith(AndroidJUnit4::class)
class CoreJourneyTest {
    private val app = ApplicationProvider.getApplicationContext<NumberedApp>()
    private val zone = ZoneId.systemDefault()

    // Far from today, so nothing kept on the device for a real week shares its key.
    private val today = LocalDate.of(2031, 3, 12)
    private val thisWeek = LocalDate.of(2031, 3, 10)
    private val lastWeek = thisWeek.minusWeeks(1)
    private lateinit var database: NumberedDatabase
    private lateinit var scenario: ActivityScenario<MainActivity>

    @get:Rule val compose = createEmptyComposeRule()

    @Before fun openOnSeededWeeks() {
        database = NumberedDatabase.inMemory(app)
        val container = AppContainer(app, database, Clock.fixed(today.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant(), zone))
        assumeFalse("App lock is on for this device, so the app would open locked", container.appLock.enabled.value)
        app.replaceContainer(container)
        runBlocking {
            database.profiles().upsert(
                Profile(
                    birthDate = LocalDate.of(1990, 5, 1),
                    horizonYears = 80,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    gentle = false,
                    startedOn = LocalDate.of(2031, 1, 6),
                ),
            )
            database.commitments().insert(Commitment(weekStart = lastWeek, title = "Draft the report", createdAt = 1))
            database.commitments().insert(Commitment(weekStart = thisWeek, title = "Renew passport", createdAt = 2))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario.close()
        database.close()
    }

    @Test fun closeLastWeekPlanThisWeekAndKeepItAll() {
        awaitText("Last week is still open")
        tap("Close")
        awaitText("Draft the report")
        compose.onAllNodes(hasText("This week") and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Steady week, report half done.")
        compose.onAllNodes(hasText("Close week") and hasClickAction()).onFirst().performScrollTo().performClick()

        awaitGone("Last week is still open")
        awaitText("Draft the report")
        tap("Add another")
        compose.onNode(hasSetTextAction()).performTextInput("Call the bank")
        compose.onAllNodes(hasText("Add") and hasClickAction()).onFirst().performClick()
        awaitText("Call the bank")

        val week = runBlocking { app.container.repository.week(thisWeek).first() }
        assertEquals(listOf("Renew passport", "Draft the report", "Call the bank"), week.map { it.title })
        assertEquals(CommitmentStatus.Carried, runBlocking { database.commitments().week(lastWeek).single().status })

        scenario.recreate()
        awaitText("Renew passport")
        awaitText("Call the bank")

        tap("Life")
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasContentDescription("Your lines")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasContentDescription("Your lines")).onFirst().performClick()
        awaitText("Steady week, report half done.")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitGone(text: String) {
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty() }
    }

    private fun tap(text: String) {
        awaitText(text)
        compose.onAllNodes(hasText(text)).onFirst().performClick()
    }

    private companion object {
        /** Generous, since emulators in CI are slow. */
        const val TIMEOUT = 15_000L
    }
}
