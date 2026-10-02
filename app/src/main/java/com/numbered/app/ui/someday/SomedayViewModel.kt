package com.numbered.app.ui.someday

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.R
import com.numbered.app.data.PlanResult
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.calendar
import com.numbered.app.domain.isStale
import com.numbered.app.ui.Notice
import com.numbered.app.ui.NoticeViewModel
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

data class SomedayRow(val item: SomedayItem, val weeksWaiting: Int, val stale: Boolean)

data class SomedayState(
    val stale: List<SomedayRow>,
    val waiting: List<SomedayRow>,
    val letGo: List<SomedayItem>,
    val thisWeekStart: LocalDate,
    val nextWeekStart: LocalDate,
)

class SomedayViewModel(private val container: AppContainer) : NoticeViewModel() {
    private val repository = container.repository

    val state: StateFlow<SomedayState?> = combine(
        repository.profile().filterNotNull(),
        container.today.value,
        repository.somedayWaiting(),
        repository.somedayLetGo(),
    ) { profile, today, waiting, letGo ->
        val now = container.today.nowMillis()
        val thisWeek = profile.calendar().weekStartOf(today)
        val rows = waiting.map { item ->
            SomedayRow(
                item = item,
                weeksWaiting = (Duration.ofMillis(now - item.createdAt).toDays() / 7).toInt().coerceAtLeast(0),
                stale = isStale(item.createdAt, item.keptAt, now),
            )
        }
        SomedayState(
            stale = rows.filter { it.stale },
            waiting = rows.filterNot { it.stale },
            letGo = letGo,
            thisWeekStart = thisWeek,
            nextWeekStart = thisWeek.plusWeeks(1),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(title: String) = launchWrite { repository.addSomeday(title) }

    fun keep(item: SomedayItem) = launchWrite { repository.keepSomeday(item.id) }

    fun letGo(item: SomedayItem) = launchWrite {
        repository.letGoSomeday(item.id)
        notify(Notice(R.string.notice_let_go, listOf(item.title)) { launchWrite { repository.bringBackSomeday(item.id) } })
    }

    fun bringBack(item: SomedayItem) = launchWrite { repository.bringBackSomeday(item.id) }

    fun delete(item: SomedayItem) = launchWrite {
        val removed = repository.deleteSomeday(item.id) ?: return@launchWrite
        notify(Notice(R.string.notice_deleted, listOf(removed.title)) { launchWrite { repository.restoreSomeday(removed) } })
    }

    fun scheduleThisWeek(item: SomedayItem) = schedule(item, thisWeek = true)

    fun scheduleNextWeek(item: SomedayItem) = schedule(item, thisWeek = false)

    private fun schedule(item: SomedayItem, thisWeek: Boolean) = launchWrite {
        val current = state.value ?: return@launchWrite
        val result = repository.schedule(item.id, if (thisWeek) current.thisWeekStart else current.nextWeekStart)
        if (result == PlanResult.Ok) {
            notify(Notice(if (thisWeek) R.string.notice_added_this_week else R.string.notice_added_next_week, listOf(item.title)))
        } else {
            report(result)
        }
    }
}
