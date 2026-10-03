package com.numbered.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.numbered.app.data.Chapter
import com.numbered.app.data.Commitment
import com.numbered.app.data.NumberedDatabase
import com.numbered.app.data.Profile
import com.numbered.app.data.Snapshot
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.WeekReview
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.Notice
import com.numbered.app.ui.yearreview.YearReviewViewModel
import com.numbered.app.ui.yearreview.document
import com.numbered.app.ui.yearreview.reviewShare
import com.numbered.app.ui.yearreview.reviewYears
import com.numbered.app.ui.yearreview.yearReview
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YearReviewTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val week = LocalDate.of(2026, 9, 28)
    private val today = week.plusDays(3)
    private val profile = Profile(
        birthDate = LocalDate.of(1989, 12, 2), horizonYears = 80,
        firstDayOfWeek = DayOfWeek.MONDAY, gentle = false, startedOn = LocalDate.of(2025, 1, 6),
    )
    private fun snapshot(
        commitments: List<Commitment> = emptyList(), reviews: List<WeekReview> = emptyList(),
        chapters: List<Chapter> = emptyList(),
    ) = Snapshot(profile, commitments, listOf(SomedayItem(1, "Private Someday idea", 1)), reviews, chapters)

    @Test fun onlyCompletedCommitmentsAndPastWeeksAppearAndBlankNotesStillCountAsClosed() {
        val entries = CommitmentStatus.entries.mapIndexed { index, status ->
            Commitment(index.toLong() + 1, week, "Entry $status", status, 1)
        } + listOf(
            Commitment(9, week.plusWeeks(1), "Future done", CommitmentStatus.Done, 1),
            Commitment(10, week.minusWeeks(1), "Earlier done", CommitmentStatus.Done, 1),
        )
        val review = yearReview(snapshot(entries, listOf(
            WeekReview(week, "  ", 1), WeekReview(week.minusWeeks(1), "Meaningful note", 1),
            WeekReview(week.plusWeeks(1), "Future note", 1),
        )), 2026, today)
        assertEquals(listOf("Earlier done", "Entry Done"), review.completed.map { it.title })
        assertEquals(2, review.weeksClosed)
        assertEquals(listOf("Meaningful note"), review.notes.map { it.note })
        assertTrue(review.inProgress)
    }

    @Test fun newYearWeeksUseTheSameMidpointRuleForMondayAndSundayStarts() {
        for (day in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            val start = if (day == DayOfWeek.MONDAY) LocalDate.of(2025, 12, 29) else LocalDate.of(2025, 12, 28)
            val data = snapshot(listOf(Commitment(1, start, "New year week", CommitmentStatus.Done, 1)))
                .copy(profile = profile.copy(firstDayOfWeek = day))
            val expected = if (day == DayOfWeek.MONDAY) 2026 else 2025
            assertEquals(listOf("New year week"), yearReview(data, expected, LocalDate.of(2026, 1, 1)).completed.map { it.title })
            assertTrue(yearReview(data, expected, LocalDate.of(2026, 1, 1)).inProgress)
            assertTrue(yearReview(data, if (expected == 2026) 2025 else 2026, LocalDate.of(2026, 1, 1)).completed.isEmpty())
            assertEquals(listOf(expected), reviewYears(data, LocalDate.of(2026, 1, 1)))
        }
    }

    @Test fun chaptersAreClippedToTheSelectedYearAndTodayIncludingOngoingSpans() {
        val review = yearReview(snapshot(chapters = listOf(
            Chapter(1, "Ongoing chapter", LocalDate.of(2025, 6, 2), null, 1),
            Chapter(2, "Older chapter", LocalDate.of(2025, 1, 6), LocalDate.of(2025, 5, 26), 1),
            Chapter(3, "Future chapter", week.plusWeeks(1), null, 1),
        )), 2026, today)
        assertEquals(listOf("Ongoing chapter"), review.chapters.map { it.chapter.title })
        assertEquals(LocalDate.of(2025, 12, 29), review.chapters.single().start)
        assertEquals(week, review.chapters.single().end)
        assertTrue(review.document(app.resources, Locale.US).contains("Oct 1, 2026"))
        assertFalse(review.document(app.resources, Locale.US).contains("Oct 4, 2026"))
    }

    @Test fun yearChoicesIncludeRecordedAndIntermediateChapterYearsButExcludeFutureRecords() {
        val data = snapshot(
            commitments = listOf(Commitment(1, LocalDate.of(2024, 1, 8), "Old open", createdAt = 1),
                Commitment(2, LocalDate.of(2027, 1, 4), "Future", createdAt = 1)),
            chapters = listOf(Chapter(1, "Long chapter", LocalDate.of(2022, 1, 3), null, 1)),
        )
        assertEquals(listOf(2026, 2025, 2024, 2023, 2022), reviewYears(data, today))
        assertEquals(listOf(2026), reviewYears(snapshot(), today))
        assertFalse(yearReview(snapshot(), 2026, today).hasContent)
        assertTrue(yearReview(snapshot(reviews = listOf(WeekReview(week, "", 1))), 2026, today).hasContent)
    }

    @Test fun readableDocumentPreservesUnicodeAndMultilineNotesWithoutPrivateOrOtherYearData() {
        val data = snapshot(
            commitments = listOf(Commitment(1, week, "Café with 妈妈", CommitmentStatus.Done, 1),
                Commitment(2, LocalDate.of(2025, 1, 6), "Other year", CommitmentStatus.Done, 1),
                Commitment(3, week, "Unfinished", createdAt = 1)),
            reviews = listOf(WeekReview(week, "First line\nSecond line ✓", 1)),
        )
        val text = yearReview(data, 2026, today).document(app.resources, Locale.US)
        assertTrue(text.startsWith("Numbered - 2026 in review\nYear so far"))
        assertTrue(text.contains("- Café with 妈妈"))
        assertTrue(text.contains("First line\nSecond line ✓"))
        for (private in listOf("Other year", "Private Someday", "Unfinished", "1989")) assertFalse(text.contains(private))
        assertTrue(text.endsWith("\n"))
        assertFalse(text.endsWith("\n\n"))
    }

    @Suppress("DEPRECATION")
    @Test fun sharingGrantsOnlyTheSelectedReviewFileWithAReadableName() {
        val review = yearReview(snapshot(listOf(Commitment(1, week, "Café", CommitmentStatus.Done, 1))), 2026, today)
        val chooser = reviewShare(app, review)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(review.document(app.resources, app.resources.configuration.locales[0]),
            app.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
            assertTrue(it.moveToFirst())
            assertEquals("numbered-2026-review.txt", it.getString(0))
        }
        assertEquals(ComponentName(app, CaptureActivity::class.java), chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!.single())
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(app, "${app.packageName}.reviews", File(app.filesDir, "private.json"))
        }
    }

    @Test fun missingOrCancelledSaveCannotOverwriteADestination() {
        val db = NumberedDatabase.inMemory(app)
        val viewModel = YearReviewViewModel(AppContainer(app, db, java.time.Clock.systemUTC()), app)
        val file = File.createTempFile("year-review", ".txt", app.cacheDir).apply { writeText("Keep me") }
        try {
            viewModel.cancelSave()
            viewModel.save(Uri.fromFile(file))
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
            assertEquals("Keep me", file.readText())
            assertFalse(viewModel.busy.value)
        } finally { file.delete(); db.close() }
    }
}
