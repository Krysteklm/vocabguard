package com.vocabguard.data.local

import android.content.Context
import android.content.pm.PackageManager

object DefaultApps {
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val TIKTOK_ASIA = "com.ss.android.ugc.trill"

    /** Feeds shown at the top of the app picker when installed. */
    val SUGGESTED: Set<String> = setOf(
        TIKTOK,
        TIKTOK_ASIA,
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest",
    )

    /** TikTok is guarded out of the box (even before it's installed); the Asia build only if present. */
    fun seedTargets(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return buildList {
            add(TIKTOK to (labelOf(pm, TIKTOK) ?: "TikTok"))
            labelOf(pm, TIKTOK_ASIA)?.let { add(TIKTOK_ASIA to it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun labelOf(pm: PackageManager, packageName: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
