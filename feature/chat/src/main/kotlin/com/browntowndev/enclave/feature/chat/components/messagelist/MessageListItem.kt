package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import com.browntowndev.enclave.feature.chat.components.EngineLoadingIndicator
import com.browntowndev.enclave.feature.chat.components.GeneratingIndicator
import com.browntowndev.enclave.feature.chat.components.ProcessingIndicator
import com.browntowndev.enclave.feature.chat.components.assistantresponse.AssistantResponse
import com.browntowndev.enclave.feature.chat.components.detailbottomsheet.ThinkingDetailsBottomSheet
import com.browntowndev.enclave.feature.chat.components.messagebubble.MessageBubble
import com.browntowndev.enclave.feature.chat.components.thinkingindicator.ThinkingIndicator
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** Reserved height for [GeneratingIndicator] so completion does not collapse layout and shift scroll. */
private val CrewGeneratingIndicatorReservedHeight = 62.dp

private enum class MessageListIndicatorPlacement {
    TopLevel,
    TranscriptTrailing,
}

@Composable
internal fun MessageListItem(
    message: ChatMessage,
    activeToolCallBanner: ToolCallBannerUi?,
    hasTtsProviderAssigned: Boolean,
    chatMode: ChatModeUi,
    callbacks: MessageListCallbacks,
    isPreview: Boolean,
    modifier: Modifier = Modifier,
) {
    var reserveCrewGeneratingSlot by remember(message.id) { mutableStateOf(value = false) }
    LaunchedEffect(message.id, chatMode, message.indicatorState) {
        if ((chatMode == ChatModeUi.COUNCIL) && (message.indicatorState is IndicatorState.Generating)) {
            reserveCrewGeneratingSlot = true
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding),
    ) {
        val hasTranscriptSegments = message.transcriptSegments.isNotEmpty()
        val hasStreamingThinkingSegment =
            message.transcriptSegments.any { segment ->
                (segment is TranscriptSegmentUi.ThinkingBlock) && segment.isStreaming
            }
        if (!hasTranscriptSegments) {
            MessageListItemIndicators(
                modelDisplayName = message.modelDisplayName,
                indicatorState = message.indicatorState,
                activeToolCallBanner = activeToolCallBanner,
                chatMode = chatMode,
                suppressDuplicateThoughtForHeader = false,
                hasTranscriptSegments = false,
                hasStreamingThinkingSegment = false,
                placement = MessageListIndicatorPlacement.TopLevel,
                reserveCrewGeneratingSlot = reserveCrewGeneratingSlot,
                isPreview = isPreview,
            )
        }

        if (message.role == MessageRole.User) {
            MessageBubble(
                message = message,
                onEditClick = callbacks.onEditMessage,
            )
        } else {
            AssistantResponse(
                message = message,
                isPreview = isPreview,
                hasTtsProviderAssigned = hasTtsProviderAssigned,
                toolCallBanner = activeToolCallBanner,
                trailingStatusIndicator =
                    if (hasTranscriptSegments) {
                        {
                            MessageListItemIndicators(
                                modelDisplayName = message.modelDisplayName,
                                indicatorState = message.indicatorState,
                                activeToolCallBanner = activeToolCallBanner,
                                chatMode = chatMode,
                                suppressDuplicateThoughtForHeader = true,
                                hasTranscriptSegments = true,
                                hasStreamingThinkingSegment = hasStreamingThinkingSegment,
                                placement = MessageListIndicatorPlacement.TranscriptTrailing,
                                reserveCrewGeneratingSlot = reserveCrewGeneratingSlot,
                                isPreview = isPreview,
                            )
                        }
                    } else {
                        null
                    },
                onPlayAudio = callbacks.onPlayAudio,
                onArtifactCardClick = callbacks.onArtifactCardClick,
                onNavigateToStudio = callbacks.onNavigateToStudio,
                onShareClick = callbacks.onShareClick,
            )
        }
    }
}

