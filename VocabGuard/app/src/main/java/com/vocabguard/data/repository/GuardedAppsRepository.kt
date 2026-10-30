package com.vocabguard.data.repository

import android.content.Context
import android.content.Intent
import com.vocabguard.data.local.GuardedAppDao
import com.vocabguard.data.local.GuardedAppEntity
import com.vocabguard.util.epochDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val packageName: String, val label: String)

class GuardedAppsRepository(
    private val context: Context,
    private val dao: GuardedAppDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Mirrors granted passes in memory: a failed disk write can't cause a lock loop. */
    private val memorySessions = ConcurrentHashMap<String, Long>()

    fun observeApps(): Flow<List<GuardedAppEntity>> = dao.observeAll()

    suspend fun get(packageName: String): GuardedAppEntity? = dao.get(packageName)

    fun sessionUntil(app: GuardedAppEntity): Long =
        maxOf(app.sessionUntil, memorySessions[app.packageName] ?: 0L)

    fun isSessionActive(app: GuardedAppEntity, now: Long = clock()): Boolean = sessionUntil(app) > now

    suspend fun addApps(apps: List<InstalledApp>): Int =
        dao.insertAll(apps.map { GuardedAppEntity(packageName = it.packageName, label = it.label) })
            .count { it != -1L }

    /** Re-adds a removed app with its previous rules (for Undo). */
    suspend fun restore(app: GuardedAppEntity) {
        dao.insertAll(listOf(app.copy(sessionUntil = 0L)))
    }

    suspend fun remove(packageName: String) {
        memorySessions.remove(packageName)
        dao.delete(packageName)
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        if (!enabled) memorySessions.remove(packageName)
        dao.setEnabled(packageName, enabled)
    }

    suspend fun updateRules(packageName: String, enabled: Boolean, streak: Int, minutes: Int, dailyLimit: Int) {
        if (!enabled) memorySessions.remove(packageName)
        dao.updateRules(
            packageName = packageName,
            enabled = enabled,
            streak = streak.coerceIn(GuardedAppEntity.STREAK_RANGE),
            minutes = minutes.coerceIn(1, MAX_SESSION_MINUTES),
            dailyLimit = dailyLimit.coerceIn(GuardedAppEntity.DAILY_LIMIT_RANGE),
        )
    }

    suspend fun grantSession(packageName: String, minutes: Int) {
        val now = clock()
        val until = now + minutes.coerceIn(1, MAX_SESSION_MINUTES) * 60_000L
        memorySessions[packageName] = until
        dao.grantSession(packageName, until, epochDay(now))
    }

    suspend fun endAllSessions() {
        memorySessions.clear()
        dao.endAllSessions()
    }

    /** Launchable apps (visible via the launcher <queries> entry; no QUERY_ALL_PACKAGES needed). */
    @Suppress("DEPRECATION")
    suspend fun loadInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private companion object {
        const val MAX_SESSION_MINUTES = 60
    }
}
