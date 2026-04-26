package com.browntowndev.pocketcrew.domain.usecase.chat

import app.cash.turbine.test
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.TtsAudioChunk
import com.browntowndev.pocketcrew.domain.port.inference.StreamingTtsServicePort
import com.browntowndev.pocketcrew.domain.port.inference.TtsServiceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.TtsServicePort
import com.browntowndev.pocketcrew.domain.port.media.AudioPlayerPort
import com.browntowndev.pocketcrew.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.ApiKeyProviderPort
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.ResolvedAssignedModelSelection
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayStreamingTtsAudioUseCaseTest {

    private val resolveSelectionUseCase: ResolveAssignedModelSelectionUseCase = mockk()
    private val ttsFactory: TtsServiceFactoryPort = mockk()
    private val streamingAudioPlayer: StreamingAudioPlayerPort = mockk(relaxed = true)
    private val audioPlayer: AudioPlayerPort = mockk(relaxed = true)
    private val apiKeyProvider: ApiKeyProviderPort = mockk()
    private val loggingPort: LoggingPort = mockk(relaxed = true)
    private lateinit var useCase: PlayStreamingTtsAudioUseCase

    @BeforeEach
    fun setUp() {
        useCase = PlayStreamingTtsAudioUseCase(
            resolveSelectionUseCase,
            ttsFactory,
            streamingAudioPlayer,
            audioPlayer,
            apiKeyProvider,
            loggingPort
        )
    }

    @Test
    fun `invoke returns initializing status first`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI,
            "alloy", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.OPENAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming("Hello", "alloy", null) } returns flowOf(
            TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
            TtsAudioChunk.Done
        )

        useCase("Hello").test {
            assertTrue(awaitItem() is StreamingPlaybackStatus.Initializing)
            awaitItem() // Playing
            awaitItem() // Completed
            awaitComplete()
        }
    }

    @Test
    fun `invoke emits playing status when audio chunks arrive`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "xAI", ApiProvider.XAI,
            "echo", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.XAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming("Hello", "echo", null) } returns flowOf(
            TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
            TtsAudioChunk.Data(byteArrayOf(4, 5, 6)),
            TtsAudioChunk.Done
        )

        useCase("Hello").test {
            awaitItem() // Initializing
            val playing = awaitItem()
            assertTrue(playing is StreamingPlaybackStatus.Playing)
            awaitItem() // Completed
            awaitComplete()
        }
    }

    @Test
    fun `invoke emits error when no TTS provider assigned`() = runTest {
        coEvery { resolveSelectionUseCase(any()) } returns ResolvedAssignedModelSelection(ttsAsset = null)

        useCase("Hello").test {
            awaitItem() // Initializing
            val error = awaitItem()
            assertTrue(error is StreamingPlaybackStatus.Error)
            assertEquals("Assigned TTS model selection is invalid.", (error as StreamingPlaybackStatus.Error).message)
            awaitComplete()
        }
    }

    @Test
    fun `invoke falls back to batch when streaming not available`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "Google", ApiProvider.GOOGLE,
            "voice", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val batchService: TtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.GOOGLE, "test-api-key", null) } returns null
        every { ttsFactory.create(ApiProvider.GOOGLE, "test-api-key", null) } returns batchService
        coEvery { batchService.synthesizeSpeech("Hello", "voice", null) } returns Result.success(byteArrayOf(1, 2, 3))
        coEvery { audioPlayer.playAudio(any()) } returns Unit

        useCase("Hello").test {
            awaitItem() // Initializing
            val playing = awaitItem()
            assertTrue(playing is StreamingPlaybackStatus.Playing)
            val completed = awaitItem()
            assertTrue(completed is StreamingPlaybackStatus.Completed)
            awaitComplete()
        }

        coVerify { audioPlayer.playAudio(any()) }
    }

    @Test
    fun `invoke emits error when streaming service throws exception`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI,
            "alloy", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.OPENAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming(any(), any(), any()) } returns flow {
            throw RuntimeException("Network error")
        }

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
    fun `invoke passes modelId to streaming service when present`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "xAI", ApiProvider.XAI,
            "echo", "grok-3-audio", null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.XAI, "test-api-key", null) } returns streamingService
        every {
            streamingService.synthesizeSpeechStreaming("Hello", "echo", "grok-3-audio")
        } returns flowOf(TtsAudioChunk.Done)

        useCase("Hello").test {
            awaitItem() // Initializing
            awaitItem() // Playing
            awaitItem() // Completed
            awaitComplete()
        }

        verify { streamingService.synthesizeSpeechStreaming("Hello", "echo", "grok-3-audio") }
    }

    @Test
    fun `stop calls both player stops`() {
        useCase.stop()
        verify { streamingAudioPlayer.stop() }
        verify { audioPlayer.stop() }
    }

    @Test
    fun `invoke enqueues audio chunks to player`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI,
            "alloy", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()
        val chunk1 = byteArrayOf(1, 2, 3)
        val chunk2 = byteArrayOf(4, 5, 6)

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.OPENAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming(any(), any(), any()) } returns flowOf(
            TtsAudioChunk.Data(chunk1),
            TtsAudioChunk.Data(chunk2),
            TtsAudioChunk.Done
        )

        useCase("Hello").collect {}

        coVerify { streamingAudioPlayer.enqueueChunk(chunk1) }
        coVerify { streamingAudioPlayer.enqueueChunk(chunk2) }
    }

    @Test
    fun `invoke starts player`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI,
            "alloy", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.OPENAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming(any(), any(), any()) } returns flowOf(
            TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
            TtsAudioChunk.Done
        )

        useCase("Hello").collect {}

        verify { streamingAudioPlayer.startPlayback() }
    }

    @Test
    fun `invoke emits error when API key not found`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI,
            "alloy", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns null

        useCase("Hello").test {
            awaitItem() // Initializing
            val error = awaitItem()
            assertTrue(error is StreamingPlaybackStatus.Error)
            assertTrue((error as StreamingPlaybackStatus.Error).message.contains("API key not found"))
            awaitComplete()
        }

        // Verify streaming player was stopped on error
        verify { streamingAudioPlayer.stop() }
    }

    @Test
    fun `invoke emits error when streaming service returns error chunk`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "xAI", ApiProvider.XAI,
            "echo", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.XAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming(any(), any(), any()) } returns flowOf(
            TtsAudioChunk.Error("WebSocket connection failed")
        )

        useCase("Hello").test {
            awaitItem() // Initializing
            awaitItem() // Playing
            val error = awaitItem()
            assertTrue(error is StreamingPlaybackStatus.Error)
            assertTrue((error as StreamingPlaybackStatus.Error).message.contains("WebSocket connection failed"))
            awaitComplete()
        }

        // Verify streaming player was stopped on error
        verify { streamingAudioPlayer.stop() }
    }

    @Test
    fun `invoke emits error when streaming service returns error after data chunks`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "xAI", ApiProvider.XAI,
            "echo", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.XAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming(any(), any(), any()) } returns flow {
            emit(TtsAudioChunk.Data(byteArrayOf(1, 2, 3)))
            emit(TtsAudioChunk.Data(byteArrayOf(4, 5, 6)))
            emit(TtsAudioChunk.Error("Connection dropped mid-stream"))
        }

        useCase("Hello").test {
            awaitItem() // Initializing
            awaitItem() // Playing
            val error = awaitItem()
            assertTrue(error is StreamingPlaybackStatus.Error)
            assertTrue((error as StreamingPlaybackStatus.Error).message.contains("Connection dropped mid-stream"))
            awaitComplete()
        }

        // Verify streaming player was stopped on error
        verify { streamingAudioPlayer.stop() }
    }

    @Test
    fun `invoke batch fallback passes correct voice and modelId`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "Google", ApiProvider.GOOGLE,
            "google-voice", "model-name", "https://custom.api.com", "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val batchService: TtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.GOOGLE, "test-api-key", "https://custom.api.com") } returns null
        every { ttsFactory.create(ApiProvider.GOOGLE, "test-api-key", "https://custom.api.com") } returns batchService
        coEvery { batchService.synthesizeSpeech("Hello", "google-voice", "model-name") } returns Result.success(byteArrayOf(1))
        coEvery { audioPlayer.playAudio(any()) } returns Unit

        useCase("Hello").collect {}

        coVerify { batchService.synthesizeSpeech("Hello", "google-voice", "model-name") }
    }

    @Test
    fun `invoke does not fall back to batch when streaming succeeds`() = runTest {
        val ttsAsset = TtsProviderAsset(
            TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI,
            "alloy", null, null, "alias-1"
        )
        val selection = ResolvedAssignedModelSelection(ttsAsset = ttsAsset)
        val streamingService: StreamingTtsServicePort = mockk()

        coEvery { resolveSelectionUseCase(any()) } returns selection
        every { apiKeyProvider.getApiKey("alias-1") } returns "test-api-key"
        every { ttsFactory.createStreaming(ApiProvider.OPENAI, "test-api-key", null) } returns streamingService
        every { streamingService.synthesizeSpeechStreaming(any(), any(), any()) } returns flowOf(
            TtsAudioChunk.Data(byteArrayOf(1, 2, 3)),
            TtsAudioChunk.Done
        )

        useCase("Hello").collect {}

        // Verify batch service was NEVER called
        verify(exactly = 0) { ttsFactory.create(any(), any(), any()) }
        coVerify(exactly = 0) { audioPlayer.playAudio(any()) }
    }

    @Test
    fun `stop does not throw when players have not been initialized`() {
        // Should be safe to call stop() before any playback
        useCase.stop()
        useCase.stop() // Call twice to verify idempotent

        verify(exactly = 2) { streamingAudioPlayer.stop() }
        verify(exactly = 2) { audioPlayer.stop() }
    }
}