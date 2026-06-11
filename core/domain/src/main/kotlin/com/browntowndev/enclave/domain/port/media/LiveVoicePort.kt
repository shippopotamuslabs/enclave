package com.browntowndev.enclave.domain.port.media

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import kotlinx.coroutines.flow.Flow

interface LiveVoicePort {
    suspend fun openSession(
        asset: LiveVoiceProviderAsset,
        apiKey: String,
        tools: List<ToolDefinition> = emptyList(),
    ): Result<LiveVoiceSession>
}

interface LiveVoiceSession {
    fun events(): Flow<LiveVoiceEvent>

    suspend fun sendAudio(pcm16: ByteArray)

    suspend fun commitTurn()

    /** Stops the in-flight assistant response so playback can be interrupted. */
    suspend fun cancelAssistantResponse()

    suspend fun close()

    suspend fun sendToolResult(
        callId: String,
        name: String,
        resultJson: String,
    )
}

sealed interface LiveVoiceEvent {
    data object Connected : LiveVoiceEvent

    data class UserTranscriptDelta(
        val text: String,
    ) : LiveVoiceEvent

    data class UserTranscriptDone(
        val text: String,
    ) : LiveVoiceEvent

    data class AssistantTranscriptDelta(
        val text: String,
    ) : LiveVoiceEvent

    data class AssistantTranscriptDone(
        val text: String,
    ) : LiveVoiceEvent

    /** Emitted when the provider finishes the current assistant spoken response. */
    data object AssistantResponseComplete : LiveVoiceEvent

    data class AssistantAudioChunk(
        val pcm16: ByteArray,
    ) : LiveVoiceEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AssistantAudioChunk) return false
            return pcm16.contentEquals(other.pcm16)
        }

        override fun hashCode(): Int = pcm16.contentHashCode()
    }

    data object SpeechStarted : LiveVoiceEvent

    data object SpeechStopped : LiveVoiceEvent

    data class Error(
        val message: String,
    ) : LiveVoiceEvent

    data object Closed : LiveVoiceEvent

    data class ToolCallRequested(
        val callId: String,
        val name: String,
        val argumentsJson: String,
    ) : LiveVoiceEvent
}
