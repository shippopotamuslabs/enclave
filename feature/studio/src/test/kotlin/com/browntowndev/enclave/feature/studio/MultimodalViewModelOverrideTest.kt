package com.browntowndev.enclave.feature.studio

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.model.media.VideoGenerationSettings
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeModerationReportRepository
import com.browntowndev.enclave.core.testing.FakeShareMediaPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeUiSelectionRepository
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.ActiveStudioGeneration
import com.browntowndev.enclave.domain.port.repository.StudioAlbumAsset
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.domain.usecase.media.GetImageGenerationCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.GetStudioCapablePresetsUseCase
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
import com.browntowndev.enclave.feature.studio.test.FakeStudioGenerationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultimodalViewModelOverrideTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private val defaultModelRepository = FakeDefaultModelRepository()
    private val mediaProviderRepository = FakeMediaProviderRepository()
    private val studioRepository = FakeStudioRepository()
    private val localModelRepository = FakeLocalModelRepository()
    private val moderationReportRepository = FakeModerationReportRepository()
    private val submitModerationReportUseCase = SubmitModerationReportUseCase(moderationReportRepository)
    private val uiSelectionRepository = FakeUiSelectionRepository()
    private val shareMediaPort = FakeShareMediaPort()
    private val playbackController = FakeTtsPlaybackController()
    private val resolveMediaProviderCapabilitiesUseCase =
        FakeResolveMediaProviderCapabilitiesUseCase(
            ProviderCapabilities(
                supportedAspectRatios = emptyList(),
                supportedImageQualities = emptyList(),
                supportedVideoQualities = emptyList(),
                supportedVideoResolutions = emptyList(),
                supportedVideoDurations = emptyList(),
                supportsVideo = false,
            ),
        )
    private val listenToSpeechUseCase = FakeListenToSpeechUseCase()

    private val activeGenerationFlow get() = studioRepository.activeGenerationState
    private val ephemeralMediaFlow get() = studioRepository.ephemeralState
    private val allMediaFlow get() = studioRepository.mediaState
    private val allAlbumsFlow get() = studioRepository.albumsState
    private val generationErrorFlow get() = studioRepository.generationErrorState
    private val generationProgressFlow get() = studioRepository.generationProgressState
    private val speechEvents get() = listenToSpeechUseCase.events

    private val fakeDispatcher = FakeStudioGenerationDispatcher()
    private lateinit var overrideCoordinator: StudioModelOverrideCoordinator
    private lateinit var generationCoordinator: StudioGenerationCoordinator
    private lateinit var viewModel: MultimodalViewModel
    private lateinit var getStudioCapablePresetsUseCase: GetStudioCapablePresetsUseCase

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val imageProviderId = MediaProviderId("test-provider")
        val imageAssignment =
            DefaultModelAssignment(
                modelType = ModelType.IMAGE_GENERATION,
                mediaProviderId = imageProviderId,
                displayName = "Default Image",
            )
        val videoAssignment =
            DefaultModelAssignment(
                modelType = ModelType.VIDEO_GENERATION,
                mediaProviderId = imageProviderId,
                displayName = "Default Video",
            )

        defaultModelRepository.seed(imageAssignment)
        defaultModelRepository.seed(videoAssignment)
        val studioProviders =
            listOf(
                provider("fal-t2i", MediaCapability.IMAGE, setOf(MediaTask.TEXT_TO_IMAGE)),
                provider("fal-edit", MediaCapability.IMAGE, setOf(MediaTask.EDIT_IMAGE)),
                provider("fal-t2v", MediaCapability.VIDEO, setOf(MediaTask.TEXT_TO_VIDEO)),
                provider("fal-i2v", MediaCapability.VIDEO, setOf(MediaTask.IMAGE_TO_VIDEO)),
            )
        kotlinx.coroutines.runBlocking {
            studioProviders.forEach { mediaProviderRepository.saveMediaProvider(it, null) }
        }

        overrideCoordinator = StudioModelOverrideCoordinator()
        generationCoordinator = StudioGenerationCoordinator(studioRepository, fakeDispatcher)
        getStudioCapablePresetsUseCase =
            GetStudioCapablePresetsUseCase(
                localModelRepository = localModelRepository,
                mediaProviderRepository = mediaProviderRepository,
            )

        val albumSelectionCoordinator = StudioAlbumSelectionCoordinator(studioRepository)
        val chatUseCases = FakeChatUseCases(listenToSpeechUseCase)
        val speechCoordinator = StudioSpeechCoordinator(chatUseCases)
        val playbackCoordinator = StudioPlaybackCoordinator(playbackController)
        val promptCoordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)
        val mediaActionsCoordinator =
            StudioMediaActionsCoordinator(shareMediaPort, studioRepository, generationCoordinator)

        viewModel =
            MultimodalViewModel(
                observeDefaultModelAssignments = com.browntowndev.enclave.domain.usecase.studio.ObserveDefaultModelAssignmentsUseCase(defaultModelRepository),
                getMediaProviders = com.browntowndev.enclave.domain.usecase.studio.GetMediaProvidersUseCase(mediaProviderRepository),
                moderatePromptUseCase = allowPromptModerationUseCase(),
                shouldModeratePromptUseCase = allowShouldModeratePromptUseCase(),
                submitModerationReportUseCase = submitModerationReportUseCase,
                getImageGenerationCapabilitiesUseCase = GetImageGenerationCapabilitiesUseCase(localModelRepository),
                generationCoordinator = generationCoordinator,
                albumSelectionCoordinator = albumSelectionCoordinator,
                speechCoordinator = speechCoordinator,
                playbackCoordinator = playbackCoordinator,
                studioUiStateReducer = StudioUiStateReducer(),
                capabilitiesCoordinator = StudioCapabilitiesCoordinator(),
                remoteCapabilitiesCoordinator = StudioRemoteCapabilitiesCoordinator(resolveMediaProviderCapabilitiesUseCase),
                promptCoordinator = promptCoordinator,
                mediaActionsCoordinator = mediaActionsCoordinator,
                getSelectedStudioMediaType = com.browntowndev.enclave.domain.usecase.studio.GetSelectedStudioMediaTypeUseCase(uiSelectionRepository),
                    setSelectedStudioMediaType = com.browntowndev.enclave.domain.usecase.studio.SetSelectedStudioMediaTypeUseCase(uiSelectionRepository),
                overrideCoordinator = overrideCoordinator,
                resolvedModelCoordinator =
                    StudioResolvedModelCoordinator(
                        resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase(),
                        mediaProviderRepository = mediaProviderRepository,
                    ),
                getStudioCapablePresetsUseCase = getStudioCapablePresetsUseCase,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun commitOverride(override: StudioModelOverride) {
        overrideCoordinator.openPicker(ModelType.IMAGE_GENERATION, null)
        overrideCoordinator.stageSelection(override)
        overrideCoordinator.confirmSelection()
    }

    @Nested
    @DisplayName("S01 — Override display name reflected in top bar + sheet header")
    inner class S01OverrideDisplayName {
        @Test
        @DisplayName("S01 — Setting override reflects override.displayName in uiState")
        fun s01_overrideDisplayNameReflected() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            commitOverride(StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal Aura"))
            advanceUntilIdle()

            assertEquals("Fal Aura", viewModel.uiState.value.activeModelDisplayName)
        }
    }

    @Nested
    @DisplayName("S02 — Setting an override updates display; clearing restores default")
    inner class S02ClearRestoresDefault {
        @Test
        @DisplayName("S02 — Setting override shows override name; clearing restores default")
        fun s02_clearRestoresDefault() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            commitOverride(StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal Aura"))
            advanceUntilIdle()

            assertEquals("Fal Aura", viewModel.uiState.value.activeModelDisplayName)

            overrideCoordinator.clearCommittedOverride()
            advanceUntilIdle()

            assertEquals("Default Image", viewModel.uiState.value.activeModelDisplayName)
        }
    }

    @Nested
    @DisplayName("S03 — Override cleared on media-type change")
    inner class S03MediaTypeChangeClears {
        @Test
        @DisplayName("S03 — onMediaTypeChange(VIDEO) clears override")
        fun s03_mediaTypeChangeClearsOverride() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            commitOverride(StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal Aura"))
            advanceUntilIdle()

            viewModel.onMediaTypeChange(MediaCapability.VIDEO.toUi())
            advanceUntilIdle()

            assertNull(overrideCoordinator.state.value.committedOverride)
            assertFalse(overrideCoordinator.state.value.picker.isVisible)
        }
    }

    @Nested
    @DisplayName("S04 — Override carried through generation into dispatcher")
    inner class S04GenerateCarriesOverride {
        @Test
        @DisplayName("S04 — generate() forwards committed override to dispatcher")
        fun s04_generateCarriesOverride() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            commitOverride(StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal Aura"))
            viewModel.onPromptChange("sunset")
            advanceUntilIdle()

            viewModel.generate()
            advanceUntilIdle()

            val expectedOverride =
                StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal Aura")

            assertEquals(expectedOverride, fakeDispatcher.lastStarted?.override)
            assertEquals(expectedOverride, activeGenerationFlow.value?.override)
        }
    }

    @Nested
    @DisplayName("S06 — Picker filters by resolved ModelType")
    inner class S06PickerModelType {
        @Test
        @DisplayName("S06 — onOpenModelPicker with reference image resolves IMAGE_EDIT")
        fun s06_pickerResolvesImageEdit() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onUpdateReferenceImage("content://.../ref.jpg")
            advanceUntilIdle()

            viewModel.onOpenModelPicker()
            advanceUntilIdle()

            assertEquals(ModelType.IMAGE_EDIT, overrideCoordinator.state.value.picker.modelType)
            val providerIds = viewModel.uiState.value.modelPickerAssets.mediaAssets.map { it.id }
            assertTrue(providerIds.contains("fal-edit"))
            assertFalse(providerIds.contains("fal-t2i"))
        }

        @Test
        @DisplayName("S06b — picker never surfaces API rows")
        fun s06b_pickerExcludesApiRows() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onOpenModelPicker()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.modelPickerAssets.apiAssets.isEmpty())
        }
    }

    @Nested
    @DisplayName("ModelType transitions clear stale overrides")
    inner class ModelTypeTransitionRevalidation {
        @Test
        @DisplayName("TEXT_TO_IMAGE override cleared when reference image flips slot to IMAGE_EDIT")
        fun imageGenerationToEditClearsTextToImageOverride() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            commitOverride(StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal T2I"))
            advanceUntilIdle()

            viewModel.onUpdateReferenceImage("content://.../ref.jpg")
            advanceUntilIdle()

            assertNull(overrideCoordinator.state.value.committedOverride)
        }

        @Test
        @DisplayName("EDIT_IMAGE override cleared when reference image removed flips slot to IMAGE_GENERATION")
        fun imageEditToGenerationClearsEditOverride() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onUpdateReferenceImage("content://.../ref.jpg")
            advanceUntilIdle()
            overrideCoordinator.openPicker(ModelType.IMAGE_EDIT, null)
            overrideCoordinator.stageSelection(
                StudioModelOverride.MediaProvider(MediaProviderId("fal-edit"), "Fal Edit"),
            )
            overrideCoordinator.confirmSelection()
            advanceUntilIdle()

            viewModel.onUpdateReferenceImage(null)
            advanceUntilIdle()

            assertNull(overrideCoordinator.state.value.committedOverride)
        }

        @Test
        @DisplayName("TEXT_TO_VIDEO override cleared when reference image flips slot to IMAGE_TO_VIDEO")
        fun videoGenerationToImageToVideoClearsOverride() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onMediaTypeChange(MediaCapability.VIDEO.toUi())
            advanceUntilIdle()
            overrideCoordinator.openPicker(ModelType.VIDEO_GENERATION, null)
            overrideCoordinator.stageSelection(
                StudioModelOverride.MediaProvider(MediaProviderId("fal-t2v"), "Fal T2V"),
            )
            overrideCoordinator.confirmSelection()
            advanceUntilIdle()

            viewModel.onUpdateReferenceImage("content://.../ref.jpg")
            advanceUntilIdle()

            assertNull(overrideCoordinator.state.value.committedOverride)
        }
    }

    @Nested
    @DisplayName("S10 — Override cleared on clearSession")
    inner class S10ClearSession {
        @Test
        @DisplayName("S10 — clearSession clears override and hides picker")
        fun s10_clearSessionClearsOverride() = runTest {
            backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            commitOverride(StudioModelOverride.MediaProvider(MediaProviderId("fal-t2i"), "Fal Aura"))
            advanceUntilIdle()

            viewModel.clearSession()
            advanceUntilIdle()

            assertNull(overrideCoordinator.state.value.committedOverride)
            assertFalse(overrideCoordinator.state.value.picker.isVisible)
        }
    }

    private fun provider(
        id: String,
        capability: MediaCapability,
        tasks: Set<MediaTask>,
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId(id),
            displayName = id,
            provider = ApiProvider.OPENAI,
            capability = capability,
            credentialAlias = "$id-alias",
            tasks = tasks,
        )
}