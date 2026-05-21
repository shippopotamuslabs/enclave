package com.browntowndev.pocketcrew.feature.chat.components.detailbottomsheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.core.ui.component.markdown.StreamableMarkdownText
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.feature.chat.R

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

    JumpFreeModalBottomSheet(
        onDismissRequest = config.onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
        ) {
            DetailBottomSheetHeader(config = config, displayDuration = displayDuration)

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
}

@Composable
private fun DetailBottomSheetHeader(
    config: DetailBottomSheetConfig,
    displayDuration: Long,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when (config) {
            is DetailBottomSheetConfig.Thinking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lightbulb),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text =
                            thinkingSheetHeaderTitle(
                                isStreaming = config.isStreaming,
                                durationSeconds = displayDuration,
                            ),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is DetailBottomSheetConfig.StepCompletion -> {
                Text(
                    text = "${config.stepName} Results",
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is DetailBottomSheetConfig.Sources -> {
                Text(
                    text = "Sources",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp),
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
) {
    val controller = LocalDetailBottomSheetController.current

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
