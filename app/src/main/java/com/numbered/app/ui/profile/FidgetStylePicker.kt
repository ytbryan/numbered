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
import androidx.compose.material.icons.outlined.TouchApp
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
import com.numbered.app.ui.week.WeekFidgetStyle
import com.numbered.app.ui.week.WeekFidgetStrength

@StringRes
private fun WeekFidgetStyle.label(): Int = when (this) {
    WeekFidgetStyle.Off -> R.string.fidget_off
    WeekFidgetStyle.SoftPress -> R.string.fidget_soft_press
    WeekFidgetStyle.PebbleWave -> R.string.fidget_pebble_wave
    WeekFidgetStyle.ElasticWeek -> R.string.fidget_elastic_week
    WeekFidgetStyle.RollingNumbers -> R.string.fidget_rolling_numbers
    WeekFidgetStyle.MechanicalRotation -> R.string.fidget_mechanical_rotation
    WeekFidgetStyle.MagneticSnap -> R.string.fidget_magnetic_snap
    WeekFidgetStyle.BreathingTrail -> R.string.fidget_breathing_trail
}

@StringRes
private fun WeekFidgetStyle.description(): Int = when (this) {
    WeekFidgetStyle.Off -> R.string.fidget_off_description
    WeekFidgetStyle.SoftPress -> R.string.fidget_soft_press_description
    WeekFidgetStyle.PebbleWave -> R.string.fidget_pebble_wave_description
    WeekFidgetStyle.ElasticWeek -> R.string.fidget_elastic_week_description
    WeekFidgetStyle.RollingNumbers -> R.string.fidget_rolling_numbers_description
    WeekFidgetStyle.MechanicalRotation -> R.string.fidget_mechanical_rotation_description
    WeekFidgetStyle.MagneticSnap -> R.string.fidget_magnetic_snap_description
    WeekFidgetStyle.BreathingTrail -> R.string.fidget_breathing_trail_description
}

@StringRes
private fun WeekFidgetStrength.label(): Int = when (this) {
    WeekFidgetStrength.Gentle -> R.string.fidget_strength_gentle
    WeekFidgetStrength.Balanced -> R.string.fidget_strength_balanced
    WeekFidgetStrength.Strong -> R.string.fidget_strength_strong
    WeekFidgetStrength.Extreme -> R.string.fidget_strength_extreme
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FidgetStylePicker(
    selected: WeekFidgetStyle,
    strength: WeekFidgetStrength,
    onSelect: (WeekFidgetStyle) -> Unit,
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
            Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.week_fidget), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (selected == WeekFidgetStyle.Off) {
                        stringResource(selected.label())
                    } else {
                        stringResource(R.string.fidget_selection, stringResource(selected.label()), stringResource(strength.label()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.fidget_style_heading), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.week_fidget_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected != WeekFidgetStyle.Off) {
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
                WeekFidgetStyle.entries.forEach { style ->
                    val chosen = selected == style
                    Surface(
                        onClick = { onSelect(style) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
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
