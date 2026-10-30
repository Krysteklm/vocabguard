package com.vocabguard.lock

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vocabguard.VocabGuardApp
import com.vocabguard.data.local.GuardedAppEntity
import com.vocabguard.data.repository.AutoWordsRepository
import com.vocabguard.data.repository.GuardedAppsRepository
import com.vocabguard.data.repository.QuizRepository
import com.vocabguard.data.repository.SettingsRepository
import com.vocabguard.domain.AnswerChecker
import com.vocabguard.domain.QuizMode
import com.vocabguard.domain.QuizQuestion
import com.vocabguard.util.epochDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AnswerFeedback { None, Correct, Almost, Wrong }

data class LockUiState(
    val appLabel: String = "this app",
    val requiredStreak: Int = GuardedAppEntity.DEFAULT_STREAK,
    val sessionMinutes: Int = GuardedAppEntity.DEFAULT_SESSION_MINUTES,
    /** null = no daily limit for this app. */
    val unlocksLeftToday: Int? = null,
    val limitReached: Boolean = false,
    val question: QuizQuestion? = null,
    val questionNumber: Int = 0,
    val streak: Int = 0,
    val selectedIndex: Int? = null,
    val selectedTruth: Boolean? = null,
    val typedAnswer: String? = null,
    val feedback: AnswerFeedback = AnswerFeedback.None,
    val shakeTrigger: Int = 0,
    val showBackHint: Boolean = false,
    val isUnlocking: Boolean = false,
    val isLeaving: Boolean = false,
) {
    val acceptsInput: Boolean
        get() = question != null && feedback == AnswerFeedback.None &&
            !isUnlocking && !isLeaving && !limitReached
}

sealed interface LockEvent {
    data class Unlocked(val targetPackage: String?) : LockEvent
    data object ExitToHome : LockEvent
}

