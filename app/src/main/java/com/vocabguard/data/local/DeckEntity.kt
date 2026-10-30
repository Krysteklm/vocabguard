package com.vocabguard.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A named word set ("zestaw"). Only active decks feed the quiz. */
@Entity(
    tableName = "decks",
    indices = [Index(value = ["name"], unique = true)],
)
data class DeckEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
    val name: String,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
)

data class DeckWithCount(
    @Embedded val deck: DeckEntity,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "learned_count") val learnedCount: Int,
)
