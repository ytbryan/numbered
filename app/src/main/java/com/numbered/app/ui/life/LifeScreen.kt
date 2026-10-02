package com.numbered.app.ui.life

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.domain.WeekTone
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.locale
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.theme.LocalWeekColors
import com.numbered.app.ui.weekRange
import java.text.NumberFormat
import java.time.LocalDate

@Composable
fun LifeScreen(onOpenWeek: (LocalDate) -> Unit, onOpenLines: () -> Unit, onOpenSettings: () -> Unit) {
    val viewModel = containerViewModel { LifeViewModel(it) }
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val state = grid ?: return

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.life_lived,
                    yearsText(state.yearsLivedTenths),
                    formatCount(state.currentIndex),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.your_life),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = onOpenLines) {
                    Icon(Icons.Outlined.FormatQuote, contentDescription = stringResource(R.string.lines_title))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
            Spacer(Modifier.height(20.dp))
            LifeGrid(
                tones = state.tones,
                decadeRows = state.decadeRows,
                selectedIndex = selected?.index ?: state.currentIndex,
                onSelect = viewModel::select,
                description = stringResource(
                    R.string.a11y_life_grid,
                    formatCount(state.currentIndex),
                    formatCount(state.tones.size),
                ),
                previousLabel = stringResource(R.string.a11y_previous_week),
                nextLabel = stringResource(R.string.a11y_next_week),
            )
            Spacer(Modifier.height(20.dp))
            Legend()
            if (!state.gentle) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.life_horizon_note, state.horizonYears),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        selected?.let { week ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectedWeekPanel(
                week = week,
                today = state.today,
                onPrevious = { viewModel.step(-1) },
                onNext = { viewModel.step(1) },
                onOpen = { onOpenWeek(week.start) },
            )
        }
    }
}

@Composable
private fun yearsText(tenths: Int): String {
    val format = NumberFormat.getNumberInstance(locale()).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return stringResource(R.string.years_decimal, format.format(tenths / 10.0))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val colors = LocalWeekColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LegendItem(colors.allDone, stringResource(R.string.legend_all_done))
        LegendItem(colors.someDone, stringResource(R.string.legend_some_done))
        LegendItem(colors.lived, stringResource(R.string.legend_lived))
        LegendItem(colors.current, stringResource(R.string.legend_this_week))
        LegendItem(colors.pinned, stringResource(R.string.legend_planned))
        LegendItem(colors.ahead, stringResource(R.string.legend_ahead))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectedWeekPanel(
    week: SelectedWeek,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
) {
    // The summary opens the week and the arrows step through weeks. They are siblings, not nested,
    // so each is its own target for touch and for TalkBack.
    val openLabel = stringResource(R.string.a11y_open_week)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
                .padding(start = ScreenPadding, end = 8.dp, top = 12.dp, bottom = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = stringResource(R.string.week_and_age, formatCount(week.index + 1), week.age),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.range_and_status, weekRange(week.start, today), statusText(week)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detail = week.note?.let { stringResource(R.string.quoted, it) }
                ?: week.commitments.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.title }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.a11y_previous_week))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.a11y_next_week))
        }
    }
}

@Composable
private fun statusText(week: SelectedWeek): String {
    val summary = week.summary
    return when (week.tone) {
        WeekTone.Current -> if (summary.counted > 0) {
            stringResource(R.string.status_this_week_done, summary.done, summary.counted)
        } else {
            stringResource(R.string.status_this_week_empty)
        }
        WeekTone.Pinned -> pluralString(R.plurals.status_planned, summary.occupied, summary.occupied)
        WeekTone.Ahead -> stringResource(R.string.status_unwritten)
        else -> when {
            summary.counted > 0 -> stringResource(R.string.status_done_of, summary.done, summary.counted)
            summary.planned > 0 -> stringResource(R.string.status_plans_changed)
            week.beforeStart -> stringResource(R.string.status_before_numbered)
            else -> stringResource(R.string.status_nothing_planned)
        }
    }
}
