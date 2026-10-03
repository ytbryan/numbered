package com.numbered.app.ui.yearreview

import android.content.res.Resources
import com.numbered.app.R
import com.numbered.app.data.Chapter
import com.numbered.app.data.Commitment
import com.numbered.app.data.Snapshot
import com.numbered.app.data.WeekReview
import com.numbered.app.data.calendar
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.lines.yearOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal data class ReviewChapter(val chapter: Chapter, val start: LocalDate, val end: LocalDate)

internal data class YearReview(
    val year: Int,
    val today: LocalDate,
    val inProgress: Boolean,
    val weeksClosed: Int,
    val completed: List<Commitment>,
    val notes: List<WeekReview>,
    val chapters: List<ReviewChapter>,
) {
    val hasContent: Boolean get() = weeksClosed > 0 || completed.isNotEmpty() || chapters.isNotEmpty()
    val fileName: String get() = "numbered-$year-review.txt"
}

/** Week years match Your lines, including New Year's weeks and the chosen week start. */
internal fun yearReview(snapshot: Snapshot, year: Int, today: LocalDate): YearReview {
    val calendar = snapshot.profile.calendar()
    val currentWeek = calendar.weekStartOf(today)
    fun belongs(week: LocalDate) = week <= currentWeek && yearOf(week) == year
    val closed = snapshot.reviews.filter { belongs(it.weekStart) }
    fun firstWeek(value: Int): LocalDate {
        val january = LocalDate.of(value, 1, 1)
        val containing = calendar.weekStartOf(january)
        return if (yearOf(containing) < value) containing.plusWeeks(1) else containing
    }
    val first = firstWeek(year)
    val last = minOf(firstWeek(year + 1).minusWeeks(1), currentWeek)
    return YearReview(
        year = year,
        today = today,
        inProgress = year == yearOf(currentWeek),
        weeksClosed = closed.size,
        completed = snapshot.commitments.filter { belongs(it.weekStart) && it.status == CommitmentStatus.Done }
            .sortedWith(compareBy<Commitment> { it.weekStart }.thenBy { it.id }),
        notes = closed.filter { it.note.isNotBlank() }.sortedBy { it.weekStart },
        chapters = snapshot.chapters.mapNotNull { chapter ->
            val start = maxOf(chapter.startWeek, first)
            val end = minOf(chapter.endWeek ?: currentWeek, last)
            if (start <= end) ReviewChapter(chapter, start, end) else null
        }.sortedWith(compareBy<ReviewChapter> { it.start }.thenBy { it.chapter.id }),
    )
}

/** Years with recorded events, plus intermediate chapter years and the current week year. */
internal fun reviewYears(snapshot: Snapshot, today: LocalDate): List<Int> {
    val currentWeek = snapshot.profile.calendar().weekStartOf(today)
    return buildSet {
        add(yearOf(currentWeek))
        snapshot.commitments.filter { it.weekStart <= currentWeek }.forEach { add(yearOf(it.weekStart)) }
        snapshot.reviews.filter { it.weekStart <= currentWeek }.forEach { add(yearOf(it.weekStart)) }
        snapshot.chapters.filter { it.startWeek <= currentWeek }.forEach { chapter ->
            val first = yearOf(chapter.startWeek)
            val last = yearOf(minOf(chapter.endWeek ?: currentWeek, currentWeek))
            // Each end remains selectable, while imported ancient spans cannot allocate billions of years.
            add(first)
            add(last)
            for (year in maxOf(first, last - 120)..last) add(year)
        }
    }.sortedDescending()
}

internal fun YearReview.summary(resources: Resources): String = resources.getString(
    R.string.lines_year_summary,
    resources.getQuantityString(R.plurals.lines_weeks_closed, weeksClosed, weeksClosed),
    resources.getQuantityString(R.plurals.lines_things_done, completed.size, completed.size),
    resources.getQuantityString(R.plurals.lines_count, notes.size, notes.size),
)

/** Only the selected year and the three previewed sections go into this readable UTF-8 document. */
internal fun YearReview.document(resources: Resources, locale: Locale): String {
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    fun range(start: LocalDate, end: LocalDate) = if (start == end) start.format(date) else "${start.format(date)} - ${end.format(date)}"
    return buildString {
        appendLine(resources.getString(R.string.review_document_title, year))
        if (inProgress) appendLine(resources.getString(R.string.review_so_far, today.format(date)))
        appendLine(summary(resources))
        if (completed.isNotEmpty()) {
            appendLine()
            appendLine(resources.getString(R.string.review_completed))
            completed.groupBy { it.weekStart }.forEach { (week, entries) ->
                appendLine(range(week, week.plusDays(6)))
                entries.forEach { appendLine("- ${it.title}") }
                appendLine()
            }
        }
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine(resources.getString(R.string.review_notes))
            notes.forEach {
                appendLine(range(it.weekStart, it.weekStart.plusDays(6)))
                appendLine(it.note)
                appendLine()
            }
        }
        if (chapters.isNotEmpty()) {
            appendLine()
            appendLine(resources.getString(R.string.chapters_title))
            chapters.forEach {
                appendLine(it.chapter.title)
                appendLine(range(it.start, minOf(it.end.plusDays(6), today)))
                appendLine()
            }
        }
    }.trimEnd() + "\n"
}
