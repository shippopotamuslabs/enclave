package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.chat.AssistantPlaceholderResult
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceOrchestratorSendTest {
    private val testDispatcher = StandardTestDispatcher()

    @RegisterExtension
    @JvmField
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var chatUseCases: ChatUseCases
    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var orchestrator: InferenceOrchestrator
    private lateinit var errorHandler: RecordingErrorHandler

    @BeforeEach
    fun setUp() {
        errorHandler = RecordingErrorHandler()
        chatUseCases = mockk(relaxed = true)
        settingsUseCases = mockk()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())

        orchestrator =
            InferenceOrchestrator(
                chatUseCases = chatUseCases,
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = mockk(relaxed = true),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                chatRepository = mockk(relaxed = true),
                errorHandler = errorHandler,
                savedStateHandle = SavedStateHandle(),
            )
    }

    @Test
    @DisplayName("OSDA-O-01 — optimistic StateFlow set before processPrompt")
    fun osdaO01_optimisticBeforePersist() =
        runTest(testDispatcher) {
            val processPromptGate = CompletableDeferred<Unit>()
            coEvery { chatUseCases.processPrompt(any()) } coAnswers {
                processPromptGate.await()
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            }

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    selectedMode = ChatModeUi.FAST,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    onInputCleared = {},
                )
            }
            runCurrent()

            assertEquals("Hi", orchestrator.optimisticUserMessage.value?.content?.text)
            assertFalse(processPromptGate.isCompleted)
            processPromptGate.complete(Unit)
        }

    @Test
    @DisplayName("OSDA-O-02 — user persist completes before assistant placeholder")
    fun osdaO02_persistOrdering() =
        runTest(testDispatcher) {
            val callOrder = mutableListOf<String>()
            coEvery { chatUseCases.processPrompt(any()) } coAnswers {
                callOrder.add("processPrompt")
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            }
            coEvery {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            } coAnswers {
                callOrder.add("createAssistantPlaceholder")
                AssistantPlaceholderResult(assistantMessageId = MessageId("asst-orch-99"))
            }
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    mode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } coAnswers {
                callOrder.add("generateChatResponse")
                flowOf()
            }

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    selectedMode = ChatModeUi.FAST,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    onInputCleared = {},
                )
            }
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            assertEquals(
                listOf("processPrompt", "createAssistantPlaceholder", "generateChatResponse"),
                callOrder,
            )
        }

    @Test
    @DisplayName("OSDA-O-03 — assistant persist delayed by ChatTurnTiming")
    fun osdaO03_assistantPersistDelayed() =
        runTest(testDispatcher) {
            coEvery { chatUseCases.processPrompt(any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-1"),
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            coEvery {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            } returns AssistantPlaceholderResult(assistantMessageId = MessageId("asst-1"))
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    mode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    selectedMode = ChatModeUi.FAST,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    onInputCleared = {},
                )
            }
            runCurrent()

            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS - 1)
            runCurrent()
            coVerify(exactly = 0) {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            }

            advanceTimeBy(1)
            runCurrent()
            coVerify(exactly = 1) {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            }
        }

    @Test
    @DisplayName("OSDA-O-04 — failed user persist clears optimistic")
    fun osdaO04_processPromptFailureClearsOptimistic() =
        runTest(testDispatcher) {
            val exception = IOException("persist failed")
            coEvery { chatUseCases.processPrompt(any()) } coAnswers {
                throw exception
            }

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    selectedMode = ChatModeUi.FAST,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    onInputCleared = {},
                )
            }
            advanceUntilIdle()

            assertNull(orchestrator.optimisticUserMessage.value)
            assertSame(exception, errorHandler.handledErrors.single())
            coVerify(exactly = 0) {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            }
            coVerify(exactly = 0) {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    mode = any(),
                    backgroundInferenceEnabled = any(),
                )
            }
        }

    private class RecordingErrorHandler : ViewModelErrorHandler {
        private val _errorEvents = MutableSharedFlow<String>()
        override val errorEvents: SharedFlow<String> = _errorEvents
        val handledErrors = mutableListOf<Throwable>()

        override fun handleError(
            tag: String,
            message: String,
            throwable: Throwable,
            userMessage: String,
        ) {
            handledErrors += throwable
        }

        override fun coroutineExceptionHandler(
            tag: String,
            message: String,
            userMessage: String,
        ): CoroutineExceptionHandler =
            CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
                handleError(tag, message, throwable, userMessage)
            }
    }

    @Test
    @DisplayName("OSDA-O-05 — generateChatResponse receives orchestrator assistant id")
    fun osdaO05_generationUsesPreassignedIds() =
        runTest(testDispatcher) {
            coEvery { chatUseCases.processPrompt(any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("user-orch-1"),
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            coEvery {
                chatUseCases.createAssistantPlaceholder(
                    chatId = any(),
                    userMessageCreatedAtMillis = any(),
                    assistantMessageId = any(),
                )
            } returns AssistantPlaceholderResult(assistantMessageId = MessageId("unused"))
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    mode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    selectedMode = ChatModeUi.FAST,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    onInputCleared = {},
                )
            }
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            val orchestratorAssistantId =
                checkNotNull(orchestrator.requestedTurnKey.value?.assistantMessageId) {
                    "Orchestrator must assign assistant id before generation"
                }
            coVerify {
                chatUseCases.generateChatResponse(
                    prompt = "Hi",
                    userMessageId = MessageId("user-orch-1"),
                    assistantMessageId = orchestratorAssistantId,
                    chatId = ChatId("chat-1"),
                    mode = Mode.FAST,
                    backgroundInferenceEnabled = any(),
                )
            }
        }

    @Test
    @DisplayName("OSDA-M-03 — immediate assistant persist fails delay assertion")
    fun osdaM03_zeroDelayGuard() {
        assertEquals(650L, ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
        assertNotEquals(0L, ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
    }
}
