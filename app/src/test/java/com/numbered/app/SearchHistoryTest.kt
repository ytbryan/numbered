package com.numbered.app

import androidx.test.core.app.ApplicationProvider
import com.numbered.app.data.Chapter
import com.numbered.app.data.Commitment
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.PlanResult
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.WeekReview
import com.numbered.app.domain.CloseChoice
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.history.carryHistory
import com.numbered.app.ui.search.searchEverything
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchHistoryTest {
    private val db = NumberedDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val repository = NumberedRepository(db, Clock.systemUTC())
    private val monday = LocalDate.of(2026, 9, 28)
    @After fun close() = db.close()

    @Test fun searchFindsEveryTypeIncludingResolvedAndLetGoAndTreatsPunctuationLiterally() {
        val commitments = listOf(
            Commitment(1, monday, "Café ideas", CommitmentStatus.Carried, 1),
            Commitment(2, monday.plusWeeks(1), "CAFÉ plans", CommitmentStatus.Done, 2),
        )
        val notes = listOf(WeekReview(monday, "A café was the highlight.", 3))
        val ideas = listOf(SomedayItem(1, "Open a café", 1, letGoAt = 2), SomedayItem(2, "100% [ready]", 2))
        val chapters = listOf(Chapter(1, "Café season", monday, null, 1))
        val results = searchEverything("  CAFÉ  ", commitments, notes, ideas, chapters)
        assertEquals(listOf(2L, 1L), results.commitments.map { it.id })
        assertEquals(notes, results.notes)
        assertEquals(listOf(1L), results.ideas.map { it.id })
        assertEquals(chapters, results.chapters)
        assertEquals(listOf(2L), searchEverything("% [", commitments, notes, ideas, chapters).ideas.map { it.id })
        assertTrue(searchEverything(" ", commitments, notes, ideas, chapters).empty)
        assertTrue(searchEverything("missing", commitments, notes, ideas, chapters).empty)
    }

    @Test fun carriesKeepTheirIdentityThroughRenamesDuplicateTitlesAndSkippedWeeks() = runBlocking {
        repository.addCommitment(monday, "Draft the talk")
        repository.addCommitment(monday, "Draft the talk")
        val original = repository.week(monday).first().first()
        repository.carry(original.id, monday.plusWeeks(1))
        val middle = repository.week(monday.plusWeeks(1)).first().single()
        repository.rename(middle.id, "Conference talk")
        repository.carry(middle.id, monday.plusWeeks(4))
        val latest = repository.week(monday.plusWeeks(4)).first().single()
        repository.setDone(latest.id, true)
        val all = repository.commitments().first()
        val expected = listOf(original.id, middle.id, latest.id)
        assertEquals(original.id, middle.carriedFromId)
        assertEquals(middle.id, latest.carriedFromId)
        for (id in expected) {
            val trail = carryHistory(id, all)!!
            assertEquals(expected, trail.entries.map { it.id })
            assertEquals(2, trail.carriedTimes)
            assertNull(trail.missingBefore)
        }
        val unrelated = all.single { it.weekStart == monday && it.id != original.id }
        assertEquals(0, carryHistory(unrelated.id, all)!!.carriedTimes)
        assertEquals(PlanResult.Missing, repository.carry(latest.id, monday.minusWeeks(1)))
    }

    @Test fun carryingBackwardOrWithinTheSameWeekDoesNotWriteOrCloseTheWeek() = runBlocking {
        repository.addCommitment(monday, "Keep this entry")
        val entry = repository.week(monday).first().single()
        for (target in listOf(monday, monday.minusWeeks(1))) {
            assertEquals(PlanResult.Missing, repository.carry(entry.id, target))
            assertEquals(PlanResult.Missing, repository.closeWeek(monday, "Do not save", mapOf(entry.id to CloseChoice.Carry), target))
            assertEquals(listOf(entry), repository.week(monday).first())
            assertNull(repository.review(monday).first())
        }
    }

    @Test fun missingAndAmbiguousOlderEntriesStayExplicitAndNeverBecomeAnotherIdea() {
        val parent = Commitment(1, monday, "Same title", CommitmentStatus.Carried, 1, resolvedAt = 2)
        val child = Commitment(3, monday.plusWeeks(1), "Same title", createdAt = 2, carriedFrom = monday)
        assertEquals(listOf(1L, 3L), carryHistory(3, listOf(parent, child))!!.entries.map { it.id })
        val ambiguous = carryHistory(3, listOf(parent, parent.copy(id = 2), child))!!
        assertEquals(listOf(3L), ambiguous.entries.map { it.id })
        assertEquals(monday, ambiguous.missingBefore)
        val removed = carryHistory(3, listOf(parent.copy(id = 2), child.copy(carriedFromId = 1)))!!
        assertEquals(listOf(3L), removed.entries.map { it.id })
        assertEquals(monday, removed.missingBefore)
        assertEquals(1, removed.carriedTimes)
        val duplicateChildren = carryHistory(3, listOf(parent, child, child.copy(id = 4)))!!
        assertEquals(listOf(3L), duplicateChildren.entries.map { it.id })
        assertEquals(monday, duplicateChildren.missingBefore)
        assertNull(carryHistory(999, listOf(parent, child)))
    }
}
