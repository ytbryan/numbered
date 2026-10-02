package com.numbered.app.ui.close

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.data.Commitment
import com.numbered.app.domain.CloseChoice
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.SectionLabel
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.theme.card
import com.numbered.app.ui.weekRange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseWeekScreen(weekStart: LocalDate, onBack: () -> Unit, onClosed: (Int) -> Unit) {
    val viewModel = containerViewModel { CloseWeekViewModel(it, weekStart) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices)
    val closedCallback by rememberUpdatedState(onClosed)
    LaunchedEffect(viewModel) { viewModel.closed.collect { closedCallback(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state?.let { stringResource(R.string.close_title, formatCount(it.weekNumber)) }.orEmpty())
                },
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
        var note by rememberSaveable(current.weekStart) { mutableStateOf(viewModel.draft() ?: current.existingNote.orEmpty()) }
        var showErrors by rememberSaveable { mutableStateOf(false) }

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
            Text(weekRange(current.weekStart, current.today), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(if (current.open.isEmpty()) R.string.close_intro_note_only else R.string.close_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (current.done.isNotEmpty()) {
                SectionLabel(stringResource(R.string.close_finished), Modifier.padding(top = 8.dp))
                current.done.forEach { commitment ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.status_done),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            commitment.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            if (current.open.isNotEmpty()) {
                SectionLabel(stringResource(R.string.close_unfinished), Modifier.padding(top = 8.dp))
                current.open.forEach { commitment ->
                    UnfinishedCard(
                        commitment = commitment,
                        selected = current.choices[commitment.id],
                        canCarry = current.canCarry(commitment),
                        closingCurrent = current.closingCurrent,
                        showError = showErrors && commitment.id !in current.choices,
                        onChoose = { viewModel.choose(commitment, it) },
                    )
                }
            }

            SectionLabel(
                stringResource(if (current.closingCurrent) R.string.close_note_label else R.string.close_note_label_past),
                Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    viewModel.saveDraft(it)
                },
                placeholder = { Text(stringResource(R.string.close_note_placeholder)) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.reflection_draft_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                    if (current.undecided == 0) viewModel.close(note)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (current.alreadyClosed) R.string.action_save else R.string.action_close_week))
            }
        }
    }
}

/**
 * One unfinished commitment and the choices for it. Carrying goes to next week when
 * [closingCurrent], and into this week when closing a past week.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnfinishedCard(
    commitment: Commitment,
    selected: CloseChoice?,
    canCarry: Boolean,
    closingCurrent: Boolean,
    showError: Boolean,
    onChoose: (CloseChoice) -> Unit,
) {
    val errorText = stringResource(R.string.close_choose_one)
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.card,
        border = BorderStroke(1.dp, if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { if (showError) error(errorText) },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
            Text(commitment.title, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloseChoice.entries.forEach { choice ->
                    val enabled = choice != CloseChoice.Carry || canCarry
                    FilterChip(
                        selected = selected == choice,
                        onClick = { onChoose(choice) },
                        enabled = enabled,
                        label = { Text(choiceLabel(choice, closingCurrent)) },
                    )
                }
            }
            if (!canCarry) {
                Text(
                    text = stringResource(if (closingCurrent) R.string.close_next_week_full else R.string.close_this_week_full),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun choiceLabel(choice: CloseChoice, closingCurrent: Boolean): String = stringResource(
    when (choice) {
        CloseChoice.Done -> R.string.choice_done
        CloseChoice.Carry -> if (closingCurrent) R.string.choice_next_week else R.string.choice_this_week
        CloseChoice.Someday -> R.string.choice_someday
        CloseChoice.LetGo -> R.string.choice_let_go
    },
)
