package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.abs
import javax.inject.Inject

class OpenAiVideoGenerationAdapter(
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
            val createResponse = execute(
                request = Request.Builder()
                    .url("$rootUrl/videos")
                    .header("Authorization", "Bearer $apiKey")
                    .post(createRequestBody(prompt, modelId, videoSettings, referenceImage))
                    .build(),
            )
            val videoId = createResponse.string("id")
            val completedId = pollUntilComplete(rootUrl, apiKey, videoId)
            executeBytes(
                request = Request.Builder()
                    .url("$rootUrl/videos/$completedId/content")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build(),
            )
        }
    }

    private suspend fun pollUntilComplete(rootUrl: String, apiKey: String, videoId: String): String {
        repeat(MAX_POLL_ATTEMPTS) {
            val response = execute(
                request = Request.Builder()
                    .url("$rootUrl/videos/$videoId")
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build(),
            )
            when (val status = response.string("status")) {
                "completed" -> return response.string("id")
                "failed", "expired", "cancelled" -> throw IllegalStateException("OpenAI video generation $status")
            }
            delay(pollDelayMillis)
        }
        throw IllegalStateException("OpenAI video generation timed out")
    }

    private fun createRequestBody(
        prompt: String,
        modelId: String,
        settings: VideoGenerationSettings,
        referenceImage: ByteArray?,
    ): MultipartBody {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("model", modelId)
            .addFormDataPart("size", settings.toOpenAiSize())
            .addFormDataPart("seconds", settings.toOpenAiSeconds())

        if (referenceImage != null) {
            builder.addFormDataPart(
                name = "input_reference",
                filename = "reference.png",
                body = referenceImage.toRequestBody("image/png".toMediaType()),
            )
        }

        return builder.build()
    }

    private fun VideoGenerationSettings.toOpenAiSeconds(): String =
        listOf(4, 8, 12).minBy { abs(it - videoDuration) }.toString()

    private fun VideoGenerationSettings.toOpenAiSize(): String =
        when (aspectRatio) {
            AspectRatio.SIXTEEN_NINE -> when (videoResolution) {
                "1080p" -> "1920x1080"
                "480p" -> "854x480"
                else -> "1280x720"
            }
            AspectRatio.NINE_SIXTEEN -> when (videoResolution) {
                "1080p" -> "1080x1920"
                "480p" -> "480x854"
                else -> "720x1280"
            }
            else -> when (videoResolution) {
                "1080p" -> "1080x1080"
                "480p" -> "480x480"
                else -> "720x720"
            }
        }

    private fun execute(request: Request): JsonObject {
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body.string()
        if (!response.isSuccessful) {
            throw IllegalStateException("OpenAI video request failed: ${response.code} $responseBody")
        }
        return Json.parseToJsonElement(responseBody).jsonObject
    }

    private fun JsonObject.string(name: String): String =
        get(name)?.jsonPrimitive?.content ?: throw IllegalStateException("Missing OpenAI video $name")

    private fun executeBytes(request: Request): ByteArray {
        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body.bytes()
        if (!response.isSuccessful) {
            throw IllegalStateException("OpenAI video content request failed: ${response.code}")
        }
        return responseBody
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_POLL_DELAY_MILLIS = 2_000L
        const val MAX_POLL_ATTEMPTS = 120
    }
}
