package com.vocabguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GuardedAppDao {

    @Query("SELECT * FROM guarded_apps ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GuardedAppEntity>>

    @Query("SELECT * FROM guarded_apps WHERE package_name = :packageName LIMIT 1")
    suspend fun get(packageName: String): GuardedAppEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<GuardedAppEntity>): List<Long>

    @Query("DELETE FROM guarded_apps WHERE package_name = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE guarded_apps SET is_enabled = :enabled WHERE package_name = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    @Query(
        """
        UPDATE guarded_apps
        SET is_enabled = :enabled, required_streak = :streak,
            session_minutes = :minutes, daily_unlock_limit = :dailyLimit
        WHERE package_name = :packageName
        """
    )
    suspend fun updateRules(packageName: String, enabled: Boolean, streak: Int, minutes: Int, dailyLimit: Int)

    /** One atomic statement: SQLite evaluates the CASE against the old unlocks_day. */
    @Query(
        """
        UPDATE guarded_apps
        SET session_until = :until,
            unlocks_today = CASE WHEN unlocks_day = :today THEN unlocks_today + 1 ELSE 1 END,
            unlocks_day = :today
        WHERE package_name = :packageName
        """
    )
    suspend fun grantSession(packageName: String, until: Long, today: Long)

    @Query("UPDATE guarded_apps SET session_until = 0")
    suspend fun endAllSessions()
}
