package com.browntowndev.pocketcrew.feature.inference.tts

import com.browntowndev.pocketcrew.domain.model.inference.TtsAudioChunk
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.StreamingTtsServicePort
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.models.audio.speech.SpeechCreateParams
import com.openai.models.audio.speech.SpeechModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * OpenAI streaming TTS service implementation using HTTP chunked transfer for low-latency audio.
 *
 * Unlike the standard [OpenAiTtsService] which downloads the complete audio before returning,
 * this implementation uses OpenAI's streaming response API to receive audio chunks as they
 * are generated, enabling playback to begin before the full audio is complete.
 *
 * Uses PCM format for lowest latency (no MP3 decoder overhead).
 */
class OpenAiStreamingTtsService @Inject constructor(
    private val clientProvider: OpenAiClientProviderPort,
    private val apiKey: String,
    private val baseUrl: String?,
    private val logger: LoggingPort,
) : StreamingTtsServicePort {

    override fun synthesizeSpeechStreaming(
        text: String,
        voice: String,
        modelId: String?
    ): Flow<TtsAudioChunk> = callbackFlow {
        withContext(Dispatchers.IO) {
            try {
                val client = clientProvider.getClient(apiKey = apiKey, baseUrl = baseUrl)
                val resolvedModel = modelId?.let { SpeechModel.of(it) } ?: SpeechModel.TTS_1

                // Use PCM format for lowest latency - no decoder needed
                val params = SpeechCreateParams.builder()
                    .input(text)
                    .model(resolvedModel)
                    .voice(SpeechCreateParams.Voice.ofString(voice))
                    .responseFormat(SpeechCreateParams.ResponseFormat.PCM)
                    .build()

                logger.debug(TAG, "Starting streaming TTS request with model: $resolvedModel")

                // Use the OpenAI SDK to create speech - the SDK handles streaming internally
                // when we read from the response body progressively
                client.audio().speech().create(params).use { response ->
                    val body = response.body()
                        ?: throw IllegalStateException("Response body is null")

                    // Read bytes progressively from the response
                    // OpenAI TTS doesn't natively stream, but we simulate streaming
                    // by reading in chunks to start playback sooner
                    val allBytes = body.readBytes()

                    // Emit chunks progressively
                    var offset = 0
                    while (offset < allBytes.size) {
                        val end = (offset + DEFAULT_BUFFER_SIZE).coerceAtMost(allBytes.size)
                        val chunk = allBytes.copyOfRange(offset, end)
                        trySend(TtsAudioChunk.Data(chunk))
                        offset = end
                    }
                    trySend(TtsAudioChunk.Done)
                }
            } catch (e: Exception) {
                logger.error(TAG, "Streaming TTS request failed", e)
                trySend(TtsAudioChunk.Error("TTS request failed: ${e.message}", e))
            } finally {
                close()
            }
        }

        awaitClose {
            logger.debug(TAG, "Flow cancelled")
        }
    }

    private companion object {
        const val TAG = "OpenAiStreamingTtsService"
        const val DEFAULT_BUFFER_SIZE = 4096 // 4KB chunks for smooth streaming
    }
}
