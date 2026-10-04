package com.numbered.app.ui.weekdetail

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.R
import com.numbered.app.data.Chapter
import com.numbered.app.data.Commitment
import com.numbered.app.data.OtherThingDone
import com.numbered.app.data.PlanResult
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.calendar
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.LifeWeekMoment
import com.numbered.app.ui.Notice
import com.numbered.app.ui.NoticeViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

enum class WeekTime { Past, Current, Future }

data class WeekDetailState(
    val today: LocalDate,
    val zone: ZoneId,
    val weekStart: LocalDate,
    val weekNumber: Int,
    val ageTenths: Int,
    val moment: LifeWeekMoment?,
    val time: WeekTime,
    val commitments: List<Commitment>,
    val otherThingsDone: List<OtherThingDone>,
    val otherThingsDoneEnabled: Boolean,
    val priorityLimit: Int,
    val note: String?,
    val closed: Boolean,
    val beforeStart: Boolean,
    val currentWeekStart: LocalDate,
    val someday: List<SomedayItem>,
    /** The chapters this week belongs to. */
    val chapters: List<Chapter>,
) {
    val occupied: Int get() = commitments.count { it.status == CommitmentStatus.Open || it.status == CommitmentStatus.Done }
    val squaresLeft: Int get() = (priorityLimit - occupied).coerceAtLeast(0)
    val canAdd: Boolean get() = !beforeStart && squaresLeft > 0
    val canClose: Boolean get() = time != WeekTime.Future && (commitments.isNotEmpty() || closed)
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeekDetailViewModel(private val container: AppContainer, private val weekStart: LocalDate) : NoticeViewModel() {
    private val repository = container.repository

    val state: StateFlow<WeekDetailState?> = combine(
        combine(repository.profile().filterNotNull(), container.today.value, ::Pair),
        combine(repository.week(weekStart), repository.otherThingsDone(weekStart), ::Pair),
        repository.review(weekStart),
        repository.somedayWaiting(),
        repository.chapters(),
    ) { (profile, today), (commitments, otherDone), review, someday, chapters ->
        val calendar = profile.calendar()
        val currentWeek = calendar.weekStartOf(today)
        WeekDetailState(
            today = today,
            zone = container.clock.zone,
            weekStart = weekStart,
            weekNumber = calendar.indexOf(weekStart) + 1,
            ageTenths = calendar.yearsLivedTenths(if (weekStart == currentWeek) today else weekStart.plusDays(6)),
            moment = calendar.momentOf(weekStart),
            time = when {
                weekStart < currentWeek -> WeekTime.Past
                weekStart == currentWeek -> WeekTime.Current
                else -> WeekTime.Future
            },
            commitments = commitments,
            otherThingsDone = otherDone,
            otherThingsDoneEnabled = profile.otherThingsDoneEnabled,
            priorityLimit = profile.prioritiesPerWeek,
            note = review?.note?.takeIf { it.isNotBlank() },
            closed = review != null,
            beforeStart = weekStart < calendar.weekStartOf(profile.startedOn),
            currentWeekStart = currentWeek,
            someday = someday,
            chapters = chapters.filter { it.covers(weekStart, currentWeek) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(title: String) = launchWrite { report(repository.addCommitment(weekStart, title)) }

    fun addOtherThingDone(title: String) = launchWrite { report(repository.addOtherThingDone(weekStart, title)) }

    fun renameOtherThingDone(item: OtherThingDone, title: String) = launchWrite {
        report(repository.renameOtherThingDone(item.id, title))
    }

    fun removeOtherThingDone(item: OtherThingDone) = launchWrite {
        val removed = repository.removeOtherThingDone(item.id) ?: return@launchWrite
        notify(Notice(R.string.notice_removed, listOf(removed.title)) {
            launchWrite { repository.restoreOtherThingDone(removed) }
        })
    }

    fun addFromSomeday(item: SomedayItem) = launchWrite { report(repository.schedule(item.id, weekStart)) }

    fun setDone(commitment: Commitment, done: Boolean) = launchWrite { repository.setDone(commitment.id, done) }

    fun rename(commitment: Commitment, title: String) = launchWrite { report(repository.rename(commitment.id, title)) }

    fun moveToThisWeek(commitment: Commitment) = launchWrite {
        val target = state.value?.currentWeekStart ?: return@launchWrite
        val result = repository.carry(commitment.id, target)
        if (result == PlanResult.Ok) notify(Notice(R.string.notice_moved_this_week)) else report(result)
    }

    fun returnToSomeday(commitment: Commitment) = launchWrite {
        val result = repository.returnToSomeday(commitment.id)
        if (result == PlanResult.Ok) notify(Notice(R.string.notice_returned_to_someday)) else report(result)
    }

    fun letGo(commitment: Commitment) = launchWrite { report(repository.letGo(commitment.id)) }

    fun remove(commitment: Commitment) = launchWrite {
        val removed = repository.remove(commitment.id) ?: return@launchWrite
        notify(Notice(R.string.notice_removed, listOf(removed.title)) { launchWrite { report(repository.restore(removed)) } })
    }
}
