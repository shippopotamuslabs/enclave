package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.XaiVideoGenerationAdapter
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

class XaiVideoGenerationAdapterTest {
    private val okHttpClient = mockk<OkHttpClient>()
    private val adapter = XaiVideoGenerationAdapter(okHttpClient, pollDelayMillis = 0L)

    @Test
    fun generateVideo_success_downloadsTemporaryVideoUrl() = runTest {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse("""{"request_id":"request-1"}"""),
            jsonResponse("""{"status":"done","video":{"url":"https://download.test/video.mp4"}}"""),
            bytesResponse("mp4".toByteArray(), "video/mp4"),
        )

        val result = adapter.generateVideo(
            prompt = "animate",
            apiKey = "key",
            modelId = "grok-imagine-video",
            baseUrl = "https://xai.test/v1",
            settings = VideoGenerationSettings(
                aspectRatio = AspectRatio.SIXTEEN_NINE,
                videoDuration = 10,
                videoResolution = "720p",
            ),
            referenceImage = "png".toByteArray(),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        assertEquals("https://xai.test/v1/videos/generations", requests[0].url.toString())
        assertEquals("https://xai.test/v1/videos/request-1", requests[1].url.toString())
        assertEquals("https://download.test/video.mp4", requests[2].url.toString())
        val body = requests[0].bodyString()
        assertTrue(body.contains("\"model\":\"grok-imagine-video\""))
        assertTrue(body.contains("\"image\":{\"url\":\"data:image/png;base64"))
        assertTrue(body.contains("\"aspect_ratio\":\"16:9\""))
    }

    @Test
    fun generateVideo_expiredStatus_returnsFailure() = runTest {
        everyResponses(
            requests = mutableListOf(),
            jsonResponse("""{"request_id":"request-1"}"""),
            jsonResponse("""{"status":"expired"}"""),
        )

        val result = adapter.generateVideo(
            prompt = "animate",
            apiKey = "key",
            modelId = "grok-imagine-video",
            baseUrl = null,
            settings = VideoGenerationSettings(),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("expired") == true)
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
