package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolvedAssignedModelSelection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlayTtsWithMedia3UseCaseTest {

    private val resolveSelectionUseCase: ResolveAssignedModelSelectionUseCase = mockk()
    private val playbackController: TtsPlaybackControllerPort = mockk(relaxed = true)
    private val apiKeyProvider: ApiKeyProviderPort = mockk()
    private lateinit var useCase: PlayTtsWithMedia3UseCase

    private val testTtsAsset = TtsProviderAsset(
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
        useCase = PlayTtsWithMedia3UseCase(resolveSelectionUseCase, playbackController, apiKeyProvider)
    }

    @Test
    fun `emits Initializing then Playing and Completed on success`() = runTest {
        val selection = ResolvedAssignedModelSelection(
            ttsAsset = testTtsAsset,
        )
        coEvery { resolveSelectionUseCase(ModelType.TTS) } returns selection
        every { apiKeyProvider.getApiKey("openai-key") } returns "sk-test"
        every { playbackController.play("Hello") } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Completed,
        )

        val statuses = useCase("Hello").toList()

        assertEquals(3, statuses.size)
        assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
        assertTrue(statuses[1] is TtsPlaybackStatus.Playing)
        assertTrue(statuses[2] is TtsPlaybackStatus.Completed)
    }

    @Test
    fun `emits Error when no TTS provider is assigned`() = runTest {
        coEvery { resolveSelectionUseCase(ModelType.TTS) } returns null

        val statuses = useCase("Hello").toList()

        assertEquals(2, statuses.size)
        assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
        val error = statuses[1] as TtsPlaybackStatus.Error
        assertTrue(error.message.contains("No TTS provider assigned"))
    }

    @Test
    fun `emits Error when TTS asset is missing`() = runTest {
        val selection = ResolvedAssignedModelSelection(
            ttsAsset = null,
        )
        coEvery { resolveSelectionUseCase(ModelType.TTS) } returns selection

        val statuses = useCase("Hello").toList()

        assertEquals(2, statuses.size)
        assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
        val error = statuses[1] as TtsPlaybackStatus.Error
        assertTrue(error.message.contains("invalid"))
    }

    @Test
    fun `emits Error when API key is missing`() = runTest {
        val selection = ResolvedAssignedModelSelection(
            ttsAsset = testTtsAsset,
        )
        coEvery { resolveSelectionUseCase(ModelType.TTS) } returns selection
        every { apiKeyProvider.getApiKey("openai-key") } returns null

        val statuses = useCase("Hello").toList()

        assertEquals(2, statuses.size)
        assertTrue(statuses[0] is TtsPlaybackStatus.Initializing)
        val error = statuses[1] as TtsPlaybackStatus.Error
        assertTrue(error.message.contains("API key not found"))
    }

    @Test
    fun `emits Error from controller`() = runTest {
        val selection = ResolvedAssignedModelSelection(
            ttsAsset = testTtsAsset,
        )
        coEvery { resolveSelectionUseCase(ModelType.TTS) } returns selection
        every { apiKeyProvider.getApiKey("openai-key") } returns "sk-test"
        every { playbackController.play("Hello") } returns flowOf(
            TtsPlaybackStatus.Initializing,
            TtsPlaybackStatus.Error("Network error"),
        )

        val statuses = useCase("Hello").toList()

        assertTrue(statuses.any { it is TtsPlaybackStatus.Error })
    }

    @Test
    fun `stop delegates to playback controller`() {
        useCase.stop()
        verify { playbackController.stop() }
    }

    @Test
    fun `never starts Media3 when no TTS provider is assigned`() = runTest {
        coEvery { resolveSelectionUseCase(ModelType.TTS) } returns null

        useCase("Hello").toList()

        verify(exactly = 0) { playbackController.play(any()) }
    }

    @Test
    fun `emits Stopped when controller emits Stopped`() = runTest {
        val selection = ResolvedAssignedModelSelection(
            ttsAsset = testTtsAsset,
        )
        coEvery { resolveSelectionUseCase(ModelType.TTS) } returns selection
        every { apiKeyProvider.getApiKey("openai-key") } returns "sk-test"
        every { playbackController.play("Hello") } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Stopped,
        )

        val statuses = useCase("Hello").toList()

        assertTrue(statuses.any { it is TtsPlaybackStatus.Stopped })
    }
}