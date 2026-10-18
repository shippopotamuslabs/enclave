package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("InferenceOrchestrator navigation detach")
class InferenceOrchestratorNavigationDetachTest {
    private val testDispatcher = StandardTestDispatcher()

    @RegisterExtension
    @JvmField
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var chatUseCases: FakeChatUseCases
    private lateinit var cancelInferenceUseCase: FakeCancelInferenceUseCase
    private lateinit var orchestrator: InferenceOrchestrator
    private lateinit var applicationScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        chatUseCases = FakeChatUseCases()
        cancelInferenceUseCase = FakeCancelInferenceUseCase()
        applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)

        val settingsUseCases = FakeChatSettingsUseCases()

        val errorHandler = FakeViewModelErrorHandler()

        orchestrator =
            InferenceOrchestrator(
                chatUseCases = chatUseCases,
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = cancelInferenceUseCase,
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                chatRepository = FakeChatRepository(),
                errorHandler = errorHandler,
                savedStateHandle = SavedStateHandle(),
                pipelineExecutor = FakePipelineExecutorPort(),
                moderatePromptUseCase = FakeModeratePromptUseCase(),
                loggingPort = FakeLoggingPort(),
                applicationScope = applicationScope,
            )

        mockSubmitPrompt(
            chatUseCases = chatUseCases,
            userMessageId = MessageId("user-nav-1"),
            chatId = ChatId("chat-nav-1"),
        )
    }

    @Test
    @DisplayName("SC-NAV-01 — detachOnViewModelCleared leaves active inference running")
    fun detachOnViewModelClearedLeavesActiveInferenceRunning() =
        runTest(testDispatcher) {
            val generationStarted = CompletableDeferred<Unit>()
            val generationCancelled = CompletableDeferred<Unit>()

            chatUseCases.generateChatResponseUseCase.flow =
                flow {
                    generationStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        generationCancelled.complete(Unit)
                    }
                }

            val viewModelScope = CoroutineScope(SupervisorJob() + testDispatcher)
            launch {
                orchestrator.sendMessage(
                    scope = viewModelScope,
                    input = "Hello",
                    responseMode = ChatResponseMode.Thinking,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()
            generationStarted.await()

            assertEquals(1, cancelInferenceUseCase.callCount)

            orchestrator.detachOnViewModelCleared()
            advanceUntilIdle()

            assertEquals(1, cancelInferenceUseCase.callCount)
            assertFalse(
                generationCancelled.isCompleted,
                "Inference must survive ChatViewModel clear / navigation detach",
            )

            orchestrator.stopInference(scope = this)
            advanceUntilIdle()
        }
}
