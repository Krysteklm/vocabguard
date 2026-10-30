package com.vocabguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Query(
        """
        SELECT decks.*,
               COUNT(words.id) AS word_count,
               COALESCE(SUM(CASE WHEN words.correct_count >= :learnedAt THEN 1 ELSE 0 END), 0) AS learned_count
        FROM decks
        LEFT JOIN words ON words.deck_id = decks.id
        GROUP BY decks.id
        ORDER BY decks.created_at ASC
        """
    )
    fun observeWithCounts(learnedAt: Int): Flow<List<DeckWithCount>>

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observe(id: Long): Flow<DeckEntity?>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun get(id: Long): DeckEntity?

    /** Case-insensitive thanks to the column's NOCASE collation. */
    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(deck: DeckEntity): Long

    @Query("UPDATE decks SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE decks SET is_active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun delete(id: Long)
}
