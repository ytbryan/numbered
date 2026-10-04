package com.numbered.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.data.Commitment
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.ui.shortDate
import com.numbered.app.ui.theme.card
import com.numbered.app.ui.toLocalDate
import com.numbered.app.ui.weekdayName
import java.time.LocalDate
import java.time.ZoneId

val CardShape = RoundedCornerShape(16.dp)

class MenuAction(val label: String, val onClick: () -> Unit)

/**
 * One commitment. Open and done commitments can be toggled, while resolved ones (carried,
 * returned, or let go) are shown as history with an icon that names what happened.
 */
@Composable
fun CommitmentCard(
    commitment: Commitment,
    subtitle: String?,
    onToggleDone: ((Boolean) -> Unit)?,
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    isDragging: Boolean = false,
    positionNumber: Int? = null,
) {
    val done = commitment.status == CommitmentStatus.Done
    val resolved = !done && commitment.status != CommitmentStatus.Open
    val colors = MaterialTheme.colorScheme
    val dragElevation by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 0.dp,
        animationSpec = tween(90),
        label = "commitment drag elevation",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = when {
            done -> colors.primaryContainer
            resolved -> colors.surface
            else -> colors.card
        },
        border = if (done) null else BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = dragElevation,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusLabel = stringResource(
                when (commitment.status) {
                    CommitmentStatus.Open -> R.string.status_open
                    CommitmentStatus.Done -> R.string.status_done
                    CommitmentStatus.Carried -> R.string.status_carried
                    CommitmentStatus.ReturnedToSomeday -> R.string.status_returned
                    CommitmentStatus.LetGo -> R.string.status_let_go
                },
            )
            if (onToggleDone != null && !resolved) {
                val toggleLabel = stringResource(R.string.a11y_mark_done, commitment.title)
                val positionDescription = positionNumber?.let { stringResource(R.string.a11y_position, it) }
                IconButton(
                    onClick = { onToggleDone(!done) },
                    modifier = Modifier.semantics {
                        contentDescription = toggleLabel
                        stateDescription = positionDescription ?: statusLabel
                        role = Role.Checkbox
                    },
                ) {
                    if (positionNumber != null) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .border(2.dp, colors.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = positionNumber.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (done) colors.primary else colors.outline,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (commitment.status) {
                            CommitmentStatus.Done -> Icons.Filled.CheckCircle
                            CommitmentStatus.Carried -> Icons.AutoMirrored.Outlined.ArrowForward
                            CommitmentStatus.ReturnedToSomeday -> Icons.Outlined.Inbox
                            CommitmentStatus.LetGo -> Icons.Outlined.Air
                            CommitmentStatus.Open -> Icons.Outlined.Circle
                        },
                        contentDescription = statusLabel,
                        tint = if (done) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(if (done) 26.dp else 22.dp),
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = commitment.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        done -> colors.onPrimaryContainer
                        resolved -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (done) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    )
                }
            }
            if (actions.isNotEmpty()) {
                OverflowMenu(
                    actions,
                    stringResource(R.string.a11y_more_for, commitment.title),
                    dragHandleModifier,
                    isDragging,
                )
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
fun OverflowMenu(
    actions: List<MenuAction>,
    description: String,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = modifier) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = description,
                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** An empty square waiting to be filled, drawn with a dashed outline. */
@Composable
fun EmptySquare(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clip(CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun commitmentStatus(status: CommitmentStatus): String = stringResource(
    when (status) {
        CommitmentStatus.Open -> R.string.status_open
        CommitmentStatus.Done -> R.string.status_done
        CommitmentStatus.Carried -> R.string.history_status_carried
        CommitmentStatus.ReturnedToSomeday -> R.string.status_returned
        CommitmentStatus.LetGo -> R.string.status_let_go
    },
)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

/** A tappable tonal card used for prompts such as closing a week or reviewing Someday. */
@Composable
fun PromptCard(
    title: String,
    body: String?,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 12.dp, end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

/** The line under a commitment, worded the same way on every screen. */
@Composable
fun commitmentSubtitle(commitment: Commitment, zone: ZoneId, today: LocalDate): String? = when (commitment.status) {
    CommitmentStatus.Done -> commitment.resolvedAt?.let { resolved ->
        val doneOn = resolved.toLocalDate(zone)
        val inItsWeek = !doneOn.isBefore(commitment.weekStart) && !doneOn.isAfter(commitment.weekStart.plusDays(6))
        stringResource(R.string.done_on, if (inItsWeek) weekdayName(doneOn) else shortDate(doneOn, today))
    }
    CommitmentStatus.Carried -> stringResource(R.string.status_carried)
    CommitmentStatus.ReturnedToSomeday -> stringResource(R.string.status_returned)
    CommitmentStatus.LetGo -> stringResource(R.string.status_let_go)
    CommitmentStatus.Open -> commitment.carriedFrom?.let { from ->
        if (from == commitment.weekStart.minusWeeks(1)) {
            stringResource(R.string.carried_from_last_week)
        } else {
            stringResource(R.string.carried_from, shortDate(from, today))
        }
    }
}

val ScreenPadding: Dp = 20.dp
