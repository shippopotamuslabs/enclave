package com.browntowndev.pocketcrew.feature.inference.tts

import com.browntowndev.pocketcrew.domain.port.inference.TtsServicePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class XAiTtsService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
) : TtsServicePort {

    @Serializable
    private data class OutputFormat(
        val codec: String = "mp3",
        val sample_rate: Int = 24000,
        val bit_rate: Int = 128000
    )

    @Serializable
    private data class TtsRequest(
        val text: String,
        val voice_id: String,
        val language: String = "auto",
        val output_format: OutputFormat = OutputFormat()
    )

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun synthesizeSpeech(text: String, voice: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = json.encodeToString(
                TtsRequest.serializer(),
                TtsRequest(text = text, voice_id = voice)
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.x.ai/v1/tts")
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("xAI TTS request failed: ${response.code} ${response.message}")
                }
                response.body.bytes()
            }
        }
    }
}
