package com.numbered.app

import com.numbered.app.data.SomedayItem
import com.numbered.app.ui.someday.SomedayRow
import com.numbered.app.ui.someday.SomedaySort
import com.numbered.app.ui.someday.SomedayState
import com.numbered.app.ui.someday.matching
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SomedaySearchTest {
    private val monday = LocalDate.of(2026, 9, 28)
    private val ideas = listOf(
        SomedayItem(id = 1, title = "Learn to sail", createdAt = 10),
        SomedayItem(id = 2, title = "Learn the violin", createdAt = 30, keptAt = 100),
        SomedayItem(id = 3, title = "Build a bookshelf", createdAt = 20, keptAt = 80),
        SomedayItem(id = 4, title = "Read Middlemarch", createdAt = 20),
    )
    private val state = SomedayState(
        stale = listOf(SomedayRow(SomedayItem(id = 5, title = "Learn to code", createdAt = 1), 20, true)),
        waiting = ideas.map { SomedayRow(it, 2, false) },
        letGo = listOf(SomedayItem(id = 6, title = "Learn French", createdAt = 5, letGoAt = 40)),
        thisWeekStart = monday,
        nextWeekStart = monday.plusWeeks(1),
    )

    @Test fun searchIgnoresCaseAndOuterWhitespaceAcrossEveryGroup() {
        val found = state.matching("  LEARN  ", SomedaySort.Oldest)
        assertEquals(listOf(5L), found.stale.map { it.item.id })
        assertEquals(listOf(1L, 2L), found.waiting.map { it.item.id })
        assertEquals(listOf(6L), found.letGo.map { it.id })
        assertEquals(monday, found.thisWeekStart)
        assertTrue(state.matching("missing", SomedaySort.Oldest).letGo.isEmpty())
    }

    @Test fun sortingHasStableTiesAndPutsActuallyKeptIdeasFirst() {
        fun ids(sort: SomedaySort) = state.matching("", sort).waiting.map { it.item.id }
        assertEquals(listOf(1L, 3L, 4L, 2L), ids(SomedaySort.Oldest))
        assertEquals(listOf(2L, 3L, 4L, 1L), ids(SomedaySort.Newest))
        assertEquals(listOf(2L, 3L, 4L, 1L), ids(SomedaySort.RecentlyKept))
        assertEquals(4, state.matching(" \t ", SomedaySort.Newest).waiting.size)
        assertEquals(ideas, state.waiting.map { it.item })
    }
}
