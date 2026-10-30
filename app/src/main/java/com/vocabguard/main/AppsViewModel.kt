package com.vocabguard.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vocabguard.VocabGuardApp
import com.vocabguard.data.local.GuardedAppEntity
import com.vocabguard.data.repository.GuardedAppsRepository
import com.vocabguard.data.repository.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppPickerState(
    val isLoading: Boolean = false,
    val installed: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val loadFailed: Boolean = false,
)

data class AppRules(val enabled: Boolean, val streak: Int, val minutes: Int, val dailyLimit: Int)

/** Which apps are guarded, and each app's rules. */
class AppsViewModel(private val repository: GuardedAppsRepository) : ViewModel() {

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    val apps: StateFlow<List<GuardedAppEntity>> = repository.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _picker = MutableStateFlow(AppPickerState())
    val picker: StateFlow<AppPickerState> = _picker.asStateFlow()

    fun openPicker() {
        // Reload every time so newly installed apps show up.
        if (_picker.value.isLoading) return
        _picker.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val installed = repository.loadInstalledApps()
                _picker.update { it.copy(isLoading = false, installed = installed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't list apps", e)
                _picker.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun closePicker() {
        _picker.update { it.copy(selected = emptySet(), query = "") }
    }

    fun onQueryChange(query: String) = _picker.update { it.copy(query = query) }

    fun toggleSelection(packageName: String) = _picker.update {
        it.copy(selected = if (packageName in it.selected) it.selected - packageName else it.selected + packageName)
    }

    fun addSelected() = launchSafely("Couldn’t add those apps.") {
        val state = _picker.value
        val chosen = state.installed.filter { it.packageName in state.selected }
        if (chosen.isEmpty()) return@launchSafely
        val added = repository.addApps(chosen)
        closePicker()
        _events.send(UiEvent.Navigate(Route.Home, DashboardTab.Apps))
        _events.send(UiEvent.Message(if (added == 1) "Now guarding ${chosen.first().label}." else "Now guarding $added more apps."))
    }

    fun setEnabled(app: GuardedAppEntity, enabled: Boolean) = launchSafely("Couldn’t update ${app.label}.") {
        repository.setEnabled(app.packageName, enabled)
    }

    fun saveRules(app: GuardedAppEntity, rules: AppRules) = launchSafely("Couldn’t save the rules for ${app.label}.") {
        repository.updateRules(app.packageName, rules.enabled, rules.streak, rules.minutes, rules.dailyLimit)
        _events.send(UiEvent.Message("Saved rules for ${app.label}."))
    }

    fun remove(app: GuardedAppEntity) = launchSafely("Couldn’t remove ${app.label}.") {
        repository.remove(app.packageName)
        _events.send(
            UiEvent.Undoable("Stopped guarding ${app.label}") {
                launchSafely("Couldn’t restore ${app.label}.") { repository.restore(app) }
            },
        )
    }

    private fun launchSafely(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, errorMessage, e)
                _events.send(UiEvent.Message(errorMessage))
            }
        }
    }

    companion object {
        private const val TAG = "AppsViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as VocabGuardApp
                AppsViewModel(app.container.guardedAppsRepository)
            }
        }
    }
}
