package com.numbered.app.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.numbered.app.R
import com.numbered.app.appContainer
import com.numbered.app.data.calendar
import com.numbered.app.ui.chapter.ChapterScreen
import com.numbered.app.ui.chapter.ChapterViewModel
import com.numbered.app.ui.close.CatchUpScreen
import com.numbered.app.ui.close.CloseWeekScreen
import com.numbered.app.ui.life.LifeScreen
import com.numbered.app.ui.search.SearchScreen
import com.numbered.app.ui.history.CarryHistoryScreen
import com.numbered.app.ui.yearreview.YearReviewScreen
import com.numbered.app.ui.lines.LinesScreen
import com.numbered.app.ui.profile.DataViewModel
import com.numbered.app.ui.profile.ImportDialog
import com.numbered.app.ui.profile.OnboardingScreen
import com.numbered.app.ui.profile.RootState
import com.numbered.app.ui.profile.RootViewModel
import com.numbered.app.ui.profile.SettingsScreen
import com.numbered.app.ui.profile.rememberImport
import com.numbered.app.ui.someday.SomedayScreen
import com.numbered.app.ui.week.ThisWeekScreen
import com.numbered.app.ui.week.YearProgressPull
import com.numbered.app.ui.weekdetail.WeekDetailScreen
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object ThisWeekRoute

@Serializable data object LifeRoute

@Serializable data class SomedayRoute(val query: String = "")

@Serializable data object SearchRoute

@Serializable data class HistoryRoute(val id: Long)

@Serializable data class WeekRoute(val epochDay: Long)

@Serializable data class CloseWeekRoute(val epochDay: Long)

/** Opens the close of a week. Only explicit intents to MainActivity can carry it. */
private const val CLOSE_WEEK_DEEP_LINK = "numbered://close"

fun closeWeekDeepLink(weekStart: LocalDate): Uri = "$CLOSE_WEEK_DEEP_LINK/${weekStart.toEpochDay()}".toUri()

@Serializable data object SettingsRoute

@Serializable data object CatchUpRoute

@Serializable data object LinesRoute

@Serializable data object YearReviewRoute

/** [id] is [ChapterViewModel.NEW] for a chapter starting in the week of [startEpochDay]. */
@Serializable data class ChapterRoute(val id: Long, val startEpochDay: Long)

private class Tab(val route: Any, val routeClass: KClass<*>, @param:StringRes val label: Int, val icon: ImageVector)

private val Tabs = listOf(
    Tab(ThisWeekRoute, ThisWeekRoute::class, R.string.tab_this_week, Icons.Outlined.CalendarViewWeek),
    Tab(LifeRoute, LifeRoute::class, R.string.tab_life, Icons.Outlined.GridView),
    Tab(SomedayRoute(), SomedayRoute::class, R.string.tab_someday, Icons.Outlined.Inbox),
    Tab(SettingsRoute, SettingsRoute::class, R.string.settings, Icons.Outlined.Settings),
)

@Composable
fun NumberedRoot() {
    val viewModel = containerViewModel { RootViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        when (state) {
            RootState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
            RootState.NeedsSetup -> Setup(today, snackbar, viewModel::completeSetup)
            RootState.Ready -> MainScaffold(snackbar)
        }
    }
}

/** Onboarding, with a way to restore an exported copy on a new phone instead. */
@Composable
private fun Setup(today: LocalDate, snackbar: SnackbarHostState, onStart: (LocalDate, Int, DayOfWeek) -> Unit) {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val data = containerViewModel { DataViewModel(it, resolver) }
    NoticeEffect(data.notices)
    val restore = rememberImport(data)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        OnboardingScreen(today, onStart, onRestore = restore)
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
    ImportDialog(data, replacing = false)
}

