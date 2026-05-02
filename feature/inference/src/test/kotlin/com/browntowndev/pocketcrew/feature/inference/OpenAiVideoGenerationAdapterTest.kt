package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.feature.inference.media.OpenAiVideoGenerationAdapter
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

class OpenAiVideoGenerationAdapterTest {
    private val okHttpClient = mockk<OkHttpClient>()
    private val adapter = OpenAiVideoGenerationAdapter(okHttpClient, pollDelayMillis = 0L)

    @Test
    fun generateVideo_success_submitsPollsAndDownloadsContent() = runTest {
        val requests = mutableListOf<Request>()
        everyResponses(
            requests = requests,
            jsonResponse("""{"id":"video_1","status":"queued"}"""),
            jsonResponse("""{"id":"video_1","status":"completed"}"""),
            bytesResponse("mp4".toByteArray(), "video/mp4"),
        )

        val result = adapter.generateVideo(
            prompt = "animate",
            apiKey = "key",
            modelId = "sora-2",
            baseUrl = "https://example.test/v1",
            settings = VideoGenerationSettings(
                aspectRatio = AspectRatio.SIXTEEN_NINE,
                videoDuration = 7,
                videoResolution = "720p",
            ),
            referenceImage = "png".toByteArray(),
        )

        assertTrue(result.isSuccess)
        assertArrayEquals("mp4".toByteArray(), result.getOrThrow())
        assertEquals("https://example.test/v1/videos", requests[0].url.toString())
        assertEquals("https://example.test/v1/videos/video_1", requests[1].url.toString())
        assertEquals("https://example.test/v1/videos/video_1/content", requests[2].url.toString())
        val body = requests[0].bodyString()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("sora-2"))
        assertTrue(body.contains("name=\"seconds\""))
        assertTrue(body.contains("8"))
        assertTrue(body.contains("name=\"input_reference\""))
    }

    @Test
    fun generateVideo_failedStatus_returnsFailure() = runTest {
        everyResponses(
            requests = mutableListOf(),
            jsonResponse("""{"id":"video_1","status":"queued"}"""),
            jsonResponse("""{"id":"video_1","status":"failed"}"""),
        )

        val result = adapter.generateVideo(
            prompt = "animate",
            apiKey = "key",
            modelId = "sora-2",
            baseUrl = null,
            settings = VideoGenerationSettings(),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("failed") == true)
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
