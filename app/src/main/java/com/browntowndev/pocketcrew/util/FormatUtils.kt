package com.browntowndev.pocketcrew.util

import android.util.Log
import java.util.Locale

private const val TAG = "FormatUtils"

fun formatBytes(bytes: Long): String {
    // Log when bytes are converted to GB (for debugging size issues)
    // Use decimal (1000) not binary (1024) for user-friendly GB display
    if (bytes >= 1000L * 1000 * 1000) {
        val gbValue = bytes / (1000.0 * 1000 * 1000)
        Log.d(TAG, "[FORMAT_SIZE] Converting $bytes bytes -> ${String.format(Locale.getDefault(), "%.2f GB", gbValue)}")
    }
    return when {
        bytes < 1000 -> "$bytes B"
        bytes < 1000 * 1000 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1000.0)
        bytes < 1000 * 1000 * 1000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1000.0 * 1000))
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1000.0 * 1000 * 1000))
    }
}
