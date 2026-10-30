package com.vocabguard.main

import androidx.compose.runtime.saveable.Saver

enum class DashboardTab { Status, Words, Apps, Settings }

sealed interface Route {
    data object Home : Route
    data class Deck(val deckId: Long) : Route
    data class Import(val deckId: Long?) : Route
    data object AddApps : Route
}

val RouteSaver: Saver<Route, String> = Saver(
    save = { route ->
        when (route) {
            Route.Home -> "home"
            is Route.Deck -> "deck:${route.deckId}"
            is Route.Import -> "import:${route.deckId ?: ""}"
            Route.AddApps -> "add_apps"
        }
    },
    restore = { saved ->
        when {
            saved.startsWith("deck:") -> saved.removePrefix("deck:").toLongOrNull()?.let { Route.Deck(it) } ?: Route.Home
            saved.startsWith("import:") -> Route.Import(saved.removePrefix("import:").toLongOrNull())
            saved == "add_apps" -> Route.AddApps
            else -> Route.Home
        }
    },
)

/** One-shot UI events shared by the dashboard ViewModels. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Undoable(val text: String, val undo: () -> Unit) : UiEvent
    data class Navigate(val route: Route, val tab: DashboardTab? = null) : UiEvent
}
