package com.browntowndev.enclave.feature.inference.fal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FalMediaResponseDiagnosticsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun extractPayloadErrorMessage_readsDetailArray() {
        val payload =
            json.decodeFromString<JsonObject>(
                """
                {
                  "detail": [
                    { "type": "value_error", "msg": "duration must be 6" }
                  ]
                }
                """.trimIndent(),
            )
        assertEquals("duration must be 6", FalMediaResponseDiagnostics.extractPayloadErrorMessage(payload))
    }

    @Test
    fun extractPayloadErrorMessage_readsNestedErrorObject() {
        val payload =
            buildJsonObject {
                put(
                    "error",
                    buildJsonObject {
                        put("message", "insufficient credits")
                    },
                )
            }
        assertEquals("insufficient credits", FalMediaResponseDiagnostics.extractPayloadErrorMessage(payload))
    }

    @Test
    fun formatHttpErrorBody_extractsDetailMsgWithoutEchoedInput() {
        val hugeBase64 = "A".repeat(10_000)
        val body =
            """
            {
              "detail": [
                {
                  "type": "content_policy_violation",
                  "msg": "Output video has sensitive content.",
                  "input": { "image_url": "data:image/png;base64,$hugeBase64" }
                }
              ]
            }
            """.trimIndent()
        val summary = FalMediaResponseDiagnostics.formatHttpErrorBody(body)
        assertEquals("Output video has sensitive content.", summary)
        assertFalse(summary.contains(hugeBase64))
    }

    @Test
    fun sanitizeUrlForLog_redactsDataUris() {
        assertEquals("data:<redacted>", FalMediaResponseDiagnostics.sanitizeUrlForLog("data:video/mp4;base64,AAAA"))
    }

    @Test
    fun sanitizeJsonForLog_redactsBase64Payloads() {
        val sanitized =
            FalMediaResponseDiagnostics.sanitizeJsonForLog(
                """{"image_url":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="}""",
            )
        assertFalse(sanitized.contains("iVBORw0KGgo"))
        assertTrue(sanitized.contains("<redacted>"))
    }

    @Test
    fun buildVideoUrlMissingMessage_includesApiErrorWhenPresent() {
        val payload = buildJsonObject { put("detail", "model overloaded") }
        val message =
            FalMediaResponseDiagnostics.buildVideoUrlMissingMessage(
                modelId = "bytedance/seedance-2.0/image-to-video",
                payload = payload,
                resultFetchFailed = false,
                resultFetchUrl = null,
            )
        assertTrue(message.contains("model overloaded"))
        assertTrue(message.contains("FalMediaGen"))
    }

    @Test
    fun describeExtraction_reportsCountsForSeedanceShape() {
        val payload =
            json.decodeFromString<JsonObject>(
                """
                {
                  "image_url": "https://cdn.test/input.jpg",
                  "video": { "url": "https://v3.fal.media/files/out.mp4" }
                }
                """.trimIndent(),
            )
        val summary = FalMediaResponseDiagnostics.describeExtraction(payload, outputSchema = null)
        assertNotNull(summary)
        assertTrue(summary.contains("primaryVideo=1") || summary.contains("all=1"))
    }
}
