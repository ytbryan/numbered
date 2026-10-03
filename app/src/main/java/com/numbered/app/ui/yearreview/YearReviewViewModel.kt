package com.numbered.app.ui.yearreview

import android.content.ClipData
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.calendar
import com.numbered.app.CaptureActivity
import com.numbered.app.R
import com.numbered.app.ui.Notice
import com.numbered.app.ui.NoticeViewModel
import com.numbered.app.ui.lines.yearOf
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class YearReviewState(val years: List<Int>, val review: YearReview)

internal class YearReviewViewModel(private val container: AppContainer, private val application: Application) : NoticeViewModel() {
    private val year = MutableStateFlow<Int?>(null)
    private val working = MutableStateFlow(false)
    val busy = working.asStateFlow()
    private val sharesChannel = Channel<Intent>(Channel.BUFFERED)
    val shares = sharesChannel.receiveAsFlow()
    private var pending: YearReview? = null
    private val snapshots = container.database.invalidationTracker.createFlow("profile", "commitments", "week_reviews", "chapters")
        .map { container.repository.snapshot() }.filterNotNull()
    val state = combine(snapshots, container.today.value, year) { snapshot, today, selected ->
        val chosen = selected ?: yearOf(snapshot.profile.calendar().weekStartOf(today))
        YearReviewState(reviewYears(snapshot, today), yearReview(snapshot, chosen, today))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectYear(value: Int?) { year.value = value }

    fun prepareSave(): String? {
        val review = state.value?.review?.takeIf { it.hasContent && !busy.value } ?: return null
        pending = review
        return review.fileName
    }

    fun cancelSave() { pending = null }

    fun save(uri: Uri) {
        val review = pending
        pending = null
        if (review == null) return notify(Notice(R.string.notice_export_interrupted))
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val document = review.document(application.resources, application.resources.configuration.locales[0])
                    application.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(document.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("No output stream")
                }
                notify(Notice(R.string.review_saved))
            } catch (_: IOException) {
                notify(Notice(R.string.notice_export_failed))
            } catch (_: SecurityException) {
                notify(Notice(R.string.notice_export_failed))
            } finally {
                working.value = false
            }
        }
    }

    fun share() {
        val review = state.value?.review?.takeIf { it.hasContent && !busy.value } ?: return
        working.value = true
        viewModelScope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { reviewShare(application, review) }
                sharesChannel.send(intent)
            } catch (_: IOException) {
                shareUnavailable()
            } catch (_: SecurityException) {
                shareUnavailable()
            } finally {
                working.value = false
            }
        }
    }

    fun shareUnavailable() { notify(Notice(R.string.review_share_failed)) }
}

/** A private cache file with a temporary read grant, exposing only the review directory. */
internal fun reviewShare(context: Context, review: YearReview): Intent {
    val directory = File(context.cacheDir, "year-reviews/${UUID.randomUUID()}")
    if (!directory.mkdirs()) throw IOException("Cannot create review directory")
    val file = File(directory, review.fileName)
    file.writeText(review.document(context.resources, context.resources.configuration.locales[0]), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.reviews", file)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.review_document_title, review.year))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri("Year in review", uri) }
    return Intent.createChooser(send, context.getString(R.string.review_share))
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, CaptureActivity::class.java)))
}
