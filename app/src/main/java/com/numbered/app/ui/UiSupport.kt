package com.numbered.app.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.numbered.app.AppContainer
import com.numbered.app.R
import com.numbered.app.appContainer
import com.numbered.app.data.PlanResult
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Formatter
import java.util.Locale
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

/** A short message for the snackbar, optionally with an Undo action. */
class Notice(
    @param:StringRes val message: Int,
    val args: List<Any> = emptyList(),
    val undo: (() -> Unit)? = null,
)

/** Shared by every screen's ViewModel: runs writes and reports outcomes as notices. */
abstract class NoticeViewModel : ViewModel() {
    private val noticeChannel = Channel<Notice>(Channel.BUFFERED)
    val notices: Flow<Notice> = noticeChannel.receiveAsFlow()

    protected fun notify(notice: Notice) {
        noticeChannel.trySend(notice)
    }

    protected fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    /** Reports the plan results people need to hear about. Success is visible on screen already. */
    protected fun report(result: PlanResult) {
        when (result) {
            PlanResult.WeekFull -> notify(Notice(R.string.notice_week_full))
            PlanResult.Missing -> notify(Notice(R.string.notice_changed_elsewhere))
            PlanResult.Ok, PlanResult.Blank -> Unit
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun NoticeEffect(notices: Flow<Notice>) {
    val snackbar = LocalSnackbar.current
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(notices) {
        notices.collect { notice ->
            val text = resources.getString(notice.message, *notice.args.toTypedArray())
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = if (notice.undo != null) undoLabel else null,
                withDismissAction = notice.undo == null,
                duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) notice.undo?.invoke()
        }
    }
}

@Composable
@ReadOnlyComposable
fun locale(): Locale = LocalConfiguration.current.locales[0]

@Composable
@ReadOnlyComposable
fun formatCount(value: Int): String = NumberFormat.getIntegerInstance(locale()).format(value)

@Composable
@ReadOnlyComposable
fun pluralString(@PluralsRes id: Int, count: Int, vararg args: Any): String =
    LocalResources.current.getQuantityString(id, count, *args)

private fun pattern(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** "Sep 28", adding the year only when it is not the current one. */
@Composable
@ReadOnlyComposable
fun shortDate(date: LocalDate, today: LocalDate): String {
    val locale = locale()
    return date.format(pattern(locale, if (date.year == today.year) "MMMd" else "yMMMd"))
}

/**
 * "Sep 28 – Oct 4" or "Sep 7 – 13", with the year only where it is needed. The platform formatter
 * collapses shared months and years correctly for every locale.
 */
@Composable
@ReadOnlyComposable
fun weekRange(start: LocalDate, today: LocalDate): String {
    val end = start.plusDays(6)
    val showYear = start.year != today.year || end.year != today.year
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
        if (showYear) DateUtils.FORMAT_SHOW_YEAR else DateUtils.FORMAT_NO_YEAR
    // An end at midnight counts as the day before, so the day after the week ends the range.
    return DateUtils.formatDateRange(
        LocalContext.current,
        Formatter(StringBuilder(), locale()),
        start.utcMillis(),
        end.plusDays(1).utcMillis(),
        flags,
        "UTC",
    ).toString()
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
@ReadOnlyComposable
fun weekdayName(date: LocalDate): String = date.format(pattern(locale(), "EEEE"))

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
