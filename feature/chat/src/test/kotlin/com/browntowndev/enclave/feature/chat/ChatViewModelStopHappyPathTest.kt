package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelStopHappyPathTest {
    private val chatId = ChatId("chat-stop-happy")
    private val userMessageId = MessageId("user-stop-happy")
    private val assistantMessageId = MessageId("assistant-stop-happy")
    private val turnKey = ActiveChatTurnKey(chatId, assistantMessageId)

    private lateinit var messagesFlow: MutableStateFlow<List<Message>>
    private lateinit var toolEvents: MutableSharedFlow<ToolExecutionEvent>
    private lateinit var turnStore: ActiveChatTurnStore
    private lateinit var cancelInferenceUseCase: FakeCancelInferenceUseCase
    private lateinit var chatRepository: FakeChatRepository
    private lateinit var viewModel: ChatViewModel
    private lateinit var harness: ChatViewModelTestSupport.Harness

    @BeforeEach
    fun setUp() {
        messagesFlow = MutableStateFlow(emptyList())
        turnStore = ActiveChatTurnStore()
        cancelInferenceUseCase = FakeCancelInferenceUseCase()
        chatRepository = FakeChatRepository()

        val settingsUseCases = FakeChatSettingsUseCases()
        val chatUseCases = FakeChatUseCases()
        chatUseCases.getChatUseCase.flowsByChatId[chatId] = messagesFlow
        val inferenceLockManager = FakeInferenceLockManager()
        val toolExecutionEventPort = FakeToolExecutionEventPort()
        toolEvents = toolExecutionEventPort.events
        val errorHandler = FakeViewModelErrorHandler()
        val studioRepository = FakeStudioRepository()

        harness =
            ChatViewModelTestSupport.createHarness(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value)),
                cancelInferenceUseCase = cancelInferenceUseCase,
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase(),
                activeModelProvider = FakeActiveModelProvider(),
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                activeChatTurnSnapshotPort = turnStore,
                playbackController = FakeTtsPlaybackController(),
                studioRepository = studioRepository,
                shareChatUseCase = FakeShareChatUseCase(),
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
            )
        viewModel = harness.viewModel
        harness.inferenceOrchestrator.seedRequestedTurnKey(turnKey)
    }

    @Test
    @DisplayName("stopGeneration persists live snapshot text, cancels inference, and clears tool banner")
    fun stopGenerationHappyPath() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            messagesFlow.value =
                listOf(
                    Message(
                        id = userMessageId,
                        chatId = chatId,
                        role = Role.USER,
                        content = Content(text = "Question"),
                        messageState = MessageState.COMPLETE,
                        createdAt = 1L,
                    ),
                    Message(
                        id = assistantMessageId,
                        chatId = chatId,
                        role = Role.ASSISTANT,
                        content = Content(text = ""),
                        messageState = MessageState.PROCESSING,
                        modelType = ModelType.FAST,
                        createdAt = 2L,
                    ),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isGenerating)

            turnStore.publish(
                turnKey,
                AccumulatedMessages(
                    messages =
                        mapOf(
                            assistantMessageId to
                                MessageSnapshot(
                                    messageId = assistantMessageId,
                                    modelType = ModelType.FAST,
                                    content = "Stopped mid-stream",
                                    thinkingRaw = "",
                                    thinkingDurationSeconds = 0L,
                                    thinkingStartTime = 0L,
                                    thinkingEndTime = 0L,
                                    messageState = MessageState.GENERATING,
                                    isComplete = false,
                                    transcriptSegments = emptyList(),
                                ),
                        ),
                ),
            )
            advanceUntilIdle()

            toolEvents.emit(
                ToolExecutionEvent.Started(
                    eventId = "tool-stop-1",
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = "{}",
                    chatId = chatId,
                    userMessageId = userMessageId,
                    modelType = ModelType.FAST,
                ),
            )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.activeToolCallBanner != null)

            viewModel.stopGeneration()
            advanceUntilIdle()

            assertTrue(cancelInferenceUseCase.callCount >= 1)
            assertTrue(
                chatRepository.updateMessageContentCalls.contains(
                    assistantMessageId to "Stopped mid-stream",
                ),
            )
            assertTrue(
                chatRepository.updateMessageStateCalls.contains(
                    assistantMessageId to MessageState.COMPLETE,
                ),
            )
            assertNull(viewModel.uiState.value.activeToolCallBanner)
        }

    private fun com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator.seedRequestedTurnKey(key: ActiveChatTurnKey?) {
        val field =
            com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator::class.java
                .getDeclaredField("_requestedTurnKey")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<ActiveChatTurnKey?>
        flow.value = key
    }
}
