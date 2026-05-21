package com.browntowndev.pocketcrew.feature.chat.components.assistantresponse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.feature.chat.ArtifactCardUiModel
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.IndicatorState
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi
import com.browntowndev.pocketcrew.feature.chat.components.detailbottomsheet.ThinkingDetailsBottomSheet

@Composable
internal fun AssistantResponseFinalMessage(
    message: ChatMessage,
    contentText: String,
    isStreaming: Boolean,
    isPreview: Boolean,
    hasTtsProviderAssigned: Boolean,
    toolCallBanner: ToolCallBannerUi?,
    onPlayAudio: (String) -> Unit,
    onArtifactCardClick: (MessageId, ArtifactCardUiModel) -> Unit,
    onNavigateToStudio: (ids: List<String>, uris: List<String>, types: List<String>, prompts: List<String>) -> Unit,
    onShareClick: (MessageId) -> Unit,
    onShowSources: () -> Unit,
) {
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

    val openThinkingSheet: (
        String,
        Long?,
        Boolean,
        Int,
    ) -> Unit = { text, durationSeconds, streaming, elapsedForComplete ->
        transcriptSheetText = text
        transcriptSheetDurationSeconds = durationSeconds ?: 0L
        transcriptSheetStreaming = streaming
        transcriptSheetElapsedSeconds = elapsedForComplete
        transcriptSheetVisible = true
    }

    AssistantResponseTranscript(
        message = message,
        contentText = contentText,
        isStreaming = isStreaming,
        isPreview = isPreview,
        toolCallBanner = toolCallBanner,
        onOpenThinkingSheet = openThinkingSheet,
    )

    if (message.content.artifacts.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(message.content.artifacts) { card ->
                ArtifactCard(
                    card = card,
                    onClick = { onArtifactCardClick(message.id, card) },
                )
            }
        }
    }

    if (message.content.generatedMediaUris.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        MediaCard(
            uris = message.content.generatedMediaUris,
            mediaTypes = message.content.generatedMediaTypes,
            prompts = message.content.generatedMediaPrompts,
            onClick = { u, t, p -> onNavigateToStudio(message.content.generatedMediaAssetIds, u, t, p) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    val isComplete =
        (message.indicatorState is IndicatorState.Complete ||
            message.indicatorState is IndicatorState.None ||
            isPreview) &&
            toolCallBanner == null

    AssistantResponseActionRow(
        message = message,
        contentText = contentText,
        isComplete = isComplete,
        hasTtsProviderAssigned = hasTtsProviderAssigned,
        onPlayAudio = onPlayAudio,
        onShareClick = onShareClick,
        onShowSources = onShowSources,
    )
}
