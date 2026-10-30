package com.vocabguard.data.repository

import androidx.room.withTransaction
import com.vocabguard.data.local.AppDatabase
import com.vocabguard.data.local.DeckEntity
import com.vocabguard.data.local.WordEntity
import com.vocabguard.domain.BankWord
import com.vocabguard.domain.CefrLevel
import com.vocabguard.domain.GermanWordBank
import com.vocabguard.util.epochDay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

data class AutoAddResult(val added: Int, val level: CefrLevel, val deckName: String)

/**
 * Optional, off by default: adds a few new words a day from the built-in graded list, at the
 * level the user's existing words sit at. Fully offline.
 *
 * Level estimate: the CEFR level most of the user's recognised words belong to; once 60% of
 * those are learned, it moves up one level. A level that runs out of new words spills into the
 * next one. Nothing is added while 30+ words are still unlearned, so decks don't pile up.
 */
class AutoWordsRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val wordDao = db.wordDao()
    private val deckDao = db.deckDao()
    private val mutex = Mutex()

    suspend fun estimateLevel(): CefrLevel {
        val progress = wordDao.getAllProgress().associate { it.foreignWord.lowercase() to it.correctCount }
        val owned = GermanWordBank.entries.filter { it.foreign.lowercase() in progress }
        if (owned.isEmpty()) return CefrLevel.A1

        val dominant = CefrLevel.entries.maxBy { level -> owned.count { it.level == level } }
        val atDominant = owned.filter { it.level == dominant }
        val learned = atDominant.count { (progress[it.foreign.lowercase()] ?: 0) >= WordRepository.LEARNED_THRESHOLD }
        val mostlyLearned = learned >= atDominant.size * STEP_UP_SHARE
        return if (mostlyLearned) dominant.next() ?: dominant else dominant
    }

    /** "Add words now": ignores the daily schedule and the backlog pause. */
    suspend fun addNow(count: Int, levelOverride: CefrLevel?): AutoAddResult =
        mutex.withLock { addLocked(count, levelOverride) }

    /** Called on app/lock-screen start. Adds at most one batch per calendar day. */
    suspend fun runDailyIfDue(): AutoAddResult? = mutex.withLock {
        val config = settings.current().autoWords
        val today = epochDay(clock())
        if (!config.enabled || config.lastRunDay == today) return@withLock null
        // Mark first, so a crash mid-way can never add two batches on the same day.
        settings.markAutoWordsRun(today)
        if (wordDao.countUnlearnedActive(WordRepository.LEARNED_THRESHOLD) >= MAX_BACKLOG) return@withLock null
        addLocked(config.perDay, config.level).takeIf { it.added > 0 }
    }

    private suspend fun addLocked(count: Int, levelOverride: CefrLevel?): AutoAddResult {
        val startLevel = levelOverride ?: estimateLevel()
        val existing = wordDao.getAllProgress().mapTo(HashSet()) { it.foreignWord.lowercase() }

        val picked = mutableListOf<BankWord>()
        var level: CefrLevel? = startLevel
        var reportedLevel = startLevel
        while (picked.size < count) {
            val current = level ?: break
            val fresh = GermanWordBank.entries
                .filter { it.level == current && it.foreign.lowercase() !in existing }
                .shuffled(random)
                .take(count - picked.size)
            if (picked.isEmpty() && fresh.isNotEmpty()) reportedLevel = current
            picked += fresh
            level = current.next()
        }
        if (picked.isEmpty()) return AutoAddResult(0, reportedLevel, AUTO_DECK_NAME)

        val added = db.withTransaction {
            val deck = deckDao.findByName(AUTO_DECK_NAME)
                ?: DeckEntity(name = AUTO_DECK_NAME).let { it.copy(id = deckDao.insert(it)) }
            wordDao.insertAll(
                picked.map { WordEntity(deckId = deck.id, foreignWord = it.foreign, nativeTranslation = it.native) },
            ).count { it != -1L }
        }
        return AutoAddResult(added, reportedLevel, AUTO_DECK_NAME)
    }

    companion object {
        const val AUTO_DECK_NAME = "Auto-added words"
        const val MAX_BACKLOG = 30
        private const val STEP_UP_SHARE = 0.6
    }
}
