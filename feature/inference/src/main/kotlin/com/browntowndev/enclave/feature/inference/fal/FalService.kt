package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

open class FalService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val queueStatusStream = FalQueueStatusStream(okHttpClient, json)

        open fun runSync(
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

        open fun submitQueue(
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

        /** Blocks until the queue reports a terminal status via Fal's SSE status stream. */
        open fun awaitStatusStream(
            apiKey: String,
            statusUrl: String,
        ): JsonObject = queueStatusStream.awaitTerminalStatus(apiKey = apiKey, statusUrl = statusUrl)

        open fun status(
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

        open fun result(
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

        open fun cancel(
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

        open fun downloadBytes(
            url: String,
            apiKey: String? = null,
        ): ByteArray = downloadBytesWithRetry(url, apiKey)

        private fun downloadBytesWithRetry(
            url: String,
            apiKey: String? = null,
        ): ByteArray {
            val builder = Request.Builder().url(url.normalizeMediaUrl()).get()
            if (!apiKey.isNullOrBlank()) {
                builder.header("Authorization", "Key ${apiKey.trim()}")
            }
            return executeBytes(
                builder.build(),
                failurePrefix = "fal media download failed",
                retryOnTransient = true,
            )
        }

        open fun resolveMediaBytes(
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
                downloadBytesWithRetry(url, apiKey)
            }

        open fun mediaUrls(
            response: JsonObject,
            outputSchema: ModelGenerationOutputSchema? = null,
        ): List<String> = FalResponseMediaUrls.mediaUrls(response, outputSchema)

        open fun primaryMediaUrls(
            response: JsonObject,
            outputSchema: ModelGenerationOutputSchema? = null,
        ): List<String> = FalResponseMediaUrls.primaryMediaUrls(response, outputSchema)

        open fun primaryVideoUrls(
            response: JsonObject,
            outputSchema: ModelGenerationOutputSchema? = null,
        ): List<String> = FalResponseMediaUrls.primaryVideoUrls(response, outputSchema)

        open fun statusValue(response: JsonObject): String? = response.queueStatusValue()

        private fun JsonObject.requiredString(name: String): String =
            optionalString(name) ?: throw IllegalStateException("Missing fal $name")

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
        ): IllegalStateException =
            IllegalStateException(
                "$failurePrefix: $statusCode ${FalMediaResponseDiagnostics.formatHttpErrorBody(body)}",
            )

        private fun jsonCallFailed(failurePrefix: String): IllegalStateException = IllegalStateException("$failurePrefix failed")

        private fun Int.isTransientHttpStatus(): Boolean = this == HTTP_TOO_MANY_REQUESTS || this == HTTP_SERVICE_UNAVAILABLE

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
            retryOnTransient: Boolean = false,
        ): ByteArray {
            val maxAttempts = if (retryOnTransient) MAX_RETRY_ATTEMPTS else 1
            var lastError: IllegalStateException? = null
            repeat(maxAttempts) { attempt ->
                if (attempt > 0) Thread.sleep(RETRY_BACKOFF_MILLIS * attempt)
                when (val result = readBytesResponse(request, failurePrefix, retryOnTransient)) {
                    is BytesCallResult.Success -> return result.body
                    is BytesCallResult.Retry -> lastError = result.error
                    is BytesCallResult.Fatal -> throw result.error
                }
            }
            throw lastError ?: IllegalStateException(failurePrefix)
        }

        private fun readBytesResponse(
            request: Request,
            failurePrefix: String,
            retryOnTransient: Boolean,
        ): BytesCallResult =
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body.bytes()
                if (response.isSuccessful) {
                    return BytesCallResult.Success(body)
                }
                val error = IllegalStateException("$failurePrefix: ${response.code}")
                if (retryOnTransient && response.code.isTransientHttpStatus()) {
                    BytesCallResult.Retry(error)
                } else {
                    BytesCallResult.Fatal(error)
                }
            }

        private sealed interface BytesCallResult {
            data class Success(
                val body: ByteArray,
            ) : BytesCallResult

            data class Retry(
                val error: IllegalStateException,
            ) : BytesCallResult

            data class Fatal(
                val error: IllegalStateException,
            ) : BytesCallResult
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

        private fun String.normalizeMediaUrl(): String = if (startsWith("//")) "https:$this" else this

        companion object {
            const val DEFAULT_SYNC_BASE_URL = "https://fal.run"
            const val DEFAULT_QUEUE_BASE_URL = "https://queue.fal.run"
            private const val DATA_URI_PREFIX = "data:"

            fun deriveResponseUrl(statusUrl: String): String? {
                val trimmed = statusUrl.trimEnd('/')
                return if (trimmed.endsWith("/status", ignoreCase = true)) {
                    trimmed.removeSuffix("/status")
                } else {
                    null
                }
            }

            private const val MAX_RETRY_ATTEMPTS = 3
            private const val RETRY_BACKOFF_MILLIS = 1_000L
            private const val HTTP_TOO_MANY_REQUESTS = 429
            private const val HTTP_SERVICE_UNAVAILABLE = 503
            private const val ERROR_BODY_LOG_MAX_CHARS = 200
            private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }

internal fun JsonObject.queueStatusValue(): String? =
    optionalString("status")
        ?: optionalString("state")
        ?: optionalString("queue_status")

internal fun JsonObject.optionalString(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
