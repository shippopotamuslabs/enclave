package com.browntowndev.enclave.core.data.download

import android.util.Log
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.RemoteModelAsset
import com.browntowndev.enclave.domain.model.config.RemoteModelConfiguration
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import androidx.test.core.app.ApplicationProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
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

        fetcher =
            ModelConfigFetcherImpl(
                ApplicationProvider.getApplicationContext(),
                mockOkHttpClient,
                mockModelUrlProvider,
            )
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun toLocalModelAssets_preservesAllTuningFields() =
        runTest {
            // Given
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Test Model",
                        huggingFaceModelName = "test/model",
                        fileName = "model.gguf",
                        sha256 = "abc123",
                        sizeInBytes = 1000000L,
                        modelFileFormat = ModelFileFormat.GGUF,
                        configurations =
                            listOf(
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
                                    defaultAssignments = listOf(ModelType.MAIN),
                                ),
                            ),
                    ),
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
    fun toLocalModelAssets_preservesIsMultimodal() =
        runTest {
            // Given
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Vision Model",
                        huggingFaceModelName = "test/vision",
                        fileName = "vision.gguf",
                        sha256 = "def456",
                        sizeInBytes = 2000000L,
                        modelFileFormat = ModelFileFormat.GGUF,
                        isMultimodal = true,
                        configurations =
                            listOf(
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
                                    defaultAssignments = listOf(ModelType.FAST),
                                ),
                            ),
                    ),
                )

            // When
            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            // Then
            assertTrue(modelAssets[0].metadata.isMultimodal)
        }

    @Test
    fun toLocalModelAssets_preservesOptionalMmprojMetadata() =
        runTest {
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Vision Model",
                        huggingFaceModelName = "test/vision",
                        fileName = "vision.gguf",
                        sha256 = "def456",
                        sizeInBytes = 2000000L,
                        modelFileFormat = ModelFileFormat.GGUF,
                        isMultimodal = true,
                        mmprojFileName = "mmproj.gguf",
                        mmprojSha256 = "proj123",
                        mmprojSizeInBytes = 123456L,
                        configurations =
                            listOf(
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
                                    defaultAssignments = listOf(ModelType.FAST),
                                ),
                            ),
                    ),
                )

            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            val metadata = modelAssets[0].metadata
            assertEquals("mmproj.gguf", metadata.mmprojRemoteFileName)
            assertEquals("mmproj.gguf", metadata.mmprojLocalFileName)
            assertEquals("proj123", metadata.mmprojSha256)
            assertEquals(123456L, metadata.mmprojSizeInBytes)
        }

    @Test
    fun toLocalModelAssets_handlesMultipleConfigsPerAsset() =
        runTest {
            // Given - one asset with two configurations (e.g., fast and thinking)
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Gemma 4 E4B",
                        huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                        fileName = "gemma-4-E4B-it.litertlm",
                        sha256 = "abc123",
                        sizeInBytes = 3654467584L,
                        modelFileFormat = ModelFileFormat.LITERTLM,
                        isMultimodal = true,
                        configurations =
                            listOf(
                                RemoteModelConfiguration(
                                    configId = LocalModelConfigurationId("config-fast-1"),
                                    displayName = "Fast",
                                    systemPrompt = "Be quick.",
                                    temperature = 0.8,
                                    topK = 50,
                                    topP = 0.9,
                                    minP = 0.07,
                                    repetitionPenalty = 1.05,
                                    maxTokens = 2048,
                                    contextWindow = 8192,
                                    thinkingEnabled = false,
                                    defaultAssignments = listOf(ModelType.FAST),
                                ),
                                RemoteModelConfiguration(
                                    configId = LocalModelConfigurationId("config-thinking-1"),
                                    displayName = "Thinking",
                                    systemPrompt = "Think deeply.",
                                    temperature = 0.05,
                                    topK = 50,
                                    topP = 0.9,
                                    minP = 0.1,
                                    repetitionPenalty = 1.05,
                                    maxTokens = 2048,
                                    contextWindow = 8192,
                                    thinkingEnabled = true,
                                    defaultAssignments = listOf(ModelType.THINKING, ModelType.FINAL_SYNTHESIS),
                                ),
                            ),
                    ),
                )

            // When
            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            // Then
            assertEquals(1, modelAssets.size)
            assertEquals(2, modelAssets[0].configurations.size)
            assertEquals("Gemma 4 E4B", modelAssets[0].metadata.displayName)
            assertEquals("Fast", modelAssets[0].configurations[0].displayName)
            assertEquals("Thinking", modelAssets[0].configurations[1].displayName)
            assertFalse(modelAssets[0].configurations[0].thinkingEnabled)
            assertTrue(modelAssets[0].configurations[1].thinkingEnabled)
            assertEquals(listOf(ModelType.FAST), modelAssets[0].configurations[0].defaultAssignments)
            assertEquals(listOf(ModelType.THINKING, ModelType.FINAL_SYNTHESIS), modelAssets[0].configurations[1].defaultAssignments)
        }

    @Test
    fun toLocalModelAssets_preservesSource() =
        runTest {
            // Given
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Test Model",
                        huggingFaceModelName = "test/model",
                        fileName = "model.gguf",
                        sha256 = "abc123",
                        sizeInBytes = 1000000L,
                        modelFileFormat = ModelFileFormat.GGUF,
                        source = DownloadSource.CLOUDFLARE_R2,
                        configurations =
                            listOf(
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
                                    defaultAssignments = listOf(ModelType.MAIN),
                                ),
                            ),
                    ),
                )

            // When
            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            // Then
            assertEquals(DownloadSource.CLOUDFLARE_R2, modelAssets[0].metadata.source)
        }

    @Test
    fun toLocalModelAssets_acceptsUtilityAssetWithEmptyConfigurations() =
        runTest {
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Test Model",
                        huggingFaceModelName = "ggerganov/whisper.cpp",
                        fileName = "ggml-base.en.bin",
                        sha256 = "whisper-sha",
                        sizeInBytes = 147_964_211L,
                        modelFileFormat = ModelFileFormat.BIN,
                        source = DownloadSource.HUGGING_FACE,
                        utilityType = UtilityType.WHISPER,
                        configurations = emptyList(),
                    ),
                )

            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            assertEquals(1, modelAssets.size)
            assertEquals(UtilityType.WHISPER, modelAssets.first().metadata.utilityType)
            assertEquals(ModelFileFormat.BIN, modelAssets.first().metadata.modelFileFormat)
            assertTrue(modelAssets.first().configurations.isEmpty())
        }

    @Test
    fun toLocalModelAssets_rejectsUtilityAssetWithConfigurations() =
        runTest {
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Test Model",
                        huggingFaceModelName = "ggerganov/whisper.cpp",
                        fileName = "ggml-base.en.bin",
                        sha256 = "whisper-sha",
                        sizeInBytes = 147_964_211L,
                        modelFileFormat = ModelFileFormat.BIN,
                        utilityType = UtilityType.WHISPER,
                        configurations =
                            listOf(
                                RemoteModelConfiguration(
                                    configId = LocalModelConfigurationId("config-main-1"),
                                    displayName = "Invalid",
                                    systemPrompt = "Invalid",
                                    temperature = 0.7,
                                    topK = 40,
                                    topP = 0.95,
                                    repetitionPenalty = 1.1,
                                    maxTokens = 2048,
                                    contextWindow = 4096,
                                ),
                            ),
                    ),
                )

            kotlin.test.assertFailsWith<IllegalArgumentException> {
                fetcher.toLocalModelAssets(remoteAssets)
            }
        }

    @Test
    fun fetchRemoteConfig_parsesAssetCentricJson() =
        runTest {
            // Given
            val json =
                """
                {
                  "assets": [
                    {
                      "displayName": "Test Model",
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
            val response =
                Response
                    .Builder()
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
    fun fetchRemoteConfig_defaultsToHFSource() =
        runTest {
            // Given
            val json =
                """
                {
                  "assets": [
                    {
                      "displayName": "Test Model",
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
            val response =
                Response
                    .Builder()
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
    fun fetchRemoteConfig_parsesUtilityAsset() =
        runTest {
            val json =
                """
                {
                  "assets": [
                    {
                      "huggingFaceModelName": "ggerganov/whisper.cpp",
                      "fileName": "ggml-base.en.bin",
                      "sha256": "whisper-sha",
                      "sizeInBytes": 147964211,
                      "modelFileFormat": "BIN",
                      "source": "HF",
                      "utilityType": "whisper",
                      "configurations": []
                    }
                  ]
                }
                """.trimIndent()

            val mockCall = mockk<okhttp3.Call>()
            val response =
                Response
                    .Builder()
                    .request(mockk())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody())
                    .build()

            every { mockOkHttpClient.newCall(any()) } returns mockCall
            every { mockCall.execute() } returns response

            val result = fetcher.fetchRemoteConfig()

            assertTrue(result.isSuccess)
            val asset = result.getOrNull()!!.first()
            assertEquals(UtilityType.WHISPER, asset.metadata.utilityType)
            assertEquals(ModelFileFormat.BIN, asset.metadata.modelFileFormat)
            assertTrue(asset.configurations.isEmpty())
        }

    @Test
    fun fetchRemoteConfig_parsesMultipleAssetsWithSharedFile() =
        runTest {
            // Given - two separate assets (different files)
            val json =
                """
                {
                  "assets": [
                    {
                      "displayName": "Draft Model",
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
                      "displayName": "Test Model",
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
            val response =
                Response
                    .Builder()
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

    @Test
    @DisplayName("MPK-001 Parse modelPacks from model_config.json")
    fun mpk001_parseModelPacksFromConfig() =
        runTest {
            val json = sd15PackConfigJson()
            mockConfigResponse(json)

            val result = fetcher.fetchParsedCatalog()

            assertTrue(result.isSuccess)
            val catalog = result.getOrThrow()
            assertEquals(1, catalog.packs.size)
            assertEquals(ModelPackTestFixtures.PACK_ID, catalog.packs[0].packId)
            assertEquals(ModelPackTestFixtures.PACK_DISPLAY, catalog.packs[0].displayName)
            assertEquals(1, catalog.packs[0].members.size)
        }

    @Test
    @DisplayName("MPK-002 Pack member assets resolve from assets[]")
    fun mpk002_packMemberAssetsResolveFromAssets() =
        runTest {
            mockConfigResponse(sd15PackConfigJson())
            val catalog = fetcher.fetchParsedCatalog().getOrThrow()
            val pack = catalog.packById(ModelPackTestFixtures.PACK_ID)!!
            val diffusion = pack.members.single { it.role == ModelPackRole.DIFFUSION }

            assertEquals(ModelPackTestFixtures.DIFFUSION_SHA, diffusion.asset.metadata.sha256)
            assertEquals(ModelPackTestFixtures.DIFFUSION_FILE, diffusion.asset.metadata.localFileName)
        }

    @Test
    @DisplayName("MPK-003 Invalid pack member fileName fails parse")
    fun mpk003_invalidPackMemberFileNameFailsParse() =
        runTest {
            val json =
                sd15PackConfigJson(
                    packMembers =
                        """
                        { "role": "diffusion", "fileName": "missing-file.gguf" }
                        """.trimIndent(),
                )
            mockConfigResponse(json)

            val result = fetcher.fetchParsedCatalog()

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message?.contains("Pack member file not found in assets") == true,
            )
        }

    @Test
    @DisplayName("MPK-027 fetchParsedCatalog reuses last successful catalog on network failure")
    fun mpk027_fetchParsedCatalogReusesLastSuccessfulCatalog() =
        runTest {
            mockConfigResponse(sd15PackConfigJson())
            fetcher.fetchParsedCatalog().getOrThrow()

            val mockCall = mockk<okhttp3.Call>()
            every { mockOkHttpClient.newCall(any()) } returns mockCall
            every { mockCall.execute() } throws java.io.IOException("offline")

            val result = fetcher.fetchParsedCatalog()

            assertTrue(result.isSuccess)
            assertEquals(ModelPackTestFixtures.PACK_ID, result.getOrThrow().packs.single().packId)
        }

    @Test
    @DisplayName("MPK-028 getPersistedParsedCatalog reads on-disk cache without network")
    fun mpk028_getPersistedParsedCatalogReadsDiskWithoutNetwork() =
        runTest {
            mockConfigResponse(sd15PackConfigJson())
            fetcher.fetchParsedCatalog().getOrThrow()

            val offlineFetcher =
                ModelConfigFetcherImpl(
                    ApplicationProvider.getApplicationContext(),
                    mockOkHttpClient,
                    mockModelUrlProvider,
                )
            val mockCall = mockk<okhttp3.Call>()
            every { mockOkHttpClient.newCall(any()) } returns mockCall
            every { mockCall.execute() } throws java.io.IOException("offline")

            val result = offlineFetcher.getPersistedParsedCatalog()

            assertTrue(result.isSuccess)
            assertEquals(ModelPackTestFixtures.PACK_ID, result.getOrThrow().packs.single().packId)
        }

    @Test
    fun `SCN-FETCH-001 fetcher maps asset displayName into metadata`() =
        runTest {
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "Gemma 4 E4B",
                        huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                        fileName = "gemma-4-E4B-it.litertlm",
                        sha256 = "abc123",
                        configurations =
                            listOf(
                                RemoteModelConfiguration(
                                    configId = LocalModelConfigurationId("cfg-fast"),
                                    displayName = "Fast",
                                    systemPrompt = "Be quick.",
                                    temperature = 0.8,
                                    topK = 50,
                                    topP = 0.9,
                                    repetitionPenalty = 1.05,
                                    maxTokens = 2048,
                                    contextWindow = 8192,
                                    defaultAssignments = listOf(ModelType.FAST),
                                ),
                            ),
                    ),
                )

            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            assertEquals("Gemma 4 E4B", modelAssets[0].metadata.displayName)
            assertEquals("Fast", modelAssets[0].configurations[0].displayName)
        }

    @Test
    fun `toLocalModelAssets parses shipped model_config json`() =
        runTest {
            val configFile =
                listOf(
                    java.io.File("model_config.json"),
                    java.io.File("../model_config.json"),
                    java.io.File("../../model_config.json"),
                ).first { it.exists() }
            val body = configFile.readText()

            val mockCall = mockk<okhttp3.Call>()
            val response =
                Response
                    .Builder()
                    .request(mockk())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()

            every { mockOkHttpClient.newCall(any()) } returns mockCall
            every { mockCall.execute() } returns response

            val result = fetcher.fetchRemoteConfig()

            assertTrue(result.isSuccess)
            val miniCpm =
                result.getOrNull()!!.single {
                    it.metadata.localFileName == "MiniCPM-V-4_6-Thinking-Q8_0.gguf"
                }
            assertEquals("MiniCPM-V-4.6 1.3B", miniCpm.metadata.displayName)
        }

    @Test
    fun `SCN-FETCH-002 non-utility asset without any label fails validation`() =
        runTest {
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "",
                        huggingFaceModelName = "",
                        fileName = "model.gguf",
                        sha256 = "abc123",
                        configurations =
                            listOf(
                                RemoteModelConfiguration(
                                    configId = LocalModelConfigurationId("cfg-main"),
                                    displayName = "",
                                    systemPrompt = "Hi",
                                    temperature = 0.7,
                                    topK = 40,
                                    topP = 0.95,
                                    repetitionPenalty = 1.1,
                                    maxTokens = 2048,
                                    contextWindow = 4096,
                                ),
                            ),
                    ),
                )

            val thrown =
                assertThrows(IllegalArgumentException::class.java) {
                    fetcher.toLocalModelAssets(remoteAssets)
                }
            assertEquals("Non-utility asset model.gguf must declare displayName.", thrown.message)
        }

    @Test
    fun `non-utility asset accepts preset displayName when asset label is blank`() =
        runTest {
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        displayName = "",
                        huggingFaceModelName = "openbmb/MiniCPM-V-4.6-Thinking-gguf",
                        fileName = "MiniCPM-V-4_6-Thinking-Q8_0.gguf",
                        sha256 = "abc123",
                        configurations =
                            listOf(
                                RemoteModelConfiguration(
                                    configId = LocalModelConfigurationId("cfg-main"),
                                    displayName = "Analytic Drafter",
                                    systemPrompt = "Hi",
                                    temperature = 0.7,
                                    topK = 40,
                                    topP = 0.95,
                                    repetitionPenalty = 1.1,
                                    maxTokens = 2048,
                                    contextWindow = 4096,
                                ),
                            ),
                    ),
                )

            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            assertEquals("Analytic Drafter", modelAssets[0].metadata.displayName)
        }

    @Test
    fun `SCN-FETCH-003 utility asset maps without asset displayName`() =
        runTest {
            val remoteAssets =
                listOf(
                    RemoteModelAsset(
                        utilityType = UtilityType.WHISPER,
                        displayName = "",
                        fileName = "ggml-base.en.bin",
                        sha256 = "whisper-sha",
                        configurations = emptyList(),
                    ),
                )

            val modelAssets = fetcher.toLocalModelAssets(remoteAssets)

            assertEquals(1, modelAssets.size)
            assertEquals("", modelAssets[0].metadata.displayName)
        }

    private fun mockConfigResponse(json: String) {
        val mockCall = mockk<okhttp3.Call>()
        val response =
            Response
                .Builder()
                .request(mockk())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(json.toResponseBody())
                .build()

        every { mockOkHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response
    }

    private fun sd15PackConfigJson(
        packMembers: String =
            """
            { "role": "diffusion", "fileName": "${ModelPackTestFixtures.DIFFUSION_FILE}" }
            """.trimIndent(),
    ): String =
        """
        {
          "assets": [
            {
              "displayName": "${ModelPackTestFixtures.PACK_DISPLAY}",
              "huggingFaceModelName": "gpustack/stable-diffusion-v1-5-GGUF",
              "fileName": "${ModelPackTestFixtures.DIFFUSION_FILE}",
              "sha256": "${ModelPackTestFixtures.DIFFUSION_SHA}",
              "sizeInBytes": 1747190784,
              "modelFileFormat": "GGUF",
              "source": "HF",
              "configurations": [
                {
                  "configId": "${ModelPackTestFixtures.SD15_CONFIG_ID}",
                  "displayName": "${ModelPackTestFixtures.PACK_DISPLAY}",
                  "systemPrompt": "",
                  "temperature": 0.0,
                  "topK": 1,
                  "topP": 1.0,
                  "minP": 0.0,
                  "repetitionPenalty": 1.0,
                  "maxTokens": 0,
                  "contextWindow": 0,
                  "defaultAssignments": ["image_generation"]
                }
              ]
            }
          ],
          "modelPacks": [
            {
              "packId": "${ModelPackTestFixtures.PACK_ID}",
              "displayName": "${ModelPackTestFixtures.PACK_DISPLAY}",
              "slot": "image_generation",
              "members": [ $packMembers ]
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `DL-001 catalog parses sd15-t2i and sdxl-t2i`() =
        runTest {
            // Parse the SHIPPED model_config.json rather than hand-crafted JSON so the
            // scenario is red until the production catalog carries BOTH packs with the
            // SDXL utility (`sdxl_qnn_pack`) membership wired through the parser.
            val configFile =
                listOf(
                    java.io.File("model_config.json"),
                    java.io.File("../model_config.json"),
                    java.io.File("../../model_config.json"),
                ).first { it.exists() }
            mockConfigResponse(configFile.readText())

            val result = fetcher.fetchParsedCatalog()

            assertTrue(result.isSuccess)
            val catalog = result.getOrThrow()

            // Both image-generation packs are present in catalog order.
            assertEquals(listOf("sd15-t2i", "sdxl-t2i"), catalog.packs.map { it.packId })

            // SD1.5 pack carries its utility (`sd15_qnn_pack`) member.
            val sd15Pack = catalog.packById("sd15-t2i")!!
            assertEquals(
                UtilityType.SD15_QNN_PACK,
                sd15Pack.members
                    .single { it.role == ModelPackRole.TEXT_ENCODER }
                    .asset.metadata.utilityType,
            )

            // SDXL pack carries its diffusion member AND its utility (`sdxl_qnn_pack`) member.
            val sdxlPack = catalog.packById("sdxl-t2i")!!
            assertEquals(
                "realvis_xl_v5_dmd2.gguf",
                sdxlPack.members
                    .single { it.role == ModelPackRole.DIFFUSION }
                    .asset.metadata.localFileName,
            )
            assertEquals(
                UtilityType.SDXL_QNN_PACK,
                sdxlPack.members
                    .single { it.role == ModelPackRole.TEXT_ENCODER }
                    .asset.metadata.utilityType,
            )
        }
}

private fun assertFalse(condition: Boolean) {
    org.junit.jupiter.api.Assertions
        .assertFalse(condition)
}
