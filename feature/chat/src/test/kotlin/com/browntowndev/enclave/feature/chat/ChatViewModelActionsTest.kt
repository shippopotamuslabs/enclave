package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeModerationReportRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
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
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.feature.chat.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
    /**
     * Per-model-type [ActiveModelProviderPort] fake: [configurationsByType] wins over the
     * fallback [configuration], mirroring the per-type MockK stubs this replaces (ENC-28).
     */
    private class PerTypeActiveModelProvider : ActiveModelProviderPort {
        var configuration: ActiveModelConfiguration? = null
        val configurationsByType = mutableMapOf<ModelType, ActiveModelConfiguration?>()
        var overrideConfiguration: ActiveModelConfiguration? = null
        val requestedModelTypes = mutableListOf<ModelType>()

        override suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration? {
            requestedModelTypes += modelType
            return if (configurationsByType.containsKey(modelType)) {
                configurationsByType[modelType]
            } else {
                configuration
            }
        }

        override suspend fun getActiveConfigurationByOverride(override: ChatModelOverride): ActiveModelConfiguration? =
            overrideConfiguration
    }

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
    private val activeModelProvider = PerTypeActiveModelProvider()
    private val shareChatUseCase = FakeShareChatUseCase()
    private val chatRepository = FakeChatRepository()
    private val exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase()

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
        activeModelProvider.configuration = visionConfig
        activeModelProvider.overrideConfiguration = null

        defaultModelsFlow = MutableStateFlow(emptyList())
        settingsUseCases.assignments.getDefaultModels.flow = defaultModelsFlow

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
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
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
            chatViewModel.onImagesSelected(listOf("test-uri"))
            runCurrent()

            // Switch to COUNCIL mode - photo attachment should be disabled for crew without API vision
            activeModelProvider.configurationsByType[ModelType.VISION] = null
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
            chatUseCases.processFileAttachmentUseCase.result = metadata
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
            assertEquals(0, cancelInferenceUseCase.callCount)
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
            inferenceLockManager.isInferenceBlocked.value = true

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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
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
            activeModelProvider.configurationsByType[ModelType.VISION] = null

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
            activeModelProvider.configuration = null

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

            stageImageAttachmentUseCase.result = "staged://keep"
            chatViewModel.onImagesSelected(listOf("content://keep"))
            advanceUntilIdle()
            stageImageAttachmentUseCase.result = "staged://drop"
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
            activeModelProvider.configuration = null
            activeModelProvider.configurationsByType[ModelType.FAST] = null
            activeModelProvider.configurationsByType[ModelType.THINKING] = localThinking
            activeModelProvider.configurationsByType[ModelType.VISION] = apiVision

            val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
            runCurrent()

            stageImageAttachmentUseCase.result = "staged://one"
            chatViewModel.onImagesSelected(listOf("content://one"))
            advanceUntilIdle()
            stageImageAttachmentUseCase.result = "staged://two"
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
            val moderationReportRepository = FakeModerationReportRepository()
            val submitModerationReportUseCase = SubmitModerationReportUseCase(moderationReportRepository)
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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                    submitModerationReportUseCase = submitModerationReportUseCase,
                )

            customViewModel.moderationMessages.test {
                customViewModel.reportBlockedPrompt("Test blocked prompt")
                assertEquals(R.string.chat_moderation_feedback_submitted, awaitItem())
            }

            assertEquals(
                listOf("Test blocked prompt" to "blocked"),
                moderationReportRepository.submittedReports,
            )
        }

    @Test
    fun `reportResponse submits response text when message exists in uiState`() =
        runTest {
            val moderationReportRepository = FakeModerationReportRepository()
            val submitModerationReportUseCase = SubmitModerationReportUseCase(moderationReportRepository)
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
            val chatUseCasesWithMessage = FakeChatUseCases()
            chatUseCasesWithMessage.getChatUseCase.flow = flowOf(listOf(assistantMessage))
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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
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

            assertEquals(
                listOf("Harmful response that should be blocked" to "allowed"),
                moderationReportRepository.submittedReports,
            )
            collectJob.cancel()
        }

    @Test
    fun `reportResponse does nothing when message not found`() =
        runTest {
            val moderationReportRepository = FakeModerationReportRepository()
            val submitModerationReportUseCase = SubmitModerationReportUseCase(moderationReportRepository)
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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                    stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                    submitModerationReportUseCase = submitModerationReportUseCase,
                )

            customViewModel.reportResponse(MessageId("nonexistent"))

            assertTrue(moderationReportRepository.submittedReports.isEmpty())
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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
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
                    chatRepository = chatRepository,
                    exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
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

            assertTrue(settingsUseCases.assignments.setDefaultModel.calls.isEmpty())
            collectJob.cancel()
        }
}
