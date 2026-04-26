package com.browntowndev.pocketcrew.feature.inference.tts.media

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStream
import com.browntowndev.pocketcrew.domain.port.media.TtsMediaStreamServicePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.inject.Inject

/**
 * Google TTS media stream adapter that delegates to the batch synthesizeSpeech API
 * and returns WAV bytes as a [ByteArrayInputStream].
 *
 * Google's Gemini TTS SDK returns generated audio only after generateContent completes,
 * so streaming is not possible. This adapter wraps the WAV bytes in an InputStream
 * so ExoPlayer can play it through the same Media3 notification path.
 */
class GoogleMediaStreamService @Inject constructor(
    private val modelsApiProvider: () -> com.google.genai.Models,
    private val logger: LoggingPort,
) : TtsMediaStreamServicePort {

    private val googleTtsService = GoogleTtsDelegate(modelsApiProvider)

    override suspend fun openStream(text: String, voice: String, modelId: String?): TtsMediaStream =
        withContext(Dispatchers.IO) {
            val result = googleTtsService.synthesizeSpeech(text, voice, modelId)
            val wavBytes = result.getOrThrow()

            logger.debug(TAG, "Google TTS produced ${wavBytes.size} bytes of WAV audio")

            TtsMediaStream(
                mimeType = MIME_TYPE_WAV,
                length = wavBytes.size.toLong(),
                stream = ByteArrayInputStream(wavBytes),
            )
        }

    /** Exposed for testing to verify MIME type without making API calls. */
    fun expectedMimeType(): String = MIME_TYPE_WAV

    /** Google uses batch mode (not streaming) for audio. */
    fun streamingMode(): String = BATCH_MODE

    companion object {
        private const val TAG = "GoogleMediaStream"
        private const val MIME_TYPE_WAV = "audio/wav"
        private const val BATCH_MODE = "batch"
    }
}

/**
 * Thin delegate that wraps [com.browntowndev.pocketcrew.feature.inference.tts.GoogleTtsService]
 * logic. Kept as a separate class to allow the GoogleMediaStreamService to be constructed
 * without needing all of GoogleTtsService's Hilt dependencies.
 */
private class GoogleTtsDelegate(
    private val modelsApiProvider: () -> com.google.genai.Models,
) {
    suspend fun synthesizeSpeech(text: String, voice: String, modelId: String?): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolvedVoice = if (voice.contains(":")) "Puck" else voice
                val modelsApi = modelsApiProvider()
                val config = com.google.genai.types.GenerateContentConfig.builder()
                    .responseModalities(listOf("audio"))
                    .speechConfig(
                        com.google.genai.types.SpeechConfig.builder()
                            .voiceConfig(
                                com.google.genai.types.VoiceConfig.builder()
                                    .prebuiltVoiceConfig(
                                        com.google.genai.types.PrebuiltVoiceConfig.builder()
                                            .voiceName(resolvedVoice)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()

                val resolvedModelId = modelId?.takeIf { it.isNotBlank() } ?: "gemini-3.1-flash-tts-preview"
                val response = modelsApi.generateContent(resolvedModelId, text, config)
                val candidate = response.candidates().get().get(0)
                val content = candidate.content().get()
                val part = content.parts().get().get(0)
                val pcmData = part.inlineData().get().data().get()
                wrapPcmInWav(pcmData)
            }
        }

    private fun wrapPcmInWav(pcmData: ByteArray): ByteArray {
        val sampleRate = 24_000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteArray(44)
        val buffer = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + pcmData.size)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmData.size)

        return header + pcmData
    }
}