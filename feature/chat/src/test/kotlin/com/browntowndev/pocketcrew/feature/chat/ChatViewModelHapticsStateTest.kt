package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.share.ShareChatUseCase
import com.browntowndev.pocketcrew.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelHapticsStateTest {

    private lateinit var messagesFlow: MutableStateFlow<List<Message>>
    private lateinit var settingsFlow: MutableStateFlow<SettingsData>
    private lateinit var viewModel: ChatViewModel

    @BeforeEach
    fun setup() {
        messagesFlow = MutableStateFlow(emptyList())
        settingsFlow = MutableStateFlow(SettingsData(hapticResponse = false))

        val settingsUseCases = mockk<SettingsUseCases>(relaxed = true)
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        val chatUseCases = mockk<ChatUseCases>(relaxed = true)
        val inferenceLockManager = mockk<InferenceLockManager>(relaxed = true)
        val modelDisplayNamesUseCase = mockk<GetModelDisplayNameUseCase>(relaxed = true)
        val activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)

        every { settingsUseCases.getSettings() } returns settingsFlow
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
        coEvery { chatUseCases.getChat(any()) } returns messagesFlow
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null

        viewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = mockk<StageImageAttachmentUseCase>(relaxed = true),
            savedStateHandle = SavedStateHandle(mapOf("chatId" to "chat-1")),
            cancelInferenceUseCase = mockk<CancelInferenceUseCase>(relaxed = true),
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            errorHandler = mockk(relaxed = true),
            toolExecutionEventPort = mockk {
                every { events } returns MutableSharedFlow()
            },
            loggingPort = mockk(relaxed = true),
            activeChatTurnSnapshotPort = ActiveChatTurnStore(),
            playbackController = mockk(relaxed = true),
            studioRepository = mockk(relaxed = true),
            shareChatUseCase = mockk<ShareChatUseCase>(relaxed = true),
            chatRepository = mockk(relaxed = true),
            exportArtifactDocumentsUseCase = mockk(relaxed = true),
        )
    }

    @Test
    fun responseHapticPhase_engineLoading_isNone() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        messagesFlow.value = listOf(assistantMessage(MessageState.ENGINE_LOADING))
        advanceUntilIdle()

        assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
    }

    @Test
    fun responseHapticPhase_processing_isNone() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        messagesFlow.value = listOf(assistantMessage(MessageState.PROCESSING))
        advanceUntilIdle()

        assertEquals(ResponseHapticPhase.None, viewModel.uiState.value.responseHapticPhase)
    }

    @Test
    fun responseHapticPhase_thinking_isThinkingPulseAndPreservesPreference() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        messagesFlow.value = listOf(
            assistantMessage(
                state = MessageState.THINKING,
                thinkingRaw = "thinking",
                thinkingStartTime = 1L,
            ),
        )
        advanceUntilIdle()

        assertEquals(ResponseHapticPhase.ThinkingPulse, viewModel.uiState.value.responseHapticPhase)
        assertFalse(viewModel.uiState.value.hapticResponse)
    }

    @Test
    fun responseHapticPhase_generating_isGeneratingBuzz() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        messagesFlow.value = listOf(
            assistantMessage(
                state = MessageState.GENERATING,
                thinkingRaw = "done thinking",
                thinkingDurationSeconds = 3L,
                thinkingStartTime = 1L,
            ),
        )
        advanceUntilIdle()

        assertEquals(ResponseHapticPhase.GeneratingBuzz, viewModel.uiState.value.responseHapticPhase)
    }

    private fun assistantMessage(
        state: MessageState,
        thinkingRaw: String? = null,
        thinkingDurationSeconds: Long? = null,
        thinkingStartTime: Long? = null,
    ): Message =
        Message(
            id = MessageId("assistant-${state.name}"),
            chatId = ChatId("chat-1"),
            content = Content(text = "assistant text"),
            role = Role.ASSISTANT,
            messageState = state,
            createdAt = 1L,
            thinkingRaw = thinkingRaw,
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingStartTime = thinkingStartTime,
            modelType = ModelType.FAST,
        )
}
