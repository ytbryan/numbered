package com.numbered.app.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.numbered.app.R
import com.numbered.app.ui.formatCount
import com.numbered.app.ui.locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

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

/** A read-only strip, ordered by the person's chosen week start, with today named and highlighted. */
@Composable
fun WeekDays(weekStart: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val locale = locale()
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(7) { offset ->
                val day = weekStart.plusDays(offset.toLong())
                val current = day == today
                val description = if (current) stringResource(R.string.today_date, day.format(formatter)) else day.format(formatter)
                Surface(
                    color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
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
                        )
                    }
                }
            }
        }
    }
}
