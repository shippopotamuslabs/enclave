package com.browntowndev.enclave.feature.chat.components.assistantresponse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.feature.chat.ArtifactCardUiModel
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.feature.chat.components.detailbottomsheet.DetailBottomSheetConfig
import com.browntowndev.enclave.feature.chat.components.detailbottomsheet.rememberDetailBottomSheetController

@Suppress("LongParameterList")
@Composable
fun AssistantResponse(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    isPreview: Boolean = false,
    hasTtsProviderAssigned: Boolean = false,
    isPlayingTts: Boolean = false,
    isLoadingTts: Boolean = false,
    isPausedTts: Boolean = false,
    toolCallBanner: ToolCallBannerUi? = null,
    trailingStatusIndicator: @Composable (() -> Unit)? = null,
    onPlayAudio: (String) -> Unit = {},
    onPauseAudio: () -> Unit = {},
    onArtifactCardClick: (MessageId, ArtifactCardUiModel) -> Unit = { _, _ -> },
    onNavigateToStudio: (ids: List<String>, uris: List<String>, types: List<String>, prompts: List<String>) -> Unit = { _, _, _, _ -> },
    onShareClick: (MessageId) -> Unit = {},
    onStatsClick: (MessageId) -> Unit = {},
) {
    val contentText = message.content.text
    val pipelineStep = message.content.pipelineStep
    val isStreaming =
        message.indicatorState !is IndicatorState.Complete &&
            message.indicatorState !is IndicatorState.None &&
            !isPreview

    var showStepDetails by remember { mutableStateOf(false) }
    var selectedStepName by remember { mutableStateOf("") }
    var showSources by remember { mutableStateOf(false) }
    val detailSheetController = rememberDetailBottomSheetController(usePreviewStub = isPreview)

    LaunchedEffect(showStepDetails, selectedStepName, contentText, isStreaming) {
        if (showStepDetails) {
            detailSheetController.show(
                DetailBottomSheetConfig.StepCompletion(
                    isVisible = true,
                    content = contentText,
                    stepName = selectedStepName,
                    isStreaming = isStreaming,
                    onDismiss = { showStepDetails = false },
                ),
            )
        }
    }

    LaunchedEffect(showSources, message.webSearchSources) {
        if (showSources) {
            detailSheetController.show(
                DetailBottomSheetConfig.Sources(
                    isVisible = true,
                    sources = message.webSearchSources,
                    onDismiss = { showSources = false },
                ),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val isCompletedStep = pipelineStep != null && pipelineStep != PipelineStep.FINAL

        if (isCompletedStep) {
            CompletedStepRow(
                stepName = pipelineStep.displayName(),
                stepOutput = contentText,
                indicatorState = message.indicatorState,
                onClick = {
                    selectedStepName = pipelineStep.displayName()
                    showStepDetails = true
                },
            )
        } else {
            AssistantResponseFinalMessage(
                message = message,
                contentText = contentText,
                isStreaming = isStreaming,
                isPreview = isPreview,
                hasTtsProviderAssigned = hasTtsProviderAssigned,
                isPlayingTts = isPlayingTts,
                isLoadingTts = isLoadingTts,
                isPausedTts = isPausedTts,
                toolCallBanner = toolCallBanner,
                trailingStatusIndicator = trailingStatusIndicator,
                onPlayAudio = onPlayAudio,
                onPauseAudio = onPauseAudio,
                onArtifactCardClick = onArtifactCardClick,
                onNavigateToStudio = onNavigateToStudio,
                onShareClick = onShareClick,
                onStatsClick = onStatsClick,
                onShowSources = { showSources = true },
            )
        }
    }
}
