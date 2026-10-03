package com.numbered.app.ui.close

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.Commitment
import com.numbered.app.data.PlanResult
import com.numbered.app.data.calendar
import com.numbered.app.domain.CloseChoice
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.NoticeViewModel
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class CloseWeekState(
    val today: LocalDate,
    val weekStart: LocalDate,
    val weekNumber: Int,
    /** Closing the current week carries into next week; closing a past week carries into this one. */
    val closingCurrent: Boolean,
    val carryTo: LocalDate,
    val done: List<Commitment>,
    val open: List<Commitment>,
    val choices: Map<Long, CloseChoice>,
    val carryRoom: Int,
    val existingNote: String?,
    val alreadyClosed: Boolean,
) {
    val carrying: Int get() = open.count { choices[it.id] == CloseChoice.Carry }
    val undecided: Int get() = open.count { it.id !in choices }

    /** Carry stays available for an item already set to carry, and for others while room remains. */
    fun canCarry(commitment: Commitment): Boolean = choices[commitment.id] == CloseChoice.Carry || carrying < carryRoom
}

class CloseWeekViewModel(container: AppContainer, private val weekStart: LocalDate) : NoticeViewModel() {
    private val repository = container.repository
    private val drafts = container.drafts
    private val choices = MutableStateFlow<Map<Long, CloseChoice>>(emptyMap())
    private val closedChannel = Channel<Int>(Channel.CONFLATED)

    /** Emits the closed week's number once the close has been saved. */
    val closed: Flow<Int> = closedChannel.receiveAsFlow()

    val state: StateFlow<CloseWeekState?> = combine(
        combine(repository.profile().filterNotNull(), container.today.value, ::Pair),
        repository.week(weekStart),
        repository.review(weekStart),
        repository.summaries(),
        choices,
    ) { (profile, today), commitments, review, summaries, chosen ->
        val calendar = profile.calendar()
        val currentWeek = calendar.weekStartOf(today)
        val closingCurrent = weekStart >= currentWeek
        val carryTo = if (closingCurrent) weekStart.plusWeeks(1) else currentWeek
        val open = commitments.filter { it.status == CommitmentStatus.Open }
        CloseWeekState(
            today = today,
            weekStart = weekStart,
            weekNumber = calendar.indexOf(weekStart) + 1,
            closingCurrent = closingCurrent,
            carryTo = carryTo,
            done = commitments.filter { it.status == CommitmentStatus.Done },
            open = open,
            choices = chosen.filterKeys { id -> open.any { it.id == id } },
            carryRoom = (profile.prioritiesPerWeek - (summaries[carryTo]?.occupied ?: 0)).coerceAtLeast(0),
            existingNote = review?.note,
            alreadyClosed = review != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(commitment: Commitment, choice: CloseChoice) {
        choices.value = choices.value + (commitment.id to choice)
    }

    fun draft(): String? = drafts.read(weekStart)

    fun saveDraft(note: String) = drafts.save(weekStart, note)

    fun close(note: String) = launchWrite {
        val current = state.value ?: return@launchWrite
        if (current.undecided > 0) return@launchWrite
        when (val result = repository.closeWeek(weekStart, note, current.choices, current.carryTo)) {
            PlanResult.Ok -> {
                drafts.clear(weekStart)
                closedChannel.trySend(current.weekNumber)
            }
            else -> report(result)
        }
    }
}
