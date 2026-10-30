package com.vocabguard.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.vocabguard.data.local.AppDatabase
import com.vocabguard.data.local.DeckEntity
import com.vocabguard.data.local.DeckWithCount
import com.vocabguard.data.local.WordEntity
import com.vocabguard.domain.ImportedPair
import com.vocabguard.domain.VocabImportParser
import kotlinx.coroutines.flow.Flow

sealed interface WordResult {
    data object Success : WordResult
    data object Duplicate : WordResult
    data class Invalid(val reason: String) : WordResult
}

sealed interface DeckResult {
    data class Success(val deckId: Long) : DeckResult
    data object Duplicate : DeckResult
    data class Invalid(val reason: String) : DeckResult
}

sealed interface ImportTarget {
    data class Existing(val deckId: Long) : ImportTarget
    data class New(val name: String) : ImportTarget
}

data class ImportSummary(val deckId: Long, val deckName: String, val added: Int, val alreadyThere: Int)

sealed interface ImportResult {
    data class Success(val summary: ImportSummary) : ImportResult
    data class Invalid(val reason: String) : ImportResult
}

/** Decks and words: browsing, editing and bulk import. */
class WordRepository(private val db: AppDatabase) {

    private val deckDao = db.deckDao()
    private val wordDao = db.wordDao()

    fun observeDecks(): Flow<List<DeckWithCount>> = deckDao.observeWithCounts(LEARNED_THRESHOLD)
    fun observeDeck(deckId: Long): Flow<DeckEntity?> = deckDao.observe(deckId)
    fun observeWords(deckId: Long): Flow<List<WordEntity>> = wordDao.observeByDeck(deckId)
    fun observeActiveWordCount(): Flow<Int> = wordDao.observeActiveCount()

    suspend fun createDeck(name: String): DeckResult {
        val clean = name.normalized()
        validateDeckName(clean)?.let { return DeckResult.Invalid(it) }
        return try {
            DeckResult.Success(deckDao.insert(DeckEntity(name = clean)))
        } catch (e: SQLiteConstraintException) {
            DeckResult.Duplicate
        }
    }

    suspend fun renameDeck(deckId: Long, name: String): DeckResult {
        val clean = name.normalized()
        validateDeckName(clean)?.let { return DeckResult.Invalid(it) }
        return try {
            deckDao.rename(deckId, clean)
            DeckResult.Success(deckId)
        } catch (e: SQLiteConstraintException) {
            DeckResult.Duplicate
        }
    }

    suspend fun setDeckActive(deckId: Long, active: Boolean) = deckDao.setActive(deckId, active)

    /** Words are removed explicitly too, so this works even if SQLite FK enforcement is off. */
    suspend fun deleteDeck(deckId: Long) {
        db.withTransaction {
            wordDao.deleteByDeck(deckId)
            deckDao.delete(deckId)
        }
    }

    suspend fun addWord(deckId: Long, foreign: String, translation: String): WordResult {
        val word = foreign.normalized()
        val meaning = translation.normalized()
        validateWord(word, meaning)?.let { return WordResult.Invalid(it) }
        return try {
            wordDao.insert(WordEntity(deckId = deckId, foreignWord = word, nativeTranslation = meaning))
            WordResult.Success
        } catch (e: SQLiteConstraintException) {
            WordResult.Duplicate
        }
    }

    suspend fun updateWord(original: WordEntity, foreign: String, translation: String): WordResult {
        val word = foreign.normalized()
        val meaning = translation.normalized()
        validateWord(word, meaning)?.let { return WordResult.Invalid(it) }
        return try {
            wordDao.update(original.copy(foreignWord = word, nativeTranslation = meaning))
            WordResult.Success
        } catch (e: SQLiteConstraintException) {
            WordResult.Duplicate
        }
    }

    suspend fun deleteWord(word: WordEntity) {
        wordDao.delete(word)
    }

    suspend fun restoreWord(word: WordEntity): Boolean = try {
        wordDao.insert(word)
        true
    } catch (e: SQLiteConstraintException) {
        false
    }

    /**
     * Imports pairs in one transaction. A "new" deck whose name already exists is merged into,
     * and words already in the target deck are skipped rather than overwritten.
     */
    suspend fun importPairs(target: ImportTarget, pairs: List<ImportedPair>): ImportResult {
        if (pairs.isEmpty()) return ImportResult.Invalid("There’s nothing to import yet.")
        return db.withTransaction {
            val deck: DeckEntity = when (target) {
                is ImportTarget.Existing -> deckDao.get(target.deckId)
                    ?: return@withTransaction ImportResult.Invalid("That deck no longer exists.")
                is ImportTarget.New -> {
                    val name = target.name.normalized()
                    validateDeckName(name)?.let { return@withTransaction ImportResult.Invalid(it) }
                    deckDao.findByName(name) ?: DeckEntity(name = name).let { it.copy(id = deckDao.insert(it)) }
                }
            }
            val ids = wordDao.insertAll(
                pairs.map {
                    WordEntity(
                        deckId = deck.id,
                        foreignWord = it.foreign.normalized(),
                        nativeTranslation = it.translation.normalized(),
                    )
                },
            )
            val added = ids.count { it != -1L }
            ImportResult.Success(ImportSummary(deck.id, deck.name, added, pairs.size - added))
        }
    }

    private fun validateWord(word: String, meaning: String): String? = when {
        word.isEmpty() || meaning.isEmpty() -> "Enter both the word and its translation."
        word.length > MAX_LENGTH || meaning.length > MAX_LENGTH -> "Keep each entry under $MAX_LENGTH characters."
        else -> null
    }

    private fun validateDeckName(name: String): String? = when {
        name.isEmpty() -> "Give the deck a name."
        name.length > MAX_DECK_NAME -> "Keep the deck name under $MAX_DECK_NAME characters."
        else -> null
    }

    private fun String.normalized(): String = trim().replace(WHITESPACE, " ")

    companion object {
        const val MAX_LENGTH = VocabImportParser.MAX_FIELD_LENGTH
        const val MAX_DECK_NAME = 40
        /** A word counts as learned after this many net correct answers. */
        const val LEARNED_THRESHOLD = 5
        private val WHITESPACE = Regex("\\s+")
    }
}
