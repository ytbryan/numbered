package com.numbered.app.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.commitmentStatus
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.weekRange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarryHistoryScreen(id: Long, onBack: () -> Unit, onOpenWeek: (LocalDate) -> Unit) {
    val viewModel = containerViewModel { CarryHistoryViewModel(it, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.carry_history)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
    }) { padding ->
        val current = state ?: return@Scaffold
        val history = current.history
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (history == null) item { Text(stringResource(R.string.history_missing)) } else {
                val title = history.entries.first { it.id == id }.title
                item(key = "heading") {
                    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            when {
                                history.carriedTimes == 0 -> stringResource(R.string.history_not_carried)
                                history.missingBefore != null -> pluralString(R.plurals.history_carried_at_least, history.carriedTimes, history.carriedTimes)
                                else -> pluralString(R.plurals.history_carried, history.carriedTimes, history.carriedTimes)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                history.missingBefore?.let { week ->
                    item(key = "missing") {
                        Text(
                            stringResource(R.string.history_earlier_missing, weekRange(week, current.today)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(history.entries, key = { it.id }) { entry ->
                    val active = entry.id == id
                    Surface(
                        onClick = { onOpenWeek(entry.weekStart) },
                        shape = CardShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = if (active) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth().semantics { selected = active },
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(weekRange(entry.weekStart, current.today), style = MaterialTheme.typography.titleMedium)
                            if (entry.title != title) Text(entry.title, style = MaterialTheme.typography.bodyMedium)
                            Text(commitmentStatus(entry.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
