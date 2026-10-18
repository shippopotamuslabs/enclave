package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.feature.chat.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModelSendHappyPathTest {
    private val settingsUseCases = FakeChatSettingsUseCases()
    private val chatUseCases = FakeChatUseCases()
    private val stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase()
    private val cancelInferenceUseCase = FakeCancelInferenceUseCase()
    private val inferenceLockManager = FakeInferenceLockManager()
    private val toolExecutionEventPort = FakeToolExecutionEventPort()
    private val modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase()
    private val errorHandler = FakeViewModelErrorHandler()
    private val loggingPort = FakeLoggingPort()
    private val activeChatTurnStore = FakeActiveChatTurnSnapshotPort()
    private val playbackController = FakeTtsPlaybackController()
    private val studioRepository = FakeStudioRepository()
    private val shareChatUseCase = FakeShareChatUseCase()
    private val chatRepository = FakeChatRepository()

    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var messagesFlow: MutableStateFlow<List<Message>>
    private lateinit var viewModel: ChatViewModel
    private lateinit var harness: ChatViewModelTestSupport.Harness

    @BeforeEach
    fun setUp() {
        activeModelProvider = FakeActiveModelProvider()
        val visionConfig =
            ActiveModelConfiguration(
                modelId = "vision-model",
                id = ApiModelConfigurationId("vision-config"),
                isLocal = false,
                name = "Vision",
                systemPrompt = null,
                temperature = 0.7,
                topK = 40,
                topP = 0.95,
                maxTokens = 512,
                minP = 0.0,
                repetitionPenalty = 1.1,
                contextWindow = 4096,
                thinkingEnabled = false,
                isMultimodal = true,
            )
        activeModelProvider.configuration = visionConfig

        messagesFlow = MutableStateFlow(emptyList())
        chatUseCases.getChatUseCase.flow = messagesFlow

        mockSubmitPrompt(
            chatUseCases = chatUseCases,
            userMessageId = MessageId("user-send-1"),
            chatId = ChatId("chat-send-1"),
        )
        chatUseCases.generateChatResponseUseCase.flow =
            flow {
                awaitCancellation()
            }

        harness =
            ChatViewModelTestSupport.createHarness(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(),
                cancelInferenceUseCase = cancelInferenceUseCase,
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                activeModelProvider = activeModelProvider,
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                loggingPort = loggingPort,
                activeChatTurnSnapshotPort = activeChatTurnStore,
                playbackController = playbackController,
                studioRepository = studioRepository,
                shareChatUseCase = shareChatUseCase,
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            )
        viewModel = harness.viewModel
    }

    @Test
    @DisplayName("DOPG-V-01 — onSendMessage routes through submit prompt")
    fun dopgV01_sendUsesSubmitPromptOnly() =
        runTest {
            viewModel.onInputChange("Hello crew")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            // The orchestrator's sendMessage is the only path to submitPrompt, so exactly
            // one submit with the typed text proves the VM routed through it.
            assertEquals(1, chatUseCases.submitPromptUseCase.calls.size)
            val request = chatUseCases.submitPromptUseCase.calls.single()
            assertEquals("Hello crew", request.message.content.text)
            assertTrue(request.assistantMessageId.value.isNotEmpty())
        }

    @Test
    @DisplayName("onSendMessage clears input, sets generating, and starts generation with selected mode")
    fun sendTextHappyPath() =
        runTest {
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            runCurrent()

            viewModel.onModeChange(ChatModeUi.THINKING)
            runCurrent()
            viewModel.onInputChange("Hello crew")
            viewModel.onSendMessage()
            runCurrent()

            assertEquals("", viewModel.uiState.value.inputText)

            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            val activeTurn =
                checkNotNull(harness.inferenceOrchestrator.requestedTurnKey.value) {
                    "Send pipeline must register an active turn"
                }
            messagesFlow.value =
                listOf(
                    Message(
                        id = activeTurn.assistantMessageId,
                        chatId = activeTurn.chatId,
                        role = Role.ASSISTANT,
                        content = Content(text = ""),
                        messageState = MessageState.PROCESSING,
                        modelType = ModelType.THINKING,
                        createdAt = 101L,
                    ),
                )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isGenerating)

            assertTrue(
                chatUseCases.generateChatResponseUseCase.calls.any { call ->
                    call.prompt == "Hello crew" &&
                        call.userMessageId == MessageId("user-send-1") &&
                        call.chatId == ChatId("chat-send-1") &&
                        call.responseMode == ChatResponseMode.Thinking
                },
            )
            collectJob.cancel()
        }

    @Test
    @DisplayName("onSendMessage passes staged image URIs into safety gate")
    fun sendWithImagesHappyPath() =
        runTest {
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            runCurrent()

            stageImageAttachmentUseCase.resultsByUri["content://img-1"] = "staged://img-1"
            stageImageAttachmentUseCase.resultsByUri["content://img-2"] = "staged://img-2"
            viewModel.onImagesSelected(listOf("content://img-1"))
            viewModel.onImagesSelected(listOf("content://img-2"))
            advanceUntilIdle()

            viewModel.onInputChange("Describe these")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            assertTrue(
                chatUseCases.submitPromptUseCase.calls.any { request ->
                    request.message.content.text == "Describe these" &&
                        request.message.content.imageUris == listOf("staged://img-1", "staged://img-2")
                },
            )
            collectJob.cancel()
        }

    @Test
    fun `compaction failure restores prompt and emits prompt too large snackbar`() =
        runTest {
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            chatUseCases.generateChatResponseUseCase.flow =
                flowOf(
                    AccumulatedMessages(
                        messages =
                            mapOf(
                                MessageId("assistant-failed") to
                                    MessageSnapshot(
                                        messageId = MessageId("assistant-failed"),
                                        modelType = ModelType.FAST,
                                        content =
                                            "Sorry, something went wrong. Please try again.\n\n" +
                                                "Error: Agent loop failed: tier=pipeline, " +
                                                "reason=Projected prompt exceeds hard ceiling",
                                        thinkingRaw = "",
                                        isComplete = true,
                                    ),
                            ),
                    ),
                )
            val snackbar = async { viewModel.chatErrorMessages.first() }

            viewModel.onInputChange("oversized prompt")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)

            assertEquals(R.string.chat_prompt_too_large, snackbar.await())
            advanceUntilIdle()
            assertEquals("oversized prompt", viewModel.uiState.value.inputText)
            collectJob.cancel()
        }

    @Test
    @DisplayName("DOPG-V-04 — trusted model bypass passes shouldModerateResponse false to orchestrator")
    fun dopgV04_trustedBypassPassesShouldModerateFalse() =
        runTest {
            val shouldModerateFake =
                FakeShouldModeratePromptUseCase().apply {
                    result = false
                }
            // Complete the generation so the orchestrator reaches its post-turn
            // moderation gate, whose debug log records the flag it received.
            chatUseCases.generateChatResponseUseCase.flow =
                flowOf(
                    AccumulatedMessages(
                        messages =
                            mapOf(
                                MessageId("assistant-trusted") to
                                    MessageSnapshot(
                                        messageId = MessageId("assistant-trusted"),
                                        modelType = ModelType.FAST,
                                        content = "A perfectly ordinary answer",
                                        thinkingRaw = "",
                                        isComplete = true,
                                    ),
                            ),
                    ),
                )
            harness =
                ChatViewModelTestSupport.createHarness(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = SavedStateHandle(),
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    activeModelProvider = activeModelProvider,
                    toolExecutionEventPort = toolExecutionEventPort,
                    errorHandler = errorHandler,
                    loggingPort = loggingPort,
                    activeChatTurnSnapshotPort = activeChatTurnStore,
                    playbackController = playbackController,
                    studioRepository = studioRepository,
                    shareChatUseCase = shareChatUseCase,
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                    shouldModeratePromptUseCase = shouldModerateFake,
                )
            viewModel = harness.viewModel

            viewModel.onInputChange("blocked input but trusted")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            assertTrue(
                loggingPort.debugCalls.any { (_, message) ->
                    message.contains("Post-turn moderation gate: shouldModerateResponse=false")
                },
            )
            assertEquals(1, chatUseCases.submitPromptUseCase.calls.size)
        }
}
