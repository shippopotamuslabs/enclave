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
import com.browntowndev.pocketcrew.domain.usecase.chat.PlayTtsAudioUseCase
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.coroutines.CoroutineContext

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModelTest {

    private val settingsUseCases: SettingsUseCases = mockk()
    private val chatUseCases: ChatUseCases = mockk()
    private val stageImageAttachmentUseCase: StageImageAttachmentUseCase = mockk(relaxed = true)
    private val cancelInferenceUseCase: CancelInferenceUseCase = mockk(relaxed = true)
    private val inferenceLockManager: InferenceLockManager = mockk()
    private val toolExecutionEventPort: ToolExecutionEventPort = mockk()
    private var modelDisplayNamesUseCase: GetModelDisplayNameUseCase = mockk()
    private val errorHandler: ViewModelErrorHandler = mockk()
    private val loggingPort: LoggingPort = mockk(relaxed = true)
    private val activeChatTurnStore: ActiveChatTurnSnapshotPort = mockk(relaxed = true)
    private val playTtsAudioUseCase: PlayTtsAudioUseCase = mockk(relaxed = true)
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
            playTtsAudioUseCase = playTtsAudioUseCase,
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
    fun `onPlayTts launches use case`() = runTest {
        val text = "Hello"
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        defaultModelsFlow.value = listOf(
            DefaultModelAssignment(
                modelType = ModelType.TTS,
                ttsProviderId = TtsProviderId("tts-provider"),
            ),
        )
        runCurrent()
        coEvery { playTtsAudioUseCase(text) } returns Result.success(Unit)

        chatViewModel.onPlayTts(text)
        runCurrent()

        coVerify { playTtsAudioUseCase(text) }
        collectJob.cancel()
    }

    @Test
    fun `onPlayTts does not launch use case when TTS is unassigned`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        runCurrent()

        chatViewModel.onPlayTts("Hello")

        coVerify(exactly = 0) { playTtsAudioUseCase(any()) }
        collectJob.cancel()
    }
}
