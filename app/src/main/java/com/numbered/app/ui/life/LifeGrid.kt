package com.numbered.app.ui.life

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.numbered.app.domain.LifeCalendar.Companion.WEEKS_PER_ROW
import com.numbered.app.domain.WeekTone
import com.numbered.app.ui.theme.LocalWeekColors
import com.numbered.app.ui.week.FidgetVibrator
import com.numbered.app.ui.week.WeekFidgetStrength
import kotlin.math.abs
import kotlin.math.floor

/**
 * Every week of a life as one square, 52 to a row, so each row holds one year of age.
 * Tap a square to select it, or drag sideways along a row to scrub through the weeks.
 */
@Composable
fun LifeGrid(
    tones: List<WeekTone>,
    decadeRows: List<Pair<Int, Int>>,
    selectedIndex: Int,
    /** Squares marked with a dot, where a chapter begins. */
    marks: Set<Int>,
    onSelect: (Int) -> Unit,
    description: String,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
    columns: Int = WEEKS_PER_ROW,
    fidgetStyle: LifeFidgetStyle = LifeFidgetStyle.Off,
    fidgetStrength: WeekFidgetStrength = WeekFidgetStrength.Balanced,
) {
    val colors = LocalWeekColors.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = MaterialTheme.colorScheme.onSurface
    val markOnLight = Color(0xFF1F1C18)
    val markOnDark = Color(0xFFFFFFFF)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()
    val labels = remember(decadeRows, labelStyle, measurer) {
        decadeRows.map { (row, age) -> row to measurer.measure(age.toString(), labelStyle) }
    }
    val density = LocalDensity.current
    val labelGap = with(density) { 6.dp.toPx() }
    val labelWidth = if (labels.isEmpty()) 0f else (labels.maxOfOrNull { it.second.size.width } ?: 0) + labelGap
    // Labels are centred on their rows, so the first and last can overhang the squares by half a line.
    val inset = (labels.maxOfOrNull { it.second.size.height } ?: 0) / 2f
    val rows = (tones.size + columns - 1) / columns
    val currentSelect = rememberUpdatedState(onSelect)
    val currentSelected = rememberUpdatedState(selectedIndex)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val vibrator = remember(context) { FidgetVibrator(context.applicationContext) }
    val fidgetColor = MaterialTheme.colorScheme.tertiary
    var fidgetIndex by remember { mutableIntStateOf(-1) }
    var lastVisitedIndex by remember { mutableIntStateOf(-1) }
    var pulseId by remember { mutableIntStateOf(0) }
    var trail by remember { mutableStateOf(emptyList<Int>()) }
    val pulse = remember { Animatable(0f) }
    val strengthFactor = when (fidgetStrength) {
        WeekFidgetStrength.Gentle -> 0.65f
        WeekFidgetStrength.Balanced -> 1f
        WeekFidgetStrength.Strong -> 1.4f
        WeekFidgetStrength.Extreme -> 2f
    }

    LaunchedEffect(pulseId, fidgetStrength) {
        if (pulseId > 0) {
            pulse.snapTo(1f)
            pulse.animateTo(
                0f,
                tween(
                    when (fidgetStrength) {
                        WeekFidgetStrength.Gentle -> 680
                        WeekFidgetStrength.Balanced -> 520
                        WeekFidgetStrength.Strong -> 400
                        WeekFidgetStrength.Extreme -> 300
                    },
                ),
            )
        }
    }

    fun triggerFidget(index: Int) {
        if (fidgetStyle == LifeFidgetStyle.Off || index == lastVisitedIndex) return
        lastVisitedIndex = index
        fidgetIndex = index
        trail = (listOf(index) + trail.filterNot { it == index }).take(8)
        pulseId++
        if (fidgetStrength == WeekFidgetStrength.Extreme) {
            vibrator.click()
        } else {
            haptics.performHapticFeedback(
                if (fidgetStrength == WeekFidgetStrength.Strong) HapticFeedbackType.LongPress
                else HapticFeedbackType.TextHandleMove,
            )
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val pitch = (widthPx - labelWidth) / columns
        val gap = maxOf(1f, pitch * 0.18f)
        val heightDp = with(density) { (pitch * rows + inset * 2).toDp() }

        fun indexAt(position: Offset): Int? {
            val column = floor((position.x - labelWidth) / pitch).toInt()
            val row = floor((position.y - inset) / pitch).toInt()
            if (column !in 0 until columns || row !in 0 until rows) return null
            return (row * columns + column).takeIf { it < tones.size }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .pointerInput(pitch, labelWidth, tones.size, columns, fidgetStyle, fidgetStrength) {
                    detectTapGestures { position ->
                        indexAt(position)?.let { index ->
                            currentSelect.value(index)
                            lastVisitedIndex = -1
                            triggerFidget(index)
                            lastVisitedIndex = -1
                        }
                    }
                }
                .pointerInput(pitch, labelWidth, tones.size, columns, fidgetStyle, fidgetStrength) {
                    detectHorizontalDragGestures(
                        onDragStart = { position ->
                            lastVisitedIndex = -1
                            indexAt(position)?.let { index ->
                                currentSelect.value(index)
                                triggerFidget(index)
                            }
                        },
                        onDragEnd = { lastVisitedIndex = -1 },
                        onDragCancel = { lastVisitedIndex = -1 },
                    ) { change, _ ->
                        indexAt(change.position)?.let { index ->
                            currentSelect.value(index)
                            triggerFidget(index)
                        }
                        change.consume()
                    }
                }
                .semantics {
                    contentDescription = description
                    customActions = listOf(
                        CustomAccessibilityAction(previousLabel) {
                            currentSelect.value(currentSelected.value - 1)
                            true
                        },
                        CustomAccessibilityAction(nextLabel) {
                            currentSelect.value(currentSelected.value + 1)
                            true
                        },
                    )
                },
        ) {
            val square = Size(pitch - gap, pitch - gap)
            val corner = CornerRadius(square.width * 0.22f)

            fun dominoOffset(index: Int): Float {
                if (fidgetStyle != LifeFidgetStyle.DominoRow || fidgetIndex !in tones.indices) return 0f
                if (index / columns != fidgetIndex / columns) return 0f
                val distance = abs(index - fidgetIndex)
                if (distance > 3) return 0f
                val direction = if ((index - fidgetIndex) % 2 == 0) -1f else 1f
                return direction * pitch * 0.18f * strengthFactor * pulse.value * (1f - distance / 4f)
            }

            fun topLeft(index: Int): Offset = Offset(
                labelWidth + (index % columns) * pitch,
                inset + (index / columns) * pitch + dominoOffset(index),
            )

            tones.forEachIndexed { index, tone ->
                drawRoundRect(
                    color = colors.of(tone),
                    topLeft = topLeft(index),
                    size = square,
                    cornerRadius = corner,
                )
            }
            if (fidgetIndex in tones.indices && pulse.value > 0f) {
                when (fidgetStyle) {
                    LifeFidgetStyle.WeekPop -> {
                        val expansion = square.width * 0.12f * strengthFactor * pulse.value
                        val origin = topLeft(fidgetIndex)
                        drawRoundRect(
                            color = fidgetColor.copy(alpha = 0.5f * pulse.value),
                            topLeft = Offset(origin.x - expansion, origin.y - expansion),
                            size = Size(square.width + expansion * 2, square.height + expansion * 2),
                            cornerRadius = CornerRadius(corner.x + expansion),
                            style = Stroke(width = maxOf(1f, 2.dp.toPx() * strengthFactor)),
                        )
                    }
                    LifeFidgetStyle.RippleField -> {
                        val activeRow = fidgetIndex / columns
                        val activeColumn = fidgetIndex % columns
                        tones.indices.forEach { index ->
                            val distance = abs(index / columns - activeRow) + abs(index % columns - activeColumn)
                            if (distance <= 3) {
                                drawRoundRect(
                                    color = fidgetColor.copy(alpha = pulse.value * (0.42f - distance * 0.09f) * strengthFactor.coerceAtMost(1.5f)),
                                    topLeft = topLeft(index),
                                    size = square,
                                    cornerRadius = corner,
                                )
                            }
                        }
                    }
                    LifeFidgetStyle.CometTrail -> trail.forEachIndexed { order, index ->
                        if (index in tones.indices) {
                            drawRoundRect(
                                color = fidgetColor.copy(alpha = pulse.value * (0.5f - order * 0.05f) * strengthFactor.coerceAtMost(1.5f)),
                                topLeft = topLeft(index),
                                size = square,
                                cornerRadius = corner,
                            )
                        }
                    }
                    LifeFidgetStyle.DominoRow -> {
                        drawRoundRect(
                            color = fidgetColor.copy(alpha = 0.35f * pulse.value),
                            topLeft = topLeft(fidgetIndex),
                            size = square,
                            cornerRadius = corner,
                        )
                    }
                    LifeFidgetStyle.Off -> Unit
                }
            }
            // A dot that reads on both dark and light squares.
            marks.forEach { index ->
                if (index !in tones.indices) return@forEach
                val fill = colors.of(tones[index])
                drawCircle(
                    color = if (fill.luminance() > 0.5f) markOnLight else markOnDark,
                    radius = square.width * 0.24f,
                    center = topLeft(index) + Offset(square.width / 2, square.height / 2),
                )
            }
            labels.forEach { (row, layout) ->
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x = labelWidth - labelGap - layout.size.width,
                        y = inset + row * pitch + (pitch - gap) / 2 - layout.size.height / 2,
                    ),
                )
            }
            if (selectedIndex in tones.indices) {
                val stroke = 1.5.dp.toPx()
                val outset = stroke + gap / 2
                drawRoundRect(
                    color = selectionColor,
                    topLeft = topLeft(selectedIndex) - Offset(outset, outset),
                    size = Size(square.width + outset * 2, square.height + outset * 2),
                    cornerRadius = CornerRadius(corner.x + outset),
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}
