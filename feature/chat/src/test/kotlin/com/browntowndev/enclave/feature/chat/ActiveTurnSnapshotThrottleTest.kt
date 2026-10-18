package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

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

    private val chatUseCases = FakeChatUseCases()
    private val settingsUseCases = FakeChatSettingsUseCases()
    private val inferenceLockManager = FakeInferenceLockManager()
    private val activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort()
    private val toolExecutionEventPort = FakeToolExecutionEventPort()
    private val errorHandler = FakeViewModelErrorHandler()
    private val studioRepository = FakeStudioRepository()
    private val activeModelProvider = FakeActiveModelProvider()

    private fun buildViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): ChatViewModel {
        val modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase()

        return ChatViewModelTestSupport.createViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            savedStateHandle = savedStateHandle,
            cancelInferenceUseCase = FakeCancelInferenceUseCase(),
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            toolExecutionEventPort = toolExecutionEventPort,
            errorHandler = errorHandler,
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
            playbackController = FakeTtsPlaybackController(),
            studioRepository = studioRepository,
            shareChatUseCase = FakeShareChatUseCase(),
            chatRepository = FakeChatRepository(),
            exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
            stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
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
            chatUseCases.getChatUseCase.flow =
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

            activeChatTurnSnapshotPort.observeFlowOverride =
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
