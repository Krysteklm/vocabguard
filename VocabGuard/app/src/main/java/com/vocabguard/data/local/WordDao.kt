package com.vocabguard.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT * FROM words WHERE deck_id = :deckId ORDER BY foreign_word ASC")
    fun observeByDeck(deckId: Long): Flow<List<WordEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM words
        INNER JOIN decks ON decks.id = words.deck_id
        WHERE decks.is_active = 1
        """
    )
    fun observeActiveCount(): Flow<Int>

    /** Weakest, least recently reviewed words from active decks first; RANDOM() breaks ties. */
    @Query(
        """
        SELECT words.* FROM words
        INNER JOIN decks ON decks.id = words.deck_id
        WHERE decks.is_active = 1 AND words.id != :excludeId
        ORDER BY words.correct_count ASC, COALESCE(words.last_reviewed, 0) ASC, RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getReviewCandidates(excludeId: Long, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE deck_id = :deckId AND id != :excludeId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomInDeck(deckId: Long, excludeId: Long, limit: Int): List<WordEntity>

    @Query(
        """
        SELECT words.* FROM words
        INNER JOIN decks ON decks.id = words.deck_id
        WHERE decks.is_active = 1 AND words.id != :excludeId
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomActive(excludeId: Long, limit: Int): List<WordEntity>

    @Query("SELECT foreign_word, correct_count FROM words")
    suspend fun getAllProgress(): List<WordProgress>

    @Query(
        """
        SELECT COUNT(*) FROM words
        INNER JOIN decks ON decks.id = words.deck_id
        WHERE decks.is_active = 1 AND words.correct_count < :learnedAt
        """
    )
    suspend fun countUnlearnedActive(learnedAt: Int): Int

    /** Throws SQLiteConstraintException on a duplicate in the same deck. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(word: WordEntity): Long

    /** Duplicates are skipped and reported as -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>): List<Long>

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(word: WordEntity): Int

    @Delete
    suspend fun delete(word: WordEntity): Int

    @Query("DELETE FROM words WHERE deck_id = :deckId")
    suspend fun deleteByDeck(deckId: Long)

    @Query("UPDATE words SET correct_count = correct_count + 1, last_reviewed = :timestamp WHERE id = :id")
    suspend fun markCorrect(id: Long, timestamp: Long)

    @Query("UPDATE words SET correct_count = MAX(correct_count - 1, 0), last_reviewed = :timestamp WHERE id = :id")
    suspend fun markIncorrect(id: Long, timestamp: Long)
}
