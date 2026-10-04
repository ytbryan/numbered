package com.numbered.app.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.data.Commitment
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.AddCommitmentSheet
import com.numbered.app.ui.components.CommitmentCard
import com.numbered.app.ui.components.EmptySquare
import com.numbered.app.ui.components.MenuAction
import com.numbered.app.ui.components.OtherThingsDoneSection
import com.numbered.app.ui.components.PromptCard
import com.numbered.app.ui.components.RenameDialog
import com.numbered.app.ui.components.commitmentSubtitle
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.StyledTabTitle
import com.numbered.app.ui.components.TodayDate
import com.numbered.app.ui.components.WeekDays
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.lifeWeekMomentText
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.shortDate
import com.numbered.app.ui.theme.TitleTab
import com.numbered.app.ui.weekRange
import java.time.LocalDate

@Composable
fun ThisWeekScreen(
    onOpenWeek: (LocalDate) -> Unit,
    onCloseWeek: (LocalDate) -> Unit,
    onCatchUp: () -> Unit,
    onOpenSomeday: () -> Unit,
    onSearch: () -> Unit,
    onHistory: (Long) -> Unit,
) {
    val viewModel = containerViewModel { ThisWeekViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices)
    val current = state ?: return
    var adding by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            item(key = "header") { WeekHeader(current, onSearch) }
            current.catchUp?.let { catchUp ->
                item(key = "catch-up") {
                    PromptCard(
                        title = pluralString(R.plurals.catch_up_title, catchUp.weeks, catchUp.weeks),
                        body = if (catchUp.open > 0) {
                            pluralString(R.plurals.catch_up_body_open, catchUp.open, catchUp.open)
                        } else {
                            stringResource(R.string.catch_up_body_notes)
                        },
                        action = stringResource(R.string.action_catch_up),
                        onClick = onCatchUp,
                    )
                }
            }
            current.unclosed?.let { unclosed ->
                item(key = "unclosed") {
                    PromptCard(
                        title = if (unclosed.weekStart == current.weekStart.minusWeeks(1)) {
                            stringResource(R.string.unclosed_last_week)
                        } else {
                            stringResource(R.string.unclosed_week_of, shortDate(unclosed.weekStart, current.today))
                        },
                        body = if (unclosed.open > 0) {
                            pluralString(R.plurals.unclosed_body_open, unclosed.open, unclosed.open)
                        } else {
                            stringResource(R.string.unclosed_body_note)
                        },
                        action = stringResource(R.string.action_close),
                        onClick = { onCloseWeek(unclosed.weekStart) },
                    )
                }
            }
            items(current.commitments, key = Commitment::id) { commitment ->
                CommitmentCard(
                    commitment = commitment,
                    subtitle = commitmentSubtitle(commitment, current.zone, current.today),
                    onToggleDone = { done -> viewModel.setDone(commitment, done) },
                    actions = buildList {
                        if (commitment.carriedFrom != null) add(MenuAction(stringResource(R.string.carry_history)) { onHistory(commitment.id) })
                        add(MenuAction(stringResource(R.string.action_edit)) { renamingId = commitment.id })
                        if (commitment.status == CommitmentStatus.Open) {
                            add(MenuAction(stringResource(R.string.action_move_next_week)) { viewModel.moveToNextWeek(commitment) })
                            add(MenuAction(stringResource(R.string.action_back_to_someday)) { viewModel.returnToSomeday(commitment) })
                        }
                        add(MenuAction(stringResource(R.string.action_remove)) { viewModel.remove(commitment) })
                    },
                    modifier = Modifier.animateItem(),
                )
            }
            if (current.squaresLeft > 0) {
                item(key = "empty") {
                    EmptySquare(
                        title = stringResource(if (current.commitments.isEmpty()) R.string.add_first else R.string.add_another),
                        subtitle = pluralString(R.plurals.squares_left, current.squaresLeft, current.squaresLeft),
                        onClick = { adding = true },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (current.otherThingsDoneEnabled || current.otherThingsDone.isNotEmpty()) {
                item(key = "other-things-done") {
                    OtherThingsDoneSection(
                        items = current.otherThingsDone,
                        canAdd = current.otherThingsDoneEnabled,
                        onAdd = viewModel::addOtherThingDone,
                        onRename = viewModel::renameOtherThingDone,
                        onRemove = viewModel::removeOtherThingDone,
                    )
                }
            }
            item(key = "gap") { Spacer(Modifier.height(6.dp)) }
            if (current.offerClose) {
                item(key = "close") {
                    PromptCard(
                        title = stringResource(R.string.close_prompt_title),
                        body = stringResource(R.string.close_prompt_body),
                        action = stringResource(R.string.action_close_week),
                        onClick = { onCloseWeek(current.weekStart) },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else if (current.isClosed) {
                item(key = "closed") {
                    PromptCard(
                        title = stringResource(R.string.closed_title),
                        body = current.closedNote?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.quoted, it) },
                        action = stringResource(R.string.action_edit),
                        onClick = { onCloseWeek(current.weekStart) },
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        content = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item(key = "next") {
                PromptCard(
                    title = stringResource(R.string.next_week),
                    body = stringResource(
                        R.string.next_week_body,
                        weekRange(current.nextWeekStart, current.today),
                        current.nextWeekPlanned,
                    ),
                    action = stringResource(R.string.action_plan),
                    onClick = { onOpenWeek(current.nextWeekStart) },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    content = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (current.staleSomeday > 0) {
                item(key = "stale") {
                    PromptCard(
                        title = pluralString(R.plurals.stale_title, current.staleSomeday, current.staleSomeday),
                        body = stringResource(R.string.stale_body),
                        action = stringResource(R.string.action_review),
                        onClick = onOpenSomeday,
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        content = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
    }

    if (adding) {
        AddCommitmentSheet(
            heading = stringResource(R.string.add_heading_this_week),
            someday = current.someday,
            onAdd = viewModel::add,
            onPick = viewModel::addFromSomeday,
            onDismiss = { adding = false },
        )
    }
    renamingId?.let { id ->
        val commitment = current.commitments.firstOrNull { it.id == id }
        if (commitment == null) {
            renamingId = null
        } else {
            RenameDialog(
                initial = commitment.title,
                onSave = { viewModel.rename(commitment, it) },
                onDismiss = { renamingId = null },
            )
        }
    }
}

@Composable
private fun WeekHeader(state: ThisWeekState, onSearch: () -> Unit) {
    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.moment?.let {
            Text(
                text = lifeWeekMomentText(it),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledTabTitle(
                tab = TitleTab.Week,
                text = stringResource(R.string.this_week),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_everything))
            }
        }
        TodayDate(state.today, Modifier.padding(top = 4.dp, bottom = 4.dp))
        WeekDays(state.weekStart, state.today, Modifier.padding(top = 12.dp))
    }
}
