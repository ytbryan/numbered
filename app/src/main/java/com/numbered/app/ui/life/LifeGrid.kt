package com.numbered.app.ui.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
                .pointerInput(pitch, labelWidth, tones.size, columns) {
                    detectTapGestures { position -> indexAt(position)?.let(currentSelect.value) }
                }
                .pointerInput(pitch, labelWidth, tones.size, columns) {
                    detectHorizontalDragGestures(
                        onDragStart = { position -> indexAt(position)?.let(currentSelect.value) },
                    ) { change, _ ->
                        indexAt(change.position)?.let(currentSelect.value)
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
            tones.forEachIndexed { index, tone ->
                val row = index / columns
                val column = index % columns
                drawRoundRect(
                    color = colors.of(tone),
                    topLeft = Offset(labelWidth + column * pitch, inset + row * pitch),
                    size = square,
                    cornerRadius = corner,
                )
            }
            // A dot that reads on both dark and light squares.
            marks.forEach { index ->
                if (index !in tones.indices) return@forEach
                val fill = colors.of(tones[index])
                drawCircle(
                    color = if (fill.luminance() > 0.5f) markOnLight else markOnDark,
                    radius = square.width * 0.24f,
                    center = Offset(
                        labelWidth + (index % columns) * pitch + square.width / 2,
                        inset + (index / columns) * pitch + square.height / 2,
                    ),
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
                val row = selectedIndex / columns
                val column = selectedIndex % columns
                val stroke = 1.5.dp.toPx()
                val outset = stroke + gap / 2
                drawRoundRect(
                    color = selectionColor,
                    topLeft = Offset(labelWidth + column * pitch - outset, inset + row * pitch - outset),
                    size = Size(square.width + outset * 2, square.height + outset * 2),
                    cornerRadius = CornerRadius(corner.x + outset),
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}
