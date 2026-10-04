package com.numbered.app.domain

import java.time.Duration

/** Three is the starting point; a person can choose a different weekly limit in Settings. */
const val DEFAULT_PRIORITIES_PER_WEEK = 3
const val MAX_PRIORITIES_PER_WEEK = 7

/** Someday items untouched for this long ask whether they still deserve a square. */
val SOMEDAY_REVIEW_AFTER: Duration = Duration.ofDays(12 * 7)

enum class CommitmentStatus {
    Open,
    Done,

    /** Unfinished and moved to a later week, which holds its own copy. */
    Carried,

    /** Unfinished and returned to Someday. */
    ReturnedToSomeday,

    /** Deliberately dropped. A decision, not a failure. */
    LetGo,
}

/** How a week's commitments ended up, counting only what still asks to be done. */
data class WeekSummary(val done: Int, val open: Int, val counted: Int, val planned: Int) {
    val allDone: Boolean get() = counted > 0 && done >= counted

    /** Commitments holding one of the week's squares. */
    val occupied: Int get() = done + open

    companion object {
        val Empty = WeekSummary(done = 0, open = 0, counted = 0, planned = 0)

        fun of(statuses: List<CommitmentStatus>) = WeekSummary(
            done = statuses.count { it == CommitmentStatus.Done },
            open = statuses.count { it == CommitmentStatus.Open },
            counted = statuses.count { it.countsTowardCompletion },
            planned = statuses.size,
        )
    }
}

/** Letting go and returning to Someday are decisions, so they do not count against a week. */
val CommitmentStatus.countsTowardCompletion: Boolean
    get() = this == CommitmentStatus.Done || this == CommitmentStatus.Open || this == CommitmentStatus.Carried

enum class WeekTone { Lived, SomeDone, AllDone, Current, Pinned, Ahead }

fun weekTone(index: Int, currentIndex: Int, summary: WeekSummary?): WeekTone = when {
    index == currentIndex -> WeekTone.Current
    index > currentIndex -> if ((summary?.occupied ?: 0) > 0) WeekTone.Pinned else WeekTone.Ahead
    summary == null || summary.done == 0 -> WeekTone.Lived
    summary.allDone -> WeekTone.AllDone
    else -> WeekTone.SomeDone
}

/** What closing a week does with each unfinished commitment. */
enum class CloseChoice { Done, Carry, Someday, LetGo }

fun isStale(createdAtMillis: Long, reviewedAtMillis: Long?, nowMillis: Long): Boolean =
    nowMillis - (reviewedAtMillis ?: createdAtMillis) >= SOMEDAY_REVIEW_AFTER.toMillis()
