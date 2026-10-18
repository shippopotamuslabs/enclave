package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.settings.GetLiveVoiceProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetTtsProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlayTtsWithMedia3UseCaseTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val ttsProviderRepository = FakeTtsProviderRepository()
    private val liveVoiceProviderRepository = FakeLiveVoiceProviderRepository()
    private val playbackController = FakeTtsPlaybackController()
    private val apiKeyProvider = FakeApiKeyProvider()
    private lateinit var useCase: PlayTtsWithMedia3UseCase

    private val testTtsAsset =
        TtsProviderAsset(
            id = TtsProviderId("tts-1"),
            displayName = "OpenAI TTS",
            provider = ApiProvider.OPENAI,
            voiceName = "alloy",
            modelName = "tts-1",
            baseUrl = null,
            credentialAlias = "openai-key",
        )

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
        useCase = PlayTtsWithMedia3UseCase(resolveSelectionUseCase, playbackController, apiKeyProvider)
    }

    private fun assignTtsAsset() {
        defaultModelRepository.seed(
            DefaultModelAssignment(modelType = ModelType.TTS, ttsProviderId = testTtsAsset.id),
        )
        ttsProviderRepository.emit(listOf(testTtsAsset))
    }

    /** Makes the TTS assignment resolve to a non-TTS selection (a Live Voice asset). */
    private fun assignNonTtsSelection() {
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
    }

    @Test
    fun `emits Initializing then Playing and Completed on success`() =
        runTest {
            assignTtsAsset()
            apiKeyProvider.saveApiKey("openai-key", "sk-test")
            playbackController.playFlow =
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Completed,
                )

            val statuses = useCase("Hello").toList()

            assertEquals(3, statuses.size)
            assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
            assertTrue(statuses[1] is TtsPlaybackStatus.Playing)
            assertTrue(statuses[2] is TtsPlaybackStatus.Completed)
            assertEquals(listOf("Hello"), playbackController.playCalls)
        }

    @Test
    fun `emits Error when no TTS provider is assigned`() =
        runTest {
            val statuses = useCase("Hello").toList()

            assertEquals(2, statuses.size)
            assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
            val error = statuses[1] as TtsPlaybackStatus.Error
            assertTrue(error.message.contains("No TTS provider assigned"))
        }

    @Test
    fun `emits Error when TTS asset is missing`() =
        runTest {
            assignNonTtsSelection()

            val statuses = useCase("Hello").toList()

            assertEquals(2, statuses.size)
            assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
            val error = statuses[1] as TtsPlaybackStatus.Error
            assertTrue(error.message.contains("invalid"))
        }

    @Test
    fun `emits Error when API key is missing`() =
        runTest {
            assignTtsAsset()

            val statuses = useCase("Hello").toList()

            assertEquals(2, statuses.size)
            assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
            val error = statuses[1] as TtsPlaybackStatus.Error
            assertTrue(error.message.contains("API key not found"))
        }

    @Test
    fun `emits Error from controller`() =
        runTest {
            assignTtsAsset()
            apiKeyProvider.saveApiKey("openai-key", "sk-test")
            playbackController.playFlow =
                flowOf(
                    TtsPlaybackStatus.Initializing,
                    TtsPlaybackStatus.Error("Network error"),
                )

            val statuses = useCase("Hello").toList()

            assertTrue(statuses.any { it is TtsPlaybackStatus.Error })
        }

    @Test
    fun `stop delegates to playback controller`() {
        useCase.stop()
        assertTrue(playbackController.stopCallCount >= 1)
    }

    @Test
    fun `never starts Media3 when no TTS provider is assigned`() =
        runTest {
            useCase("Hello").toList()

            assertTrue(playbackController.playCalls.isEmpty())
        }

    @Test
    fun `emits Stopped when controller emits Stopped`() =
        runTest {
            assignTtsAsset()
            apiKeyProvider.saveApiKey("openai-key", "sk-test")
            playbackController.playFlow =
                flowOf(
                    TtsPlaybackStatus.Playing,
                    TtsPlaybackStatus.Stopped,
                )

            val statuses = useCase("Hello").toList()

            assertTrue(statuses.any { it is TtsPlaybackStatus.Stopped })
        }
}