@Composable
private fun MessageListItemIndicators(
    modelDisplayName: String,
    indicatorState: IndicatorState?,
    activeToolCallBanner: ToolCallBannerUi?,
    chatMode: ChatModeUi,
    suppressDuplicateThoughtForHeader: Boolean,
    hasTranscriptSegments: Boolean,
    hasStreamingThinkingSegment: Boolean,
    placement: MessageListIndicatorPlacement,
    reserveCrewGeneratingSlot: Boolean,
    isPreview: Boolean,
) {
    if ((indicatorState == null) && (activeToolCallBanner == null)) return

    var isThinkingSheetVisible by remember { mutableStateOf(value = false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    val startTime = indicatorState?.let(::indicatorThinkingStartTime) ?: 0L
    val isThinkingActive = indicatorState?.let(::isIndicatorThinkingActive) ?: false

    if ((startTime > 0) && isThinkingActive) {
        LaunchedEffect(startTime) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                delay(1.seconds)
            }
        }
    }

    LaunchedEffect(indicatorState) {
        if ((indicatorState != null) && !isThinkingActive) {
            elapsedSeconds = settledIndicatorElapsedSeconds(indicatorState)
        }
    }

    MessageListIndicatorBody(
        modelDisplayName = modelDisplayName,
        indicatorState = indicatorState,
        activeToolCallBanner = activeToolCallBanner,
        chatMode = chatMode,
        suppressDuplicateThoughtForHeader = suppressDuplicateThoughtForHeader,
        hasTranscriptSegments = hasTranscriptSegments,
        hasStreamingThinkingSegment = hasStreamingThinkingSegment,
        placement = placement,
        reserveCrewGeneratingSlot = reserveCrewGeneratingSlot,
        elapsedSeconds = elapsedSeconds,
        isThinkingSheetVisible = isThinkingSheetVisible,
    ) {
        isThinkingSheetVisible = !isThinkingSheetVisible
    }

    if (isThinkingSheetVisible && (indicatorState != null)) {
        ThinkingDetailsBottomSheet(
            isVisible = true,
            thinkingRaw = indicatorThinkingRawForSheet(indicatorState),
            thinkingDurationSeconds = indicatorThinkingDurationForSheet(indicatorState),
            elapsedSeconds = elapsedSeconds,
            isStreaming = isIndicatorStreaming(indicatorState),
            onDismiss = { isThinkingSheetVisible = false },
            isPreview = isPreview,
        )
    }
}

@Composable
private fun MessageListIndicatorBody(
    modelDisplayName: String,
    indicatorState: IndicatorState?,
    activeToolCallBanner: ToolCallBannerUi?,
    chatMode: ChatModeUi,
    suppressDuplicateThoughtForHeader: Boolean,
    hasTranscriptSegments: Boolean,
    hasStreamingThinkingSegment: Boolean,
    placement: MessageListIndicatorPlacement,
    reserveCrewGeneratingSlot: Boolean,
    elapsedSeconds: Int,
    isThinkingSheetVisible: Boolean,
    onToggleThinkingSheet: () -> Unit,
) {
    if (activeToolCallBanner != null) {
        if (
            (placement == MessageListIndicatorPlacement.TranscriptTrailing) &&
            !showsTrailingActiveToolCall(hasStreamingThinkingSegment)
        ) {
            return
        }
        ActiveToolCallIndicator(
            indicatorState = indicatorState,
            activeToolCallBanner = activeToolCallBanner,
            elapsedSeconds = elapsedSeconds,
            isThinkingSheetVisible = isThinkingSheetVisible,
            onToggleThinkingSheet = onToggleThinkingSheet,
        )
        return
    }

    when (indicatorState) {
        is IndicatorState.EngineLoading -> {
            if (
                (placement == MessageListIndicatorPlacement.TopLevel) &&
                messageListShowsTopLevelStatusIndicator(indicatorState, hasTranscriptSegments)
            ) {
                EngineLoadingIndicator(modelDisplayName = modelDisplayName)
            }
        }
        is IndicatorState.Thinking -> {
            val showThinkingIndicator =
                when (placement) {
                    MessageListIndicatorPlacement.TopLevel ->
                        transcriptLayoutShowsTopThinkingIndicator(indicatorState, suppressDuplicateThoughtForHeader)
                    MessageListIndicatorPlacement.TranscriptTrailing ->
                        transcriptLayoutShowsTrailingStatusIndicator(indicatorState, hasStreamingThinkingSegment)
                }
            if (showThinkingIndicator) {
                ThinkingIndicator(
                    elapsedSeconds = elapsedSeconds,
                    isExpanded = isThinkingSheetVisible,
                    onToggleDetails = onToggleThinkingSheet,
                )
            }
        }
        is IndicatorState.Processing -> {
            val showProcessingIndicator =
                when (placement) {
                    MessageListIndicatorPlacement.TopLevel ->
                        messageListShowsTopLevelStatusIndicator(indicatorState, hasTranscriptSegments)
                    MessageListIndicatorPlacement.TranscriptTrailing ->
                        transcriptLayoutShowsTrailingStatusIndicator(indicatorState, hasStreamingThinkingSegment)
                }
            if (showProcessingIndicator) {
                ProcessingIndicator()
            }
        }
        is IndicatorState.Generating -> {
            if (placement == MessageListIndicatorPlacement.TopLevel) {
                GeneratingIndicatorSection(
                    indicatorState = indicatorState,
                    chatMode = chatMode,
                    suppressDuplicateThoughtForHeader = suppressDuplicateThoughtForHeader,
                    elapsedSeconds = elapsedSeconds,
                    isThinkingSheetVisible = isThinkingSheetVisible,
                    onToggleThinkingSheet = onToggleThinkingSheet,
                )
            }
        }
        is IndicatorState.Complete -> {
            if (placement == MessageListIndicatorPlacement.TopLevel) {
                CompleteIndicatorSection(
                    indicatorState = indicatorState,
                    suppressDuplicateThoughtForHeader = suppressDuplicateThoughtForHeader,
                    reserveCrewGeneratingSlot = reserveCrewGeneratingSlot,
                    elapsedSeconds = elapsedSeconds,
                    isThinkingSheetVisible = isThinkingSheetVisible,
                    onToggleThinkingSheet = onToggleThinkingSheet,
                )
            }
        }
        is IndicatorState.None, null -> Unit
    }
}

