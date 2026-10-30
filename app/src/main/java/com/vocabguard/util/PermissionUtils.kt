package com.vocabguard.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.vocabguard.service.VocabAccessibilityService

object PermissionUtils {

    /** Reads the system's enabled-services list; works even before the service has bound. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, VocabAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry.trim()) == expected
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun accessibilitySettingsIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    /** Some OEM builds reject the package-scoped overlay screen, hence the fallbacks. */
    fun overlaySettingsIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        appDetailsIntent(context),
    )

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}

/** Starts the first intent the device can handle. Returns false if none could be opened. */
fun Context.startFirstAvailable(intents: List<Intent>): Boolean {
    for (intent in intents) {
        val launchable = if (this is Activity) intent else Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launchable)
            return true
        } catch (e: ActivityNotFoundException) {
            continue
        } catch (e: SecurityException) {
            continue
        }
    }
    return false
}
