package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStream
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamServicePort
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.models.audio.speech.SpeechCreateParams
import com.openai.models.audio.speech.SpeechModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * OpenAI TTS media stream adapter that returns MP3 audio as an [InputStream].
 *
 * Unlike [com.browntowndev.pocketcrew.feature.inference.tts.OpenAiStreamingTtsService]
 * which returns PCM chunks via a Flow, this adapter streams the MP3 response body
 * directly, allowing ExoPlayer to decode it natively without a PCM -> WAV conversion.
 *
 * Uses MP3 format (not PCM) because ExoPlayer supports MP3 demuxing out of the box,
 * and MP3 avoids the need for sample rate / channel configuration that PCM requires.
 */
class OpenAiMediaStreamService @Inject constructor(
    private val clientProvider: OpenAiClientProviderPort,
    private val apiKey: String,
    private val baseUrl: String?,
    private val logger: LoggingPort,
) : TtsMediaStreamServicePort {

    override suspend fun openStream(text: String, voice: String, modelId: String?): TtsMediaStream =
        withContext(Dispatchers.IO) {
            val client = clientProvider.getClient(apiKey = apiKey, baseUrl = baseUrl)
            val resolvedModel = modelId?.let { SpeechModel.of(it) } ?: SpeechModel.TTS_1

            val params = SpeechCreateParams.builder()
                .input(text)
                .model(resolvedModel)
                .voice(SpeechCreateParams.Voice.ofString(voice))
                .responseFormat(SpeechCreateParams.ResponseFormat.MP3)
                .build()

            logger.debug(TAG, "Starting MP3 stream for OpenAI TTS: model=$resolvedModel, voice=$voice")

            val response = client.audio().speech().create(params)
            // response.body() returns an InputStream from the SDK.
            // We do NOT close the response here — the caller (TtsDataSource) will
            // close the InputStream when ExoPlayer is done, which also closes the
            // underlying HTTP connection.
            val stream = response.body()

            TtsMediaStream(
                mimeType = MIME_TYPE_MP3,
                length = null, // Content-Length not available from SDK InputStream
                stream = stream,
            )
        }

    /** Exposed for testing to verify MIME type without making HTTP calls. */
    fun expectedMimeType(): String = MIME_TYPE_MP3

    /** Confirms this service uses streaming response (not readBytes). */
    fun usesStreamingResponse(): Boolean = true

    companion object {
        private const val TAG = "OpenAiMediaStream"
        private const val MIME_TYPE_MP3 = "audio/mpeg"
    }
}