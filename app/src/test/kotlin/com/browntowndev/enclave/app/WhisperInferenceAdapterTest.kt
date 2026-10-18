package com.browntowndev.enclave.app

import com.shippopotamus.whisper.WhisperEngine
import com.shippopotamus.whisper.WhisperParams
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhisperInferenceAdapterTest {
    @Test
    fun initialize_delegatesToLoadWithDefaultParams() =
        runTest {
            val engine = FakeWhisperEngine()
            val adapter = WhisperInferenceAdapter(engine)

            adapter.initialize("/models/ggml-base.en.bin")

            assertEquals("/models/ggml-base.en.bin", engine.loadedModelPath)
            assertEquals(WhisperParams(), engine.loadedParams)
            assertTrue(engine.isLoaded())
        }

    @Test
    fun transcribe_delegatesSamplesAndReturnsEngineText() =
        runTest {
            val engine = FakeWhisperEngine(transcript = "hello world")
            val adapter = WhisperInferenceAdapter(engine)
            val samples = floatArrayOf(0.1f, -0.2f, 0.3f)

            val result = adapter.transcribe(samples)

            assertEquals("hello world", result)
            assertTrue(samples.contentEquals(engine.lastSamples ?: FloatArray(0)))
        }

    @Test
    fun close_delegatesToEngineClose() =
        runTest {
            val engine = FakeWhisperEngine()
            val adapter = WhisperInferenceAdapter(engine)

            adapter.initialize("/models/ggml-base.en.bin")
            adapter.close()

            assertTrue(engine.closed)
            assertFalse(engine.isLoaded())
        }

    private class FakeWhisperEngine(
        private val transcript: String = "",
    ) : WhisperEngine {
        var loadedModelPath: String? = null
        var loadedParams: WhisperParams? = null
        var lastSamples: FloatArray? = null
        var closed: Boolean = false
        private var loaded: Boolean = false

        override suspend fun load(
            modelPath: String,
            params: WhisperParams,
        ) {
            loadedModelPath = modelPath
            loadedParams = params
            loaded = true
            closed = false
        }

        override suspend fun transcribe(samples: FloatArray): String {
            lastSamples = samples
            return transcript
        }

        override fun isLoaded(): Boolean = loaded

        override suspend fun close() {
            closed = true
            loaded = false
        }
    }
}
