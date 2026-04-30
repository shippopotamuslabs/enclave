package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.GoogleVideoGenerationAdapter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleVideoGenerationAdapterTest {
    private val okHttpClient = mockk<OkHttpClient>()
    private val adapter = GoogleVideoGenerationAdapter(okHttpClient, pollDelayMillis = 0L)

    @Test
    fun generateVideo_success_downloadsGeneratedSampleUri() = runTest {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse("""{"name":"operations/video-op"}"""),
            jsonResponse(
                """
                {
                  "done": true,
                  "response": {
                    "generateVideoResponse": {
                      "generatedSamples": [
                        { "video": { "uri": "https://download.test/video.mp4" } }
                      ]
                    }
                  }
                }
                """.trimIndent(),
            ),
            bytesResponse("mp4".toByteArray(), "video/mp4"),
        )

        val result = adapter.generateVideo(
            prompt = "animate",
            apiKey = "key",
            modelId = "veo-3.1-generate-preview",
            baseUrl = "https://google.test/v1beta",
            settings = VideoGenerationSettings(
                aspectRatio = AspectRatio.NINE_SIXTEEN,
                videoDuration = 6,
                videoResolution = "1080p",
            ),
            referenceImage = "png".toByteArray(),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        assertEquals("https://google.test/v1beta/models/veo-3.1-generate-preview:predictLongRunning", requests[0].url.toString())
        assertEquals("https://google.test/v1beta/operations/video-op", requests[1].url.toString())
        assertEquals("https://download.test/video.mp4", requests[2].url.toString())
        val body = requests[0].bodyString()
        assertTrue(body.contains("\"prompt\":\"animate\""))
        assertTrue(body.contains("\"bytesBase64Encoded\""))
        assertTrue(body.contains("\"aspectRatio\":\"9:16\""))
    }

    @Test
    fun generateVideo_missingGeneratedSample_returnsFailure() = runTest {
        everyResponses(
            requests = mutableListOf(),
            jsonResponse("""{"name":"operations/video-op"}"""),
            jsonResponse("""{"done":true,"response":{"generateVideoResponse":{"generatedSamples":[]}}}"""),
        )

        val result = adapter.generateVideo(
            prompt = "animate",
            apiKey = "key",
            modelId = "veo",
            baseUrl = null,
            settings = VideoGenerationSettings(),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("No Google video result URI") == true)
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

    private fun jsonResponse(body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun bytesResponse(body: ByteArray, mediaType: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://example.test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody(mediaType.toMediaType()))
            .build()

    private fun Request.bodyString(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }
}
