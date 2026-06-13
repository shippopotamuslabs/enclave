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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.coroutines.CoroutineContext

/**
 * Verifies snapshot-driven [ChatViewModel.uiState] updates use [kotlinx.coroutines.flow.conflate]
 * so the UI keeps the latest snapshot when the reducer cannot keep pace with inference.
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

    @Test
    fun `uiState reflects the latest snapshot after a rapid burst`() =
        runTest {
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

            every { activeChatTurnSnapshotPort.observe(key) } returns
                flow {
                    repeat(100) { i ->
                        emit(snapshot("Token $i"))
                        delay(1L)
                    }
                }

            val vm = buildViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)))

            val collectJob =
                launch {
                    vm.uiState.collect { }
                }

            advanceUntilIdle()
            advanceTimeBy(100L)
            advanceUntilIdle()

            collectJob.cancel()

            val assistantMessage =
                vm.uiState.value.messages.firstOrNull { it.id == assistantMsgId }
            assertTrue(assistantMessage != null, "Expected assistant message in uiState")
            assertEquals(
                "Token 99",
                assistantMessage!!.content.text,
                "conflate must preserve the most recent snapshot content",
            )
        }
}