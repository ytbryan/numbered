package com.numbered.app.ui.lines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.weekRange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinesScreen(onBack: () -> Unit, onOpenWeek: (LocalDate) -> Unit) {
    val viewModel = containerViewModel { LinesViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The query stays in the screen, so typing never waits on the view model.
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lines_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val current = state ?: return@Scaffold
        val years = remember(current, query) { current.years.matching(query) }
        val searching = query.isNotBlank()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (current.years.any { it.lines.isNotEmpty() }) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.lines_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.a11y_clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            when {
                current.years.all { it.lines.isEmpty() } && !searching -> item(key = "empty") {
                    Message(stringResource(R.string.lines_empty))
                }
                years.isEmpty() -> item(key = "no-match") {
                    Message(stringResource(R.string.lines_no_match, query.trim()))
                }
            }
            years.forEach { year ->
                item(key = "year-${year.year}") { YearHeader(year, searching) }
                items(year.lines, key = { it.weekStart.toEpochDay() }) { line ->
                    LineRow(line, query, current.today, onClick = { onOpenWeek(line.weekStart) })
                }
            }
        }
    }
}

@Composable
private fun YearHeader(year: LinesYear, searching: Boolean) {
    Column(Modifier.padding(top = 16.dp, bottom = 2.dp)) {
        Text(
            year.year.toString(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            if (searching) {
                pluralString(R.plurals.lines_count, year.lines.size, formatCount(year.lines.size))
            } else {
                stringResource(
                    R.string.lines_year_summary,
                    pluralString(R.plurals.lines_weeks_closed, year.weeksClosed, formatCount(year.weeksClosed)),
                    pluralString(R.plurals.lines_things_done, year.thingsDone, formatCount(year.thingsDone)),
                    pluralString(R.plurals.lines_count, year.lines.size, formatCount(year.lines.size)),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LineRow(line: Line, query: String, today: LocalDate, onClick: () -> Unit) {
    val highlight = SpanStyle(fontWeight = FontWeight.SemiBold, background = MaterialTheme.colorScheme.primaryContainer)
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.catch_up_week, formatCount(line.weekNumber), weekRange(line.weekStart, today)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(highlighted(line.note, query.trim(), highlight), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** [text] with every case-insensitive occurrence of [query] in [style]. */
private fun highlighted(text: String, query: String, style: SpanStyle): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var from = 0
        while (from < text.length) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            append(text.substring(from, at))
            withStyle(style) { append(text.substring(at, at + query.length)) }
            from = at + query.length
        }
        append(text.substring(from))
    }
}
