package com.numbered.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.commitmentStatus
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.weekRange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenWeek: (LocalDate) -> Unit,
    onOpenSomeday: (String) -> Unit,
    onOpenChapter: (Long) -> Unit,
) {
    val viewModel = containerViewModel { SearchViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var commitmentLimit by rememberSaveable(query) { mutableIntStateOf(30) }
    var noteLimit by rememberSaveable(query) { mutableIntStateOf(30) }
    var ideaLimit by rememberSaveable(query) { mutableIntStateOf(30) }
    var chapterLimit by rememberSaveable(query) { mutableIntStateOf(30) }
    var otherDoneLimit by rememberSaveable(query) { mutableIntStateOf(30) }
    LaunchedEffect(query) { viewModel.search(query) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.search_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_everything)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.a11y_clear_search))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 12.dp),
            )
            val current = state
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    query.isBlank() -> item { Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    current == null || current.results.query != query.trim() -> Unit
                    current.results.empty -> item { Text(stringResource(R.string.search_no_matches)) }
                    else -> {
                        val matches = current.results
                        results("commitments", matches.commitments, commitmentLimit, { it.id }, { commitmentLimit += 30 }, R.string.search_commitments) { entry ->
                            SearchRow(
                                entry.title,
                                stringResource(R.string.range_and_status, weekRange(entry.weekStart, current.today), commitmentStatus(entry.status)),
                            ) { onOpenWeek(entry.weekStart) }
                        }
                        results("other-done", matches.otherThingsDone, otherDoneLimit, { it.id }, { otherDoneLimit += 30 }, R.string.search_other_things_done) { entry ->
                            SearchRow(entry.title, weekRange(entry.weekStart, current.today)) { onOpenWeek(entry.weekStart) }
                        }
                        results("notes", matches.notes, noteLimit, { it.weekStart.toEpochDay() }, { noteLimit += 30 }, R.string.search_notes) { note ->
                            SearchRow(note.note, weekRange(note.weekStart, current.today)) { onOpenWeek(note.weekStart) }
                        }
                        results("ideas", matches.ideas, ideaLimit, { it.id }, { ideaLimit += 30 }, R.string.search_ideas) { idea ->
                            SearchRow(idea.title, stringResource(if (idea.letGoAt == null) R.string.tab_someday else R.string.status_let_go)) {
                                onOpenSomeday(idea.title)
                            }
                        }
                        results("chapters", matches.chapters, chapterLimit, { it.id }, { chapterLimit += 30 }, R.string.search_chapters) { chapter ->
                            SearchRow(chapter.title, weekRange(chapter.startWeek, current.today)) { onOpenChapter(chapter.id) }
                        }
                    }
                }
            }
        }
    }
}

/** Initial pages keep other result groups within reach, even with decades of matching notes. */
private fun <T> LazyListScope.results(
    key: String, values: List<T>, limit: Int, id: (T) -> Long, onMore: () -> Unit, label: Int,
    row: @Composable (T) -> Unit,
) {
    if (values.isEmpty()) return
    item(key = "$key-heading") {
        Text(
            stringResource(label, values.size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    items(values.take(limit), key = { "$key-${id(it)}" }) { row(it) }
    if (values.size > limit) item(key = "$key-more") {
        TextButton(onClick = onMore) { Text(stringResource(R.string.search_more)) }
    }
}

@Composable
private fun SearchRow(title: String, detail: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
