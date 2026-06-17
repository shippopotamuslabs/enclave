package com.browntowndev.enclave.feature.inference.fal

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.coroutineContext

class FalQueuePoller(
    private val falService: FalService,
    private val pollDelayMillis: Long,
    private val errorPrefix: String,
) {
    suspend fun pollUntilComplete(
        apiKey: String,
        request: FalRequest,
    ): JsonObject {
        try {
            val streamStatus =
                try {
                    falService.awaitStatusStream(apiKey = apiKey, statusUrl = request.statusUrl)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            if (streamStatus != null) {
                when (falService.statusValue(streamStatus)?.lowercase()) {
                    "failed", "error", "cancelled" -> failGeneration(streamStatus)
                    else -> {
                        streamStatus.optionalQueueError()?.let { failGeneration(streamStatus) }
                        return resolveCompletedPayload(apiKey = apiKey, request = request, status = streamStatus)
                    }
                }
            }
            return pollWithBackoff(apiKey = apiKey, request = request)
        } catch (cancellation: CancellationException) {
            cancelQueue(apiKey = apiKey, request = request)
            throw cancellation
        }
    }

    private suspend fun pollWithBackoff(
        apiKey: String,
        request: FalRequest,
    ): JsonObject {
        var consecutiveUnknownAttempts = 0
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            coroutineContext.ensureActive()
            val status = falService.status(apiKey = apiKey, statusUrl = request.statusUrl)
            val statusStr = falService.statusValue(status)?.lowercase()
            when (statusStr) {
                "completed", "complete", "done" -> {
                    status.optionalQueueError()?.let { failGeneration(status) }
                    return resolveCompletedPayload(apiKey = apiKey, request = request, status = status)
                }
                "failed", "error", "cancelled" -> failGeneration(status)
                "in_queue", "in_progress", "processing" -> {
                    consecutiveUnknownAttempts = 0
                }
                else -> {
                    if (falService.primaryMediaUrls(status).isNotEmpty()) {
                        return status
                    }
                    consecutiveUnknownAttempts++
                    if (consecutiveUnknownAttempts >= 5) {
                        cancelQueue(apiKey = apiKey, request = request)
                        throw IllegalStateException(
                            "$errorPrefix failed: received unknown or unexpected status '$statusStr' multiple times",
                        )
                    }
                }
            }
            delay(backoffDelayMillis(attempt))
        }
        cancelQueue(apiKey = apiKey, request = request)
        failGenerationTimeout()
    }

    private fun resolveCompletedPayload(
        apiKey: String,
        request: FalRequest,
        status: JsonObject,
    ): JsonObject {
        status.optionalQueueError()?.let { failGeneration(status) }
        val explicitResponseUrl = request.responseUrl
        val fetchUrl =
            explicitResponseUrl
                ?: FalService
                    .deriveResponseUrl(request.statusUrl)
                    .takeIf { falService.mediaUrls(status).isEmpty() }
        return if (fetchUrl != null) {
            val result = fetchQueueResult(apiKey = apiKey, fetchUrl = fetchUrl, request = request, status = status)
            val payload = selectCompletedPayload(status = status, result = result)
            throwIfPayloadReportsError(payload)
            payload
        } else {
            throwIfPayloadReportsError(status)
            status
        }
    }

    private fun fetchQueueResult(
        apiKey: String,
        fetchUrl: String,
        request: FalRequest,
        status: JsonObject,
    ): JsonObject? {
        val resultOutcome =
            try {
                Result.success(falService.result(apiKey = apiKey, responseUrl = fetchUrl))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
        resultOutcome.exceptionOrNull()?.let { error ->
            FalMediaResponseDiagnostics.logResultFetchFailure(
                modelId = request.requestId ?: "unknown",
                fetchUrl = fetchUrl,
                error = error,
            )
        }
        val result = resultOutcome.getOrNull()
        if (result == null && falService.mediaUrls(status).isEmpty()) {
            failResultFetch(fetchUrl = fetchUrl, cause = resultOutcome.exceptionOrNull())
        }
        return result
    }

    private fun throwIfPayloadReportsError(payload: JsonObject): JsonObject {
        payload.extractPayloadErrorMessage()?.let { message -> failGenerationDetail(message) }
        return payload
    }

    private fun failResultFetch(
        fetchUrl: String,
        cause: Throwable?,
    ): Nothing =
        throw IllegalStateException(
            "$errorPrefix failed: could not fetch queue result from $fetchUrl" +
                (cause?.message?.let { " ($it)" } ?: ""),
        )

    private fun failGenerationDetail(message: String): Nothing = throw IllegalStateException("$errorPrefix failed: $message")

    private fun JsonObject.extractPayloadErrorMessage(): String? = FalMediaResponseDiagnostics.extractPayloadErrorMessage(this)

    private fun backoffDelayMillis(attempt: Int): Long {
        val scaled = pollDelayMillis * (attempt + 1)
        return scaled.coerceAtMost(MAX_POLL_DELAY_MILLIS)
    }

    private fun cancelQueue(
        apiKey: String,
        request: FalRequest,
    ) {
        runCatching {
            falService.cancel(apiKey = apiKey, statusUrl = request.cancelUrl ?: request.statusUrl)
        }.onFailure { e ->
            Log.w("FalQueuePoller", "Failed to cancel fal queue job: ${e.message}")
        }
    }

    private fun failGeneration(status: JsonObject): Nothing {
        val detail =
            listOf("error", "detail", "message", "error_message")
                .firstNotNullOfOrNull { key -> (status[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
        throw IllegalStateException(
            if (detail != null) "$errorPrefix failed: $detail" else "$errorPrefix failed",
        )
    }

    private fun failGenerationTimeout(): Nothing = throw IllegalStateException("$errorPrefix timed out")

    private fun JsonObject.optionalQueueError(): String? =
        listOf("error", "detail", "message", "error_message")
            .firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }

    private fun selectCompletedPayload(
        status: JsonObject,
        result: JsonObject?,
    ): JsonObject {
        val statusPrimary = falService.primaryMediaUrls(status)
        val resultPrimary = result?.let { falService.primaryMediaUrls(it) }.orEmpty()
        return when {
            result != null && resultPrimary.isNotEmpty() -> result
            statusPrimary.isNotEmpty() -> status
            result != null && falService.mediaUrls(result).isNotEmpty() -> result
            else -> status
        }
    }

    private companion object {
        private const val MAX_POLL_ATTEMPTS = 120
        private const val MAX_POLL_DELAY_MILLIS = 5_000L
    }
}
