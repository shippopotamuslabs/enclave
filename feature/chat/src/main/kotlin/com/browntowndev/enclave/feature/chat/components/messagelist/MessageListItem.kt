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
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
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
                .padding(horizontal = 10.dp),
    ) {
        MessageListItemIndicators(
            modelDisplayName = message.modelDisplayName,
            indicatorState = message.indicatorState,
            chatMode = chatMode,
            suppressDuplicateThoughtForHeader = message.transcriptSegments.isNotEmpty(),
            reserveCrewGeneratingSlot = reserveCrewGeneratingSlot,
        )

        val inlineToolCallBannerInTranscript = usesInlineToolCallBannerInTranscript(message)

        StandaloneToolCallBannerRow(
            message = message,
            activeToolCallBanner = activeToolCallBanner,
        )

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
                toolCallBanner = if (inlineToolCallBannerInTranscript) activeToolCallBanner else null,
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
    chatMode: ChatModeUi,
    suppressDuplicateThoughtForHeader: Boolean,
    reserveCrewGeneratingSlot: Boolean,
) {
    if (indicatorState == null) return

    var isThinkingSheetVisible by remember { mutableStateOf(value = false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    val startTime = indicatorThinkingStartTime(indicatorState)
    val isThinkingActive = isIndicatorThinkingActive(indicatorState)

    if ((startTime > 0) && isThinkingActive) {
        LaunchedEffect(startTime) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                delay(1.seconds)
            }
        }
    }

    LaunchedEffect(indicatorState) {
        if (!isThinkingActive) {
            elapsedSeconds = settledIndicatorElapsedSeconds(indicatorState)
        }
    }

    MessageListIndicatorBody(
        modelDisplayName = modelDisplayName,
        indicatorState = indicatorState,
        chatMode = chatMode,
        suppressDuplicateThoughtForHeader = suppressDuplicateThoughtForHeader,
        hasTranscriptSegments = suppressDuplicateThoughtForHeader,
        reserveCrewGeneratingSlot = reserveCrewGeneratingSlot,
        elapsedSeconds = elapsedSeconds,
        isThinkingSheetVisible = isThinkingSheetVisible,
    ) {
        isThinkingSheetVisible = !isThinkingSheetVisible
    }

    if (isThinkingSheetVisible) {
        ThinkingDetailsBottomSheet(
            isVisible = true,
            thinkingRaw = indicatorThinkingRawForSheet(indicatorState),
            thinkingDurationSeconds = indicatorThinkingDurationForSheet(indicatorState),
            elapsedSeconds = elapsedSeconds,
            isStreaming = isIndicatorStreaming(indicatorState),
            onDismiss = { isThinkingSheetVisible = false },
        )
    }
}

@Composable
private fun MessageListIndicatorBody(
    modelDisplayName: String,
    indicatorState: IndicatorState,
    chatMode: ChatModeUi,
    suppressDuplicateThoughtForHeader: Boolean,
    hasTranscriptSegments: Boolean,
    reserveCrewGeneratingSlot: Boolean,
    elapsedSeconds: Int,
    isThinkingSheetVisible: Boolean,
    onToggleThinkingSheet: () -> Unit,
) {
    when (indicatorState) {
        is IndicatorState.EngineLoading -> {
            if (messageListShowsTopLevelStatusIndicator(indicatorState, hasTranscriptSegments)) {
                EngineLoadingIndicator(modelDisplayName = modelDisplayName)
            }
        }
        is IndicatorState.Thinking -> {
            if (transcriptLayoutShowsTopThinkingIndicator(indicatorState, suppressDuplicateThoughtForHeader)) {
                ThinkingIndicator(
                    elapsedSeconds = elapsedSeconds,
                    isExpanded = isThinkingSheetVisible,
                    onToggleDetails = onToggleThinkingSheet,
                )
            }
        }
        is IndicatorState.Processing -> {
            if (messageListShowsTopLevelStatusIndicator(indicatorState, hasTranscriptSegments)) {
                ProcessingIndicator()
            }
        }
        is IndicatorState.Generating -> {
            GeneratingIndicatorSection(
                indicatorState = indicatorState,
                chatMode = chatMode,
                suppressDuplicateThoughtForHeader = suppressDuplicateThoughtForHeader,
                elapsedSeconds = elapsedSeconds,
                isThinkingSheetVisible = isThinkingSheetVisible,
                onToggleThinkingSheet = onToggleThinkingSheet,
            )
        }
        is IndicatorState.Complete -> {
            CompleteIndicatorSection(
                indicatorState = indicatorState,
                suppressDuplicateThoughtForHeader = suppressDuplicateThoughtForHeader,
                reserveCrewGeneratingSlot = reserveCrewGeneratingSlot,
                elapsedSeconds = elapsedSeconds,
                isThinkingSheetVisible = isThinkingSheetVisible,
                onToggleThinkingSheet = onToggleThinkingSheet,
            )
        }
        is IndicatorState.None -> Unit
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
