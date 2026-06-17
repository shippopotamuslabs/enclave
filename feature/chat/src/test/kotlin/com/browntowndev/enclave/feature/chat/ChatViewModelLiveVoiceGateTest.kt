package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ChatViewModelLiveVoiceGateTest {
    private val settingsUseCases = mockk<SettingsUseCases>()
    private val chatUseCases = mockk<ChatUseCases>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private lateinit var defaultModelsFlow: MutableStateFlow<List<DefaultModelAssignment>>
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setUp() {
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
            CoroutineExceptionHandler { _, _ -> }
        every { errorHandler.handleError(any(), any(), any(), any()) } just Runs

        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        defaultModelsFlow = MutableStateFlow(emptyList())
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns defaultModelsFlow

        coEvery { chatUseCases.getChat(any()) } returns MutableStateFlow(emptyList())
        every { chatUseCases.mergeMessagesUseCase } returns
            com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase()

        val inferenceLockManager = mockk<InferenceLockManager>()
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)

        val modelDisplayNamesUseCase = mockk<GetModelDisplayNameUseCase>(relaxed = true)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"

        val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
        val toolExecutionEventPort = mockk<ToolExecutionEventPort>()
        every { toolExecutionEventPort.events } returns MutableSharedFlow()

        val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())

        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(),
                cancelInferenceUseCase = mockk(relaxed = true),
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                activeModelProvider = activeModelProvider,
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                loggingPort = mockk(relaxed = true),
                activeChatTurnSnapshotPort = mockk(relaxed = true),
                playbackController = mockk<TtsPlaybackControllerPort>(relaxed = true),
                studioRepository = studioRepository,
                shareChatUseCase = mockk(relaxed = true),
                chatRepository = mockk(relaxed = true),
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = mockk(relaxed = true),
            )
    }

    @Test
    @DisplayName("LV-GATE-001: hasLiveVoiceProviderAssigned is true when LIVE_VOICE default exists")
    fun lvGate001_providerAssigned() =
        runTest {
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.LIVE_VOICE,
                        liveVoiceProviderId = LiveVoiceProviderId("lv-1"),
                    ),
                )

            viewModel.uiState.test {
                val uiState = awaitItem()
                assertTrue(uiState.hasLiveVoiceProviderAssigned)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @DisplayName("LV-GATE-002: hasLiveVoiceProviderAssigned is false when no LIVE_VOICE assignment")
    fun lvGate002_noProviderAssigned() =
        runTest {
            defaultModelsFlow.value =
                listOf(
                    DefaultModelAssignment(
                        modelType = ModelType.LIVE_VOICE,
                        liveVoiceProviderId = LiveVoiceProviderId("lv-1"),
                    ),
                )

            viewModel.uiState.test {
                assertTrue(awaitItem().hasLiveVoiceProviderAssigned)

                defaultModelsFlow.value =
                    listOf(
                        DefaultModelAssignment(
                            modelType = ModelType.FAST,
                        ),
                    )

                assertFalse(awaitItem().hasLiveVoiceProviderAssigned)
                cancelAndIgnoreRemainingEvents()
            }
        }
}