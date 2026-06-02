package com.browntowndev.enclave.feature.studio

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioAlbumAsset
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaProviderCapabilitiesUseCase
import com.browntowndev.enclave.feature.studio.coordinators.StudioAlbumSelectionCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioGenerationCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPlaybackCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioSpeechCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioUiStateReducer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val shareMediaPort = mockk<ShareMediaPort>(relaxed = true)
    private val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    private val defaultModelRepository = mockk<DefaultModelRepositoryPort>(relaxed = true)
    private val mediaProviderRepository = mockk<MediaProviderRepositoryPort>(relaxed = true)
    private val resolveMediaProviderCapabilitiesUseCase = mockk<ResolveMediaProviderCapabilitiesUseCase>()
    private val chatUseCases = mockk<ChatUseCases>()
    private val listenToSpeechUseCase = mockk<ListenToSpeechUseCase>()
    private val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
    private val dispatcher = mockk<StudioGenerationDispatcherPort>(relaxed = true)
    private lateinit var generationCoordinator: StudioGenerationCoordinator
    private lateinit var albumSelectionCoordinator: StudioAlbumSelectionCoordinator
    private lateinit var speechCoordinator: StudioSpeechCoordinator
    private lateinit var playbackCoordinator: StudioPlaybackCoordinator
    private lateinit var studioUiStateReducer: StudioUiStateReducer

    private val allMediaFlow = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
    private val ephemeralMediaFlow = MutableStateFlow<List<StudioMediaAsset>>(emptyList())
    private val allAlbumsFlow = MutableStateFlow<List<StudioAlbumAsset>>(emptyList())
    private val generationErrorFlow = MutableStateFlow<String?>(null)
    private val speechEvents = MutableSharedFlow<SpeechState>(extraBufferCapacity = 8)

    private lateinit var app: android.app.Application
    private lateinit var viewModel: MultimodalViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = mockk<android.app.Application>(relaxed = true)

        val mediaProviderId = MediaProviderId("test-provider")
        val assignment =
            DefaultModelAssignment(
                modelType = ModelType.IMAGE_GENERATION,
                mediaProviderId = mediaProviderId,
            )
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
                capability = MediaCapability.IMAGE,
                credentialAlias = "test-alias",
            )

        val musicAssignment =
            DefaultModelAssignment(
                modelType = ModelType.MUSIC_GENERATION,
                mediaProviderId = mediaProviderId,
            )
        val imageToVideoAssignment =
            DefaultModelAssignment(
                modelType = ModelType.IMAGE_TO_VIDEO,
                mediaProviderId = mediaProviderId,
            )

        every { defaultModelRepository.observeDefaults() } returns
            flowOf(listOf(assignment, videoAssignment, musicAssignment, imageToVideoAssignment))
        coEvery { defaultModelRepository.getDefault(ModelType.VIDEO_GENERATION) } returns videoAssignment
        coEvery { defaultModelRepository.getDefault(ModelType.IMAGE_TO_VIDEO) } returns imageToVideoAssignment
        coEvery { mediaProviderRepository.getMediaProvider(mediaProviderId) } returns providerAsset
        every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(providerAsset))

        every { studioRepository.observeAllMedia() } returns allMediaFlow
        every { studioRepository.observeEphemeralMedia() } returns ephemeralMediaFlow
        every { studioRepository.observeGenerationError() } returns generationErrorFlow
        every { studioRepository.observeAllAlbums() } returns allAlbumsFlow
        coEvery { studioRepository.updateMedia(any()) } returns Unit
        coEvery { studioRepository.promoteEphemeralMedia(any(), any()) } returns
            StudioMediaAsset(
                id = "perm-1",
                localUri = "files://permanent.jpg",
                prompt = "test",
                mediaType = "IMAGE",
                createdAt = 1L,
                albumId = "album-123",
            )
        coEvery { studioRepository.saveMedia(any<String>(), any(), any(), any(), any()) } returns
            StudioMediaAsset(
                id = "perm-1",
                localUri = "files://permanent.jpg",
                prompt = "test",
                mediaType = "IMAGE",
                createdAt = 1L,
                albumId = "album-123",
            )

        coEvery { studioRepository.cacheEphemeralMedia(any(), MediaCapability.VIDEO.name) } returns "cache://video.mp4"
        coEvery {
            resolveMediaProviderCapabilitiesUseCase(any(), any())
        } returns
            ProviderCapabilities(
                supportedAspectRatios = AspectRatio.entries.toList(),
                supportedImageQualities = listOf(GenerationQuality.SPEED),
                supportedVideoQualities = listOf(GenerationQuality.SPEED),
                supportedVideoResolutions = listOf("480p"),
                supportedVideoDurations = listOf(5),
                supportsVideo = true,
            )

        every { listenToSpeechUseCase.invoke(any(), any()) } returns speechEvents
        every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase

        generationCoordinator = StudioGenerationCoordinator(studioRepository, dispatcher)
        albumSelectionCoordinator = StudioAlbumSelectionCoordinator(studioRepository)
        speechCoordinator = StudioSpeechCoordinator(chatUseCases)
        playbackCoordinator = StudioPlaybackCoordinator(playbackController)
        studioUiStateReducer = StudioUiStateReducer()
        viewModel = createViewModel()
    }

    private fun createViewModel(): MultimodalViewModel {
        val capabilitiesCoordinator = StudioCapabilitiesCoordinator()
        return MultimodalViewModel(
            shareMediaPort = shareMediaPort,
            studioRepository = studioRepository,
            defaultModelRepository = defaultModelRepository,
            mediaProviderRepository = mediaProviderRepository,
            resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase(),
            resolveMediaProviderCapabilitiesUseCase = resolveMediaProviderCapabilitiesUseCase,
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
    fun `ST1 generation completes with media visible and isGenerating false`() =
        runTest {
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            viewModel.onPromptChange("sunset mountains")
            viewModel.generate()
            assertTrue(viewModel.uiState.value.isGenerating)

            ephemeralMediaFlow.value =
                listOf(
                    StudioMediaAsset(
                        id = "gen-1",
                        localUri = "cache://generated.jpg",
                        prompt = "sunset mountains",
                        mediaType = "IMAGE",
                        createdAt = 1L,
                    ),
                )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertEquals(1, state.gallery.size)
            assertEquals("gen-1", state.gallery.first().id)
            assertEquals("cache://generated.jpg", state.gallery.first().localUri)

            job.cancel()
        }

    @Test
    fun `uiState emits typed studio state without generation error`() =
        runTest {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertEquals("", initial.prompt)
                assertFalse(initial.isGenerating)

                viewModel.onPromptChange("studio prompt")
                val updated = awaitItem()
                assertEquals("studio prompt", updated.prompt)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `capabilities should be updated via use case`() =
        runTest {
            val capabilities =
                ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED, GenerationQuality.HD),
                    supportedVideoQualities = emptyList(),
                    supportedVideoResolutions = emptyList(),
                    supportedVideoDurations = emptyList(),
                    supportsVideo = false,
                )
            coEvery { resolveMediaProviderCapabilitiesUseCase(any(), any()) } returns capabilities

            viewModel = createViewModel()
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf(AspectRatio.ONE_ONE, AspectRatio.SIXTEEN_NINE), state.capabilities?.supportedAspectRatios)
            assertEquals(listOf(GenerationQuality.SPEED, GenerationQuality.HD), state.capabilities?.supportedImageQualities)
            job.cancel()
        }

    @Test
    fun `switching media type while fal schema fetch is in flight keeps loading active`() =
        runTest {
            val mediaProviderId = MediaProviderId("fal-provider")
            val falProvider =
                MediaProviderAsset(
                    id = mediaProviderId,
                    displayName = "Fal",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.IMAGE,
                    credentialAlias = "fal-alias",
                    modelName = "fal-ai/flux/dev",
                )
            val imageAssignment =
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_GENERATION,
                    mediaProviderId = mediaProviderId,
                )
            val videoAssignment =
                DefaultModelAssignment(
                    modelType = ModelType.VIDEO_GENERATION,
                    mediaProviderId = mediaProviderId,
                )
            val imageEditAssignment =
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_EDIT,
                    mediaProviderId = mediaProviderId,
                )
            val imageToVideoAssignment =
                DefaultModelAssignment(
                    modelType = ModelType.IMAGE_TO_VIDEO,
                    mediaProviderId = mediaProviderId,
                )
            every { defaultModelRepository.observeDefaults() } returns
                flowOf(listOf(imageAssignment, videoAssignment, imageEditAssignment, imageToVideoAssignment))
            every { mediaProviderRepository.getMediaProviders() } returns flowOf(listOf(falProvider))

            val remoteFetchGate = CompletableDeferred<Unit>()
            val resolvedCapabilities =
                ProviderCapabilities(
                    supportedAspectRatios = listOf(AspectRatio.ONE_ONE),
                    supportedImageQualities = listOf(GenerationQuality.SPEED),
                    supportedVideoQualities = listOf(GenerationQuality.SPEED),
                    supportedVideoResolutions = listOf("480p"),
                    supportedVideoDurations = listOf(5),
                    supportsVideo = true,
                )
            coEvery { resolveMediaProviderCapabilitiesUseCase(any(), any()) } coAnswers {
                remoteFetchGate.await()
                resolvedCapabilities
            }

            viewModel = createViewModel()
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isCapabilitiesLoading)

            viewModel.onMediaTypeChange(MediaCapability.VIDEO)
            assertTrue(
                viewModel.uiState.value.isCapabilitiesLoading,
                "Loading must stay active until the superseding fetch completes",
            )

            remoteFetchGate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isCapabilitiesLoading)
            assertEquals(resolvedCapabilities, viewModel.uiState.value.capabilities)
            job.cancel()
        }

    @Test
    fun `generate exposes moderation failure message for snackbar`() =
        runTest {
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            viewModel.onPromptChange("rejected prompt")
            viewModel.generate()

            generationErrorFlow.value = "Prompt rejected due to moderation."
            advanceUntilIdle()

            assertEquals("Prompt rejected due to moderation.", viewModel.uiState.value.error)
            job.cancel()
        }

    @Test
    fun `generate exposes fal content policy failure as snackbar message`() =
        runTest {
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            viewModel.onPromptChange("mock microsoft")
            viewModel.generate()

            generationErrorFlow.value =
                "fal video generation failed: could not fetch queue result from " +
                "https://queue.fal.run/bytedance/seedance-2.0/requests/test " +
                "(fal queue result failed: 422 Output video has sensitive content.)"
            advanceUntilIdle()

            val error = viewModel.uiState.value.error
            assertTrue(error?.contains("sensitive content", ignoreCase = true) == true)
            assertTrue(error?.contains("blocked", ignoreCase = true) == true)
            job.cancel()
        }

    @Test
    fun `generate clears visible prompt while preserving submitted prompt for generation`() =
        runTest {
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            viewModel.onPromptChange("studio prompt")
            viewModel.generate()
            assertEquals("", viewModel.uiState.value.prompt)

            advanceUntilIdle()
            verify { dispatcher.startGeneration(any(), any(), any()) }
            job.cancel()
        }

    @Test
    fun `scroll threshold only generates after prompt is sent and resets generating state`() =
        runTest {
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            viewModel.onPromptChange("scroll prompt")
            viewModel.onContinualModeToggle(true)

            // Before generate, threshold hit is ignored
            viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-0")
            verify(exactly = 0) { dispatcher.startGeneration(any(), any(), any()) }

            // Initial generate
            viewModel.generate()
            verify(exactly = 1) { dispatcher.startGeneration(any(), any(), any()) }
            assertTrue(viewModel.uiState.value.isGenerating)

            // Simulate generation completion to reset _isGenerating
            ephemeralMediaFlow.value =
                listOf(StudioMediaAsset(id = "1", localUri = "uri", prompt = "p", mediaType = "IMAGE", createdAt = 1L))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isGenerating)

            // First threshold hit
            viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1")
            verify(exactly = 2) { dispatcher.startGeneration(any(), any(), any()) }
            assertTrue(viewModel.uiState.value.isGenerating)

            // Simulate second completion
            ephemeralMediaFlow.value =
                ephemeralMediaFlow.value + StudioMediaAsset(id = "2", localUri = "uri2", prompt = "p", mediaType = "IMAGE", createdAt = 2L)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isGenerating)

            // Second threshold hit with same anchor should be ignored (internal consumedScrollAnchors)
            viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1")
            verify(exactly = 2) { dispatcher.startGeneration(any(), any(), any()) }

            // Third threshold hit with new anchor should work
            viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-2")
            verify(exactly = 3) { dispatcher.startGeneration(any(), any(), any()) }

            job.cancel()
        }

    @Test
    fun `prompt change disarms active continual generation`() =
        runTest {
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            viewModel.onPromptChange("first prompt")
            viewModel.onContinualModeToggle(true)
            viewModel.generate()
            assertTrue(viewModel.uiState.value.isContinualGenerationActive)

            // Simulate completion so next hit is possible
            ephemeralMediaFlow.value =
                listOf(StudioMediaAsset(id = "1", localUri = "uri", prompt = "p", mediaType = "IMAGE", createdAt = 1L))
            advanceUntilIdle()

            viewModel.onPromptChange("second prompt")
            viewModel.onGenerativeScrollThresholdVisible(anchorAssetId = "anchor-1")

            assertFalse(viewModel.uiState.value.isContinualGenerationActive)
            verify(exactly = 1) { dispatcher.startGeneration(any(), any(), any()) }
            job.cancel()
        }

    @Test
    fun `onSaveSelectedMediaToAlbum moves selected media and clears selection`() =
        runTest {
            val assetId = "asset-1"
            coEvery {
                studioRepository.saveMedia(any<String>(), any<String>(), any<String>(), any(), any())
            } returns
                StudioMediaAsset(
                    id = "perm-1",
                    localUri = "files://permanent.jpg",
                    prompt = "test",
                    mediaType = "IMAGE",
                    createdAt = 1L,
                    albumId = "album-123",
                )

            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            ephemeralMediaFlow.value =
                listOf(
                    StudioMediaAsset(
                        id = assetId,
                        localUri = "cache://ephemeral.jpg",
                        prompt = "test",
                        mediaType = "IMAGE",
                        createdAt = 1L,
                    ),
                )
            advanceUntilIdle()

            viewModel.toggleMediaSelection(assetId)
            viewModel.onToggleSaveBottomSheet()

            viewModel.onSaveSelectedMediaToAlbum("album-123")

            ephemeralMediaFlow.value = emptyList()
            allAlbumsFlow.value = listOf(StudioAlbumAsset(id = "album-123", name = "Test Album"))
            allMediaFlow.value =
                listOf(
                    StudioMediaAsset(
                        id = "perm-1",
                        localUri = "files://permanent.jpg",
                        prompt = "test",
                        mediaType = "IMAGE",
                        createdAt = 1L,
                        albumId = "album-123",
                    ),
                )
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.selectedMediaItemIds
                    .isEmpty(),
            )
            assertFalse(viewModel.uiState.value.isSaveBottomSheetOpen)
            assertEquals(1, viewModel.uiState.value.gallery.size)
            assertEquals(
                "album-123",
                viewModel.uiState.value.gallery
                    .first()
                    .albumId,
            )

            coVerify { studioRepository.promoteEphemeralMedia(assetId, "album-123") }

            job.cancel()
        }

    @Test
    fun `onAnimateMedia with autoAnimate true starts service`() =
        runTest {
            val assetId = "test-id"
            val asset = StudioMediaAsset(id = assetId, prompt = "original prompt", localUri = "uri", mediaType = "IMAGE", createdAt = 0L)
            coEvery { studioRepository.getMediaById(assetId) } returns asset

            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            viewModel.onAnimateMedia(assetId, autoAnimate = true, customPrompt = "custom prompt")
            advanceUntilIdle()

            verify { dispatcher.startGeneration(any(), any(), any()) }
            job.cancel()
        }

    @Test
    fun `shareMedia passes correct URIs to ShareMediaPort`() =
        runTest {
            val assetId = "asset-1"
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            ephemeralMediaFlow.value =
                listOf(
                    StudioMediaAsset(
                        id = assetId,
                        localUri = "cache://ephemeral.jpg",
                        prompt = "test",
                        mediaType = "IMAGE",
                        createdAt = 1L,
                    ),
                )
            advanceUntilIdle()

            viewModel.shareMedia(setOf(assetId))
            verify { shareMediaPort.shareMedia(listOf("cache://ephemeral.jpg"), "*/*") }
            job.cancel()
        }

    @Test
    fun `onMicClick starts listening when Idle`() =
        runTest {
            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }
            viewModel.onMicClick()
            verify(exactly = 1) { listenToSpeechUseCase.invoke(any(), any()) }
            job.cancel()
        }

    @Test
    fun `onEditMedia with rehydration should load assets into gallery but keep prompt and settings clean`() =
        runTest {
            // Given
            val rehydrateIds = listOf("id-1")
            val rehydrateUris = listOf("file:///test.jpg")
            val rehydrateTypes = listOf("IMAGE")
            val rehydratePrompts = listOf("Rehydrated Prompt")

            val job =
                backgroundScope.launch(testDispatcher) {
                    viewModel.uiState.collect {}
                }

            // When
            viewModel.onEditMedia(
                assetId = null,
                rehydrateIds = rehydrateIds,
                rehydrateUris = rehydrateUris,
                rehydrateTypes = rehydrateTypes,
                rehydratePrompts = rehydratePrompts,
            )
            advanceUntilIdle()

            // Then
            coVerify { studioRepository.addEphemeralMedia(any()) }
            assertEquals("", viewModel.uiState.value.prompt)
            val settings = viewModel.uiState.value.settings as ImageGenerationSettings
            assertNull(settings.referenceImageUri)

            job.cancel()
        }
}
