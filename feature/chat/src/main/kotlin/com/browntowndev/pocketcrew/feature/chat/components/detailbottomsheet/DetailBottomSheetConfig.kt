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
