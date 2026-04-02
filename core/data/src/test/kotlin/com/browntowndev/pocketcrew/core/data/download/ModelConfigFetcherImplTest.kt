package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
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
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
    fun toLocalModelAssets_preservesAllTuningsFields() = runTest {
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
        val modelAssets = fetcher.toLocalModelAssets(remoteConfigs)

        // Then
        val asset = modelAssets[ModelType.MAIN]!!
        val config = asset.configurations.first()
        assertEquals(0.8, config.temperature)
        assertEquals(50, config.topK)
        assertEquals(0.9, config.topP)
        assertEquals(1.2, config.repetitionPenalty)
        assertEquals(4096, config.maxTokens)
        assertEquals(8192, config.contextWindow)
        assertTrue(config.thinkingEnabled)
    }

    @Test
    fun toLocalModelAssets_preservesVisionCapable() = runTest {
        // Given
        val remoteConfigs = listOf(
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
                systemPrompt = "You are a vision assistant.",
                visionCapable = true
            )
        )

        // When
        val modelAssets = fetcher.toLocalModelAssets(remoteConfigs)

        // Then
        val asset = modelAssets[ModelType.VISION]!!
        assertTrue(asset.metadata.visionCapable)
    }

    @Test
    fun toLocalModelAssets_setsIsSystemPresetToTrue() = runTest {
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
                systemPrompt = "You are helpful."
            )
        )

        // When
        val modelAssets = fetcher.toLocalModelAssets(remoteConfigs)

        // Then
        val asset = modelAssets[ModelType.MAIN]!!
        val config = asset.configurations.first()
        assertTrue(config.isSystemPreset)
    }

    @Test
    fun fetchRemoteConfig_failsIfVisionSlotModelNotVisionCapable() = runTest {
        // Given
        val json = """
            {
                "vision": {
                    "fileName": "not_vision.gguf",
                    "displayName": "Not Vision Model",
                    "sha256": "abc123",
                    "sizeInBytes": 1000000,
                    "temperature": 0.7,
                    "topK": 40,
                    "topP": 0.95,
                    "minP": 0.05,
                    "repetitionPenalty": 1.1,
                    "maxTokens": 2048,
                    "contextWindow": 4096,
                    "systemPrompt": "Hi",
                    "visionCapable": false
                }
            }
        """.trimIndent()

        val mockCall = mockk<okhttp3.Call>()
        val response = Response.Builder()
            .request(mockk())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody())
            .build()

        every { mockOkHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response

        // When
        val result = fetcher.fetchRemoteConfig()

        // Then
        assertTrue(result.isFailure)
        assertEquals("Model configured for 'vision' slot must have 'visionCapable' set to true", result.exceptionOrNull()?.message)
    }

    @Test
    fun toLocalModelAssets_convertsAllModelTypes() = runTest {
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
        val modelAssets = fetcher.toLocalModelAssets(remoteConfigs)

        // Then
        assertEquals(6, modelAssets.size)

        val mainConfig = modelAssets[ModelType.MAIN]!!.configurations.first()
        val visionConfig = modelAssets[ModelType.VISION]!!.configurations.first()
        val draftOneConfig = modelAssets[ModelType.DRAFT_ONE]!!.configurations.first()
        val draftTwoConfig = modelAssets[ModelType.DRAFT_TWO]!!.configurations.first()
        val fastConfig = modelAssets[ModelType.FAST]!!.configurations.first()
        val finalSynthesisConfig = modelAssets[ModelType.FINAL_SYNTHESIS]!!.configurations.first()

        assertEquals(0.7, mainConfig.temperature)
        assertEquals(0.7, visionConfig.temperature)
        assertEquals(0.5, draftOneConfig.temperature)
        assertEquals(0.5, draftTwoConfig.temperature)
        assertEquals(0.3, fastConfig.temperature)
        assertEquals(0.3, finalSynthesisConfig.temperature)
        assertEquals("You are a final synthesizer.", finalSynthesisConfig.systemPrompt)
    }
}
