package com.browntowndev.enclave.feature.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.media.StudioGenerationDispatcherPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.media.GetImageGenerationCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaProviderCapabilitiesUseCase
import com.browntowndev.enclave.feature.studio.components.StudioOptionsSheetContent
import com.browntowndev.enclave.feature.studio.coordinators.StudioAlbumSelectionCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioGenerationCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioMediaActionsCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPlaybackCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioPromptCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioRemoteCapabilitiesCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioSpeechCoordinator
import com.browntowndev.enclave.feature.studio.coordinators.StudioUiStateReducer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudioImageSettingsUiTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `STU-004 Studio bottom sheet shows Resolution for SDXL as 1024x1024`() =
    runTest {
      Dispatchers.setMain(testDispatcher)
      val viewModel = createAssignedModelViewModel(LocalModelConfigurationId("sdxl-local-config"))
      val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
      advanceUntilIdle()

      composeTestRule.setContent {
        EnclaveTheme {
          StudioOptionsSheetContent(
            state = viewModel.uiState.value,
            onDismiss = {},
            onUpdateSettings = {},
            onContinualModeChange = {},
            onMediaTypeChange = {},
            onTemplateSelected = {},
          )
        }
      }

      composeTestRule.onNodeWithText("Resolution").assertIsDisplayed()
      composeTestRule.onNodeWithText("1024x1024").assertIsDisplayed()
      job.cancel()
    }

  @Test
  fun `STU-005 Studio bottom sheet shows Diffusion Steps for SDXL`() =
    runTest {
      Dispatchers.setMain(testDispatcher)
      val viewModel = createAssignedModelViewModel(LocalModelConfigurationId("sdxl-local-config"))
      val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
      advanceUntilIdle()

      composeTestRule.setContent {
        EnclaveTheme {
          StudioOptionsSheetContent(
            state = viewModel.uiState.value,
            onDismiss = {},
            onUpdateSettings = {},
            onContinualModeChange = {},
            onMediaTypeChange = {},
            onTemplateSelected = {},
          )
        }
      }

      composeTestRule.onNodeWithText("Diffusion Steps").assertIsDisplayed()
      composeTestRule.onNodeWithText("8").assertIsDisplayed()
      composeTestRule.onNodeWithText("12").assertIsDisplayed()
      composeTestRule.onNodeWithText("16").assertIsDisplayed()
      composeTestRule.onNodeWithText("20").assertIsDisplayed()
      composeTestRule.onNodeWithText("24").assertIsDisplayed()
      composeTestRule.onNodeWithText("30").assertIsDisplayed()
      job.cancel()
    }

  @Test
  fun `STU-006 Studio bottom sheet hides Diffusion Steps for SD15`() =
    runTest {
      Dispatchers.setMain(testDispatcher)
      val viewModel = createAssignedModelViewModel(LocalModelConfigurationId("sd15-local-config"))
      val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
      advanceUntilIdle()

      composeTestRule.setContent {
        EnclaveTheme {
          StudioOptionsSheetContent(
            state = viewModel.uiState.value,
            onDismiss = {},
            onUpdateSettings = {},
            onContinualModeChange = {},
            onMediaTypeChange = {},
            onTemplateSelected = {},
          )
        }
      }

      composeTestRule.onNodeWithText("Resolution").assertIsDisplayed()
      composeTestRule.onNodeWithText("512x512").assertIsDisplayed()
      composeTestRule.onNodeWithText("Diffusion Steps").assertDoesNotExist()
      job.cancel()
    }

  private fun createAssignedModelViewModel(localConfigId: LocalModelConfigurationId): MultimodalViewModel {
    val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
    val defaultModelRepository = mockk<DefaultModelRepositoryPort>(relaxed = true)
    val mediaProviderRepository = mockk<MediaProviderRepositoryPort>(relaxed = true)
    val resolveMediaProviderCapabilitiesUseCase = mockk<ResolveMediaProviderCapabilitiesUseCase>()
    val getImageGenerationCapabilitiesUseCase = mockk<GetImageGenerationCapabilitiesUseCase>(relaxed = true)
    val chatUseCases = mockk<ChatUseCases>()
    val listenToSpeechUseCase = mockk<ListenToSpeechUseCase>()
    val playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true)
    val dispatcher = mockk<StudioGenerationDispatcherPort>(relaxed = true)
    val speechEvents = MutableSharedFlow<com.browntowndev.enclave.domain.port.media.SpeechState>(extraBufferCapacity = 8)

    val localImageAssignment =
      DefaultModelAssignment(
        modelType = ModelType.IMAGE_GENERATION,
        localConfigId = localConfigId,
      )
    every { defaultModelRepository.observeDefaults() } returns flowOf(listOf(localImageAssignment))
    every { studioRepository.observeAllMedia() } returns MutableStateFlow(emptyList())
    every { studioRepository.observeEphemeralMedia() } returns MutableStateFlow(emptyList())
    every { studioRepository.observeGenerationError() } returns MutableStateFlow(null)
    every { studioRepository.observeAllAlbums() } returns MutableStateFlow(emptyList())
    every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase
    every { listenToSpeechUseCase.invoke(any(), any()) } returns speechEvents

    val generationCoordinator = StudioGenerationCoordinator(studioRepository, dispatcher)
    return MultimodalViewModel(
      defaultModelRepository = defaultModelRepository,
      mediaProviderRepository = mediaProviderRepository,
      moderatePromptUseCase = ModeratePromptUseCase(MutablePromptModerationPort()),
      shouldModeratePromptUseCase = allowShouldModeratePromptUseCase(),
      resolveMediaModelTypeUseCase = ResolveMediaModelTypeUseCase(),
      getImageGenerationCapabilitiesUseCase = getImageGenerationCapabilitiesUseCase,
      generationCoordinator = generationCoordinator,
      albumSelectionCoordinator = StudioAlbumSelectionCoordinator(studioRepository),
      speechCoordinator = StudioSpeechCoordinator(chatUseCases),
      playbackCoordinator = StudioPlaybackCoordinator(playbackController),
      studioUiStateReducer = StudioUiStateReducer(),
      capabilitiesCoordinator = StudioCapabilitiesCoordinator(),
      remoteCapabilitiesCoordinator = StudioRemoteCapabilitiesCoordinator(resolveMediaProviderCapabilitiesUseCase),
      promptCoordinator = StudioPromptCoordinator(generationCoordinator, studioRepository),
      mediaActionsCoordinator = StudioMediaActionsCoordinator(mockk(relaxed = true), studioRepository, generationCoordinator),
    )
  }

  private class MutablePromptModerationPort : PromptModerationPort {
    override suspend fun classify(prompt: String): PromptModerationScores =
      PromptModerationScores(scoresByLabel = emptyMap())
  }
}
