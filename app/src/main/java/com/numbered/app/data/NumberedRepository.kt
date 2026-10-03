package com.numbered.app.data

import androidx.room.withTransaction
import com.numbered.app.domain.CloseChoice
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.DEFAULT_PRIORITIES_PER_WEEK
import com.numbered.app.domain.MAX_PRIORITIES_PER_WEEK
import com.numbered.app.domain.WeekSummary
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class PlanResult { Ok, WeekFull, Blank, Missing }

/** The exact rows changed by one planning move, held only while Undo is offered. */
data class PlanningUndo(
    val originalCommitment: Commitment? = null,
    val originalSomeday: SomedayItem? = null,
    val changedCommitment: Commitment? = null,
    val createdCommitment: Commitment? = null,
    val createdSomeday: SomedayItem? = null,
)

data class PlanningMove(val result: PlanResult, val undo: PlanningUndo? = null)

/** One week's close: its note, and what happens to each unfinished commitment. */
data class WeekClosing(val weekStart: LocalDate, val note: String, val choices: Map<Long, CloseChoice>)

/** Every write that must respect a week's chosen limit goes through one transaction here. */
class NumberedRepository(
    private val db: NumberedDatabase,
    private val clock: Clock,
) {
    private val profiles = db.profiles()
    private val commitments = db.commitments()
    private val otherThingsDone = db.otherThingsDone()
    private val someday = db.someday()
    private val reviews = db.reviews()
    private val chapters = db.chapters()

    fun profile(): Flow<Profile?> = profiles.observe()

    suspend fun currentProfile(): Profile? = profiles.get()

    suspend fun setPrioritiesPerWeek(prioritiesPerWeek: Int) {
        require(prioritiesPerWeek in 1..MAX_PRIORITIES_PER_WEEK)
        db.withTransaction {
            profiles.get()?.let { profiles.upsert(it.copy(prioritiesPerWeek = prioritiesPerWeek)) }
        }
    }

    suspend fun setOtherThingsDoneEnabled(enabled: Boolean) {
        db.withTransaction {
            profiles.get()?.let { profiles.upsert(it.copy(otherThingsDoneEnabled = enabled)) }
        }
    }

    private suspend fun priorityLimit(): Int = profiles.get()?.prioritiesPerWeek ?: DEFAULT_PRIORITIES_PER_WEEK

    /** Creates the profile, or updates the personal details while keeping the week layout fixed. */
    suspend fun saveProfile(birthDate: LocalDate, horizonYears: Int, gentle: Boolean, firstDayOfWeek: DayOfWeek) {
        db.withTransaction {
            val existing = profiles.get()
            profiles.upsert(
                existing?.copy(birthDate = birthDate, horizonYears = horizonYears, gentle = gentle)
                    ?: Profile(
                        birthDate = birthDate,
                        horizonYears = horizonYears,
                        firstDayOfWeek = firstDayOfWeek,
                        gentle = gentle,
                        startedOn = LocalDate.now(clock),
                    ),
            )
        }
    }

    fun commitments(): Flow<List<Commitment>> = commitments.observeAll()

    fun somedayAll(): Flow<List<SomedayItem>> = someday.observeAll()

    fun week(weekStart: LocalDate): Flow<List<Commitment>> = commitments.observeWeek(weekStart)

    fun otherThingsDone(weekStart: LocalDate): Flow<List<OtherThingDone>> = otherThingsDone.observeWeek(weekStart)

    fun allOtherThingsDone(): Flow<List<OtherThingDone>> = otherThingsDone.observeAll()

    suspend fun addOtherThingDone(weekStart: LocalDate, title: String): PlanResult {
        val clean = title.cleanTitle() ?: return PlanResult.Blank
        db.withTransaction {
            otherThingsDone.insert(OtherThingDone(weekStart = weekStart, title = clean, createdAt = clock.millis()))
        }
        return PlanResult.Ok
    }

    suspend fun renameOtherThingDone(id: Long, title: String): PlanResult {
        val clean = title.cleanTitle() ?: return PlanResult.Blank
        return db.withTransaction {
            val item = otherThingsDone.get(id) ?: return@withTransaction PlanResult.Missing
            otherThingsDone.update(item.copy(title = clean))
            PlanResult.Ok
        }
    }

    suspend fun removeOtherThingDone(id: Long): OtherThingDone? = db.withTransaction {
        otherThingsDone.get(id)?.also { otherThingsDone.delete(id) }
    }

    suspend fun restoreOtherThingDone(item: OtherThingDone) = db.withTransaction { otherThingsDone.insert(item) }

    fun summaries(): Flow<Map<LocalDate, WeekSummary>> = commitments.observeStatuses()
        .map { rows -> rows.groupBy(WeekStatusRow::weekStart) { it.status }.mapValues { WeekSummary.of(it.value) } }
        .distinctUntilChanged()

    fun review(weekStart: LocalDate): Flow<WeekReview?> = reviews.observe(weekStart)

    fun reviews(): Flow<Map<LocalDate, WeekReview>> = reviews.observeAll().map { all -> all.associateBy(WeekReview::weekStart) }

    fun somedayWaiting(): Flow<List<SomedayItem>> = someday.observeWaiting()

    fun chapters(): Flow<List<Chapter>> = chapters.observeAll()

    suspend fun chapter(id: Long): Chapter? = chapters.get(id)

    /** Adds a chapter, or renames and moves the one with [id]. An end before the start becomes the start. */
    suspend fun saveChapter(id: Long?, title: String, startWeek: LocalDate, endWeek: LocalDate?): PlanResult {
        val clean = title.cleanTitle() ?: return PlanResult.Blank
        val end = endWeek?.let { maxOf(it, startWeek) }
        return db.withTransaction {
            if (id == null) {
                chapters.insert(Chapter(title = clean, startWeek = startWeek, endWeek = end, createdAt = clock.millis()))
            } else {
                val existing = chapters.get(id) ?: return@withTransaction PlanResult.Missing
                chapters.update(existing.copy(title = clean, startWeek = startWeek, endWeek = end))
            }
            PlanResult.Ok
        }
    }

    /** Removes a chapter. Returns it so the caller can offer Undo. */
    suspend fun deleteChapter(id: Long): Chapter? = db.withTransaction {
        chapters.get(id)?.also { chapters.delete(id) }
    }

    suspend fun restoreChapter(chapter: Chapter) {
        chapters.insert(chapter)
    }

    fun somedayLetGo(): Flow<List<SomedayItem>> = someday.observeLetGo()

    suspend fun addCommitment(weekStart: LocalDate, title: String): PlanResult {
        val clean = title.cleanTitle() ?: return PlanResult.Blank
        return db.withTransaction {
            if (commitments.occupied(weekStart) >= priorityLimit()) return@withTransaction PlanResult.WeekFull
            commitments.insert(Commitment(weekStart = weekStart, title = clean, createdAt = clock.millis()))
            PlanResult.Ok
        }
    }

    suspend fun setDone(id: Long, done: Boolean) {
        db.withTransaction {
            val commitment = commitments.get(id) ?: return@withTransaction
            val allowed = if (done) commitment.status == CommitmentStatus.Open else commitment.status == CommitmentStatus.Done
            if (!allowed) return@withTransaction
            commitments.update(
                if (done) commitment.copy(status = CommitmentStatus.Done, resolvedAt = clock.millis())
                else commitment.copy(status = CommitmentStatus.Open, resolvedAt = null),
            )
        }
    }

    suspend fun rename(id: Long, title: String): PlanResult {
        val clean = title.cleanTitle() ?: return PlanResult.Blank
        return db.withTransaction {
            val commitment = commitments.get(id) ?: return@withTransaction PlanResult.Missing
            commitments.update(commitment.copy(title = clean))
            PlanResult.Ok
        }
    }

    /** Removes a mistaken entry entirely. Returns it so the caller can offer Undo. */
    suspend fun remove(id: Long): Commitment? = db.withTransaction {
        commitments.get(id)?.also { commitments.delete(id) }
    }

    /** Puts a removed commitment back exactly as it was, if its week still has room. */
    suspend fun restore(commitment: Commitment): PlanResult = db.withTransaction {
        val occupies = commitment.status == CommitmentStatus.Open || commitment.status == CommitmentStatus.Done
        if (occupies && commitments.occupied(commitment.weekStart) >= priorityLimit()) {
            return@withTransaction PlanResult.WeekFull
        }
        commitments.insert(commitment)
        PlanResult.Ok
    }

    /** Moves an unfinished commitment to [toWeek], keeping a Carried record in its original week. */
    suspend fun carry(id: Long, toWeek: LocalDate): PlanResult = carryWithUndo(id, toWeek).result

    suspend fun carryWithUndo(id: Long, toWeek: LocalDate): PlanningMove = db.withTransaction {
        val original = commitments.get(id) ?: return@withTransaction PlanningMove(PlanResult.Missing)
        if (original.status != CommitmentStatus.Open || original.weekStart >= toWeek) {
            return@withTransaction PlanningMove(PlanResult.Missing)
        }
        if (commitments.occupied(toWeek) >= priorityLimit()) return@withTransaction PlanningMove(PlanResult.WeekFull)
        val createdId = carryUnchecked(original, toWeek, clock.millis())
        PlanningMove(PlanResult.Ok, PlanningUndo(
            originalCommitment = original,
            changedCommitment = commitments.get(id),
            createdCommitment = commitments.get(createdId),
        ))
    }

    suspend fun returnToSomedayWithUndo(id: Long): PlanningMove = db.withTransaction {
        val original = commitments.get(id)?.takeIf { it.status == CommitmentStatus.Open }
            ?: return@withTransaction PlanningMove(PlanResult.Missing)
        val createdId = resolveUnchecked(original, CommitmentStatus.ReturnedToSomeday, clock.millis())!!
        PlanningMove(PlanResult.Ok, PlanningUndo(
            originalCommitment = original,
            changedCommitment = commitments.get(id),
            createdSomeday = someday.get(createdId),
        ))
    }

    suspend fun scheduleWithUndo(id: Long, weekStart: LocalDate): PlanningMove = db.withTransaction {
        val item = someday.get(id)?.takeIf { it.letGoAt == null }
            ?: return@withTransaction PlanningMove(PlanResult.Missing)
        if (commitments.occupied(weekStart) >= priorityLimit()) return@withTransaction PlanningMove(PlanResult.WeekFull)
        someday.delete(id)
        val createdId = commitments.insert(Commitment(weekStart = weekStart, title = item.title, createdAt = clock.millis()))
        PlanningMove(PlanResult.Ok, PlanningUndo(originalSomeday = item, createdCommitment = commitments.get(createdId)))
    }

    /** Refuses stale Undo actions and restores both sides of a move atomically. */
    suspend fun undoPlanningMove(undo: PlanningUndo): PlanResult = db.withTransaction {
        if (undo.changedCommitment?.let { commitments.get(it.id) != it } == true ||
            undo.createdCommitment?.let { commitments.get(it.id) != it } == true ||
            undo.createdSomeday?.let { someday.get(it.id) != it } == true ||
            undo.originalSomeday?.let { someday.get(it.id) != null } == true
        ) return@withTransaction PlanResult.Missing
        undo.originalCommitment?.let {
            if (commitments.occupied(it.weekStart) >= priorityLimit()) return@withTransaction PlanResult.WeekFull
        }
        undo.createdCommitment?.let { commitments.delete(it.id) }
        undo.createdSomeday?.let { someday.delete(it.id) }
        undo.originalCommitment?.let { commitments.update(it) }
        undo.originalSomeday?.let { someday.insert(it) }
        PlanResult.Ok
    }

    suspend fun returnToSomeday(id: Long): PlanResult = resolveOpen(id, CommitmentStatus.ReturnedToSomeday)

    suspend fun letGo(id: Long): PlanResult = resolveOpen(id, CommitmentStatus.LetGo)

    private suspend fun resolveOpen(id: Long, status: CommitmentStatus): PlanResult = db.withTransaction {
        val commitment = commitments.get(id)?.takeIf { it.status == CommitmentStatus.Open }
            ?: return@withTransaction PlanResult.Missing
        resolveUnchecked(commitment, status, clock.millis())
        PlanResult.Ok
    }

    suspend fun addSomeday(title: String): PlanResult {
        val clean = title.cleanTitle() ?: return PlanResult.Blank
        someday.insert(SomedayItem(title = clean, createdAt = clock.millis()))
        return PlanResult.Ok
    }

    suspend fun keepSomeday(id: Long) = updateSomeday(id) { it.copy(keptAt = clock.millis()) }

    suspend fun letGoSomeday(id: Long) = updateSomeday(id) { it.copy(letGoAt = clock.millis()) }

    /** Brings a let-go item back, treating it as freshly kept so it does not ask again at once. */
    suspend fun bringBackSomeday(id: Long) = updateSomeday(id) { it.copy(letGoAt = null, keptAt = clock.millis()) }

    private suspend fun updateSomeday(id: Long, change: (SomedayItem) -> SomedayItem) {
        db.withTransaction { someday.get(id)?.let { someday.update(change(it)) } }
    }

    suspend fun deleteSomeday(id: Long): SomedayItem? = db.withTransaction {
        someday.get(id)?.also { someday.delete(id) }
    }

    suspend fun restoreSomeday(item: SomedayItem) {
        someday.insert(item)
    }

    /** Moves a Someday item into a week's squares. */
    suspend fun schedule(id: Long, weekStart: LocalDate): PlanResult = scheduleWithUndo(id, weekStart).result

    /**
     * Closes [weekStart]: applies a choice to every unfinished commitment and records the note.
     * Carried commitments go to [carryTo]. Nothing changes if they would overfill it.
     */
    suspend fun closeWeek(
        weekStart: LocalDate,
        note: String,
        choices: Map<Long, CloseChoice>,
        carryTo: LocalDate,
    ): PlanResult = closeWeeks(listOf(WeekClosing(weekStart, note, choices)), carryTo)

    /**
     * Closes several weeks at once, all or nothing. Everything carried from any of them goes to
     * [carryTo], and nothing changes if together they would overfill it.
     */
    suspend fun closeWeeks(closings: List<WeekClosing>, carryTo: LocalDate): PlanResult = db.withTransaction {
        val now = clock.millis()
        val open = closings.associateWith { closing ->
            commitments.week(closing.weekStart).filter { it.status == CommitmentStatus.Open }
        }
        if (open.any { (closing, unfinished) ->
                unfinished.any { closing.choices[it.id] == CloseChoice.Carry && it.weekStart >= carryTo }
            }) return@withTransaction PlanResult.Missing
        val carrying = open.entries.sumOf { (closing, unfinished) -> unfinished.count { closing.choices[it.id] == CloseChoice.Carry } }
        if (carrying > 0 && commitments.occupied(carryTo) + carrying > priorityLimit()) {
            return@withTransaction PlanResult.WeekFull
        }
        open.forEach { (closing, unfinished) ->
            unfinished.forEach { commitment ->
                when (closing.choices[commitment.id]) {
                    CloseChoice.Done -> resolveUnchecked(commitment, CommitmentStatus.Done, now)
                    CloseChoice.Carry -> carryUnchecked(commitment, carryTo, now)
                    CloseChoice.Someday -> resolveUnchecked(commitment, CommitmentStatus.ReturnedToSomeday, now)
                    CloseChoice.LetGo -> resolveUnchecked(commitment, CommitmentStatus.LetGo, now)
                    null -> Unit
                }
            }
            reviews.upsert(WeekReview(weekStart = closing.weekStart, note = closing.note.trim(), closedAt = now))
        }
        PlanResult.Ok
    }

    /** Everything stored, read in one transaction so the copy is consistent. Null before setup. */
    suspend fun snapshot(): Snapshot? = db.withTransaction {
        profiles.get()?.let { profile ->
            Snapshot(profile, commitments.all(), someday.all(), reviews.all(), chapters.all(),
                otherThingsDone = otherThingsDone.all(), savedAt = clock.millis())
        }
    }

    /** Replaces everything stored with [snapshot], all at once or not at all. */
    suspend fun replaceAll(snapshot: Snapshot) {
        db.withTransaction {
            commitments.deleteAll()
            otherThingsDone.deleteAll()
            someday.deleteAll()
            reviews.deleteAll()
            chapters.deleteAll()
            profiles.upsert(snapshot.profile)
            commitments.insertAll(snapshot.commitments)
            otherThingsDone.insertAll(snapshot.otherThingsDone)
            someday.insertAll(snapshot.someday)
            reviews.insertAll(snapshot.reviews)
            chapters.insertAll(snapshot.chapters)
        }
    }

    private suspend fun carryUnchecked(commitment: Commitment, toWeek: LocalDate, now: Long): Long {
        commitments.update(commitment.copy(status = CommitmentStatus.Carried, resolvedAt = now))
        return commitments.insert(
            Commitment(weekStart = toWeek, title = commitment.title, createdAt = now, carriedFrom = commitment.weekStart, carriedFromId = commitment.id),
        )
    }

    private suspend fun resolveUnchecked(commitment: Commitment, status: CommitmentStatus, now: Long): Long? {
        commitments.update(commitment.copy(status = status, resolvedAt = now))
        return if (status == CommitmentStatus.ReturnedToSomeday) {
            someday.insert(SomedayItem(title = commitment.title, createdAt = now))
        } else null
    }
}

/** Trims and collapses whitespace so pasted titles stay on one tidy line. */
internal fun String.cleanTitle(): String? = trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }
