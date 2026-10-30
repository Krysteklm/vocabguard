package com.vocabguard.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.appContainer
import com.vocabguard.sound.LocalSoundPlayer
import com.vocabguard.sound.SoundPlayer
import kotlinx.coroutines.launch
import com.vocabguard.ui.theme.VocabGuardTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val wordsViewModel: WordsViewModel by viewModels { WordsViewModel.Factory }
    private val appsViewModel: AppsViewModel by viewModels { AppsViewModel.Factory }
    private lateinit var soundPlayer: SoundPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        soundPlayer = SoundPlayer(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.settingsRepository.soundsEnabled.collect { soundPlayer.enabled = it }
            }
        }
        val uiScaleFlow = appContainer.settingsRepository.uiScale
        setContent {
            // Wait for the stored scale (a few ms) so the UI never jumps from 100% to the user's size.
            val uiScale by uiScaleFlow.collectAsStateWithLifecycle(initialValue = null)
            uiScale?.let { scale ->
                CompositionLocalProvider(LocalSoundPlayer provides soundPlayer) {
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
    }

    override fun onDestroy() {
        soundPlayer.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Permissions are granted in system Settings; re-check when the user comes back.
        mainViewModel.refreshPermissions()
        mainViewModel.runDailyAutoWords()
    }
}
