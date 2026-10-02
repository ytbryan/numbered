package com.numbered.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.numbered.app.AppContainer
import com.numbered.app.data.Chapter
import com.numbered.app.data.Commitment
import com.numbered.app.data.SomedayItem
import com.numbered.app.data.WeekReview
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

internal data class SearchResults(
    val query: String,
    val commitments: List<Commitment> = emptyList(),
    val notes: List<WeekReview> = emptyList(),
    val ideas: List<SomedayItem> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
) {
    val empty: Boolean get() = commitments.isEmpty() && notes.isEmpty() && ideas.isEmpty() && chapters.isEmpty()
}

/** Literal, case-insensitive search. Blank queries do not expose a wall of history. */
internal fun searchEverything(
    query: String,
    commitments: List<Commitment>,
    notes: List<WeekReview>,
    ideas: List<SomedayItem>,
    chapters: List<Chapter>,
): SearchResults {
    val needle = query.trim()
    if (needle.isEmpty()) return SearchResults(needle)
    return SearchResults(
        query = needle,
        commitments = commitments.filter { it.title.contains(needle, ignoreCase = true) }
            .sortedWith(compareByDescending<Commitment> { it.weekStart }.thenBy { it.id }),
        notes = notes.filter { it.note.contains(needle, ignoreCase = true) }.sortedByDescending { it.weekStart },
        ideas = ideas.filter { it.title.contains(needle, ignoreCase = true) }
            .sortedWith(compareByDescending<SomedayItem> { it.createdAt }.thenBy { it.id }),
        chapters = chapters.filter { it.title.contains(needle, ignoreCase = true) }
            .sortedWith(compareByDescending<Chapter> { it.startWeek }.thenBy { it.id }),
    )
}

internal data class SearchState(val today: LocalDate, val results: SearchResults)

@OptIn(FlowPreview::class)
internal class SearchViewModel(container: AppContainer) : ViewModel() {
    private val query = MutableStateFlow("")
    private val repository = container.repository
    private val data = combine(repository.commitments(), repository.reviews(), repository.somedayAll(), repository.chapters()) {
            commitments, notes, ideas, chapters ->
        SearchResults("", commitments, notes.values.toList(), ideas, chapters)
    }
    val state = combine(data, query.debounce(150), container.today.value) { data, query, today ->
        SearchState(today, searchEverything(query, data.commitments, data.notes, data.ideas, data.chapters))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun search(text: String) { query.value = text.trim() }
}
