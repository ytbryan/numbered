package com.numbered.app.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
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
import com.numbered.app.ui.weekdetail.WeekDetailScreen
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.reflect.KClass
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
private fun Setup(today: LocalDate, snackbar: SnackbarHostState, onStart: (LocalDate, Int, Boolean, DayOfWeek) -> Unit) {
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
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val onTab = Tabs.any { tab -> destination?.hasRoute(tab.routeClass) == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
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
        NavHost(
            navController = nav,
            startDestination = ThisWeekRoute,
            modifier = Modifier.padding(padding),
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

/** Switches tabs the standard way: one copy of each tab, with its scroll and state restored. */
private fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
