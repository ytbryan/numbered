package com.numbered.app.ui.someday

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.data.SomedayItem
import com.numbered.app.ui.NoticeEffect
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.ExpandedTextEditor
import com.numbered.app.ui.components.ExpandTextButton
import com.numbered.app.ui.components.MenuAction
import com.numbered.app.ui.components.OverflowMenu
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.SectionLabel
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.theme.card

@Composable
fun SomedayScreen(initialQuery: String = "") {
    val viewModel = containerViewModel { SomedayViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoticeEffect(viewModel.notices)
    val source = state ?: return
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var searching by rememberSaveable { mutableStateOf(initialQuery.isNotBlank()) }
    var sort by rememberSaveable { mutableStateOf(SomedaySort.Oldest) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val current = source.matching(query, sort)
    val filtering = query.isNotBlank()
    var draft by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showLetGo by rememberSaveable { mutableStateOf(false) }
    val submit = {
        if (draft.isNotBlank()) {
            viewModel.add(draft)
            draft = ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.tab_someday),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = { searching = !searching; query = "" }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.someday_search))
                    }
                    Box {
                        val orderLabel = stringResource(sort.label())
                        IconButton(onClick = { sorting = true }, modifier = Modifier.semantics { stateDescription = orderLabel }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.someday_sort))
                        }
                        DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                            SomedaySort.entries.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(choice.label())) },
                                    onClick = { sort = choice; sorting = false },
                                    trailingIcon = { if (sort == choice) Icon(Icons.Outlined.Check, contentDescription = null) },
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.someday_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (searching) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.someday_search)) },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.someday_clear_search))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (!searching) item(key = "add") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.someday_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = { ExpandTextButton { expanded = true } },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = submit, enabled = draft.isNotBlank()) {
                    Text(stringResource(R.string.action_add))
                }
            }
        }
        if (filtering && current.stale.isEmpty() && current.waiting.isEmpty() && current.letGo.isEmpty()) {
            item(key = "no-matches") { Text(stringResource(R.string.someday_no_matches), Modifier.padding(vertical = 16.dp)) }
        }
        if (current.stale.isNotEmpty()) {
            item(key = "stale-label") {
                Column(Modifier.padding(top = 12.dp)) {
                    SectionLabel(stringResource(R.string.stale_heading))
                    Text(
                        stringResource(R.string.stale_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(current.stale, key = { "stale-${it.item.id}" }) { row ->
                StaleCard(row, onKeep = { viewModel.keep(row.item) }, onLetGo = { viewModel.letGo(row.item) }, modifier = Modifier.animateItem())
            }
        }
        if (!filtering || current.waiting.isNotEmpty()) item(key = "waiting-label") {
            SectionLabel(
                stringResource(R.string.waiting_heading, current.waiting.size),
                Modifier.padding(top = 12.dp),
            )
        }
        if (!filtering && current.waiting.isEmpty()) {
            item(key = "waiting-empty") {
                Text(
                    stringResource(R.string.waiting_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(current.waiting, key = { it.item.id }) { row ->
            WaitingRow(
                row = row,
                actions = listOf(
                    MenuAction(stringResource(R.string.action_put_this_week)) { viewModel.scheduleThisWeek(row.item) },
                    MenuAction(stringResource(R.string.action_put_next_week)) { viewModel.scheduleNextWeek(row.item) },
                    MenuAction(stringResource(R.string.action_let_go)) { viewModel.letGo(row.item) },
                    MenuAction(stringResource(R.string.action_delete)) { viewModel.delete(row.item) },
                ),
                modifier = Modifier.animateItem(),
            )
        }
        if (current.letGo.isNotEmpty()) {
            item(key = "let-go-label") {
                val expandedLabel = stringResource(if (showLetGo || filtering) R.string.a11y_expanded else R.string.a11y_collapsed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !filtering, role = Role.Button) { showLetGo = !showLetGo }
                        .semantics { stateDescription = expandedLabel },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.let_go_heading, current.letGo.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showLetGo || filtering) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showLetGo || filtering) {
                items(current.letGo, key = { "let-go-${it.id}" }) { item ->
                    LetGoRow(item, onBringBack = { viewModel.bringBack(item) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
    if (expanded) ExpandedTextEditor(
        title = stringResource(R.string.tab_someday),
        value = draft,
        onValueChange = { draft = it },
        onClose = { expanded = false },
        submitLabel = stringResource(R.string.action_add),
        onSubmit = { expanded = false; submit() },
    )
}

@Composable
private fun StaleCard(row: SomedayRow, onKeep: () -> Unit, onLetGo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
            Text(row.item.title, style = MaterialTheme.typography.bodyLarge)
            Text(waitingText(row.weeksWaiting), style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                val onCard = MaterialTheme.colorScheme.onSecondaryContainer
                TextButton(onClick = onLetGo, colors = ButtonDefaults.textButtonColors(contentColor = onCard)) {
                    Text(stringResource(R.string.action_let_go))
                }
                OutlinedButton(
                    onClick = onKeep,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onCard),
                    border = BorderStroke(1.dp, onCard.copy(alpha = 0.4f)),
                ) { Text(stringResource(R.string.action_keep)) }
            }
        }
    }
}

@Composable
private fun WaitingRow(row: SomedayRow, actions: List<MenuAction>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            ) {
                Text(row.item.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    waitingText(row.weeksWaiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OverflowMenu(actions, stringResource(R.string.a11y_more_for, row.item.title))
        }
    }
}

@Composable
private fun LetGoRow(item: SomedayItem, onBringBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onBringBack) { Text(stringResource(R.string.action_bring_back)) }
    }
}

@Composable
private fun waitingText(weeks: Int): String =
    if (weeks == 0) stringResource(R.string.added_this_week) else pluralString(R.plurals.added_weeks_ago, weeks, weeks)

private fun SomedaySort.label(): Int = when (this) {
    SomedaySort.Oldest -> R.string.someday_oldest
    SomedaySort.Newest -> R.string.someday_newest
    SomedaySort.RecentlyKept -> R.string.someday_recently_kept
}
