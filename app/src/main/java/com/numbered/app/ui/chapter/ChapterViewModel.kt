package com.numbered.app.ui.chapter

import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.PlanResult
import com.numbered.app.data.calendar
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.ui.NoticeViewModel
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** How a chapter ends: in the week it starts, in a later week, or not yet. */
enum class ChapterEnd { SameWeek, Later, Ongoing }

/** What the form starts from: an existing chapter, or a new one beginning in a chosen week. */
data class ChapterForm(
    val existing: Boolean,
    val title: String,
    val start: LocalDate,
    val end: ChapterEnd,
    val endWeek: LocalDate,
    val calendar: LifeCalendar,
    val today: LocalDate,
)

class ChapterViewModel(container: AppContainer, private val id: Long?, private val startWeek: LocalDate) : NoticeViewModel() {
    private val repository = container.repository
    private val doneChannel = Channel<Unit>(Channel.CONFLATED)

    /** Emits once the chapter has been saved or deleted. */
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    val form: StateFlow<ChapterForm?> = combine(
        repository.profile().filterNotNull(),
        container.today.value,
        flow { emit(id?.let { repository.chapter(it) }) },
    ) { profile, today, chapter ->
        val calendar = profile.calendar()
        ChapterForm(
            existing = chapter != null,
            title = chapter?.title.orEmpty(),
            start = chapter?.startWeek ?: startWeek,
            end = when {
                chapter == null -> ChapterEnd.SameWeek
                chapter.endWeek == null -> ChapterEnd.Ongoing
                chapter.endWeek == chapter.startWeek -> ChapterEnd.SameWeek
                else -> ChapterEnd.Later
            },
            endWeek = chapter?.endWeek ?: chapter?.startWeek ?: startWeek,
            calendar = calendar,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(title: String, start: LocalDate, end: ChapterEnd, endWeek: LocalDate) = launchWrite {
        val last = when (end) {
            ChapterEnd.SameWeek -> start
            ChapterEnd.Later -> endWeek
            ChapterEnd.Ongoing -> null
        }
        when (val result = repository.saveChapter(id, title, start, last)) {
            PlanResult.Ok -> doneChannel.trySend(Unit)
            else -> report(result)
        }
    }

    fun delete() = launchWrite {
        id?.let { repository.deleteChapter(it) }
        doneChannel.trySend(Unit)
    }

    companion object {
        /** Route value for a chapter that does not exist yet. */
        const val NEW = -1L
    }
}
