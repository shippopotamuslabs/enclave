package com.browntowndev.enclave.feature.inference.fal

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class FalServiceTest {
    private val okHttpClient = mockk<OkHttpClient>()
    private val service = FalService(okHttpClient)

    @Test
    fun runSync_usesFalKeyAuthorizationAndSyncEndpoint() {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse("""{"images":[{"url":"https://cdn.test/image.png"}]}"""),
        )

        val response = service.runSync(" fal-key ", "fal-ai/test-model", kotlinx.serialization.json.buildJsonObject {}, null)

        assertEquals("https://fal.run/fal-ai/test-model", requests.single().url.toString())
        assertEquals("Key fal-key", requests.single().header("Authorization"))
        assertEquals(listOf("https://cdn.test/image.png"), service.mediaUrls(response))
    }

    @Test
    fun submitQueue_parsesQueueUrlsAndUsesQueueEndpoint() {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse(
                """
                {
                  "request_id": "request-1",
                  "status_url": "https://queue.fal.run/status/request-1",
                  "response_url": "https://queue.fal.run/result/request-1",
                  "cancel_url": "https://queue.fal.run/cancel/request-1"
                }
                """.trimIndent(),
            ),
        )

        val request = service.submitQueue("key", "fal-ai/video", kotlinx.serialization.json.buildJsonObject {}, null)

        assertEquals("https://queue.fal.run/fal-ai/video", requests.single().url.toString())
        assertEquals("request-1", request.requestId)
        assertEquals("https://queue.fal.run/status/request-1", request.statusUrl)
        assertEquals("https://queue.fal.run/result/request-1", request.responseUrl)
        assertEquals("https://queue.fal.run/cancel/request-1", request.cancelUrl)
    }

    @Test
    fun downloadBytes_returnsResponseBytes() {
        everyResponses(requests = mutableListOf(), bytesResponse("media".toByteArray()))

        assertEquals("media", service.downloadBytes("https://cdn.test/media.mp3").decodeToString())
    }

    @Test
    fun resolveMediaBytes_decodesDataUris() {
        val bytes = "media".toByteArray()
        val dataUri = "data:audio/mpeg;base64,${Base64.getEncoder().encodeToString(bytes)}"

        assertEquals("media", service.resolveMediaBytes(dataUri).decodeToString())
    }

    @Test
    fun status_usesFalKeyAuthorizationAndQueueStatusFallback() {
        val requests = mutableListOf<Request>()
        everyResponses(requests = requests, jsonResponse("""{"queue_status":"IN_PROGRESS"}"""))

        val response = service.status(" key ", "https://queue.test/status/request-1")

        assertEquals("https://queue.test/status/request-1", requests.single().url.toString())
        assertEquals("GET", requests.single().method)
        assertEquals("Key key", requests.single().header("Authorization"))
        assertEquals("IN_PROGRESS", service.statusValue(response))
    }

    @Test
    fun cancel_sendsDeleteToStatusUrl() {
        val requests = mutableListOf<Request>()
        everyResponses(requests = requests, bytesResponse(ByteArray(0)))

        service.cancel("key", "https://queue.test/status/request-1")

        assertEquals("https://queue.test/status/request-1", requests.single().url.toString())
        assertEquals("DELETE", requests.single().method)
        assertEquals("Key key", requests.single().header("Authorization"))
    }

    @Test
    fun runSync_httpErrorIncludesStatusAndBody() {
        everyResponses(
            requests = mutableListOf(),
            jsonResponse("""{"error":"bad key"}""", code = 401),
        )

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                service.runSync("key", "fal-ai/test-model", kotlinx.serialization.json.buildJsonObject {}, null)
            }

        assertEquals("fal sync request failed: 401 {\"error\":\"bad key\"}", thrown.message)
    }

    @Test
    fun mediaUrls_prioritizesPrimaryMediaUrlsAndDeprioritizesPreviews() {
        val response =
            Json
                .parseToJsonElement(
                    """
                    {
                      "images": [
                        {
                          "url": "https://cdn.test/image.png",
                          "preview_url": "https://cdn.test/preview.png"
                        }
                      ],
                      "audio_url": "data:audio/mpeg;base64,bXAz",
                      "nested": {
                        "video_url": "https://cdn.test/image.png"
                      },
                      "ignored": "https://cdn.test/not-a-url-key"
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(
            listOf(
                "data:audio/mpeg;base64,bXAz",
                "https://cdn.test/image.png",
                "https://cdn.test/preview.png",
            ),
            service.mediaUrls(response),
        )
    }

    @Test
    fun primaryMediaUrls_excludesPreviewAndReturnsOnlyPrimaryUrls() {
        val response =
            Json
                .parseToJsonElement(
                    """
                    {
                      "video_url": "https://cdn.test/video.mp4",
                      "preview_url": "https://cdn.test/preview.jpg",
                      "thumbnail_url": "https://cdn.test/thumb.jpg",
                      "audio_url": "https://cdn.test/audio.mp3"
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(
            listOf("https://cdn.test/video.mp4", "https://cdn.test/audio.mp3"),
            service.primaryMediaUrls(response),
        )
    }

    @Test
    fun primaryMediaUrls_previewOnlyResponse_returnsEmpty() {
        val response =
            Json
                .parseToJsonElement(
                    """{"preview_url": "https://cdn.test/preview.jpg", "thumbnail_url": "https://cdn.test/thumb.jpg"}""",
                ).jsonObject

        assertTrue(service.primaryMediaUrls(response).isEmpty())
    }

    @Test
    fun mediaUrls_extractsVideoUrlFromDataWrapper() {
        val response =
            Json
                .parseToJsonElement(
                    """
                    {
                      "data": {
                        "video": {
                          "url": "https://cdn.test/wrapped-video.mp4"
                        },
                        "seed": 7
                      }
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(listOf("https://cdn.test/wrapped-video.mp4"), service.primaryMediaUrls(response))
    }

    @Test
    fun mediaUrls_extractsVideoWhenUrlIsDirectString() {
        val response =
            Json
                .parseToJsonElement(
                    """{"video": "https://cdn.test/direct-video.mp4", "seed": 3}""",
                ).jsonObject

        assertEquals(listOf("https://cdn.test/direct-video.mp4"), service.primaryMediaUrls(response))
    }

    @Test
    fun mediaUrls_extractsNestedHappyHorseVideoObject() {
        val response =
            Json
                .parseToJsonElement(
                    """
                    {
                      "video": {
                        "url": "https://cdn.test/happy-horse.mp4",
                        "content_type": "video/mp4",
                        "file_name": "output.mp4"
                      },
                      "seed": 42
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(listOf("https://cdn.test/happy-horse.mp4"), service.primaryMediaUrls(response))
        assertEquals(listOf("https://cdn.test/happy-horse.mp4"), service.mediaUrls(response))
    }

    @Test
    fun mediaUrls_ignoresQueueControlUrls() {
        val response =
            Json
                .parseToJsonElement(
                    """
                    {
                      "response_url": "https://queue.test/result",
                      "status_url": "https://queue.test/status",
                      "cancel_url": "https://queue.test/cancel",
                      "video_url": "https://cdn.test/video.mp4"
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(listOf("https://cdn.test/video.mp4"), service.mediaUrls(response))
    }

    @Test
    fun resolveMediaBytes_invalidDataUri_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            service.resolveMediaBytes("data:image/png;base64") // missing comma
        }
        assertThrows(IllegalStateException::class.java) {
            service.resolveMediaBytes("data:image/png;base64,") // empty payload
        }
        assertThrows(IllegalStateException::class.java) {
            service.resolveMediaBytes("data:image/png;base64,invalid@@base64!!") // invalid base64
        }
    }

    @Test
    fun resolveMediaBytes_emptyBase64Decode_throwsException() {
        val emptyBase64 = Base64.getEncoder().encodeToString(ByteArray(0))
        assertThrows(IllegalStateException::class.java) {
            service.resolveMediaBytes("data:image/png;base64,$emptyBase64")
        }
    }

    @Test
    fun downloadBytes_withApiKey_sendsAuthorizationHeader() {
        val requests = mutableListOf<Request>()
        everyResponses(requests = requests, bytesResponse("media".toByteArray()))

        service.downloadBytes("https://cdn.test/media.mp3", apiKey = "my-key")

        assertEquals("https://cdn.test/media.mp3", requests.single().url.toString())
        assertEquals("Key my-key", requests.single().header("Authorization"))
    }

    @Test
    fun runSync_malformedJsonResponse_includesFailurePrefixInException() {
        everyResponses(
            requests = mutableListOf(),
            jsonResponse("not-json-at-all"),
        )

        val thrown =
            assertThrows(IllegalStateException::class.java) {
                service.runSync("key", "fal-ai/test-model", kotlinx.serialization.json.buildJsonObject {}, null)
            }

        assertTrue(thrown.message?.contains("fal sync request failed") == true)
    }

    @Test
    fun customBaseUrl_syncVsQueueMapping() {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse("""{"images":[]}"""),
            jsonResponse("""{"request_id":"req-1","status_url":"https://custom.run/status/req-1"}"""),
        )

        // Test custom sync mapping
        service.runSync("key", "fal-ai/test", kotlinx.serialization.json.buildJsonObject {}, "https://custom.run")
        assertEquals("https://custom.run/fal-ai/test", requests[0].url.toString())

        // Test custom queue mapping
        service.submitQueue("key", "fal-ai/test", kotlinx.serialization.json.buildJsonObject {}, "https://custom.run")
        assertEquals("https://custom.run/fal-ai/test", requests[1].url.toString())
    }

    @Test
    fun syncBaseUrl_falRunInput_usedDirectlyForSync() {
        val requests = mutableListOf<Request>()
        everyResponses(requests = requests, jsonResponse("""{"images":[]}"""))

        service.runSync("key", "fal-ai/test", kotlinx.serialization.json.buildJsonObject {}, "https://fal.run")

        assertEquals("https://fal.run/fal-ai/test", requests.single().url.toString())
    }

    @Test
    fun status_retriesTransient429BeforeSuccess() {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse("""{"error":"rate limited"}""", code = 429),
            jsonResponse("""{"status":"COMPLETED"}"""),
        )

        val response = service.status("key", "https://queue.test/status/request-1")

        assertEquals("COMPLETED", service.statusValue(response))
        assertEquals(2, requests.size)
    }

    @Test
    fun queueBaseUrl_falRunInput_remapsToQueueHost() {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse("""{"request_id":"req-1","status_url":"https://queue.fal.run/status/req-1"}"""),
        )

        service.submitQueue("key", "fal-ai/video", kotlinx.serialization.json.buildJsonObject {}, "https://fal.run")

        assertEquals("https://queue.fal.run/fal-ai/video", requests.single().url.toString())
    }

    private fun everyResponses(
        requests: MutableList<Request>,
        vararg responses: Response,
    ) {
        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } answers {
            requests += firstArg<Request>()
            call
        }
        every { call.execute() } returnsMany responses.toList()
    }

    private fun jsonResponse(
        body: String,
        code: Int = 200,
    ): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun bytesResponse(body: ByteArray): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://example.test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/octet-stream".toMediaType()))
            .build()
}
