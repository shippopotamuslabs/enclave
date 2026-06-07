package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.media.LiveVoicePort
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import com.browntowndev.enclave.domain.usecase.settings.ResolvedAssignedModelSelection
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiveVoiceSessionUseCaseTest {
    private val resolveAssignedModelSelectionUseCase = mockk<ResolveAssignedModelSelectionUseCase>()
    private val liveVoicePort = mockk<LiveVoicePort>()
    private val apiKeyProvider = mockk<ApiKeyProviderPort>()

    private val fakeSession =
        object : LiveVoiceSession {
            override fun events() = emptyFlow<com.browntowndev.enclave.domain.port.media.LiveVoiceEvent>()

            override suspend fun sendAudio(pcm16: ByteArray) = Unit

            override suspend fun commitTurn() = Unit

            override suspend fun cancelAssistantResponse() = Unit

            override suspend fun close() = Unit
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

    @Test
    @DisplayName("LV-SESSION-001")
    fun lvSession001_openSessionHappyPath() =
        runTest {
            coEvery { resolveAssignedModelSelectionUseCase(ModelType.LIVE_VOICE) } returns
                ResolvedAssignedModelSelection(liveVoiceAsset = liveVoiceAsset)
            coEvery { apiKeyProvider.getApiKey("alias") } returns "sk-live"
            coEvery { liveVoicePort.openSession(liveVoiceAsset, "sk-live") } returns Result.success(fakeSession)

            val result = useCase()

            assertTrue(result.isSuccess)
            assertEquals(fakeSession, result.getOrNull()?.session)
            assertEquals(liveVoiceAsset, result.getOrNull()?.asset)
        }

    @Test
    @DisplayName("LV-SESSION-002")
    fun lvSession002_noProviderAssigned() =
        runTest {
            coEvery { resolveAssignedModelSelectionUseCase(ModelType.LIVE_VOICE) } returns null

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
            coEvery { resolveAssignedModelSelectionUseCase(ModelType.LIVE_VOICE) } returns
                ResolvedAssignedModelSelection(liveVoiceAsset = liveVoiceAsset)
            coEvery { apiKeyProvider.getApiKey("alias") } returns null

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
            coEvery { resolveAssignedModelSelectionUseCase(ModelType.LIVE_VOICE) } returns
                ResolvedAssignedModelSelection(liveVoiceAsset = null)

            val result = useCase()

            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals("Assigned Live Voice selection is invalid.", error?.message)
        }
}