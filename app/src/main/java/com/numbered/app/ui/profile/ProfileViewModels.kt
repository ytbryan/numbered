package com.numbered.app.ui.profile

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.Profile
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.ui.NoticeViewModel
import java.time.DayOfWeek
import java.time.LocalDate
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

    fun completeSetup(birthDate: LocalDate, horizonYears: Int, gentle: Boolean, firstDayOfWeek: DayOfWeek) = launchWrite {
        container.repository.saveProfile(birthDate, horizonYears, gentle, firstDayOfWeek)
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

    private fun update(change: (Profile) -> Profile) = launchWrite {
        val current = profile.value ?: return@launchWrite
        val next = change(current)
        container.repository.saveProfile(next.birthDate, next.horizonYears, next.gentle, next.firstDayOfWeek)
    }
}
