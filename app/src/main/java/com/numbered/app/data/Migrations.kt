package com.numbered.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema step the app has ever taken, oldest first.
 *
 * A person's weeks span decades, so the database is never rebuilt destructively. Changing an
 * entity means bumping [NumberedDatabase] to a new version, committing the new exported schema,
 * adding its migration here, pinning its hash in MigrationTest, and covering it with a test that
 * starts from the previous version's data.
 */
internal val MIGRATIONS: Array<Migration> = arrayOf(
    // Version 2 adds life chapters.
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `startWeek` INTEGER NOT NULL, `endWeek` INTEGER, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_startWeek` ON `chapters` (`startWeek`)")
        }
    },
    // Stable carry-over links. Only link old entries when their provenance is unambiguous.
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `commitments` ADD COLUMN `carriedFromId` INTEGER")
            db.execSQL(
                "UPDATE commitments SET carriedFromId = (SELECT source.id FROM commitments AS source " +
                    "WHERE source.weekStart = commitments.carriedFrom AND source.title = commitments.title " +
                    "AND source.status = 'Carried' AND source.resolvedAt = commitments.createdAt) " +
                    "WHERE carriedFrom < weekStart AND (SELECT COUNT(*) FROM commitments AS source " +
                    "WHERE source.weekStart = commitments.carriedFrom AND source.title = commitments.title " +
                    "AND source.status = 'Carried' AND source.resolvedAt = commitments.createdAt) = 1 " +
                    "AND (SELECT COUNT(*) FROM commitments AS child WHERE child.carriedFrom = commitments.carriedFrom " +
                    "AND child.title = commitments.title AND child.createdAt = commitments.createdAt) = 1",
            )
        }
    },
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `prioritiesPerWeek` INTEGER NOT NULL DEFAULT 3")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `otherThingsDoneEnabled` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `other_things_done` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`weekStart` INTEGER NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_other_things_done_weekStart` ON `other_things_done` (`weekStart`)")
        }
    },
)
