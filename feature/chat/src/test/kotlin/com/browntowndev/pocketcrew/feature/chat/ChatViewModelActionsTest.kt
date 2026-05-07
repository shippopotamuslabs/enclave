package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.share.ShareChatUseCase
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import io.mockk.coEvery
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
        val visionConfig = ActiveModelConfiguration(
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
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())

        chatViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
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
        )
    }

    @Test
    fun `onInputChange updates inputText in uiState`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        chatViewModel.onInputChange("Hello world")
        runCurrent()

        assertEquals("Hello world", chatViewModel.uiState.value.inputText)
        collectJob.cancel()
    }

    @Test
    fun `onEditMessage updates inputText`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        chatViewModel.onEditMessage("Edited message")
        runCurrent()

        assertEquals("Edited message", chatViewModel.uiState.value.inputText)
        collectJob.cancel()
    }

    @Test
    fun `onModeChange updates selectedMode`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        assertEquals(ChatModeUi.FAST, chatViewModel.uiState.value.selectedMode)

        chatViewModel.onModeChange(ChatModeUi.THINKING)
        runCurrent()

        assertEquals(ChatModeUi.THINKING, chatViewModel.uiState.value.selectedMode)
        collectJob.cancel()
    }

    @Test
    fun `onModeChange to CREW clears selected image when photo not enabled for crew`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        // First, set an image
        coEvery { stageImageAttachmentUseCase("test-uri") } returns "test-uri"
        chatViewModel.onImageSelected("test-uri")
        runCurrent()

        // Switch to CREW mode - photo attachment should be disabled for crew without API vision
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null
        chatViewModel.onModeChange(ChatModeUi.CREW)
        advanceUntilIdle()

        // Photo attachment should be disabled, so image should be cleared
        assertNull(chatViewModel.uiState.value.selectedImageUri)
        collectJob.cancel()
    }

    @Test
    fun `onImageSelected updates selectedImageUri when photo attachment enabled`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        coEvery { stageImageAttachmentUseCase("content://image") } returns "content://image"

        chatViewModel.onImageSelected("content://image")
        advanceUntilIdle()

        assertEquals("content://image", chatViewModel.uiState.value.selectedImageUri)
        collectJob.cancel()
    }

    @Test
    fun `onImageSelected with null clears selectedImageUri`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        chatViewModel.onImageSelected(null)
        runCurrent()

        assertNull(chatViewModel.uiState.value.selectedImageUri)
        collectJob.cancel()
    }

    @Test
    fun `clearSelectedImage clears selectedImageUri`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        coEvery { stageImageAttachmentUseCase("content://image") } returns "content://image"
        chatViewModel.onImageSelected("content://image")
        advanceUntilIdle()
        assertEquals("content://image", chatViewModel.uiState.value.selectedImageUri)

        chatViewModel.clearSelectedImage()
        runCurrent()
        assertNull(chatViewModel.uiState.value.selectedImageUri)
        collectJob.cancel()
    }

    @Test
    fun `clearSelectedFile clears fileName and fileText`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        val metadata = com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata("test.txt", "text/plain", "content")
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
    fun `stopGeneration does not cancel when not generating`() = runTest {
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
    fun `createNewChat resets state`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        chatViewModel.onInputChange("Some text")
        runCurrent()
        assertEquals("Some text", chatViewModel.uiState.value.inputText)

        chatViewModel.createNewChat()
        runCurrent()

        assertEquals("", chatViewModel.uiState.value.inputText)
        assertNull(chatViewModel.uiState.value.selectedImageUri)
        assertNull(chatViewModel.uiState.value.chatId)
        collectJob.cancel()
    }

    @Test
    fun `getShieldReason returns null when inference not blocked`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        assertNull(chatViewModel.getShieldReason())
        collectJob.cancel()
    }

    @Test
    fun `getShieldReason returns message when inference is blocked`() = runTest {
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(true)

        // Recreate VM with blocked inference
        chatViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
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
        )

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        assertEquals("Inference is currently blocked", chatViewModel.getShieldReason())
        collectJob.cancel()
    }

    @Test
    fun `initial state has FAST mode selected`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        assertEquals(ChatModeUi.FAST, chatViewModel.uiState.value.selectedMode)
        collectJob.cancel()
    }

    @Test
    fun `onFileSelected with null clears file state`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        chatViewModel.onFileSelected(null)
        runCurrent()

        assertNull(chatViewModel.uiState.value.selectedFileName)
        collectJob.cancel()
    }

    @Test
    fun `onInputChange with empty string clears input`() = runTest {
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
    fun `onModeChange from FAST to THINKING preserves photo attachment`() = runTest {
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
    fun `onModeChange to CREW disables photo when no API vision model`() = runTest {
        // No vision API model configured
        coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        chatViewModel.onModeChange(ChatModeUi.CREW)
        advanceUntilIdle()

        assertFalse(chatViewModel.uiState.value.isPhotoAttachmentEnabled)
        collectJob.cancel()
    }

    @Test
    fun `onImageSelected clears image when photo attachment is disabled`() = runTest {
        // No vision model configured
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        runCurrent()

        chatViewModel.onImageSelected("content://image")
        advanceUntilIdle()

        // Image should be cleared since photo attachment is disabled
        assertNull(chatViewModel.uiState.value.selectedImageUri)
        collectJob.cancel()
    }

    @Test
    fun `onShieldTap does not crash`() = runTest {
        // onShieldTap is a no-op stub, just verify it doesn't throw
        chatViewModel.onShieldTap()
    }

    @Test
    fun `onAttach does not crash`() = runTest {
        // onAttach is a no-op stub, just verify it doesn't throw
        chatViewModel.onAttach()
    }
}
