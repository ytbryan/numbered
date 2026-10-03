package com.numbered.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.components.ExplanationHelp
import com.numbered.app.domain.LifeCalendar
import com.numbered.app.ui.components.ScreenPadding
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.locale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields

@Composable
fun OnboardingScreen(
    today: LocalDate,
    onStart: (LocalDate, Int, Boolean, DayOfWeek) -> Unit,
    onRestore: () -> Unit,
) {
    var birthEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var horizon by rememberSaveable { mutableIntStateOf(LifeCalendar.HORIZON_CHOICES.first()) }
    var gentle by rememberSaveable { mutableStateOf(false) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val birthDate = birthEpochDay?.let(LocalDate::ofEpochDay)
    // Weeks start on the locale's first day, fixed at setup so stored weeks never shift.
    val firstDay: DayOfWeek = WeekFields.of(locale()).firstDayOfWeek
    val weekNumber = birthDate?.let { LifeCalendar(it, horizon, firstDay).indexOf(today) + 1 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_hook), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        BirthDateField(birthDate, today, onChange = { picked ->
            birthEpochDay = picked.toEpochDay()
            if (!horizonAvailable(horizon, picked, today)) horizon = LifeCalendar.defaultHorizon(picked, today)
            showError = false
        })
        if (showError && birthDate == null) {
            Text(
                stringResource(R.string.birth_date_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        HorizonField(horizon, birthDate, today) { horizon = it }
        GentleField(gentle) { gentle = it }
        Text(
            stringResource(R.string.privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                if (birthDate == null) showError = true else onStart(birthDate, horizon, gentle, firstDay)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                weekNumber?.let { stringResource(R.string.start_week, formatCount(it)) }
                    ?: stringResource(R.string.action_start),
            )
        }
        TextButton(onClick = onRestore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.action_restore_from_file))
        }
        ExplanationHelp(
            title = stringResource(R.string.about_numbered),
            body = stringResource(R.string.name_origin),
            buttonLabel = stringResource(R.string.about_numbered),
        )
    }
}
