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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    onSelect: (Int) -> Unit,
    description: String,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWeekColors.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()
    val labels = remember(decadeRows, labelStyle, measurer) {
        decadeRows.map { (row, age) -> row to measurer.measure(age.toString(), labelStyle) }
    }
    val density = LocalDensity.current
    val labelGap = with(density) { 6.dp.toPx() }
    val labelWidth = (labels.maxOfOrNull { it.second.size.width } ?: 0) + labelGap
    // Labels are centred on their rows, so the first and last can overhang the squares by half a line.
    val inset = (labels.maxOfOrNull { it.second.size.height } ?: 0) / 2f
    val rows = (tones.size + WEEKS_PER_ROW - 1) / WEEKS_PER_ROW
    val currentSelect = rememberUpdatedState(onSelect)
    val currentSelected = rememberUpdatedState(selectedIndex)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val pitch = (widthPx - labelWidth) / WEEKS_PER_ROW
        val gap = maxOf(1f, pitch * 0.18f)
        val heightDp = with(density) { (pitch * rows + inset * 2).toDp() }

        fun indexAt(position: Offset): Int? {
            val column = floor((position.x - labelWidth) / pitch).toInt()
            val row = floor((position.y - inset) / pitch).toInt()
            if (column !in 0 until WEEKS_PER_ROW || row !in 0 until rows) return null
            return (row * WEEKS_PER_ROW + column).takeIf { it < tones.size }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .pointerInput(pitch, labelWidth, tones.size) {
                    detectTapGestures { position -> indexAt(position)?.let(currentSelect.value) }
                }
                .pointerInput(pitch, labelWidth, tones.size) {
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
                val row = index / WEEKS_PER_ROW
                val column = index % WEEKS_PER_ROW
                drawRoundRect(
                    color = colors.of(tone),
                    topLeft = Offset(labelWidth + column * pitch, inset + row * pitch),
                    size = square,
                    cornerRadius = corner,
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
                val row = selectedIndex / WEEKS_PER_ROW
                val column = selectedIndex % WEEKS_PER_ROW
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
