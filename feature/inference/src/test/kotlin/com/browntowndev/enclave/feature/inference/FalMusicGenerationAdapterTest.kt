package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.fal.FalService
import com.browntowndev.enclave.feature.inference.media.FalMusicGenerationAdapter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class FalMusicGenerationAdapterTest {
    private val falService = mockk<FalService>(relaxed = true)
    private val adapter = FalMusicGenerationAdapter(falService, pollDelayMillis = 0L)

    @Test
    fun generateMusic_queueResult_downloadsAudioUrl() =
        runTest {
            val queueRequest =
                FalRequest(
                    requestId = "req-sync",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            val completed = buildJsonObject { put("audio_url", "https://cdn.test/music.mp3") }
            every { falService.submitQueue(any(), any(), any(), any()) } returns queueRequest
            every { falService.status(any(), any()) } returns buildJsonObject { put("status", "completed") }
            every { falService.result(any(), any()) } returns completed
            every { falService.primaryMediaUrls(any()) } returns listOf("https://cdn.test/music.mp3")
            every { falService.mediaUrls(any()) } returns listOf("https://cdn.test/music.mp3")
            every { falService.resolveMediaBytes("https://cdn.test/music.mp3", any()) } returns "mp3".toByteArray()

            val result = adapter.generateMusic("song", "key", "fal-ai/stable-audio", null, MusicGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("mp3".toByteArray(), result.getOrThrow())
        }

    @Test
    fun generateMusic_queueResult_decodesDataUriWithoutDownload() =
        runTest {
            val bytes = "mp3".toByteArray()
            val dataUri = "data:audio/mpeg;base64,${Base64.getEncoder().encodeToString(bytes)}"
            val queueRequest =
                FalRequest(
                    requestId = "req-data",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            val completed = buildJsonObject { put("audio_url", dataUri) }
            every { falService.submitQueue(any(), any(), any(), any()) } returns queueRequest
            every { falService.status(any(), any()) } returns buildJsonObject { put("status", "completed") }
            every { falService.result(any(), any()) } returns completed
            every { falService.primaryMediaUrls(any()) } returns listOf(dataUri)
            every { falService.mediaUrls(any()) } returns listOf(dataUri)
            every { falService.resolveMediaBytes(dataUri, any()) } returns bytes

            val result = adapter.generateMusic("song", "key", "fal-ai/stable-audio", null, MusicGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals(bytes, result.getOrThrow())
        }

    @Test
    fun generateMusic_queueResponse_pollsAndDownloadsPrimaryAudio() =
        runTest {
            val queueRequest =
                FalRequest(
                    requestId = "req-1",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = "https://queue.test/cancel",
                )
            val completed =
                buildJsonObject {
                    put("status", "completed")
                    put("audio_url", "https://cdn.test/final.mp3")
                }
            every { falService.submitQueue(any(), any(), any(), any()) } returns queueRequest
            every { falService.status(any(), any()) } returns buildJsonObject { put("status", "completed") }
            every { falService.result(any(), any()) } returns completed
            every { falService.primaryMediaUrls(any()) } returns listOf("https://cdn.test/final.mp3")
            every { falService.mediaUrls(any()) } returns listOf("https://cdn.test/final.mp3")
            every { falService.resolveMediaBytes("https://cdn.test/final.mp3", any()) } returns "mp3".toByteArray()

            val result = adapter.generateMusic("song", "key", "fal-ai/stable-audio", null, MusicGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("mp3".toByteArray(), result.getOrThrow())
        }
}
