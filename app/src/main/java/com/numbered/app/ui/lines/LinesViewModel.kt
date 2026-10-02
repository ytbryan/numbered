package com.numbered.app.ui.lines

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.WeekReview
import com.numbered.app.data.calendar
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.domain.WeekSummary
import com.numbered.app.ui.NoticeViewModel
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** One week's line. */
data class Line(val weekStart: LocalDate, val weekNumber: Int, val note: String)

/** A year of lines, with what that year held. */
data class LinesYear(val year: Int, val weeksClosed: Int, val thingsDone: Int, val lines: List<Line>)

data class LinesState(val today: LocalDate, val years: List<LinesYear>)

/** A week belongs to the year that holds most of its days, so New Year's week lands where it mostly was. */
fun yearOf(weekStart: LocalDate): Int = weekStart.plusDays(3).year

/** Every closed week's line, newest first and grouped by year, with each year's summary. */
fun linesByYear(
    calendar: LifeCalendar,
    reviews: Collection<WeekReview>,
    summaries: Map<LocalDate, WeekSummary>,
): List<LinesYear> {
    val doneByYear = summaries.entries.groupBy({ yearOf(it.key) }) { it.value.done }.mapValues { it.value.sum() }
    return reviews.groupBy { yearOf(it.weekStart) }
        .map { (year, closed) ->
            LinesYear(
                year = year,
                weeksClosed = closed.size,
                thingsDone = doneByYear[year] ?: 0,
                lines = closed.filter { it.note.isNotBlank() }
                    .sortedByDescending { it.weekStart }
                    .map { Line(it.weekStart, calendar.indexOf(it.weekStart) + 1, it.note) },
            )
        }
        .sortedByDescending { it.year }
}

/** Lines mentioning [query], ignoring case. Years without a match drop out. */
fun List<LinesYear>.matching(query: String): List<LinesYear> {
    val words = query.trim()
    if (words.isEmpty()) return this
    return mapNotNull { year ->
        year.copy(lines = year.lines.filter { it.note.contains(words, ignoreCase = true) }).takeIf { it.lines.isNotEmpty() }
    }
}

class LinesViewModel(container: AppContainer) : NoticeViewModel() {
    private val repository = container.repository

    val state: StateFlow<LinesState?> = combine(
        repository.profile().filterNotNull(),
        container.today.value,
        repository.reviews(),
        repository.summaries(),
    ) { profile, today, reviews, summaries ->
        LinesState(today, linesByYear(profile.calendar(), reviews.values, summaries))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
