package com.numbered.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * Maps calendar dates onto the weeks of one life.
 *
 * Week 0 is the calendar week that contains the birth date, and every later week starts on
 * [firstDayOfWeek]. Planning data is keyed by each week's start date rather than its index,
 * so changing the birth date or horizon never moves a commitment to a different calendar week.
 */
class LifeCalendar(
    val birthDate: LocalDate,
    val horizonYears: Int,
    val firstDayOfWeek: DayOfWeek,
) {
    val firstWeekStart: LocalDate = weekStartOf(birthDate)

    /** Weeks from the birth week up to, but not including, the week of the horizon birthday. */
    val horizonWeeks: Int = indexOf(birthDate.plusYears(horizonYears.toLong()))

    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    fun indexOf(date: LocalDate): Int = ChronoUnit.WEEKS.between(firstWeekStart, weekStartOf(date)).toInt()

    fun weekStart(index: Int): LocalDate = firstWeekStart.plusWeeks(index.toLong())

    fun weekEnd(index: Int): LocalDate = weekStart(index).plusDays(6)

    /** Uses the configured week start; a week belongs to the year containing at least four days. */
    fun calendarWeek(date: LocalDate): CalendarWeek {
        val fields = WeekFields.of(firstDayOfWeek, 4)
        val year = date.get(fields.weekBasedYear())
        return CalendarWeek(
            year = year,
            number = date.get(fields.weekOfWeekBasedYear()),
            total = LocalDate.of(year, 12, 28).get(fields.weekOfWeekBasedYear()),
        )
    }

    /** Whole years of age on [date]. */
    fun ageOn(date: LocalDate): Int = ChronoUnit.YEARS.between(birthDate, date).toInt().coerceAtLeast(0)

    /** Years lived on [date], truncated (never rounded up) to one decimal place. */
    fun yearsLivedTenths(date: LocalDate): Int {
        val age = ageOn(date)
        val lastBirthday = birthDate.plusYears(age.toLong())
        val nextBirthday = birthDate.plusYears(age + 1L)
        val tenths = ChronoUnit.DAYS.between(lastBirthday, date) * 10 / ChronoUnit.DAYS.between(lastBirthday, nextBirthday)
        // A 29 February birthday falls on 28 February in common years, a day before the age turns over.
        return age * 10 + tenths.toInt().coerceAtMost(9)
    }

    /**
     * Number of squares to draw. Someone who outlives their horizon keeps getting new weeks,
     * and gentle mode stops at the end of the current row so the weeks ahead stay out of view.
     */
    fun visibleWeeks(currentIndex: Int, gentle: Boolean): Int {
        val throughCurrentRow = (currentIndex / WEEKS_PER_ROW + 1) * WEEKS_PER_ROW
        return if (gentle) throughCurrentRow else maxOf(horizonWeeks, throughCurrentRow)
    }

    /** Rows that contain a birthday starting a new decade, labelled with that age. */
    fun decadeRows(rows: Int): List<Pair<Int, Int>> {
        var previousDecade = -1
        return buildList {
            for (row in 0 until rows) {
                val decade = ageOn(weekEnd(row * WEEKS_PER_ROW + WEEKS_PER_ROW - 1)) / 10
                if (decade != previousDecade) {
                    add(row to decade * 10)
                    previousDecade = decade
                }
            }
        }
    }

    companion object {
        const val WEEKS_PER_ROW = 52
        val HORIZON_CHOICES = listOf(80, 90, 100)

        /** The smallest offered horizon that is still ahead of someone born on [birthDate]. */
        fun defaultHorizon(birthDate: LocalDate, today: LocalDate): Int {
            val age = ChronoUnit.YEARS.between(birthDate, today).toInt()
            return HORIZON_CHOICES.firstOrNull { it > age } ?: HORIZON_CHOICES.last()
        }
    }
}

data class CalendarWeek(val year: Int, val number: Int, val total: Int)
