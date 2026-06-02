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
            var consecutiveUnknownAttempts = 0
            repeat(MAX_POLL_ATTEMPTS) {
                coroutineContext.ensureActive()
                val status = falService.status(apiKey = apiKey, statusUrl = request.statusUrl)
                val statusStr = falService.statusValue(status)?.lowercase()
                when (statusStr) {
                    "completed", "complete", "done" -> {
                        status.optionalQueueError()?.let { failGeneration(status) }
                        return if (request.responseUrl != null) {
                            val result = runCatching { falService.result(apiKey = apiKey, responseUrl = request.responseUrl) }.getOrNull()
                            selectCompletedPayload(status = status, result = result)
                        } else {
                            status
                        }
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
                delay(pollDelayMillis)
            }
            cancelQueue(apiKey = apiKey, request = request)
            failGenerationTimeout()
        } catch (cancellation: CancellationException) {
            cancelQueue(apiKey = apiKey, request = request)
            throw cancellation
        }
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
    ): JsonObject =
        when {
            result != null && falService.primaryMediaUrls(result).isNotEmpty() -> result
            falService.primaryMediaUrls(status).isNotEmpty() -> status
            result != null && falService.mediaUrls(result).isNotEmpty() -> result
            falService.mediaUrls(status).isNotEmpty() -> status
            result != null -> result
            else -> status
        }

    private companion object {
        private const val MAX_POLL_ATTEMPTS = 120
    }
}
