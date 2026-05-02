package com.browntowndev.pocketcrew.feature.studio

import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.media.AspectRatio
import com.browntowndev.pocketcrew.domain.model.media.GenerationQuality
import com.browntowndev.pocketcrew.domain.model.media.ProviderCapabilities
import com.browntowndev.pocketcrew.domain.model.media.VideoGenerationSettings
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.ImageGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.MusicGenerationPort
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GenerateVideoUseCase
import com.browntowndev.pocketcrew.domain.usecase.media.GetProviderCapabilitiesUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModelAnimateTest {
    private val imageGenerationPort = mockk<ImageGenerationPort>(relaxed = true)
    private val videoGenerationPort = mockk<VideoGenerationPort>(relaxed = true)
    private val generateMusicUseCase = mockk<GenerateMusicUseCase>(relaxed = true)
    private val shareMediaPort = mockk<ShareMediaPort>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>(relaxed = true)
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>(relaxed = true)
    private val getProviderCapabilitiesUseCase = mockk<GetProviderCapabilitiesUseCase>(relaxed = true)
    private val chatUseCases = mockk<ChatUseCases>(relaxed = true)
    private val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
    private val logger = mockk<LoggingPort>(relaxed = true)
    private lateinit var viewModel: MultimodalViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val mediaProviderId = MediaProviderId("test-provider")
        val videoAssignment = DefaultModelAssignment(
            modelType = ModelType.VIDEO_GENERATION,
            mediaProviderId = mediaProviderId
        )
        val providerAsset = MediaProviderAsset(
            id = mediaProviderId,
            displayName = "Test Provider",
            provider = ApiProvider.OPENAI,
            capability = MediaCapability.VIDEO,
            credentialAlias = "test-alias"
        )

        every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(videoAssignment))
        coEvery { defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION) } returns videoAssignment
        coEvery { mediaProviderRepository.getMediaProvider(mediaProviderId) } returns providerAsset
        every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(providerAsset))
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeAllAlbums() } returns flowOf(emptyList())
        coEvery { videoGenerationPort.generateVideo(any(), any(), any()) } returns Result.success("video".toByteArray())
        coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "cache://video.mp4"
        every { getProviderCapabilitiesUseCase(any()) } returns ProviderCapabilities(
            supportedAspectRatios = AspectRatio.entries.toList(),
            supportedImageQualities = listOf(GenerationQuality.SPEED),
            supportedVideoQualities = listOf(GenerationQuality.SPEED),
            supportedVideoResolutions = listOf("480p"),
            supportedVideoDurations = listOf(5),
            supportsReferenceImage = true,
            supportsVideo = true
        )

        viewModel = MultimodalViewModel(
            imageGenerationPort,
            videoGenerationPort,
            GenerateVideoUseCase(defaultModelRepository, mediaProviderRepository, videoGenerationPort),
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
    fun `onAnimateMedia with autoAnimate=true should start generation but NOT update shared state`() = runTest {
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

        // When
        viewModel.onAnimateMedia(assetId, autoAnimate = true)
        advanceUntilIdle()

        // Then
        assertEquals(VideoGenerationState.Success(assetId, "cache://video.mp4"), viewModel.uiState.value.videoGenerationState)
        assertEquals("Original Prompt", viewModel.uiState.value.prompt)
        job.cancel()
    }

    @Test
    fun `onAnimateMedia with autoAnimate=false should update shared state for navigation flow`() = runTest {
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

        // When
        viewModel.onAnimateMedia(assetId, autoAnimate = false)
        advanceUntilIdle()

        // Then
        assertEquals(MediaCapability.VIDEO, viewModel.uiState.value.mediaType)
        assertEquals("Original Prompt", viewModel.uiState.value.prompt)
        val settings = viewModel.uiState.value.settings as VideoGenerationSettings
        assertEquals("file://image.jpg", settings.referenceImageUri)
        job.cancel()
    }

    @Test
    fun `onAnimateMedia with customPrompt and autoAnimate=true should use custom prompt for generation but NOT update shared state`() = runTest {
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

        // When
        viewModel.onAnimateMedia(assetId, autoAnimate = true, customPrompt = "Custom Prompt")
        advanceUntilIdle()

        // Then
        coEvery { videoGenerationPort.generateVideo("Custom Prompt", any(), any()) }
        assertEquals("Custom Prompt", viewModel.uiState.value.prompt)
        job.cancel()
    }
}
