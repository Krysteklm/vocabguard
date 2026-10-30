package com.vocabguard.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.ui.components.rememberReducedMotion
import com.vocabguard.ui.theme.VGColors
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

private val DashboardTab.label: String
    get() = when (this) {
        DashboardTab.Status -> "Status"
        DashboardTab.Words -> "Words"
        DashboardTab.Apps -> "Apps"
        DashboardTab.Settings -> "Settings"
    }

private val DashboardTab.icon: ImageVector
    get() = when (this) {
        DashboardTab.Status -> Icons.Rounded.Shield
        DashboardTab.Words -> Icons.Rounded.Translate
        DashboardTab.Apps -> Icons.Rounded.Apps
        DashboardTab.Settings -> Icons.Rounded.Tune
    }

private val Route.depth: Int
    get() = when (this) {
        Route.Home -> 0
        is Route.Deck -> 1
        Route.AddApps -> 1
        is Route.Import -> 2
    }

/**
 * Shared-axis style motion: deeper screens slide in from the right, going back slides the
 * other way. With "Remove animations" on, it falls back to a plain cross-fade.
 */
private fun <S> AnimatedContentTransitionScope<S>.slideTransition(forward: Boolean, reduced: Boolean, distanceDivisor: Int): ContentTransform {
    if (reduced) return fadeIn(tween(150)) togetherWith fadeOut(tween(100))
    val enterSpring = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    return (
        slideInHorizontally(enterSpring) { w -> if (forward) w / distanceDivisor else -w / distanceDivisor } +
            fadeIn(tween(220, delayMillis = 40)) +
            scaleIn(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.98f)
        ) togetherWith (
        slideOutHorizontally(tween(200)) { w -> if (forward) -w / (distanceDivisor * 2) else w / (distanceDivisor * 2) } +
            fadeOut(tween(140)) +
            scaleOut(tween(200), targetScale = 0.98f)
        )
}

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    wordsViewModel: WordsViewModel,
    appsViewModel: AppsViewModel,
) {
    val mainState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val reduced = rememberReducedMotion()
    val snackbarHostState = remember { SnackbarHostState() }
    var route by rememberSaveable(stateSaver = RouteSaver) { mutableStateOf<Route>(Route.Home) }
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Status) }

    fun goBack() {
        route = when (val current = route) {
            is Route.Import -> {
                wordsViewModel.closeImport()
                current.deckId?.let { Route.Deck(it) } ?: Route.Home
            }
            Route.AddApps -> {
                appsViewModel.closePicker()
                Route.Home
            }
            else -> Route.Home
        }
    }

    val events = remember(mainViewModel, wordsViewModel, appsViewModel) {
        merge(mainViewModel.events, wordsViewModel.events, appsViewModel.events)
    }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is UiEvent.Message -> launch { snackbarHostState.showSnackbar(event.text) }
                is UiEvent.Undoable -> launch {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text,
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) event.undo()
                }
                is UiEvent.Navigate -> {
                    route = event.route
                    event.tab?.let { tab = it }
                }
            }
        }
    }

    // Prepare screen state whenever a route is entered (no-ops on recomposition / rotation).
    LaunchedEffect(route) {
        when (val current = route) {
            is Route.Deck -> wordsViewModel.openDeck(current.deckId)
            is Route.Import -> wordsViewModel.startImport(current.deckId)
            Route.AddApps -> appsViewModel.openPicker()
            Route.Home -> Unit
        }
    }

    BackHandler(enabled = route != Route.Home) { goBack() }

    Scaffold(
        containerColor = VGColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (route == Route.Home) {
                NavigationBar(containerColor = VGColors.Surface, tonalElevation = 0.dp) {
                    DashboardTab.entries.forEach { item ->
                        val needsAttention = item == DashboardTab.Settings &&
                            !mainState.isLoading && !mainState.permissions.allGranted
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                if (needsAttention) {
                                    BadgedBox(badge = { Badge(containerColor = VGColors.Warning) }) {
                                        Icon(item.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = null)
                                }
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = VGColors.Primary,
                                selectedTextColor = VGColors.Primary,
                                indicatorColor = VGColors.Primary.copy(alpha = 0.14f),
                                unselectedIconColor = VGColors.TextSecondary,
                                unselectedTextColor = VGColors.TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            transitionSpec = { slideTransition(targetState.depth >= initialState.depth, reduced, distanceDivisor = 3) },
            label = "route",
        ) { current ->
            when (current) {
                Route.Home -> HomeTabs(
                    tab = tab,
                    onTabChange = { tab = it },
                    mainState = mainState,
                    mainViewModel = mainViewModel,
                    wordsViewModel = wordsViewModel,
                    appsViewModel = appsViewModel,
                    onNavigate = { route = it },
                )
                is Route.Deck -> DeckScreen(
                    viewModel = wordsViewModel,
                    deckId = current.deckId,
                    onBack = ::goBack,
                    onImportIntoDeck = { route = Route.Import(current.deckId) },
                )
                is Route.Import -> ImportScreen(viewModel = wordsViewModel, onBack = ::goBack)
                Route.AddApps -> AddAppsScreen(viewModel = appsViewModel, onBack = ::goBack)
            }
        }
    }
}

@Composable
private fun HomeTabs(
    tab: DashboardTab,
    onTabChange: (DashboardTab) -> Unit,
    mainState: MainUiState,
    mainViewModel: MainViewModel,
    wordsViewModel: WordsViewModel,
    appsViewModel: AppsViewModel,
    onNavigate: (Route) -> Unit,
) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = tab,
        transitionSpec = { slideTransition(targetState.ordinal > initialState.ordinal, reduced, distanceDivisor = 6) },
        label = "dashboardTab",
    ) { current ->
        when (current) {
            DashboardTab.Status -> StatusScreen(
                state = mainState,
                onToggleBlocker = mainViewModel::setBlockerEnabled,
                onOpenSettings = { onTabChange(DashboardTab.Settings) },
                onOpenApps = { onTabChange(DashboardTab.Apps) },
                onOpenWords = { onTabChange(DashboardTab.Words) },
            )
            DashboardTab.Words -> WordsHomeScreen(
                viewModel = wordsViewModel,
                activeWordCount = mainState.activeWordCount,
                onOpenDeck = { onNavigate(Route.Deck(it)) },
                onImport = { onNavigate(Route.Import(null)) },
            )
            DashboardTab.Apps -> AppsHomeScreen(
                viewModel = appsViewModel,
                onAddApps = { onNavigate(Route.AddApps) },
            )
            DashboardTab.Settings -> SettingsScreen(state = mainState, viewModel = mainViewModel)
        }
    }
}
