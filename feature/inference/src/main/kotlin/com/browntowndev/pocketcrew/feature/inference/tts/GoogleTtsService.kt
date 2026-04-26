package com.browntowndev.pocketcrew.feature.inference.tts

import com.browntowndev.pocketcrew.domain.port.inference.TtsServicePort
import com.google.genai.Models
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.PrebuiltVoiceConfig
import com.google.genai.types.SpeechConfig
import com.google.genai.types.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GoogleTtsService @Inject constructor(
    private val modelsApiProvider: () -> Models,
) : TtsServicePort {

    override suspend fun synthesizeSpeech(text: String, voice: String, modelId: String?): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedVoice = if (voice.contains(":")) {
                // Legacy Cloud TTS voice format "languageCode:voiceName" is no longer supported.
                // The Cloud TTS API requires OAuth 2.0 credentials which are incompatible with
                // the BYOK AI Studio API key model. Fall back to Puck (default Gemini voice).
                "Puck"
            } else {
                voice
            }

            val modelsApi = modelsApiProvider()
            val config = GenerateContentConfig.builder()
                .responseModalities(listOf("audio"))
                .speechConfig(
                    SpeechConfig.builder()
                        .voiceConfig(
                            VoiceConfig.builder()
                                .prebuiltVoiceConfig(
                                    PrebuiltVoiceConfig.builder()
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
            // Gemini TTS returns raw PCM 16-bit 24kHz mono audio without a container header.
            // MediaPlayer requires a WAV/RIFF header to play PCM, so wrap it before returning.
            wrapPcmInWav(pcmData)
        }
    }

    /**
     * Wraps raw PCM 16-bit 24kHz mono audio data in a minimal WAV/RIFF header
     * so that Android MediaPlayer can decode and play it.
     */
    private fun wrapPcmInWav(pcmData: ByteArray): ByteArray {
        val sampleRate = 24_000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteArray(44)
        val buffer = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + pcmData.size) // chunk size
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt sub-chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size
        buffer.putShort(1) // AudioFormat: PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())

        // data sub-chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmData.size)

        return header + pcmData
    }
}