package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelHapticsStateTest {
    private lateinit var messagesFlow: MutableStateFlow<List<Message>>
    private lateinit var settingsFlow: MutableStateFlow<SettingsData>
    private lateinit var toolExecutionEventPort: FakeToolExecutionEventPort
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setup() {
        messagesFlow = MutableStateFlow(emptyList())
        settingsFlow = MutableStateFlow(SettingsData(hapticResponse = false))
        toolExecutionEventPort = FakeToolExecutionEventPort()

        val settingsUseCases = FakeChatSettingsUseCases()
        settingsUseCases.preferences.getSettings.flow = settingsFlow
        val chatUseCases = FakeChatUseCases()
        chatUseCases.getChatUseCase.flow = messagesFlow
        val inferenceLockManager = FakeInferenceLockManager()
        val modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase()
        val activeModelProvider = FakeActiveModelProvider()

        viewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(mapOf("chatId" to "chat-1")),
                cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                activeModelProvider = activeModelProvider,
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = FakeViewModelErrorHandler(),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                playbackController = FakeTtsPlaybackController(),
                studioRepository = FakeStudioRepository(),
                shareChatUseCase = FakeShareChatUseCase(),
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
            )
    }

    @Test
    fun responseHapticPhase_engineLoading_isNone() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            messagesFlow.value = listOf(assistantMessage(MessageState.ENGINE_LOADING))
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
        }

    @Test
    fun responseHapticPhase_processing_isNone() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            messagesFlow.value = listOf(assistantMessage(MessageState.PROCESSING))
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
        }

    @Test
    fun responseHapticPhase_thinking_isThinkingPulseAndPreservesPreference() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            messagesFlow.value =
                listOf(
                    assistantMessage(
                        state = MessageState.THINKING,
                        contentText = "",
                        thinkingRaw = "thinking",
                        thinkingStartTime = 1L,
                    ),
                )
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.ThinkingPulse, viewModel.uiState.value.responseHapticPhase)
            assertFalse(viewModel.uiState.value.hapticResponse)
        }

    @Test
    fun responseHapticPhase_lateThinkingAfterVisibleAnswer_isNone() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            messagesFlow.value =
                listOf(
                    assistantMessage(
                        state = MessageState.THINKING,
                        contentText = "already visible answer",
                        thinkingRaw = "late thinking",
                        thinkingStartTime = 1L,
                    ),
                )
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
        }

    @Test
    fun responseHapticPhase_generatingBeforeVisibleTextAdvances_isNone() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            messagesFlow.value =
                listOf(
                    assistantMessage(
                        state = MessageState.GENERATING,
                        thinkingRaw = "done thinking",
                        thinkingDurationSeconds = 3L,
                        thinkingStartTime = 1L,
                    ),
                )
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
            assertEquals(0L, viewModel.uiState.value.responseHapticPulseKey)
        }

    @Test
    fun responseHapticPhase_visibleAnswerTextAdvances_isGeneratingBuzzPulse() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            val initial =
                assistantMessage(
                    state = MessageState.GENERATING,
                    contentText = "visible",
                    thinkingRaw = "done thinking",
                    thinkingDurationSeconds = 3L,
                    thinkingStartTime = 1L,
                )
            messagesFlow.value = listOf(initial)
            advanceUntilIdle()

            messagesFlow.value =
                listOf(
                    initial.copy(content = Content(text = "visible answer")),
                )
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.GeneratingBuzz, viewModel.uiState.value.responseHapticPhase)
            assertEquals(1L, viewModel.uiState.value.responseHapticPulseKey)
        }

    @Test
    fun responseHapticPhase_generatingWithActiveTool_isNoneAndShowsToolBanner() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            val assistant =
                assistantMessage(
                    state = MessageState.GENERATING,
                    thinkingRaw = "done thinking",
                    thinkingDurationSeconds = 3L,
                    thinkingStartTime = 1L,
                )
            messagesFlow.value = listOf(assistant)
            advanceUntilIdle()

            toolExecutionEventPort.events.emit(
                ToolExecutionEvent.Started(
                    eventId = "tool-1",
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = "{}",
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("user-1"),
                    modelType = ModelType.FAST,
                ),
            )
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
            assertEquals(assistant.id, viewModel.uiState.value.activeIndicatorMessageId)
            assertEquals(
                ToolCallBannerKind.SEARCH,
                viewModel.uiState.value.activeToolCallBanner
                    ?.kind,
            )
        }

    @Test
    fun activeTool_withoutActiveMessageIndicator_anchorsBannerToLatestAssistantAndKeepsTurnStoppable() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            val assistant = assistantMessage(MessageState.COMPLETE)
            messagesFlow.value = listOf(assistant)
            advanceUntilIdle()

            toolExecutionEventPort.events.emit(
                ToolExecutionEvent.Started(
                    eventId = "tool-1",
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = "{}",
                    chatId = ChatId("chat-1"),
                    userMessageId = MessageId("user-1"),
                    modelType = ModelType.FAST,
                ),
            )
            advanceUntilIdle()

            assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
            assertEquals(assistant.id, viewModel.uiState.value.activeIndicatorMessageId)
            assertEquals(
                ToolCallBannerKind.SEARCH,
                viewModel.uiState.value.activeToolCallBanner
                    ?.kind,
            )
            assertTrue(viewModel.uiState.value.hasActiveIndicator)
            assertTrue(viewModel.uiState.value.isGenerating)
        }

    private fun assistantMessage(
        state: MessageState,
        contentText: String = "assistant text",
        thinkingRaw: String? = null,
        thinkingDurationSeconds: Long? = null,
        thinkingStartTime: Long? = null,
    ): Message =
        Message(
            id = MessageId("assistant-${state.name}"),
            chatId = ChatId("chat-1"),
            content = Content(text = contentText),
            role = Role.ASSISTANT,
            messageState = state,
            createdAt = 1L,
            thinkingRaw = thinkingRaw,
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingStartTime = thinkingStartTime,
            modelType = ModelType.FAST,
        )
}
