package com.numbered.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.numbered.app.appContainer
import kotlinx.coroutines.launch

/** Receives the weekly alarms. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = Reminder.entries.firstOrNull { it.action == intent.action } ?: return
        val container = context.appContainer
        val pending = goAsync()
        container.scope.launch {
            try {
                container.reminders.deliver(reminder)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms do not survive a reboot, and a new clock or time zone moves every reminder. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = context.appContainer
        val pending = goAsync()
        container.scope.launch {
            try {
                container.reminders.reschedule()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
