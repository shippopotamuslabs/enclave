package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val settingsUseCases = FakeChatSettingsUseCases()
    private val chatUseCases = FakeChatUseCases()
    private val errorHandler = FakeViewModelErrorHandler()
    private lateinit var defaultModelsFlow: MutableStateFlow<List<DefaultModelAssignment>>
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setUp() {
        defaultModelsFlow = MutableStateFlow(emptyList())
        settingsUseCases.assignments.getDefaultModels.flow = defaultModelsFlow

        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(),
                cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                inferenceLockManager = FakeInferenceLockManager(),
                modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase(),
                activeModelProvider = FakeActiveModelProvider(),
                toolExecutionEventPort = FakeToolExecutionEventPort(),
                errorHandler = errorHandler,
                loggingPort = FakeLoggingPort(),
                activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort(),
                playbackController = FakeTtsPlaybackController(),
                studioRepository = FakeStudioRepository(),
                shareChatUseCase = FakeShareChatUseCase(),
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
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
