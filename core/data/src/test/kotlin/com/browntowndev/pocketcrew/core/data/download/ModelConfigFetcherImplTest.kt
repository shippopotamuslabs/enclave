package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelAsset
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
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
    fun toLocalModelAssets_preservesAllTuningFields() = runTest {
        // Given
        val remoteAssets = listOf(
            RemoteModelAsset(
                huggingFaceModelName = "test/model",
                fileName = "model.gguf",
                sha256 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.GGUF,
                configurations = listOf(
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-main-1"),
                        displayName = "Test Model",
                        systemPrompt = "You are helpful.",
                        temperature = 0.8,
                        topK = 50,
                        topP = 0.9,
                        minP = 0.05,
                        repetitionPenalty = 1.2,
                        maxTokens = 4096,
                        contextWindow = 8192,
                        thinkingEnabled = true,
                        defaultAssignments = listOf(ModelType.MAIN)
                    )
                )
            )
        )

        // When
        val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

        // Then
        assertEquals(1, modelAssets.size)
        val config = modelAssets[0].configurations.first()
        assertEquals(0.8, config.temperature)
        assertEquals(50, config.topK)
        assertEquals(0.9, config.topP)
        assertEquals(0.05, config.minP)
        assertEquals(1.2, config.repetitionPenalty)
        assertEquals(4096, config.maxTokens)
        assertEquals(8192, config.contextWindow)
        assertTrue(config.thinkingEnabled)
        assertTrue(config.isSystemPreset)
    }

    @Test
    fun toLocalModelAssets_preservesIsMultimodal() = runTest {
        // Given
        val remoteAssets = listOf(
            RemoteModelAsset(
                huggingFaceModelName = "test/vision",
                fileName = "vision.gguf",
                sha256 = "def456",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.GGUF,
                isMultimodal = true,
                configurations = listOf(
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-vision-1"),
                        displayName = "Vision Model",
                        systemPrompt = "You are a vision assistant.",
                        temperature = 0.7,
                        topK = 40,
                        topP = 0.95,
                        minP = 0.05,
                        repetitionPenalty = 1.1,
                        maxTokens = 2048,
                        contextWindow = 4096,
                        defaultAssignments = listOf(ModelType.FAST)
                    )
                )
            )
        )

        // When
        val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

        // Then
        assertTrue(modelAssets[0].metadata.isMultimodal)
    }

    @Test
    fun toLocalModelAssets_preservesOptionalMmprojMetadata() = runTest {
        val remoteAssets = listOf(
            RemoteModelAsset(
                huggingFaceModelName = "test/vision",
                fileName = "vision.gguf",
                sha256 = "def456",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.GGUF,
                isMultimodal = true,
                mmprojFileName = "mmproj.gguf",
                mmprojSha256 = "proj123",
                mmprojSizeInBytes = 123456L,
                configurations = listOf(
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-vision-1"),
                        displayName = "Vision Model",
                        systemPrompt = "You are a vision assistant.",
                        temperature = 0.7,
                        topK = 40,
                        topP = 0.95,
                        minP = 0.05,
                        repetitionPenalty = 1.1,
                        maxTokens = 2048,
                        contextWindow = 4096,
                        defaultAssignments = listOf(ModelType.FAST)
                    )
                )
            )
        )

        val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

        val metadata = modelAssets[0].metadata
        assertEquals("mmproj.gguf", metadata.mmprojRemoteFileName)
        assertEquals("mmproj.gguf", metadata.mmprojLocalFileName)
        assertEquals("proj123", metadata.mmprojSha256)
        assertEquals(123456L, metadata.mmprojSizeInBytes)
    }

    @Test
    fun toLocalModelAssets_handlesMultipleConfigsPerAsset() = runTest {
        // Given - one asset with two configurations (e.g., fast and thinking)
        val remoteAssets = listOf(
            RemoteModelAsset(
                huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                fileName = "gemma-4-E4B-it.litertlm",
                sha256 = "abc123",
                sizeInBytes = 3654467584L,
                modelFileFormat = ModelFileFormat.LITERTLM,
                isMultimodal = true,
                configurations = listOf(
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-fast-1"),
                        displayName = "Gemma 4 E4B (Fast)",
                        systemPrompt = "Be quick.",
                        temperature = 0.8,
                        topK = 50,
                        topP = 0.9,
                        minP = 0.07,
                        repetitionPenalty = 1.05,
                        maxTokens = 2048,
                        contextWindow = 8192,
                        thinkingEnabled = false,
                        defaultAssignments = listOf(ModelType.FAST)
                    ),
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-thinking-1"),
                        displayName = "Gemma 4 E4B (Thinking)",
                        systemPrompt = "Think deeply.",
                        temperature = 0.05,
                        topK = 50,
                        topP = 0.9,
                        minP = 0.1,
                        repetitionPenalty = 1.05,
                        maxTokens = 2048,
                        contextWindow = 8192,
                        thinkingEnabled = true,
                        defaultAssignments = listOf(ModelType.THINKING, ModelType.FINAL_SYNTHESIS)
                    )
                )
            )
        )

        // When
        val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

        // Then
        assertEquals(1, modelAssets.size)
        assertEquals(2, modelAssets[0].configurations.size)
        assertEquals("Gemma 4 E4B (Fast)", modelAssets[0].configurations[0].displayName)
        assertEquals("Gemma 4 E4B (Thinking)", modelAssets[0].configurations[1].displayName)
        assertFalse(modelAssets[0].configurations[0].thinkingEnabled)
        assertTrue(modelAssets[0].configurations[1].thinkingEnabled)
        assertEquals(listOf(ModelType.FAST), modelAssets[0].configurations[0].defaultAssignments)
        assertEquals(listOf(ModelType.THINKING, ModelType.FINAL_SYNTHESIS), modelAssets[0].configurations[1].defaultAssignments)
    }

    @Test
    fun toLocalModelAssets_preservesSource() = runTest {
        // Given
        val remoteAssets = listOf(
            RemoteModelAsset(
                huggingFaceModelName = "test/model",
                fileName = "model.gguf",
                sha256 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.GGUF,
                source = DownloadSource.CLOUDFLARE_R2,
                configurations = listOf(
                    RemoteModelConfiguration(
                        configId = LocalModelConfigurationId("config-main-1"),
                        displayName = "Test Model",
                        systemPrompt = "You are helpful.",
                        temperature = 0.8,
                        topK = 50,
                        topP = 0.9,
                        minP = 0.05,
                        repetitionPenalty = 1.2,
                        maxTokens = 4096,
                        contextWindow = 8192,
                        defaultAssignments = listOf(ModelType.MAIN)
                    )
                )
            )
        )

        // When
        val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

        // Then
        assertEquals(DownloadSource.CLOUDFLARE_R2, modelAssets[0].metadata.source)
    }

    @Test
    fun fetchRemoteConfig_parsesAssetCentricJson() = runTest {
        // Given
        val json = """
            {
              "assets": [
                {
                  "huggingFaceModelName": "test/model",
                  "fileName": "model.gguf",
                  "sha256": "abc123",
                  "sizeInBytes": 1000000,
                  "source": "R2",
                  "isMultimodal": true,
                  "configurations": [
                    {
                      "configId": "config-main-1",
                      "displayName": "Test Model",
                      "systemPrompt": "You are helpful.",
                      "temperature": 0.7,
                      "topK": 40,
                      "topP": 0.95,
                      "minP": 0.05,
                      "repetitionPenalty": 1.1,
                      "maxTokens": 2048,
                      "contextWindow": 4096,
                      "thinkingEnabled": false,
                      "defaultAssignments": ["main", "fast"]
                    }
                  ]
                }
              ]
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
        assertTrue(result.isSuccess)
        val assets = result.getOrNull()!!
        assertEquals(1, assets.size)
        assertEquals("model.gguf", assets[0].metadata.localFileName)
        assertEquals(DownloadSource.CLOUDFLARE_R2, assets[0].metadata.source)
        assertTrue(assets[0].metadata.isMultimodal)
        assertEquals(1, assets[0].configurations.size)
        assertEquals("Test Model", assets[0].configurations[0].displayName)
        assertEquals(listOf(ModelType.MAIN, ModelType.FAST), assets[0].configurations[0].defaultAssignments)
    }

    @Test
    fun fetchRemoteConfig_defaultsToHFSource() = runTest {
        // Given
        val json = """
            {
              "assets": [
                {
                  "fileName": "model.gguf",
                  "sha256": "abc123",
                  "sizeInBytes": 1000000,
                  "configurations": [
                    {
                      "configId": "config-main-1",
                      "displayName": "Test Model",
                      "systemPrompt": "Hi",
                      "temperature": 0.7,
                      "topK": 40,
                      "topP": 0.95,
                      "minP": 0.05,
                      "repetitionPenalty": 1.1,
                      "maxTokens": 2048,
                      "contextWindow": 4096,
                      "defaultAssignments": ["main"]
                    }
                  ]
                }
              ]
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
        assertTrue(result.isSuccess)
        val assets = result.getOrNull()!!
        assertEquals(DownloadSource.HUGGING_FACE, assets[0].metadata.source)
    }

    @Test
    fun fetchRemoteConfig_parsesMultipleAssetsWithSharedFile() = runTest {
        // Given - two separate assets (different files)
        val json = """
            {
              "assets": [
                {
                  "huggingFaceModelName": "test/draft",
                  "fileName": "draft.gguf",
                  "sha256": "ghi789",
                  "sizeInBytes": 500000,
                  "configurations": [
                    {
                      "configId": "config-draft-1",
                      "displayName": "Draft Model",
                      "systemPrompt": "You are a draft.",
                      "temperature": 0.5,
                      "topK": 30,
                      "topP": 0.9,
                      "minP": 0.05,
                      "repetitionPenalty": 1.0,
                      "maxTokens": 1024,
                      "contextWindow": 2048,
                      "defaultAssignments": ["draft_one"]
                    }
                  ]
                },
                {
                  "huggingFaceModelName": "test/model",
                  "fileName": "model.gguf",
                  "sha256": "abc123",
                  "sizeInBytes": 1000000,
                  "isMultimodal": true,
                  "configurations": [
                    {
                      "configId": "config-fast-1",
                      "displayName": "Fast Model",
                      "systemPrompt": "Be quick.",
                      "temperature": 0.8,
                      "topK": 50,
                      "topP": 0.9,
                      "minP": 0.07,
                      "repetitionPenalty": 1.05,
                      "maxTokens": 2048,
                      "contextWindow": 8192,
                      "defaultAssignments": ["fast"]
                    },
                    {
                      "configId": "config-thinking-1",
                      "displayName": "Thinking Model",
                      "systemPrompt": "Think deeply.",
                      "temperature": 0.05,
                      "topK": 50,
                      "topP": 0.9,
                      "minP": 0.1,
                      "repetitionPenalty": 1.05,
                      "maxTokens": 2048,
                      "contextWindow": 8192,
                      "thinkingEnabled": true,
                      "defaultAssignments": ["thinking"]
                    }
                  ]
                }
              ]
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
        assertTrue(result.isSuccess)
        val assets = result.getOrNull()!!
        assertEquals(2, assets.size)
        assertEquals(1, assets[0].configurations.size)
        assertEquals(2, assets[1].configurations.size)
        assertTrue(assets[1].metadata.isMultimodal)
        assertFalse(assets[0].metadata.isMultimodal)
    }
}

private fun assertFalse(condition: Boolean) {
    org.junit.jupiter.api.Assertions.assertFalse(condition)
}
