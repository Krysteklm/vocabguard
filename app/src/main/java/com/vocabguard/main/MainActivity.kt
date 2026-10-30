package com.vocabguard.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.appContainer
import com.vocabguard.ui.theme.VocabGuardTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val wordsViewModel: WordsViewModel by viewModels { WordsViewModel.Factory }
    private val appsViewModel: AppsViewModel by viewModels { AppsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uiScaleFlow = appContainer.settingsRepository.uiScale
        setContent {
            // Wait for the stored scale (a few ms) so the UI never jumps from 100% to the user's size.
            val uiScale by uiScaleFlow.collectAsStateWithLifecycle(initialValue = null)
            uiScale?.let { scale ->
                VocabGuardTheme(uiScale = scale) {
                    MainScreen(
                        mainViewModel = mainViewModel,
                        wordsViewModel = wordsViewModel,
                        appsViewModel = appsViewModel,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions are granted in system Settings; re-check when the user comes back.
        mainViewModel.refreshPermissions()
        mainViewModel.runDailyAutoWords()
    }
}
