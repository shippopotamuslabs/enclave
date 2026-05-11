package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalUriHandler
import android.content.ClipData
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.core.ui.component.markdown.StreamableMarkdownText
import com.browntowndev.pocketcrew.feature.chat.ArtifactCardUiModel
import com.browntowndev.pocketcrew.feature.chat.IndicatorState
import com.browntowndev.pocketcrew.feature.chat.TranscriptSegmentUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Main composable ──

@Composable
fun AssistantResponse(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    isPreview: Boolean = false,
    hasTtsProviderAssigned: Boolean = false,
    /** When set with a non-empty transcript, banner is placed under leading thinking UI (see [toolCallBannerInsertAfterSegmentIndex]). */
    toolCallBanner: ToolCallBannerUi? = null,
    onPlayAudio: (String) -> Unit = {},
    onArtifactCardClick: (com.browntowndev.pocketcrew.domain.model.chat.MessageId, ArtifactCardUiModel) -> Unit = { _, _ -> },
    onNavigateToStudio: (ids: List<String>, uris: List<String>, types: List<String>, prompts: List<String>) -> Unit = { _, _, _, _ -> },
    onShareClick: (MessageId) -> Unit = {},
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    // Get content and pipeline step from ContentUi
    val contentText = message.content.text
    val pipelineStep = message.content.pipelineStep
    val isStreaming = message.indicatorState !is IndicatorState.Complete &&
                     message.indicatorState !is IndicatorState.None &&
                     !isPreview

    // State for step completion bottom sheet
    var showStepDetails by remember { mutableStateOf(false) }
    var selectedStepName by remember { mutableStateOf("") }
    var showSources by remember { mutableStateOf(false) }

    // Show bottom sheet if triggered
    if (showStepDetails) {
        DetailBottomSheet(
            config = DetailBottomSheetConfig.StepCompletion(
                isVisible = showStepDetails,
                content = contentText, // Always use live content from message
                stepName = selectedStepName,
                isStreaming = isStreaming,
                onDismiss = { showStepDetails = false }
            )
        )
    }

    if (showSources) {
        DetailBottomSheet(
            config = DetailBottomSheetConfig.Sources(
                isVisible = showSources,
                sources = message.webSearchSources,
                onDismiss = { showSources = false }
            )
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // For Crew mode: if this message has a pipeline step (non-FINAL), render as completed step
        // For FINAL or null pipelineStep: render as regular message content
        val isCompletedStep = pipelineStep != null && pipelineStep != PipelineStep.FINAL

        if (isCompletedStep) {
            // Render as completed step row
            CompletedStepRow(
                stepName = pipelineStep.displayName(),
                stepOutput = contentText,
                modelDisplayName = message.modelDisplayName,
                indicatorState = message.indicatorState,
                onClick = {
                    selectedStepName = pipelineStep.displayName()
                    showStepDetails = true
                }
            )
        } else {
            var transcriptSheetVisible by remember { mutableStateOf(false) }
            var transcriptSheetText by remember { mutableStateOf("") }
            var transcriptSheetDurationSeconds by remember { mutableStateOf(0L) }
            var transcriptSheetStreaming by remember { mutableStateOf(false) }
            var transcriptSheetElapsedSeconds by remember { mutableIntStateOf(0) }

            if (transcriptSheetVisible) {
                ThinkingDetailsBottomSheet(
                    isVisible = transcriptSheetVisible,
                    thinkingDurationSeconds = transcriptSheetDurationSeconds,
                    thinkingRaw = transcriptSheetText,
                    elapsedSeconds = transcriptSheetElapsedSeconds,
                    isStreaming = transcriptSheetStreaming,
                    onDismiss = { transcriptSheetVisible = false },
                )
            }

            fun openThinkingSheet(text: String, durationSeconds: Long?, streaming: Boolean, elapsedForComplete: Int) {
                transcriptSheetText = text
                transcriptSheetDurationSeconds = durationSeconds ?: 0L
                transcriptSheetStreaming = streaming
                transcriptSheetElapsedSeconds = elapsedForComplete
                transcriptSheetVisible = true
            }

            if (message.transcriptSegments.isNotEmpty()) {
                val insertAfterIndex = toolCallBannerInsertAfterSegmentIndex(message.transcriptSegments)
                val showBannerBeforeSegments = toolCallBanner != null && insertAfterIndex == null
                if (showBannerBeforeSegments) {
                    ToolCallBanner(
                        banner = toolCallBanner,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                message.transcriptSegments.forEachIndexed { index, segment ->
                    key(segment.ordinal) {
                        when (segment) {
                            is TranscriptSegmentUi.ThinkingBlock -> {
                                if (segment.isStreaming) {
                                    LiveThinkingTranscriptRow(
                                        segment = segment,
                                        isPreview = isPreview,
                                        onToggleDetails = {
                                            openThinkingSheet(
                                                text = segment.text,
                                                durationSeconds = null,
                                                streaming = true,
                                                elapsedForComplete = 0,
                                            )
                                        },
                                    )
                                } else {
                                    ThoughtForHeader(
                                        durationText = formatThinkingDuration(
                                            (segment.durationSeconds ?: 0L).toInt().coerceAtLeast(0),
                                        ),
                                        isExpanded = false,
                                        onViewFullThinking = {
                                            openThinkingSheet(
                                                text = segment.text,
                                                durationSeconds = segment.durationSeconds,
                                                streaming = false,
                                                elapsedForComplete = (segment.durationSeconds ?: 0L)
                                                    .toInt()
                                                    .coerceAtLeast(0),
                                            )
                                        },
                                    )
                                }
                            }
                            is TranscriptSegmentUi.AnswerBlock -> {
                                StreamableMarkdownText(
                                    markdown = segment.text,
                                    modifier = Modifier.fillMaxWidth(),
                                    enableScroll = false,
                                    isStreaming = segment.isStreaming && isStreaming,
                                    isPreview = isPreview,
                                )
                            }
                        }
                    }
                    if (toolCallBanner != null && insertAfterIndex == index) {
                        ToolCallBanner(
                            banner = toolCallBanner,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            } else {
                StreamableMarkdownText(
                    markdown = contentText,
                    modifier = Modifier.fillMaxWidth(),
                    enableScroll = false,
                    isStreaming = isStreaming,
                    isPreview = isPreview,
                )
            }

            // Artifact Cards Scrollable Row
            if (message.content.artifacts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(message.content.artifacts) { card ->
                        ArtifactCard(
                            card = card,
                            onClick = { onArtifactCardClick(message.id, card) },
                        )
                    }
                }
            }
            
            // Generated Media Card (Aggregated)
            if (message.content.generatedMediaUris.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                val mediaUris = message.content.generatedMediaUris
                val mediaIds = message.content.generatedMediaAssetIds
                val mediaTypes = message.content.generatedMediaTypes
                val mediaPrompts = message.content.generatedMediaPrompts

                MediaCard(
                    uris = mediaUris,
                    mediaTypes = mediaTypes,
                    prompts = mediaPrompts,
                    onClick = { u, t, p -> 
                        onNavigateToStudio(mediaIds, u, t, p) 
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            val isComplete = message.indicatorState is IndicatorState.Complete || 
                            message.indicatorState is IndicatorState.None || 
                            isPreview

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isComplete) {
                        // Share Button
                        IconButton(
                            onClick = { onShareClick(message.id) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Copy Button
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    clipboardManager.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("Pocket Crew Response", contentText)),
                                    )
                                }
                            },
                            modifier = Modifier
                                .offset(x = (-8).dp)
                                .size(32.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.content_copy),
                                contentDescription = "Copy response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        if (hasTtsProviderAssigned) {
                            // Speaker Button
                            IconButton(
                                onClick = { onPlayAudio(contentText) },
                                modifier = Modifier
                                    .offset(x = (-8).dp)
                                    .size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Read aloud",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Sources Pill Button
                        if (message.webSearchSources.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable { showSources = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.link),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sources",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        // Spacer to maintain layout if button is hidden
                        Spacer(modifier = Modifier.size(32.dp).offset(x = (-8).dp))
                    }
                }

                // Timestamp
                if (isComplete && message.formattedTimestamp.isNotBlank()) {
                    Text(
                        text = message.formattedTimestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveThinkingTranscriptRow(
    segment: TranscriptSegmentUi.ThinkingBlock,
    isPreview: Boolean,
    onToggleDetails: () -> Unit,
) {
    val startMs = segment.thinkingStartTimeMs ?: 0L
    var elapsedSeconds by remember(segment.ordinal) { mutableIntStateOf(0) }
    if (startMs > 0L && !isPreview) {
        LaunchedEffect(segment.ordinal, startMs) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                delay(1000L)
            }
        }
    }
    ThinkingIndicator(
        elapsedSeconds = elapsedSeconds,
        isExpanded = false,
        onToggleDetails = onToggleDetails,
    )
}

// ── Single completed step row (used for interleaved rendering) ──

@Composable
private fun CompletedStepRow(
    stepName: String,
    stepOutput: String,
    modelDisplayName: String,
    indicatorState: IndicatorState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only hide if we have no output AND we aren't actively working on it
    val isStepActive = indicatorState is IndicatorState.Processing ||
            indicatorState is IndicatorState.EngineLoading ||
            indicatorState is IndicatorState.Thinking ||
            indicatorState is IndicatorState.Generating
    if (stepOutput.isEmpty() && !isStepActive) return

    // Preview text - "Tap to View" as requested
    val previewText = "Tap to View"

    val statusText = when (indicatorState) {
        is IndicatorState.EngineLoading -> "I'm loading the engine from disk..."
        is IndicatorState.Processing -> "I'm processing your request now..."
        is IndicatorState.Thinking -> "I'm thinking about the next step..."
        is IndicatorState.Generating -> "I understand the request and I'm generating my response now."
        is IndicatorState.Complete, IndicatorState.None, null -> "I have completed my task. Here are the results."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Model message: "$modelDisplayName: $statusText"
        val completionMessage = remember(modelDisplayName, statusText) {
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(modelDisplayName)
                }
                append(": $statusText")
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = completionMessage,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .clickable { onClick() }
                .padding(horizontal = 12.dp)
        ) {
            // Document icon - Rectangular, rotated 10 degrees, shifted down slightly
            // Position extends below card so bottom gets clipped by container
            Box(
                modifier = Modifier
                    .offset(y = 12.dp)
                    .padding(start = 12.dp)
                    .rotate(-10f)
                    .size(width = 60.dp, height = 90.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.document),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            // Text content on the right - moved further right
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$stepName Results",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
@Composable
private fun ArtifactCard(
    card: ArtifactCardUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(280.dp)
            .height(110.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Document Preview / Icon
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                // PDF Label - The "Premium" touch
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.document),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = card.badgeLabel,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.ctaLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    uris: List<String>,
    mediaTypes: List<String>,
    prompts: List<String>,
    onClick: (uris: List<String>, types: List<String>, prompts: List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var loadError by remember { mutableStateOf(false) }
    val primaryUri = uris.firstOrNull() ?: ""
    val primaryType = mediaTypes.firstOrNull()
    val count = uris.size

    Surface(
        onClick = { if (!loadError) onClick(uris, mediaTypes, prompts) },
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            // Media Preview
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (loadError) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deleted",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    coil3.compose.AsyncImage(
                        model = primaryUri,
                        contentDescription = "Generated media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onError = { loadError = true },
                        error = painterResource(R.drawable.pocket_crew_icon)
                    )
                    
                    if (count > 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${count - 1}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                val title = if (loadError) "Media Missing" else {
                    val typeLabel = when (primaryType) {
                        "VIDEO" -> "Video"
                        "MUSIC" -> "Audio"
                        else -> "Image"
                    }
                    if (count > 1) "$count ${typeLabel}s Generated" else "Generated $typeLabel"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    ),
                    color = if (loadError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (loadError) "Deleted from storage" else "Available in Studio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (!loadError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Open in Studio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Where to insert an active [ToolCallBanner] relative to transcript segments so it sits **below**
 * the **latest** thinking UI (streaming [LiveThinkingTranscriptRow] or completed [ThoughtForHeader])
 * instead of above it in [MessageItem].
 *
 * @return index of the segment **after which** the banner is shown, or `null` if there is no
 * thinking row yet (e.g. answer-only transcript) so the banner stays above the first segment.
 */
internal fun toolCallBannerInsertAfterSegmentIndex(segments: List<TranscriptSegmentUi>): Int? {
    if (segments.isEmpty()) return null
    val lastThinkingIndex = segments.indexOfLast { it is TranscriptSegmentUi.ThinkingBlock }
    return if (lastThinkingIndex >= 0) lastThinkingIndex else null
}
