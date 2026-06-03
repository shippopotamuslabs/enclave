package com.browntowndev.enclave.feature.inference.fal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

internal class FalQueueStatusStream(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    fun awaitTerminalStatus(
        apiKey: String,
        statusUrl: String,
    ): JsonObject {
        val request =
            Request
                .Builder()
                .url(statusStreamUrl(statusUrl))
                .header("Authorization", "Key ${apiKey.trim()}")
                .header("Accept", "text/event-stream")
                .get()
                .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("fal queue status stream failed: ${response.code}")
            }
            var lastStatus: JsonObject? = null
            response.body.source().use { rawSource ->
                val source = rawSource.buffer()
                while (!source.exhausted()) {
                    val event = parseEventLine(source.readUtf8Line()) ?: continue
                    lastStatus = event
                    if (event.queueStatusValue()?.lowercase() in TERMINAL_QUEUE_STATUSES) {
                        return event
                    }
                }
            }
            return lastStatus ?: throw IllegalStateException("fal queue status stream ended without status")
        }
    }

    private fun parseEventLine(line: String?): JsonObject? {
        if (line == null || !line.startsWith(SSE_DATA_PREFIX)) return null
        val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
        if (payload.isEmpty() || payload == SSE_DONE_MARKER) return null
        return try {
            json.parseToJsonElement(payload).jsonObject
        } catch (e: Exception) {
            throw IllegalStateException(
                "fal queue status stream failed: malformed event ${payload.take(ERROR_BODY_LOG_MAX_CHARS)}",
                e,
            )
        }
    }

    private fun statusStreamUrl(statusUrl: String): String {
        val trimmed = statusUrl.trimEnd('/')
        return if (trimmed.endsWith("/status", ignoreCase = true)) {
            "$trimmed/stream"
        } else {
            "$trimmed/status/stream"
        }
    }

    private companion object {
        private val TERMINAL_QUEUE_STATUSES = setOf("completed", "complete", "done", "failed", "error", "cancelled")
        private const val SSE_DATA_PREFIX = "data:"
        private const val SSE_DONE_MARKER = "[DONE]"
        private const val ERROR_BODY_LOG_MAX_CHARS = 200
    }
}
