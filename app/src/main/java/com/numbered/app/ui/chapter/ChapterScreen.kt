package com.numbered.app.ui.chapter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.shortDate
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterScreen(id: Long?, startWeek: LocalDate, onDone: () -> Unit) {
    val viewModel = containerViewModel { ChapterViewModel(it, id, startWeek) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices)
    val doneCallback by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.done.collect { doneCallback() } }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (id == null) R.string.chapter_new else R.string.chapter_edit)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (form?.existing == true) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.a11y_delete_chapter))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val initial = form ?: return@Scaffold
        // Typed text and choices stay in the screen; the view model only saves them.
        var title by rememberSaveable { mutableStateOf(initial.title) }
        var start by rememberSaveable { mutableLongStateOf(initial.start.toEpochDay()) }
        var end by rememberSaveable { mutableStateOf(initial.end) }
        var endWeek by rememberSaveable { mutableLongStateOf(initial.endWeek.toEpochDay()) }
        var picking by rememberSaveable { mutableStateOf<String?>(null) }
        val startDate = LocalDate.ofEpochDay(start)
        val endDate = maxOf(LocalDate.ofEpochDay(endWeek), startDate)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.chapter_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.chapter_name)) },
                placeholder = { Text(stringResource(R.string.chapter_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            WeekField(stringResource(R.string.chapter_starts), startDate, initial.today) { picking = START }
            // Radio rows rather than segmented buttons, so the choices stay on one line at large text sizes.
            Column(Modifier.selectableGroup()) {
                Text(stringResource(R.string.chapter_ends), style = MaterialTheme.typography.titleSmall)
                ChapterEnd.entries.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = end == choice, role = Role.RadioButton, onClick = { end = choice }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = end == choice, onClick = null)
                        Text(stringResource(choice.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (end == ChapterEnd.Later) {
                WeekField(stringResource(R.string.chapter_last_week), endDate, initial.today) { picking = END }
            }
            Button(
                onClick = { viewModel.save(title, startDate, end, endDate) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }

        picking?.let { which ->
            WeekPicker(
                initial = if (which == START) startDate else endDate,
                earliest = if (which == START) initial.calendar.firstWeekStart else startDate,
                today = initial.today,
                onPicked = { date ->
                    val week = initial.calendar.weekStartOf(date)
                    if (which == START) start = week.toEpochDay() else endWeek = week.toEpochDay()
                },
                onDismiss = { picking = null },
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.chapter_delete_title)) },
            text = { Text(stringResource(R.string.chapter_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private const val START = "start"
private const val END = "end"

private val ChapterEnd.label: Int
    get() = when (this) {
        ChapterEnd.SameWeek -> R.string.chapter_end_same
        ChapterEnd.Later -> R.string.chapter_end_later
        ChapterEnd.Ongoing -> R.string.chapter_end_ongoing
    }

@Composable
private fun WeekField(label: String, week: LocalDate, today: LocalDate, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.chapter_week_of, shortDate(week, today)), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Picks any day; the chapter takes the week that day falls in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPicker(
    initial: LocalDate,
    earliest: LocalDate,
    today: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val latest = today.plusYears(LATER_YEARS)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.utcMillis(),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, 86_400_000L))
                return !date.isBefore(earliest) && !date.isAfter(latest)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** How far ahead a chapter may be placed, for plans already made. */
private const val LATER_YEARS = 10L

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
