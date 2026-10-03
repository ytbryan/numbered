package com.numbered.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.numbered.app.AppContainer
import com.numbered.app.MainActivity
import com.numbered.app.R
import com.numbered.app.appContainer
import com.numbered.app.data.NumberedRepository
import com.numbered.app.data.calendar
import com.numbered.app.domain.CommitmentStatus
import com.numbered.app.domain.MAX_COMMITMENTS_PER_WEEK
import com.numbered.app.ui.closeWeekDeepLink
import com.numbered.app.ui.theme.DarkColors
import com.numbered.app.ui.theme.LightColors
import com.numbered.app.ui.week.ThisWeekState
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

data class WidgetItem(val id: Long, val title: String, val done: Boolean)

/** Everything the widget shows, read in one go so it renders without waiting. */
data class WidgetState(
    val weekStart: LocalDate,
    val daysLeft: Int,
    val items: List<WidgetItem>,
    val offerClose: Boolean,
) {
    val squaresLeft: Int get() = MAX_COMMITMENTS_PER_WEEK - items.size
}

/** This week as the widget shows it, or null before setup. */
suspend fun loadWidgetState(repository: NumberedRepository, clock: Clock): WidgetState? {
    val profile = repository.currentProfile() ?: return null
    val calendar = profile.calendar()
    val today = LocalDate.now(clock)
    val weekStart = calendar.weekStartOf(today)
    val daysLeft = ChronoUnit.DAYS.between(today, weekStart.plusDays(6)).toInt() + 1
    val closed = repository.review(weekStart).first() != null
    return WidgetState(
        weekStart = weekStart,
        daysLeft = daysLeft,
        items = repository.week(weekStart).first()
            .filter { it.status == CommitmentStatus.Open || it.status == CommitmentStatus.Done }
            .map { WidgetItem(it.id, it.title, it.status == CommitmentStatus.Done) },
        // The same rule as the This week screen.
        offerClose = !closed && daysLeft <= ThisWeekState.CLOSE_OFFER_DAYS,
    )
}

/** This week's commitments on the home screen, ticked off without opening the app. */
class ThisWeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val locked = container.appLock.enabled.value
        val state = if (locked) null else loadWidgetState(container.repository, container.clock)
        provideContent {
            val currentlyLocked by container.appLock.enabled.collectAsState()
            ThisWeekWidgetUi(state, currentlyLocked)
        }
    }
}

class ThisWeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = ThisWeekWidget()
}

/** Ticks a commitment done or open again, straight from the widget. */
class ToggleDone : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (context.appContainer.appLock.enabled.value) return
        val id = parameters[CommitmentId] ?: return
        val done = parameters[ToggleableStateKey] ?: return
        context.appContainer.repository.setDone(id, done)
        ThisWeekWidget().updateAll(context)
    }

    companion object {
        val CommitmentId = ActionParameters.Key<Long>("commitment")
    }
}

/**
 * Keeps placed widgets current while the app process runs, so every change to this week shows
 * on the home screen. The provider's hourly update covers a new week starting while it does not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun keepWidgetsCurrent(context: Context, container: AppContainer) {
    val repository = container.repository
    combine(repository.profile(), container.today.value, container.appLock.enabled) { profile, today, locked -> Triple(profile, today, locked) }
        .flatMapLatest { (profile, today, locked) ->
            if (locked) return@flatMapLatest flowOf(null)
            val weekStart = profile?.calendar()?.weekStartOf(today) ?: return@flatMapLatest flowOf(null)
            combine(repository.week(weekStart), repository.review(weekStart), ::Pair)
        }
        .distinctUntilChanged()
        .collect {
            val placed = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, ThisWeekWidgetReceiver::class.java))
            if (placed.isNotEmpty()) ThisWeekWidget().updateAll(context)
        }
}

private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** The widget in the app's own colors, light and dark. */
@Composable
fun ThisWeekWidgetUi(state: WidgetState?, locked: Boolean = false) {
    GlanceTheme(colors = WidgetColors) { WidgetContent(state, locked) }
}

@Composable
fun WidgetContent(state: WidgetState?, locked: Boolean = false) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val openApp = actionStartActivity<MainActivity>()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.background)
            .cornerRadius(20.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (locked || state == null) {
            Text(
                context.getString(if (locked) R.string.lock_widget else R.string.widget_setup),
                style = TextStyle(color = colors.onSurface, fontSize = 14.sp),
                modifier = GlanceModifier.clickable(openApp),
            )
            return@Column
        }
        val daysLeft = if (state.daysLeft == 1) {
            context.getString(R.string.last_day)
        } else {
            context.resources.getQuantityString(R.plurals.days_left, state.daysLeft, state.daysLeft)
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(openApp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                context.getString(R.string.this_week),
                style = TextStyle(color = colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                daysLeft,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        state.items.forEach { item ->
            CheckBox(
                checked = item.done,
                onCheckedChange = actionRunCallback<ToggleDone>(actionParametersOf(ToggleDone.CommitmentId to item.id)),
                text = item.title,
                // Finished ones recede, so what is left stands out.
                style = TextStyle(color = if (item.done) colors.onSurfaceVariant else colors.onSurface, fontSize = 14.sp),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
        // Adding and closing share one row, so three commitments still fit a two-row widget.
        if (state.squaresLeft > 0 || state.offerClose) {
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.squaresLeft > 0) {
                    Text(
                        context.getString(if (state.items.isEmpty()) R.string.widget_add_first else R.string.widget_add),
                        style = TextStyle(color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight().clickable(openApp),
                    )
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                if (state.offerClose) {
                    val close = Intent(Intent.ACTION_VIEW, closeWeekDeepLink(state.weekStart), context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    Text(
                        context.getString(R.string.widget_close),
                        style = TextStyle(color = colors.secondary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(start = 12.dp).clickable(actionStartActivity(close)),
                    )
                }
            }
        }
    }
}
