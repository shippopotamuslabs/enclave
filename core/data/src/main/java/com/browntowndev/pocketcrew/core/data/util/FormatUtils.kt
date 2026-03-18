package com.browntowndev.pocketcrew.core.data.util

import java.util.Locale

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1000 -> "$bytes B"
        bytes < 1000 * 1000 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1000.0)
        bytes < 1000 * 1000 * 1000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1000.0 * 1000))
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1000.0 * 1000 * 1000))
    }
}
