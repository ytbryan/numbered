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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPlanTest {
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

    @Test fun theOldestWeekWithUnfinishedWorkIsOfferedFirst() {
        val current = LocalDate.of(2026, 9, 28)
        val summaries = mapOf(
            current.minusWeeks(5) to WeekSummary.of(listOf(Open)),
            current.minusWeeks(3) to WeekSummary.of(listOf(Open, Done)),
            current.minusWeeks(1) to WeekSummary.of(listOf(Done)),
            current to WeekSummary.of(listOf(Open)),
        )
        assertEquals(current.minusWeeks(3), ThisWeekViewModel.findUnclosed(summaries, setOf(current.minusWeeks(5)), current))
    }

    @Test fun lastWeekIsOfferedForItsNoteWhenEverythingIsSettled() {
        val current = LocalDate.of(2026, 9, 28)
        val summaries = mapOf(current.minusWeeks(1) to WeekSummary.of(listOf(Done, LetGo)))
        assertEquals(current.minusWeeks(1), ThisWeekViewModel.findUnclosed(summaries, emptySet(), current))
        assertNull(ThisWeekViewModel.findUnclosed(summaries, setOf(current.minusWeeks(1)), current))
    }

    @Test fun olderSettledWeeksAreLeftAlone() {
        val current = LocalDate.of(2026, 9, 28)
        val summaries = mapOf(current.minusWeeks(4) to WeekSummary.of(listOf(Done)))
        assertNull(ThisWeekViewModel.findUnclosed(summaries, emptySet(), current))
    }
}
