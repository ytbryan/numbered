package com.numbered.app.ui.yearreview

import android.content.ActivityNotFoundException
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.ui.LocalSnackbar
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.shortDate
import com.numbered.app.ui.weekRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearReviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel = containerViewModel { YearReviewViewModel(it, context.applicationContext as Application) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var chosen by rememberSaveable { mutableStateOf<Int?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(chosen) { viewModel.selectYear(chosen) }
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbar provides snackbar) { NoticeEffect(viewModel.notices) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) viewModel.cancelSave() else viewModel.save(uri)
    }
    LaunchedEffect(viewModel) {
        viewModel.shares.collect { intent ->
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                viewModel.shareUnavailable()
            } catch (_: SecurityException) {
                viewModel.shareUnavailable()
            }
        }
    }
    val current = state
    val ready = current != null && (chosen == null || chosen == current.review.year) && current.review.hasContent && !busy
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = ScreenPadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.prepareSave()?.let { save.launch(it) } },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.review_save)) }
                Button(onClick = viewModel::share, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_share))
                }
            }
        },
    ) { padding ->
        if (current == null) return@Scaffold
        val review = current.review
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "year") {
                val description = stringResource(R.string.review_choose_year)
                TextButton(onClick = { choosing = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = description }) {
                    Text((chosen ?: review.year).toString(), style = MaterialTheme.typography.headlineLarge)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }
                if (review.inProgress) Text(
                    stringResource(R.string.review_so_far, shortDate(review.today, review.today)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(review.summary(resources), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            if (!review.hasContent) item(key = "empty") {
                Text(stringResource(R.string.review_empty), Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (review.completed.isNotEmpty()) {
                item(key = "completed") { ReviewHeading(stringResource(R.string.review_completed)) }
                items(review.completed.groupBy { it.weekStart }.entries.toList(), key = { "completed-${it.key}" }) { (week, entries) ->
                    ReviewRow(entries.map { it.title }, weekRange(week, review.today))
                }
            }
            if (review.notes.isNotEmpty()) {
                item(key = "notes") { ReviewHeading(stringResource(R.string.review_notes)) }
                items(review.notes, key = { "note-${it.weekStart}" }) { note ->
                    ReviewRow(listOf(note.note), weekRange(note.weekStart, review.today))
                }
            }
            if (review.chapters.isNotEmpty()) {
                item(key = "chapters") { ReviewHeading(stringResource(R.string.chapters_title)) }
                items(review.chapters, key = { "chapter-${it.chapter.id}" }) { chapter ->
                    ReviewRow(
                        listOf(chapter.chapter.title),
                        stringResource(R.string.chapter_span, shortDate(chapter.start, review.today), shortDate(minOf(chapter.end.plusDays(6), review.today), review.today)),
                    )
                }
            }
        }
        if (choosing) AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.review_choose_year)) },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(current.years, key = { it }) { year ->
                        TextButton(onClick = { chosen = year; choosing = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(year.toString())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ReviewHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
}

@Composable
private fun ReviewRow(texts: List<String>, dates: String) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dates, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            texts.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
