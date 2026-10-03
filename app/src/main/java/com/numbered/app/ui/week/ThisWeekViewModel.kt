package com.numbered.app.ui.week

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.R
import com.numbered.app.data.Commitment
import com.numbered.app.data.PlanResult
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.calendar
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.LifeWeekMoment
import com.numbered.app.domain.MAX_COMMITMENTS_PER_WEEK
import com.numbered.app.domain.WeekSummary
import com.numbered.app.domain.isStale
import com.numbered.app.ui.Notice
import com.numbered.app.ui.NoticeViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** A past week that still needs closing, because it has unfinished commitments or no review. */
data class UnclosedWeek(val weekStart: LocalDate, val open: Int)

/** Several past weeks left open, offered as one catch-up instead of one week at a time. */
data class CatchUp(val weeks: Int, val open: Int)

data class ThisWeekState(
    val today: LocalDate,
    val zone: ZoneId,
    val weekStart: LocalDate,
    val moment: LifeWeekMoment?,
    val daysLeft: Int,
    val commitments: List<Commitment>,
    /** The one past week to close, when it is the only one. */
    val unclosed: UnclosedWeek?,
    val catchUp: CatchUp?,
    val closedNote: String?,
    val isClosed: Boolean,
    val nextWeekStart: LocalDate,
    val nextWeekPlanned: Int,
    val staleSomeday: Int,
    val someday: List<SomedayItem>,
) {
    val squaresLeft: Int get() = MAX_COMMITMENTS_PER_WEEK - commitments.size
    val doneCount: Int get() = commitments.count { it.status == CommitmentStatus.Done }

    /** The weekly close is offered in the last three days, and whenever it has already begun. */
    val offerClose: Boolean get() = !isClosed && daysLeft <= CLOSE_OFFER_DAYS

    companion object {
        const val CLOSE_OFFER_DAYS = 3
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ThisWeekViewModel(private val container: AppContainer) : NoticeViewModel() {
    private val repository = container.repository

    val state: StateFlow<ThisWeekState?> = combine(repository.profile().filterNotNull(), container.today.value) { profile, today ->
        profile to today
    }.flatMapLatest { (profile, today) ->
        val calendar = profile.calendar()
        val weekStart = calendar.weekStartOf(today)
        val nextWeekStart = weekStart.plusWeeks(1)
        combine(
            repository.week(weekStart),
            repository.summaries(),
            repository.reviews(),
            repository.somedayWaiting(),
        ) { week, summaries, reviews, someday ->
            val now = container.today.nowMillis()
            val unclosed = unclosedWeeks(summaries, reviews.keys, weekStart)
            ThisWeekState(
                today = today,
                zone = container.clock.zone,
                weekStart = weekStart,
                moment = calendar.momentOf(weekStart),
                daysLeft = ChronoUnit.DAYS.between(today, weekStart.plusDays(6)).toInt() + 1,
                commitments = week.filter { it.status == CommitmentStatus.Open || it.status == CommitmentStatus.Done },
                unclosed = unclosed.singleOrNull()?.let { start ->
                    UnclosedWeek(start, summaries[start]?.open ?: 0)
                },
                catchUp = unclosed.takeIf { it.size > 1 }?.let { weeks ->
                    CatchUp(weeks.size, weeks.sumOf { summaries[it]?.open ?: 0 })
                },
                closedNote = reviews[weekStart]?.note,
                isClosed = weekStart in reviews,
                nextWeekStart = nextWeekStart,
                nextWeekPlanned = summaries[nextWeekStart]?.occupied ?: 0,
                staleSomeday = someday.count { isStale(it.createdAt, it.keptAt, now) },
                someday = someday,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(title: String) = launchWrite {
        val weekStart = state.value?.weekStart ?: return@launchWrite
        report(repository.addCommitment(weekStart, title))
    }

    fun addFromSomeday(item: SomedayItem) = launchWrite {
        val weekStart = state.value?.weekStart ?: return@launchWrite
        val move = repository.scheduleWithUndo(item.id, weekStart)
        if (move.result == PlanResult.Ok) {
            notify(Notice(R.string.notice_added_this_week, listOf(item.title)) {
                launchWrite { report(repository.undoPlanningMove(requireNotNull(move.undo))) }
            })
        } else report(move.result)
    }

    fun setDone(commitment: Commitment, done: Boolean) = launchWrite {
        repository.setDone(commitment.id, done)
    }

    fun rename(commitment: Commitment, title: String) = launchWrite {
        report(repository.rename(commitment.id, title))
    }

    fun moveToNextWeek(commitment: Commitment) = launchWrite {
        val next = state.value?.nextWeekStart ?: return@launchWrite
        val move = repository.carryWithUndo(commitment.id, next)
        if (move.result == PlanResult.Ok) notify(Notice(R.string.notice_moved_next_week, undo = {
            launchWrite { report(repository.undoPlanningMove(requireNotNull(move.undo))) }
        })) else report(move.result)
    }

    fun returnToSomeday(commitment: Commitment) = launchWrite {
        val move = repository.returnToSomedayWithUndo(commitment.id)
        if (move.result == PlanResult.Ok) notify(Notice(R.string.notice_returned_to_someday, undo = {
            launchWrite { report(repository.undoPlanningMove(requireNotNull(move.undo))) }
        })) else report(move.result)
    }

    fun remove(commitment: Commitment) = launchWrite {
        val removed = repository.remove(commitment.id) ?: return@launchWrite
        notify(Notice(R.string.notice_removed, listOf(removed.title)) { launchWrite { report(repository.restore(removed)) } })
    }

    companion object {
        /**
         * Past weeks that still need closing, oldest first: every one with unfinished commitments
         * and no review, and last week if it had commitments and was never closed.
         */
        fun unclosedWeeks(
            summaries: Map<LocalDate, WeekSummary>,
            closed: Set<LocalDate>,
            currentWeekStart: LocalDate,
        ): List<LocalDate> {
            val unclosed = summaries.filterKeys { it < currentWeekStart && it !in closed }
            val lastWeek = currentWeekStart.minusWeeks(1).takeIf { (unclosed[it]?.planned ?: 0) > 0 }
            return (unclosed.filterValues { it.open > 0 }.keys + listOfNotNull(lastWeek)).sorted()
        }
    }
}
