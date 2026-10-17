package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeModerationReportRepository
import com.browntowndev.enclave.core.testing.FakeShareMediaPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeUiSelectionRepository
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.usecase.media.GetImageGenerationCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.GetStudioCapablePresetsUseCase
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.feature.studio.test.FakeStudioGenerationDispatcher
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaProviderCapabilitiesUseCase
import com.browntowndev.enclave.feature.studio.coordinators.StudioAlbumSelectionCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioGenerationCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioMediaActionsCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioModelOverrideCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioResolvedModelCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPlaybackCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPromptCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioRemoteCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioSpeechCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioUiStateReducer
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
    private val shareMediaPort = FakeShareMediaPort()
    private val studioRepository = FakeStudioRepository()
    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val localModelRepository = FakeLocalModelRepository()
    private val resolveMediaProviderCapabilitiesUseCase =
        FakeResolveMediaProviderCapabilitiesUseCase(
            ProviderCapabilities(
                supportedAspectRatios = AspectRatio.entries.toList(),
                supportedImageQualities = listOf(GenerationQuality.SPEED),
                supportedVideoQualities = listOf(GenerationQuality.SPEED),
                supportedVideoResolutions = listOf("480p"),
                supportedVideoDurations = listOf(5),
                supportsVideo = true,
            ),
        )
    private val listenToSpeechUseCase = FakeListenToSpeechUseCase()
    private val chatUseCases = FakeChatUseCases(listenToSpeechUseCase)
    private val playbackController = FakeTtsPlaybackController()
    private val dispatcher = FakeStudioGenerationDispatcher()
    private lateinit var viewModel: MultimodalViewModel
    private val generationProgressFlow get() = studioRepository.generationProgressState
    private val activeGenerationFlow get() = studioRepository.activeGenerationState

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val mediaProviderId = MediaProviderId("test-provider")
        val videoAssignment =
            DefaultModelAssignment(
                modelType = ModelType.VIDEO_GENERATION,
                mediaProviderId = mediaProviderId,
            )
        val imageToVideoAssignment =
            DefaultModelAssignment(
                modelType = ModelType.IMAGE_TO_VIDEO,
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

        defaultModelRepository.seed(videoAssignment)
        defaultModelRepository.seed(imageToVideoAssignment)
        kotlinx.coroutines.runBlocking { mediaProviderRepository.saveMediaProvider(providerAsset, null) }
        studioRepository.cacheEphemeralUri = "cache://video.mp4"

        val generationCoordinator = StudioGenerationCoordinator(studioRepository, dispatcher)
        val albumSelectionCoordinator = StudioAlbumSelectionCoordinator(studioRepository)
        val speechCoordinator = StudioSpeechCoordinator(chatUseCases)
        val playbackCoordinator = StudioPlaybackCoordinator(playbackController)
        val studioUiStateReducer = StudioUiStateReducer()
        val capabilitiesCoordinator = StudioCapabilitiesCoordinator()

        val remoteCapabilitiesCoordinator =
            StudioRemoteCapabilitiesCoordinator(resolveMediaProviderCapabilitiesUseCase)
        val promptCoordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)
        val mediaActionsCoordinator =
            StudioMediaActionsCoordinator(shareMediaPort, studioRepository, generationCoordinator)
        val uiSelectionRepository = FakeUiSelectionRepository()

        viewModel =
            MultimodalViewModel(
                observeDefaultModelAssignments = com.browntowndev.enclave.domain.usecase.studio.ObserveDefaultModelAssignmentsUseCase(defaultModelRepository),
                getMediaProviders = com.browntowndev.enclave.domain.usecase.studio.GetMediaProvidersUseCase(mediaProviderRepository),
                moderatePromptUseCase = allowPromptModerationUseCase(),
                shouldModeratePromptUseCase = allowShouldModeratePromptUseCase(),
                submitModerationReportUseCase = SubmitModerationReportUseCase(FakeModerationReportRepository()),
                getImageGenerationCapabilitiesUseCase = GetImageGenerationCapabilitiesUseCase(localModelRepository),
                generationCoordinator = generationCoordinator,
                albumSelectionCoordinator = albumSelectionCoordinator,
                speechCoordinator = speechCoordinator,
                playbackCoordinator = playbackCoordinator,
                studioUiStateReducer = studioUiStateReducer,
                capabilitiesCoordinator = capabilitiesCoordinator,
                remoteCapabilitiesCoordinator = remoteCapabilitiesCoordinator,
                promptCoordinator = promptCoordinator,
                mediaActionsCoordinator = mediaActionsCoordinator,
                getSelectedStudioMediaType = com.browntowndev.enclave.domain.usecase.studio.GetSelectedStudioMediaTypeUseCase(uiSelectionRepository),
                    setSelectedStudioMediaType = com.browntowndev.enclave.domain.usecase.studio.SetSelectedStudioMediaTypeUseCase(uiSelectionRepository),
                overrideCoordinator = StudioModelOverrideCoordinator(),
                resolvedModelCoordinator =
                    StudioResolvedModelCoordinator(
                        resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase(),
                        mediaProviderRepository = mediaProviderRepository,
                    ),
                getStudioCapablePresetsUseCase = GetStudioCapablePresetsUseCase(localModelRepository, mediaProviderRepository),
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
            studioRepository.emitMedia(listOf(asset))

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }

            // When
            viewModel.onAnimateMedia(
                assetId = assetId,
                autoAnimate = true,
            )
            advanceUntilIdle()

            // Then
            val started = dispatcher.startedCalls.single()
            assertEquals("Original Prompt", started.prompt)
            assertEquals(MediaCapability.VIDEO, started.mediaType)
            val settings = started.settings as VideoGenerationSettings
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
            studioRepository.emitMedia(listOf(asset))

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }

            // When
            viewModel.onAnimateMedia(
                assetId = assetId,
                autoAnimate = false,
            )
            advanceUntilIdle()

            // Then
            assertEquals(StudioMediaTypeUi.VIDEO, viewModel.uiState.value.mediaType)
            assertEquals("", viewModel.uiState.value.prompt)
            val settings = viewModel.uiState.value.settings as StudioVideoSettingsUi
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
            studioRepository.emitMedia(listOf(asset))

            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }

            // When
            viewModel.onAnimateMedia(
                assetId = assetId,
                autoAnimate = true,
                customPrompt = "Custom Prompt",
            )
            advanceUntilIdle()

            // Then
            val started = dispatcher.startedCalls.single()
            assertEquals("Custom Prompt", started.prompt)
            assertEquals(MediaCapability.VIDEO, started.mediaType)
            assertEquals("", viewModel.uiState.value.prompt)
            job.cancel()
        }
}
