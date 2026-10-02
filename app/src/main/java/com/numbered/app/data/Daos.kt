package com.numbered.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): Profile?

    @Upsert
    suspend fun upsert(profile: Profile)
}

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM commitments WHERE weekStart = :weekStart ORDER BY id")
    fun observeWeek(weekStart: LocalDate): Flow<List<Commitment>>

    @Query("SELECT * FROM commitments WHERE weekStart = :weekStart ORDER BY id")
    suspend fun week(weekStart: LocalDate): List<Commitment>

    @Query("SELECT * FROM commitments ORDER BY weekStart DESC, id")
    fun observeAll(): Flow<List<Commitment>>

    @Query("SELECT weekStart, status FROM commitments")
    fun observeStatuses(): Flow<List<WeekStatusRow>>

    @Query("SELECT * FROM commitments WHERE id = :id")
    suspend fun get(id: Long): Commitment?

    /** Commitments that occupy one of the week's squares. */
    @Query("SELECT COUNT(*) FROM commitments WHERE weekStart = :weekStart AND status IN ('Open', 'Done')")
    suspend fun occupied(weekStart: LocalDate): Int

    @Query("SELECT * FROM commitments ORDER BY weekStart, id")
    suspend fun all(): List<Commitment>

    @Insert
    suspend fun insert(commitment: Commitment): Long

    @Insert
    suspend fun insertAll(commitments: List<Commitment>)

    @Update
    suspend fun update(commitment: Commitment)

    @Query("DELETE FROM commitments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM commitments")
    suspend fun deleteAll()
}

@Dao
interface SomedayDao {
    @Query("SELECT * FROM someday ORDER BY createdAt DESC, id")
    fun observeAll(): Flow<List<SomedayItem>>

    @Query("SELECT * FROM someday WHERE letGoAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeWaiting(): Flow<List<SomedayItem>>

    @Query("SELECT * FROM someday WHERE letGoAt IS NOT NULL ORDER BY letGoAt DESC, id DESC")
    fun observeLetGo(): Flow<List<SomedayItem>>

    @Query("SELECT * FROM someday WHERE id = :id")
    suspend fun get(id: Long): SomedayItem?

    @Query("SELECT * FROM someday ORDER BY id")
    suspend fun all(): List<SomedayItem>

    @Insert
    suspend fun insert(item: SomedayItem): Long

    @Insert
    suspend fun insertAll(items: List<SomedayItem>)

    @Update
    suspend fun update(item: SomedayItem)

    @Query("DELETE FROM someday WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM someday")
    suspend fun deleteAll()
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters ORDER BY startWeek, id")
    fun observeAll(): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters ORDER BY startWeek, id")
    suspend fun all(): List<Chapter>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: Long): Chapter?

    @Insert
    suspend fun insert(chapter: Chapter): Long

    @Insert
    suspend fun insertAll(chapters: List<Chapter>)

    @Update
    suspend fun update(chapter: Chapter)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM chapters")
    suspend fun deleteAll()
}

@Dao
interface WeekReviewDao {
    @Query("SELECT * FROM week_reviews WHERE weekStart = :weekStart")
    fun observe(weekStart: LocalDate): Flow<WeekReview?>

    @Query("SELECT * FROM week_reviews")
    fun observeAll(): Flow<List<WeekReview>>

    @Query("SELECT * FROM week_reviews ORDER BY weekStart")
    suspend fun all(): List<WeekReview>

    @Upsert
    suspend fun upsert(review: WeekReview)

    @Insert
    suspend fun insertAll(reviews: List<WeekReview>)

    @Query("DELETE FROM week_reviews")
    suspend fun deleteAll()
}
