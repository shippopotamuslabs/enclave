package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.media.VideoGenerationPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.feature.studio.coordinators.StudioAlbumSelectionCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioGenerationCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPlaybackCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioSpeechCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioUiStateReducer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    private val dispatcher = mockk<StudioGenerationDispatcherPort>(relaxed = true)
    private lateinit var viewModel: MultimodalViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = mockk<android.app.Application>(relaxed = true)

        val mediaProviderId = MediaProviderId("test-provider")
        val videoAssignment =
            DefaultModelAssignment(
                modelType = ModelType.VIDEO_GENERATION,
                mediaProviderId = mediaProviderId,
            )
        val providerAsset =
            MediaProviderAsset(
                id = mediaProviderId,
                displayName = "Test Provider",
                provider = ApiProvider.OPENAI,
                capability = MediaCapability.VIDEO,
                credentialAlias = "test-alias",
            )

        every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(videoAssignment))
        coEvery { defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION) } returns videoAssignment
        coEvery { mediaProviderRepository.getMediaProvider(mediaProviderId) } returns providerAsset
        every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(providerAsset))
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeAllAlbums() } returns flowOf(emptyList())
        coEvery { videoGenerationPort.generateVideo(any(), any(), any()) } returns Result.success("video".toByteArray())
        coEvery { studioRepository.cacheEphemeralMedia(any(), any()) } returns "cache://video.mp4"
        every { getProviderCapabilitiesUseCase(any()) } returns
            ProviderCapabilities(
                supportedAspectRatios = AspectRatio.entries.toList(),
                supportedImageQualities = listOf(GenerationQuality.SPEED),
                supportedVideoQualities = listOf(GenerationQuality.SPEED),
                supportedVideoResolutions = listOf("480p"),
                supportedVideoDurations = listOf(5),
                supportsReferenceImage = true,
                supportsVideo = true,
            )

        val generationCoordinator = StudioGenerationCoordinator(studioRepository, dispatcher)
        val albumSelectionCoordinator = StudioAlbumSelectionCoordinator(studioRepository)
        val speechCoordinator = StudioSpeechCoordinator(chatUseCases)
        val playbackCoordinator = StudioPlaybackCoordinator(playbackController)
        val studioUiStateReducer = StudioUiStateReducer()
        val capabilitiesCoordinator = StudioCapabilitiesCoordinator()

        viewModel =
            MultimodalViewModel(
                shareMediaPort = shareMediaPort,
                studioRepository = studioRepository,
                defaultModelRepository = defaultModelRepository,
                mediaProviderRepository = mediaProviderRepository,
                getProviderCapabilitiesUseCase = getProviderCapabilitiesUseCase,
                generationCoordinator = generationCoordinator,
                albumSelectionCoordinator = albumSelectionCoordinator,
                speechCoordinator = speechCoordinator,
                playbackCoordinator = playbackCoordinator,
                studioUiStateReducer = studioUiStateReducer,
                capabilitiesCoordinator = capabilitiesCoordinator,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onAnimateMedia with autoAnimate=true should start generation but NOT update shared state`() =
        runTest {
            // Given
            val assetId = "asset-1"
            val asset =
                StudioMediaAsset(
                    id = assetId,
                    localUri = "file://image.jpg",
                    prompt = "Original Prompt",
                    mediaType = MediaCapability.IMAGE.name,
                    createdAt = 1L,
                )
            coEvery { studioRepository.getMediaById(assetId) } returns asset

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }

            // When
            viewModel.onAnimateMedia(assetId, autoAnimate = true)
            advanceUntilIdle()

            // Then
            val settingsSlot = slot<GenerationSettings>()
            verify {
                dispatcher.startGeneration(
                    "Original Prompt",
                    MediaCapability.VIDEO,
                    capture(settingsSlot),
                )
            }
            val settings = settingsSlot.captured as VideoGenerationSettings
            assertEquals("file://image.jpg", settings.referenceImageUri)
            assertTrue(viewModel.uiState.value.isGenerating)
            assertEquals("", viewModel.uiState.value.prompt)
            job.cancel()
        }

    @Test
    fun `onAnimateMedia with autoAnimate=false should update shared state for navigation flow`() =
        runTest {
            // Given
            val assetId = "asset-1"
            val asset =
                StudioMediaAsset(
                    id = assetId,
                    localUri = "file://image.jpg",
                    prompt = "Original Prompt",
                    mediaType = MediaCapability.IMAGE.name,
                    createdAt = 1L,
                )
            coEvery { studioRepository.getMediaById(assetId) } returns asset

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }

            // When
            viewModel.onAnimateMedia(assetId, autoAnimate = false)
            advanceUntilIdle()

            // Then
            assertEquals(MediaCapability.VIDEO, viewModel.uiState.value.mediaType)
            assertEquals("", viewModel.uiState.value.prompt)
            val settings = viewModel.uiState.value.settings as VideoGenerationSettings
            assertNull(settings.referenceImageUri)
            job.cancel()
        }

    @Test
    fun `onAnimateMedia with customPrompt and autoAnimate=true should use custom prompt for generation but NOT update shared state`() =
        runTest {
            // Given
            val assetId = "asset-1"
            val asset =
                StudioMediaAsset(
                    id = assetId,
                    localUri = "file://image.jpg",
                    prompt = "Original Prompt",
                    mediaType = MediaCapability.IMAGE.name,
                    createdAt = 1L,
                )
            coEvery { studioRepository.getMediaById(assetId) } returns asset

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }

            // When
            viewModel.onAnimateMedia(assetId, autoAnimate = true, customPrompt = "Custom Prompt")
            advanceUntilIdle()

            // Then
            verify { dispatcher.startGeneration("Custom Prompt", MediaCapability.VIDEO, any()) }
            assertEquals("", viewModel.uiState.value.prompt)
            job.cancel()
        }
}
