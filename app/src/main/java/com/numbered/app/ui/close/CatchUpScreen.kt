package com.numbered.app.ui.close

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.domain.CloseChoice
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.SectionLabel
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.weekRange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatchUpScreen(onBack: () -> Unit, onClosed: (Int) -> Unit) {
    val viewModel = containerViewModel { CatchUpViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices)
    val closedCallback by rememberUpdatedState(onClosed)
    LaunchedEffect(viewModel) { viewModel.closed.collect { closedCallback(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catch_up_heading)) },
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
        var showErrors by rememberSaveable { mutableStateOf(false) }
        // Typed text stays in the screen, so the field never waits on a round trip through the view model.
        val notes = rememberSaveable(saver = NotesSaver) { mutableStateMapOf<LocalDate, String>() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                pluralString(R.plurals.catch_up_title, current.weeks.size, current.weeks.size),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(if (current.open.isEmpty()) R.string.catch_up_intro_notes else R.string.catch_up_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (current.undecided > 0) {
                SectionLabel(stringResource(R.string.catch_up_all), Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { viewModel.chooseRemaining(CloseChoice.Someday) },
                        label = { Text(stringResource(R.string.choice_someday)) },
                    )
                    AssistChip(
                        onClick = { viewModel.chooseRemaining(CloseChoice.LetGo) },
                        label = { Text(stringResource(R.string.choice_let_go)) },
                    )
                }
            }

            current.weeks.forEach { week ->
                SectionLabel(
                    stringResource(R.string.catch_up_week, formatCount(week.weekNumber), weekRange(week.weekStart, current.today)),
                    Modifier.padding(top = 12.dp),
                )
                week.open.forEach { commitment ->
                    UnfinishedCard(
                        commitment = commitment,
                        selected = current.choices[commitment.id],
                        canCarry = current.canCarry(commitment),
                        closingCurrent = false,
                        showError = showErrors && commitment.id !in current.choices,
                        onChoose = { viewModel.choose(commitment, it) },
                    )
                }
                OutlinedTextField(
                    value = notes[week.weekStart] ?: viewModel.draft(week.weekStart).orEmpty(),
                    onValueChange = {
                        notes[week.weekStart] = it
                        viewModel.saveDraft(week.weekStart, it)
                    },
                    placeholder = { Text(stringResource(R.string.catch_up_note_placeholder)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            if (showErrors && current.undecided > 0) {
                Text(
                    text = pluralString(R.plurals.close_undecided, current.undecided, current.undecided),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    showErrors = true
                    if (current.undecided == 0) viewModel.close(notes.toMap())
                },
                enabled = current.weeks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pluralString(R.plurals.action_close_weeks, current.weeks.size, current.weeks.size))
            }
        }
    }
}

private val NotesSaver = listSaver<SnapshotStateMap<LocalDate, String>, String>(
    save = { notes -> notes.flatMap { (week, note) -> listOf(week.toString(), note) } },
    restore = { saved -> mutableStateMapOf<LocalDate, String>().apply { saved.chunked(2).forEach { (week, note) -> put(LocalDate.parse(week), note) } } },
)
