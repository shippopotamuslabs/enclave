package com.browntowndev.pocketcrew.feature.chat.components.assistantresponse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.feature.chat.ArtifactCardUiModel
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.IndicatorState
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi
import com.browntowndev.pocketcrew.feature.chat.components.detailbottomsheet.DetailBottomSheet
import com.browntowndev.pocketcrew.feature.chat.components.detailbottomsheet.DetailBottomSheetConfig

@Composable
fun AssistantResponse(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    isPreview: Boolean = false,
    hasTtsProviderAssigned: Boolean = false,
    toolCallBanner: ToolCallBannerUi? = null,
    onPlayAudio: (String) -> Unit = {},
    onArtifactCardClick: (MessageId, ArtifactCardUiModel) -> Unit = { _, _ -> },
    onNavigateToStudio: (ids: List<String>, uris: List<String>, types: List<String>, prompts: List<String>) -> Unit = { _, _, _, _ -> },
    onShareClick: (MessageId) -> Unit = {},
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

    if (showStepDetails) {
        DetailBottomSheet(
            config =
                DetailBottomSheetConfig.StepCompletion(
                    isVisible = showStepDetails,
                    content = contentText,
                    stepName = selectedStepName,
                    isStreaming = isStreaming,
                    onDismiss = { showStepDetails = false },
                ),
        )
    }

    if (showSources) {
        DetailBottomSheet(
            config =
                DetailBottomSheetConfig.Sources(
                    isVisible = showSources,
                    sources = message.webSearchSources,
                    onDismiss = { showSources = false },
                ),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val isCompletedStep = pipelineStep != null && pipelineStep != PipelineStep.FINAL

        if (isCompletedStep) {
            CompletedStepRow(
                stepName = pipelineStep.displayName(),
                stepOutput = contentText,
                modelDisplayName = message.modelDisplayName,
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
                toolCallBanner = toolCallBanner,
                onPlayAudio = onPlayAudio,
                onArtifactCardClick = onArtifactCardClick,
                onNavigateToStudio = onNavigateToStudio,
                onShareClick = onShareClick,
                onShowSources = { showSources = true },
            )
        }
    }
}
