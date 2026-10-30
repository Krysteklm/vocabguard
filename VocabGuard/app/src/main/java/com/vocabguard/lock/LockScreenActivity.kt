package com.vocabguard.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocabguard.appContainer
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vocabguard.ui.theme.VocabGuardTheme

/**
 * Full-screen quiz shown over a guarded app. Back is swallowed in [LockScreenRoute]; Home and Recents
 * can't be blocked by any app, so leaving that way simply finishes the lock, and the service
 * shows it again the next time TikTok comes to the front.
 */
class LockScreenActivity : ComponentActivity() {

    private val viewModel: LockScreenViewModel by viewModels { LockScreenViewModel.Factory }
    private var leavingIntentionally = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        viewModel.bindTarget(intent?.getStringExtra(EXTRA_TARGET_PACKAGE))

        val uiScaleFlow = appContainer.settingsRepository.uiScale
        setContent {
            val uiScale by uiScaleFlow.collectAsStateWithLifecycle(initialValue = null)
            uiScale?.let { scale ->
                VocabGuardTheme(uiScale = scale) {
                    LockScreenRoute(
                        viewModel = viewModel,
                        onUnlocked = ::openTargetApp,
                        onExitHome = ::goHome,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.bindTarget(intent.getStringExtra(EXTRA_TARGET_PACKAGE))
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        // Cleared on pause (not stop) so a quick Recents -> TikTok switch is still caught.
        isShowing = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Left via Home/Recents: don't leave a stale quiz in the background.
        if (!isChangingConfigurations && !isFinishing && !leavingIntentionally) {
            finishAndRemoveTask()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun openTargetApp(targetPackage: String?) {
        leavingIntentionally = true
        val launchIntent = targetPackage?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't reopen $targetPackage", e)
            }
        }
        // Even without a launch intent, finishing reveals the task underneath (the guarded app).
        finishAndRemoveTask()
    }

    private fun goHome() {
        leavingIntentionally = true
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(home)
        } catch (e: Exception) {
            Log.w(TAG, "No home activity found", e)
        }
        finishAndRemoveTask()
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private const val TAG = "LockScreenActivity"
        private const val EXTRA_TARGET_PACKAGE = "com.vocabguard.extra.TARGET_PACKAGE"

        /** Read by the service as an extra loop guard. */
        @Volatile
        var isShowing: Boolean = false
            private set

        fun createIntent(context: Context, targetPackage: String): Intent =
            Intent(context, LockScreenActivity::class.java)
                .putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
    }
}
