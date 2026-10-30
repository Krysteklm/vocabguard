package com.vocabguard.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One card. A foreign word is unique per deck (case-insensitive), not globally. */
@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["deck_id", "foreign_word"], unique = true),
        Index(value = ["deck_id"]),
    ],
)
data class WordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "deck_id")
    val deckId: Long,

    @ColumnInfo(name = "foreign_word", collate = ColumnInfo.NOCASE)
    val foreignWord: String,

    @ColumnInfo(name = "native_translation")
    val nativeTranslation: String,

    @ColumnInfo(name = "correct_count", defaultValue = "0")
    val correctCount: Int = 0,

    @ColumnInfo(name = "last_reviewed")
    val lastReviewed: Long? = null,
)

/** Lightweight projection used for level estimation and duplicate checks. */
data class WordProgress(
    @ColumnInfo(name = "foreign_word") val foreignWord: String,
    @ColumnInfo(name = "correct_count") val correctCount: Int,
)
