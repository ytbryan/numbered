package com.numbered.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.domain.CloseChoice
import com.numbered.app.reminders.Reminder
import com.numbered.app.reminders.RescheduleReceiver
import com.numbered.app.reminders.nextOccurrence
import com.numbered.app.ui.closeWeekDeepLink
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = NumberedDatabase.inMemory(app)
    private val thisWeek = LocalDate.of(2026, 9, 28)

    @get:Rule val compose = createEmptyComposeRule()

    @After fun close() = db.close()

    private fun containerAt(dateTime: LocalDateTime): AppContainer {
        val container = AppContainer(app, db, Clock.fixed(dateTime.atZone(zone).toInstant(), zone))
        (app as NumberedApp).replaceContainer(container)
        runBlocking {
            if (container.repository.currentProfile() == null) {
                container.repository.saveProfile(LocalDate.of(1989, 12, 2), 80, gentle = false, firstDayOfWeek = DayOfWeek.MONDAY)
            }
        }
        container.reminders.createChannel()
        return container
    }

    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    private fun allowNotifications() = shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun turnOn(container: AppContainer, reminder: Reminder) = runBlocking {
        container.reminders.store.update { it.with(reminder, on = true) }
        container.reminders.reschedule()
    }

    private fun millis(dateTime: LocalDateTime) = dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test fun nextOccurrenceIsAlwaysAhead() {
        val sunday7pm = LocalTime.of(19, 0)
        fun next(now: String) = nextOccurrence(ZonedDateTime.of(LocalDateTime.parse(now), zone), DayOfWeek.SUNDAY, sunday7pm).toLocalDateTime()
        assertEquals(LocalDateTime.parse("2026-10-04T19:00"), next("2026-10-01T10:00"))
        assertEquals(LocalDateTime.parse("2026-10-04T19:00"), next("2026-10-04T18:59"))
        // At the moment itself, the next one is a week away, so a delivered alarm never repeats.
        assertEquals(LocalDateTime.parse("2026-10-11T19:00"), next("2026-10-04T19:00"))
        assertEquals(LocalDateTime.parse("2026-10-11T19:00"), next("2026-10-04T21:00"))
    }

    @Test fun aTimeSkippedByDaylightSavingMovesForward() {
        val newYork = ZoneId.of("America/New_York")
        // Clocks jump from 02:00 to 03:00 on Sunday 8 March 2026.
        val next = nextOccurrence(ZonedDateTime.of(2026, 3, 5, 12, 0, 0, 0, newYork), DayOfWeek.SUNDAY, LocalTime.of(2, 30))
        assertEquals(LocalDateTime.parse("2026-03-08T03:30"), next.toLocalDateTime())
    }

    @Test fun turningRemindersOnAndOffSchedulesAlarms() {
        val container = containerAt(LocalDateTime.parse("2026-10-01T10:00"))
        turnOn(container, Reminder.Close)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(millis(LocalDateTime.parse("2026-10-04T19:00")), alarms.scheduledAlarms.single().triggerAtTime)

        turnOn(container, Reminder.Plan)
        assertEquals(
            setOf(millis(LocalDateTime.parse("2026-10-04T19:00")), millis(LocalDateTime.parse("2026-10-05T08:00"))),
            alarms.scheduledAlarms.map { it.triggerAtTime }.toSet(),
        )

        runBlocking {
            container.reminders.store.update { it.with(Reminder.Close, on = false).with(Reminder.Plan, on = false) }
            container.reminders.reschedule()
        }
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test fun theCloseReminderOpensTheCloseOfThisWeek() {
        allowNotifications()
        val container = containerAt(LocalDateTime.parse("2026-10-04T19:00"))
        runBlocking { container.repository.addCommitment(thisWeek, "Finish the grant draft") }
        turnOn(container, Reminder.Close)

        runBlocking { container.reminders.deliver(Reminder.Close) }

        val posted = notifications.allNotifications.single()
        assertEquals("Close the week", posted.extras.getString("android.title"))
        assertEquals("1 unfinished. Decide what happens to it and write one line.", posted.extras.getString("android.text"))
        val opens = shadowOf(posted.contentIntent).savedIntent
        assertEquals(closeWeekDeepLink(thisWeek), opens.data)
        assertTrue(opens.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        // Delivering schedules next week's.
        assertEquals(millis(LocalDateTime.parse("2026-10-11T19:00")), alarms.scheduledAlarms.single().triggerAtTime)
    }

    @Test fun remindersStayQuietWhenThereIsNothingToDo() {
        allowNotifications()
        val container = containerAt(LocalDateTime.parse("2026-10-04T19:00"))
        runBlocking { container.repository.closeWeek(thisWeek, "Done.", emptyMap<Long, CloseChoice>(), thisWeek.plusWeeks(1)) }
        turnOn(container, Reminder.Close)
        runBlocking { container.reminders.deliver(Reminder.Close) }
        assertTrue("A closed week needs no reminder", notifications.allNotifications.isEmpty())

        // An alarm held back until the next week, by a phone that was off, says nothing.
        val late = containerAt(LocalDateTime.parse("2026-10-05T09:00"))
        turnOn(late, Reminder.Close)
        runBlocking { late.reminders.deliver(Reminder.Close) }
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test fun thePlanReminderOnlyAsksAboutAnEmptyWeek() {
        allowNotifications()
        val nextMonday = thisWeek.plusWeeks(1)
        val container = containerAt(nextMonday.atTime(8, 0))
        turnOn(container, Reminder.Plan)
        runBlocking { container.reminders.deliver(Reminder.Plan) }
        val posted = notifications.allNotifications.single()
        assertEquals("What deserves this week?", posted.extras.getString("android.title"))
        assertEquals("Week 1,924 has begun. Choose up to three things.", posted.extras.getString("android.text"))
        assertNull(shadowOf(posted.contentIntent).savedIntent.data)

        app.getSystemService(NotificationManager::class.java).cancelAll()
        runBlocking { container.repository.addCommitment(nextMonday, "Book flights to Osaka") }
        runBlocking { container.reminders.deliver(Reminder.Plan) }
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test fun nothingIsPostedWithoutPermission() {
        val container = containerAt(LocalDateTime.parse("2026-10-04T19:00"))
        turnOn(container, Reminder.Close)
        runBlocking { container.reminders.deliver(Reminder.Close) }
        assertTrue(notifications.allNotifications.isEmpty())
        // It still keeps the schedule, so allowing notifications later is enough.
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test fun rebootsAndTimeChangesReachTheRescheduler() {
        // Alarms vanish on reboot and drift with the clock, so these broadcasts must reach the app.
        listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
            .forEach { action ->
                val receivers = app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0)
                // Libraries such as Glance may listen too, so only ours is required.
                assertTrue(action, RescheduleReceiver::class.java.name in receivers.map { it.activityInfo.name })
            }
    }

    @Test fun tappingTheCloseReminderOpensTheCloseScreen() {
        containerAt(LocalDateTime.parse("2026-10-04T19:00"))
        val intent = Intent(Intent.ACTION_VIEW, closeWeekDeepLink(thisWeek), app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Close week 1,923").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Close week 1,923").assertExists()
        }
        runBlocking { assertTrue(db.reviews().observe(thisWeek).first() == null) }
    }
}
