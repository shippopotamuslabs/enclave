package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.core.ui.component.markdown.StreamableMarkdownText

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

    /**
     * Configuration for displaying thinking/thought details.
     *
     * @param isVisible Whether the bottom sheet is visible.
     * @param content The markdown content to display (thinking raw text).
     * @param durationSeconds The duration of the thinking process in seconds.
     * @param onDismiss Callback to dismiss the bottom sheet.
     */
    data class Thinking(
        override val isVisible: Boolean,
        override val content: String,
        val durationSeconds: Long,
        override val onDismiss: () -> Unit
    ) : DetailBottomSheetConfig()

    /**
     * Configuration for displaying step completion details.
     *
     * @param isVisible Whether the bottom sheet is visible.
     * @param content The markdown content to display (step output).
     * @param stepName The name of the completed step.
     * @param onDismiss Callback to dismiss the bottom sheet.
     */
    data class StepCompletion(
        override val isVisible: Boolean,
        override val content: String,
        val stepName: String,
        override val onDismiss: () -> Unit
    ) : DetailBottomSheetConfig()
}

/**
 * Formats a thinking duration in seconds to a human-readable string.
 *
 * @param seconds The duration in seconds.
 * @return A formatted string (e.g., "42s" or "1m 30s").
 */
fun formatThinkingDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

/**
 * A reusable bottom sheet composable that displays detailed content with a configurable header.
 *
 * @param config The configuration for the bottom sheet, including visibility, content,
 *               and header details. Use [DetailBottomSheetConfig.Thinking] or
 *               [DetailBottomSheetConfig.StepCompletion] to configure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailBottomSheet(
    config: DetailBottomSheetConfig,
) {
    if (config.isVisible) {
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = config.onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = { WindowInsets.safeDrawing }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header row - content varies by config type
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (config) {
                        is DetailBottomSheetConfig.Thinking -> {
                            Icon(
                                painter = painterResource(R.drawable.lightbulb),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Thought for ${formatThinkingDuration(config.durationSeconds)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        is DetailBottomSheetConfig.StepCompletion -> {
                            Text(
                                text = "${config.stepName} Results",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content - markdown rendered text
                when (config) {
                    is DetailBottomSheetConfig.Thinking -> {
                        if (config.content.isNotBlank()) {
                            StreamableMarkdownText(
                                modifier = Modifier.fillMaxWidth(),
                                markdown = config.content,
                                isStreaming = true,
                                enableScroll = false,
                            )
                        } else {
                            Text(
                                text = "No thinking content available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is DetailBottomSheetConfig.StepCompletion -> {
                        StreamableMarkdownText(
                            modifier = Modifier.fillMaxWidth(),
                            markdown = config.content,
                            isStreaming = true,
                            enableScroll = false,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Re-export of [DetailBottomSheet] with [DetailBottomSheetConfig.Thinking] configuration.
 *
 * This composable is a convenience wrapper around [DetailBottomSheet] for displaying
 * thinking/thought process details. It is equivalent to calling:
 * ```
 * DetailBottomSheet(DetailBottomSheetConfig.Thinking(...))
 * ```
 *
 * @param isVisible Whether the bottom sheet is visible.
 * @param thinkingDurationSeconds The duration of the thinking process in seconds.
 * @param thinkingRaw The raw thinking content in markdown format.
 * @param onDismiss Callback to dismiss the bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingDetailsBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    thinkingDurationSeconds: Long,
    thinkingRaw: String,
) {
    DetailBottomSheet(
        config = DetailBottomSheetConfig.Thinking(
            isVisible = isVisible,
            content = thinkingRaw,
            durationSeconds = thinkingDurationSeconds,
            onDismiss = onDismiss,
        )
    )
}
