package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.LiveVoiceToolset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.media.LiveVoicePort
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.settings.GetLiveVoiceProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetTtsProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Recording [LiveVoicePort] fake: returns the canned [openSessionResult] and records every call. */
private class FakeLiveVoicePort : LiveVoicePort {
    data class OpenSessionCall(
        val asset: LiveVoiceProviderAsset,
        val apiKey: String,
        val tools: List<ToolDefinition>,
    )

    val openSessionCalls = mutableListOf<OpenSessionCall>()
    var openSessionResult: Result<LiveVoiceSession> =
        Result.failure(IllegalStateException("FakeLiveVoicePort.openSessionResult not set"))

    override suspend fun openSession(
        asset: LiveVoiceProviderAsset,
        apiKey: String,
        tools: List<ToolDefinition>,
    ): Result<LiveVoiceSession> {
        openSessionCalls += OpenSessionCall(asset, apiKey, tools)
        return openSessionResult
    }
}

class LiveVoiceSessionUseCaseTest {
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val ttsProviderRepository = FakeTtsProviderRepository()
    private val liveVoiceProviderRepository = FakeLiveVoiceProviderRepository()
    private val liveVoicePort = FakeLiveVoicePort()
    private val apiKeyProvider = FakeApiKeyProvider()

    private val resolveAssignedModelSelectionUseCase =
        ResolveAssignedModelSelectionUseCase(
            GetDefaultModelsUseCaseImpl(defaultModelRepository),
            GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository()),
            GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
            GetTtsProvidersUseCase(ttsProviderRepository),
            GetLiveVoiceProvidersUseCase(liveVoiceProviderRepository),
        )

    private val fakeSession =
        object : LiveVoiceSession {
            override fun events() = emptyFlow<com.browntowndev.enclave.domain.port.media.LiveVoiceEvent>()

            override suspend fun sendAudio(pcm16: ByteArray) = Unit

            override suspend fun commitTurn() = Unit

            override suspend fun cancelAssistantResponse() = Unit

            override suspend fun close() = Unit

            override suspend fun sendToolResult(
                callId: String,
                name: String,
                resultJson: String,
            ) = Unit
        }

    private val liveVoiceAsset =
        LiveVoiceProviderAsset(
            id = LiveVoiceProviderId("lv-1"),
            displayName = "OpenAI RT",
            provider = ApiProvider.OPENAI,
            voiceName = "alloy",
            modelName = null,
            baseUrl = null,
            credentialAlias = "alias",
        )

    private val useCase =
        LiveVoiceSessionUseCase(
            resolveAssignedModelSelectionUseCase,
            liveVoicePort,
            apiKeyProvider,
        )

    private fun assignLiveVoiceProvider() {
        defaultModelRepository.seed(
            DefaultModelAssignment(
                modelType = ModelType.LIVE_VOICE,
                liveVoiceProviderId = liveVoiceAsset.id,
            ),
        )
        liveVoiceProviderRepository.emit(listOf(liveVoiceAsset))
    }

    @Test
    @DisplayName("LV-SESSION-001")
    fun lvSession001_openSessionHappyPath() =
        runTest {
            assignLiveVoiceProvider()
            apiKeyProvider.saveApiKey("alias", "sk-live")
            liveVoicePort.openSessionResult = Result.success(fakeSession)

            val result = useCase()

            assertTrue(result.isSuccess)
            assertEquals(fakeSession, result.getOrNull()?.session)
            assertEquals(liveVoiceAsset, result.getOrNull()?.asset)
        }

    @Test
    @DisplayName("LV-SESSION-002")
    fun lvSession002_noProviderAssigned() =
        runTest {
            val result = useCase()

            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals("No Live Voice provider assigned.", error?.message)
        }

    @Test
    @DisplayName("LV-SESSION-003")
    fun lvSession003_keyMissing() =
        runTest {
            assignLiveVoiceProvider()

            val result = useCase()

            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals("API key not found for Live Voice provider: OpenAI RT", error?.message)
        }

    @Test
    @DisplayName("LV-SESSION-004")
    fun lvSession004_selectionWithoutLiveVoiceAsset() =
        runTest {
            // The LIVE_VOICE assignment resolves to a TTS provider, so the
            // selection is non-null but carries no Live Voice asset.
            val ttsAsset =
                TtsProviderAsset(
                    id = TtsProviderId("tts-1"),
                    displayName = "TTS",
                    provider = ApiProvider.OPENAI,
                    voiceName = "alloy",
                    credentialAlias = "alias",
                )
            defaultModelRepository.seed(
                DefaultModelAssignment(
                    modelType = ModelType.LIVE_VOICE,
                    ttsProviderId = ttsAsset.id,
                ),
            )
            ttsProviderRepository.emit(listOf(ttsAsset))

            val result = useCase()

            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals("Assigned Live Voice selection is invalid.", error?.message)
        }

    @Test
    @DisplayName("LV-TOOL-002")
    fun lvTool002_useCaseForwardsLiveVoiceToolsetTools() =
        runTest {
            assignLiveVoiceProvider()
            apiKeyProvider.saveApiKey("alias", "sk-live")
            liveVoicePort.openSessionResult = Result.success(fakeSession)

            val result = useCase()

            assertTrue(result.isSuccess)
            assertEquals(fakeSession, result.getOrNull()?.session)
            assertEquals(liveVoiceAsset, result.getOrNull()?.asset)
            assertEquals(
                listOf(
                    FakeLiveVoicePort.OpenSessionCall(liveVoiceAsset, "sk-live", LiveVoiceToolset.tools),
                ),
                liveVoicePort.openSessionCalls,
            )
        }

    @Test
    @DisplayName("LV-TOOL-MUT-002")
    fun lvToolMut002_useCaseMustPassWhitelistNotEmptyList() =
        runTest {
            assignLiveVoiceProvider()
            apiKeyProvider.saveApiKey("alias", "sk-live")
            liveVoicePort.openSessionResult = Result.success(fakeSession)

            useCase()

            assertEquals(
                listOf(
                    FakeLiveVoicePort.OpenSessionCall(liveVoiceAsset, "sk-live", LiveVoiceToolset.tools),
                ),
                liveVoicePort.openSessionCalls,
            )
            assertTrue(liveVoicePort.openSessionCalls.none { it.tools == emptyList<ToolDefinition>() })
        }
}
