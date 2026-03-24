package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelConfig
import com.browntowndev.pocketcrew.domain.port.download.ModelUrlProviderPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelConfigFetcherImplTest {

    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var mockModelUrlProvider: ModelUrlProviderPort
    private lateinit var fetcher: ModelConfigFetcherImpl

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockOkHttpClient = mockk()
        mockModelUrlProvider = mockk()

        every { mockModelUrlProvider.getConfigUrl() } returns "https://config.example.com/model_config.json"

        fetcher = ModelConfigFetcherImpl(mockOkHttpClient, mockModelUrlProvider)
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun toModelConfigurations_preservesAllTuningsFields() = runTest {
        // Given
        val remoteConfigs = listOf(
            RemoteModelConfig(
                modelType = ModelType.MAIN,
                fileName = "model.gguf",
                huggingFaceModelName = "test/model",
                displayName = "Test Model",
                sha256 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.GGUF,
                temperature = 0.8,
                topK = 50,
                topP = 0.9,
                repetitionPenalty = 1.2,
                maxTokens = 4096,
                contextWindow = 8192,
                systemPrompt = "You are helpful.",
                thinkingEnabled = true
            )
        )

        // When
        val modelConfigs = fetcher.toModelConfigurations(remoteConfigs)

        // Then
        val config = modelConfigs.first()
        assertEquals(0.8, config.tunings.temperature)
        assertEquals(50, config.tunings.topK)
        assertEquals(0.9, config.tunings.topP)
        assertEquals(1.2, config.tunings.repetitionPenalty)
        assertEquals(4096, config.tunings.maxTokens)
        assertEquals(8192, config.tunings.contextWindow)
        assertTrue(config.tunings.thinkingEnabled)
    }

    @Test
    fun toModelConfigurations_convertsAllModelTypes() = runTest {
        // Given
        val remoteConfigs = listOf(
            RemoteModelConfig(
                modelType = ModelType.MAIN,
                fileName = "main.gguf",
                huggingFaceModelName = "test/main",
                displayName = "Main Model",
                sha256 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.GGUF,
                temperature = 0.7,
                topK = 40,
                topP = 0.95,
                repetitionPenalty = 1.1,
                maxTokens = 2048,
                contextWindow = 4096,
                systemPrompt = "You are helpful."
            ),
            RemoteModelConfig(
                modelType = ModelType.VISION,
                fileName = "vision.gguf",
                huggingFaceModelName = "test/vision",
                displayName = "Vision Model",
                sha256 = "def456",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.GGUF,
                temperature = 0.7,
                topK = 40,
                topP = 0.95,
                repetitionPenalty = 1.1,
                maxTokens = 2048,
                contextWindow = 4096,
                systemPrompt = "You are a vision assistant."
            ),
            RemoteModelConfig(
                modelType = ModelType.DRAFT_ONE,
                fileName = "draft.gguf",
                huggingFaceModelName = "test/draft",
                displayName = "Draft Model",
                sha256 = "ghi789",
                sizeInBytes = 500000L,
                modelFileFormat = ModelFileFormat.GGUF,
                temperature = 0.5,
                topK = 30,
                topP = 0.9,
                repetitionPenalty = 1.0,
                maxTokens = 1024,
                contextWindow = 2048,
                systemPrompt = "You are a draft assistant."
            ),
            RemoteModelConfig(
                modelType = ModelType.DRAFT_TWO,
                fileName = "draft2.gguf",
                huggingFaceModelName = "test/draft2",
                displayName = "Draft 2 Model",
                sha256 = "jkl012",
                sizeInBytes = 500000L,
                modelFileFormat = ModelFileFormat.GGUF,
                temperature = 0.5,
                topK = 30,
                topP = 0.9,
                repetitionPenalty = 1.0,
                maxTokens = 1024,
                contextWindow = 2048,
                systemPrompt = "You are draft 2."
            ),
            RemoteModelConfig(
                modelType = ModelType.FAST,
                fileName = "fast.gguf",
                huggingFaceModelName = "test/fast",
                displayName = "Fast Model",
                sha256 = "mno345",
                sizeInBytes = 300000L,
                modelFileFormat = ModelFileFormat.GGUF,
                temperature = 0.3,
                topK = 20,
                topP = 0.8,
                repetitionPenalty = 1.0,
                maxTokens = 512,
                contextWindow = 1024,
                systemPrompt = "Be quick."
            ),
            RemoteModelConfig(
                modelType = ModelType.FINAL_SYNTHESIS,
                fileName = "final_synthesis.gguf",
                huggingFaceModelName = "test/final",
                displayName = "Final Synthesis Model",
                sha256 = "pqr678",
                sizeInBytes = 300000L,
                modelFileFormat = ModelFileFormat.GGUF,
                temperature = 0.3,
                topK = 20,
                topP = 0.8,
                repetitionPenalty = 1.0,
                maxTokens = 512,
                contextWindow = 1024,
                systemPrompt = "You are a final synthesizer."
            )
        )

        // When
        val modelConfigs = fetcher.toModelConfigurations(remoteConfigs)

        // Then
        assertEquals(6, modelConfigs.size)

        val mainConfig = modelConfigs.first { it.modelType == ModelType.MAIN }
        val visionConfig = modelConfigs.first { it.modelType == ModelType.VISION }
        val draftOneConfig = modelConfigs.first { it.modelType == ModelType.DRAFT_ONE }
        val draftTwoConfig = modelConfigs.first { it.modelType == ModelType.DRAFT_TWO }
        val fastConfig = modelConfigs.first { it.modelType == ModelType.FAST }
        val finalSynthesisConfig = modelConfigs.first { it.modelType == ModelType.FINAL_SYNTHESIS }

        assertEquals(0.7, mainConfig.tunings.temperature)
        assertEquals(0.7, visionConfig.tunings.temperature)
        assertEquals(0.5, draftOneConfig.tunings.temperature)
        assertEquals(0.5, draftTwoConfig.tunings.temperature)
        assertEquals(0.3, fastConfig.tunings.temperature)
        assertEquals(0.3, finalSynthesisConfig.tunings.temperature)
        assertEquals("You are a final synthesizer.", finalSynthesisConfig.persona.systemPrompt)
    }
}
