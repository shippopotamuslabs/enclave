package com.browntowndev.pocketcrew.feature.inference.tts

import com.browntowndev.pocketcrew.domain.port.inference.TtsServicePort
import com.browntowndev.pocketcrew.feature.inference.OpenAiClientProviderPort
import com.openai.models.audio.speech.SpeechCreateParams
import com.openai.models.audio.speech.SpeechModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OpenAiTtsService @Inject constructor(
    private val clientProvider: OpenAiClientProviderPort,
    private val apiKey: String,
    private val baseUrl: String?,
) : TtsServicePort {

    override suspend fun synthesizeSpeech(text: String, voice: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val client = clientProvider.getClient(apiKey = apiKey, baseUrl = baseUrl)
            val params = SpeechCreateParams.builder()
                .input(text)
                .model(SpeechModel.TTS_1)
                .voice(SpeechCreateParams.Voice.ofString(voice))
                .responseFormat(SpeechCreateParams.ResponseFormat.MP3)
                .build()

            client.audio().speech().create(params).use { response ->
                response.body().readBytes()
            }
        }
    }
}
