package com.vocabguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DeckEntity::class, WordEntity::class, GuardedAppEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun wordDao(): WordDao
    abstract fun guardedAppDao(): GuardedAppDao

    companion object {
        private const val DB_NAME = "vocabguard.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addCallback(SeedCallback(context))
                // v1 only ever existed as an unreleased test build, so it may be rebuilt.
                // From v2 on, every schema change needs a real Migration so user decks survive.
                .fallbackToDestructiveMigrationFrom(1)
                .build()
    }
}

/**
 * Seeds the starter deck and default guarded apps with raw SQL on the opening connection
 * (DAOs would deadlock here). Idempotent thanks to INSERT OR IGNORE, so it's safe in both hooks.
 */
private class SeedCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed(db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR IGNORE INTO decks (id, name, is_active, created_at) VALUES (?, ?, 1, ?)",
                arrayOf<Any?>(StarterPack.DECK_ID, StarterPack.DECK_NAME, now),
            )
            StarterPack.words.forEach { (foreign, native) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO words (deck_id, foreign_word, native_translation, correct_count, last_reviewed) " +
                        "VALUES (?, ?, ?, 0, NULL)",
                    arrayOf<Any?>(StarterPack.DECK_ID, foreign, native),
                )
            }
            DefaultApps.seedTargets(context).forEach { (packageName, label) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO guarded_apps (package_name, label, is_enabled, required_streak, " +
                        "session_minutes, daily_unlock_limit, session_until, unlocks_today, unlocks_day, added_at) " +
                        "VALUES (?, ?, 1, ?, ?, 0, 0, 0, 0, ?)",
                    arrayOf<Any?>(
                        packageName,
                        label,
                        GuardedAppEntity.DEFAULT_STREAK,
                        GuardedAppEntity.DEFAULT_SESSION_MINUTES,
                        now,
                    ),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
