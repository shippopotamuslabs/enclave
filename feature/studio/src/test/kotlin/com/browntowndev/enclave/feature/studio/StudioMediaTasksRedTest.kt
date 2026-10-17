package com.browntowndev.enclave.feature.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.cash.turbine.test
import com.browntowndev.enclave.core.ui.component.FullscreenMediaAction
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.AspectRatio
import com.browntowndev.enclave.domain.model.media.GenerationQuality
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeModerationReportRepository
import com.browntowndev.enclave.core.testing.FakeShareMediaPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeUiSelectionRepository
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.usecase.media.GetImageGenerationCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.GetStudioCapablePresetsUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.feature.studio.test.FakeStudioGenerationDispatcher
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioMediaTasksRedTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()
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
                supportsVideo = false,
            ),
        )
    private val listenToSpeechUseCase = FakeListenToSpeechUseCase()
    private val chatUseCases = FakeChatUseCases(listenToSpeechUseCase)
    private val playbackController = FakeTtsPlaybackController()
    private val dispatcher = FakeStudioGenerationDispatcher()
    private val ephemeralMediaFlow get() = studioRepository.ephemeralState
    private val generationErrorFlow get() = studioRepository.generationErrorState
    private val speechEvents get() = listenToSpeechUseCase.events

    private val seenCapabilityProviderIds: List<String?>
        get() = resolveMediaProviderCapabilitiesUseCase.calls.map { it.first?.id?.value }

    private lateinit var generationCoordinator: StudioGenerationCoordinator
    private lateinit var viewModel: MultimodalViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        studioRepository.emitMedia(listOf(referenceAsset()))
        generationCoordinator = StudioGenerationCoordinator(studioRepository, dispatcher)
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `VM-01 image mode resolves IMAGE_EDIT when reference is attached`() =
        runTest {
            val imageProvider = mediaProvider("text-to-image", MediaCapability.IMAGE)
            val editProvider = mediaProvider("image-edit", MediaCapability.IMAGE)
            defaultModelRepository.emitDefaults(
                listOf(
                    default(ModelType.IMAGE_GENERATION, imageProvider.id),
                    default(ModelType.IMAGE_EDIT, editProvider.id),
                ),
            )
            mediaProviderRepository.emitProviders(listOf(imageProvider, editProvider))

            viewModel.uiState.test {
                awaitItem()
                viewModel.onUpdateReferenceImage("file://reference.png")
                advanceUntilIdle()

                assertEquals("image-edit", seenCapabilityProviderIds.last())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `VM-02 task-flag state reflects edit and image-to-video defaults`() =
        runTest {
            defaultModelRepository.emitDefaults(
                listOf(
                    default(ModelType.IMAGE_EDIT, MediaProviderId("image-edit")),
                    default(ModelType.IMAGE_TO_VIDEO, MediaProviderId("image-to-video")),
                ),
            )

            viewModel.uiState.test {
                val state = awaitItem()

                assertTrue(state.booleanFlag("hasImageEditModel"))
                assertTrue(state.booleanFlag("hasImageToVideoModel"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `VM-02b local image edit default enables reference image affordance`() =
        runTest {
            defaultModelRepository.emitDefaults(
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.IMAGE_EDIT,
                        localConfigId = LocalModelConfigurationId("sdxl-local-config"),
                    ),
                ),
            )

            viewModel.uiState.test {
                val state = awaitItem()

                assertTrue(state.booleanFlag("hasImageEditModel"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `VM-03 absent image-to-video default yields false task flags`() =
        runTest {
            defaultModelRepository.emitDefaults(emptyList())

            viewModel.uiState.test {
                val state = awaitItem()

                assertFalse(state.booleanFlag("hasImageEditModel"))
                assertFalse(state.booleanFlag("hasImageToVideoModel"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `VM-04 animate without image-to-video default emits error and skips generation`() =
        runTest {
            defaultModelRepository.emitDefaults(listOf(default(ModelType.VIDEO_GENERATION, MediaProviderId("text-to-video"))))

            viewModel.uiState.test {
                val initialState = awaitItem()
                assertEquals(VideoGenerationState.Idle, initialState.videoGenerationState)

                viewModel.onAnimateMedia(
                    assetId = "asset-1",
                    autoAnimate = true,
                )
                advanceUntilIdle()

                val errorState = awaitItem()
                assertEquals(
                    VideoGenerationState.Error(
                        sourceAssetId = "asset-1",
                        message = "No image-to-video model is set. Choose one in Settings to animate.",
                    ),
                    errorState.videoGenerationState,
                )
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(dispatcher.startedCalls.isEmpty())
        }

    @Test
    fun `COORD-01 coordinator blocks detail video generation without image-to-video default`() {
        generationCoordinator.startDetailVideoGeneration(referenceAsset(), hasImageToVideoModel = false)

        assertTrue(dispatcher.startedCalls.isEmpty())
        assertEquals(
            VideoGenerationState.Error(
                sourceAssetId = "asset-1",
                message = "No image-to-video model is set. Choose one in Settings to animate.",
            ),
            generationCoordinator.videoGenerationState.value,
        )
    }

    @Test
    fun `UI-01 image reference picker shown but disabled without edit model`() {
        composeReferenceDock(mediaType = MediaCapability.IMAGE, hasImageEditModel = false)

        composeTestRule.onNodeWithContentDescription("Reference image").assertIsDisplayed()
        composeTestRule.onNodeWithText(STUDIO_REFERENCE_IMAGE_EDIT_UNAVAILABLE).assertDoesNotExist()
    }

    @Test
    fun `UI-02 image reference picker shown when edit model is present`() {
        composeReferenceDock(mediaType = MediaCapability.IMAGE)

        composeTestRule.onNodeWithContentDescription("Reference image").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Reference image").assertIsEnabled()
    }

    @Test
    fun `UI-03 video reference picker shown but disabled without image-to-video model`() {
        composeReferenceDock(mediaType = MediaCapability.VIDEO, hasImageToVideoModel = false)

        composeTestRule.onNodeWithContentDescription("Reference image").assertIsDisplayed()
        composeTestRule.onNodeWithText(STUDIO_REFERENCE_I2V_UNAVAILABLE).assertDoesNotExist()
    }

    @Test
    fun `UI-04 animate disabled with exact image-to-video tooltip`() {
        val actions = studioDetailActions(hasImageToVideoModel = false)
        val animate = actions.single { it.label == "Animate" }
        val quickAnimate = actions.single { it.label == "Quick Animate" }

        assertFalse(animate.enabled)
        assertFalse(quickAnimate.enabled)
        assertEquals(
            "Your video model only supports text-to-video. Set an image-to-video model in Settings to animate.",
            animate.disabledTooltip,
        )
        assertEquals(
            "Your video model only supports text-to-video. Set an image-to-video model in Settings to animate.",
            quickAnimate.disabledTooltip,
        )
    }

    @Test
    fun `UI-05 animate enabled when image-to-video model is present`() {
        val actions = studioDetailActions(hasImageToVideoModel = true)
        val animate = actions.single { it.label == "Animate" }
        val quickAnimate = actions.single { it.label == "Quick Animate" }

        assertTrue(animate.enabled)
        assertTrue(quickAnimate.enabled)
        assertNull(animate.disabledTooltip)
        assertNull(quickAnimate.disabledTooltip)
    }

    private fun createViewModel(): MultimodalViewModel {
        val albumSelectionCoordinator = StudioAlbumSelectionCoordinator(studioRepository)
        val remoteCapabilitiesCoordinator =
            StudioRemoteCapabilitiesCoordinator(resolveMediaProviderCapabilitiesUseCase)
        val promptCoordinator = StudioPromptCoordinator(generationCoordinator, studioRepository)
        val mediaActionsCoordinator =
            StudioMediaActionsCoordinator(shareMediaPort, studioRepository, generationCoordinator)
        val uiSelectionRepository = FakeUiSelectionRepository()
        return MultimodalViewModel(
            observeDefaultModelAssignments = com.browntowndev.enclave.domain.usecase.studio.ObserveDefaultModelAssignmentsUseCase(defaultModelRepository),
            getMediaProviders = com.browntowndev.enclave.domain.usecase.studio.GetMediaProvidersUseCase(mediaProviderRepository),
            moderatePromptUseCase = allowPromptModerationUseCase(),
            shouldModeratePromptUseCase = allowShouldModeratePromptUseCase(),
            submitModerationReportUseCase = SubmitModerationReportUseCase(FakeModerationReportRepository()),
            getImageGenerationCapabilitiesUseCase = GetImageGenerationCapabilitiesUseCase(localModelRepository),
            generationCoordinator = generationCoordinator,
            albumSelectionCoordinator = albumSelectionCoordinator,
            speechCoordinator = StudioSpeechCoordinator(chatUseCases),
            playbackCoordinator = StudioPlaybackCoordinator(playbackController),
            studioUiStateReducer = StudioUiStateReducer(),
            capabilitiesCoordinator = StudioCapabilitiesCoordinator(),
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

    private fun composeReferenceDock(
        mediaType: MediaCapability,
        hasImageEditModel: Boolean = true,
        hasImageToVideoModel: Boolean = true,
    ) {
        composeTestRule.setContent {
            EnclaveTheme {
                StudioInputDock(
                    prompt = "",
                    speechState = SpeechState.Idle.toUi(),
                    mediaType = mediaType.toUi(),
                    hasImageEditModel = hasImageEditModel,
                    hasImageToVideoModel = hasImageToVideoModel,
                    referenceImageUri = null,
                    referenceMediaType = null,
                    isGenerating = false,
                    isContinualGenerationActive = false,
                    onPromptChange = {},
                    onReferenceImageClick = {},
                    onClearReferenceImage = {},
                    onMicClick = {},
                    onGenerateClick = {},
                )
            }
        }
    }

    private fun StudioUiState.booleanFlag(name: String): Boolean {
        val field = StudioUiState::class.java.declaredFields.single { it.name == name }
        field.isAccessible = true
        return field.get(this) as Boolean
    }

    @Suppress("UNCHECKED_CAST")
    private fun studioDetailActions(hasImageToVideoModel: Boolean): List<FullscreenMediaAction> {
        val method =
            Class
                .forName("com.browntowndev.enclave.feature.studio.StudioDetailScreenKt")
                .declaredMethods
                .single { it.name == "studioDetailActions" }
        method.isAccessible = true
        return method.invoke(
            null,
            StudioMediaUi(
                id = "image-1",
                localUri = "file://image.png",
                prompt = "Prompt",
                mediaType = MediaCapability.IMAGE,
                createdAt = 1L,
            ),
            hasImageToVideoModel,
            {},
            { _: Boolean -> },
            {},
            {},
            {},
        ) as List<FullscreenMediaAction>
    }

    private fun default(
        modelType: ModelType,
        mediaProviderId: MediaProviderId,
    ): DefaultModelAssignment =
        DefaultModelAssignment(
            modelType = modelType,
            mediaProviderId = mediaProviderId,
        )

    private fun mediaProvider(
        id: String,
        capability: MediaCapability,
    ): MediaProviderAsset =
        MediaProviderAsset(
            id = MediaProviderId(id),
            displayName = id,
            provider = ApiProvider.FAL,
            capability = capability,
            modelName = "fal-ai/$id",
            credentialAlias = "alias",
        )

    private fun referenceAsset(): StudioMediaAsset =
        StudioMediaAsset(
            id = "asset-1",
            localUri = "file://reference.png",
            prompt = "Animate",
            mediaType = MediaCapability.IMAGE.name,
            createdAt = 1L,
        )
}
