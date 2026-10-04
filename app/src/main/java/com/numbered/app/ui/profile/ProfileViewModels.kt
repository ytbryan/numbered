package com.numbered.app.ui.profile

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.Profile
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.domain.MAX_PRIORITIES_PER_WEEK
import com.numbered.app.reminders.Reminder
import com.numbered.app.reminders.ReminderSettings
import com.numbered.app.ui.NoticeViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface RootState {
    data object Loading : RootState
    data object NeedsSetup : RootState
    data object Ready : RootState
}

class RootViewModel(private val container: AppContainer) : NoticeViewModel() {
    val state: StateFlow<RootState> = container.repository.profile()
        .map { if (it == null) RootState.NeedsSetup else RootState.Ready }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    val today: StateFlow<LocalDate> = container.today.value

    fun completeSetup(birthDate: LocalDate, horizonYears: Int, firstDayOfWeek: DayOfWeek) = launchWrite {
        container.repository.createProfile(birthDate, horizonYears, firstDayOfWeek)
    }
}

class SettingsViewModel(private val container: AppContainer) : NoticeViewModel() {
    val profile: StateFlow<Profile?> = container.repository.profile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val today: StateFlow<LocalDate> = container.today.value

    fun setBirthDate(date: LocalDate) = update { profile ->
        val horizon = profile.horizonYears.takeIf { horizonAvailable(it, date, today.value) }
            ?: LifeCalendar.defaultHorizon(date, today.value)
        profile.copy(birthDate = date, horizonYears = horizon)
    }

    fun setHorizon(years: Int) = update { it.copy(horizonYears = years) }

    fun setGentle(gentle: Boolean) = update { it.copy(gentle = gentle) }

    fun setPrioritiesPerWeek(count: Int) = launchWrite {
        if (count !in 1..MAX_PRIORITIES_PER_WEEK) return@launchWrite
        container.repository.setPrioritiesPerWeek(count)
    }

    fun setOtherThingsDoneEnabled(enabled: Boolean) = launchWrite {
        container.repository.setOtherThingsDoneEnabled(enabled)
    }

    val reminders: StateFlow<ReminderSettings> = container.reminders.store.settings

    fun canNotify(): Boolean = container.reminders.canNotify()

    fun setReminder(reminder: Reminder, on: Boolean) = updateReminders { it.with(reminder, on = on) }

    fun setReminderTime(reminder: Reminder, at: LocalTime) = updateReminders { it.with(reminder, at = at) }

    private fun updateReminders(change: (ReminderSettings) -> ReminderSettings) = launchWrite {
        container.reminders.store.update(change)
        container.reminders.reschedule()
    }

    private fun update(change: (Profile) -> Profile) = launchWrite {
        val current = profile.value ?: return@launchWrite
        val next = change(current)
        container.repository.saveProfile(next.birthDate, next.horizonYears, next.gentle, next.firstDayOfWeek)
    }
}
