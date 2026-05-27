package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
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
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
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
import kotlin.coroutines.CoroutineContext

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
    private lateinit var cancelInferenceUseCase: CancelInferenceUseCase
    private lateinit var chatRepository: ChatRepository
    private lateinit var viewModel: ChatViewModel
    private lateinit var harness: ChatViewModelTestSupport.Harness

    @BeforeEach
    fun setUp() {
        messagesFlow = MutableStateFlow(emptyList())
        toolEvents = MutableSharedFlow(extraBufferCapacity = 4)
        turnStore = ActiveChatTurnStore()
        cancelInferenceUseCase = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)

        val settingsUseCases = mockk<SettingsUseCases>(relaxed = true)
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        val chatUseCases = mockk<ChatUseCases>(relaxed = true)
        val inferenceLockManager = mockk<InferenceLockManager>(relaxed = true)
        val toolExecutionEventPort = mockk<ToolExecutionEventPort>()

        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
        coEvery { chatUseCases.getChat(chatId) } returns messagesFlow
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { toolExecutionEventPort.events } returns toolEvents

        val errorHandler = mockk<ViewModelErrorHandler>()
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
            CoroutineExceptionHandler { _: CoroutineContext, _ -> }

        val studioRepository = mockk<StudioRepositoryPort>(relaxed = true)
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())

        coEvery { chatRepository.updateMessageState(any(), any()) } just runs
        coEvery { chatRepository.updateMessageContent(any(), any()) } just runs

        harness =
            ChatViewModelTestSupport.createHarness(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value)),
                cancelInferenceUseCase = cancelInferenceUseCase,
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = mockk(relaxed = true),
                activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true),
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                loggingPort = mockk(relaxed = true),
                activeChatTurnSnapshotPort = turnStore,
                playbackController = mockk(relaxed = true),
                studioRepository = studioRepository,
                shareChatUseCase = mockk(relaxed = true),
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = mockk<StageImageAttachmentUseCase>(relaxed = true),
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

            coVerify { cancelInferenceUseCase() }
            coVerify {
                chatRepository.updateMessageContent(
                    assistantMessageId,
                    "Stopped mid-stream",
                )
            }
            coVerify { chatRepository.updateMessageState(assistantMessageId, MessageState.COMPLETE) }
            assertNull(viewModel.uiState.value.activeToolCallBanner)
        }

    private fun com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator.seedRequestedTurnKey(
        key: ActiveChatTurnKey?,
    ) {
        val field =
            com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator::class.java
                .getDeclaredField("_requestedTurnKey")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<ActiveChatTurnKey?>
        flow.value = key
    }
}
