package com.browntowndev.enclave.feature.inference.tts

import com.browntowndev.enclave.domain.port.inference.TtsServicePort
import com.browntowndev.enclave.feature.inference.OpenAiClientProviderPort
import com.openai.models.audio.speech.SpeechCreateParams
import com.openai.models.audio.speech.SpeechModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OpenAiTtsService
    @Inject
    constructor(
        private val clientProvider: OpenAiClientProviderPort,
        private val apiKey: String,
        private val baseUrl: String?,
    ) : TtsServicePort {
        override suspend fun synthesizeSpeech(
            text: String,
            voice: String,
            modelId: String?,
        ): Result<ByteArray> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val client = clientProvider.getClient(apiKey = apiKey, baseUrl = baseUrl)
                    val resolvedModel = modelId?.let { SpeechModel.of(it) } ?: SpeechModel.TTS_1
                    val params =
                        SpeechCreateParams
                            .builder()
                            .input(text)
                            .model(resolvedModel)
                            .voice(SpeechCreateParams.Voice.ofString(voice))
                            .responseFormat(SpeechCreateParams.ResponseFormat.MP3)
                            .build()

                    client.audio().speech().create(params).use { response ->
                        response.body().readBytes()
                    }
                }
            }
    }
