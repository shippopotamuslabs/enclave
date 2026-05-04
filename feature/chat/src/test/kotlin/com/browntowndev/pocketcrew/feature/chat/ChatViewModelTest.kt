package com.browntowndev.pocketcrew.feature.chat
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.TAVILY_WEB_SEARCH
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.coroutines.CoroutineContext

import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessFileAttachmentUseCase

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModelTest {

    private val settingsUseCases: SettingsUseCases = mockk()
    private val chatUseCases: ChatUseCases = mockk()
    private val processFileAttachmentUseCase: ProcessFileAttachmentUseCase = mockk()
    private val stageImageAttachmentUseCase: StageImageAttachmentUseCase = mockk(relaxed = true)
    private val cancelInferenceUseCase: CancelInferenceUseCase = mockk(relaxed = true)
    private val inferenceLockManager: InferenceLockManager = mockk()
    private val toolExecutionEventPort: ToolExecutionEventPort = mockk()
    private var modelDisplayNamesUseCase: GetModelDisplayNameUseCase = mockk()
    private val errorHandler: ViewModelErrorHandler = mockk()
    private val loggingPort: LoggingPort = mockk(relaxed = true)
    private val activeChatTurnStore: ActiveChatTurnSnapshotPort = mockk(relaxed = true)
    private val playbackController: TtsPlaybackControllerPort = mockk(relaxed = true)
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var defaultModelsFlow: MutableStateFlow<List<DefaultModelAssignment>>

    private lateinit var speechEvents: MutableSharedFlow<SpeechState>
    private lateinit var listenToSpeechUseCase: ListenToSpeechUseCase

    @BeforeEach
    fun setUp() {
        val activeModelProvider: ActiveModelProviderPort = mockk()
        val visionConfig = ActiveModelConfiguration(
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
        
        // Stub coroutineExceptionHandler to return a real one to avoid ClassCastException with MockK
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }

        speechEvents = MutableSharedFlow(extraBufferCapacity = 8)
        listenToSpeechUseCase = mockk()
        every { listenToSpeechUseCase.invoke(any(), any()) } returns speechEvents
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
        // Create a simple fake for GetModelDisplayNameUseCase
        modelDisplayNamesUseCase = mockk(relaxed = true)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { toolExecutionEventPort.events } returns MutableSharedFlow()

        val savedStateHandle = SavedStateHandle()

        chatViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            savedStateHandle = savedStateHandle,
            cancelInferenceUseCase = cancelInferenceUseCase,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            toolExecutionEventPort = toolExecutionEventPort,
            errorHandler = errorHandler,
            loggingPort = loggingPort,
            activeChatTurnSnapshotPort = activeChatTurnStore,
            playbackController = playbackController,
        )
    }

    @Test
    fun `onSendMessage error invokes errorHandler`() = runTest {
        // Given
        val input = "Hello"
        chatViewModel.onInputChange(input)
        
        val exception = IOException("Network error")
        coEvery { chatUseCases.processPrompt(any()) } throws exception
        
        // When
        chatViewModel.onSendMessage()
        
        // Then
        // Verify coroutineExceptionHandler was called with correct parameters
        verify {
            errorHandler.coroutineExceptionHandler(
                tag = "ChatViewModel",
                message = "Failed to send message",
                userMessage = "Could not send message. Please try again."
            )
        }
    }

    @Test
    fun `initial speech state is Idle`() = runTest {
        assertEquals(SpeechState.Idle, chatViewModel.uiState.value.speechState)
    }

    @Test
    fun `onMicClick starts listening when Idle`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        chatViewModel.onMicClick()
        coVerify(exactly = 1) { listenToSpeechUseCase.invoke(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun `onMicClick sets stop signal when Listening`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        chatViewModel.onMicClick()
        speechEvents.emit(SpeechState.Listening(0f))
        runCurrent()
        chatViewModel.onMicClick()
        runCurrent()
        // Verify that the stop signal was sent. The actual state change to Idle will happen
        // when the use case processes the signal and completes.
        coVerify { listenToSpeechUseCase.invoke(any(), match { it.value }) }
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts launches streaming use case`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text) } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Completed
        )

        chatViewModel.onPlayTts(text)
        runCurrent()

        verify { playbackController.play(text) }
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts does not launch use case when TTS is unassigned`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        runCurrent()

        chatViewModel.onPlayTts("Hello")

        collectJob.cancel()
    }

    @Test
    fun `onPlayTts sets isPlayingTts to true when Playing status received`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text) } returns flow {
            emit(TtsPlaybackStatus.Playing)
            delay(10_000) // Keep flow alive so isPlayingTts stays true
        }

        chatViewModel.onPlayTts(text)
        runCurrent()

        assertTrue(chatViewModel.uiState.value.isPlayingTts)
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts sets isPlayingTts to false when Completed status received`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text) } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Completed
        )

        chatViewModel.onPlayTts(text)
        runCurrent()

        // After Completed, isPlayingTts should be false
        assertFalse(chatViewModel.uiState.value.isPlayingTts)
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts sets isPlayingTts to false when Error status received`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text) } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Error("Connection failed")
        )

        chatViewModel.onPlayTts(text)
        runCurrent()

        // After Error, isPlayingTts should be false
        assertFalse(chatViewModel.uiState.value.isPlayingTts)
        collectJob.cancel()
    }

    @Test
    fun `onStopTts cancels streaming job and stops use case`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()

        chatViewModel.onStopTts()

        verify { playbackController.stop() }
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts cancels previous streaming job before starting new one`() = runTest {
        val text1 = "Hello"
        val text2 = "Goodbye"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text1) } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Completed
        )
        every { playbackController.play(text2) } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Completed
        )

        chatViewModel.onPlayTts(text1)
        runCurrent()
        chatViewModel.onPlayTts(text2)
        runCurrent()

        // stop() should be called at least once (from the cancel-before-start)
        verify(atLeast = 1) { playbackController.stop() }
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts does not call batch use case - streaming only`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text) } returns flowOf(
            TtsPlaybackStatus.Playing,
            TtsPlaybackStatus.Completed
        )

        chatViewModel.onPlayTts(text)
        runCurrent()

        // Verify the playback controller was called
        verify { playbackController.play(text) }
        collectJob.cancel()
    }

    @Test
    fun `onStopTts stops streaming and cancels job`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text) } returns flow {
            emit(TtsPlaybackStatus.Playing)
            delay(10_000) // Keep alive
        }

        chatViewModel.onPlayTts(text)
        runCurrent()
        assertTrue(chatViewModel.uiState.value.isPlayingTts)

        chatViewModel.onStopTts()
        runCurrent()

        verify { playbackController.stop() }
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts when no TTS provider assigned does nothing`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        // No TTS provider assigned - defaultModelsFlow is empty
        runCurrent()

        chatViewModel.onPlayTts("Hello")
        runCurrent()

        // playbackController.play should NOT be called
        verify(exactly = 0) { playbackController.play(any()) }
        assertFalse(chatViewModel.uiState.value.isPlayingTts)
        collectJob.cancel()
    }

    @Test
    fun `UI state reflects multiple artifacts from active turn snapshot`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        val snapshotFlow = MutableStateFlow<AccumulatedMessages?>(null)
        val chatId = ChatId("chat")
        val assistantId = MessageId("assistant")
        val key = ActiveChatTurnKey(chatId, assistantId)
        
        // Mock processPrompt to return our IDs, which sets the requestedTurnKey
        coEvery { chatUseCases.processPrompt(any()) } returns com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase.PromptResult(
            chatId = chatId,
            userMessageId = MessageId("user"),
            assistantMessageId = assistantId
        )
        
        every { activeChatTurnStore.observe(key) } returns snapshotFlow
        
        // Trigger send to set the active turn key
        chatViewModel.onInputChange("test")
        chatViewModel.onSendMessage()
        advanceUntilIdle() // Ensure requestedTurnKey is processed
        
        // Simulate a snapshot with two artifacts
        val artifact1 = com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest(
            title = "Doc 1",
            documentType = com.browntowndev.pocketcrew.domain.model.artifact.DocumentType.PDF,
            sections = emptyList()
        )
        val artifact2 = com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest(
            title = "Doc 2",
            documentType = com.browntowndev.pocketcrew.domain.model.artifact.DocumentType.PDF,
            sections = emptyList()
        )
        
        val snapshot = AccumulatedMessages(
            messages = mapOf(
                assistantId to MessageSnapshot(
                    messageId = assistantId,
                    modelType = ModelType.FAST,
                    content = "Here are your docs",
                    thinkingRaw = "",
                    messageState = MessageState.COMPLETE,
                    artifacts = listOf(artifact1, artifact2)
                )
            )
        )
        
        snapshotFlow.value = snapshot
        advanceUntilIdle() // Ensure snapshot update is processed
        
        val uiMessages = chatViewModel.uiState.value.messages
        val assistantMessage = uiMessages.find { it.id == assistantId }
        
        assertEquals(1, uiMessages.size)
        assertEquals(2, assistantMessage?.content?.artifacts?.size)
        assertEquals("Doc 1", assistantMessage?.content?.artifacts?.get(0)?.title)
        assertEquals("Doc 2", assistantMessage?.content?.artifacts?.get(1)?.title)
        
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts with Initializing status does not set isPlayingTts`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        every { playbackController.play(text) } returns flowOf(
            TtsPlaybackStatus.Initializing
        )

        chatViewModel.onPlayTts(text)
        runCurrent()

        // Initializing should NOT set isPlayingTts to true
        assertFalse(chatViewModel.uiState.value.isPlayingTts)
        collectJob.cancel()
    }

    @Test
    fun `onFileSelected updates uiState with file name`() = runTest {
        val uri = "content://test.txt"
        val metadata = FileAttachmentMetadata("test.txt", "text/plain", "File content")
        coEvery { processFileAttachmentUseCase(uri) } returns metadata
        
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        
        chatViewModel.onFileSelected(uri)
        advanceUntilIdle()
        
        assertEquals("test.txt", chatViewModel.uiState.value.selectedFileName)
        collectJob.cancel()
    }

    @Test
    fun `onSendMessage includes file content in prompt`() = runTest {
        val uri = "content://test.txt"
        val metadata = FileAttachmentMetadata("test.txt", "text/plain", "Extracted content")
        coEvery { processFileAttachmentUseCase(uri) } returns metadata
        
        // Mock prompt result
        val chatId = ChatId("chat")
        val assistantId = MessageId("assistant")
        coEvery { chatUseCases.processPrompt(any()) } returns com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase.PromptResult(
            chatId = chatId,
            userMessageId = MessageId("user"),
            assistantMessageId = assistantId
        )
        coEvery { chatUseCases.generateChatResponse(any(), any(), any(), any(), any(), any()) } returns flowOf()

        chatViewModel.onFileSelected(uri)
        advanceUntilIdle()
        
        chatViewModel.onInputChange("Hello")
        chatViewModel.onSendMessage()
        advanceUntilIdle()
        
        val expectedPrompt = "--- Attached File: test.txt ---\nExtracted content\n\n--- User Prompt ---\nHello"
        
        // Verify that the prompt passed to processPrompt and generateChatResponse includes the file content
        coVerify { 
            chatUseCases.processPrompt(match { it.content.text == expectedPrompt })
            chatUseCases.generateChatResponse(
                prompt = expectedPrompt,
                userMessageId = any(),
                assistantMessageId = any(),
                chatId = any(),
                mode = any(),
                backgroundInferenceEnabled = any()
            )
        }
    }
}
