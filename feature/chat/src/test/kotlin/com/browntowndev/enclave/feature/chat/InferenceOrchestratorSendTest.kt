package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
    private lateinit var applicationScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)
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
                pipelineExecutor = mockk(relaxed = true),
                moderatePromptUseCase = mockk(relaxed = true),
                loggingPort = mockk(relaxed = true),
                applicationScope = applicationScope,
            )
    }

    @Test
    @DisplayName("DOPG-O-01 — optimistic StateFlow set before submit returns")
    fun dopgO01_optimisticBeforePersist() =
        runTest(testDispatcher) {
            val submitPrompt = CompletableDeferred<Unit>()
            coEvery { chatUseCases.submitPrompt(any()) } coAnswers {
                submitPrompt.await()
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
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            runCurrent()

            assertEquals(
                "Hi",
                orchestrator.optimisticUserMessage.value
                    ?.content
                    ?.text,
            )
            assertFalse(submitPrompt.isCompleted)
            submitPrompt.complete(Unit)
        }

    @Test
    @DisplayName("DOPG-O-02 — generateChatResponse runs after 650ms delay once submit returns")
    fun dopgO02_generationAfterDelay() =
        runTest(testDispatcher) {
            mockSubmitPrompt(chatUseCases)
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            advanceTimeBy(649)
            runCurrent()
            coVerify(exactly = 0) {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            }
            advanceTimeBy(1)
            advanceUntilIdle()
            coVerify(exactly = 1) {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            }
        }

    @Test
    @DisplayName("DOPG-O-03 — placeholder is inside submit, not delayed by ChatTurnTiming in orchestrator")
    fun dopgO03_placeholderInsideSubmitNotOrchestrator() =
        runTest(testDispatcher) {
            mockSubmitPrompt(chatUseCases)
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            runCurrent()

            coVerify(exactly = 1) { chatUseCases.submitPrompt(any()) }
            coVerify(exactly = 0) {
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
            mockSubmitPromptThrows(chatUseCases, exception)

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
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
                    responseMode = any(),
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
            mockSubmitPrompt(
                chatUseCases = chatUseCases,
                userMessageId = MessageId("user-orch-1"),
                chatId = ChatId("chat-1"),
            )
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hi",
                    responseMode = ChatResponseMode.Fast,
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
                    responseMode = ChatResponseMode.Fast,
                    backgroundInferenceEnabled = any(),
                )
            }
        }

    @Test
    @DisplayName("DOPG-O-04 — post-turn response moderation blocks NSFW output")
    fun dopgO04_postTurnResponseModerationBlocksNsfw() =
        runTest(testDispatcher) {
            val moderatePromptUseCase =
                mockk<com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase>(relaxed = true)
            coEvery { moderatePromptUseCase(any()) } returns
                com.browntowndev.enclave.domain.usecase.inference.PromptModerationResult(
                    blocked = true,
                    scoresByLabel = mapOf("nsfw" to 0.95),
                    canonicalScores = mapOf("nsfw" to 0.95),
                    triggeredLabels = listOf("nsfw"),
                    combinedIllegalScore = 0.95,
                )

            val orchestratorWithModeration =
                InferenceOrchestrator(
                    chatUseCases = chatUseCases,
                    settingsUseCases = settingsUseCases,
                    cancelInferenceUseCase = mockk(relaxed = true),
                    activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                    chatRepository = mockk(relaxed = true),
                    errorHandler = errorHandler,
                    savedStateHandle = SavedStateHandle(),
                    pipelineExecutor = mockk(relaxed = true),
                    moderatePromptUseCase = moderatePromptUseCase,
                    loggingPort = mockk(relaxed = true),
                    applicationScope = applicationScope,
                )

            mockSubmitPrompt(chatUseCases)
            val responseBlocked = CompletableDeferred<Unit>()
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns
                kotlinx.coroutines.flow.flow {
                    emit(
                        com.browntowndev.enclave.domain.model.chat.AccumulatedMessages(
                            messages =
                                mapOf(
                                    MessageId("asst-1") to
                                        com.browntowndev.enclave.domain.model.chat.MessageSnapshot(
                                            messageId = MessageId("asst-1"),
                                            modelType = com.browntowndev.enclave.domain.model.inference.ModelType.FAST,
                                            content = "Explicit NSFW response",
                                            thinkingRaw = "",
                                            thinkingDurationSeconds = 0L,
                                            thinkingStartTime = 0L,
                                            thinkingEndTime = 0L,
                                            messageState = com.browntowndev.enclave.domain.model.chat.MessageState.COMPLETE,
                                            isComplete = true,
                                            transcriptSegments = emptyList(),
                                        ),
                                ),
                        ),
                    )
                }

            launch {
                orchestratorWithModeration.sendMessage(
                    scope = this,
                    input = "Hi",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = { responseBlocked.complete(Unit) },
                )
            }
            advanceUntilIdle()

            coVerify { moderatePromptUseCase("Explicit NSFW response") }
        }

    @Test
    @DisplayName("DOPG-O-05 — orchestrator does not launch title; submit owns title generation")
    fun dopgO05_orchestratorDoesNotLaunchTitle() =
        runTest(testDispatcher) {
            coEvery { chatUseCases.submitPrompt(any()) } coAnswers {
                UserPromptResult(
                    userMessageId = MessageId("user-title-1"),
                    chatId = ChatId("chat-title-1"),
                    userMessageCreatedAtMillis = 100L,
                )
            }
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hello crew",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            advanceUntilIdle()

            coEvery { chatUseCases.submitPrompt(any()) } coAnswers {
                UserPromptResult(
                    userMessageId = MessageId("user-title-2"),
                    chatId = ChatId("chat-title-2"),
                    userMessageCreatedAtMillis = 100L,
                )
            }

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "   ",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            advanceUntilIdle()

            coEvery { chatUseCases.submitPrompt(any()) } coAnswers {
                UserPromptResult(
                    userMessageId = MessageId("user-title-3"),
                    chatId = ChatId("chat-existing-title"),
                    userMessageCreatedAtMillis = 100L,
                )
            }

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hello crew",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = ChatId("chat-existing-title"),
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            advanceUntilIdle()

            coVerify(exactly = 0) {
                chatUseCases.generateChatTitle(any(), any())
            }
        }

    @Test
    @DisplayName("OSDA-M-03 — immediate assistant persist fails delay assertion")
    fun osdaM03_zeroDelayGuard() {
        assertEquals(650L, ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
        assertNotEquals(0L, ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
    }

    @Test
    @DisplayName("DOPG-O-07 — rapid double-send only launches one inference")
    fun dopgO07_rapidDoubleSendLaunchesSingleInference() =
        runTest(testDispatcher) {
            val gateStarted = CompletableDeferred<Unit>()
            coEvery { chatUseCases.submitPrompt(any()) } coAnswers {
                gateStarted.await()
                UserPromptResult(
                    userMessageId = MessageId("user-double"),
                    chatId = ChatId("chat-double"),
                    userMessageCreatedAtMillis = 100L,
                )
            }
            coEvery {
                chatUseCases.generateChatResponse(
                    prompt = any(),
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            } returns flowOf()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "first",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            runCurrent()

            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "second",
                    responseMode = ChatResponseMode.Fast,
                    selectedImageUris = emptyList(),
                    fileAttachments = emptyList(),
                    initialChatId = null,
                    shouldModerateResponse = true,
                    onInputCleared = {},
                    onResponseBlocked = {},
                )
            }
            gateStarted.complete(Unit)
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                chatUseCases.generateChatResponse(
                    prompt = "second",
                    userMessageId = any(),
                    assistantMessageId = any(),
                    chatId = any(),
                    responseMode = any(),
                    backgroundInferenceEnabled = any(),
                )
            }
        }
}
