package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
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
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * ViewModel-level regression tests for PROCESSING -> THINKING -> RESPONDING indicator transitions.
 *
 * Covers the warm-engine / second-chat scenario where a live THINKING snapshot can arrive before
 * Room exposes the PROCESSING placeholder row.
 */
@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModelGenerationStateTransitionTest {
    private val chatId = ChatId("chat-b")
    private val previousAssistantId = MessageId("assistant-prev")
    private val userMessageId = MessageId("user-new")
    private val assistantMessageId = MessageId("assistant-new")
    private val turnKey = ActiveChatTurnKey(chatId, assistantMessageId)

    private lateinit var turnStore: ActiveChatTurnStore
    private lateinit var viewModel: ChatViewModel
    private lateinit var inferenceOrchestrator: com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
    private lateinit var cancelInferenceUseCase: FakeCancelInferenceUseCase
    private lateinit var chatRepository: FakeChatRepository

    @BeforeEach
    fun setup() {
        turnStore = ActiveChatTurnStore()
        chatRepository = FakeChatRepository()
        cancelInferenceUseCase = FakeCancelInferenceUseCase()

        val settingsUseCases = FakeChatSettingsUseCases()
        val chatUseCases = FakeChatUseCases()
        // The VM reads messages through the same repository the stop path mutates, so
        // updateMessageState/updateMessageContent are visible in the getChat flow.
        chatUseCases.getChatUseCase.flowsByChatId[chatId] = chatRepository.getMessagesForChat(chatId)
        val inferenceLockManager = FakeInferenceLockManager()
        val modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase()
        modelDisplayNamesUseCase.result = "LiteRT Model"
        val activeModelProvider = FakeActiveModelProvider()
        val toolExecutionEventPort = FakeToolExecutionEventPort()
        val errorHandler = FakeViewModelErrorHandler()
        val studioRepository = FakeStudioRepository()

        mockSubmitPrompt(
            chatUseCases = chatUseCases,
            userMessageId = userMessageId,
            chatId = chatId,
            userMessageCreatedAtMillis = 2L,
        )

        val harness =
            ChatViewModelTestSupport.createHarness(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value)),
                cancelInferenceUseCase = cancelInferenceUseCase,
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                activeModelProvider = activeModelProvider,
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                activeChatTurnSnapshotPort = turnStore,
                playbackController = FakeTtsPlaybackController(),
                studioRepository = studioRepository,
                shareChatUseCase = FakeShareChatUseCase(),
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
                stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase(),
            )
        viewModel = harness.viewModel
        inferenceOrchestrator = harness.inferenceOrchestrator
    }

    @Test
    fun `live generating snapshot without content shows Processing indicator`() =
        runTest {
            val indicators = collectAssistantIndicators()

            chatRepository.seedMessages(
                listOf(
                    userMessage(),
                    assistantMessage(id = assistantMessageId, state = MessageState.PROCESSING),
                ),
            )
            advanceUntilIdle()

            publishSnapshot(
                snapshot(
                    state = MessageState.GENERATING,
                    content = "",
                ),
            )
            advanceUntilIdle()

            assertEquals(IndicatorState.Processing, viewModel.uiState.value.indicatorFor(assistantMessageId))
            indicators.cancel()
        }

    @Test
    fun `room processing placeholder shows Processing before live thinking snapshot`() =
        runTest {
            val indicators = collectAssistantIndicators()

            chatRepository.seedMessages(
                listOf(
                    assistantMessage(
                        id = assistantMessageId,
                        state = MessageState.PROCESSING,
                    ),
                ),
            )
            advanceUntilIdle()

            assertTrue(indicators.last() is IndicatorState.Processing)

            publishSnapshot(thinkingSnapshot("planning"))
            advanceUntilIdle()

            assertTrue(indicators.last() is IndicatorState.Thinking)
            assertTrue(viewModel.uiState.value.isGenerating)
            indicators.cancel()
        }

    @Test
    fun `warm engine second chat shows Processing when thinking snapshot arrives before room row`() =
        runTest {
            val indicators = collectAssistantIndicators()

            chatRepository.seedMessages(
                listOf(
                    assistantMessage(
                        id = previousAssistantId,
                        state = MessageState.COMPLETE,
                        content = "previous answer",
                        createdAt = 1L,
                    ),
                ),
            )
            advanceUntilIdle()

            viewModel.onInputChange("follow up")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            val sentAssistantId =
                checkNotNull(inferenceOrchestrator.requestedTurnKey.value?.assistantMessageId) {
                    "Expected requested turn after send pipeline"
                }
            publishSnapshot(
                snapshot = thinkingSnapshot("planning", sentAssistantId),
                activeTurnKey = ActiveChatTurnKey(chatId, sentAssistantId),
            )
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.messages
                    .any { message -> message.id == sentAssistantId },
                "Expected orphan assistant row before Room catch-up; messages=" +
                    viewModel.uiState.value.messages.map { message ->
                        message.id.value to message.indicatorState
                    },
            )
            assertEquals(
                IndicatorState.Processing,
                viewModel.uiState.value.indicatorFor(sentAssistantId),
                "history=${indicators.history()}",
            )

            chatRepository.seedMessages(
                listOf(
                    assistantMessage(
                        id = previousAssistantId,
                        state = MessageState.COMPLETE,
                        content = "previous answer",
                        createdAt = 1L,
                    ),
                    userMessage(),
                    assistantMessage(
                        id = sentAssistantId,
                        state = MessageState.PROCESSING,
                        createdAt = 3L,
                    ),
                ),
            )
            advanceUntilIdle()

            assertTrue(indicators.last() is IndicatorState.Thinking)
            indicators.cancel()
        }

    @Test
    fun `full generation lifecycle transitions Processing Thinking Generating Complete`() =
        runTest {
            val indicators = collectAssistantIndicators()

            chatRepository.seedMessages(
                listOf(
                    userMessage(),
                    assistantMessage(id = assistantMessageId, state = MessageState.PROCESSING),
                ),
            )
            advanceUntilIdle()
            assertTrue(indicators.last() is IndicatorState.Processing)

            publishSnapshot(thinkingSnapshot("planning"))
            advanceUntilIdle()
            assertTrue(indicators.last() is IndicatorState.Thinking)

            publishSnapshot(
                snapshot(
                    state = MessageState.GENERATING,
                    content = "Hello",
                    thinkingRaw = "planning",
                    thinkingDurationSeconds = 2L,
                    thinkingStartTime = 1L,
                ),
            )
            advanceUntilIdle()
            assertTrue(indicators.last() is IndicatorState.Generating)

            chatRepository.seedMessages(
                listOf(
                    userMessage(),
                    assistantMessage(
                        id = assistantMessageId,
                        state = MessageState.COMPLETE,
                        content = "Hello",
                        thinkingRaw = "planning",
                        thinkingDurationSeconds = 2L,
                        thinkingStartTime = 1L,
                    ),
                ),
            )
            publishSnapshot(
                snapshot(
                    state = MessageState.COMPLETE,
                    content = "Hello",
                    thinkingRaw = "planning",
                    thinkingDurationSeconds = 2L,
                    thinkingStartTime = 1L,
                    isComplete = true,
                ),
            )
            advanceUntilIdle()

            assertTrue(indicators.last() is IndicatorState.Complete)
            assertFalse(viewModel.uiState.value.isGenerating)
            indicators.cancel()
        }

    @Test
    fun `messageState change without updatedAt change refreshes uiState`() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            val assistant =
                assistantMessage(
                    id = assistantMessageId,
                    state = MessageState.PROCESSING,
                    updatedAt = 100L,
                )
            chatRepository.seedMessages(listOf(assistant))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.latestAssistantIndicator() is IndicatorState.Processing)

            chatRepository.seedMessages(
                listOf(
                    assistant.copy(
                        messageState = MessageState.COMPLETE,
                        content = Content(text = "done"),
                    ),
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.latestAssistantIndicator() is IndicatorState.Complete)
            assertFalse(viewModel.uiState.value.isGenerating)
        }

    @Test
    fun `send keeps observe key alive and shows Processing once room catches up`() =
        runTest {
            val indicators = collectAssistantIndicators()

            viewModel.onInputChange("Hello")
            viewModel.onSendMessage()
            advanceTimeBy(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS)
            advanceUntilIdle()

            chatRepository.seedMessages(
                listOf(
                    userMessage(),
                    assistantMessage(id = assistantMessageId, state = MessageState.PROCESSING),
                ),
            )
            advanceUntilIdle()

            assertTrue(indicators.any { indicator -> indicator is IndicatorState.Processing })
            assertTrue(viewModel.uiState.value.isGenerating)
            indicators.cancel()
        }

    @Test
    fun `engine loading snapshot shows EngineLoading then Thinking`() =
        runTest {
            val indicators = collectAssistantIndicators()

            chatRepository.seedMessages(
                listOf(
                    assistantMessage(id = assistantMessageId, state = MessageState.PROCESSING),
                ),
            )
            advanceUntilIdle()

            publishSnapshot(snapshot(state = MessageState.ENGINE_LOADING))
            advanceUntilIdle()
            assertTrue(indicators.last() is IndicatorState.EngineLoading)
            assertFalse(viewModel.uiState.value.canStop)

            publishSnapshot(thinkingSnapshot("loading complete"))
            advanceUntilIdle()
            assertTrue(indicators.last() is IndicatorState.Thinking)
            assertTrue(viewModel.uiState.value.canStop)
            indicators.cancel()
        }

    @Test
    fun `stopGeneration completes retained incomplete turn after ViewModel recreation`() =
        runTest {
            val indicators = collectAssistantIndicators()

            chatRepository.seedMessages(
                listOf(
                    userMessage(),
                    assistantMessage(id = assistantMessageId, state = MessageState.PROCESSING),
                ),
            )
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isGenerating)

            viewModel.stopGeneration()
            advanceUntilIdle()

            assertTrue(cancelInferenceUseCase.callCount >= 1)
            assertTrue(
                chatRepository.updateMessageStateCalls.contains(
                    assistantMessageId to MessageState.COMPLETE,
                ),
            )
            chatRepository.seedMessages(
                listOf(
                    userMessage(),
                    assistantMessage(id = assistantMessageId, state = MessageState.COMPLETE),
                ),
            )
            advanceUntilIdle()
            assertFalse(
                viewModel.uiState.value.isGenerating,
                "messages=${viewModel.uiState.value.messages.map { it.id.value to it.indicatorState }}",
            )
            assertFalse(viewModel.uiState.value.indicatorFor(assistantMessageId) is IndicatorState.Processing)
            indicators.cancel()
        }

    @Test
    fun `uiState turbine observes Processing before Thinking in order`() =
        runTest {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }

            viewModel.uiState
                .map { state -> state.latestAssistantIndicator() }
                .distinctUntilChanged()
                .test {
                    skipItems(1)

                    chatRepository.seedMessages(
                        listOf(
                            assistantMessage(id = assistantMessageId, state = MessageState.PROCESSING),
                        ),
                    )
                    advanceUntilIdle()
                    assertTrue(awaitItem() is IndicatorState.Processing)

                    publishSnapshot(thinkingSnapshot("planning"))
                    advanceUntilIdle()
                    assertTrue(awaitItem() is IndicatorState.Thinking)

                    cancelAndIgnoreRemainingEvents()
                }
        }

    private fun TestScope.collectAssistantIndicators(): IndicatorCollector {
        val indicators = mutableListOf<IndicatorState?>()
        val job =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                launch {
                    viewModel.uiState.collect()
                }
                viewModel.uiState
                    .map { state -> state.latestAssistantIndicator() }
                    .distinctUntilChanged()
                    .collect { indicator -> indicators.add(indicator) }
            }
        return IndicatorCollector(indicators, job)
    }

    private class IndicatorCollector(
        private val indicators: MutableList<IndicatorState?>,
        private val job: kotlinx.coroutines.Job,
    ) {
        fun last(): IndicatorState? = indicators.lastOrNull()

        fun any(predicate: (IndicatorState?) -> Boolean): Boolean = indicators.any(predicate)

        fun history(): List<IndicatorState?> = indicators.toList()

        fun cancel() {
            job.cancel()
        }
    }

    private suspend fun TestScope.publishSnapshot(
        snapshot: AccumulatedMessages,
        activeTurnKey: ActiveChatTurnKey = turnKey,
    ) {
        turnStore.publish(activeTurnKey, snapshot)
        advanceTimeBy(20)
    }

    private fun ChatUiState.indicatorFor(messageId: MessageId): IndicatorState? =
        messages.firstOrNull { message -> message.id == messageId }?.indicatorState

    private fun ChatUiState.latestAssistantIndicator(): IndicatorState? =
        messages.lastOrNull { message -> message.role == MessageRole.Assistant }?.indicatorState

    private fun userMessage(): Message =
        Message(
            id = userMessageId,
            chatId = chatId,
            role = Role.USER,
            content = Content(text = "Hello"),
            messageState = MessageState.COMPLETE,
            createdAt = 2L,
        )

    private fun assistantMessage(
        id: MessageId,
        state: MessageState,
        content: String = "",
        thinkingRaw: String? = null,
        thinkingDurationSeconds: Long? = null,
        thinkingStartTime: Long? = null,
        createdAt: Long = 10L,
        updatedAt: Long? = null,
    ): Message =
        Message(
            id = id,
            chatId = chatId,
            role = Role.ASSISTANT,
            content = Content(text = content),
            messageState = state,
            thinkingRaw = thinkingRaw,
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingStartTime = thinkingStartTime,
            createdAt = createdAt,
            updatedAt = updatedAt,
            modelType = ModelType.THINKING,
        )

    private fun thinkingSnapshot(
        text: String,
        targetAssistantId: MessageId = assistantMessageId,
    ): AccumulatedMessages =
        snapshot(
            state = MessageState.THINKING,
            thinkingRaw = text,
            transcriptSegments =
                listOf(
                    MessageTranscriptSegment.Thinking(
                        ordinal = 0,
                        text = text,
                        thinkingStartTime = 1L,
                    ),
                ),
            targetAssistantId = targetAssistantId,
        )

    private fun snapshot(
        state: MessageState,
        content: String = "",
        thinkingRaw: String = "",
        thinkingDurationSeconds: Long = 0L,
        thinkingStartTime: Long = 0L,
        transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
        isComplete: Boolean = state == MessageState.COMPLETE,
        targetAssistantId: MessageId = assistantMessageId,
    ): AccumulatedMessages =
        AccumulatedMessages(
            messages =
                mapOf(
                    targetAssistantId to
                        MessageSnapshot(
                            messageId = targetAssistantId,
                            modelType = ModelType.THINKING,
                            content = content,
                            thinkingRaw = thinkingRaw,
                            thinkingDurationSeconds = thinkingDurationSeconds,
                            thinkingStartTime = thinkingStartTime,
                            thinkingEndTime = 0L,
                            messageState = state,
                            isComplete = isComplete,
                            transcriptSegments = transcriptSegments,
                        ),
                ),
        )
}
