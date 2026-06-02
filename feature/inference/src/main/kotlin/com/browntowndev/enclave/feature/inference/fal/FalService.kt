package com.browntowndev.enclave.feature.inference.fal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import javax.inject.Inject

data class FalRequest(
    val requestId: String?,
    val statusUrl: String,
    val responseUrl: String?,
    val cancelUrl: String?,
)

class FalService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        fun runSync(
            apiKey: String,
            modelId: String,
            body: JsonObject,
            baseUrl: String?,
        ): JsonObject =
            executeJson(
                Request
                    .Builder()
                    .url("${syncBaseUrl(baseUrl)}/${modelId.trim('/')}")
                    .header("Authorization", "Key ${apiKey.trim()}")
                    .header("Accept", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
                failurePrefix = "fal sync request failed",
            )

        fun submitQueue(
            apiKey: String,
            modelId: String,
            body: JsonObject,
            baseUrl: String?,
        ): FalRequest {
            val response =
                executeJson(
                    Request
                        .Builder()
                        .url("${queueBaseUrl(baseUrl)}/${modelId.trim('/')}")
                        .header("Authorization", "Key ${apiKey.trim()}")
                        .header("Accept", "application/json")
                        .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build(),
                    failurePrefix = "fal queue submit failed",
                )
            return FalRequest(
                requestId = response.optionalString("request_id"),
                statusUrl = response.requiredString("status_url"),
                responseUrl = response.optionalString("response_url"),
                cancelUrl = response.optionalString("cancel_url"),
            )
        }

        fun status(
            apiKey: String,
            statusUrl: String,
        ): JsonObject =
            executeJson(
                Request
                    .Builder()
                    .url(statusUrl)
                    .header("Authorization", "Key ${apiKey.trim()}")
                    .header("Accept", "application/json")
                    .get()
                    .build(),
                failurePrefix = "fal queue status failed",
                retryOnTransient = true,
            )

        fun result(
            apiKey: String,
            responseUrl: String,
        ): JsonObject =
            executeJson(
                Request
                    .Builder()
                    .url(responseUrl)
                    .header("Authorization", "Key ${apiKey.trim()}")
                    .header("Accept", "application/json")
                    .get()
                    .build(),
                failurePrefix = "fal queue result failed",
                retryOnTransient = true,
            )

        fun cancel(
            apiKey: String,
            statusUrl: String,
        ) {
            executeBytes(
                Request
                    .Builder()
                    .url(statusUrl)
                    .header("Authorization", "Key ${apiKey.trim()}")
                    .delete()
                    .build(),
                failurePrefix = "fal queue cancel failed",
            )
        }

        fun downloadBytes(
            url: String,
            apiKey: String? = null,
        ): ByteArray {
            val builder = Request.Builder().url(url).get()
            if (!apiKey.isNullOrBlank()) {
                builder.header("Authorization", "Key ${apiKey.trim()}")
            }
            return executeBytes(
                builder.build(),
                failurePrefix = "fal media download failed",
            )
        }

        fun resolveMediaBytes(
            url: String,
            apiKey: String? = null,
        ): ByteArray =
            if (url.startsWith(DATA_URI_PREFIX)) {
                val commaIndex = url.indexOf(',')
                if (commaIndex == -1) {
                    throw IllegalStateException("Malformed data URI: missing comma separator")
                }
                val payload = url.substring(commaIndex + 1)
                if (payload.isEmpty()) {
                    throw IllegalStateException("Malformed data URI: empty payload")
                }
                try {
                    val decoded = Base64.getDecoder().decode(payload)
                    if (decoded.isEmpty()) {
                        throw IllegalStateException("Malformed data URI: decoded content is empty")
                    }
                    decoded
                } catch (e: IllegalArgumentException) {
                    throw IllegalStateException("Malformed data URI: invalid base64 payload", e)
                }
            } else {
                downloadBytes(url, apiKey)
            }

        fun mediaUrls(response: JsonObject): List<String> =
            response
                .unwrapResultPayload()
                .collectUrlCandidates()
                .filter { it.url.isMediaUrl() }
                .sortedWith(compareBy<UrlCandidate> { it.priority }.thenBy { it.index })
                .distinctBy(UrlCandidate::url)
                .map(UrlCandidate::url)

        fun primaryMediaUrls(response: JsonObject): List<String> =
            response
                .unwrapResultPayload()
                .collectUrlCandidates()
                .filter { it.url.isMediaUrl() && it.priority < PREVIEW_MEDIA_URL_PRIORITY }
                .sortedWith(compareBy<UrlCandidate> { it.priority }.thenBy { it.index })
                .distinctBy(UrlCandidate::url)
                .map(UrlCandidate::url)

        fun statusValue(response: JsonObject): String? =
            response.optionalString("status")
                ?: response.optionalString("state")
                ?: response.optionalString("queue_status")

        private fun executeJson(
            request: Request,
            failurePrefix: String,
            retryOnTransient: Boolean = false,
        ): JsonObject {
            val maxAttempts = if (retryOnTransient) MAX_RETRY_ATTEMPTS else 1
            var lastRetryError: IllegalStateException? = null
            repeat(maxAttempts) { attempt ->
                if (attempt > 0) Thread.sleep(RETRY_BACKOFF_MILLIS * attempt)
                when (val result = evaluateJsonResponse(request, failurePrefix, retryOnTransient)) {
                    is JsonCallResult.Success -> return result.body
                    is JsonCallResult.Retry -> lastRetryError = result.error
                    is JsonCallResult.Fatal -> throw result.error
                }
            }
            throw lastRetryError ?: jsonCallFailed(failurePrefix)
        }

        private fun evaluateJsonResponse(
            request: Request,
            failurePrefix: String,
            retryOnTransient: Boolean,
        ): JsonCallResult =
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> parseSuccessfulJson(body, response.code, failurePrefix)
                    retryOnTransient && response.code.isTransientHttpStatus() ->
                        JsonCallResult.Retry(httpError(failurePrefix, response.code, body))
                    else -> JsonCallResult.Fatal(httpError(failurePrefix, response.code, body))
                }
            }

        private fun parseSuccessfulJson(
            body: String,
            statusCode: Int,
            failurePrefix: String,
        ): JsonCallResult =
            try {
                JsonCallResult.Success(json.parseToJsonElement(body).jsonObject)
            } catch (e: Exception) {
                JsonCallResult.Fatal(
                    IllegalStateException(
                        "$failurePrefix: $statusCode ${body.take(ERROR_BODY_LOG_MAX_CHARS)}",
                        e,
                    ),
                )
            }

        private fun httpError(
            failurePrefix: String,
            statusCode: Int,
            body: String,
        ): IllegalStateException = IllegalStateException("$failurePrefix: $statusCode $body")

        private fun jsonCallFailed(failurePrefix: String): IllegalStateException =
            IllegalStateException("$failurePrefix failed")

        private fun Int.isTransientHttpStatus(): Boolean =
            this == HTTP_TOO_MANY_REQUESTS || this == HTTP_SERVICE_UNAVAILABLE

        private sealed class JsonCallResult {
            data class Success(
                val body: JsonObject,
            ) : JsonCallResult()

            data class Retry(
                val error: IllegalStateException,
            ) : JsonCallResult()

            data class Fatal(
                val error: IllegalStateException,
            ) : JsonCallResult()
        }

        private fun executeBytes(
            request: Request,
            failurePrefix: String,
        ): ByteArray {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body.bytes()
                if (!response.isSuccessful) {
                    throw IllegalStateException("$failurePrefix: ${response.code}")
                }
                return body
            }
        }

        private fun syncBaseUrl(baseUrl: String?): String =
            when (val trimmed = baseUrl?.trim()?.trimEnd('/')) {
                null, "", DEFAULT_QUEUE_BASE_URL -> DEFAULT_SYNC_BASE_URL
                else -> trimmed
            }

        private fun queueBaseUrl(baseUrl: String?): String {
            val trimmed = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return DEFAULT_QUEUE_BASE_URL
            return if (trimmed.equals(DEFAULT_SYNC_BASE_URL, ignoreCase = true)) DEFAULT_QUEUE_BASE_URL else trimmed
        }

        private fun JsonObject.requiredString(name: String): String =
            optionalString(name) ?: throw IllegalStateException("Missing fal $name")

        private fun JsonObject.optionalString(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.unwrapResultPayload(): JsonObject {
            val wrapperKey =
                RESULT_PAYLOAD_WRAPPER_KEYS.firstOrNull { key ->
                    (this[key] as? JsonObject)?.let { nested ->
                        nested.containsMediaArtifactKey() || nested.collectUrlCandidates().any { it.url.isMediaUrl() }
                    } == true
                }
            val nested = wrapperKey?.let { this[it] as? JsonObject }
            return if (nested != null && nested != this) nested.unwrapResultPayload() else this
        }

        private fun JsonObject.containsMediaArtifactKey(): Boolean =
            keys.any { key ->
                key.isMediaFileKey() ||
                    key.endsWith("url", ignoreCase = true) ||
                    key.equals("images", ignoreCase = true) ||
                    key.equals("videos", ignoreCase = true)
            }

        private fun JsonElement.collectUrlCandidates(): List<UrlCandidate> {
            val candidates = mutableListOf<UrlCandidate>()

            fun visit(
                element: JsonElement,
                sequenceIndex: () -> Int,
            ) {
                when (element) {
                    is JsonObject -> {
                        for ((key, value) in element.entries) {
                            when {
                                key.endsWith("url", ignoreCase = true) && value is JsonPrimitive -> {
                                    value.contentOrNull?.let { url ->
                                        if (!key.isQueueControlUrlKey()) {
                                            candidates +=
                                                UrlCandidate(
                                                    url = url,
                                                    priority = key.mediaUrlPriority(),
                                                    index = sequenceIndex())
                                        }
                                    }
                                }
                                key.isMediaFileKey() && value is JsonPrimitive -> {
                                    value.contentOrNull?.takeIf { it.isMediaUrl() }?.let { url ->
                                        candidates +=
                                            UrlCandidate(
                                                url = url,
                                                priority = PRIMARY_MEDIA_URL_PRIORITY,
                                                index = sequenceIndex())
                                    }
                                }
                            }
                            visit(value, sequenceIndex)
                        }
                    }
                    is JsonArray -> element.forEach { visit(it, sequenceIndex) }
                    else -> Unit
                }
            }

            var index = 0
            visit(this) { index++ }
            return candidates
        }

        private fun String.isMediaFileKey(): Boolean = MEDIA_FILE_KEYS.any { equals(it, ignoreCase = true) }

        private fun String.isMediaUrl(): Boolean = startsWith("http://") || startsWith("https://") || startsWith(DATA_URI_PREFIX)

        private fun String.isQueueControlUrlKey(): Boolean =
            equals("status_url", ignoreCase = true) ||
                equals("response_url", ignoreCase = true) ||
                equals("cancel_url", ignoreCase = true)

        private fun String.mediaUrlPriority(): Int =
            when {
                equals("video_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
                equals("audio_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
                equals("music_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
                equals("image_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
                equals("file_url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY
                equals("url", ignoreCase = true) -> PRIMARY_MEDIA_URL_PRIORITY + 10
                contains("preview", ignoreCase = true) -> PREVIEW_MEDIA_URL_PRIORITY
                contains("thumbnail", ignoreCase = true) -> PREVIEW_MEDIA_URL_PRIORITY
                contains("poster", ignoreCase = true) -> PREVIEW_MEDIA_URL_PRIORITY
                else -> FALLBACK_MEDIA_URL_PRIORITY
            }

        private data class UrlCandidate(
            val url: String,
            val priority: Int,
            val index: Int,
        )

        companion object {
            const val DEFAULT_SYNC_BASE_URL = "https://fal.run"
            const val DEFAULT_QUEUE_BASE_URL = "https://queue.fal.run"
            private val RESULT_PAYLOAD_WRAPPER_KEYS = listOf("data", "output", "response", "payload", "result")
            private val MEDIA_FILE_KEYS = setOf("video", "image", "audio", "file", "output", "result")
            private const val DATA_URI_PREFIX = "data:"
            private const val PRIMARY_MEDIA_URL_PRIORITY = 0
            private const val FALLBACK_MEDIA_URL_PRIORITY = 50
            private const val PREVIEW_MEDIA_URL_PRIORITY = 100
            private const val MAX_RETRY_ATTEMPTS = 3
            private const val RETRY_BACKOFF_MILLIS = 1_000L
            private const val HTTP_TOO_MANY_REQUESTS = 429
            private const val HTTP_SERVICE_UNAVAILABLE = 503
            private const val ERROR_BODY_LOG_MAX_CHARS = 200
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
