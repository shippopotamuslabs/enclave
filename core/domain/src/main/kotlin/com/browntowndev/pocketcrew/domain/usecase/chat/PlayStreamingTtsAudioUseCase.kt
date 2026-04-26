package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.TtsAudioChunk
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.TtsServiceFactoryPort
import com.browntowndev.pocketcrew.domain.port.media.AudioPlayerPort
import com.browntowndev.pocketcrew.domain.port.media.StreamingAudioConfig
import com.browntowndev.pocketcrew.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for playing TTS audio with streaming support for low latency.
 *
 * This use case orchestrates TTS playback, using streaming for providers that support it
 * (xAI, OpenAI) and falling back to batch mode for providers that don't (Google).
 *
 * Streaming enables audio to begin playing as soon as the first chunks arrive rather than
 * waiting for the complete audio file to be generated, significantly reducing time-to-first-audio.
 */
class PlayStreamingTtsAudioUseCase @Inject constructor(
    private val resolveAssignedModelSelectionUseCase: ResolveAssignedModelSelectionUseCase,
    private val ttsServiceFactory: TtsServiceFactoryPort,
    private val streamingAudioPlayer: StreamingAudioPlayerPort,
    private val audioPlayer: AudioPlayerPort,
    private val apiKeyProvider: ApiKeyProviderPort,
    private val loggingPort: LoggingPort,
) {
    /**
     * Synthesizes and plays TTS audio, using streaming when available for lower latency.
     *
     * Returns a [Flow] that emits playback status events:
     * - [StreamingPlaybackStatus.Initializing] when setting up the audio player
     * - [StreamingPlaybackStatus.Playing] when audio chunks are being received and played (streaming)
     *   or when batch playback has started (fallback)
     * - [StreamingPlaybackStatus.Completed] when playback is finished successfully
     * - [StreamingPlaybackStatus.Error] if an error occurs during streaming or playback
     *
     * @param text The text to synthesize into speech.
     * @return A [Flow] of [StreamingPlaybackStatus] representing the playback lifecycle.
     */
    operator fun invoke(text: String): Flow<StreamingPlaybackStatus> = flow {
        try {
            emit(StreamingPlaybackStatus.Initializing)

            val selection = resolveAssignedModelSelectionUseCase(ModelType.TTS)
                ?: throw IllegalStateException("No TTS provider assigned.")

            val ttsAsset = selection.ttsAsset
                ?: throw IllegalStateException("Assigned TTS model selection is invalid.")

            val apiKey = apiKeyProvider.getApiKey(ttsAsset.credentialAlias)
                ?: throw IllegalStateException("API key not found for TTS provider: ${ttsAsset.displayName}")

            val streamingService = ttsServiceFactory.createStreaming(
                provider = ttsAsset.provider,
                apiKey = apiKey,
                baseUrl = ttsAsset.baseUrl
            )

            if (streamingService != null) {
                // Provider supports streaming — use low-latency streaming path
                loggingPort.debug(TAG, "Using streaming TTS for provider: ${ttsAsset.provider}")
                streamingAudioPlayer.initialize(StreamingAudioConfig.PCM_16_24KHZ_MONO)
                emit(StreamingPlaybackStatus.Playing)

                var hasError = false
                var hasStartedPlayback = false

                streamingService.synthesizeSpeechStreaming(
                    text = text,
                    voice = ttsAsset.voiceName,
                    modelId = ttsAsset.modelName
                ).collect { chunk ->
                    when (chunk) {
                        is TtsAudioChunk.Data -> {
                            streamingAudioPlayer.enqueueChunk(chunk.bytes)
                            if (!hasStartedPlayback) {
                                loggingPort.debug(TAG, "First audio chunk received, starting playback")
                                streamingAudioPlayer.startPlayback()
                                hasStartedPlayback = true
                            }
                        }

                        is TtsAudioChunk.Done -> {
                            loggingPort.debug(TAG, "Streaming TTS received done signal")
                        }

                        is TtsAudioChunk.Error -> {
                            loggingPort.error(TAG, "Streaming TTS chunk error: ${chunk.message}", chunk.cause)
                            streamingAudioPlayer.stop()
                            hasError = true
                            emit(StreamingPlaybackStatus.Error(chunk.message, chunk.cause))
                            return@collect
                        }
                    }
                }

                if (!hasError) {
                    loggingPort.debug(TAG, "Streaming TTS completed successfully")
                    emit(StreamingPlaybackStatus.Completed)
                }
            } else {
                // Provider doesn't support streaming — fall back to batch mode
                loggingPort.debug(TAG, "Streaming not available for provider: ${ttsAsset.provider}, falling back to batch TTS")
                emit(StreamingPlaybackStatus.Playing)

                val ttsService = ttsServiceFactory.create(
                    provider = ttsAsset.provider,
                    apiKey = apiKey,
                    baseUrl = ttsAsset.baseUrl
                )

                val audioBytes = ttsService.synthesizeSpeech(
                    text = text,
                    voice = ttsAsset.voiceName,
                    modelId = ttsAsset.modelName
                ).getOrThrow()

                // Batch playback blocks until MediaPlayer completes.
                // After playback, the use case emits Completed and the ViewModel
                // calls stop(). The stop() is safe — AndroidAudioPlayer.stop()
                // handles a null MediaPlayer gracefully.
                audioPlayer.playAudio(audioBytes)

                loggingPort.debug(TAG, "Batch TTS playback completed")
                emit(StreamingPlaybackStatus.Completed)
            }
        } catch (e: Exception) {
            loggingPort.error(TAG, "TTS playback error: ${e.message}", e)
            streamingAudioPlayer.stop()
            emit(StreamingPlaybackStatus.Error(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Stops any currently playing audio (streaming or batch).
     */
    fun stop() {
        loggingPort.debug(TAG, "Stopping all TTS audio playback")
        streamingAudioPlayer.stop()
        audioPlayer.stop()
    }

    companion object {
        private const val TAG = "StreamingTTS"
    }
}

/**
 * Represents the status of a streaming TTS playback session.
 */
sealed class StreamingPlaybackStatus {
    /**
     * Initializing the audio player and connecting to the streaming service.
     */
    data object Initializing : StreamingPlaybackStatus()

    /**
     * Audio is being played (either streaming or batch).
     */
    data object Playing : StreamingPlaybackStatus()

    /**
     * Playback completed successfully.
     */
    data object Completed : StreamingPlaybackStatus()

    /**
     * An error occurred during streaming or playback.
     */
    data class Error(val message: String, val cause: Throwable? = null) : StreamingPlaybackStatus()
}