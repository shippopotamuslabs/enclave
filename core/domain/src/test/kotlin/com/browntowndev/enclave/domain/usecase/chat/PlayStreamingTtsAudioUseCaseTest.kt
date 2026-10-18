package com.browntowndev.enclave.domain.usecase.chat

import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeAudioPlayer
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStreamingAudioPlayer
import com.browntowndev.enclave.core.testing.FakeStreamingTtsService
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.core.testing.FakeTtsService
import com.browntowndev.enclave.core.testing.FakeTtsServiceFactory
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.TtsAudioChunk
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.settings.GetLiveVoiceProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetTtsProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayStreamingTtsAudioUseCaseTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val ttsProviderRepository = FakeTtsProviderRepository()
    private val liveVoiceProviderRepository = FakeLiveVoiceProviderRepository()
    private val ttsFactory = FakeTtsServiceFactory()
    private val streamingAudioPlayer = FakeStreamingAudioPlayer()
    private val audioPlayer = FakeAudioPlayer()
    private val apiKeyProvider = FakeApiKeyProvider()
    private lateinit var useCase: PlayStreamingTtsAudioUseCase

    @BeforeEach
    fun setUp() {
        val resolveSelectionUseCase =
            ResolveAssignedModelSelectionUseCase(
                GetDefaultModelsUseCaseImpl(defaultModelRepository),
                GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository()),
                GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
                GetTtsProvidersUseCase(ttsProviderRepository),
                GetLiveVoiceProvidersUseCase(liveVoiceProviderRepository),
            )
        useCase =
            PlayStreamingTtsAudioUseCase(
                resolveSelectionUseCase,
                ttsFactory,
                streamingAudioPlayer,
                audioPlayer,
                apiKeyProvider,
                FakeLoggingPort(),
            )
    }

    private fun assignTtsAsset(asset: TtsProviderAsset) {
        defaultModelRepository.seed(
            DefaultModelAssignment(modelType = ModelType.TTS, ttsProviderId = asset.id),
        )
        ttsProviderRepository.emit(listOf(asset))
    }

    private fun openAiAsset() =
        TtsProviderAsset(
            TtsProviderId("1"),
            "OpenAI",
            ApiProvider.OPENAI,
            "alloy",
            null,
            null,
            "alias-1",
        )

    @Test
    fun `invoke returns initializing status first`() =
        runTest {
            val ttsAsset = openAiAsset()
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks =
                flowOf(
                    TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
                    TtsAudioChunk.Done,
                )
            ttsFactory.streamingService = streamingService

            useCase("Hello").test {
                assertTrue(awaitItem() is StreamingPlaybackStatus.Initializing)
                awaitItem() // Playing
                awaitItem() // Completed
                awaitComplete()
            }
        }

    @Test
    fun `invoke emits playing status when audio chunks arrive`() =
        runTest {
            val ttsAsset =
                TtsProviderAsset(
                    TtsProviderId("1"),
                    "xAI",
                    ApiProvider.XAI,
                    "echo",
                    null,
                    null,
                    "alias-1",
                )
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks =
                flowOf(
                    TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
                    TtsAudioChunk.Data(byteArrayOf(4, 5, 6)),
                    TtsAudioChunk.Done,
                )
            ttsFactory.streamingService = streamingService

            useCase("Hello").test {
                awaitItem() // Initializing
                val playing = awaitItem()
                assertTrue(playing is StreamingPlaybackStatus.Playing)
                awaitItem() // Completed
                awaitComplete()
            }
        }

    @Test
    fun `invoke emits error when no TTS provider assigned`() =
        runTest {
            // The TTS assignment resolves to a non-TTS selection (a Live Voice asset).
            val liveVoiceAsset =
                LiveVoiceProviderAsset(
                    id = LiveVoiceProviderId("lv-1"),
                    displayName = "OpenAI RT",
                    provider = ApiProvider.OPENAI,
                    voiceName = "alloy",
                    credentialAlias = "alias",
                )
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.TTS, liveVoiceProviderId = liveVoiceAsset.id),
            )
            liveVoiceProviderRepository.emit(listOf(liveVoiceAsset))

            useCase("Hello").test {
                awaitItem() // Initializing
                val error = awaitItem()
                assertTrue(error is StreamingPlaybackStatus.Error)
                assertEquals("Assigned TTS model selection is invalid.", (error as StreamingPlaybackStatus.Error).message)
                awaitComplete()
            }
        }

    @Test
    fun `invoke falls back to batch when streaming not available`() =
        runTest {
            val ttsAsset =
                TtsProviderAsset(
                    TtsProviderId("1"),
                    "Google",
                    ApiProvider.GOOGLE,
                    "voice",
                    null,
                    null,
                    "alias-1",
                )
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            ttsFactory.streamingService = null
            val batchService = FakeTtsService()
            batchService.synthesizeResult = Result.success(byteArrayOf(1, 2, 3))
            ttsFactory.batchService = batchService

            useCase("Hello").test {
                awaitItem() // Initializing
                val playing = awaitItem()
                assertTrue(playing is StreamingPlaybackStatus.Playing)
                val completed = awaitItem()
                assertTrue(completed is StreamingPlaybackStatus.Completed)
                awaitComplete()
            }

            assertEquals(1, audioPlayer.playAudioCalls.size)
        }

    @Test
    fun `invoke emits error when streaming service throws exception`() =
        runTest {
            val ttsAsset = openAiAsset()
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks =
                flow {
                    throw RuntimeException("Network error")
                }
            ttsFactory.streamingService = streamingService

            useCase("Hello").test {
                awaitItem() // Initializing
                awaitItem() // Playing (emitted before collect starts)
                val error = awaitItem()
                assertTrue(error is StreamingPlaybackStatus.Error)
                assertTrue((error as StreamingPlaybackStatus.Error).message.contains("Network error"))
                awaitComplete()
            }
        }

    @Test
    fun `invoke passes modelId to streaming service when present`() =
        runTest {
            val ttsAsset =
                TtsProviderAsset(
                    TtsProviderId("1"),
                    "xAI",
                    ApiProvider.XAI,
                    "echo",
                    "grok-3-audio",
                    null,
                    "alias-1",
                )
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks = flowOf(TtsAudioChunk.Done)
            ttsFactory.streamingService = streamingService

            useCase("Hello").test {
                awaitItem() // Initializing
                awaitItem() // Playing
                awaitItem() // Completed
                awaitComplete()
            }

            assertEquals(
                listOf(FakeStreamingTtsService.SynthesizeStreamingCall("Hello", "echo", "grok-3-audio")),
                streamingService.synthesizeStreamingCalls,
            )
        }

    @Test
    fun `stop calls both player stops`() {
        useCase.stop()
        assertTrue(streamingAudioPlayer.stopCallCount >= 1)
        assertTrue(audioPlayer.stopCallCount >= 1)
    }

    @Test
    fun `invoke enqueues audio chunks to player`() =
        runTest {
            val ttsAsset = openAiAsset()
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            val chunk1 = byteArrayOf(1, 2, 3)
            val chunk2 = byteArrayOf(4, 5, 6)
            streamingService.chunks =
                flowOf(
                    TtsAudioChunk.Data(chunk1),
                    TtsAudioChunk.Data(chunk2),
                    TtsAudioChunk.Done,
                )
            ttsFactory.streamingService = streamingService

            useCase("Hello").collect {}

            assertEquals(2, streamingAudioPlayer.enqueuedChunks.size)
            assertTrue(streamingAudioPlayer.enqueuedChunks[0].contentEquals(chunk1))
            assertTrue(streamingAudioPlayer.enqueuedChunks[1].contentEquals(chunk2))
        }

    @Test
    fun `invoke starts player`() =
        runTest {
            val ttsAsset = openAiAsset()
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks =
                flowOf(
                    TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
                    TtsAudioChunk.Done,
                )
            ttsFactory.streamingService = streamingService

            useCase("Hello").collect {}

            assertTrue(streamingAudioPlayer.startPlaybackCallCount >= 1)
        }

    @Test
    fun `invoke emits error when API key not found`() =
        runTest {
            val ttsAsset = openAiAsset()
            assignTtsAsset(ttsAsset)

            useCase("Hello").test {
                awaitItem() // Initializing
                val error = awaitItem()
                assertTrue(error is StreamingPlaybackStatus.Error)
                assertTrue((error as StreamingPlaybackStatus.Error).message.contains("API key not found"))
                awaitComplete()
            }

            // Streaming player was stopped on error
            assertTrue(streamingAudioPlayer.stopCallCount >= 1)
        }

    @Test
    fun `invoke emits error when streaming service returns error chunk`() =
        runTest {
            val ttsAsset =
                TtsProviderAsset(
                    TtsProviderId("1"),
                    "xAI",
                    ApiProvider.XAI,
                    "echo",
                    null,
                    null,
                    "alias-1",
                )
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks =
                flowOf(
                    TtsAudioChunk.Error("WebSocket connection failed"),
                )
            ttsFactory.streamingService = streamingService

            useCase("Hello").test {
                awaitItem() // Initializing
                awaitItem() // Playing
                val error = awaitItem()
                assertTrue(error is StreamingPlaybackStatus.Error)
                assertTrue((error as StreamingPlaybackStatus.Error).message.contains("WebSocket connection failed"))
                awaitComplete()
            }

            // Streaming player was stopped on error
            assertTrue(streamingAudioPlayer.stopCallCount >= 1)
        }

    @Test
    fun `invoke emits error when streaming service returns error after data chunks`() =
        runTest {
            val ttsAsset =
                TtsProviderAsset(
                    TtsProviderId("1"),
                    "xAI",
                    ApiProvider.XAI,
                    "echo",
                    null,
                    null,
                    "alias-1",
                )
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks =
                flow {
                    emit(TtsAudioChunk.Data(byteArrayOf(1, 2, 3)))
                    emit(TtsAudioChunk.Data(byteArrayOf(4, 5, 6)))
                    emit(TtsAudioChunk.Error("Connection dropped mid-stream"))
                }
            ttsFactory.streamingService = streamingService

            useCase("Hello").test {
                awaitItem() // Initializing
                awaitItem() // Playing
                val error = awaitItem()
                assertTrue(error is StreamingPlaybackStatus.Error)
                assertTrue((error as StreamingPlaybackStatus.Error).message.contains("Connection dropped mid-stream"))
                awaitComplete()
            }

            // Streaming player was stopped on error
            assertTrue(streamingAudioPlayer.stopCallCount >= 1)
        }

    @Test
    fun `invoke batch fallback passes correct voice and modelId`() =
        runTest {
            val ttsAsset =
                TtsProviderAsset(
                    TtsProviderId("1"),
                    "Google",
                    ApiProvider.GOOGLE,
                    "google-voice",
                    "model-name",
                    "https://custom.api.com",
                    "alias-1",
                )
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            ttsFactory.streamingService = null
            val batchService = FakeTtsService()
            batchService.synthesizeResult = Result.success(byteArrayOf(1))
            ttsFactory.batchService = batchService

            useCase("Hello").collect {}

            assertEquals(
                listOf(FakeTtsServiceFactory.CreateCall(ApiProvider.GOOGLE, "test-api-key", "https://custom.api.com")),
                ttsFactory.createStreamingCalls,
            )
            assertEquals(
                listOf(FakeTtsServiceFactory.CreateCall(ApiProvider.GOOGLE, "test-api-key", "https://custom.api.com")),
                ttsFactory.createCalls,
            )
            assertEquals(
                listOf(FakeTtsService.SynthesizeCall("Hello", "google-voice", "model-name")),
                batchService.synthesizeCalls,
            )
        }

    @Test
    fun `invoke does not fall back to batch when streaming succeeds`() =
        runTest {
            val ttsAsset = openAiAsset()
            assignTtsAsset(ttsAsset)
            apiKeyProvider.saveApiKey("alias-1", "test-api-key")
            val streamingService = FakeStreamingTtsService()
            streamingService.chunks =
                flowOf(
                    TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
                    TtsAudioChunk.Done,
                )
            ttsFactory.streamingService = streamingService

            useCase("Hello").collect {}

            // Batch service was NEVER called
            assertTrue(ttsFactory.createCalls.isEmpty())
            assertTrue(audioPlayer.playAudioCalls.isEmpty())
        }

    @Test
    fun `stop does not throw when players have not been initialized`() {
        // Should be safe to call stop() before any playback
        useCase.stop()
        useCase.stop() // Call twice to verify idempotent

        assertEquals(2, streamingAudioPlayer.stopCallCount)
        assertEquals(2, audioPlayer.stopCallCount)
    }
}
