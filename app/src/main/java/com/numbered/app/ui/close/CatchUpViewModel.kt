package com.numbered.app.ui.close

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.Commitment
import com.numbered.app.data.PlanResult
import com.numbered.app.data.WeekClosing
import com.numbered.app.data.calendar
import com.numbered.app.domain.CloseChoice
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.MAX_COMMITMENTS_PER_WEEK
import com.numbered.app.ui.NoticeViewModel
import com.numbered.app.ui.week.ThisWeekViewModel
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

data class CatchUpWeek(val weekStart: LocalDate, val weekNumber: Int, val open: List<Commitment>)

data class CatchUpState(
    val today: LocalDate,
    val currentWeek: LocalDate,
    val weeks: List<CatchUpWeek>,
    val choices: Map<Long, CloseChoice>,
    /** Squares left this week, where carried commitments go. */
    val carryRoom: Int,
) {
    val open: List<Commitment> get() = weeks.flatMap { it.open }
    val carrying: Int get() = open.count { choices[it.id] == CloseChoice.Carry }
    val undecided: Int get() = open.count { it.id !in choices }

    fun canCarry(commitment: Commitment): Boolean = choices[commitment.id] == CloseChoice.Carry || carrying < carryRoom
}

/** Closes every past week left open in one go, for someone coming back after time away. */
@OptIn(ExperimentalCoroutinesApi::class)
class CatchUpViewModel(private val container: AppContainer) : NoticeViewModel() {
    private val repository = container.repository
    private val choices = MutableStateFlow<Map<Long, CloseChoice>>(emptyMap())
    private val closedChannel = Channel<Int>(Channel.CONFLATED)

    /** Emits how many weeks were closed, once the catch-up has been saved. */
    val closed: Flow<Int> = closedChannel.receiveAsFlow()

    private val weeks: Flow<Pair<LocalDate, List<CatchUpWeek>>> = combine(
        repository.profile().filterNotNull(),
        container.today.value,
        repository.summaries(),
        repository.reviews(),
    ) { profile, today, summaries, reviews ->
        val calendar = profile.calendar()
        val currentWeek = calendar.weekStartOf(today)
        currentWeek to ThisWeekViewModel.unclosedWeeks(summaries, reviews.keys, currentWeek).map { it to calendar.indexOf(it) + 1 }
    }.flatMapLatest { (currentWeek, unclosed) ->
        if (unclosed.isEmpty()) return@flatMapLatest flowOf(currentWeek to emptyList())
        combine(unclosed.map { (start, _) -> repository.week(start) }) { perWeek ->
            currentWeek to unclosed.mapIndexed { index, (start, number) ->
                CatchUpWeek(start, number, perWeek[index].filter { it.status == CommitmentStatus.Open })
            }
        }
    }

    val state: StateFlow<CatchUpState?> = combine(
        weeks,
        container.today.value,
        repository.summaries(),
        choices,
    ) { (currentWeek, weeks), today, summaries, chosen ->
        val openIds = weeks.flatMap { week -> week.open.map { it.id } }.toSet()
        CatchUpState(
            today = today,
            currentWeek = currentWeek,
            weeks = weeks,
            choices = chosen.filterKeys { it in openIds },
            carryRoom = MAX_COMMITMENTS_PER_WEEK - (summaries[currentWeek]?.occupied ?: 0),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun choose(commitment: Commitment, choice: CloseChoice) {
        choices.value = choices.value + (commitment.id to choice)
    }

    fun draft(week: LocalDate): String? = container.drafts.read(week)

    fun saveDraft(week: LocalDate, note: String) = container.drafts.save(week, note)

    /** Settles everything still undecided the same way. Carrying is chosen one by one, as room allows. */
    fun chooseRemaining(choice: CloseChoice) {
        require(choice != CloseChoice.Carry)
        val current = state.value ?: return
        choices.value = choices.value + current.open.filter { it.id !in current.choices }.associate { it.id to choice }
    }

    /** Closes every week, with the optional line written for each in [notes]. */
    fun close(notes: Map<LocalDate, String>) = launchWrite {
        val current = state.value ?: return@launchWrite
        if (current.undecided > 0 || current.weeks.isEmpty()) return@launchWrite
        val closings = current.weeks.map { week ->
            WeekClosing(
                weekStart = week.weekStart,
                note = notes[week.weekStart] ?: draft(week.weekStart).orEmpty(),
                choices = current.choices.filterKeys { id -> week.open.any { it.id == id } },
            )
        }
        when (val result = repository.closeWeeks(closings, current.currentWeek)) {
            PlanResult.Ok -> {
                closings.forEach { container.drafts.clear(it.weekStart) }
                closedChannel.trySend(closings.size)
            }
            else -> report(result)
        }
    }
}
