package com.vocabguard.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vocabguard.VocabGuardApp
import com.vocabguard.data.local.GuardedAppEntity
import com.vocabguard.data.repository.AutoWordsRepository
import com.vocabguard.data.repository.AutoWordsSettings
import com.vocabguard.data.repository.DailyStats
import com.vocabguard.data.repository.GuardedAppsRepository
import com.vocabguard.data.repository.SettingsRepository
import com.vocabguard.data.repository.WordRepository
import com.vocabguard.domain.CefrLevel
import com.vocabguard.domain.QuizMode
import com.vocabguard.domain.ReviewPace
import com.vocabguard.sound.SoundPlayer
import com.vocabguard.util.PermissionUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionState(
    val accessibilityEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
) {
    val grantedCount: Int get() = listOf(accessibilityEnabled, overlayGranted).count { it }
    val allGranted: Boolean get() = accessibilityEnabled && overlayGranted
}

data class MainUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val blockerEnabled: Boolean = true,
    val quizModes: Set<QuizMode> = QuizMode.DEFAULT,
    val reviewPace: ReviewPace = ReviewPace.DEFAULT,
    val todayStats: DailyStats = DailyStats(),
    val guardedApps: List<GuardedAppEntity> = emptyList(),
    val activeWordCount: Int = 0,
    val permissions: PermissionState = PermissionState(),
    val uiScale: Float = 1f,
    val soundsEnabled: Boolean = true,
    val autoWords: AutoWordsSettings = AutoWordsSettings(),
    val estimatedLevel: CefrLevel? = null,
    val isAddingWords: Boolean = false,
)

/** Status and Settings tabs: global switch, quiz types, stats and permissions. */
class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val appsRepository: GuardedAppsRepository,
    wordRepository: WordRepository,
    private val autoWordsRepository: AutoWordsRepository,
    private val checkPermissions: () -> PermissionState,
) : ViewModel() {

    private val permissions = MutableStateFlow(checkPermissions())
    private val estimatedLevel = MutableStateFlow<CefrLevel?>(null)
    private val isAddingWords = MutableStateFlow(false)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val autoState = combine(estimatedLevel, isAddingWords) { level, adding -> level to adding }

    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.settings,
        appsRepository.observeApps(),
        wordRepository.observeActiveWordCount(),
        permissions,
        autoState,
    ) { settings, apps, activeWords, perms, (level, adding) ->
        MainUiState(
            isLoading = false,
            blockerEnabled = settings.blockerEnabled,
            quizModes = settings.quizModes,
            reviewPace = settings.reviewPace,
            todayStats = settings.today,
            guardedApps = apps,
            activeWordCount = activeWords,
            permissions = perms,
            uiScale = settings.uiScale,
            soundsEnabled = settings.soundsEnabled,
            autoWords = settings.autoWords,
            estimatedLevel = level,
            isAddingWords = adding,
        )
    }
        .catch { e ->
            Log.e(TAG, "Dashboard failed to load", e)
            emit(MainUiState(isLoading = false, loadFailed = true, permissions = permissions.value))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun refreshPermissions() {
        permissions.value = checkPermissions()
    }

    fun setBlockerEnabled(enabled: Boolean) = launchSafely("Couldn’t change the blocker. Try again.") {
        settingsRepository.setBlockerEnabled(enabled)
        if (!enabled) appsRepository.endAllSessions()
        if (enabled && !permissions.value.allGranted) {
            _events.send(UiEvent.Message("Finish setup so VocabGuard can see when your apps open."))
        }
    }

    fun setQuizMode(mode: QuizMode, enabled: Boolean) = launchSafely("Couldn’t save quiz types.") {
        val current = uiState.value.quizModes
        val updated = if (enabled) current + mode else current - mode
        if (updated.isEmpty()) {
            _events.send(UiEvent.Message("Keep at least one quiz type on."))
            return@launchSafely
        }
        settingsRepository.setQuizModes(updated)
    }

    fun setUiScale(scale: Float) = launchSafely("Couldn’t change the display size.") {
        settingsRepository.setUiScale(scale)
    }

    fun setReviewPace(pace: ReviewPace) = launchSafely("Couldn’t save that.") {
        settingsRepository.setReviewPace(pace)
    }

    fun setSoundsEnabled(enabled: Boolean) = launchSafely("Couldn’t change sounds.") {
        settingsRepository.setSoundsEnabled(enabled)
    }

    // ---- Auto-add words ----------------------------------------------------------------------

    fun refreshEstimatedLevel() = launchSafely("Couldn’t estimate your level.") {
        estimatedLevel.value = autoWordsRepository.estimateLevel()
    }

    fun setAutoWordsEnabled(enabled: Boolean) = launchSafely("Couldn’t change auto-add.") {
        settingsRepository.setAutoWordsEnabled(enabled)
        if (enabled) {
            refreshEstimatedLevelNow()
            // Give immediate feedback: today's batch is added right away.
            autoWordsRepository.runDailyIfDue()?.let { announce(it.added, it.level) }
        }
    }

    fun setAutoWordsPerDay(perDay: Int) = launchSafely("Couldn’t save that.") {
        settingsRepository.setAutoWordsPerDay(perDay)
    }

    fun setAutoWordsLevel(level: CefrLevel?) = launchSafely("Couldn’t save that.") {
        settingsRepository.setAutoWordsLevel(level)
    }

    fun addWordsNow() {
        if (isAddingWords.value) return
        isAddingWords.value = true
        launchSafely("Couldn’t add words.") {
            try {
                val config = settingsRepository.current().autoWords
                val result = autoWordsRepository.addNow(config.perDay, config.level)
                if (result.added == 0) {
                    _events.send(UiEvent.Message("You already have every word in the built-in list."))
                } else {
                    announce(result.added, result.level)
                }
                refreshEstimatedLevelNow()
            } finally {
                isAddingWords.value = false
            }
        }
    }

    /** Called when the dashboard comes to the front; adds at most one batch per day. */
    fun runDailyAutoWords() = launchSafely("Couldn’t add today’s words.") {
        autoWordsRepository.runDailyIfDue()?.let { announce(it.added, it.level) }
    }

    private suspend fun refreshEstimatedLevelNow() {
        estimatedLevel.value = autoWordsRepository.estimateLevel()
    }

    private suspend fun announce(added: Int, level: CefrLevel) {
        val words = if (added == 1) "1 new ${level.name} word" else "$added new ${level.name} words"
        _events.send(UiEvent.PlaySound(SoundPlayer.Sound.Correct))
        _events.send(UiEvent.Message("Added $words to “${AutoWordsRepository.AUTO_DECK_NAME}”."))
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
        private const val TAG = "MainViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as VocabGuardApp
                MainViewModel(
                    settingsRepository = app.container.settingsRepository,
                    appsRepository = app.container.guardedAppsRepository,
                    wordRepository = app.container.wordRepository,
                    autoWordsRepository = app.container.autoWordsRepository,
                    checkPermissions = {
                        PermissionState(
                            accessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(app),
                            overlayGranted = PermissionUtils.canDrawOverlays(app),
                        )
                    },
                )
            }
        }
    }
}
