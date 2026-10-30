package com.vocabguard

import android.app.Application
import android.content.Context
import com.vocabguard.data.local.AppDatabase
import com.vocabguard.data.repository.AutoWordsRepository
import com.vocabguard.data.repository.GuardedAppsRepository
import com.vocabguard.data.repository.QuizRepository
import com.vocabguard.data.repository.SettingsRepository
import com.vocabguard.data.repository.WordRepository
import com.vocabguard.data.repository.guardDataStore

class VocabGuardApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Minimal manual DI shared by the activities and the accessibility service (same process). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val wordRepository: WordRepository by lazy { WordRepository(database) }
    val quizRepository: QuizRepository by lazy { QuizRepository(database.wordDao()) }
    val guardedAppsRepository: GuardedAppsRepository by lazy {
        GuardedAppsRepository(appContext, database.guardedAppDao())
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext.guardDataStore) }
    val autoWordsRepository: AutoWordsRepository by lazy { AutoWordsRepository(database, settingsRepository) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VocabGuardApp).container