class LockScreenViewModel(
    private val quizRepository: QuizRepository,
    private val appsRepository: GuardedAppsRepository,
    private val settingsRepository: SettingsRepository,
    private val autoWordsRepository: AutoWordsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private val _events = Channel<LockEvent>(Channel.BUFFERED)
    val events: Flow<LockEvent> = _events.receiveAsFlow()

    private var targetPackage: String? = null
    private var quizModes: Set<QuizMode> = QuizMode.DEFAULT
    private var configJob: Job? = null
    private var backHintJob: Job? = null

    init {
        viewModelScope.launch {
            quizModes = attempt { settingsRepository.current().quizModes } ?: QuizMode.DEFAULT
            showNextQuestion(excludeWordId = null)
            // Today's auto-added words (if the feature is on) join from the next question.
            attempt { autoWordsRepository.runDailyIfDue() }
        }
    }

    /** Loads this app's own rules (streak, pass length, daily limit). */
    fun bindTarget(packageName: String?) {
        if (packageName.isNullOrBlank() || packageName == targetPackage) return
        targetPackage = packageName
        configJob?.cancel()
        configJob = viewModelScope.launch {
            val app = attempt { appsRepository.get(packageName) } ?: return@launch
            val left = app.unlocksLeft(epochDay())
            _uiState.update {
                it.copy(
                    appLabel = app.label,
                    requiredStreak = app.requiredStreak.coerceIn(GuardedAppEntity.STREAK_RANGE),
                    sessionMinutes = app.sessionMinutes,
                    unlocksLeftToday = left,
                    limitReached = left == 0,
                )
            }
        }
    }

    fun onChoiceSelected(index: Int) {
        val question = _uiState.value.question as? QuizQuestion.Choice ?: return
        if (index !in question.options.indices) return
        submit(correct = index == question.correctIndex, almost = false) { it.copy(selectedIndex = index) }
    }

    fun onTrueFalseSelected(value: Boolean) {
        val question = _uiState.value.question as? QuizQuestion.TrueFalse ?: return
        submit(correct = value == question.isMatch, almost = false) { it.copy(selectedTruth = value) }
    }

    fun onTypedAnswer(text: String) {
        val question = _uiState.value.question as? QuizQuestion.Typing ?: return
        if (text.isBlank()) return
        val verdict = AnswerChecker.check(text, question.correctAnswer)
        submit(
            correct = verdict != AnswerChecker.Verdict.Wrong,
            almost = verdict == AnswerChecker.Verdict.Almost,
        ) { it.copy(typedAnswer = text.trim()) }
    }

    fun onExitRequested() {
        val state = _uiState.value
        if (state.isUnlocking || state.isLeaving) return
        _uiState.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            attempt { settingsRepository.recordMindfulExit() }
            _events.send(LockEvent.ExitToHome)
        }
    }

    fun onBackAttempt() {
        backHintJob?.cancel()
        _uiState.update { it.copy(showBackHint = true) }
        backHintJob = viewModelScope.launch {
            delay(BACK_HINT_DURATION_MS)
            _uiState.update { it.copy(showBackHint = false) }
        }
    }

    private fun submit(correct: Boolean, almost: Boolean, record: (LockUiState) -> LockUiState) {
        val state = _uiState.value
        val question = state.question
        // Ignores double taps and input during feedback, unlocking or a reached daily limit.
        if (!state.acceptsInput || question == null) return

        val newStreak = if (correct) state.streak + 1 else 0 // penalty: a miss resets the streak
        _uiState.update {
            record(it).copy(
                feedback = when {
                    !correct -> AnswerFeedback.Wrong
                    almost -> AnswerFeedback.Almost
                    else -> AnswerFeedback.Correct
                },
                streak = newStreak,
                shakeTrigger = if (correct) it.shakeTrigger else it.shakeTrigger + 1,
                showBackHint = false,
            )
        }
        viewModelScope.launch { attempt { quizRepository.recordAnswer(question, correct) } }

        viewModelScope.launch {
            if (correct && newStreak >= state.requiredStreak) {
                unlock()
            } else {
                delay(
                    when {
                        correct && !almost -> CORRECT_DELAY_MS
                        correct -> ALMOST_DELAY_MS
                        question is QuizQuestion.Typing -> WRONG_TYPING_DELAY_MS
                        else -> WRONG_DELAY_MS
                    },
                )
                showNextQuestion(excludeWordId = question.wordId)
            }
        }
    }

    private suspend fun unlock() {
        _uiState.update { it.copy(isUnlocking = true) }
        val pkg = targetPackage
        val minutes = _uiState.value.sessionMinutes
        // grantSession stores the pass in memory first, so a failed write can't cause a loop.
        if (pkg != null) attempt { appsRepository.grantSession(pkg, minutes) }
        attempt { settingsRepository.recordQuizSolved() }
        delay(UNLOCK_CELEBRATION_MS)
        _events.send(LockEvent.Unlocked(pkg))
    }

    private suspend fun showNextQuestion(excludeWordId: Long?) {
        val next = attempt { quizRepository.nextQuestion(quizModes, excludeWordId) }
            ?: quizRepository.fallbackQuestion(quizModes.random(), excludeWordId)
        _uiState.update {
            it.copy(
                question = next,
                questionNumber = it.questionNumber + 1,
                selectedIndex = null,
                selectedTruth = null,
                typedAnswer = null,
                feedback = AnswerFeedback.None,
            )
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Lock screen operation failed", e)
        null
    }

    companion object {
        private const val TAG = "LockScreenViewModel"
        private const val CORRECT_DELAY_MS = 550L
        private const val ALMOST_DELAY_MS = 1_300L
        private const val WRONG_DELAY_MS = 1_400L
        private const val WRONG_TYPING_DELAY_MS = 1_900L
        private const val UNLOCK_CELEBRATION_MS = 750L
        private const val BACK_HINT_DURATION_MS = 2_500L

        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as VocabGuardApp
                LockScreenViewModel(
                    quizRepository = app.container.quizRepository,
                    appsRepository = app.container.guardedAppsRepository,
                    settingsRepository = app.container.settingsRepository,
                    autoWordsRepository = app.container.autoWordsRepository,
                )
            }
        }
    }
}
