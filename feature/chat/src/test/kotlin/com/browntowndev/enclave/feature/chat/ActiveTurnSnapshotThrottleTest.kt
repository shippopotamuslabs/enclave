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
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.coroutines.CoroutineContext

/**
 * Verifies that [ChatViewModel.uiState] emits at a throttled rate when
 * [ActiveChatTurnSnapshotPort.observe] fires many tokens in rapid succession.
 *
 * RED before Phase 1 impl (no throttle → ~100 snapshot-driven emissions).
 * GREEN after adding `.throttleLatest(16)` to activeTurnSnapshotFlow.
 */
@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveTurnSnapshotThrottleTest {
    private val chatId = ChatId("throttle-test-chat")
    private val assistantMsgId = MessageId("assistant-msg-1")
    private val key = ActiveChatTurnKey(chatId, assistantMsgId)

    private val chatUseCases: ChatUseCases = mockk(relaxed = true)
    private val settingsUseCases: SettingsUseCases = mockk(relaxed = true)
    private val inferenceLockManager: InferenceLockManager = mockk(relaxed = true)
    private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort = mockk(relaxed = true)
    private val toolExecutionEventPort: ToolExecutionEventPort = mockk(relaxed = true)
    private val errorHandler: ViewModelErrorHandler = mockk(relaxed = true)
    private val studioRepository: StudioRepositoryPort = mockk(relaxed = true)
    private val activeModelProvider: ActiveModelProviderPort = mockk(relaxed = true)

    private fun buildViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): ChatViewModel {
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns kotlinx.coroutines.CoroutineExceptionHandler { _: CoroutineContext, _ -> }

        val modelDisplayNamesUseCase = mockk<GetModelDisplayNameUseCase>(relaxed = true)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null

        return ChatViewModelTestSupport.createViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            savedStateHandle = savedStateHandle,
            cancelInferenceUseCase = mockk(relaxed = true),
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            toolExecutionEventPort = toolExecutionEventPort,
            errorHandler = errorHandler,
            loggingPort = mockk(relaxed = true),
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
            playbackController = mockk(relaxed = true),
            studioRepository = studioRepository,
            shareChatUseCase = mockk(relaxed = true),
            chatRepository = mockk(relaxed = true),
            exportArtifactDocumentsUseCase = mockk(relaxed = true),
            stageImageAttachmentUseCase = mockk(relaxed = true),
        )
    }

    private fun snapshot(content: String) =
        AccumulatedMessages(
            messages =
                mapOf(
                    assistantMsgId to
                        MessageSnapshot(
                            messageId = assistantMsgId,
                            modelType = ModelType.MAIN,
                            content = content,
                            thinkingRaw = "",
                            isComplete = false,
                        ),
                ),
        )

    /**
     * Without throttle: 100 rapid snapshot emissions → ~100 distinct ChatUiState values →
     * ~100 uiState collector calls.
     *
     * With `.throttleLatest(16)`: at most one emission per 16ms window over 100ms →
     * ≤ 8 emissions (100ms/16ms ≈ 6 windows + 1 immediate + 1 final flush).
     */
    @Test
    fun `uiState emission count is bounded when 100 snapshots arrive over 100ms virtual time`() =
        runTest {
            // DB has one incomplete assistant message so activeTurnKeyFlow resolves the key.
            coEvery { chatUseCases.getChat(chatId) } returns
                MutableStateFlow(
                    listOf(
                        Message(
                            id = assistantMsgId,
                            chatId = chatId,
                            content = Content(""),
                            role = Role.ASSISTANT,
                            messageState = MessageState.PROCESSING,
                        ),
                    ),
                )

            // Snapshot port emits 100 distinct snapshots, each 1ms apart (100ms total span).
            every { activeChatTurnSnapshotPort.observe(key) } returns
                flow {
                    repeat(100) { i ->
                        emit(snapshot("Token $i"))
                        delay(1L)
                    }
                }

            val vm = buildViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)))

            var totalEmissions = 0
            val collectJob =
                launch {
                    vm.uiState.collect { totalEmissions++ }
                }

            advanceUntilIdle() // propagate initial state + first snapshot
            advanceTimeBy(100L) // process all 100 snapshot emissions + throttle windows
            advanceUntilIdle() // flush any remaining buffered value

            collectJob.cancel()

            // Without throttle: ≥ 100 snapshot-driven uiState emissions.
            // With throttleLatest(16ms): ≤ 15 total emissions including initial + DB load + throttled snapshots.
            assertTrue(
                totalEmissions <= 15,
                "Expected ≤15 total uiState emissions with 16ms throttle, got $totalEmissions",
            )
        }
}
