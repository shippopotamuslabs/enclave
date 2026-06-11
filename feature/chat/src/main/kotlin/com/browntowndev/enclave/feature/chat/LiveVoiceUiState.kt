package com.browntowndev.enclave.feature.chat

import androidx.compose.runtime.Immutable

@Immutable
sealed interface LiveVoiceUiState {
    @Immutable
    data object Idle : LiveVoiceUiState

    @Immutable
    data object Connecting : LiveVoiceUiState

    @Immutable
    data class Listening(
        val userCaption: String,
        val assistantCaption: String,
        val isMuted: Boolean,
        val micLevel: Float,
        val activeToolCallBanner: ToolCallBannerUi? = null,
    ) : LiveVoiceUiState

    @Immutable
    data class AssistantSpeaking(
        val userCaption: String,
        val assistantCaption: String,
        val isMuted: Boolean,
        val audioLevel: Float,
        val activeToolCallBanner: ToolCallBannerUi? = null,
    ) : LiveVoiceUiState

    @Immutable
    data class Error(
        val message: String,
    ) : LiveVoiceUiState
}
