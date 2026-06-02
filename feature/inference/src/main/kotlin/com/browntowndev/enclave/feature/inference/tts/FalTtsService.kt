package com.browntowndev.enclave.feature.inference.tts

import com.browntowndev.enclave.domain.port.inference.TtsServicePort
import com.browntowndev.enclave.feature.inference.fal.FalService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FalTtsService(
    private val falService: FalService,
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
                val response =
                    falService.runSync(
                        apiKey = apiKey,
                        modelId = modelId ?: DEFAULT_TTS_MODEL_ID,
                        baseUrl = baseUrl,
                        body =
                            buildJsonObject {
                                put("text", text)
                                put("voice", voice)
                            },
                    )
                val mediaUrl =
                    falService.mediaUrls(response).firstOrNull()
                        ?: throw IllegalStateException("No fal TTS audio URL returned")
                falService.resolveMediaBytes(mediaUrl, apiKey)
            }
        }

    private companion object {
        private const val DEFAULT_TTS_MODEL_ID = "fal-ai/minimax/speech-02-hd"
    }
}
