package com.numbered.app.ui.life

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.Chapter
import com.numbered.app.data.Commitment
import com.numbered.app.data.calendar
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.domain.LifeWeekMoment
import com.numbered.app.domain.WeekSummary
import com.numbered.app.domain.WeekTone
import com.numbered.app.domain.weekTone
import com.numbered.app.ui.NoticeViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class LifeGridState(
    val today: LocalDate,
    val tones: List<WeekTone>,
    val currentIndex: Int,
    /** Row index to the age labelled beside it. */
    val decadeRows: List<Pair<Int, Int>>,
    val yearsLivedTenths: Int,
    val horizonYears: Int,
    val horizonWeeks: Int,
    val gentle: Boolean,
    val calendar: LifeCalendar,
    val startedWeek: LocalDate,
    /** Oldest first. */
    val chapters: List<Chapter>,
    /** Squares where a chapter begins. */
    val chapterStarts: Set<Int>,
)

data class SelectedWeek(
    val index: Int,
    val start: LocalDate,
    val age: Int,
    val moment: LifeWeekMoment?,
    val tone: WeekTone,
    val summary: WeekSummary,
    val commitments: List<Commitment>,
    val note: String?,
    val beforeStart: Boolean,
    /** Titles of the chapters this week belongs to. */
    val chapters: List<String>,
)

/** Weeks belong to the calendar year containing their midpoint, including New Year boundaries. */
internal fun yearWindow(calendar: LifeCalendar, year: Int, visibleWeeks: Int): IntRange {
    fun firstInYear(value: Int): Int {
        val january = LocalDate.of(value, 1, 1)
        val index = calendar.indexOf(january)
        return index + if (calendar.weekStart(index).plusDays(3) < january) 1 else 0
    }
    return firstInYear(year).coerceAtLeast(0) until firstInYear(year + 1).coerceAtMost(visibleWeeks)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LifeViewModel(container: AppContainer) : NoticeViewModel() {
    private val repository = container.repository
    private val selectedIndex = MutableStateFlow<Int?>(null)

    val grid: StateFlow<LifeGridState?> = combine(
        repository.profile().filterNotNull(),
        container.today.value,
        repository.summaries(),
        repository.chapters(),
    ) { profile, today, summaries, chapters ->
        val calendar = profile.calendar()
        val currentIndex = calendar.indexOf(today)
        val visible = calendar.visibleWeeks(currentIndex, profile.gentle)
        LifeGridState(
            today = today,
            tones = List(visible) { index -> weekTone(index, currentIndex, summaries[calendar.weekStart(index)]) },
            currentIndex = currentIndex,
            decadeRows = calendar.decadeRows((visible + LifeCalendar.WEEKS_PER_ROW - 1) / LifeCalendar.WEEKS_PER_ROW),
            yearsLivedTenths = calendar.yearsLivedTenths(today),
            horizonYears = profile.horizonYears,
            horizonWeeks = calendar.horizonWeeks,
            gentle = profile.gentle,
            calendar = calendar,
            startedWeek = calendar.weekStartOf(profile.startedOn),
            chapters = chapters,
            chapterStarts = chapters.map { calendar.indexOf(it.startWeek) }.filter { it in 0 until visible }.toSet(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selected: StateFlow<SelectedWeek?> = combine(grid.filterNotNull(), selectedIndex) { grid, index ->
        grid to (index ?: grid.currentIndex).coerceIn(0, grid.tones.lastIndex)
    }.flatMapLatest { (grid, index) ->
        val start = grid.calendar.weekStart(index)
        combine(repository.week(start), repository.review(start)) { commitments, review ->
            SelectedWeek(
                index = index,
                start = start,
                age = grid.calendar.ageOn(start.plusDays(6)),
                moment = grid.calendar.momentOf(start),
                tone = grid.tones[index],
                summary = WeekSummary.of(commitments.map(Commitment::status)),
                commitments = commitments,
                note = review?.note?.takeIf { it.isNotBlank() },
                beforeStart = start < grid.startedWeek,
                chapters = grid.chapters.filter { it.covers(start, grid.calendar.weekStart(grid.currentIndex)) }.map { it.title },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(index: Int) {
        val last = grid.value?.tones?.lastIndex ?: return
        selectedIndex.value = index.coerceIn(0, last)
    }

    /** Steps from the requested selection, not the loaded one, so quick repeated taps all count. */
    fun step(by: Int) {
        val current = selectedIndex.value ?: grid.value?.currentIndex ?: return
        select(current + by)
    }
}
