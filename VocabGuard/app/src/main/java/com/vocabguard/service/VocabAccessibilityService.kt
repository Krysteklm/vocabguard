package com.vocabguard.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.vocabguard.appContainer
import com.vocabguard.data.local.GuardedAppEntity
import com.vocabguard.data.repository.GuardSettings
import com.vocabguard.lock.LockScreenActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Puts [LockScreenActivity] in front of every app the user chose to guard.
 *
 * Loop and false-positive guards:
 *  1. Our own package never triggers a lock.
 *  2. System UI, the "android" package and keyboards are ignored, so the notification shade or
 *     the keyboard inside a guarded app isn't treated as leaving and re-entering it.
 *  3. Only a *transition into* a guarded app triggers (in-app navigation fires the same event).
 *  4. Nothing triggers while the phone is locked, the screen is off, the app has an active pass,
 *     or the lock screen is already showing; a short debounce absorbs duplicate events.
 *
 * Everything runs on the main thread, so the mutable fields need no synchronisation.
 */
class VocabAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { appContainer }
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val transientPackages: Set<String> by lazy { resolveTransientPackages() }

    /** Cached copy of the guarded-apps table, kept fresh by [observeGuardedApps]. */
    private var guardedApps: Map<String, GuardedAppEntity> = emptyMap()

    private var foregroundPackage: String? = null
    private var lastLockLaunchAt = 0L
    private var lockRequestInFlight = false
    private var appsWatcher: Job? = null
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Waking the phone straight back into a guarded app counts as a fresh entry.
                Intent.ACTION_SCREEN_OFF -> foregroundPackage = null
                // The entry event may have arrived while the keyguard was still up; re-check.
                Intent.ACTION_USER_PRESENT -> foregroundPackage
                    ?.takeIf { guardedApps[it]?.isEnabled == true }
                    ?.let { requestLock(it) }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerScreenReceiver()
        observeGuardedApps()
        Log.i(TAG, "Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in transientPackages) return

        val previous = foregroundPackage
        foregroundPackage = pkg

        if (pkg == packageName) return
        val app = guardedApps[pkg] ?: return
        if (!app.isEnabled) return
        if (pkg == previous) return

        requestLock(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun requestLock(targetPackage: String) {
        if (lockRequestInFlight) return
        lockRequestInFlight = true
        serviceScope.launch {
            try {
                // Fail closed on the global switch, but never lock an app we can't confirm is guarded.
                val settings = attempt { container.settingsRepository.current() } ?: GuardSettings()
                val app = attempt { container.guardedAppsRepository.get(targetPackage) } ?: guardedApps[targetPackage]
                if (app != null && foregroundPackage == targetPackage && shouldLock(settings, app)) {
                    launchLockScreen(targetPackage)
                }
            } finally {
                lockRequestInFlight = false
            }
        }
    }

    private fun shouldLock(settings: GuardSettings, app: GuardedAppEntity): Boolean = when {
        !settings.blockerEnabled -> false
        !app.isEnabled -> false
        container.guardedAppsRepository.isSessionActive(app) -> false
        LockScreenActivity.isShowing -> false
        keyguardManager?.isKeyguardLocked == true -> false
        powerManager?.isInteractive == false -> false
        SystemClock.elapsedRealtime() - lastLockLaunchAt < RELAUNCH_DEBOUNCE_MS -> false
        else -> true
    }

    private fun launchLockScreen(targetPackage: String) {
        lastLockLaunchAt = SystemClock.elapsedRealtime()
        val intent = LockScreenActivity.createIntent(this, targetPackage).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't show the lock screen", e)
        }
    }

    /** Keeps the cache fresh and re-locks an app whose pass runs out while it's still open. */
    private fun observeGuardedApps() {
        appsWatcher?.cancel()
        appsWatcher = serviceScope.launch {
            container.guardedAppsRepository.observeApps()
                .catch { e -> Log.e(TAG, "Guarded apps unavailable", e) }
                .collectLatest { apps ->
                    guardedApps = apps.associateBy { it.packageName }
                    relockWhenPassesExpire(apps)
                }
        }
    }

    private suspend fun relockWhenPassesExpire(apps: List<GuardedAppEntity>) {
        val repo = container.guardedAppsRepository
        val passes = apps.filter { it.isEnabled }.associate { it.packageName to repo.sessionUntil(it) }
        while (true) {
            val now = System.currentTimeMillis()
            val nextExpiry = passes.values.filter { it > now }.minOrNull() ?: return
            delay(nextExpiry - now + PASS_EXPIRY_GRACE_MS)
            val current = foregroundPackage ?: continue
            val until = passes[current] ?: continue
            if (until <= System.currentTimeMillis()) requestLock(current)
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Read failed", e)
        null
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Both are protected system broadcasts, so other apps can't spoof them.
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun resolveTransientPackages(): Set<String> {
        val keyboards = try {
            getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.map { it.packageName }
                .orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        return buildSet {
            add("com.android.systemui")
            add("android")
            addAll(keyboards)
        }
    }

    private companion object {
        const val TAG = "VocabGuardService"
        const val RELAUNCH_DEBOUNCE_MS = 700L
        const val PASS_EXPIRY_GRACE_MS = 500L
    }
}
