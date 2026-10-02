package com.numbered.app.data

import androidx.room.migration.Migration

/**
 * Every schema step the app has ever taken, oldest first.
 *
 * A person's weeks span decades, so the database is never rebuilt destructively. Changing an
 * entity means bumping [NumberedDatabase] to a new version, committing the new exported schema,
 * adding its migration here, pinning its hash in MigrationTest, and covering it with a test that
 * starts from the previous version's data.
 */
internal val MIGRATIONS: Array<Migration> = arrayOf()
