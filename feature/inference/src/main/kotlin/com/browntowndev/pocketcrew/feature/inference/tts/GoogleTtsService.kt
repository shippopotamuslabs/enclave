package com.browntowndev.pocketcrew.feature.inference.tts

import android.util.Base64
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

class GoogleTtsService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
) : TtsServicePort {

    @Serializable
    private data class GoogleTtsInput(val text: String)

    @Serializable
    private data class GoogleTtsVoice(val languageCode: String, val name: String)

    @Serializable
    private data class GoogleTtsAudioConfig(val audioEncoding: String)

    @Serializable
    private data class GoogleTtsRequest(
        val input: GoogleTtsInput,
        val voice: GoogleTtsVoice,
        val audioConfig: GoogleTtsAudioConfig
    )

    @Serializable
    private data class GoogleTtsResponse(val audioContent: String)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun synthesizeSpeech(text: String, voice: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            // voice string expected as "languageCode:voiceName" e.g. "en-US:en-US-Neural2-D"
            val voiceParts = voice.split(":")
            val languageCode = voiceParts.getOrNull(0) ?: "en-US"
            val voiceName = voiceParts.getOrNull(1) ?: voice

            val requestBody = json.encodeToString(
                GoogleTtsRequest.serializer(),
                GoogleTtsRequest(
                    input = GoogleTtsInput(text = text),
                    voice = GoogleTtsVoice(languageCode = languageCode, name = voiceName),
                    audioConfig = GoogleTtsAudioConfig(audioEncoding = "MP3")
                )
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Google TTS request failed: ${response.code} ${response.message}")
                }
                val responseBody = response.body?.string() ?: throw IllegalStateException("Google TTS response body is null")
                val ttsResponse = json.decodeFromString(GoogleTtsResponse.serializer(), responseBody)
                Base64.decode(ttsResponse.audioContent, Base64.DEFAULT)
            }
        }
    }
}
