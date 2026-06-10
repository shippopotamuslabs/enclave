package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("InferenceOrchestrator navigation detach")
class InferenceOrchestratorNavigationDetachTest {
    private val testDispatcher = StandardTestDispatcher()

    @RegisterExtension
    @JvmField
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var chatUseCases: ChatUseCases
    private lateinit var cancelInferenceUseCase: CancelInferenceUseCase
    private lateinit var orchestrator: InferenceOrchestrator
    private lateinit var applicationScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        chatUseCases = mockk(relaxed = true)
        cancelInferenceUseCase = mockk(relaxed = true)
        applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)

        val settingsUseCases = mockk<SettingsUseCases>()
        every { settingsUseCases.getSettings() } returns kotlinx.coroutines.flow.MutableStateFlow(SettingsData())

        val errorHandler = mockk<ViewModelErrorHandler>()
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
            CoroutineExceptionHandler { _: CoroutineContext, _ -> }

        orchestrator =
            InferenceOrchestrator(
                chatUseCases = chatUseCases,
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = cancelInferenceUseCase,
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                chatRepository = mockk(relaxed = true),
                errorHandler = errorHandler,
                savedStateHandle = SavedStateHandle(),
                pipelineExecutor = mockk(relaxed = true),
                applicationScope = applicationScope,
            )

        mockAllowedSafetyGate(
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

            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    mode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns
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
                    selectedMode = ChatModeUi.THINKING,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerate = true,
                    onInputCleared = {},
                    onModerationBlocked = {},
                )
            }
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()
            generationStarted.await()

            verify(exactly = 1) { cancelInferenceUseCase() }

            orchestrator.detachOnViewModelCleared()
            advanceUntilIdle()

            verify(exactly = 1) { cancelInferenceUseCase() }
            assertFalse(
                generationCancelled.isCompleted,
                "Inference must survive ChatViewModel clear / navigation detach",
            )

            orchestrator.stopInference(scope = this)
            advanceUntilIdle()
        }
}