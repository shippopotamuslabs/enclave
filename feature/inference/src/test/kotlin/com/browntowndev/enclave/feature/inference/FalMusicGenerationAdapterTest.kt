package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.media.MusicGenerationSettings
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.media.FalMusicGenerationAdapter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class FalMusicGenerationAdapterTest {
    private val falService = SequencedFalService()
    private val outputSchemaResolver = FakeFalOutputSchemaResolver()
    private val adapter =
        FalMusicGenerationAdapter(
            falService = falService,
            outputSchemaResolver = outputSchemaResolver,
            pollDelayMillis = 0L,
        )

    @Test
    fun generateMusic_queueResult_downloadsAudioUrl() =
        runTest {
            falService.submitQueueResult =
                FalRequest(
                    requestId = "req-sync",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            falService.statusResult = buildJsonObject { put("status", "completed") }
            falService.resultResult = buildJsonObject { put("audio_url", "https://cdn.test/music.mp3") }
            falService.resolveMediaBytesResult = "mp3".toByteArray()

            val result = adapter.generateMusic("song", "key", "fal-ai/stable-audio", null, MusicGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("mp3".toByteArray(), result.getOrThrow())
        }

    @Test
    fun generateMusic_queueResult_decodesDataUriWithoutDownload() =
        runTest {
            val bytes = "mp3".toByteArray()
            val dataUri = "data:audio/mpeg;base64,${Base64.getEncoder().encodeToString(bytes)}"
            falService.submitQueueResult =
                FalRequest(
                    requestId = "req-data",
                    statusUrl = "https://queue.test/status",
                    responseUrl = "https://queue.test/result",
                    cancelUrl = null,
                )
            falService.statusResult = buildJsonObject { put("status", "completed") }
            falService.resultResult = buildJsonObject { put("audio_url", dataUri) }
            falService.resolveMediaBytesResult = bytes

            val result = adapter.generateMusic("song", "key", "fal-ai/stable-audio", null, MusicGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals(bytes, result.getOrThrow())
        }

    @Test
    fun generateMusic_queueResponse_pollsAndDownloadsPrimaryAudio() =
        runTest {
            falService.submitQueueResult =
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
            falService.statusResult = buildJsonObject { put("status", "completed") }
            falService.resultResult = completed
            falService.resolveMediaBytesResult = "mp3".toByteArray()

            val result = adapter.generateMusic("song", "key", "fal-ai/stable-audio", null, MusicGenerationSettings())

            assertTrue(result.isSuccess)
            assertArrayEquals("mp3".toByteArray(), result.getOrThrow())
        }
}
