package com.numbered.app.ui.weekdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.data.Commitment
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.AddCommitmentSheet
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.CommitmentCard
import com.numbered.app.ui.components.EmptySquare
import com.numbered.app.ui.components.MenuAction
import com.numbered.app.ui.components.OtherThingsDoneSection
import com.numbered.app.ui.components.RenameDialog
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.commitmentSubtitle
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.lifeWeekMomentText
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.weekRange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekDetailScreen(
    weekStart: LocalDate,
    onBack: () -> Unit,
    onCloseWeek: (LocalDate) -> Unit,
    onHistory: (Long) -> Unit,
    onOpenChapter: (id: Long?, startWeek: LocalDate) -> Unit,
) {
    val viewModel = containerViewModel { WeekDetailViewModel(it, weekStart) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices)
    var adding by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.let { stringResource(R.string.week_number, formatCount(it.weekNumber)) }.orEmpty()) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(bottom = 10.dp)) {
                    current.moment?.let {
                        Text(lifeWeekMomentText(it), style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        weekRange(current.weekStart, current.today),
                        style = if (current.moment == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.age_and_time, current.age, timeLabel(current)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "chapters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.chapters.forEach { chapter ->
                        Surface(
                            onClick = { onOpenChapter(chapter.id, chapter.startWeek) },
                            shape = CardShape,
                            // Neutral, so a chapter never reads as a finished commitment.
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(chapter.title, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    TextButton(
                        onClick = { onOpenChapter(null, current.weekStart) },
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.action_start_chapter))
                    }
                }
            }
            current.note?.let { note ->
                item(key = "note") {
                    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.quoted, note),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            if (current.commitments.isEmpty() && !current.canAdd) {
                item(key = "empty") {
                    Text(
                        text = stringResource(if (current.beforeStart) R.string.week_before_numbered else R.string.week_nothing_planned),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(current.commitments, key = Commitment::id) { commitment ->
                CommitmentCard(
                    commitment = commitment,
                    subtitle = commitmentSubtitle(commitment, current.zone, current.today),
                    onToggleDone = { done -> viewModel.setDone(commitment, done) },
                    actions = buildList {
                        if (commitment.carriedFrom != null || commitment.status == CommitmentStatus.Carried) {
                            add(MenuAction(stringResource(R.string.carry_history)) { onHistory(commitment.id) })
                        }
                        addAll(actionsFor(commitment, current, viewModel, onRename = { renamingId = commitment.id }))
                    },
                    modifier = Modifier.animateItem(),
                )
            }
            if (current.canAdd) {
                item(key = "add") {
                    EmptySquare(
                        title = stringResource(if (current.time == WeekTime.Future) R.string.add_ahead else R.string.add_another),
                        subtitle = pluralString(R.plurals.squares_left, current.squaresLeft, current.squaresLeft),
                        onClick = { adding = true },
                    )
                }
            }
            if (current.otherThingsDone.isNotEmpty() ||
                (current.otherThingsDoneEnabled && current.time != WeekTime.Future)
            ) {
                item(key = "other-things-done") {
                    OtherThingsDoneSection(
                        items = current.otherThingsDone,
                        canAdd = current.otherThingsDoneEnabled && current.time != WeekTime.Future,
                        onAdd = viewModel::addOtherThingDone,
                        onRename = viewModel::renameOtherThingDone,
                        onRemove = viewModel::removeOtherThingDone,
                    )
                }
            }
            if (current.canClose) {
                item(key = "close") {
                    FilledTonalButton(
                        onClick = { onCloseWeek(current.weekStart) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(stringResource(if (current.closed) R.string.action_edit_close else R.string.action_close_this_week))
                    }
                }
            }
        }

        if (adding) {
            AddCommitmentSheet(
                heading = stringResource(R.string.add_heading_week, weekRange(current.weekStart, current.today)),
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
                RenameDialog(commitment.title, onSave = { viewModel.rename(commitment, it) }, onDismiss = { renamingId = null })
            }
        }
    }
}

@Composable
private fun timeLabel(state: WeekDetailState): String = stringResource(
    when {
        state.time == WeekTime.Current -> R.string.time_this_week
        state.time == WeekTime.Future -> R.string.time_ahead
        state.beforeStart -> R.string.time_before_numbered
        state.closed -> R.string.time_closed
        else -> R.string.time_not_closed
    },
)

@Composable
private fun actionsFor(
    commitment: Commitment,
    state: WeekDetailState,
    viewModel: WeekDetailViewModel,
    onRename: () -> Unit,
): List<MenuAction> = buildList {
    when (commitment.status) {
        CommitmentStatus.Open -> {
            add(MenuAction(stringResource(R.string.action_edit), onRename))
            if (state.time == WeekTime.Past) {
                add(MenuAction(stringResource(R.string.action_move_this_week)) { viewModel.moveToThisWeek(commitment) })
            }
            add(MenuAction(stringResource(R.string.action_back_to_someday)) { viewModel.returnToSomeday(commitment) })
            if (state.time == WeekTime.Past) {
                add(MenuAction(stringResource(R.string.action_let_go)) { viewModel.letGo(commitment) })
            }
            add(MenuAction(stringResource(R.string.action_remove)) { viewModel.remove(commitment) })
        }
        CommitmentStatus.Done -> {
            add(MenuAction(stringResource(R.string.action_edit), onRename))
            add(MenuAction(stringResource(R.string.action_remove)) { viewModel.remove(commitment) })
        }
        CommitmentStatus.Carried, CommitmentStatus.ReturnedToSomeday, CommitmentStatus.LetGo -> Unit
    }
}
