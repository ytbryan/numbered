package com.numbered.app.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.numbered.app.R
import com.numbered.app.domain.CalendarWeek
import com.numbered.app.ui.components.ScreenPadding
import kotlin.math.roundToInt

@Composable
internal fun YearProgressPull(
    week: CalendarWeek?,
    style: WeekProgressStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    var indicatorHeight by remember(style) { mutableIntStateOf(0) }
    val progress = pullState.distanceFraction.coerceIn(0f, 1f)
    val available = enabled && week != null && style != WeekProgressStyle.Off

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = false,
                state = pullState,
                enabled = available,
                onRefresh = {},
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (indicatorHeight * progress).roundToInt()) },
            content = content,
        )
        if (available) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .onSizeChanged { indicatorHeight = it.height }
                    .offset { IntOffset(0, (-indicatorHeight * (1f - progress)).roundToInt()) }
                    .alpha(progress)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = ScreenPadding, end = ScreenPadding, top = 24.dp),
            ) {
                WeekProgressVisual(week, style, visible = progress > 0f)
            }
        }
    }
}

@Composable
private fun WeekProgressVisual(week: CalendarWeek, style: WeekProgressStyle, visible: Boolean) {
    if (style == WeekProgressStyle.Off) return
    val position = stringResource(R.string.week_visual_position, week.number, week.total)
    val description = stringResource(R.string.week_visual_accessibility, week.number, week.total, week.year)
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (visible) 1f else 0f)
            .clearAndSetSemantics {
                if (visible) contentDescription = description else hideFromAccessibility()
            },
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
