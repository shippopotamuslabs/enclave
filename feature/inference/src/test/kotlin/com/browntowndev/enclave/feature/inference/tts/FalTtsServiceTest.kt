package com.browntowndev.enclave.feature.inference.tts

import com.browntowndev.enclave.feature.inference.FakeFalService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class FalTtsServiceTest {
    private val falService = FakeFalService()
    private val service = FalTtsService(falService = falService, apiKey = "key", baseUrl = null)

    @Test
    fun synthesizeSpeech_syncResponse_downloadsAudioUrl() =
        runTest {
            val response = buildJsonObject { put("audio_url", "https://cdn.test/speech.mp3") }
            falService.runSyncResult = response
            falService.resolveMediaBytesResult = "mp3".toByteArray()

            val result = service.synthesizeSpeech("hello", "Wise_Woman", "fal-ai/minimax/speech-02-hd")

            assertTrue(result.isSuccess)
            assertArrayEquals("mp3".toByteArray(), result.getOrThrow())
        }

    @Test
    fun synthesizeSpeech_syncResponse_decodesDataUriWithoutDownload() =
        runTest {
            val bytes = "mp3".toByteArray()
            val dataUri = "data:audio/mpeg;base64,${Base64.getEncoder().encodeToString(bytes)}"
            val response = buildJsonObject { put("audio_url", dataUri) }
            falService.runSyncResult = response
            falService.resolveMediaBytesResult = bytes

            val result = service.synthesizeSpeech("hello", "Wise_Woman", "fal-ai/minimax/speech-02-hd")

            assertTrue(result.isSuccess)
            assertArrayEquals(bytes, result.getOrThrow())
        }

    @Test
    fun synthesizeSpeech_nullModelUsesDefaultModel() =
        runTest {
            val response = buildJsonObject { put("audio_url", "https://cdn.test/speech.mp3") }
            falService.runSyncResult = response
            falService.resolveMediaBytesResult = "mp3".toByteArray()

            val result = service.synthesizeSpeech("hello", "Wise_Woman", null)

            assertTrue(result.isSuccess)
            assertEquals("fal-ai/minimax/speech-02-hd", falService.runSyncCalls.single())
        }

    @Test
    fun synthesizeSpeech_noUrls_returnsFailure() =
        runTest {
            val response = buildJsonObject {}
            falService.runSyncResult = response

            val result = service.synthesizeSpeech("hello", "Wise_Woman", "fal-ai/minimax/speech-02-hd")

            assertTrue(result.isFailure)
            assertEquals("No fal TTS audio URL returned", result.exceptionOrNull()?.message)
        }
}
