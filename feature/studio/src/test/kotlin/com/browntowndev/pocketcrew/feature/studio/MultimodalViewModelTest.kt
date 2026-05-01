package com.browntowndev.pocketcrew.feature.studio

import app.cash.turbine.test
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ImageGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.MusicGenerationSettings
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.MusicGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateVideoUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModelTest {
    private val imageGenerationPort = mockk<ImageGenerationPort>(relaxed = true)
    private val videoGenerationPort = mockk<VideoGenerationPort>(relaxed = true)
    private val generateMusicUseCase = mockk<GenerateMusicUseCase>(relaxed = true)
    private val shareMediaPort = mockk<ShareMediaPort>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>(relaxed = true)
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>(relaxed = true)
    private val getProviderCapabilitiesUseCase = mockk<GetProviderCapabilitiesUseCase>()
    private val chatUseCases = mockk<ChatUseCases>()
    private val listenToSpeechUseCase = mockk<ListenToSpeechUseCase>()
    private val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
    private val logger = mockk<LoggingPort>(relaxed = true)
    private val allMediaFlow = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
    private lateinit var viewModel: MultimodalViewModel
    private val speechEvents = MutableSharedFlow<SpeechState>(extraBufferCapacity = 8)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        
        val mediaProviderId = MediaProviderId("test-provider")
        val assignment = DefaultModelAssignment(
            modelType = ModelType.IMAGE_GENERATION,
            mediaProviderId = mediaProviderId
        )
        val videoAssignment = DefaultModelAssignment(
            modelType = ModelType.VIDEO_GENERATION,
            mediaProviderId = mediaProviderId
        )
        val providerAsset = MediaProviderAsset(
            id = mediaProviderId,
            displayName = "Test Provider",
            provider = ApiProvider.OPENAI,
            capability = MediaCapability.IMAGE,
            credentialAlias = "test-alias"
        )

        val musicAssignment = DefaultModelAssignment(
            modelType = ModelType.MUSIC_GENERATION,
            mediaProviderId = mediaProviderId
        )

        every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(assignment, videoAssignment, musicAssignment))
        coEvery { defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION) } returns videoAssignment
        coEvery { mediaProviderRepository.getMediaProvider(mediaProviderId) } returns providerAsset
        every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(providerAsset))
        every { studioRepository.observeAllMedia() } returns allMediaFlow
        every { studioRepository.observeAllAlbums() } returns flowOf(emptyList())
        coEvery { videoGenerationPort.generateVideo(any(), any(), any()) } returns Result.success("video".toByteArray())
        coEvery { studioRepository.cacheEphemeralMedia(any(), MediaCapability.VIDEO.name) } returns "cache://video.mp4"
        every { getProviderCapabilitiesUseCase(any()) } returns ProviderCapabilities(
            supportedAspectRatios = AspectRatio.entries.toList(),
            supportedImageQualities = listOf(GenerationQuality.SPEED),
            supportedVideoQualities = listOf(GenerationQuality.SPEED),
            supportedVideoResolutions = listOf("480p"),
            supportedVideoDurations = listOf(5),
            supportsReferenceImage = true,
            supportsVideo = true
        )

        every { listenToSpeechUseCase.invoke(any(), any()) } returns speechEvents
        every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase
        
        viewModel = MultimodalViewModel(
            imageGenerationPort,
            videoGenerationPort,
            generateVideoUseCase(),
            generateMusicUseCase,
            shareMediaPort,
            studioRepository,
            defaultModelRepository,
            mediaProviderRepository,
            getProviderCapabilitiesUseCase,
            chatUseCases,
            playbackController,
            logger,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `capabilities should be updated via use case`() = runTest {
        // Given
        val capabilities = ProviderCapabilities(
            supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE),
            supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.HD),
            supportedVideoQualities = emptyList(),
            supportedVideoResolutions = emptyList(),
            supportedVideoDurations = emptyList(),
            supportsReferenceImage = false,
            supportsVideo = false
        )
        every { getProviderCapabilitiesUseCase(any()) } returns capabilities

        // Start collection to trigger stateIn(SharingStarted.WhileSubscribed)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE), state.capabilities?.supportedAspectRatios)
        assertEquals(listOf(GenerationQuality.SPEED, GenerationQuality.HD), state.capabilities?.supportedImageQualities)
    }

    @Test
    fun `capabilities should be resolved from provider type instead of provider id`() = runTest {
        val mediaProviderId = MediaProviderId("custom-provider-id")
        val assignment = DefaultModelAssignment(
            modelType = ModelType.IMAGE_GENERATION,
            mediaProviderId = mediaProviderId,
        )
        val providerAsset = MediaProviderAsset(
            id = mediaProviderId,
            displayName = "Custom xAI Image Provider",
            provider = ApiProvider.XAI,
            capability = MediaCapability.IMAGE,
            credentialAlias = "xai-alias",
        )
        val xaiCapabilities = ProviderCapabilities(
            supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
            supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
            supportedVideoQualities = emptyList(),
            supportedVideoResolutions = emptyList(),
            supportedVideoDurations = emptyList(),
            supportsReferenceImage = true,
            supportsVideo = false,
        )
        every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(assignment))
        every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(providerAsset))
        every { getProviderCapabilitiesUseCase(ApiProvider.XAI.name) } returns xaiCapabilities

        viewModel = MultimodalViewModel(
            imageGenerationPort,
            videoGenerationPort,
            generateVideoUseCase(),
            generateMusicUseCase,
            shareMediaPort,
            studioRepository,
            defaultModelRepository,
            mediaProviderRepository,
            getProviderCapabilitiesUseCase,
            chatUseCases,
            playbackController,
            logger,
        )
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(
            listOf(GenerationQuality.SPEED, GenerationQuality.QUALITY),
            viewModel.uiState.value.capabilities?.supportedImageQualities,
        )
        verify { getProviderCapabilitiesUseCase(ApiProvider.XAI.name) }
        job.cancel()
    }

    @Test
    fun `selecting template should update selectedTemplateId`() = runTest {
        // Given
        val template = com.browntowndev.pocketcrew.domain.model.media.StudioTemplate(
            id = "test",
            name = "Test",
            exampleUri = "",
            promptPrefix = "Prefix ",
            promptSuffix = " Suffix"
        )

        // Start collection to trigger stateIn(SharingStarted.WhileSubscribed)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        // When
        viewModel.onTemplateSelected(template)
        
        // Then
        assertEquals("test", viewModel.uiState.value.selectedTemplateId)
    }

    @Test
    fun `generate exposes moderation failure message for snackbar`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.failure(
            IllegalStateException("Prompt rejected due to moderation.")
        )

        viewModel.onPromptChange("rejected prompt")
        viewModel.generate()

        assertEquals("Prompt rejected due to moderation.", viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `generate propagates generation count and saves each returned image`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("first".toByteArray(), "second".toByteArray()),
        )

        viewModel.onPromptChange("batch prompt")
        viewModel.onUpdateSettings(ImageGenerationSettings(generationCount = 2))
        viewModel.generate()

        coVerify {
            imageGenerationPort.generateImage(
                prompt = "batch prompt",
                provider = any(),
                settings = ImageGenerationSettings(generationCount = 2),
            )
            studioRepository.cacheEphemeralMedia(
                match<ByteArray> { it.contentEquals("first".toByteArray()) },
                MediaCapability.IMAGE.name,
            )
            studioRepository.cacheEphemeralMedia(
                match<ByteArray> { it.contentEquals("second".toByteArray()) },
                MediaCapability.IMAGE.name,
            )
        }
        job.cancel()
    }

    @Test
    fun `generate clears visible prompt while preserving submitted prompt for generation`() = runTest {
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("image".toByteArray()),
        )

        viewModel.uiState.test {
            assertEquals("", awaitItem().prompt)

            viewModel.onPromptChange("studio prompt")
            assertEquals("studio prompt", awaitItem().prompt)

            viewModel.generate()
            assertEquals("", awaitItem().prompt)

            advanceUntilIdle()

                coVerify {
                    imageGenerationPort.generateImage(
                        prompt = "studio prompt",
                        provider = any(),
                        settings = any(),
                    )
                    studioRepository.cacheEphemeralMedia(
                        match<ByteArray> { it.contentEquals("image".toByteArray()) },
                        MediaCapability.IMAGE.name,
                    )
                }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `scroll threshold only generates after prompt is sent and stops after stop action`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("image".toByteArray()),
        )

        viewModel.onPromptChange("scroll prompt")
        viewModel.onContinualModeToggle(true)
        viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1", gallerySize = 1)

        coVerify(exactly = 0) { imageGenerationPort.generateImage(any(), any(), any()) }

        viewModel.generate()
        assertEquals(true, viewModel.uiState.value.isContinualGenerationActive)

        viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1", gallerySize = 1)
        viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1", gallerySize = 1)

        coVerify(exactly = 2) { imageGenerationPort.generateImage(any(), any(), any()) }

        viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1", gallerySize = 2)

        coVerify(exactly = 3) { imageGenerationPort.generateImage(any(), any(), any()) }

        viewModel.generate()
        assertEquals(false, viewModel.uiState.value.isContinualGenerationActive)

        viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-2", gallerySize = 3)

        coVerify(exactly = 3) { imageGenerationPort.generateImage(any(), any(), any()) }
        job.cancel()
    }

    @Test
    fun `prompt change disarms active continual generation`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("image".toByteArray()),
        )

        viewModel.onPromptChange("first prompt")
        viewModel.onContinualModeToggle(true)
        viewModel.generate()

        assertEquals(true, viewModel.uiState.value.isContinualGenerationActive)

        viewModel.onPromptChange("second prompt")
        viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1", gallerySize = 1)

        assertEquals(false, viewModel.uiState.value.isContinualGenerationActive)
        coVerify(exactly = 1) { imageGenerationPort.generateImage(any(), any(), any()) }
        job.cancel()
    }

    @Test
    fun `toggleMediaSelection updates selection state`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        viewModel.toggleMediaSelection("asset-1")
        assertTrue(viewModel.uiState.value.selectedMediaItemIds.contains("asset-1"))
        
        viewModel.toggleMediaSelection("asset-1")
        assertFalse(viewModel.uiState.value.selectedMediaItemIds.contains("asset-1"))
        
        job.cancel()
    }

    @Test
    fun `onSaveSelectedMediaToAlbum moves selected media and clears selection`() = runTest {
        // Given
        val ephemeralItem = StudioMediaUi(
            id = "asset-1",
            localUri = "cache://ephemeral.jpg",
            prompt = "ephemeral prompt",
            mediaType = MediaCapability.IMAGE,
            createdAt = 1L
        )
        // Inject ephemeral item into gallery (since it's a private flow, we simulate via generate)
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(
            listOf("image".toByteArray())
        )
        coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "cache://ephemeral.jpg"
        
        coEvery { 
            studioRepository.saveMedia(
                localUri = any(),
                prompt = any(),
                mediaType = any(),
                albumId = any()
            ) 
        } returns StudioMediaAsset(
            id = "generated-long-id",
            localUri = "files://permanent.jpg",
            prompt = "test",
            mediaType = MediaCapability.IMAGE.name,
            createdAt = 1L,
            albumId = "album-123"
        )
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        viewModel.onPromptChange("test")
        viewModel.generate()
        advanceUntilIdle()
        
        val generatedId = viewModel.uiState.value.gallery.first().id
        viewModel.toggleMediaSelection(generatedId)
        
        // Open bottom sheet
        viewModel.onToggleSaveBottomSheet()
        assertTrue(viewModel.uiState.value.isSaveBottomSheetOpen)
        
        // Save it
        viewModel.onSaveSelectedMediaToAlbum("album-123")
        
        // Manually update mock repository flow to simulate persistence
        allMediaFlow.update { current ->
            current + StudioMediaAsset(
                id = generatedId,
                localUri = "files://permanent.jpg",
                prompt = "test",
                mediaType = MediaCapability.IMAGE.name,
                createdAt = 1L,
                albumId = "album-123"
            )
        }
        
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.selectedMediaItemIds.isEmpty())
        assertFalse(viewModel.uiState.value.isSaveBottomSheetOpen)
        assertEquals(1, viewModel.uiState.value.gallery.size)
        assertEquals("album-123", viewModel.uiState.value.gallery.first().albumId)
        
        job.cancel()
    }

    @Test
    fun `viewModel onCleared clears ephemeral cache`() = runTest {
        // Trigger onCleared via ViewModelStore or reflection if needed, 
        // but here we just call it directly for the unit test.
        val method = viewModel.javaClass.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
        
        advanceUntilIdle()
        
        coVerify { studioRepository.clearEphemeralCache() }
    }

    @Test
    fun `onAddAlbum calls repository createAlbum`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        viewModel.onAddAlbum("Vacation")
        
        coVerify { studioRepository.createAlbum("Vacation") }
        job.cancel()
    }

    @Test
    fun `onEditMedia with ephemeral UUID uses session media and skips repository`() = runTest {
        // Given
        val uuid = UUID.randomUUID().toString()
        val ephemeralItem = StudioMediaUi(
            id = uuid,
            localUri = "cache://ephemeral.jpg",
            prompt = "ephemeral prompt",
            mediaType = MediaCapability.IMAGE,
            createdAt = 12345L
        )
        
        // We need to get this into the ViewModel's private _sessionMedia state.
        // The most reliable way is to mock generate or manually trigger a state update if possible.
        // Since _sessionMedia is private, we'll simulate it by mock-returning it from a generation call.
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(listOf("bytes".toByteArray()))
        coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "cache://ephemeral.jpg"
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        viewModel.onPromptChange("trigger prompt")
        viewModel.generate()
        advanceUntilIdle()
        
        // Now find the generated item's ID (which should be a UUID if the code is correct)
        val generatedItem = viewModel.uiState.value.gallery.first()
        val generatedId = generatedItem.id
        
        // When
        viewModel.onEditMedia(generatedId)
        
        // Then
        assertEquals(generatedItem.prompt, viewModel.uiState.value.prompt)
        // Verify we DID NOT call repository (which would crash if it tried to parse UUID)
        coVerify(exactly = 0) { studioRepository.getMediaById(generatedId) }
        
        job.cancel()
    }

    @Test
    fun `onAnimateMedia with ephemeral UUID uses session media and skips repository`() = runTest {
        // Given
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(listOf("bytes".toByteArray()))
        coEvery { studioRepository.cacheEphemeralMedia(any(), MediaCapability.IMAGE.name) } returns "cache://ephemeral.jpg"
        coEvery { studioRepository.cacheEphemeralMedia(any(), MediaCapability.VIDEO.name) } returns "cache://video.mp4"
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        viewModel.onPromptChange("trigger prompt")
        viewModel.generate()
        advanceUntilIdle()
        
        val generatedItem = viewModel.uiState.value.gallery.first()
        val generatedId = generatedItem.id
        
        // When
        viewModel.onAnimateMedia(generatedId, autoAnimate = true)
        
        // Then
        assertEquals(MediaCapability.VIDEO, viewModel.uiState.value.mediaType)
        assertEquals(generatedItem.prompt, viewModel.uiState.value.prompt)
        assertEquals(generatedItem.localUri, (viewModel.uiState.value.settings as VideoGenerationSettings).referenceImageUri)
        assertEquals(VideoGenerationState.Success(generatedId, "cache://video.mp4"), viewModel.uiState.value.videoGenerationState)
        
        // Verify we DID NOT call repository
        coVerify(exactly = 0) { studioRepository.getMediaById(generatedId) }
        
        job.cancel()
    }

    @Test
    fun `onAnimateMedia failure publishes detail video error state`() = runTest {
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(listOf("bytes".toByteArray()))
        coEvery { studioRepository.cacheEphemeralMedia(any(), MediaCapability.IMAGE.name) } returns "cache://ephemeral.jpg"
        coEvery { videoGenerationPort.generateVideo(any(), any(), any()) } returns Result.failure(
            IllegalStateException("video failed"),
        )

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onPromptChange("trigger prompt")
        viewModel.generate()
        advanceUntilIdle()

        val generatedId = viewModel.uiState.value.gallery.first().id
        viewModel.onAnimateMedia(generatedId, autoAnimate = true)
        advanceUntilIdle()

        assertEquals(VideoGenerationState.Error(generatedId, "video failed"), viewModel.uiState.value.videoGenerationState)
        assertEquals("video failed", viewModel.uiState.value.error)
        job.cancel()
    }

    @Test
    fun `shareMedia passes correct URIs to ShareMediaPort`() = runTest {
        // Given
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(listOf("bytes".toByteArray()))
        coEvery { studioRepository.cacheEphemeralMedia(any(), MediaCapability.IMAGE.name) } returns "cache://ephemeral.jpg"
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        viewModel.onPromptChange("trigger prompt")
        viewModel.generate()
        advanceUntilIdle()
        
        val generatedItem = viewModel.uiState.value.gallery.first()
        
        // When
        viewModel.shareMedia(setOf(generatedItem.id))
        
        // Then
        verify { shareMediaPort.shareMedia(listOf("cache://ephemeral.jpg"), "*/*") }
        
        job.cancel()
    }

    @Test
    fun `shareSingleMedia passes correct URI and MIME type to ShareMediaPort`() = runTest {
        // Given
        coEvery { imageGenerationPort.generateImage(any(), any(), any()) } returns Result.success(listOf("bytes".toByteArray()))
        coEvery { studioRepository.cacheEphemeralMedia(any(), MediaCapability.IMAGE.name) } returns "cache://ephemeral.jpg"
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        viewModel.onPromptChange("trigger prompt")
        viewModel.generate()
        advanceUntilIdle()
        
        val generatedItem = viewModel.uiState.value.gallery.first()
        
        // When
        viewModel.shareSingleMedia(generatedItem.id)
        
        // Then
        verify { shareMediaPort.shareMedia(listOf("cache://ephemeral.jpg"), "image/*") }
        
        job.cancel()
    }

    @Test
    fun `initial speech state is Idle`() = runTest {
        assertEquals(SpeechState.Idle, viewModel.uiState.value.speechState)
    }

    @Test
    fun `onMicClick starts listening when Idle`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onMicClick()
        verify(exactly = 1) { listenToSpeechUseCase.invoke(any(), any()) }
        job.cancel()
    }

    @Test
    fun `onMicClick sets stop signal when Listening`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onMicClick()
        speechEvents.emit(SpeechState.Listening(0f))
        runCurrent()
        viewModel.onMicClick()
        runCurrent()
        // Verify that the stop signal was sent.
        verify { listenToSpeechUseCase.invoke(any(), match { it.value }) }
        job.cancel()
    }

    @Test
    fun `speech transcription updates prompt`() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        viewModel.onMicClick()
        speechEvents.emit(SpeechState.FinalText("Voice prompt"))
        runCurrent()
        assertEquals("Voice prompt", viewModel.uiState.value.prompt)
        job.cancel()
    }

    @Test
    fun `onPlayTts launches streaming playback`() = runTest {
        val text = "Hello"
        val ttsAssignment = DefaultModelAssignment(
            modelType = ModelType.TTS,
            ttsProviderId = com.browntowndev.pocketcrew.domain.model.config.TtsProviderId("tts-provider")
        )
        every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(ttsAssignment))
        
        // Re-create VM to pick up new defaults flow
        viewModel = MultimodalViewModel(
            imageGenerationPort,
            videoGenerationPort,
            generateVideoUseCase(),
            generateMusicUseCase,
            shareMediaPort,
            studioRepository,
            defaultModelRepository,
            mediaProviderRepository,
            getProviderCapabilitiesUseCase,
            chatUseCases,
            playbackController,
            logger,
        )

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        every { playbackController.play(text) } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Completed
        )

        viewModel.onPlayTts(text)
        runCurrent()

        verify { playbackController.play(text) }
        job.cancel()
    }

    @Test
    fun `onStopTts stops playback`() = runTest {
        viewModel.onStopTts()
        verify { playbackController.stop() }
    }

    @Test
    fun `generate music success updates gallery`() = runTest {
        // Given
        val prompt = "techno track"
        val musicBytes = "music bytes".toByteArray()
        val localUri = "cache://music.mp3"
        val musicSettings = MusicGenerationSettings()
        
        coEvery { generateMusicUseCase(prompt, any()) } returns Result.success(musicBytes)
        coEvery { studioRepository.cacheEphemeralMedia(musicBytes, MediaCapability.MUSIC.name) } returns localUri
        
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        
        // When
        viewModel.onMediaTypeChange(MediaCapability.MUSIC)
        viewModel.onPromptChange(prompt)
        viewModel.generate()
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertEquals(1, state.gallery.size)
        assertEquals(localUri, state.gallery.first().localUri)
        assertEquals(MediaCapability.MUSIC, state.gallery.first().mediaType)
        assertFalse(state.isGenerating)
        
        job.cancel()
    }

    @Test
    fun `onEditMedia does not overwrite manual changes if called again with same ID`() = runTest {
        // Given
        val assetId = "asset-1"
        val asset = StudioMediaAsset(
            id = assetId,
            localUri = "file://image.jpg",
            prompt = "Original Prompt",
            mediaType = MediaCapability.IMAGE.name,
            createdAt = 1L
        )
        coEvery { studioRepository.getMediaById(assetId) } returns asset

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // When - First navigation
        viewModel.onEditMedia(assetId)
        assertEquals("Original Prompt", viewModel.uiState.value.prompt)

        // User clears the prompt
        viewModel.onPromptChange("")
        assertEquals("", viewModel.uiState.value.prompt)

        // Then - Re-navigating to the same asset (simulated by LaunchedEffect trigger)
        viewModel.onEditMedia(assetId)
        assertEquals("", viewModel.uiState.value.prompt) // Should remain empty

        // But - Navigating to a different asset SHOULD update
        val assetId2 = "asset-2"
        val asset2 = StudioMediaAsset(
            id = assetId2,
            localUri = "file://image2.jpg",
            prompt = "Second Prompt",
            mediaType = MediaCapability.IMAGE.name,
            createdAt = 2L
        )
        coEvery { studioRepository.getMediaById(assetId2) } returns asset2

        viewModel.onEditMedia(assetId2)
        assertEquals("Second Prompt", viewModel.uiState.value.prompt)

        job.cancel()
    }

    @Test
    fun `onAnimateMedia does not overwrite manual changes if called again with same ID`() = runTest {
        // Given
        val assetId = "asset-1"
        val asset = StudioMediaAsset(
            id = assetId,
            localUri = "file://image.jpg",
            prompt = "Original Prompt",
            mediaType = MediaCapability.IMAGE.name,
            createdAt = 1L
        )
        coEvery { studioRepository.getMediaById(assetId) } returns asset

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        // When - First navigation
        viewModel.onAnimateMedia(assetId, autoAnimate = false)
        assertEquals("Original Prompt", viewModel.uiState.value.prompt)
        assertEquals(MediaCapability.VIDEO, viewModel.uiState.value.mediaType)

        // User changes prompt
        viewModel.onPromptChange("Changed")
        assertEquals("Changed", viewModel.uiState.value.prompt)

        // Then - Re-navigating to the same asset
        viewModel.onAnimateMedia(assetId, autoAnimate = false)
        assertEquals("Changed", viewModel.uiState.value.prompt)

        job.cancel()
    }

    private fun generateVideoUseCase(): GenerateVideoUseCase =
// ... existing code ...
        GenerateVideoUseCase(
            defaultModelRepository = defaultModelRepository,
            mediaProviderRepository = mediaProviderRepository,
            videoGenerationPort = videoGenerationPort,
        )
}

private fun assertTrue(actual: Boolean) = org.junit.jupiter.api.Assertions.assertTrue(actual)
private fun assertFalse(actual: Boolean) = org.junit.jupiter.api.Assertions.assertFalse(actual)
