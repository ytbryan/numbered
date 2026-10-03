package com.numbered.app.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.week.WeekProgressStyle

@StringRes
private fun WeekProgressStyle.label(): Int = when (this) {
    WeekProgressStyle.Off -> R.string.week_visual_off
    WeekProgressStyle.Circle -> R.string.week_visual_circle
    WeekProgressStyle.Bars -> R.string.week_visual_bars
}

@Composable
internal fun WeekProgressPicker(selected: WeekProgressStyle, onSelect: (WeekProgressStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.week_visual_setting), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.week_visual_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeekProgressStyle.entries.forEach { style ->
            val chosen = selected == style
            Surface(
                onClick = { onSelect(style) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(style.label()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    RadioButton(selected = chosen, onClick = null)
                }
            }
        }
    }
}
