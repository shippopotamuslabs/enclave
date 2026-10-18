package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.domain.usecase.inference.PromptModerationResult
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
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

    private lateinit var chatUseCases: FakeChatUseCases
    private lateinit var settingsUseCases: FakeChatSettingsUseCases
    private lateinit var orchestrator: InferenceOrchestrator
    private lateinit var errorHandler: RecordingErrorHandler
    private lateinit var applicationScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)
        errorHandler = RecordingErrorHandler()
        chatUseCases = FakeChatUseCases()
        settingsUseCases = FakeChatSettingsUseCases()

        orchestrator =
            InferenceOrchestrator(
                chatUseCases = chatUseCases,
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                chatRepository = FakeChatRepository(),
                errorHandler = errorHandler,
                savedStateHandle = SavedStateHandle(),
                pipelineExecutor = FakePipelineExecutorPort(),
                moderatePromptUseCase = FakeModeratePromptUseCase(),
                loggingPort = FakeLoggingPort(),
                applicationScope = applicationScope,
            )
    }

    @Test
    @DisplayName("DOPG-O-01 — optimistic StateFlow set before submit returns")
    fun dopgO01_optimisticBeforePersist() =
        runTest(testDispatcher) {
            val submitPrompt = CompletableDeferred<Unit>()
            chatUseCases.submitPromptUseCase.behavior = {
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
            assertEquals(0, chatUseCases.generateChatResponseUseCase.calls.size)
            advanceTimeBy(1)
            advanceUntilIdle()
            assertEquals(1, chatUseCases.generateChatResponseUseCase.calls.size)
        }

    @Test
    @DisplayName("DOPG-O-03 — placeholder is inside submit, not delayed by ChatTurnTiming in orchestrator")
    fun dopgO03_placeholderInsideSubmitNotOrchestrator() =
        runTest(testDispatcher) {
            mockSubmitPrompt(chatUseCases)

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

            assertEquals(1, chatUseCases.submitPromptUseCase.calls.size)
            assertEquals(0, chatUseCases.createAssistantPlaceholderUseCase.calls.size)
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
            assertEquals(0, chatUseCases.createAssistantPlaceholderUseCase.calls.size)
            assertEquals(0, chatUseCases.generateChatResponseUseCase.calls.size)
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
            val generation = chatUseCases.generateChatResponseUseCase.calls.single()
            assertEquals("Hi", generation.prompt)
            assertEquals(MessageId("user-orch-1"), generation.userMessageId)
            assertEquals(orchestratorAssistantId, generation.assistantMessageId)
            assertEquals(ChatId("chat-1"), generation.chatId)
            assertEquals(ChatResponseMode.Fast, generation.responseMode)
        }

    @Test
    @DisplayName("DOPG-O-04 — post-turn response moderation blocks NSFW output")
    fun dopgO04_postTurnResponseModerationBlocksNsfw() =
        runTest(testDispatcher) {
            val moderatePromptUseCase =
                FakeModeratePromptUseCase().apply {
                    result =
                        PromptModerationResult(
                            blocked = true,
                            scoresByLabel = mapOf("nsfw" to 0.95),
                            canonicalScores = mapOf("nsfw" to 0.95),
                            triggeredLabels = listOf("nsfw"),
                            combinedIllegalScore = 0.95,
                        )
                }

            val orchestratorWithModeration =
                InferenceOrchestrator(
                    chatUseCases = chatUseCases,
                    settingsUseCases = settingsUseCases,
                    cancelInferenceUseCase = FakeCancelInferenceUseCase(),
                    activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                    chatRepository = FakeChatRepository(),
                    errorHandler = errorHandler,
                    savedStateHandle = SavedStateHandle(),
                    pipelineExecutor = FakePipelineExecutorPort(),
                    moderatePromptUseCase = moderatePromptUseCase,
                    loggingPort = FakeLoggingPort(),
                    applicationScope = applicationScope,
                )

            mockSubmitPrompt(chatUseCases)
            val responseBlocked = CompletableDeferred<Unit>()
            chatUseCases.generateChatResponseUseCase.flow =
                flow {
                    emit(
                        AccumulatedMessages(
                            messages =
                                mapOf(
                                    MessageId("asst-1") to
                                        MessageSnapshot(
                                            messageId = MessageId("asst-1"),
                                            modelType = ModelType.FAST,
                                            content = "Explicit NSFW response",
                                            thinkingRaw = "",
                                            thinkingDurationSeconds = 0L,
                                            thinkingStartTime = 0L,
                                            thinkingEndTime = 0L,
                                            messageState = MessageState.COMPLETE,
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

            assertEquals(listOf("Explicit NSFW response"), moderatePromptUseCase.calls)
        }

    @Test
    @DisplayName("DOPG-O-05 — orchestrator does not launch title; submit owns title generation")
    fun dopgO05_orchestratorDoesNotLaunchTitle() =
        runTest(testDispatcher) {
            chatUseCases.submitPromptUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-title-1"),
                    chatId = ChatId("chat-title-1"),
                    userMessageCreatedAtMillis = 100L,
                )

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

            chatUseCases.submitPromptUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-title-2"),
                    chatId = ChatId("chat-title-2"),
                    userMessageCreatedAtMillis = 100L,
                )

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

            chatUseCases.submitPromptUseCase.result =
                UserPromptResult(
                    userMessageId = MessageId("user-title-3"),
                    chatId = ChatId("chat-existing-title"),
                    userMessageCreatedAtMillis = 100L,
                )

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

            assertEquals(0, chatUseCases.generateChatTitleUseCase.calls.size)
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
            chatUseCases.submitPromptUseCase.behavior = {
                gateStarted.await()
                UserPromptResult(
                    userMessageId = MessageId("user-double"),
                    chatId = ChatId("chat-double"),
                    userMessageCreatedAtMillis = 100L,
                )
            }

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

            val generation = chatUseCases.generateChatResponseUseCase.calls.single()
            assertEquals("second", generation.prompt)
        }
}
