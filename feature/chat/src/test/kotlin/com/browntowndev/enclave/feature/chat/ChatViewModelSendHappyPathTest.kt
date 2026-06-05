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
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.PromptSafetyGateRequest
import com.browntowndev.enclave.domain.usecase.chat.PromptSafetyGateResult
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
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

        mockAllowedSafetyGate(
            chatUseCases = chatUseCases,
            userMessageId = MessageId("user-send-1"),
            chatId = ChatId("chat-send-1"),
            wasNewChat = false,
        )
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
    @DisplayName("DOPG-V-01 — onSendMessage routes through safety gate")
    fun dopgV01_sendUsesSafetyGateOnly() =
        runTest {
            viewModel.onInputChange("Hello crew")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                chatUseCases.processPromptWithSafetyGate(
                    match { request ->
                        request.shouldModerate &&
                            request.promptTextForModeration == "Hello crew" &&
                            request.assistantMessageId.value.isNotEmpty()
                    },
                )
            }
            coVerify {
                harness.inferenceOrchestrator.sendMessage(
                    scope = any(),
                    input = "Hello crew",
                    selectedMode = any(),
                    selectedImageUris = any(),
                    fileAttachments = any(),
                    initialChatId = any(),
                    shouldModerate = true,
                    onInputCleared = any(),
                    onModerationBlocked = any(),
                )
            }
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
    @DisplayName("onSendMessage passes staged image URIs into safety gate")
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
                chatUseCases.processPromptWithSafetyGate(
                    match { request ->
                        request.message.content.text == "Describe these" &&
                            request.message.content.imageUris == listOf("staged://img-1", "staged://img-2")
                    },
                )
            }
            collectJob.cancel()
        }

    @Test
    @DisplayName("DOPG-V-02 — blocked send restores input and emits moderation snackbar")
    fun sendBlockedByModeration() =
        runTest {
            coEvery { chatUseCases.processPromptWithSafetyGate(any()) } returns
                PromptSafetyGateResult.Blocked(
                    moderationResult =
                        com.browntowndev.enclave.domain.usecase.inference.PromptModerationResult(
                            blocked = true,
                            scoresByLabel = emptyMap(),
                            canonicalScores = emptyMap(),
                            triggeredLabels = listOf("sexual/minors"),
                            combinedIllegalScore = 0.9,
                        ),
                )
            val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
            runCurrent()
            val snackbar = async { viewModel.moderationMessages.first() }

            viewModel.onInputChange("blocked input")
            viewModel.onSendMessage()
            advanceUntilIdle()

            assertEquals("Prompt rejected due to moderation", snackbar.await())
            assertEquals("blocked input", viewModel.uiState.value.inputText)
            coVerify(exactly = 1) { chatUseCases.processPromptWithSafetyGate(any()) }
            collectJob.cancel()
        }

    @Test
    @DisplayName("DOPG-V-03 — blocked send invokes safety gate")
    fun dopgV03_blockedSendNeverCallsProcessPrompt() =
        runTest {
            coEvery { chatUseCases.processPromptWithSafetyGate(any()) } returns
                PromptSafetyGateResult.Blocked(
                    moderationResult =
                        com.browntowndev.enclave.domain.usecase.inference.PromptModerationResult(
                            blocked = true,
                            scoresByLabel = emptyMap(),
                            canonicalScores = emptyMap(),
                            triggeredLabels = listOf("sexual/minors"),
                            combinedIllegalScore = 0.9,
                        ),
                )

            viewModel.onInputChange("blocked input")
            viewModel.onSendMessage()
            advanceUntilIdle()

            coVerify(exactly = 1) { chatUseCases.processPromptWithSafetyGate(any()) }
        }

    @Test
    @DisplayName("DOPG-V-04 — trusted model bypass passes shouldModerate false to orchestrator and gate")
    fun dopgV04_trustedBypassPassesShouldModerateFalse() =
        runTest {
            val shouldModerateMock =
                mockk<ShouldModeratePromptUseCase> {
                    coEvery { this@mockk.invoke(any()) } returns false
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
                    shouldModeratePromptUseCase = shouldModerateMock,
                )
            viewModel = harness.viewModel

            viewModel.onInputChange("blocked input but trusted")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            coVerify {
                harness.inferenceOrchestrator.sendMessage(
                    scope = any(),
                    input = "blocked input but trusted",
                    selectedMode = any(),
                    selectedImageUris = any(),
                    fileAttachments = any(),
                    initialChatId = any(),
                    shouldModerate = false,
                    onInputCleared = any(),
                    onModerationBlocked = any(),
                )
            }
            coVerify(exactly = 1) {
                chatUseCases.processPromptWithSafetyGate(
                    match { request: PromptSafetyGateRequest -> !request.shouldModerate },
                )
            }
        }
}
