package com.numbered.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.domain.DEFAULT_PRIORITIES_PER_WEEK
import java.time.DayOfWeek
import java.time.LocalDate

/** The single row describing whose life this is. */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val birthDate: LocalDate,
    val horizonYears: Int,
    /** Fixed at setup, because every stored week is keyed by its start date. */
    val firstDayOfWeek: DayOfWeek,
    /** Hides the weeks ahead and the horizon count. */
    val gentle: Boolean,
    val startedOn: LocalDate,
    val prioritiesPerWeek: Int = DEFAULT_PRIORITIES_PER_WEEK,
    val otherThingsDoneEnabled: Boolean = false,
) {
    companion object {
        const val SINGLE_ROW = 1
    }
}

fun Profile.calendar() = LifeCalendar(birthDate, horizonYears, firstDayOfWeek)

@Entity(tableName = "commitments", indices = [Index("weekStart")])
data class Commitment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: LocalDate,
    val title: String,
    val status: CommitmentStatus = CommitmentStatus.Open,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    /** The week this was carried from, when it was not finished there. */
    val carriedFrom: LocalDate? = null,
    /** Stable provenance, including after either entry is renamed or removed. */
    val carriedFromId: Long? = null,
)

/** A completed thing recorded after the fact, separate from the week's chosen priorities. */
@Entity(tableName = "other_things_done", indices = [Index("weekStart")])
data class OtherThingDone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: LocalDate,
    val title: String,
    val createdAt: Long,
)

@Entity(tableName = "someday")
data class SomedayItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    /** Set when someone chooses to keep a stale item, which restarts its review clock. */
    val keptAt: Long? = null,
    val letGoAt: Long? = null,
)

@Entity(tableName = "week_reviews")
data class WeekReview(
    @PrimaryKey val weekStart: LocalDate,
    /** One line about what mattered. May be empty. */
    val note: String,
    val closedAt: Long,
)

/**
 * A labelled stretch of life, such as a move or a new job, drawn onto the grid.
 * Weeks are keyed by their start date, like everything else, so a chapter never shifts.
 */
@Entity(tableName = "chapters", indices = [Index("startWeek")])
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startWeek: LocalDate,
    /** The last week, inclusive. Null while the chapter is still going. */
    val endWeek: LocalDate?,
    val createdAt: Long,
) {
    /** Whether the chapter covers the week starting [weekStart], counting an open one up to [currentWeek]. */
    fun covers(weekStart: LocalDate, currentWeek: LocalDate): Boolean =
        weekStart >= startWeek && weekStart <= (endWeek ?: maxOf(currentWeek, startWeek))
}

/** A lightweight projection used to colour every square of the life grid. */
data class WeekStatusRow(val weekStart: LocalDate, val status: CommitmentStatus)
