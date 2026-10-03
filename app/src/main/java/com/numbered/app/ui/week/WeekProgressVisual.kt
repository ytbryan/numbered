package com.numbered.app.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.weekRange

@Composable
internal fun WeekProgressVisual(state: ThisWeekState, style: WeekProgressStyle) {
    if (style == WeekProgressStyle.Off) return
    val week = state.calendarWeek
    val position = stringResource(R.string.week_visual_position, week.number, week.total)
    val range = stringResource(R.string.week_visual_range, weekRange(state.weekStart, state.today))
    val description = stringResource(
        R.string.week_visual_full_accessibility,
        stringResource(R.string.week_and_age, formatCount(state.lifeWeekNumber), state.age),
        stringResource(R.string.week_visual_accessibility, week.number, week.total, week.year),
        range,
    )
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.week_and_age, formatCount(state.lifeWeekNumber), state.age),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            when (style) {
                WeekProgressStyle.Bars -> {
                    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                        val gap = 2.dp.toPx()
                        val barWidth = (size.width - gap * (week.total - 1)) / week.total
                        val radius = CornerRadius(barWidth / 2f)
                        for (index in 0 until week.total) {
                            val current = index + 1 == week.number
                            val height = if (current) size.height else size.height * 0.78f
                            drawRoundRect(
                                color = if (current) accent else track.copy(alpha = if (index < week.number) 0.5f else 0.25f),
                                topLeft = Offset(index * (barWidth + gap), (size.height - height) / 2f),
                                size = Size(barWidth, height),
                                cornerRadius = radius,
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(range, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(position, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                WeekProgressStyle.Circle -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(80.dp)) {
                                val stroke = 7.dp.toPx()
                                val inset = stroke / 2f
                                val arcSize = Size(size.width - stroke, size.height - stroke)
                                drawArc(track.copy(alpha = 0.25f), -90f, 360f, false, Offset(inset, inset), arcSize,
                                    style = Stroke(stroke, cap = StrokeCap.Round))
                                drawArc(accent, -90f, 360f * week.number / week.total, false, Offset(inset, inset), arcSize,
                                    style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Text(week.number.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.week_visual_total, week.total), style = MaterialTheme.typography.titleMedium)
                            Text(weekRange(state.weekStart, state.today), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                WeekProgressStyle.Off -> Unit
            }
        }
    }
}
