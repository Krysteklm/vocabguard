package com.vocabguard.util

import java.time.Instant
import java.time.ZoneId

/** Local calendar day as an epoch-day number, used for "per day" counters. */
fun epochDay(millis: Long = System.currentTimeMillis()): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

/** 95_000 ms -> "1:35". */
fun formatCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
