package com.numbered.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.numbered.app.MainActivity
import com.numbered.app.R
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.calendar
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.closeWeekDeepLink
import java.text.NumberFormat
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class Reminder(val action: String, val id: Int) {
    /** On the week's first day: what deserves it? */
    Plan("com.numbered.app.action.REMIND_PLAN", 1),

    /** On the week's last day: close it with one line. */
    Close("com.numbered.app.action.REMIND_CLOSE", 2),
}

/** The day a reminder falls on, for weeks that start on [firstDay]. */
fun Reminder.dayOf(firstDay: DayOfWeek): DayOfWeek = when (this) {
    Reminder.Plan -> firstDay
    Reminder.Close -> firstDay.minus(1)
}

data class ReminderSettings(
    val planOn: Boolean = false,
    val planAt: LocalTime = LocalTime.of(8, 0),
    val closeOn: Boolean = false,
    val closeAt: LocalTime = LocalTime.of(19, 0),
) {
    fun isOn(reminder: Reminder): Boolean = when (reminder) {
        Reminder.Plan -> planOn
        Reminder.Close -> closeOn
    }

    fun timeOf(reminder: Reminder): LocalTime = when (reminder) {
        Reminder.Plan -> planAt
        Reminder.Close -> closeAt
    }

    fun with(reminder: Reminder, on: Boolean = isOn(reminder), at: LocalTime = timeOf(reminder)) = when (reminder) {
        Reminder.Plan -> copy(planOn = on, planAt = at)
        Reminder.Close -> copy(closeOn = on, closeAt = at)
    }
}

/**
 * The first moment after [now] that falls on [day] at [time], in [now]'s zone. A time skipped
 * by a daylight saving change moves forward to the first valid moment, as the clock does.
 */
fun nextOccurrence(now: ZonedDateTime, day: DayOfWeek, time: LocalTime): ZonedDateTime {
    val date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day))
    val candidate = date.atTime(time).atZone(now.zone)
    return if (candidate.isAfter(now)) candidate else date.plusWeeks(1).atTime(time).atZone(now.zone)
}

/**
 * Reminder choices belong to this phone, like its notification permission, so they live in
 * device preferences rather than in the life data that backups and exports carry.
 */
class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<ReminderSettings> = state.asStateFlow()

    fun update(change: (ReminderSettings) -> ReminderSettings) {
        val next = change(state.value)
        prefs.edit {
            putBoolean(PLAN_ON, next.planOn)
            putInt(PLAN_AT, next.planAt.toSecondOfDay() / 60)
            putBoolean(CLOSE_ON, next.closeOn)
            putInt(CLOSE_AT, next.closeAt.toSecondOfDay() / 60)
        }
        state.value = next
    }

    private fun read(): ReminderSettings {
        val defaults = ReminderSettings()
        fun time(key: String, fallback: LocalTime) =
            prefs.getInt(key, -1).takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) } ?: fallback
        return ReminderSettings(
            planOn = prefs.getBoolean(PLAN_ON, defaults.planOn),
            planAt = time(PLAN_AT, defaults.planAt),
            closeOn = prefs.getBoolean(CLOSE_ON, defaults.closeOn),
            closeAt = time(CLOSE_AT, defaults.closeAt),
        )
    }

    private companion object {
        const val PLAN_ON = "plan_on"
        const val PLAN_AT = "plan_at_minute"
        const val CLOSE_ON = "close_on"
        const val CLOSE_AT = "close_at_minute"
    }
}

/**
 * Schedules the weekly reminders and posts them.
 *
 * Alarms use setAndAllowWhileIdle, which arrives within minutes even in Doze and needs no
 * exact-alarm permission. Each delivery schedules the next week's, and boot, clock, time zone,
 * and app updates reschedule from scratch.
 */
class Reminders(
    private val context: Context,
    private val repository: NumberedRepository,
    private val clock: Clock,
) {
    val store = ReminderStore(context)
    private val alarms: AlarmManager = requireNotNull(context.getSystemService())
    private val notifications = NotificationManagerCompat.from(context)

    fun createChannel() {
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_name))
                .setDescription(context.getString(R.string.notification_channel_description))
                .build(),
        )
    }

    /** Whether posted reminders can be seen at all. */
    fun canNotify(): Boolean = notifications.areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    suspend fun reschedule() {
        val profile = repository.currentProfile()
        val settings = store.settings.value
        val now = ZonedDateTime.now(clock)
        Reminder.entries.forEach { reminder ->
            val alarm = alarmIntent(reminder)
            if (profile == null || !settings.isOn(reminder)) {
                alarms.cancel(alarm)
            } else {
                val at = nextOccurrence(now, reminder.dayOf(profile.firstDayOfWeek), settings.timeOf(reminder))
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), alarm)
            }
        }
    }

    /** Posts [reminder] when it still has something to say, then schedules next week's. */
    suspend fun deliver(reminder: Reminder) {
        try {
            post(reminder)
        } finally {
            reschedule()
        }
    }

    private suspend fun post(reminder: Reminder) {
        val profile = repository.currentProfile() ?: return
        if (!store.settings.value.isOn(reminder) || !canNotify()) return
        val today = LocalDate.now(clock)
        // An alarm held back past its day, by a phone that was off, would speak about the wrong week.
        if (today.dayOfWeek != reminder.dayOf(profile.firstDayOfWeek)) return
        val calendar = profile.calendar()
        val weekStart = calendar.weekStartOf(today)
        val week = repository.week(weekStart).first()
        val number = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(calendar.indexOf(today) + 1)
        val (title, text, open) = when (reminder) {
            Reminder.Plan -> {
                if (week.any { it.status == CommitmentStatus.Open || it.status == CommitmentStatus.Done }) return
                Triple(
                    context.getString(R.string.add_heading_this_week),
                    context.getString(R.string.notification_plan_text, number),
                    Intent(context, MainActivity::class.java),
                )
            }
            Reminder.Close -> {
                if (repository.review(weekStart).first() != null) return
                val open = week.count { it.status == CommitmentStatus.Open }
                Triple(
                    context.getString(R.string.close_prompt_title),
                    if (open > 0) {
                        context.resources.getQuantityString(R.plurals.unclosed_body_open, open, open)
                    } else {
                        context.getString(R.string.unclosed_body_note)
                    },
                    Intent(Intent.ACTION_VIEW, closeWeekDeepLink(weekStart), context, MainActivity::class.java),
                )
            }
        }
        // A fresh task, so the notification always lands on the screen it names.
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(
                PendingIntent.getActivity(context, reminder.id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(reminder.id, notification)
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and the post.
        }
    }

    private fun alarmIntent(reminder: Reminder): PendingIntent = PendingIntent.getBroadcast(
        context,
        reminder.id,
        Intent(context, ReminderReceiver::class.java).setAction(reminder.action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL = "weekly"
    }
}
