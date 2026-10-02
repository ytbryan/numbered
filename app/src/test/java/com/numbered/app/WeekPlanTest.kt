package com.numbered.app

import com.numbered.app.domain.CommitmentStatus.Carried
import com.numbered.app.domain.CommitmentStatus.Done
import com.numbered.app.domain.CommitmentStatus.LetGo
import com.numbered.app.domain.CommitmentStatus.Open
import com.numbered.app.domain.CommitmentStatus.ReturnedToSomeday
import com.numbered.app.domain.SOMEDAY_REVIEW_AFTER
import com.numbered.app.domain.WeekSummary
import com.numbered.app.domain.WeekTone
import com.numbered.app.domain.isStale
import com.numbered.app.domain.weekTone
import com.numbered.app.ui.week.ThisWeekViewModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.numbered.app.ui.lines.matching
import org.junit.Test

class WeekPlanTest {
    @Test fun enlargedYearsCoverEveryVisibleWeekExactlyOnce() {
        java.time.DayOfWeek.entries.forEach { firstDay ->
            val calendar = com.numbered.app.domain.LifeCalendar(LocalDate.of(1989, 12, 31), 80, firstDay)
            listOf(60, 4175).forEach { visible ->
                val firstYear = calendar.weekStart(0).plusDays(3).year
                val lastYear = calendar.weekStart(visible - 1).plusDays(3).year
                val covered = (firstYear..lastYear).flatMap { year ->
                    com.numbered.app.ui.life.yearWindow(calendar, year, visible).also { window ->
                        window.forEach { assertEquals(year, calendar.weekStart(it).plusDays(3).year) }
                    }.toList()
                }
                assertEquals((0 until visible).toList(), covered)
            }
        }
    }
    @Test fun lettingGoAndReturningDoNotCountAgainstAWeek() {
        val summary = WeekSummary.of(listOf(Done, Done, LetGo, ReturnedToSomeday))
        assertEquals(2, summary.done)
        assertEquals(2, summary.counted)
        assertEquals(4, summary.planned)
        assertTrue(summary.allDone)
    }

    @Test fun carriedWorkStillCountsAsUnfinished() {
        val summary = WeekSummary.of(listOf(Done, Carried))
        assertEquals(2, summary.counted)
        assertFalse(summary.allDone)
        assertEquals(1, summary.occupied)
    }

    @Test fun aWeekWithNothingCountedIsNeverAllDone() {
        assertFalse(WeekSummary.of(listOf(LetGo)).allDone)
        assertFalse(WeekSummary.Empty.allDone)
    }

    @Test fun tonesFollowTimeThenCompletion() {
        val current = 100
        assertEquals(WeekTone.Current, weekTone(100, current, WeekSummary.of(listOf(Done))))
        assertEquals(WeekTone.Ahead, weekTone(101, current, null))
        assertEquals(WeekTone.Ahead, weekTone(101, current, WeekSummary.of(listOf(ReturnedToSomeday))))
        assertEquals(WeekTone.Pinned, weekTone(140, current, WeekSummary.of(listOf(Open))))
        assertEquals(WeekTone.Lived, weekTone(10, current, null))
        assertEquals(WeekTone.Lived, weekTone(99, current, WeekSummary.of(listOf(Open, Carried))))
        assertEquals(WeekTone.SomeDone, weekTone(99, current, WeekSummary.of(listOf(Done, Carried))))
        assertEquals(WeekTone.AllDone, weekTone(99, current, WeekSummary.of(listOf(Done, LetGo))))
    }

    @Test fun somedayItemsGoStaleAfterTwelveWeeksUnlessKept() {
        val limit = SOMEDAY_REVIEW_AFTER.toMillis()
        assertFalse(isStale(createdAtMillis = 0, reviewedAtMillis = null, nowMillis = limit - 1))
        assertTrue(isStale(createdAtMillis = 0, reviewedAtMillis = null, nowMillis = limit))
        assertFalse(isStale(createdAtMillis = 0, reviewedAtMillis = limit, nowMillis = limit + 1))
    }

    @Test fun everyUnclosedWeekWithUnfinishedWorkIsOfferedOldestFirst() {
        val current = LocalDate.of(2026, 9, 28)
        val summaries = mapOf(
            current.minusWeeks(5) to WeekSummary.of(listOf(Open)),
            current.minusWeeks(3) to WeekSummary.of(listOf(Open, Done)),
            current.minusWeeks(1) to WeekSummary.of(listOf(Done)),
            current to WeekSummary.of(listOf(Open)),
        )
        assertEquals(
            listOf(current.minusWeeks(5), current.minusWeeks(3), current.minusWeeks(1)),
            ThisWeekViewModel.unclosedWeeks(summaries, emptySet(), current),
        )
        assertEquals(listOf(current.minusWeeks(3), current.minusWeeks(1)), ThisWeekViewModel.unclosedWeeks(summaries, setOf(current.minusWeeks(5)), current))
    }

    @Test fun lastWeekIsOfferedForItsNoteWhenEverythingIsSettled() {
        val current = LocalDate.of(2026, 9, 28)
        val summaries = mapOf(current.minusWeeks(1) to WeekSummary.of(listOf(Done, LetGo)))
        assertEquals(listOf(current.minusWeeks(1)), ThisWeekViewModel.unclosedWeeks(summaries, emptySet(), current))
        assertTrue(ThisWeekViewModel.unclosedWeeks(summaries, setOf(current.minusWeeks(1)), current).isEmpty())
    }

    @Test fun olderSettledWeeksAreLeftAlone() {
        val current = LocalDate.of(2026, 9, 28)
        val summaries = mapOf(current.minusWeeks(4) to WeekSummary.of(listOf(Done)))
        assertTrue(ThisWeekViewModel.unclosedWeeks(summaries, emptySet(), current).isEmpty())
    }

    @Test fun linesGroupByTheYearHoldingMostOfTheWeek() {
        val calendar = com.numbered.app.domain.LifeCalendar(LocalDate.of(1989, 12, 2), 80, java.time.DayOfWeek.MONDAY)
        // Monday 29 December 2025 to Sunday 4 January 2026 is mostly in 2026.
        val newYear = LocalDate.of(2025, 12, 29)
        val december = LocalDate.of(2025, 12, 22)
        val reviews = listOf(
            com.numbered.app.data.WeekReview(newYear, "Fireworks from the roof.", 1),
            com.numbered.app.data.WeekReview(december, "  ", 1),
        )
        val summaries = mapOf(newYear to WeekSummary.of(listOf(Done, Done)), december to WeekSummary.of(listOf(Done, Open)))
        val years = com.numbered.app.ui.lines.linesByYear(calendar, reviews, summaries)
        assertEquals(listOf(2026, 2025), years.map { it.year })
        assertEquals(listOf("Fireworks from the roof."), years[0].lines.map { it.note })
        assertEquals(2, years[0].thingsDone)
        // A closed week with a blank line still counts as closed, but has no line to show.
        assertEquals(1, years[1].weeksClosed)
        assertTrue(years[1].lines.isEmpty())
        // Search ignores case, and years without a match drop out.
        assertEquals(listOf(2026), years.matching("  FIREWORKS ").map { it.year })
        assertTrue(years.matching("osaka").isEmpty())
        assertEquals(years, years.matching(" "))
    }
}
