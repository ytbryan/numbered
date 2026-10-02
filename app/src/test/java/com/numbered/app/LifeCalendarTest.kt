package com.numbered.app

import com.numbered.app.domain.LifeCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LifeCalendarTest {
    private val birth = LocalDate.of(1989, 12, 2) // A Saturday.
    private val monday = LifeCalendar(birth, 80, DayOfWeek.MONDAY)

    @Test fun birthWeekIsWeekZeroAndStartsOnTheConfiguredDay() {
        assertEquals(LocalDate.of(1989, 11, 27), monday.firstWeekStart)
        assertEquals(0, monday.indexOf(birth))
        assertEquals(LocalDate.of(1989, 11, 26), LifeCalendar(birth, 80, DayOfWeek.SUNDAY).firstWeekStart)
    }

    @Test fun indexCountsWholeCalendarWeeks() {
        assertEquals(1922, monday.indexOf(LocalDate.of(2026, 10, 1)))
        assertEquals(1922, monday.indexOf(LocalDate.of(2026, 9, 28)))
        assertEquals(1922, monday.indexOf(LocalDate.of(2026, 10, 4)))
        assertEquals(1923, monday.indexOf(LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 9, 28), monday.weekStart(1922))
        assertEquals(LocalDate.of(2026, 10, 4), monday.weekEnd(1922))
    }

    @Test fun horizonEndsAtTheWeekOfTheHorizonBirthday() {
        assertEquals(4175, monday.horizonWeeks)
        assertEquals(LocalDate.of(2069, 11, 25), monday.weekStart(4174))
    }

    @Test fun yearsLivedIsTruncatedNotRounded() {
        assertEquals(368, monday.yearsLivedTenths(LocalDate.of(2026, 10, 1)))
        assertEquals(369, monday.yearsLivedTenths(LocalDate.of(2026, 12, 1)))
        assertEquals(370, monday.yearsLivedTenths(LocalDate.of(2026, 12, 2)))
    }

    @Test fun leapDayBirthdaysAgeOnMarchFirstInCommonYears() {
        val leap = LifeCalendar(LocalDate.of(2000, 2, 29), 80, DayOfWeek.MONDAY)
        assertEquals(20, leap.ageOn(LocalDate.of(2021, 2, 28)))
        assertEquals(21, leap.ageOn(LocalDate.of(2021, 3, 1)))
        assertEquals(209, leap.yearsLivedTenths(LocalDate.of(2021, 2, 28)))
        assertEquals(210, leap.yearsLivedTenths(LocalDate.of(2021, 3, 1)))
    }

    @Test fun decadeLabelsLandOnEveryTenthRowBecauseEachRowHoldsItsBirthday() {
        listOf(
            LocalDate.of(1989, 12, 2),
            LocalDate.of(2000, 2, 29),
            LocalDate.of(1950, 1, 1),
            LocalDate.of(1975, 12, 31),
        ).forEach { date ->
            DayOfWeek.entries.forEach { firstDay ->
                val calendar = LifeCalendar(date, 100, firstDay)
                val rows = calendar.visibleWeeks(0, gentle = false) / LifeCalendar.WEEKS_PER_ROW
                val expected = (0 until rows step 10).map { it to it }
                assertEquals("$date starting $firstDay", expected, calendar.decadeRows(rows))
            }
        }
    }

    @Test fun gentleModeStopsAtTheEndOfTheCurrentRow() {
        assertEquals(37 * 52, monday.visibleWeeks(1922, gentle = true))
        assertEquals(4175, monday.visibleWeeks(1922, gentle = false))
    }

    @Test fun theGridKeepsGrowingForSomeoneWhoOutlivesTheirHorizon() {
        val index = monday.horizonWeeks + 30
        assertEquals((index / 52 + 1) * 52, monday.visibleWeeks(index, gentle = false))
    }

    @Test fun defaultHorizonIsTheFirstOneStillAhead() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals(80, LifeCalendar.defaultHorizon(birth, today))
        assertEquals(90, LifeCalendar.defaultHorizon(LocalDate.of(1940, 1, 1), today))
        assertEquals(100, LifeCalendar.defaultHorizon(LocalDate.of(1930, 1, 1), today))
    }
}
