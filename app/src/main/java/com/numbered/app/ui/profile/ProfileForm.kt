package com.numbered.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.domain.LifeCalendar
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** The earliest birth date the picker offers. */
private val EARLIEST_BIRTH_DATE: LocalDate = LocalDate.of(1900, 1, 1)

/** A horizon is only offered once it lies ahead of the person's current age. */
fun horizonAvailable(horizon: Int, birthDate: LocalDate?, today: LocalDate): Boolean =
    birthDate == null || ChronoUnit.YEARS.between(birthDate, today) < horizon

@Composable
fun BirthDateField(birthDate: LocalDate?, today: LocalDate, onChange: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    OutlinedCard(
        onClick = { picking = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(
                    stringResource(R.string.birth_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    birthDate?.format(formatter) ?: stringResource(R.string.birth_date_choose),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (birthDate == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (picking) BirthDatePicker(birthDate, today, onPicked = onChange, onDismiss = { picking = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePicker(initial: LocalDate?, today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toUtcMillis(),
        initialDisplayedMonthMillis = (initial ?: today.minusYears(30)).toUtcMillis(),
        yearRange = EARLIEST_BIRTH_DATE.year..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = LocalDate.ofEpochDay(Math.floorDiv(utcTimeMillis, 86_400_000L))
                return !date.isAfter(today) && !date.isBefore(EARLIEST_BIRTH_DATE)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(
            state = state,
            showModeToggle = true,
            title = {
                Text(
                    stringResource(R.string.birth_date),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizonField(horizon: Int, birthDate: LocalDate?, today: LocalDate, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.horizon_label), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LifeCalendar.HORIZON_CHOICES.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = horizon == choice,
                    onClick = { onChange(choice) },
                    enabled = horizonAvailable(choice, birthDate, today),
                    shape = SegmentedButtonDefaults.itemShape(index, LifeCalendar.HORIZON_CHOICES.size),
                ) { Text(stringResource(R.string.horizon_years, choice)) }
            }
        }
        Text(
            stringResource(R.string.horizon_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GentleField(gentle: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = gentle, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(stringResource(R.string.gentle_label), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.gentle_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = gentle, onCheckedChange = null)
    }
}
