package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import javax.inject.Inject

class XaiVideoGenerationAdapter(
    private val okHttpClient: OkHttpClient,
    private val pollDelayMillis: Long,
) {
    @Inject
    constructor(okHttpClient: OkHttpClient) : this(okHttpClient, DEFAULT_POLL_DELAY_MILLIS)

    suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray? = null,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val videoSettings = settings as? VideoGenerationSettings
                ?: throw IllegalArgumentException("Settings must be VideoGenerationSettings")
            val rootUrl = baseUrl?.trimEnd('/') ?: DEFAULT_BASE_URL
            val requestId = executeJson(
                request = Request.Builder()
                    .url("$rootUrl/videos/generations")
                    .header("Authorization", "Bearer $apiKey")
                    .post(createRequestBody(prompt, modelId, videoSettings, referenceImage))
                    .build(),
            ).string("request_id")
            val videoUrl = pollUntilComplete(rootUrl, apiKey, requestId)
            executeBytes(Request.Builder().url(videoUrl).get().build())
        }
    }

    private suspend fun pollUntilComplete(rootUrl: String, apiKey: String, requestId: String): String {
        repeat(MAX_POLL_ATTEMPTS) {
            val response = executeJson(
                request = Request.Builder()
                    .url("$rootUrl/videos/$requestId")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build(),
            )
            when (val status = response.string("status")) {
                "done" -> {
                    return response["video"]?.jsonObject
                        ?.get("url")?.jsonPrimitive
                        ?.contentOrNull
                        ?: throw IllegalStateException("No xAI video result URL")
                }
                "failed", "expired" -> throw IllegalStateException("xAI video generation $status")
            }
            delay(pollDelayMillis)
        }
        throw IllegalStateException("xAI video generation timed out")
    }

    private fun createRequestBody(
        prompt: String,
        modelId: String,
        settings: VideoGenerationSettings,
        referenceImage: ByteArray?,
    ): RequestBody {
        val request = buildJsonObject {
            put("model", modelId)
            put("prompt", prompt)
            put("duration", settings.videoDuration)
            put("aspect_ratio", settings.aspectRatio.ratio)
            put("resolution", settings.videoResolution)
            if (referenceImage != null) {
                val dataUri = "data:image/png;base64,${Base64.getEncoder().encodeToString(referenceImage)}"
                put("image", buildJsonObject { put("url", dataUri) })
            }
        }
        return request.toString().toRequestBody("application/json".toMediaType())
    }

    private fun executeJson(request: Request): JsonObject {
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body.string()
        if (!response.isSuccessful) {
            throw IllegalStateException("xAI video request failed: ${response.code} $responseBody")
        }
        return Json.parseToJsonElement(responseBody).jsonObject
    }

    private fun executeBytes(request: Request): ByteArray {
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body.bytes()
        if (!response.isSuccessful) {
            throw IllegalStateException("xAI video download failed: ${response.code}")
        }
        return responseBody
    }

    private fun JsonObject.string(name: String): String =
        get(name)?.jsonPrimitive?.content ?: throw IllegalStateException("Missing xAI video $name")

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.x.ai/v1"
        const val DEFAULT_POLL_DELAY_MILLIS = 5_000L
        const val MAX_POLL_ATTEMPTS = 120
    }
}
