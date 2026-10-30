package com.vocabguard.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** An app VocabGuard puts a quiz in front of, with its own rules and today's usage. */
@Entity(tableName = "guarded_apps")
data class GuardedAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "is_enabled", defaultValue = "1")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "required_streak", defaultValue = "2")
    val requiredStreak: Int = DEFAULT_STREAK,

    @ColumnInfo(name = "session_minutes", defaultValue = "2")
    val sessionMinutes: Int = DEFAULT_SESSION_MINUTES,

    /** 0 = unlimited. */
    @ColumnInfo(name = "daily_unlock_limit", defaultValue = "0")
    val dailyUnlockLimit: Int = 0,

    @ColumnInfo(name = "session_until", defaultValue = "0")
    val sessionUntil: Long = 0L,

    @ColumnInfo(name = "unlocks_today", defaultValue = "0")
    val unlocksToday: Int = 0,

    /** Epoch day that [unlocksToday] belongs to. */
    @ColumnInfo(name = "unlocks_day", defaultValue = "0")
    val unlocksDay: Long = 0L,

    @ColumnInfo(name = "added_at", defaultValue = "0")
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun hasDailyLimit(): Boolean = dailyUnlockLimit > 0

    fun unlocksUsed(today: Long): Int = if (unlocksDay == today) unlocksToday else 0

    /** null when unlimited. */
    fun unlocksLeft(today: Long): Int? =
        if (hasDailyLimit()) (dailyUnlockLimit - unlocksUsed(today)).coerceAtLeast(0) else null

    companion object {
        const val DEFAULT_STREAK = 2
        const val DEFAULT_SESSION_MINUTES = 2
        val STREAK_RANGE = 1..5
        val SESSION_MINUTE_OPTIONS = listOf(1, 2, 3, 5, 10, 15, 20, 30)
        val DAILY_LIMIT_RANGE = 0..20
    }
}
