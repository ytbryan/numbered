package com.numbered.app

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.numbered.app.data.BackupCipher
import com.numbered.app.data.BackupFormat
import com.numbered.app.data.BackupRead
import com.numbered.app.data.Commitment
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.Profile
import com.numbered.app.data.Snapshot
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.WeekReview
import com.numbered.app.domain.CloseChoice
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.Notice
import com.numbered.app.ui.profile.DataViewModel
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneId.of("Asia/Singapore"))
    private val db = NumberedDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val repository = NumberedRepository(db, clock)
    private val thisWeek = LocalDate.of(2026, 9, 28)
    private val lastWeek = thisWeek.minusWeeks(1)

    @After fun close() = db.close()

    private fun seeded(): Snapshot = runBlocking {
        repository.saveProfile(LocalDate.of(1989, 12, 2), 80, gentle = true, firstDayOfWeek = DayOfWeek.MONDAY)
        repository.addCommitment(lastWeek, "Finish the grant draft")
        repository.addCommitment(lastWeek, "Call Mum, then “plan” the tripé")
        val draft = repository.week(lastWeek).first().first()
        repository.closeWeek(lastWeek, "A steady week.", mapOf(draft.id to CloseChoice.Carry), carryTo = thisWeek)
        repository.addSomeday("Learn to sail")
        repository.addSomeday("Start a podcast")
        repository.letGoSomeday(repository.somedayWaiting().first().first { it.title == "Start a podcast" }.id)
        repository.snapshot()!!
    }

    @Test fun anExportReadsBackExactly() {
        val snapshot = seeded()
        val read = BackupFormat.decode(BackupFormat.encode(snapshot))
        assertEquals(BackupRead.Ok(snapshot), read)
        assertEquals(clock.millis(), snapshot.savedAt)
        assertEquals(CommitmentStatus.Carried, snapshot.commitments.first { it.weekStart == lastWeek && it.title.startsWith("Finish") }.status)
        assertEquals(lastWeek, snapshot.commitments.first { it.weekStart == thisWeek }.carriedFrom)
    }

    @Test fun theFileIsReadableAndStable() {
        val text = BackupFormat.encode(seeded())
        listOf(
            "\"format\": \"numbered\"",
            "\"version\": 1",
            "\"birthDate\": \"1989-12-02\"",
            "\"firstDayOfWeek\": \"monday\"",
            "\"status\": \"carried\"",
            "\"carriedFrom\": \"2026-09-21\"",
            "\"weekNotes\": [",
        ).forEach { assertTrue("Missing $it", text.contains(it)) }
        // Absent values are left out rather than written as null.
        assertTrue(!text.contains("null"))
    }

    @Test fun importReplacesEverythingAtOnce() = runBlocking {
        val snapshot = seeded()
        repository.addCommitment(thisWeek.plusWeeks(1), "Something added after the export")
        repository.saveProfile(LocalDate.of(1990, 1, 1), 90, gentle = false, firstDayOfWeek = DayOfWeek.MONDAY)

        repository.replaceAll(snapshot)

        assertEquals(snapshot, repository.snapshot())
        assertTrue(repository.week(thisWeek.plusWeeks(1)).first().isEmpty())
        // New rows still get fresh ids after an import keeps the old ones.
        repository.addSomeday("Build a bookshelf")
        assertEquals(3, repository.snapshot()!!.someday.map { it.id }.toSet().size)
    }

    @Test fun snapshotIsNullBeforeSetup() = runBlocking {
        assertNull(repository.snapshot())
    }

    @Test fun otherFilesAreRefused() {
        assertEquals(BackupRead.NotABackup, BackupFormat.decode(""))
        assertEquals(BackupRead.NotABackup, BackupFormat.decode("Dear diary"))
        assertEquals(BackupRead.NotABackup, BackupFormat.decode("[1, 2, 3]"))
        assertEquals(BackupRead.NotABackup, BackupFormat.decode("""{"format": "doerlist", "version": 1}"""))
        assertEquals(BackupRead.NotABackup, BackupFormat.decode("""{"format": "numbered"}"""))
    }

    @Test fun newerFilesAreRefusedWithoutGuessing() {
        assertEquals(BackupRead.TooNew, BackupFormat.decode("""{"format": "numbered", "version": 2, "somethingNew": true}"""))
    }

    @Test fun damagedFilesChangeNothing() {
        val good = BackupFormat.encode(seeded())
        listOf(
            good.substring(0, good.length / 2),
            good.replace("\"monday\"", "\"someday\""),
            good.replace("\"carried\"", "\"postponed\""),
            good.replace("\"2026-09-28\"", "\"2026-09-30\""),
            good.replace("\"horizonYears\": 80", "\"horizonYears\": 7"),
            good.replace("\"title\": \"Learn to sail\"", "\"title\": \"  \""),
        ).forEachIndexed { index, damaged -> assertEquals("Case $index", BackupRead.Damaged, BackupFormat.decode(damaged)) }
    }

    @Test fun aWeekCannotArriveWithMoreThanThreeSquares() {
        val profile = Profile(birthDate = LocalDate.of(1989, 12, 2), horizonYears = 80, firstDayOfWeek = DayOfWeek.MONDAY, gentle = false, startedOn = thisWeek)
        fun commitment(id: Long, status: CommitmentStatus) = Commitment(id, thisWeek, "Goal $id", status, createdAt = 1)
        val three = Snapshot(profile, (1L..3L).map { commitment(it, CommitmentStatus.Done) } + commitment(4, CommitmentStatus.LetGo), emptyList(), emptyList())
        assertTrue(BackupFormat.decode(BackupFormat.encode(three)) is BackupRead.Ok)
        val four = three.copy(commitments = three.commitments + commitment(5, CommitmentStatus.Open))
        assertEquals(BackupRead.Damaged, BackupFormat.decode(BackupFormat.encode(four)))
        val duplicateIds = three.copy(someday = listOf(SomedayItem(1, "A", 1), SomedayItem(1, "B", 1)))
        assertEquals(BackupRead.Damaged, BackupFormat.decode(BackupFormat.encode(duplicateIds)))
        val twoNotes = three.copy(reviews = listOf(WeekReview(thisWeek, "a", 1), WeekReview(thisWeek, "b", 2)))
        assertEquals(BackupRead.Damaged, BackupFormat.decode(BackupFormat.encode(twoNotes)))
    }
    @Test fun keyStretchingMatchesThePublishedVectors() {
        // RFC 7914, section 11, and the widely published PBKDF2-HMAC-SHA256 vectors.
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        fun derive(password: String, salt: String, iterations: Int, length: Int) =
            hex(BackupCipher.pbkdf2(password.toByteArray(), salt.toByteArray(), iterations, length))
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", derive("password", "salt", 1, 32))
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", derive("password", "salt", 4096, 32))
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            derive("passwd", "salt", 1, 64),
        )
    }

    @Test fun aProtectedCopyHidesEverythingAndOpensWithItsPassphrase() {
        val snapshot = seeded()
        val text = BackupFormat.encode(snapshot, "correct horse battery")
        listOf("Finish the grant draft", "A steady week.", "Learn to sail", "1989-12-02").forEach {
            assertTrue("Leaked $it", !text.contains(it))
        }
        listOf("\"format\": \"numbered\"", "\"kdf\": \"pbkdf2-hmac-sha256\"", "\"iterations\": 600000", "\"cipher\": \"aes-256-gcm\"")
            .forEach { assertTrue("Missing $it", text.contains(it)) }

        val locked = BackupFormat.decode(text)
        assertTrue(locked is BackupRead.Locked)
        locked as BackupRead.Locked
        assertEquals(BackupRead.WrongPassphrase, BackupFormat.unlock(locked, "correct horse battery "))
        assertEquals(BackupRead.WrongPassphrase, BackupFormat.unlock(locked, ""))
        assertEquals(BackupRead.Ok(snapshot), BackupFormat.unlock(locked, "correct horse battery"))
        // Every export is salted afresh, so the same data and passphrase never give the same file.
        assertTrue(text != BackupFormat.encode(snapshot, "correct horse battery"))
    }

    @Test fun thePassphraseMeansTheSameOnEveryKeyboard() {
        val snapshot = seeded()
        // One keyboard types é as one character, another as e plus a combining accent.
        val locked = BackupFormat.decode(BackupFormat.encode(snapshot, "caf\u00e9 au lait")) as BackupRead.Locked
        assertEquals(BackupRead.Ok(snapshot), BackupFormat.unlock(locked, "cafe\u0301 au lait"))
    }

    @Test fun alteredProtectedFilesNeverOpen() {
        val text = BackupFormat.encode(seeded(), "correct horse battery")
        fun unlock(changed: String) = (BackupFormat.decode(changed) as BackupRead.Locked).let { BackupFormat.unlock(it, "correct horse battery") }
        val data = Regex("\"data\": \"([^\"]+)\"").find(text)!!.groupValues[1]
        val flipped = data.replaceRange(10, 11, if (data[10] == 'A') "B" else "A")
        assertEquals(BackupRead.WrongPassphrase, unlock(text.replace(data, flipped)))
        assertEquals(BackupRead.WrongPassphrase, unlock(text.replace("\"iterations\": 600000", "\"iterations\": 599999")))
        assertEquals(BackupRead.Damaged, unlock(text.replace("\"iterations\": 600000", "\"iterations\": 2000000000")))
        assertEquals(BackupRead.Damaged, unlock(text.replace("aes-256-gcm", "rot13")))
        assertEquals(BackupRead.Damaged, unlock(text.replace("\"salt\": \"", "\"salt\": \"%%")))
    }

    @Test fun anInterruptedExportWritesNothing() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = DataViewModel(AppContainer(app, db, clock), app.contentResolver)
        val file = File.createTempFile("numbered", ".json").apply { deleteOnExit() }
        runBlocking { repository.saveProfile(LocalDate.of(1989, 12, 2), 80, gentle = false, firstDayOfWeek = DayOfWeek.MONDAY) }

        // The save dialog returns, but the passphrase choice was lost with the app's process.
        viewModel.export(Uri.fromFile(file))

        // The write finishes on background threads, then reports back on the main looper.
        val notice = runBlocking {
            withTimeout(5_000) {
                var received: Notice? = null
                while (received == null) {
                    shadowOf(Looper.getMainLooper()).idle()
                    received = withTimeoutOrNull(50) { viewModel.notices.first() }
                }
                received
            }
        }
        assertEquals(R.string.notice_export_interrupted, notice.message)
        assertEquals(0L, file.length())
    }
}
