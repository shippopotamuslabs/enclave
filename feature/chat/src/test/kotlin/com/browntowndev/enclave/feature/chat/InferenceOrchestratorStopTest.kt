package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceOrchestratorStopTest {
    private val testDispatcher = StandardTestDispatcher()

    @RegisterExtension
    @JvmField
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var chatUseCases: ChatUseCases
    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var cancelInferenceUseCase: CancelInferenceUseCase
    private lateinit var chatRepository: com.browntowndev.enclave.domain.port.repository.ChatRepository
    private lateinit var turnStore: ActiveChatTurnStore
    private lateinit var orchestrator: InferenceOrchestrator
    private lateinit var applicationScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)
        chatUseCases = mockk(relaxed = true)
        settingsUseCases = mockk()
        cancelInferenceUseCase = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        turnStore = ActiveChatTurnStore()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())

        val errorHandler = mockk<ViewModelErrorHandler>()
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns
            CoroutineExceptionHandler { _: CoroutineContext, _ -> }

        orchestrator =
            InferenceOrchestrator(
                chatUseCases = chatUseCases,
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = cancelInferenceUseCase,
                activeChatTurnSnapshotPort = turnStore,
                chatRepository = chatRepository,
                errorHandler = errorHandler,
                savedStateHandle = SavedStateHandle(),
                pipelineExecutor = mockk(relaxed = true),
                moderatePromptUseCase = mockk<ModeratePromptUseCase>(relaxed = true),
                loggingPort = mockk(relaxed = true),
                applicationScope = applicationScope,
            )

        mockSubmitPrompt(
            chatUseCases = chatUseCases,
            userMessageId = MessageId("user-stop-1"),
            chatId = ChatId("chat-stop-1"),
        )
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
                awaitCancellation()
            }
    }

    @Test
    @DisplayName("stopInference cancels generation, calls cancel use case, and persists final content")
    fun stopInferenceHappyPath() =
        runTest(testDispatcher) {
            launch {
                orchestrator.sendMessage(
                    scope = this,
                    input = "Hello",
                    selectedMode = ChatModeUi.THINKING,
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

            val turnKey =
                checkNotNull(orchestrator.requestedTurnKey.value) {
                    "Send pipeline must register an active turn before stop"
                }
            turnStore.publish(
                turnKey,
                com.browntowndev.enclave.domain.model.chat.AccumulatedMessages(
                    messages =
                        mapOf(
                            turnKey.assistantMessageId to
                                com.browntowndev.enclave.domain.model.chat.MessageSnapshot(
                                    messageId = turnKey.assistantMessageId,
                                    modelType = com.browntowndev.enclave.domain.model.inference.ModelType.THINKING,
                                    content = "Partial answer",
                                    thinkingRaw = "",
                                    thinkingDurationSeconds = 0L,
                                    thinkingStartTime = 0L,
                                    thinkingEndTime = 0L,
                                    messageState = MessageState.GENERATING,
                                    isComplete = false,
                                    transcriptSegments = emptyList(),
                                ),
                        ),
                ),
            )

            orchestrator.stopInference(
                scope = this,
                activeTurnKey = turnKey,
                finalContent = "Partial answer",
            )
            advanceUntilIdle()

            verify(exactly = 2) { cancelInferenceUseCase() }
            coVerify {
                chatRepository.updateMessageContent(
                    turnKey.assistantMessageId,
                    "Partial answer",
                )
            }
            coVerify {
                chatRepository.updateMessageState(
                    turnKey.assistantMessageId,
                    MessageState.COMPLETE,
                )
            }
            assertNull(turnStore.getSnapshot(turnKey))
            assertNull(orchestrator.requestedTurnKey.value)
        }
}
