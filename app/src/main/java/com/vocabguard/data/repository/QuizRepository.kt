package com.vocabguard.data.repository

import com.vocabguard.data.local.StarterPack
import com.vocabguard.data.local.WordDao
import com.vocabguard.data.local.WordEntity
import com.vocabguard.domain.QuizMode
import com.vocabguard.domain.QuizQuestion
import kotlin.random.Random

/** Builds quiz questions from active decks, falling back to the built-in pack so it never dead-ends. */
class QuizRepository(
    private val wordDao: WordDao,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun nextQuestion(modes: Set<QuizMode>, excludeWordId: Long?): QuizQuestion {
        val mode = modes.ifEmpty { QuizMode.DEFAULT }.random(random)
        val word = wordDao.getReviewCandidates(excludeWordId ?: NO_ID, CANDIDATE_POOL).randomOrNull(random)
            ?: return fallbackQuestion(mode, excludeWordId)
        val pool = distractorPool(word)
        return build(mode, word.id, word.foreignWord, word.nativeTranslation, pool)
            ?: fallbackQuestion(mode, excludeWordId)
    }

    fun fallbackQuestion(mode: QuizMode, excludeWordId: Long?): QuizQuestion {
        val pack = StarterPack.words
        val index = pack.indices.filter { fallbackId(it) != excludeWordId }.random(random)
        val (foreign, native) = pack[index]
        return build(mode, fallbackId(index), foreign, native, pack)
            ?: QuizQuestion.Typing(fallbackId(index), foreign, native)
    }

    suspend fun recordAnswer(question: QuizQuestion, correct: Boolean) {
        if (!question.isTracked) return
        if (correct) wordDao.markCorrect(question.wordId, clock())
        else wordDao.markIncorrect(question.wordId, clock())
    }

    /** Same-deck words first so options share a language; topped up from other active decks. */
    private suspend fun distractorPool(word: WordEntity): List<Pair<String, String>> {
        val sameDeck = wordDao.getRandomInDeck(word.deckId, word.id, DISTRACTOR_POOL)
        val words = if (sameDeck.size >= DISTRACTOR_POOL / 2) {
            sameDeck
        } else {
            sameDeck + wordDao.getRandomActive(word.id, DISTRACTOR_POOL)
        }
        return words.map { it.foreignWord to it.nativeTranslation }
    }

    private fun build(
        mode: QuizMode,
        id: Long,
        foreign: String,
        native: String,
        pool: List<Pair<String, String>>,
    ): QuizQuestion? {
        fun others(reversed: Boolean): List<String> {
            val correct = if (reversed) foreign else native
            return pool.map { if (reversed) it.first else it.second }
                .filterNot { it.equals(correct, ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .shuffled(random)
        }

        return when (mode) {
            QuizMode.CHOICE -> choice(id, prompt = foreign, answer = native, others(reversed = false), reversed = false)
            QuizMode.REVERSE_CHOICE -> choice(id, prompt = native, answer = foreign, others(reversed = true), reversed = true)
            QuizMode.TYPING -> QuizQuestion.Typing(id, foreign, native)
            QuizMode.TRUE_FALSE -> {
                val wrong = others(reversed = false).firstOrNull()
                val isMatch = wrong == null || random.nextBoolean()
                QuizQuestion.TrueFalse(
                    wordId = id,
                    prompt = foreign,
                    proposedTranslation = if (isMatch) native else wrong ?: native,
                    isMatch = isMatch,
                    correctAnswer = native,
                )
            }
        }
    }

    private fun choice(id: Long, prompt: String, answer: String, distractors: List<String>, reversed: Boolean): QuizQuestion? {
        if (distractors.size < OPTION_COUNT - 1) return null
        val options = (distractors.take(OPTION_COUNT - 1) + answer).shuffled(random)
        return QuizQuestion.Choice(id, prompt, options, options.indexOf(answer), reversed)
    }

    private fun fallbackId(index: Int): Long = -(index + 1L)

    private companion object {
        const val OPTION_COUNT = 3
        const val CANDIDATE_POOL = 6
        const val DISTRACTOR_POOL = 8
        const val NO_ID = -1L
    }
}
