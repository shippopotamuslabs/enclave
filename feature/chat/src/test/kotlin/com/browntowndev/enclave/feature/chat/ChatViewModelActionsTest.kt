package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.feature.chat.R
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModelActionsTest {
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
    private val activeModelProvider: ActiveModelProviderPort = mockk()
    private val shareChatUseCase: ShareChatUseCase = mockk(relaxed = true)

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var defaultModelsFlow: MutableStateFlow<List<DefaultModelAssignment>>

    @BeforeEach
    fun setUp() {
        val visionConfig =
            ActiveModelConfiguration(
                modelId = "test-model",
                id = ApiModelConfigurationId("vision-api-config"),
                isLocal = false,
                name = "Vision API",
                systemPrompt = "Describe images briefly.",
                reasoningEffort = null,
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
        coEvery { activeModelProvider.getActiveConfigurationByOverride(any()) } returns null

        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }

        val listenToSpeechUseCase = mockk<ListenToSpeechUseCase>(relaxed = true)
        every { listenToSpeechUseCase.invoke(any(), any()) } returns MutableSharedFlow()
        every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase
        every { chatUseCases.processFileAttachmentUseCase } returns processFileAttachmentUseCase

        coEvery { chatUseCases.getChat(any()) } returns MutableStateFlow(emptyList())
        every { chatUseCases.mergeMessagesUseCase } returns MergeMessagesUseCase()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        defaultModelsFlow = MutableStateFlow(emptyList())
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns defaultModelsFlow
        val setDefaultModelUseCase = mockk<SetDefaultModelUseCase>(relaxed = true)
        every { assignmentUseCases.setDefaultModel } returns setDefaultModelUseCase
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())
        val getChatCapablePresetsUseCase = mockk<com.browntowndev.enclave.domain.usecase.chat.GetChatCapablePresetsUseCase>()
        every { getChatCapablePresetsUseCase.invoke() } returns
            flowOf(
                com.browntowndev.enclave.domain.usecase.chat.ChatCapablePresets(
                    localAssets = emptyList(),
                    apiAssets = emptyList(),
                ),
            )
        every { chatUseCases.getChatCapablePresetsUseCase } returns getChatCapablePresetsUseCase

        chatViewModel =
            ChatViewModelTestSupport.createViewModel(
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
                chatRepository = mockk(relaxed = true),
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            )
    }

    @Test
    fun `onInputChange updates inputText in uiState`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            chatViewModel.onInputChange("Hello world")
            runCurrent()

            assertEquals("Hello world", chatViewModel.uiState.value.inputText)
            collectJob.cancel()
        }

    @Test
    fun `onEditMessage updates inputText`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            chatViewModel.onEditMessage("Edited message")
            runCurrent()

            assertEquals("Edited message", chatViewModel.uiState.value.inputText)
            collectJob.cancel()
        }

    @Test
    fun `onModeChange updates selectedMode`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            assertEquals(ChatModeUi.FAST, chatViewModel.uiState.value.selectedMode)

            chatViewModel.onModeChange(ChatModeUi.THINKING)
            runCurrent()

            assertEquals(ChatModeUi.THINKING, chatViewModel.uiState.value.selectedMode)
            collectJob.cancel()
        }

    @Test
    fun `onModeChange to COUNCIL clears selected image when photo not enabled for crew`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            // First, set an image
            coEvery { stageImageAttachmentUseCase("test-uri") } returns "test-uri"
            chatViewModel.onImagesSelected(listOf("test-uri"))
            runCurrent()

            // Switch to COUNCIL mode - photo attachment should be disabled for crew without API vision
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null
            chatViewModel.onModeChange(ChatModeUi.COUNCIL)
            advanceUntilIdle()

            // Photo attachment should be disabled, so images should be cleared
            assertTrue(
                chatViewModel.uiState.value.selectedImageUris
                    .isEmpty(),
            )
            collectJob.cancel()
        }

    @Test
    fun `onImagesSelected updates selectedImageUris when photo attachment enabled`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            coEvery { stageImageAttachmentUseCase("content://image") } returns "content://image"

            chatViewModel.onImagesSelected(listOf("content://image"))
            advanceUntilIdle()

            assertEquals(listOf("content://image"), chatViewModel.uiState.value.selectedImageUris)
            collectJob.cancel()
        }

    @Test
    fun `onImagesSelected with empty list does not clear selectedImageUris`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            coEvery { stageImageAttachmentUseCase("content://image") } returns "content://image"
            chatViewModel.onImagesSelected(listOf("content://image"))
            advanceUntilIdle()

            chatViewModel.onImagesSelected(emptyList())
            runCurrent()

            assertFalse(
                chatViewModel.uiState.value.selectedImageUris
                    .isEmpty(),
            )
            assertEquals(listOf("content://image"), chatViewModel.uiState.value.selectedImageUris)
            collectJob.cancel()
        }

    @Test
    fun `clearSelectedImages clears selectedImageUris`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            coEvery { stageImageAttachmentUseCase("content://image") } returns "content://image"
            chatViewModel.onImagesSelected(listOf("content://image"))
            advanceUntilIdle()
            assertEquals(listOf("content://image"), chatViewModel.uiState.value.selectedImageUris)

            chatViewModel.clearSelectedImages()
            runCurrent()
            assertTrue(
                chatViewModel.uiState.value.selectedImageUris
                    .isEmpty(),
            )
            collectJob.cancel()
        }

    @Test
    fun `clearSelectedFile clears fileName and fileText`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            val metadata =
                com.browntowndev.enclave.domain.port.media
                    .FileAttachmentMetadata("test.txt", "text/plain", "content")
            coEvery { processFileAttachmentUseCase("content://file") } returns metadata
            chatViewModel.onFileSelected("content://file")
            advanceUntilIdle()
            assertEquals("test.txt", chatViewModel.uiState.value.selectedFileName)

            chatViewModel.clearSelectedFile()
            runCurrent()
            assertNull(chatViewModel.uiState.value.selectedFileName)
            collectJob.cancel()
        }

    @Test
    fun `stopGeneration does not cancel when not generating`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            // Not in generating state, so stopGeneration should be a no-op
            assertFalse(chatViewModel.uiState.value.isGenerating)
            chatViewModel.stopGeneration()
            runCurrent()

            // cancelInferenceUseCase should NOT be called since we're not generating
            verify(exactly = 0) { cancelInferenceUseCase() }
            collectJob.cancel()
        }

    @Test
    fun `createNewChat resets state`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            chatViewModel.onInputChange("Some text")
            runCurrent()
            assertEquals("Some text", chatViewModel.uiState.value.inputText)

            chatViewModel.createNewChat()
            runCurrent()

            assertEquals("", chatViewModel.uiState.value.inputText)
            assertTrue(
                chatViewModel.uiState.value.selectedImageUris
                    .isEmpty(),
            )
            assertNull(chatViewModel.uiState.value.chatId)
            collectJob.cancel()
        }

    @Test
    fun `uiState isGlobalInferenceBlocked reflects lock manager`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            assertFalse(chatViewModel.uiState.value.isGlobalInferenceBlocked)
            collectJob.cancel()
        }

    @Test
    fun `uiState isGlobalInferenceBlocked true when inference blocked`() =
        runTest {
            every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(true)

            chatViewModel =
                ChatViewModelTestSupport.createViewModel(
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )

            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            assertTrue(chatViewModel.uiState.value.isGlobalInferenceBlocked)
            collectJob.cancel()
        }

    @Test
    fun `initial state has FAST mode selected`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            assertEquals(ChatModeUi.FAST, chatViewModel.uiState.value.selectedMode)
            collectJob.cancel()
        }

    @Test
    fun `onFileSelected with null clears file state`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            chatViewModel.onFileSelected(null)
            runCurrent()

            assertNull(chatViewModel.uiState.value.selectedFileName)
            collectJob.cancel()
        }

    @Test
    fun `onInputChange with empty string clears input`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            chatViewModel.onInputChange("Hello")
            runCurrent()
            assertEquals("Hello", chatViewModel.uiState.value.inputText)

            chatViewModel.onInputChange("")
            runCurrent()
            assertEquals("", chatViewModel.uiState.value.inputText)
            collectJob.cancel()
        }

    @Test
    fun `onModeChange from FAST to THINKING preserves photo attachment`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            // Both FAST and THINKING should allow photos when vision is available
            chatViewModel.onModeChange(ChatModeUi.THINKING)
            advanceUntilIdle()

            // Photo attachment should still be enabled since activeModelProvider returns multimodal config
            assertTrue(chatViewModel.uiState.value.isPhotoAttachmentEnabled)
            collectJob.cancel()
        }

    @Test
    fun `onModeChange to COUNCIL disables photo when no API vision model`() =
        runTest {
            // No vision API model configured
            coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null

            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            chatViewModel.onModeChange(ChatModeUi.COUNCIL)
            advanceUntilIdle()

            assertFalse(chatViewModel.uiState.value.isPhotoAttachmentEnabled)
            collectJob.cancel()
        }

    @Test
    fun `onImagesSelected clears images when photo attachment is disabled`() =
        runTest {
            // No vision model configured
            coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null

            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            chatViewModel.onImagesSelected(listOf("content://image"))
            advanceUntilIdle()

            // Images should be cleared since photo attachment is disabled
            assertTrue(
                chatViewModel.uiState.value.selectedImageUris
                    .isEmpty(),
            )
            collectJob.cancel()
        }

    @Test
    fun `removeImage removes only the matching staged uri`() =
        runTest {
            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            coEvery { stageImageAttachmentUseCase("content://keep") } returns "staged://keep"
            coEvery { stageImageAttachmentUseCase("content://drop") } returns "staged://drop"
            chatViewModel.onImagesSelected(listOf("content://keep"))
            chatViewModel.onImagesSelected(listOf("content://drop"))
            advanceUntilIdle()
            assertEquals(
                listOf("staged://keep", "staged://drop"),
                chatViewModel.uiState.value.selectedImageUris,
            )

            chatViewModel.removeImage("staged://drop")
            runCurrent()

            assertEquals(listOf("staged://keep"), chatViewModel.uiState.value.selectedImageUris)
            collectJob.cancel()
        }

    @Test
    fun `onModeChange trims staged images when policy maxItems shrinks`() =
        runTest {
            val apiVision =
                ActiveModelConfiguration(
                    modelId = "api-vision",
                    id =
                        com.browntowndev.enclave.domain.model.config
                            .ApiModelConfigurationId("vision"),
                    isLocal = false,
                    name = "API Vision",
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
                    apiProvider = com.browntowndev.enclave.domain.model.inference.ApiProvider.OPENAI,
                )
            val localThinking =
                apiVision.copy(
                    id =
                        com.browntowndev.enclave.domain.model.config
                            .ApiModelConfigurationId("local-thinking"),
                    isLocal = true,
                    modelId = "local-thinking",
                )
            coEvery { activeModelProvider.getActiveConfiguration(any()) } answers {
                when (firstArg<com.browntowndev.enclave.domain.model.inference.ModelType>()) {
                    com.browntowndev.enclave.domain.model.inference.ModelType.FAST -> null
                    com.browntowndev.enclave.domain.model.inference.ModelType.THINKING -> localThinking
                    com.browntowndev.enclave.domain.model.inference.ModelType.VISION -> apiVision
                    else -> null
                }
            }

            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            coEvery { stageImageAttachmentUseCase("content://one") } returns "staged://one"
            coEvery { stageImageAttachmentUseCase("content://two") } returns "staged://two"
            chatViewModel.onImagesSelected(listOf("content://one"))
            chatViewModel.onImagesSelected(listOf("content://two"))
            advanceUntilIdle()
            assertEquals(2, chatViewModel.uiState.value.selectedImageUris.size)

            chatViewModel.onModeChange(ChatModeUi.THINKING)
            advanceUntilIdle()

            assertEquals(1, chatViewModel.uiState.value.selectedImageUris.size)
            assertEquals(
                "staged://one",
                chatViewModel.uiState.value.selectedImageUris
                    .single(),
            )
            collectJob.cancel()
        }

    @Test
    fun `reportBlockedPrompt submits blocked outcome report and emits feedback message`() =
        runTest {
            val submitModerationReportUseCase = mockk<SubmitModerationReportUseCase>(relaxed = true)
            val customViewModel =
                ChatViewModelTestSupport.createViewModel(
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                    submitModerationReportUseCase = submitModerationReportUseCase,
                )

            customViewModel.moderationMessages.test {
                customViewModel.reportBlockedPrompt("Test blocked prompt")
                assertEquals(R.string.chat_moderation_feedback_submitted, awaitItem())
            }

            coVerify { submitModerationReportUseCase("Test blocked prompt", "blocked") }
        }

    @Test
    fun `reportResponse submits response text when message exists in uiState`() =
        runTest {
            val submitModerationReportUseCase = mockk<SubmitModerationReportUseCase>(relaxed = true)
            val testChatId = ChatId("test-chat")
            val testMessageId = MessageId("msg-assistant-1")
            val assistantMessage =
                Message(
                    id = testMessageId,
                    chatId = testChatId,
                    content = Content("Harmful response that should be blocked"),
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                    createdAt = 1L,
                )
            val chatUseCasesWithMessage = mockk<ChatUseCases>(relaxed = true)
            val listenToSpeechUseCase = mockk<ListenToSpeechUseCase>(relaxed = true)
            every { listenToSpeechUseCase.invoke(any(), any()) } returns MutableSharedFlow()
            every { chatUseCasesWithMessage.listenToSpeechUseCase } returns listenToSpeechUseCase
            every { chatUseCasesWithMessage.processFileAttachmentUseCase } returns processFileAttachmentUseCase
            every { chatUseCasesWithMessage.mergeMessagesUseCase } returns MergeMessagesUseCase()
            coEvery { chatUseCasesWithMessage.getChat(any()) } returns flowOf(listOf(assistantMessage))
            val customViewModel =
                ChatViewModelTestSupport.createViewModel(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCasesWithMessage,
                    savedStateHandle = SavedStateHandle(mapOf("chatId" to testChatId.value)),
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                    submitModerationReportUseCase = submitModerationReportUseCase,
                )

            // Collect uiState so messages flow through the reducer
            val collectJob = backgroundScope.launch { customViewModel.uiState.collect {} }
            runCurrent()

            customViewModel.moderationMessages.test {
                customViewModel.reportResponse(testMessageId)
                assertEquals(R.string.chat_moderation_feedback_submitted, awaitItem())
            }

            coVerify { submitModerationReportUseCase("Harmful response that should be blocked", "allowed") }
            collectJob.cancel()
        }

    @Test
    fun `reportResponse does nothing when message not found`() =
        runTest {
            val submitModerationReportUseCase = mockk<SubmitModerationReportUseCase>(relaxed = true)
            val customViewModel =
                ChatViewModelTestSupport.createViewModel(
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                    submitModerationReportUseCase = submitModerationReportUseCase,
                )

            customViewModel.reportResponse(MessageId("nonexistent"))

            coVerify(exactly = 0) { submitModerationReportUseCase(any(), any()) }
        }

    @Test
    fun `S08 — createNewChat clears model override`() =
        runTest {
            val harness =
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )
            val coordinator = harness.chatInputCoordinator
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )
            coordinator.onModelOverrideSelected(override)
            coordinator.onModeChange(ChatModeUi.OTHER)

            val collectJob = backgroundScope.launch { harness.viewModel.uiState.collect { } }
            runCurrent()

            harness.viewModel.createNewChat()
            runCurrent()

            assertNull(coordinator.selectedModelOverride.value)
            assertNotEquals(ChatModeUi.OTHER, coordinator.selectedMode.value)
            collectJob.cancel()
        }

    @Test
    fun `S09 — onModeChange to FAST clears model override`() =
        runTest {
            val harness =
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )
            val coordinator = harness.chatInputCoordinator
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-grok-beta"),
                    displayName = "Grok Beta",
                )
            coordinator.onModelOverrideSelected(override)
            coordinator.onModeChange(ChatModeUi.OTHER)

            val collectJob = backgroundScope.launch { harness.viewModel.uiState.collect { } }
            runCurrent()

            harness.viewModel.onModeChange(ChatModeUi.FAST)
            runCurrent()

            assertNull(coordinator.selectedModelOverride.value)
            assertEquals(ChatModeUi.FAST, coordinator.selectedMode.value)
            collectJob.cancel()
        }

    @Test
    fun `S02 — onOtherClick sets isModelOverrideSheetVisible to true`() =
        runTest {
            val harness =
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )

            val collectJob = backgroundScope.launch { harness.viewModel.uiState.collect { } }
            runCurrent()

            assertFalse(harness.viewModel.uiState.value.isModelOverrideSheetVisible)

            harness.viewModel.onOtherClick()
            runCurrent()

            assertTrue(harness.viewModel.uiState.value.isModelOverrideSheetVisible)
            collectJob.cancel()
        }

    @Test
    fun `S07 — setDefaultModel is never called when override is selected`() =
        runTest {
            val setDefaultModelUseCase = mockk<SetDefaultModelUseCase>(relaxed = true)
            val customAssignmentUseCases = mockk<SettingsAssignmentUseCases>()
            val customGetDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
            every { settingsUseCases.assignments } returns customAssignmentUseCases
            every { customAssignmentUseCases.getDefaultModels } returns customGetDefaultModelsUseCase
            every { customGetDefaultModelsUseCase() } returns defaultModelsFlow
            every { customAssignmentUseCases.setDefaultModel } returns setDefaultModelUseCase

            val harness =
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
                    chatRepository = mockk(relaxed = true),
                    exportArtifactDocumentsUseCase = mockk(relaxed = true),
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                )

            val collectJob = backgroundScope.launch { harness.viewModel.uiState.collect { } }
            runCurrent()

            harness.viewModel.onModelOverrideSelected(
                localId = null,
                apiId = ApiModelConfigurationId("api-grok-beta"),
                ttsId = null,
                mediaId = null,
                liveVoiceId = null,
            )
            runCurrent()

            coVerify(exactly = 0) {
                setDefaultModelUseCase(any(), any(), any(), any(), any(), any())
            }
            collectJob.cancel()
        }
}
