package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.inference.TtsServiceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.TtsServicePort
import com.browntowndev.pocketcrew.domain.port.media.AudioPlayerPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolvedAssignedModelSelection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlayTtsAudioUseCaseTest {

    private val resolveSelectionUseCase: ResolveAssignedModelSelectionUseCase = mockk()
    private val ttsFactory: TtsServiceFactoryPort = mockk()
    private val audioPlayer: AudioPlayerPort = mockk(relaxed = true)
    private val apiKeyProvider: ApiKeyProviderPort = mockk()
    private lateinit var useCase: PlayTtsAudioUseCase

    @BeforeEach
    fun setUp() {
        useCase = PlayTtsAudioUseCase(resolveSelectionUseCase, ttsFactory, audioPlayer, apiKeyProvider)
    }

    @Test
    fun `invoke synthesizes and plays audio on success`() = runTest {
        val ttsAsset = TtsProviderAsset(TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI, "alloy", null, "alias-1")
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val audioBytes = byteArrayOf(1, 2, 3)
        val ttsService: TtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        coEvery { apiKeyProvider.getApiKey("alias-1") } returns "sk-test"
        every { ttsFactory.create(ApiProvider.OPENAI, "sk-test", null) } returns ttsService
        coEvery { ttsService.synthesizeSpeech("Hello", "alloy") } returns Result.success(audioBytes)

        val result = useCase("Hello")

        assertTrue(result.isSuccess)
        coVerify {
            ttsService.synthesizeSpeech("Hello", "alloy")
            audioPlayer.playAudio(audioBytes)
        }
    }
}
