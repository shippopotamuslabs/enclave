package com.browntowndev.pocketcrew.feature.chat.components.detailbottomsheet

import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource

/**
 * Configuration for [DetailBottomSheet].
 *
 * Provides a type-safe way to configure the bottom sheet header and content
 * for different use cases (e.g., thinking details, step completion).
 */
sealed class DetailBottomSheetConfig {
    abstract val content: String
    abstract val isVisible: Boolean
    abstract val onDismiss: () -> Unit
    abstract val isStreaming: Boolean

    data class Thinking(
        override val isVisible: Boolean,
        override val content: String,
        val durationSeconds: Long,
        override val onDismiss: () -> Unit,
        override val isStreaming: Boolean = false,
    ) : DetailBottomSheetConfig()

    data class StepCompletion(
        override val isVisible: Boolean,
        override val content: String,
        val stepName: String,
        override val onDismiss: () -> Unit,
        override val isStreaming: Boolean = false,
    ) : DetailBottomSheetConfig()

    data class Sources(
        override val isVisible: Boolean,
        val sources: List<WebSearchSource>,
        override val onDismiss: () -> Unit,
        override val content: String = "",
        override val isStreaming: Boolean = false,
    ) : DetailBottomSheetConfig()
}

/** Formats a thinking duration in seconds to a human-readable string (e.g. "42s" or "1m 30s"). */
fun formatSheetThinkingDuration(seconds: Long): String =
    when {
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }

/**
 * Title for the thinking detail sheet header.
 * Active thinking uses "Thinking" (optionally with a live elapsed counter); completed uses "Thought for …".
 */
fun thinkingSheetHeaderTitle(
    isStreaming: Boolean,
    durationSeconds: Long,
): String =
    if (isStreaming) {
        if (durationSeconds > 0L) {
            "Thinking • ${formatSheetThinkingDuration(durationSeconds)}"
        } else {
            "Thinking"
        }
    } else {
        "Thought for ${formatSheetThinkingDuration(durationSeconds)}"
    }

internal fun thinkingSheetDisplayDuration(
    isStreaming: Boolean,
    durationSeconds: Long,
    elapsedSeconds: Int,
): Long =
    when {
        isStreaming -> {
            val elapsed = elapsedSeconds.toLong()
            if (elapsed > 0L) elapsed else durationSeconds
        }
        durationSeconds == 0L && elapsedSeconds > 0 -> elapsedSeconds.toLong()
        else -> durationSeconds
    }
