package com.numbered.app.ui.life

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.numbered.app.R
import com.numbered.app.domain.WeekTone
import com.numbered.app.ui.components.CardShape
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.components.TodayDate
import com.numbered.app.ui.containerViewModel
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.locale
import com.numbered.app.ui.pluralString
import com.numbered.app.ui.shortDate
import com.numbered.app.ui.theme.LocalWeekColors
import com.numbered.app.ui.weekRange
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun LifeScreen(
    onOpenWeek: (LocalDate) -> Unit,
    onOpenChapter: (Long) -> Unit,
    onOpenLines: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = containerViewModel { LifeViewModel(it) }
    val grid by viewModel.grid.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val state = grid ?: return
    val calendarWeek = state.calendar.calendarWeek(state.today)
    var enlarged by rememberSaveable { mutableStateOf(false) }
    var choosingYear by rememberSaveable { mutableStateOf(false) }
    var showLegend by rememberSaveable { mutableStateOf(false) }
    val index = selected?.index ?: state.currentIndex
    val year = state.calendar.weekStart(index).plusDays(3).year
    val firstYear = state.calendar.weekStart(0).plusDays(3).year
    val lastYear = state.calendar.weekStart(state.tones.lastIndex).plusDays(3).year
    val window = if (enlarged) yearWindow(state.calendar, year, state.tones.size) else state.tones.indices

    fun jumpToYear(value: Int) {
        val target = yearWindow(state.calendar, value, state.tones.size)
        if (!target.isEmpty()) viewModel.select(target.first)
        enlarged = true
    }

    if (choosingYear) {
        AlertDialog(
            onDismissRequest = { choosingYear = false },
            title = { Text(stringResource(R.string.life_jump_year)) },
            text = {
                LazyColumn(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = (year - firstYear - 2).coerceAtLeast(0)),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) {
                    items((firstYear..lastYear).toList()) { choice ->
                        TextButton(
                            onClick = {
                                jumpToYear(choice)
                                choosingYear = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(choice.toString()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingYear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

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
            TodayDate(state.today, Modifier.padding(top = 4.dp))
            Text(
                stringResource(R.string.today_week_of_year, calendarWeek.number, calendarWeek.total, calendarWeek.year),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            GridControls(
                enlarged = enlarged,
                onEnlarge = { enlarged = it },
                onJump = { choosingYear = true },
                onCurrent = { viewModel.select(state.currentIndex) },
            )
            if (enlarged) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { jumpToYear(year - 1) }, enabled = year > firstYear) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.life_previous_year))
                    }
                    Text(
                        year.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { jumpToYear(year + 1) }, enabled = year < lastYear) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.life_next_year))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LifeGrid(
                tones = state.tones.slice(window),
                decadeRows = if (enlarged) emptyList() else state.decadeRows,
                selectedIndex = index - window.first,
                marks = state.chapterStarts.filter { it in window }.map { it - window.first }.toSet(),
                onSelect = { viewModel.select(it + window.first) },
                description = if (enlarged) stringResource(R.string.a11y_year_grid, year) else stringResource(
                    R.string.a11y_life_grid,
                    formatCount(state.currentIndex),
                    formatCount(state.tones.size),
                ),
                previousLabel = stringResource(R.string.a11y_previous_week),
                nextLabel = stringResource(R.string.a11y_next_week),
                columns = if (enlarged) 7 else 52,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.life_lived,
                    yearsText(state.yearsLivedTenths),
                    formatCount(state.currentIndex),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { showLegend = !showLegend }) { Text(stringResource(R.string.life_colour_key)) }
            if (showLegend) Legend()
            if (showLegend && !state.gentle) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.life_horizon_note, state.horizonYears),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Chapters(state, onOpenChapter)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GridControls(enlarged: Boolean, onEnlarge: (Boolean) -> Unit, onJump: () -> Unit, onCurrent: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !enlarged, onClick = { onEnlarge(false) }, label = { Text(stringResource(R.string.life_whole)) })
        FilterChip(selected = enlarged, onClick = { onEnlarge(true) }, label = { Text(stringResource(R.string.life_larger)) })
        TextButton(onClick = onJump) { Text(stringResource(R.string.life_jump_year)) }
        if (enlarged) TextButton(onClick = onCurrent) { Text(stringResource(R.string.life_jump_this_week)) }
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
        LegendMark(stringResource(R.string.legend_chapter))
    }
}

/** The legend entry for the dot that marks where a chapter begins. */
@Composable
private fun LegendMark(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LocalWeekColors.current.allDone),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Every chapter, oldest first, each opening for editing. */
@Composable
private fun Chapters(state: LifeGridState, onOpen: (Long) -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.chapters_title), style = MaterialTheme.typography.titleSmall)
    if (state.chapters.isEmpty()) {
        Text(
            stringResource(R.string.chapters_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        return
    }
    val locale = locale()
    val monthYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMM"), locale)
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.chapters.forEach { chapter ->
            val span = when (chapter.endWeek) {
                null -> stringResource(R.string.chapter_span_ongoing, chapter.startWeek.format(monthYear))
                chapter.startWeek -> shortDate(chapter.startWeek, state.today)
                else -> stringResource(R.string.chapter_span, chapter.startWeek.format(monthYear), chapter.endWeek.format(monthYear))
            }
            Surface(
                onClick = { onOpen(chapter.id) },
                shape = CardShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(chapter.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.chapter_age_span, state.calendar.ageOn(chapter.startWeek), span),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
            if (week.chapters.isNotEmpty()) {
                Text(
                    text = week.chapters.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
