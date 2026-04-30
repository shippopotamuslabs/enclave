package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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

class GoogleVideoGenerationAdapter(
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
            val operation = executeJson(
                request = Request.Builder()
                    .url("$rootUrl/models/$modelId:predictLongRunning")
                    .header("x-goog-api-key", apiKey)
                    .post(createRequestBody(prompt, videoSettings, referenceImage))
                    .build(),
            ).string("name")
            val videoUri = pollUntilComplete(rootUrl, apiKey, operation)
            executeBytes(
                request = Request.Builder()
                    .url(videoUri)
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build(),
            )
        }
    }

    private suspend fun pollUntilComplete(rootUrl: String, apiKey: String, operationName: String): String {
        repeat(MAX_POLL_ATTEMPTS) {
            val response = executeJson(
                request = Request.Builder()
                    .url("$rootUrl/$operationName")
                    .header("x-goog-api-key", apiKey)
                    .get()
                    .build(),
            )
            if (response["error"] != null) {
                throw IllegalStateException("Google video generation failed: ${response["error"]}")
            }
            if (response["done"]?.jsonPrimitive?.content == "true") {
                return response.findGoogleVideoUri()
            }
            delay(pollDelayMillis)
        }
        throw IllegalStateException("Google video generation timed out")
    }

    private fun createRequestBody(
        prompt: String,
        settings: VideoGenerationSettings,
        referenceImage: ByteArray?,
    ): RequestBody {
        val request = buildJsonObject {
            put("instances", buildJsonArray {
                add(buildJsonObject {
                    put("prompt", prompt)
                    if (referenceImage != null) {
                        put("image", buildJsonObject {
                            put("bytesBase64Encoded", Base64.getEncoder().encodeToString(referenceImage))
                            put("mimeType", "image/png")
                        })
                    }
                })
            })
            put("parameters", buildJsonObject {
                put("aspectRatio", settings.aspectRatio.toGoogleAspectRatio())
                put("durationSeconds", settings.videoDuration)
                put("resolution", settings.videoResolution)
            })
        }
        return request.toString().toRequestBody("application/json".toMediaType())
    }

    private fun AspectRatio.toGoogleAspectRatio(): String = when (this) {
        AspectRatio.ONE_ONE,
        AspectRatio.NINE_SIXTEEN,
        AspectRatio.SIXTEEN_NINE -> ratio
        else -> AspectRatio.SIXTEEN_NINE.ratio
    }

    private fun executeJson(request: Request): JsonObject {
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body.string()
        if (!response.isSuccessful) {
            throw IllegalStateException("Google video request failed: ${response.code} $responseBody")
        }
        return Json.parseToJsonElement(responseBody).jsonObject
    }

    private fun executeBytes(request: Request): ByteArray {
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body.bytes()
        if (!response.isSuccessful) {
            throw IllegalStateException("Google video download failed: ${response.code}")
        }
        return responseBody
    }

    private fun JsonObject.string(name: String): String =
        get(name)?.jsonPrimitive?.content ?: throw IllegalStateException("Missing Google operation $name")

    private fun JsonObject.findGoogleVideoUri(): String {
        val generatedSamples = this["response"]?.jsonObject
            ?.get("generateVideoResponse")?.jsonObject
            ?.get("generatedSamples")?.jsonArray
        val uri = generatedSamples
            ?.firstOrNull()
            ?.jsonObject
            ?.get("video")?.jsonObject
            ?.get("uri")?.jsonPrimitive
            ?.contentOrNull
        return uri ?: throw IllegalStateException("No Google video result URI")
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_POLL_DELAY_MILLIS = 10_000L
        const val MAX_POLL_ATTEMPTS = 120
    }
}
