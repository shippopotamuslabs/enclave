package com.browntowndev.enclave.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.media.SaveMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsMediaTasksRedTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SETTINGS-01 model configuration offers four Studio and Media slots`() {
        composeModelConfiguration(
            assignments =
                listOf(
                    assignment(ModelType.IMAGE_GENERATION, "Image Generation"),
                    assignment(ModelType.IMAGE_EDIT, "Image Edit"),
                    assignment(ModelType.VIDEO_GENERATION, "Video Generation"),
                    assignment(ModelType.IMAGE_TO_VIDEO, "Image to Video"),
                ),
        )

        composeTestRule.onNodeWithText("Image Generation").assertExists()
        composeTestRule.onNodeWithText("Image Edit").assertExists()
        composeTestRule.onNodeWithText("Video Generation").assertExists()
        composeTestRule.onNodeWithText("Image to Video").assertExists()
    }

    @Test
    fun `SETTINGS-02 edit-slot picker filters to EDIT_IMAGE providers`() {
        composeModelConfiguration(
            assignments = listOf(assignment(ModelType.IMAGE_EDIT, "Image Edit")),
            editingSlot = ModelType.IMAGE_EDIT,
            mediaAssets =
                listOf(
                    mediaAsset("t2i", "Fal Text Image", MediaCapability.IMAGE, setOf(MediaTask.TEXT_TO_IMAGE)),
                    mediaAsset("edit", "Fal Edit", MediaCapability.IMAGE, setOf(MediaTask.EDIT_IMAGE)),
                    mediaAsset("both", "Fal Both", MediaCapability.IMAGE, setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE)),
                ),
        )

        composeTestRule.onNodeWithText("Fal Text Image").assertDoesNotExist()
        composeTestRule.onNodeWithText("Fal Edit").assertExists()
        composeTestRule.onNodeWithText("Fal Both").assertExists()
    }

    @Test
    fun `SETTINGS-03 image-to-video slot picker filters to IMAGE_TO_VIDEO providers`() {
        composeModelConfiguration(
            assignments = listOf(assignment(ModelType.IMAGE_TO_VIDEO, "Image to Video")),
            editingSlot = ModelType.IMAGE_TO_VIDEO,
            mediaAssets =
                listOf(
                    mediaAsset("t2v", "Fal Text Video", MediaCapability.VIDEO, setOf(MediaTask.TEXT_TO_VIDEO)),
                    mediaAsset("i2v", "Fal Image Video", MediaCapability.VIDEO, setOf(MediaTask.IMAGE_TO_VIDEO)),
                ),
        )

        composeTestRule.onNodeWithText("Fal Text Video").assertDoesNotExist()
        composeTestRule.onNodeWithText("Fal Image Video").assertExists()
    }

    @Test
    fun `SETTINGS-04 provider save persists selected tasks`() =
        runTest {
            val saveMediaProviderUseCase = mockk<SaveMediaProviderUseCase>()
            val mediaUseCases = mockk<SettingsMediaUseCases>()
            val assignmentsUseCases = mockk<SettingsAssignmentUseCases>(relaxed = true)
            every { mediaUseCases.saveMediaProvider } returns saveMediaProviderUseCase
            coEvery { saveMediaProviderUseCase(any(), any()) } returns MediaProviderId("edit")
            val coordinator = mediaProvidersCoordinator(mediaUseCases = mediaUseCases)
            val mediaState =
                MutableStateFlow(
                    MediaProvidersTransientState(
                        assetDraft =
                            MediaProviderAssetUi(
                                id = MediaProviderId("edit"),
                                displayName = "Fal Edit",
                                provider = ApiProvider.FAL,
                                capability = MediaCapability.IMAGE,
                                tasks = setOf(MediaTask.EDIT_IMAGE),
                                modelName = "fal-ai/flux-pro/kontext",
                                credentialAlias = "alias",
                            ),
                    ),
                )

            coordinator.onSaveMediaProvider(
                scope = this,
                tag = "tag",
                mediaState = mediaState,
                currentApiKey = MutableStateFlow(""),
                assignmentsUseCases = assignmentsUseCases,
                onSuccess = {},
            )
            advanceUntilIdle()

            coVerify {
                saveMediaProviderUseCase(
                    match { it.tasks == setOf(MediaTask.EDIT_IMAGE) },
                    null,
                )
            }
        }

    @Test
    fun `SETTINGS-05 edit task set-default targets IMAGE_EDIT slot`() =
        runTest {
            val saveMediaProviderUseCase = mockk<SaveMediaProviderUseCase>()
            val setDefaultModelUseCase = mockk<SetDefaultModelUseCase>(relaxed = true)
            val mediaUseCases = mockk<SettingsMediaUseCases>()
            val assignmentsUseCases = mockk<SettingsAssignmentUseCases>(relaxed = true)
            every { mediaUseCases.saveMediaProvider } returns saveMediaProviderUseCase
            every { assignmentsUseCases.setDefaultModel } returns setDefaultModelUseCase
            coEvery { saveMediaProviderUseCase(any(), any()) } returns MediaProviderId("edit")
            val coordinator = mediaProvidersCoordinator(mediaUseCases = mediaUseCases)
            val mediaState =
                MutableStateFlow(
                    MediaProvidersTransientState(
                        assetDraft =
                            MediaProviderAssetUi(
                                id = MediaProviderId("edit"),
                                displayName = "Fal Edit",
                                provider = ApiProvider.FAL,
                                capability = MediaCapability.IMAGE,
                                tasks = setOf(MediaTask.EDIT_IMAGE),
                                modelName = "fal-ai/flux-pro/kontext",
                                credentialAlias = "alias",
                                useAsDefault = true,
                            ),
                    ),
                )

            coordinator.onSaveMediaProvider(
                scope = this,
                tag = "tag",
                mediaState = mediaState,
                currentApiKey = MutableStateFlow(""),
                assignmentsUseCases = assignmentsUseCases,
                onSuccess = {},
            )
            advanceUntilIdle()

            coVerify {
                setDefaultModelUseCase(
                    modelType = ModelType.IMAGE_EDIT,
                    localConfigId = null,
                    apiConfigId = null,
                    mediaProviderId = MediaProviderId("edit"),
                )
            }
        }

    @Test
    fun `MUT-03 edit-slot picker excludes text-to-image-only providers`() {
        composeModelConfiguration(
            assignments = listOf(assignment(ModelType.IMAGE_EDIT, "Image Edit")),
            editingSlot = ModelType.IMAGE_EDIT,
            mediaAssets =
                listOf(
                    mediaAsset("t2i", "Fal Text Image", MediaCapability.IMAGE, setOf(MediaTask.TEXT_TO_IMAGE)),
                    mediaAsset("edit", "Fal Edit", MediaCapability.IMAGE, setOf(MediaTask.EDIT_IMAGE)),
                ),
        )

        composeTestRule.onNodeWithText("Fal Text Image").assertDoesNotExist()
        composeTestRule.onNodeWithText("Fal Edit").assertExists()
        assertEquals(1, composeTestRule.onAllNodesWithText("Fal Edit").fetchSemanticsNodes().size)
    }

    private fun composeModelConfiguration(
        assignments: List<DefaultModelAssignmentUi>,
        editingSlot: ModelType? = null,
        mediaAssets: List<MediaProviderAssetUi> = emptyList(),
    ) {
        composeTestRule.setContent {
            EnclaveTheme {
                ModelConfigurationScreen(
                    uiState =
                        SettingsUiState(
                            assignments =
                                ModelAssignmentsUiState(
                                    assignments = assignments,
                                    isDialogOpen = editingSlot != null,
                                    editingSlot = editingSlot,
                                ),
                            mediaProvidersSheet = MediaProvidersSheetUiState(assets = mediaAssets),
                        ),
                    onNavigateBack = {},
                    onSetDefaultModel = { _, _, _, _, _ -> },
                    onShowAssignmentDialog = { _, _ -> },
                )
            }
        }
    }

    private fun mediaProvidersCoordinator(mediaUseCases: SettingsMediaUseCases): MediaProvidersCoordinator {
        val errorHandler = mockk<ViewModelErrorHandler>()
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }
        return MediaProvidersCoordinator(
            apiProviderUseCases = mockk<SettingsApiProviderUseCases>(relaxed = true),
            mediaUseCases = mediaUseCases,
            errorHandler = errorHandler,
        )
    }

    private fun assignment(
        modelType: ModelType,
        label: String,
    ): DefaultModelAssignmentUi =
        DefaultModelAssignmentUi(
            modelType = modelType,
            source = ModelSource.API,
            currentModelName = "None Assigned",
            displayLabel = label,
        )

    private fun mediaAsset(
        id: String,
        name: String,
        capability: MediaCapability,
        tasks: Set<MediaTask>,
    ): MediaProviderAssetUi =
        MediaProviderAsset(
            id = MediaProviderId(id),
            displayName = name,
            provider = ApiProvider.FAL,
            capability = capability,
            modelName = "fal-ai/$id",
            credentialAlias = "alias",
            tasks = tasks,
        ).let {
            MediaProviderAssetUi(
                id = it.id,
                displayName = it.displayName,
                provider = it.provider,
                capability = it.capability,
                tasks = it.tasks,
                modelName = it.modelName.orEmpty(),
                credentialAlias = it.credentialAlias,
            )
        }
}
