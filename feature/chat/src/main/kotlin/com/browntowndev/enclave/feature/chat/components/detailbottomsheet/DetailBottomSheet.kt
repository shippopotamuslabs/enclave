package com.browntowndev.enclave.feature.chat.components.detailbottomsheet

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.markdown.StreamableMarkdownText
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailBottomSheet(
    config: DetailBottomSheetConfig,
    elapsedSeconds: Int = 0,
) {
    if (!config.isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current

    val displayDuration =
        when (config) {
            is DetailBottomSheetConfig.Thinking -> {
                thinkingSheetDisplayDuration(
                    isStreaming = config.isStreaming,
                    durationSeconds = config.durationSeconds,
                    elapsedSeconds = elapsedSeconds,
                )
            }
            else -> 0L
        }

    NeoModalBottomSheet(
        onDismissRequest = config.onDismiss,
        sheetState = sheetState,
    ) {
        val title =
            when (config) {
                is DetailBottomSheetConfig.Thinking -> {
                    thinkingSheetHeaderTitle(
                        isStreaming = config.isStreaming,
                        durationSeconds = displayDuration,
                    )
                }
                is DetailBottomSheetConfig.StepCompletion -> {
                    "${config.stepName} Results"
                }
                is DetailBottomSheetConfig.Sources -> {
                    "Sources"
                }
            }

        NeoSheetHeader(
            title = title,
            onDismiss = config.onDismiss,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
        ) {
            item {
                DetailBottomSheetBody(
                    config = config,
                    onSourceClick = { url -> uriHandler.openUri(url) },
                )
            }
        }
    }
}

@Composable
private fun DetailBottomSheetBody(
    config: DetailBottomSheetConfig,
    onSourceClick: (String) -> Unit,
) {
    when (config) {
        is DetailBottomSheetConfig.Thinking -> {
            if (config.content.isNotBlank()) {
                StreamableMarkdownText(
                    modifier = Modifier.fillMaxWidth(),
                    markdown = config.content,
                    isStreaming = config.isStreaming,
                    enableScroll = false,
                )
            } else {
                Text(
                    text = "No thinking content available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        is DetailBottomSheetConfig.StepCompletion -> {
            StreamableMarkdownText(
                modifier = Modifier.fillMaxWidth(),
                markdown = config.content,
                isStreaming = config.isStreaming,
                enableScroll = false,
            )
        }
        is DetailBottomSheetConfig.Sources -> {
            DetailBottomSheetSourcesContent(
                sources = config.sources,
                onSourceClick = onSourceClick,
            )
        }
    }
}

/**
 * Requests the screen-level [DetailBottomSheet] via [LocalDetailBottomSheetController].
 * Safe to call from list rows; does not compose a sheet in place.
 */
@Composable
fun ThinkingDetailsBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    thinkingDurationSeconds: Long,
    thinkingRaw: String,
    elapsedSeconds: Int = 0,
    isStreaming: Boolean = false,
    isPreview: Boolean = false,
) {
    val controller = rememberDetailBottomSheetController(usePreviewStub = isPreview)

    LaunchedEffect(isVisible, thinkingRaw, thinkingDurationSeconds, isStreaming) {
        if (isVisible) {
            controller.show(
                DetailBottomSheetConfig.Thinking(
                    isVisible = true,
                    content = thinkingRaw,
                    durationSeconds = thinkingDurationSeconds,
                    onDismiss = onDismiss,
                    isStreaming = isStreaming,
                ),
                elapsedSeconds = elapsedSeconds,
            )
        }
    }

    LaunchedEffect(elapsedSeconds) {
        if (isVisible) {
            controller.updateElapsed(elapsedSeconds)
        }
    }
}
