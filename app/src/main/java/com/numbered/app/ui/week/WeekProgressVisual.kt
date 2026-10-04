package com.numbered.app.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

@Composable
internal fun WeekProgressVisual(state: ThisWeekState, style: WeekProgressStyle) {
    if (style == WeekProgressStyle.Off) return
    val week = state.calendarWeek
    val position = stringResource(R.string.week_visual_position, week.number, week.total)
    val description = stringResource(R.string.week_visual_accessibility, week.number, week.total, week.year)
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        when (style) {
            WeekProgressStyle.Bars -> {
                Column(Modifier.padding(16.dp)) {
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
                    Text(
                        position,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WeekProgressStyle.Circle -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(96.dp)) {
                            val stroke = 8.dp.toPx()
                            val inset = stroke / 2f
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(track.copy(alpha = 0.25f), -90f, 360f, false, Offset(inset, inset), arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                            drawArc(accent, -90f, 360f * week.number / week.total, false, Offset(inset, inset), arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        Text(
                            stringResource(R.string.week_visual_fraction, week.number, week.total),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            WeekProgressStyle.Off -> Unit
        }
    }
}