@Composable
private fun MainScaffold(snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val container = LocalContext.current.appContainer
    val profile by container.repository.profile().collectAsStateWithLifecycle(initialValue = null)
    val today by container.today.value.collectAsStateWithLifecycle()
    val progressStyle by container.weekProgress.style.collectAsStateWithLifecycle()
    val calendarWeek = profile?.calendar()?.calendarWeek(today)
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = Tabs.any { tab -> destination?.hasRoute(tab.routeClass) == true }
    var offlinePullProgress by remember { mutableFloatStateOf(0f) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { OfflineBanner(offlinePullProgress) },
        snackbarHost = {
            SnackbarHost(snackbar, if (onTab) Modifier else Modifier.navigationBarsPadding())
        },
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        YearProgressPull(
            week = calendarWeek,
            style = progressStyle,
            enabled = onTab,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            onPullProgress = { offlinePullProgress = it },
        ) {
            NavHost(
                navController = nav,
                startDestination = ThisWeekRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
            composable<ThisWeekRoute> {
                ThisWeekScreen(
                    onOpenWeek = { nav.navigate(WeekRoute(it.toEpochDay())) },
                    onCloseWeek = { nav.navigate(CloseWeekRoute(it.toEpochDay())) },
                    onCatchUp = { nav.navigate(CatchUpRoute) },
                    onOpenSomeday = { nav.openTab(SomedayRoute()) },
                    onSearch = { nav.navigate(SearchRoute) },
                    onHistory = { nav.navigate(HistoryRoute(it)) },
                )
            }
            composable<LifeRoute> {
                LifeScreen(
                    onOpenWeek = { nav.navigate(WeekRoute(it.toEpochDay())) },
                    onOpenChapter = { id -> nav.navigate(ChapterRoute(id, 0)) },
                    onNewChapter = { nav.navigate(ChapterRoute(ChapterViewModel.NEW, it.toEpochDay())) },
                    onOpenLines = { nav.navigate(LinesRoute) },
                )
            }
            composable<SomedayRoute> { entry -> SomedayScreen(initialQuery = entry.toRoute<SomedayRoute>().query) }
            composable<SearchRoute> {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpenWeek = { nav.navigate(WeekRoute(it.toEpochDay())) },
                    onOpenSomeday = { nav.navigate(SomedayRoute(it)) },
                    onOpenChapter = { nav.navigate(ChapterRoute(it, 0)) },
                )
            }
            composable<HistoryRoute> { entry ->
                CarryHistoryScreen(
                    id = entry.toRoute<HistoryRoute>().id,
                    onBack = { nav.popBackStack() },
                    onOpenWeek = { nav.navigate(WeekRoute(it.toEpochDay())) },
                )
            }
            composable<WeekRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<WeekRoute>()
                WeekDetailScreen(
                    weekStart = LocalDate.ofEpochDay(route.epochDay),
                    onBack = { nav.popBackStack() },
                    onCloseWeek = { nav.navigate(CloseWeekRoute(it.toEpochDay())) },
                    onHistory = { nav.navigate(HistoryRoute(it)) },
                    onOpenChapter = { id, start -> nav.navigate(ChapterRoute(id ?: ChapterViewModel.NEW, start.toEpochDay())) },
                )
            }
            composable<ChapterRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<ChapterRoute>()
                ChapterScreen(
                    id = route.id.takeIf { it != ChapterViewModel.NEW },
                    startWeek = LocalDate.ofEpochDay(route.startEpochDay),
                    onDone = { nav.popBackStack() },
                )
            }
            composable<CloseWeekRoute>(deepLinks = listOf(navDeepLink<CloseWeekRoute>(basePath = CLOSE_WEEK_DEEP_LINK))) { backStackEntry ->
                val route = backStackEntry.toRoute<CloseWeekRoute>()
                CloseWeekScreen(
                    weekStart = LocalDate.ofEpochDay(route.epochDay),
                    onBack = { nav.popBackStack() },
                    onClosed = { weekNumber ->
                        nav.popBackStack()
                        scope.launch {
                            val number = NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(weekNumber)
                            snackbar.showSnackbar(resources.getString(R.string.notice_week_closed, number))
                        }
                    },
                )
            }
            composable<LinesRoute> {
                LinesScreen(
                    onBack = { nav.popBackStack() },
                    onOpenWeek = { nav.navigate(WeekRoute(it.toEpochDay())) },
                    onYearReview = { nav.navigate(YearReviewRoute) },
                )
            }
            composable<YearReviewRoute> {
                YearReviewScreen(
                    onBack = { nav.popBackStack() },
                    onOpenWeek = { nav.navigate(WeekRoute(it.toEpochDay())) },
                )
            }
            composable<CatchUpRoute> {
                CatchUpScreen(
                    onBack = { nav.popBackStack() },
                    onClosed = { weeks ->
                        nav.popBackStack()
                        scope.launch { snackbar.showSnackbar(resources.getQuantityString(R.plurals.notice_weeks_closed, weeks, weeks)) }
                    },
                )
            }
            composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }
}

/** A persistent, rectangular reminder of the app's OS-enforced network boundary. */
@Composable
private fun OfflineBanner(pullProgress: Float) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(OFFLINE_BANNER_READ_MILLIS)
        collapsed = true
    }
    val pull = pullProgress.coerceIn(0f, 1f)
    val targetHeight = when {
        !collapsed -> OFFLINE_PANEL_HEIGHT
        pull > 0f -> OFFLINE_STRIP_HEIGHT + (OFFLINE_PANEL_HEIGHT - OFFLINE_STRIP_HEIGHT) * pull
        else -> OFFLINE_STRIP_HEIGHT
    }
    val panelHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = if (pull > 0f) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            tween(OFFLINE_COLLAPSE_MILLIS)
        },
        label = "offline panel height",
    )
    val messageVisible = !collapsed || pull >= OFFLINE_MESSAGE_REVEAL_PROGRESS

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth().height(panelHeight).clipToBounds(),
        ) {
            AnimatedVisibility(
                visible = messageVisible,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .heightIn(min = OFFLINE_PANEL_HEIGHT)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.offline_status),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Text(
                        stringResource(R.string.offline_status_summary),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

private const val OFFLINE_BANNER_READ_MILLIS = 4_000L
private const val OFFLINE_COLLAPSE_MILLIS = 240
private const val OFFLINE_MESSAGE_REVEAL_PROGRESS = 0.45f
private val OFFLINE_PANEL_HEIGHT = 48.dp
private val OFFLINE_STRIP_HEIGHT = 6.dp

/** Switches tabs the standard way: one copy of each tab, with its scroll and state restored. */
private fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
