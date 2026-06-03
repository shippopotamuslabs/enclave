package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.AssistantPlaceholderResult
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val settingsUseCases: SettingsUseCases = mockk()
    private val chatUseCases: ChatUseCases = mockk()
    private val processFileAttachmentUseCase: ProcessFileAttachmentUseCase = mockk()
    private val stageImageAttachmentUseCase: StageImageAttachmentUseCase = mockk(relaxed = true)
    private val cancelInferenceUseCase: CancelInferenceUseCase = mockk(relaxed = true)
    private val inferenceLockManager: InferenceLockManager = mockk()
    private val toolExecutionEventPort: ToolExecutionEventPort = mockk()
    private val modelDisplayNamesUseCase: GetModelDisplayNameUseCase = mockk()
    private val errorHandler: ViewModelErrorHandler = mockk()
    private val loggingPort: LoggingPort = mockk(relaxed = true)
    private val activeChatTurnStore: ActiveChatTurnSnapshotPort = mockk(relaxed = true)
    private val playbackController: TtsPlaybackControllerPort = mockk(relaxed = true)
    private val studioRepository: StudioRepositoryPort = mockk(relaxed = true)
    private val shareChatUseCase: ShareChatUseCase = mockk(relaxed = true)
    private val chatRepository: com.browntowndev.enclave.domain.port.repository.ChatRepository =
        mockk(relaxed = true)

    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var messagesFlow: MutableStateFlow<List<Message>>
    private lateinit var viewModel: ChatViewModel
    private lateinit var harness: ChatViewModelTestSupport.Harness

    @BeforeEach
    fun setUp() {
        activeModelProvider = mockk()
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
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns visionConfig

        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
            CoroutineExceptionHandler { _, _ -> }

        val listenToSpeechUseCase = mockk<ListenToSpeechUseCase>(relaxed = true)
        every { listenToSpeechUseCase.invoke(any(), any()) } returns MutableSharedFlow()
        every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase
        every { chatUseCases.processFileAttachmentUseCase } returns processFileAttachmentUseCase

        messagesFlow = MutableStateFlow(emptyList())
        coEvery { chatUseCases.getChat(any()) } returns messagesFlow
        every { chatUseCases.mergeMessagesUseCase } returns MergeMessagesUseCase()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())

        coEvery { chatUseCases.processPrompt(any()) } returns
            UserPromptResult(
                userMessageId = MessageId("user-send-1"),
                chatId = ChatId("chat-send-1"),
                userMessageCreatedAtMillis = 100L,
            )
        coEvery {
            chatUseCases.createAssistantPlaceholder(
                chatId = any(),
                userMessageCreatedAtMillis = any(),
                assistantMessageId = any(),
            )
        } returns AssistantPlaceholderResult(assistantMessageId = MessageId("placeholder-unused"))
        coEvery {
            chatUseCases.generateChatResponse(
                prompt = any(),
                userMessageId = any(),
                assistantMessageId = any(),
                chatId = any(),
                mode = any(),
                backgroundInferenceEnabled = any(),
            )
        } returns
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
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            )
        viewModel = harness.viewModel
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

            coVerify {
                chatUseCases.generateChatResponse(
                    prompt = "Hello crew",
                    userMessageId = MessageId("user-send-1"),
                    assistantMessageId = any(),
                    chatId = ChatId("chat-send-1"),
                    mode = Mode.THINKING,
                    backgroundInferenceEnabled = any(),
                )
            }
            collectJob.cancel()
        }

    @Test
    @DisplayName("onSendMessage passes staged image URIs into processPrompt")
    fun sendWithImagesHappyPath() =
        runTest {
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            runCurrent()

            coEvery { stageImageAttachmentUseCase("content://img-1") } returns "staged://img-1"
            coEvery { stageImageAttachmentUseCase("content://img-2") } returns "staged://img-2"
            viewModel.onImagesSelected(listOf("content://img-1"))
            viewModel.onImagesSelected(listOf("content://img-2"))
            advanceUntilIdle()

            viewModel.onInputChange("Describe these")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            coVerify {
                chatUseCases.processPrompt(
                    match { message ->
                        message.content.text == "Describe these" &&
                            message.content.imageUris == listOf("staged://img-1", "staged://img-2")
                    },
                )
            }
            collectJob.cancel()
        }

    @Test
    @DisplayName("onSendMessage rejects moderated prompts before processing")
    fun sendBlockedByModeration() =
        runTest {
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
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                    moderatePromptUseCase =
                        ModeratePromptUseCase(
                            FixedPromptModerationPort(
                                PromptModerationScores(
                                    scoresByLabel = mapOf("S3" to 0.66, "OK" to 0.34),
                                    labelAliases = mapOf("sexual/minors" to listOf("S3")),
                                    thresholds = mapOf("sexual/minors" to 0.65),
                                ),
                            ),
                        ),
                )
            viewModel = harness.viewModel
            val snackbar = async { viewModel.moderationMessages.first() }

            viewModel.onInputChange("blocked input")
            viewModel.onSendMessage()
            advanceUntilIdle()

            assertEquals("Prompt rejected due to moderation", snackbar.await())
            coVerify(exactly = 0) { chatUseCases.processPrompt(any()) }
        }

    private class FixedPromptModerationPort(
        private val scores: PromptModerationScores,
    ) : PromptModerationPort {
        override suspend fun classify(prompt: String): PromptModerationScores = scores
    }
}
