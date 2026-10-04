package com.numbered.app

import android.app.Application
import android.content.Context
import androidx.annotation.VisibleForTesting
import com.numbered.app.backup.AutoBackup
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.ReflectionDrafts
import com.numbered.app.data.BackupSafety
import com.numbered.app.reminders.Reminders
import com.numbered.app.widget.keepWidgetsCurrent
import com.numbered.app.security.AppLock
import com.numbered.app.security.KeystoreVault
import com.numbered.app.security.PassphraseVault
import com.numbered.app.ui.AgeDisplayStore
import com.numbered.app.ui.theme.ThemeStore
import com.numbered.app.ui.week.WeekProgressStore
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NumberedApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, NumberedDatabase.open(this), DeviceClock())
        val started = container
        started.reminders.createChannel()
        started.scope.launch { started.reminders.reschedule() }
        started.scope.launch { keepWidgetsCurrent(this@NumberedApp, started) }
        started.autoBackup.keepScheduled()
    }

    @VisibleForTesting
    fun replaceContainer(replacement: AppContainer) {
        container.scope.cancel()
        container = replacement
    }
}

class AppContainer(
    context: Context,
    val database: NumberedDatabase,
    val clock: Clock,
    vault: PassphraseVault = KeystoreVault(),
) {
    val repository = NumberedRepository(database, clock)
    val today = Today(clock)
    val drafts = ReflectionDrafts(context.applicationContext)
    val backupSafety = BackupSafety(context.applicationContext)
    val appLock = AppLock(context.applicationContext)
    val themes = ThemeStore(context.applicationContext)
    val ageDisplay = AgeDisplayStore(context.applicationContext)
    val weekProgress = WeekProgressStore(context.applicationContext)

    /** Work that outlives a screen, such as scheduling reminders. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val reminders = Reminders(context.applicationContext, repository, clock)
    val autoBackup = AutoBackup(context.applicationContext, repository, clock, vault)
}

/**
 * The system clock in whatever zone the phone is in now. Clock.systemDefaultZone() fixes the zone
 * when it is created, so travelling would leave dates and reminders in the old zone.
 */
class DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = system(zone)

    override fun instant(): Instant = Instant.now()
}

/** The current date as a stream, so every screen rolls over together at midnight and on resume. */
class Today(private val clock: Clock) {
    private val date = MutableStateFlow(LocalDate.now(clock))
    val value: StateFlow<LocalDate> = date.asStateFlow()

    fun refresh() {
        date.value = LocalDate.now(clock)
    }

    fun nowMillis(): Long = clock.millis()
}

val Context.appContainer: AppContainer get() = (applicationContext as NumberedApp).container
