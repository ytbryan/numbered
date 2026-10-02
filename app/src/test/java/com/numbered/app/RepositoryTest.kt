package com.numbered.app

import androidx.test.core.app.ApplicationProvider
import com.numbered.app.data.Commitment
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.PlanResult
import com.numbered.app.domain.CloseChoice
import com.numbered.app.domain.CommitmentStatus
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private val db = NumberedDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val clock = Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneId.of("Asia/Singapore"))
    private val repository = NumberedRepository(db, clock)
    private val thisWeek = LocalDate.of(2026, 9, 28)
    private val nextWeek = thisWeek.plusWeeks(1)
    private val lastWeek = thisWeek.minusWeeks(1)

    @After fun close() = db.close()

    private fun week(start: LocalDate) = runBlocking { repository.week(start).first() }

    private fun titles(start: LocalDate, status: CommitmentStatus? = null) =
        week(start).filter { status == null || it.status == status }.map(Commitment::title)

    @Test fun aWeekHoldsThreeCommitments() = runBlocking {
        repeat(3) { assertEquals(PlanResult.Ok, repository.addCommitment(thisWeek, "Thing $it")) }
        assertEquals(PlanResult.WeekFull, repository.addCommitment(thisWeek, "One more"))
        assertEquals(3, week(thisWeek).size)
    }

    @Test fun resolvedCommitmentsFreeTheirSquare() = runBlocking {
        repeat(3) { repository.addCommitment(thisWeek, "Thing $it") }
        repository.returnToSomeday(week(thisWeek).first().id)
        assertEquals(PlanResult.Ok, repository.addCommitment(thisWeek, "Something better"))
        assertEquals(listOf("Thing 0"), repository.somedayWaiting().first().map { it.title })
    }

    @Test fun titlesAreTidiedAndBlankOnesRefused() = runBlocking {
        assertEquals(PlanResult.Blank, repository.addCommitment(thisWeek, "   \n "))
        repository.addCommitment(thisWeek, "  Call   mum\nabout Sunday ")
        assertEquals(listOf("Call mum about Sunday"), titles(thisWeek))
    }

    @Test fun carryingKeepsARecordInTheOriginalWeek() = runBlocking {
        repository.addCommitment(thisWeek, "Draft the talk")
        assertEquals(PlanResult.Ok, repository.carry(week(thisWeek).single().id, nextWeek))
        assertEquals(CommitmentStatus.Carried, week(thisWeek).single().status)
        val carried = week(nextWeek).single()
        assertEquals("Draft the talk", carried.title)
        assertEquals(CommitmentStatus.Open, carried.status)
        assertEquals(thisWeek, carried.carriedFrom)
    }

    @Test fun carryingIntoAFullWeekChangesNothing() = runBlocking {
        repeat(3) { repository.addCommitment(nextWeek, "Next $it") }
        repository.addCommitment(thisWeek, "Draft the talk")
        assertEquals(PlanResult.WeekFull, repository.carry(week(thisWeek).single().id, nextWeek))
        assertEquals(CommitmentStatus.Open, week(thisWeek).single().status)
        assertEquals(3, week(nextWeek).size)
    }

    @Test fun closingAWeekAppliesEveryChoiceAndSavesTheNote() = runBlocking {
        listOf("Finish report", "Draft the talk", "Fix the bike").forEach { repository.addCommitment(lastWeek, it) }
        val ids = week(lastWeek).associate { it.title to it.id }
        val result = repository.closeWeek(
            weekStart = lastWeek,
            note = "  Shipped the report.  ",
            choices = mapOf(
                ids.getValue("Finish report") to CloseChoice.Done,
                ids.getValue("Draft the talk") to CloseChoice.Carry,
                ids.getValue("Fix the bike") to CloseChoice.Someday,
            ),
            carryTo = thisWeek,
        )
        assertEquals(PlanResult.Ok, result)
        assertEquals(listOf("Finish report"), titles(lastWeek, CommitmentStatus.Done))
        assertEquals(listOf("Draft the talk"), titles(lastWeek, CommitmentStatus.Carried))
        assertEquals(listOf("Fix the bike"), titles(lastWeek, CommitmentStatus.ReturnedToSomeday))
        assertEquals(listOf("Draft the talk"), titles(thisWeek, CommitmentStatus.Open))
        assertEquals(listOf("Fix the bike"), repository.somedayWaiting().first().map { it.title })
        assertEquals("Shipped the report.", repository.review(lastWeek).first()?.note)
    }

    @Test fun closingIsAllOrNothingWhenCarriesWouldOverfill() = runBlocking {
        repeat(2) { repository.addCommitment(thisWeek, "Already $it") }
        repeat(2) { repository.addCommitment(lastWeek, "Unfinished $it") }
        val choices = week(lastWeek).associate { it.id to CloseChoice.Carry }
        assertEquals(PlanResult.WeekFull, repository.closeWeek(lastWeek, "note", choices, thisWeek))
        assertEquals(listOf(CommitmentStatus.Open, CommitmentStatus.Open), week(lastWeek).map { it.status })
        assertEquals(2, week(thisWeek).size)
        assertNull(repository.review(lastWeek).first())
    }

    @Test fun schedulingMovesAnItemOutOfSomedayOnlyWhenThereIsRoom() = runBlocking {
        repository.addSomeday("Learn to sail")
        val item = repository.somedayWaiting().first().single()
        repeat(3) { repository.addCommitment(thisWeek, "Busy $it") }
        assertEquals(PlanResult.WeekFull, repository.schedule(item.id, thisWeek))
        assertEquals(1, repository.somedayWaiting().first().size)
        assertEquals(PlanResult.Ok, repository.schedule(item.id, nextWeek))
        assertEquals(0, repository.somedayWaiting().first().size)
        assertEquals(listOf("Learn to sail"), titles(nextWeek))
    }

    @Test fun lettingGoAndBringingBackAreReversible() = runBlocking {
        repository.addSomeday("Learn to sail")
        val item = repository.somedayWaiting().first().single()
        repository.letGoSomeday(item.id)
        assertEquals(listOf("Learn to sail"), repository.somedayLetGo().first().map { it.title })
        repository.bringBackSomeday(item.id)
        val back = repository.somedayWaiting().first().single()
        assertEquals(clock.millis(), back.keptAt)
        assertEquals(0, repository.somedayLetGo().first().size)
    }

    @Test fun removeAndRestoreRoundTrip() = runBlocking {
        repository.addCommitment(thisWeek, "Plan mum's 70th")
        val removed = repository.remove(week(thisWeek).single().id)!!
        assertEquals(0, week(thisWeek).size)
        assertEquals(PlanResult.Ok, repository.restore(removed))
        assertEquals(removed, week(thisWeek).single())
    }

    @Test fun undoingDoneReopensOnlyDoneCommitments() = runBlocking {
        repository.addCommitment(thisWeek, "Plan mum's 70th")
        val id = week(thisWeek).single().id
        repository.setDone(id, true)
        assertEquals(clock.millis(), week(thisWeek).single().resolvedAt)
        repository.setDone(id, false)
        assertEquals(CommitmentStatus.Open, week(thisWeek).single().status)
        assertNull(week(thisWeek).single().resolvedAt)
        repository.letGo(id)
        repository.setDone(id, true)
        assertEquals(CommitmentStatus.LetGo, week(thisWeek).single().status)
    }

    @Test fun updatingTheProfileNeverMovesTheWeekLayout() = runBlocking {
        repository.saveProfile(LocalDate.of(1989, 12, 2), 80, gentle = false, firstDayOfWeek = DayOfWeek.MONDAY)
        repository.saveProfile(LocalDate.of(1990, 1, 5), 90, gentle = true, firstDayOfWeek = DayOfWeek.SUNDAY)
        val profile = repository.profile().first()!!
        assertEquals(LocalDate.of(1990, 1, 5), profile.birthDate)
        assertEquals(90, profile.horizonYears)
        assertEquals(true, profile.gentle)
        assertEquals(DayOfWeek.MONDAY, profile.firstDayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 1), profile.startedOn)
    }

    @Test fun summariesCountEachWeek() = runBlocking {
        repeat(2) { repository.addCommitment(lastWeek, "Last $it") }
        repository.setDone(week(lastWeek).first().id, true)
        repository.addCommitment(nextWeek, "Pinned")
        val summaries = repository.summaries().first()
        assertEquals(1, summaries.getValue(lastWeek).done)
        assertEquals(2, summaries.getValue(lastWeek).counted)
        assertEquals(1, summaries.getValue(nextWeek).occupied)
        assertNull(summaries[thisWeek])
    }
}
