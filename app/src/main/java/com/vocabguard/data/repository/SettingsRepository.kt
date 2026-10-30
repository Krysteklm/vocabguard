package com.vocabguard.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vocabguard.domain.CefrLevel
import com.vocabguard.domain.QuizMode
import com.vocabguard.util.epochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.guardDataStore: DataStore<Preferences> by preferencesDataStore(name = "vocabguard_settings")

data class DailyStats(
    val quizzesSolved: Int = 0,
    val mindfulExits: Int = 0,
) {
    /** Rough, clearly labelled estimate: each "Not now" is assumed to skip one scroll session. */
    val estimatedMinutesSaved: Int get() = mindfulExits * MINUTES_SAVED_PER_EXIT

    companion object {
        const val MINUTES_SAVED_PER_EXIT = 10
    }
}

data class AutoWordsSettings(
    val enabled: Boolean = false,
    val perDay: Int = 5,
    /** null = follow the estimated level. */
    val level: CefrLevel? = null,
    val lastRunDay: Long = 0L,
) {
    companion object {
        val PER_DAY_OPTIONS = listOf(3, 5, 10)
    }
}

data class GuardSettings(
    val blockerEnabled: Boolean = true,
    val quizModes: Set<QuizMode> = QuizMode.DEFAULT,
    val today: DailyStats = DailyStats(),
    /** Multiplies the whole UI (layout and text) on top of the system font size. */
    val uiScale: Float = 1f,
    val soundsEnabled: Boolean = true,
    val autoWords: AutoWordsSettings = AutoWordsSettings(),
)

/** Global switches and daily stats. Per-app rules live in Room (guarded_apps). */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private object Keys {
        val BLOCKER_ENABLED = booleanPreferencesKey("blocker_enabled")
        val QUIZ_MODES = stringSetPreferencesKey("quiz_modes")
        val STATS_DAY = longPreferencesKey("stats_epoch_day")
        val QUIZZES_SOLVED = intPreferencesKey("stats_quizzes_solved")
        val MINDFUL_EXITS = intPreferencesKey("stats_mindful_exits")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val SOUNDS_ENABLED = booleanPreferencesKey("sounds_enabled")
        val AUTO_WORDS_ENABLED = booleanPreferencesKey("auto_words_enabled")
        val AUTO_WORDS_PER_DAY = intPreferencesKey("auto_words_per_day")
        val AUTO_WORDS_LEVEL = stringPreferencesKey("auto_words_level")
        val AUTO_WORDS_LAST_DAY = longPreferencesKey("auto_words_last_day")
    }

    val settings: Flow<GuardSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "Settings read failed, using defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { it.toGuardSettings() }
        .distinctUntilChanged()

    val uiScale: Flow<Float> = settings.map { it.uiScale }.distinctUntilChanged()
    val soundsEnabled: Flow<Boolean> = settings.map { it.soundsEnabled }.distinctUntilChanged()

    suspend fun current(): GuardSettings = settings.first()

    suspend fun setUiScale(scale: Float) {
        dataStore.edit { it[Keys.UI_SCALE] = scale.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE) }
    }

    suspend fun setSoundsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUNDS_ENABLED] = enabled }
    }

    suspend fun setAutoWordsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_WORDS_ENABLED] = enabled }
    }

    suspend fun setAutoWordsPerDay(perDay: Int) {
        dataStore.edit { it[Keys.AUTO_WORDS_PER_DAY] = perDay.coerceIn(1, 20) }
    }

    /** null = automatic. */
    suspend fun setAutoWordsLevel(level: CefrLevel?) {
        dataStore.edit { prefs ->
            if (level == null) prefs.remove(Keys.AUTO_WORDS_LEVEL) else prefs[Keys.AUTO_WORDS_LEVEL] = level.name
        }
    }

    suspend fun markAutoWordsRun(day: Long) {
        dataStore.edit { it[Keys.AUTO_WORDS_LAST_DAY] = day }
    }

    suspend fun setBlockerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BLOCKER_ENABLED] = enabled }
    }

    /** Ignores an empty set: at least one quiz type always stays on. */
    suspend fun setQuizModes(modes: Set<QuizMode>) {
        if (modes.isEmpty()) return
        dataStore.edit { prefs -> prefs[Keys.QUIZ_MODES] = modes.map { it.name }.toSet() }
    }

    suspend fun recordQuizSolved() = increment(Keys.QUIZZES_SOLVED)

    suspend fun recordMindfulExit() = increment(Keys.MINDFUL_EXITS)

    private suspend fun increment(key: Preferences.Key<Int>) {
        dataStore.edit { prefs ->
            prefs.rollOverIfNewDay()
            prefs[key] = (prefs[key] ?: 0) + 1
        }
    }

    private fun MutablePreferences.rollOverIfNewDay() {
        val today = epochDay(clock())
        if (this[Keys.STATS_DAY] != today) {
            this[Keys.STATS_DAY] = today
            this[Keys.QUIZZES_SOLVED] = 0
            this[Keys.MINDFUL_EXITS] = 0
        }
    }

    private fun Preferences.toGuardSettings(): GuardSettings {
        val statsAreToday = this[Keys.STATS_DAY] == epochDay(clock())
        return GuardSettings(
            blockerEnabled = this[Keys.BLOCKER_ENABLED] ?: true,
            quizModes = QuizMode.fromStored(this[Keys.QUIZ_MODES]),
            today = if (statsAreToday) {
                DailyStats(
                    quizzesSolved = this[Keys.QUIZZES_SOLVED] ?: 0,
                    mindfulExits = this[Keys.MINDFUL_EXITS] ?: 0,
                )
            } else {
                DailyStats()
            },
            uiScale = (this[Keys.UI_SCALE] ?: 1f).coerceIn(MIN_UI_SCALE, MAX_UI_SCALE),
            soundsEnabled = this[Keys.SOUNDS_ENABLED] ?: true,
            autoWords = AutoWordsSettings(
                enabled = this[Keys.AUTO_WORDS_ENABLED] ?: false,
                perDay = this[Keys.AUTO_WORDS_PER_DAY] ?: 5,
                level = CefrLevel.fromStored(this[Keys.AUTO_WORDS_LEVEL]),
                lastRunDay = this[Keys.AUTO_WORDS_LAST_DAY] ?: 0L,
            ),
        )
    }

    companion object {
        private const val TAG = "SettingsRepository"
        const val MIN_UI_SCALE = 0.85f
        const val MAX_UI_SCALE = 2f
        val UI_SCALE_OPTIONS = listOf(1f, 1.15f, 1.3f, 1.5f, 1.75f)
    }
}