@Composable
private fun ActiveToolCallIndicator(
    indicatorState: IndicatorState?,
    activeToolCallBanner: ToolCallBannerUi,
    elapsedSeconds: Int,
    isThinkingSheetVisible: Boolean,
    onToggleThinkingSheet: () -> Unit,
) {
    if (indicatorState is IndicatorState.Thinking) {
        ThinkingIndicator(
            elapsedSeconds = elapsedSeconds,
            isExpanded = isThinkingSheetVisible,
            toolCallBanner = activeToolCallBanner,
            onToggleDetails = onToggleThinkingSheet,
        )
    } else {
        ProcessingIndicator(toolCallBanner = activeToolCallBanner)
    }
}

@Composable
private fun GeneratingIndicatorSection(
    indicatorState: IndicatorState.Generating,
    chatMode: ChatModeUi,
    suppressDuplicateThoughtForHeader: Boolean,
    elapsedSeconds: Int,
    isThinkingSheetVisible: Boolean,
    onToggleThinkingSheet: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (transcriptLayoutShowsAggregateThoughtForHeader(indicatorState, suppressDuplicateThoughtForHeader)) {
            ThoughtForHeader(
                modifier = Modifier.padding(top = 8.dp),
                durationText = formatThinkingDuration(elapsedSeconds),
                isExpanded = isThinkingSheetVisible,
                onViewFullThinking = onToggleThinkingSheet,
            )
        }

        if (chatMode == ChatModeUi.COUNCIL) {
            GeneratingIndicator()
        }
    }
}

@Composable
private fun CompleteIndicatorSection(
    indicatorState: IndicatorState.Complete,
    suppressDuplicateThoughtForHeader: Boolean,
    reserveCrewGeneratingSlot: Boolean,
    elapsedSeconds: Int,
    isThinkingSheetVisible: Boolean,
    onToggleThinkingSheet: () -> Unit,
) {
    if (transcriptLayoutShowsAggregateThoughtForHeader(indicatorState, suppressDuplicateThoughtForHeader)) {
        ThoughtForHeader(
            modifier = Modifier.padding(top = 8.dp),
            durationText = formatThinkingDuration(elapsedSeconds),
            isExpanded = isThinkingSheetVisible,
            onViewFullThinking = onToggleThinkingSheet,
        )
    }
    if (reserveCrewGeneratingSlot) {
        Spacer(modifier = Modifier.height(CrewGeneratingIndicatorReservedHeight))
    }
}
