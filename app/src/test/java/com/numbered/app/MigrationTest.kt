package com.numbered.app

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.numbered.app.data.Chapter
import com.numbered.app.data.MIGRATIONS
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.domain.CommitmentStatus
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the promise that saved weeks survive every app update.
 *
 * Each exported schema is frozen once released: its identity hash is pinned here, so changing an
 * entity without bumping the database version fails instead of silently rewriting the old schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NumberedDatabase::class.java)

    @Test fun everyReleasedSchemaIsPinned() {
        val exported = File(SCHEMA_DIR).listFiles().orEmpty()
            .associate { it.nameWithoutExtension.toInt() to identityHash(it) }
        assertEquals(
            "Bump NumberedDatabase.VERSION and add a migration instead of changing a released schema.",
            FROZEN_SCHEMAS,
            exported,
        )
        assertEquals(FROZEN_SCHEMAS.keys.max(), NumberedDatabase.VERSION)
    }

    /** Each migration step, checked by Room against the exported schema it should produce. */
    @Test fun version1MigratesToVersion2() {
        val name = "migration-1-2.db"
        helper.createDatabase(name, 1).use(::seedVersion1)
        helper.runMigrationsAndValidate(name, 2, true, *MIGRATIONS).close()
        context.deleteDatabase(name)
    }

    /** Data written by the first release opens, with every migration applied, in the current app. */
    @Test fun version1DataOpensInTheCurrentApp() {
        val name = "migration-from-1.db"
        helper.createDatabase(name, 1).use(::seedVersion1)

        val db = NumberedDatabase.open(context, name)
        try {
            runBlocking {
                val profile = db.profiles().get()!!
                assertEquals(LocalDate.of(1989, 12, 2), profile.birthDate)
                assertEquals(DayOfWeek.MONDAY, profile.firstDayOfWeek)
                val week = db.commitments().week(WEEK)
                assertEquals(listOf("Finish the grant draft", "Renew passport photos"), week.map { it.title })
                assertEquals(listOf(CommitmentStatus.Carried, CommitmentStatus.Done), week.map { it.status })
                assertEquals("A steady week.", db.reviews().observe(WEEK).first()!!.note)
                assertEquals(listOf("Learn to sail"), db.someday().observeWaiting().first().map { it.title })
                // Version 2 adds an empty chapters table that takes new rows.
                assertEquals(emptyList<Any>(), db.chapters().all())
                db.chapters().insert(Chapter(title = "Moved to Singapore", startWeek = WEEK, endWeek = null, createdAt = 4))
                assertEquals(listOf("Moved to Singapore"), db.chapters().all().map { it.title })
            }
            // Room validates every table against the current schema when the database opens.
            db.openHelper.writableDatabase
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun version2PreservesDataAndOnlyLinksUnambiguousCarries() {
        val name = "migration-2-3.db"
        helper.createDatabase(name, 2).use { db ->
            seedVersion1(db)
            val from = WEEK.toEpochDay()
            val to = WEEK.plusWeeks(1).toEpochDay()
            db.execSQL(
                "INSERT INTO commitments (id, weekStart, title, status, createdAt, resolvedAt, carriedFrom) VALUES " +
                    "(10, ?, 'Same title', 'Carried', 1, 50, NULL), (11, ?, 'Same title', 'Carried', 1, 50, NULL), " +
                    "(12, ?, 'Same title', 'Open', 50, NULL, ?), (13, ?, 'Finish the grant draft', 'Open', 2, NULL, ?), " +
                    "(14, ?, 'Two children', 'Carried', 1, 70, NULL), " +
                    "(15, ?, 'Two children', 'Open', 70, NULL, ?), (16, ?, 'Two children', 'Open', 70, NULL, ?)",
                arrayOf(from, from, to, from, to, from, from, to, from, to, from),
            )
        }
        helper.runMigrationsAndValidate(name, 3, true, *MIGRATIONS).close()
        val db = NumberedDatabase.open(context, name)
        try {
            runBlocking {
                val entries = db.commitments().all()
                assertEquals(9, entries.size)
                assertEquals(null, entries.single { it.id == 12L }.carriedFromId)
                assertEquals(1L, entries.single { it.id == 13L }.carriedFromId)
                assertEquals(null, entries.single { it.id == 15L }.carriedFromId)
                assertEquals(null, entries.single { it.id == 16L }.carriedFromId)
                assertEquals(WEEK, entries.single { it.id == 12L }.carriedFrom)
                assertEquals("A steady week.", db.reviews().all().single().note)
                assertEquals("Learn to sail", db.someday().all().single().title)
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun version3AddsPlanningDefaultsAndKeepsExistingWeeks() {
        val name = "migration-3-4.db"
        helper.createDatabase(name, 3).use(::seedVersion1)
        helper.runMigrationsAndValidate(name, 4, true, *MIGRATIONS).close()
        val db = NumberedDatabase.open(context, name)
        try {
            runBlocking {
                val profile = db.profiles().get()!!
                assertEquals(3, profile.prioritiesPerWeek)
                assertEquals(false, profile.otherThingsDoneEnabled)
                assertEquals(2, db.commitments().week(WEEK).size)
                assertEquals(emptyList<Any>(), db.otherThingsDone().all())
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun version4PreservesCommitmentOrder() {
        val name = "migration-4-5.db"
        helper.createDatabase(name, 4).use { db ->
            val week = WEEK.toEpochDay()
            db.execSQL(
                "INSERT INTO commitments (id, weekStart, title, status, createdAt, resolvedAt, carriedFrom, carriedFromId) " +
                    "VALUES (8, ?, 'Second', 'Open', 1, NULL, NULL, NULL), " +
                    "(3, ?, 'First', 'Open', 1, NULL, NULL, NULL)",
                arrayOf(week, week),
            )
        }
        helper.runMigrationsAndValidate(name, 5, true, *MIGRATIONS).close()
        val db = NumberedDatabase.open(context, name)
        try {
            runBlocking {
                assertEquals(listOf("First", "Second"), db.commitments().week(WEEK).map { it.title })
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    private fun seedVersion1(db: SupportSQLiteDatabase) {
        val week = WEEK.toEpochDay()
        db.execSQL(
            "INSERT INTO profile (id, birthDate, horizonYears, firstDayOfWeek, gentle, startedOn) VALUES (1, ?, 80, 1, 0, ?)",
            arrayOf(LocalDate.of(1989, 12, 2).toEpochDay(), LocalDate.of(2026, 1, 5).toEpochDay()),
        )
        db.execSQL(
            "INSERT INTO commitments (weekStart, title, status, createdAt, resolvedAt, carriedFrom) VALUES " +
                "(?, 'Finish the grant draft', 'Carried', 1, 2, NULL), (?, 'Renew passport photos', 'Done', 1, 2, NULL)",
            arrayOf(week, week),
        )
        db.execSQL("INSERT INTO week_reviews (weekStart, note, closedAt) VALUES (?, 'A steady week.', 3)", arrayOf(week))
        db.execSQL("INSERT INTO someday (title, createdAt, keptAt, letGoAt) VALUES ('Learn to sail', 1, NULL, NULL)")
    }

    private fun identityHash(file: File): String =
        Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject["identityHash"]!!.jsonPrimitive.content

    private companion object {
        val WEEK: LocalDate = LocalDate.of(2026, 9, 21)
        const val SCHEMA_DIR = "schemas/com.numbered.app.data.NumberedDatabase"

        /** Released schema versions and their identity hashes. Never edit an entry once released. */
        val FROZEN_SCHEMAS = mapOf(
            1 to "23828907e852728555f8f59d986c2f97",
            2 to "b8169d580e770840a3abf439edef752a",
            3 to "11e9ceb29e61c4a6b91d2cc5c572eb8c",
            4 to "d4c7b95f171c5f0fde4f8e1edf136132",
            5 to "cd07747e2f37fc3915735a4b555d387b",
        )
    }
}
