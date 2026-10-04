package com.numbered.app

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.numbered.app.backup.AutoBackup
import com.numbered.app.backup.AutoBackupProblem
import com.numbered.app.backup.AutoBackupResult
import com.numbered.app.backup.AutoBackupWorker
import com.numbered.app.backup.Retention
import com.numbered.app.data.BackupFormat
import com.numbered.app.data.BackupRead
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.security.PassphraseVault
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupTest {
    private val zone = ZoneId.of("Asia/Singapore")
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val db = NumberedDatabase.inMemory(app)
    private val clock = MovableClock(LocalDateTime.of(2026, 10, 4, 9, 0).atZone(zone).toInstant(), zone)
    private val vault = FakeVault()
    private val thisWeek = LocalDate.of(2026, 9, 28)
    private lateinit var container: AppContainer
    private lateinit var directory: File
    private lateinit var tree: Uri

    @get:Rule val folders = TemporaryFolder()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        container = AppContainer(app, db, clock, vault)
        (app as NumberedApp).replaceContainer(container)
        runBlocking {
            container.repository.saveProfile(LocalDate.of(1989, 12, 2), 80, gentle = false, firstDayOfWeek = DayOfWeek.MONDAY)
            container.repository.addCommitment(thisWeek, "Finish the grant draft")
        }
        directory = folders.newFolder("Backups")
        tree = FolderDocumentsProvider.register(directory)
    }

    @After fun close() = db.close()

    private val backup: AutoBackup get() = container.autoBackup
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    private fun files(): List<String> = directory.list().orEmpty().sorted()

    private fun autoFiles(): List<String> = files().filter { Retention.parse(it) != null }

    private fun later(duration: Duration = Duration.ofDays(1)) {
        clock.now = clock.now.plus(duration)
    }

    private fun change(title: String) = runBlocking { container.repository.addSomeday(title) }

    private fun dailyWork(): WorkInfo? = WorkManager.getInstance(app).getWorkInfosForUniqueWork(AutoBackup.WORK_NAME).get().singleOrNull()

    private fun persistedFolders(): List<Uri> = app.contentResolver.persistedUriPermissions.map { it.uri }

    @Test fun retentionKeepsTheNewestWeekAndOneCopyForEachRecentMonth() {
        val first = LocalDateTime.of(2025, 6, 1, 9, 0)
        val daily = (0L until 500L).map { Retention.fileName(first.plusDays(it)) }
        val others = listOf("notes.txt", "numbered-2026-01-01.json", "numbered-auto-2026-13-01-000000.json", "numbered-auto-latest.json")
        val deleted = Retention.toDelete(daily + others)
        assertTrue(deleted.none { it in others })
        val kept = (daily - deleted.toSet()).map { Retention.parse(it)!!.toLocalDate() }
        val last = first.plusDays(499).toLocalDate()
        // The newest seven days, plus the last day of each of the eleven months before this one.
        val expected = (0L until 7L).map { last.minusDays(it) } +
            (1L..11L).map { last.withDayOfMonth(1).minusMonths(it - 1).minusDays(1) }
        assertEquals(expected.sorted(), kept.sorted())
        assertEquals(emptyList<String>(), Retention.toDelete(daily.take(3) + others))
    }

    @Test fun turningOnSavesAReadableCopyAndSchedulesADailyRun() = runBlocking {
        assertEquals(AutoBackupResult.Saved, backup.enable(tree, passphrase = null))
        assertEquals(listOf("numbered-auto-2026-10-04-090000.json"), files())
        val read = BackupFormat.decode(File(directory, files().single()).readText()) as BackupRead.Ok
        assertEquals(container.repository.snapshot()!!.copy(savedAt = 0), read.snapshot.copy(savedAt = 0))
        val status = backup.status.value
        assertTrue(status.enabled)
        assertEquals("Backups", status.folderName)
        assertEquals(clock.millis(), status.lastSavedAt)
        assertFalse(status.protected)
        assertNull(status.problem)
        assertEquals(listOf(tree), persistedFolders())
        assertEquals(WorkInfo.State.ENQUEUED, dailyWork()?.state)
        // The choice belongs to this phone and outlives the process.
        assertEquals(status, AutoBackup(app, container.repository, clock, vault).status.value)
    }

    @Test fun dailyRunsSaveOnlyWhenSomethingChanged() = runBlocking {
        backup.enable(tree, passphrase = null)
        later()
        assertEquals(AutoBackupResult.Unchanged, backup.run())
        assertEquals(1, files().size)
        assertEquals(clock.millis(), backup.status.value.lastSavedAt)
        change("Learn to sail")
        later()
        assertEquals(AutoBackupResult.Saved, backup.run())
        assertEquals(listOf("numbered-auto-2026-10-04-090000.json", "numbered-auto-2026-10-06-090000.json"), files())
        assertTrue(File(directory, files().last()).readText().contains("Learn to sail"))
        // A copy deleted from the folder is saved again even though nothing changed.
        File(directory, files().last()).delete()
        later()
        assertEquals(AutoBackupResult.Saved, backup.run())
        assertEquals(2, files().size)
    }

    @Test fun oldCopiesArePrunedAndOtherFilesAreNeverTouched() = runBlocking {
        File(directory, "notes.txt").writeText("mine")
        File(directory, "numbered-2026-09-01.json").writeText("an export")
        backup.enable(tree, passphrase = null)
        repeat(39) { day ->
            later()
            change("Idea $day")
            assertEquals(AutoBackupResult.Saved, backup.run())
        }
        // October 4 to November 12: the newest seven days, and the last copy from October.
        assertEquals(
            listOf("2026-10-31") + (6..12).map { "2026-11-%02d".format(it) },
            autoFiles().map { Retention.parse(it)!!.toLocalDate().toString() },
        )
        assertTrue("notes.txt" in files())
        assertTrue("numbered-2026-09-01.json" in files())
    }

    @Test fun protectedCopiesOpenOnlyWithThePassphrase() = runBlocking {
        assertEquals(AutoBackupResult.Saved, backup.enable(tree, passphrase = "correct horse"))
        assertTrue(backup.status.value.protected)
        val text = File(directory, files().single()).readText()
        assertFalse(text.contains("Finish the grant draft"))
        val locked = BackupFormat.decode(text) as BackupRead.Locked
        assertEquals(BackupRead.WrongPassphrase, BackupFormat.unlock(locked, "wrong horse"))
        val opened = BackupFormat.unlock(locked, "correct horse") as BackupRead.Ok
        assertEquals("Finish the grant draft", opened.snapshot.commitments.single().title)
    }

    @Test fun aMissingFolderIsShownAtOnceAndNotifiedOnceAfterTwoDays() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        backup.enable(tree, passphrase = null)
        directory.deleteRecursively()
        later()
        assertEquals(AutoBackupResult.FolderUnavailable, backup.run())
        assertEquals(AutoBackupProblem.FolderUnavailable, backup.status.value.problem)
        assertEquals(0, notifications.size())
        later()
        assertEquals(AutoBackupResult.FolderUnavailable, backup.run())
        assertEquals(0, notifications.size())
        later()
        backup.run()
        assertEquals(1, notifications.size())
        assertEquals("Automatic backups have stopped", shadowOf(notifications.allNotifications.single()).contentTitle)
        // Dismissed, and not posted again while the same problem lasts.
        app.getSystemService(NotificationManager::class.java).cancelAll()
        later()
        backup.run()
        assertEquals(0, notifications.size())
        // Once the folder is back, the next run saves and clears the problem.
        directory.mkdirs()
        later()
        assertEquals(AutoBackupResult.Saved, backup.run())
        assertNull(backup.status.value.problem)
        assertEquals(1, files().size)
    }

    @Test fun aLostPassphraseStopsCopiesRatherThanSavingThemUnprotected() = runBlocking {
        backup.enable(tree, passphrase = "correct horse")
        vault.lost = true
        change("Learn to sail")
        later()
        assertEquals(AutoBackupResult.PassphraseUnavailable, backup.run())
        assertEquals(AutoBackupProblem.PassphraseUnavailable, backup.status.value.problem)
        assertEquals(1, files().size)
    }

    @Test fun aFolderThatRefusesCopiesChangesNothing() = runBlocking {
        val readOnly = File(directory, "Read only").apply { mkdirs() }
        FolderDocumentsProvider.readOnly += readOnly
        assertEquals(AutoBackupResult.FolderUnavailable, backup.enable(FolderDocumentsProvider.treeOf("Read only"), passphrase = null))
        assertFalse(backup.status.value.enabled)
        assertEquals(emptyList<Uri>(), persistedFolders())
        assertNull(dailyWork())

        // With a working folder already chosen, a refused new one keeps the old.
        backup.enable(tree, passphrase = null)
        val before = backup.status.value
        assertEquals(AutoBackupResult.FolderUnavailable, backup.enable(FolderDocumentsProvider.treeOf("Read only"), passphrase = null))
        assertEquals(before, backup.status.value)
        assertEquals(listOf(tree), persistedFolders())
    }

    @Test fun changingFolderMovesFutureCopiesAndReleasesTheOldOne() = runBlocking {
        val other = File(directory, "Other").apply { mkdirs() }
        val otherTree = FolderDocumentsProvider.treeOf("Other")
        backup.enable(tree, passphrase = null)
        later()
        assertEquals(AutoBackupResult.Saved, backup.enable(otherTree, passphrase = null))
        assertEquals("Other", backup.status.value.folderName)
        assertEquals(listOf(otherTree), persistedFolders())
        assertEquals(1, other.list()!!.size)
    }

    @Test fun turningOffKeepsSavedCopiesAndStopsTheDailyRun() = runBlocking {
        backup.enable(tree, passphrase = null)
        backup.disable()
        assertEquals(1, files().size)
        assertFalse(backup.status.value.enabled)
        assertEquals(emptyList<Uri>(), persistedFolders())
        assertEquals(WorkInfo.State.CANCELLED, dailyWork()?.state)
        assertEquals(AutoBackupResult.Off, backup.run())
    }

    @Test fun theDailyWorkerSavesACopy() = runBlocking {
        backup.enable(tree, passphrase = null)
        change("Learn to sail")
        later()
        val worker = TestListenableWorkerBuilder<AutoBackupWorker>(app).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(2, files().size)
    }

    private class MovableClock(var now: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)

        override fun instant(): Instant = now
    }

    /** Android Keystore is not available off a phone, so passphrases are sealed in plain sight. */
    private class FakeVault : PassphraseVault {
        var lost = false

        override fun seal(passphrase: String) = "sealed:$passphrase"

        override fun open(sealed: String) = if (lost) null else sealed.removePrefix("sealed:")
    }
}
