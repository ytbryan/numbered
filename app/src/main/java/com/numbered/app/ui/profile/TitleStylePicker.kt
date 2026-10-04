package com.numbered.app.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.numbered.app.ui.components.StyledTitleText
import com.numbered.app.ui.components.withTitleStyle
import com.numbered.app.ui.theme.TitleFont
import com.numbered.app.ui.theme.TitleStyle
import com.numbered.app.ui.theme.TitleTab
import com.numbered.app.ui.theme.TitleTreatment
import com.numbered.app.ui.theme.TitleWeight

@StringRes
private fun TitleTab.label(): Int = when (this) {
    TitleTab.Week -> R.string.this_week
    TitleTab.Life -> R.string.your_life
    TitleTab.Someday -> R.string.tab_someday
}

@StringRes
private fun TitleFont.label(): Int = when (this) {
    TitleFont.Clean -> R.string.title_font_clean
    TitleFont.Book -> R.string.title_font_book
    TitleFont.Mono -> R.string.title_font_mono
}

@StringRes
private fun TitleWeight.label(): Int = when (this) {
    TitleWeight.Regular -> R.string.title_weight_regular
    TitleWeight.Medium -> R.string.title_weight_medium
    TitleWeight.Bold -> R.string.title_weight_bold
}

@StringRes
private fun TitleTreatment.label(): Int = when (this) {
    TitleTreatment.Ink -> R.string.title_color_ink
    TitleTreatment.Accent -> R.string.title_color_accent
    TitleTreatment.Warm -> R.string.title_color_warm
    TitleTreatment.Dawn -> R.string.title_gradient_dawn
    TitleTreatment.Dusk -> R.string.title_gradient_dusk
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TitleStylePicker(styles: Map<TitleTab, TitleStyle>, onSelect: (TitleTab, TitleStyle) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(TitleTab.Week) }
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
            Icon(Icons.Outlined.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.title_style_setting), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.title_style_summary),
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.title_style_heading), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.title_style_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TitleTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = { Text(stringResource(tab.label())) },
                        )
                    }
                }

                val current = styles.getValue(selectedTab)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    StyledTitleText(
                        text = stringResource(selectedTab.label()),
                        selection = current,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                }

                OptionSection(stringResource(R.string.title_font)) {
                    TitleFont.entries.forEach { choice ->
                        val sample = current.copy(font = choice)
                        FilterChip(
                            selected = current.font == choice,
                            onClick = { onSelect(selectedTab, sample) },
                            label = { Text(stringResource(choice.label()), style = MaterialTheme.typography.labelLarge.withTitleStyle(sample)) },
                        )
                    }
                }
                OptionSection(stringResource(R.string.title_weight)) {
                    TitleWeight.entries.forEach { choice ->
                        val sample = current.copy(weight = choice)
                        FilterChip(
                            selected = current.weight == choice,
                            onClick = { onSelect(selectedTab, sample) },
                            label = { Text(stringResource(choice.label()), style = MaterialTheme.typography.labelLarge.withTitleStyle(sample)) },
                        )
                    }
                }
                OptionSection(stringResource(R.string.title_color)) {
                    TitleTreatment.entries.forEach { choice ->
                        val sample = current.copy(treatment = choice)
                        FilterChip(
                            selected = current.treatment == choice,
                            onClick = { onSelect(selectedTab, sample) },
                            label = { Text(stringResource(choice.label()), style = MaterialTheme.typography.labelLarge.withTitleStyle(sample)) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = { content() })
    }
}
