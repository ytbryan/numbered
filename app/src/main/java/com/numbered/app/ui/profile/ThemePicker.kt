package com.numbered.app.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.theme.NumberedTheme
import com.numbered.app.ui.theme.ThemeOption

@StringRes
private fun ThemeOption.label(): Int = when (this) {
    ThemeOption.FocusLight -> R.string.theme_focus_light
    ThemeOption.WarmPaper -> R.string.theme_warm_paper
    ThemeOption.DeepInk -> R.string.theme_deep_ink
    ThemeOption.SoftSage -> R.string.theme_soft_sage
    ThemeOption.HighContrast -> R.string.theme_high_contrast
}

@StringRes
private fun ThemeOption.description(): Int = when (this) {
    ThemeOption.FocusLight -> R.string.theme_focus_light_description
    ThemeOption.WarmPaper -> R.string.theme_warm_paper_description
    ThemeOption.DeepInk -> R.string.theme_deep_ink_description
    ThemeOption.SoftSage -> R.string.theme_soft_sage_description
    ThemeOption.HighContrast -> R.string.theme_high_contrast_description
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemePicker(selected: ThemeOption, onSelect: (ThemeOption) -> Unit) {
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
            Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.theme_setting), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(selected.label()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThemeSwatches(selected)
        }
    }

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.theme_choose), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.theme_choose_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(560.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ThemeOption.entries, key = ThemeOption::name) { option ->
                    ThemeChoice(option, selected == option) { onSelect(option) }
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(option: ThemeOption, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(option.label()), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(option.description()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RadioButton(selected = selected, onClick = null)
            }
            ThemePreview(option)
        }
    }
}

@Composable
private fun ThemePreview(option: ThemeOption) {
    NumberedTheme(option) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.tab_this_week), style = MaterialTheme.typography.titleMedium)
                PreviewCommitment(done = true, label = stringResource(R.string.theme_preview_done))
                PreviewCommitment(done = false, label = stringResource(R.string.theme_preview_open))
                Text(
                    stringResource(R.string.add_another),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PreviewCommitment(done: Boolean, label: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = if (done) MaterialTheme.colorScheme.primary else Color.Transparent,
                    border = if (done) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                ) {}
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ThemeSwatches(theme: ThemeOption) {
    NumberedTheme(theme) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.background,
            ).forEach { color ->
                Surface(
                    modifier = Modifier.size(14.dp),
                    shape = CircleShape,
                    color = color,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {}
            }
        }
    }
}
