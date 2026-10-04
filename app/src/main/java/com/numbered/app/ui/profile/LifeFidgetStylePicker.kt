package com.numbered.app.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.life.LifeFidgetStyle
import com.numbered.app.ui.week.WeekFidgetStrength

@StringRes
private fun LifeFidgetStyle.label(): Int = when (this) {
    LifeFidgetStyle.Off -> R.string.fidget_off
    LifeFidgetStyle.WeekPop -> R.string.life_fidget_week_pop
    LifeFidgetStyle.RippleField -> R.string.life_fidget_ripple_field
    LifeFidgetStyle.CometTrail -> R.string.life_fidget_comet_trail
    LifeFidgetStyle.DominoRow -> R.string.life_fidget_domino_row
}

@StringRes
private fun LifeFidgetStyle.description(): Int = when (this) {
    LifeFidgetStyle.Off -> R.string.life_fidget_off_description
    LifeFidgetStyle.WeekPop -> R.string.life_fidget_week_pop_description
    LifeFidgetStyle.RippleField -> R.string.life_fidget_ripple_field_description
    LifeFidgetStyle.CometTrail -> R.string.life_fidget_comet_trail_description
    LifeFidgetStyle.DominoRow -> R.string.life_fidget_domino_row_description
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LifeFidgetStylePicker(
    selected: LifeFidgetStyle,
    strength: WeekFidgetStrength,
    onSelect: (LifeFidgetStyle) -> Unit,
    onStrength: (WeekFidgetStrength) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.life_fidget), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (selected == LifeFidgetStyle.Off) stringResource(selected.label()) else stringResource(
                        R.string.fidget_selection,
                        stringResource(selected.label()),
                        stringResource(strength.label()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.life_fidget_heading), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.life_fidget_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected != LifeFidgetStyle.Off) {
                    Text(stringResource(R.string.fidget_strength), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekFidgetStrength.entries.forEach { option ->
                            FilterChip(
                                selected = strength == option,
                                onClick = { onStrength(option) },
                                label = { Text(stringResource(option.label())) },
                            )
                        }
                    }
                }
                LifeFidgetStyle.entries.forEach { style ->
                    val chosen = selected == style
                    Surface(
                        onClick = { onSelect(style) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(stringResource(style.label()), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(style.description()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(selected = chosen, onClick = null)
                        }
                    }
                }
            }
        }
    }
}
