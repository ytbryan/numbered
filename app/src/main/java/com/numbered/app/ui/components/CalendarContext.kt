package com.numbered.app.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.locale
import com.numbered.app.ui.week.WeekFidgetStyle
import com.numbered.app.ui.week.WeekFidgetStrength
import com.numbered.app.ui.week.FidgetVibrator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun boostedFidgetColorAmount(glow: Float, energy: Float): Float {
    val recentPulse = (glow * 4f).coerceIn(0f, 1f)
    return (glow + (1f - glow) * energy * 0.65f * recentPulse).coerceIn(0f, 1f)
}

@Composable
fun TodayDate(today: LocalDate, modifier: Modifier = Modifier, showYear: Boolean = false) {
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(), if (showYear) "yEEEMMMd" else "EEEMMMd"), locale())
    val full = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale(), if (showYear) "yEEEEMMMd" else "EEEEMMMd"), locale())
    val description = stringResource(R.string.today_date, today.format(full))
    Text(
        today.format(formatter),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/** A week context strip. Its optional fidget interaction is decorative and never edits data. */
@Composable
fun WeekDays(
    weekStart: LocalDate,
    today: LocalDate,
    fidgetStyle: WeekFidgetStyle = WeekFidgetStyle.Off,
    fidgetStrength: WeekFidgetStrength = WeekFidgetStrength.Balanced,
    modifier: Modifier = Modifier,
) {
    val locale = locale()
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val fidgetVibrator = remember(context) { FidgetVibrator(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val strengthFactor = when (fidgetStrength) {
        WeekFidgetStrength.Gentle -> 0.65f
        WeekFidgetStrength.Balanced -> 1f
        WeekFidgetStrength.Strong -> 1.4f
        WeekFidgetStrength.Extreme -> 2f
    }
    val movement = with(LocalDensity.current) { 7.dp.toPx() } * strengthFactor
    var rowWidth by remember { mutableIntStateOf(0) }
    var activeDay by remember(weekStart) { mutableStateOf<Int?>(null) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var colorEnergyTarget by remember(weekStart) { mutableFloatStateOf(0f) }
    var colorEnergyDecay by remember(weekStart) { mutableStateOf<Job?>(null) }
    val colorEnergy by animateFloatAsState(
        targetValue = colorEnergyTarget,
        animationSpec = tween(if (colorEnergyTarget > 0f) 90 else 900),
        label = "fidget colour energy",
    )
    val pulses = remember(weekStart) { mutableStateListOf<Int>().apply { repeat(7) { add(0) } } }
    val todayIndex = ChronoUnit.DAYS.between(weekStart, today).toInt().takeIf { it in 0..6 } ?: -1
    val fidgetModifier = if (fidgetStyle == WeekFidgetStyle.Off) {
        Modifier
    } else {
        Modifier
            .onSizeChanged { rowWidth = it.width }
            .pointerInput(rowWidth, weekStart, today, fidgetStyle, fidgetStrength) {
                if (rowWidth == 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastDay = -1
                    var crossedDay = false

                    fun visit(x: Float) {
                        pointerX = x
                        val index = ((x / rowWidth) * 7).toInt().coerceIn(0, 6)
                        if (index == lastDay) return
                        crossedDay = lastDay >= 0
                        lastDay = index
                        activeDay = index
                        pulses[index] = pulses[index] + 1
                        if (fidgetStyle != WeekFidgetStyle.RollingNumbers &&
                            fidgetStyle != WeekFidgetStyle.MechanicalRotation
                        ) {
                            val energyStep = when (fidgetStrength) {
                                WeekFidgetStrength.Gentle -> 0.08f
                                WeekFidgetStrength.Balanced -> 0.12f
                                WeekFidgetStrength.Strong -> 0.18f
                                WeekFidgetStrength.Extreme -> 0.25f
                            }
                            colorEnergyTarget = (colorEnergyTarget + energyStep).coerceAtMost(1f)
                            colorEnergyDecay?.cancel()
                            colorEnergyDecay = scope.launch {
                                delay(650)
                                colorEnergyTarget = 0f
                            }
                        }
                        if (fidgetStrength == WeekFidgetStrength.Extreme) {
                            fidgetVibrator.click()
                        } else {
                            haptics.performHapticFeedback(
                                if (fidgetStrength == WeekFidgetStrength.Strong) {
                                    HapticFeedbackType.LongPress
                                } else {
                                    HapticFeedbackType.TextHandleMove
                                },
                            )
                        }
                        if (fidgetStyle == WeekFidgetStyle.PebbleWave) {
                            scope.launch {
                                val reach = when (fidgetStrength) {
                                    WeekFidgetStrength.Gentle -> 1
                                    WeekFidgetStrength.Balanced -> 2
                                    WeekFidgetStrength.Strong -> 3
                                    WeekFidgetStrength.Extreme -> 4
                                }
                                repeat(reach) { distance ->
                                    delay(36)
                                    listOf(index - distance - 1, index + distance + 1)
                                        .filter { it in 0..6 }
                                        .forEach { neighbour -> pulses[neighbour] = pulses[neighbour] + 1 }
                                }
                            }
                        }
                    }

                    visit(down.position.x)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        visit(change.position.x)
                        change.consume()
                    }
                    activeDay = null

                    if (!crossedDay && lastDay == todayIndex) {
                        scope.launch {
                            repeat(7) { distance ->
                                (0..6).filter { abs(it - todayIndex) == distance }.forEach { index ->
                                    pulses[index] = pulses[index] + 1
                                }
                                delay(34)
                            }
                        }
                    }
                }
            }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().then(fidgetModifier),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(7) { offset ->
                val day = weekStart.plusDays(offset.toLong())
                val current = day == today
                val description = if (current) stringResource(R.string.today_date, day.format(formatter)) else day.format(formatter)
                val glow = remember(day) { Animatable(0f) }
                val pulse = pulses[offset]
                val pulsePeak = (if (fidgetStyle == WeekFidgetStyle.BreathingTrail) 0.55f else 0.42f) * strengthFactor
                LaunchedEffect(pulse) {
                    if (pulse > 0) {
                        glow.snapTo(pulsePeak.coerceAtMost(0.82f))
                        val duration = when (fidgetStyle) {
                            WeekFidgetStyle.BreathingTrail -> 1_050
                            WeekFidgetStyle.MechanicalRotation -> 340
                            else -> 460
                        }
                        glow.animateTo(0f, tween(duration))
                    }
                }
                val isActive = activeDay == offset
                val distanceFromActive = activeDay?.let { it - offset }
                fun scale(target: Float) = 1f + (target - 1f) * strengthFactor
                val targetScaleX = when (fidgetStyle) {
                    WeekFidgetStyle.SoftPress -> if (isActive) scale(0.91f) else 1f
                    WeekFidgetStyle.PebbleWave -> if (isActive) scale(1.03f) else 1f
                    WeekFidgetStyle.ElasticWeek -> if (isActive) scale(1.12f) else if (abs(distanceFromActive ?: 9) == 1) scale(0.96f) else 1f
                    WeekFidgetStyle.MagneticSnap -> if (isActive) scale(1.055f) else 1f
                    WeekFidgetStyle.BreathingTrail -> if (isActive) scale(1.07f) else 1f
                    WeekFidgetStyle.MechanicalRotation -> if (isActive) scale(1.035f) else 1f
                    WeekFidgetStyle.RollingNumbers, WeekFidgetStyle.Off -> 1f
                }
                val targetScaleY = when (fidgetStyle) {
                    WeekFidgetStyle.SoftPress -> if (isActive) scale(0.91f) else 1f
                    WeekFidgetStyle.ElasticWeek -> if (isActive) scale(0.94f) else 1f
                    WeekFidgetStyle.BreathingTrail -> if (isActive) scale(1.07f) else 1f
                    else -> targetScaleX
                }
                val scaleX by animateFloatAsState(
                    targetValue = targetScaleX,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "fidget day width",
                )
                val scaleY by animateFloatAsState(
                    targetValue = targetScaleY,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
                    label = "fidget day height",
                )
                val baseColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                val pulseColor = when (fidgetStyle) {
                    WeekFidgetStyle.PebbleWave -> MaterialTheme.colorScheme.tertiaryContainer
                    WeekFidgetStyle.MagneticSnap -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }
                val targetTranslationX = when {
                    fidgetStyle == WeekFidgetStyle.ElasticWeek && distanceFromActive != null && abs(distanceFromActive) == 1 ->
                        if (distanceFromActive > 0) movement * 0.55f else -movement * 0.55f
                    fidgetStyle == WeekFidgetStyle.MagneticSnap && isActive -> {
                        val centre = (offset + 0.5f) * rowWidth / 7f
                        ((pointerX - centre) * 0.2f).coerceIn(-movement, movement)
                    }
                    else -> 0f
                }
                val translationX by animateFloatAsState(
                    targetValue = targetTranslationX,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "fidget day pull",
                )
                Surface(
                    color = if (fidgetStyle == WeekFidgetStyle.RollingNumbers ||
                        fidgetStyle == WeekFidgetStyle.MechanicalRotation
                    ) {
                        baseColor
                    } else {
                        lerp(baseColor, pulseColor, boostedFidgetColorAmount(glow.value, colorEnergy))
                    },
                    contentColor = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            this.scaleX = scaleX
                            this.scaleY = scaleY
                            this.translationX = translationX
                            translationY = if (fidgetStyle == WeekFidgetStyle.PebbleWave) -glow.value * movement * 1.7f else 0f
                            rotationY = if (fidgetStyle == WeekFidgetStyle.MechanicalRotation) {
                                -(glow.value / pulsePeak).coerceIn(0f, 1f) * 360f * strengthFactor
                            } else {
                                0f
                            }
                            cameraDistance = 12f * density
                        }
                        .clearAndSetSemantics { contentDescription = description },
                ) {
                    Column(
                        Modifier.padding(vertical = 8.dp, horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            formatCount(day.dayOfMonth),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                rotationX = if (fidgetStyle == WeekFidgetStyle.RollingNumbers) {
                                    -(glow.value / pulsePeak).coerceIn(0f, 1f) * 180f * strengthFactor
                                } else {
                                    0f
                                }
                                cameraDistance = 12f * density
                            },
                        )
                    }
                }
            }
        }
    }
}
