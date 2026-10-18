package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LiteRtInferenceServiceImplTest {
    private lateinit var conversationManager: FakeConversationManager
    private lateinit var conversation: FakeLiteRtConversation
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase
    private val toolExecutionEventPort = FakeToolExecutionEventPort()
    private lateinit var service: LiteRtInferenceServiceImpl

    @BeforeEach
    fun setup() {
        conversation = FakeLiteRtConversation()
        conversationManager =
            FakeConversationManager().also { it.conversation = conversation }
        processThinkingTokens = ProcessThinkingTokensUseCase()

        service = LiteRtInferenceServiceImpl(conversationManager, processThinkingTokens, toolExecutionEventPort, ModelType.FAST)
    }

    // ========== Integration Tests (using real use case) ==========

    @Test
    fun `sendPrompt should emit events correctly`() =
        runTest {
            // Given - simulate the conversation returning responses with thought and text
            val responses =
                flowOf(
                    ConversationResponse(thought = "thinking"),
                    ConversationResponse(text = "answer"),
                    ConversationResponse(thought = "more thought", text = "more answer"),
                )
            conversation.responseFlow = responses

            val events = service.sendPrompt("Hello", closeConversation = false).toList()

            // Then - events should be processed
            assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "thinking" })
            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "answer" })
            assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "more thought" })
            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "more answer" })
            assertTrue(events.any { it is InferenceEvent.Finished })
        }

    @Test
    fun `sendPrompt should emit WebSearchResults when specific ToolExecutionEvent Finished is received`() =
        runTest {
            val toolEventEmitted = kotlinx.coroutines.CompletableDeferred<Unit>()

            // Given
            val responses =
                kotlinx.coroutines.flow.flow {
                    emit(ConversationResponse(text = "thinking"))
                    toolEventEmitted.await()
                    kotlinx.coroutines.delay(50)
                    emit(ConversationResponse(text = "answer"))
                }
            conversation.responseFlow = responses

            val options =
                GenerationOptions(
                    reasoningBudget = 0,
                    chatId =
                        com.browntowndev.enclave.domain.model.chat
                            .ChatId("chat-1"),
                    userMessageId =
                        com.browntowndev.enclave.domain.model.chat
                            .MessageId("msg-1"),
                    assistantMessageId =
                        com.browntowndev.enclave.domain.model.chat
                            .MessageId("msg-2"),
                    modelType = ModelType.FAST,
                )

            // When
            val deferredEvents = async { service.sendPrompt("Hello", options, false).toList() }

            launch {
                while (toolExecutionEventPort.events.subscriptionCount.value == 0) {
                    kotlinx.coroutines.delay(10)
                }
                val resultJson = """{"results":[{"title":"Test","url":"https://test.com","content":"test content"}]}"""
                toolExecutionEventPort.events.emit(
                    com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent.Finished(
                        eventId = "event-1",
                        chatId =
                            com.browntowndev.enclave.domain.model.chat
                                .ChatId("chat-1"),
                        userMessageId =
                            com.browntowndev.enclave.domain.model.chat
                                .MessageId("msg-1"),
                        resultJson = resultJson,
                    ),
                )
                toolEventEmitted.complete(Unit)
            }

            // Then
            val events = deferredEvents.await()
            val tavilyResultsEvent = events.filterIsInstance<InferenceEvent.WebSearchResults>().firstOrNull()

            assertNotNull(tavilyResultsEvent)
            assertEquals(1, tavilyResultsEvent!!.sources.size)
            assertEquals("Test", tavilyResultsEvent.sources.first().title)
        }

    @Test
    fun `sendPrompt should handle errors correctly`() =
        runTest {
            // Given - simulate the conversation returning responses with thought and text
            val responses =
                flowOf(
                    ConversationResponse(thought = "thinking"),
                    ConversationResponse(text = "answer"),
                    ConversationResponse(thought = "more thought", text = "more answer"),
                )
            conversation.responseFlow = responses

            val events = service.sendPrompt("Hello", closeConversation = false).toList()

            // Then - events should be processed
            assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "thinking" })
            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "answer" })
            assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "more thought" })
            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "more answer" })
            assertTrue(events.any { it is InferenceEvent.Finished })
        }

    @Test
    fun `sendPrompt should close conversation when closeConversation is true`() =
        runTest {
            val responses = flowOf(ConversationResponse(text = "answer"))
            conversation.responseFlow = responses

            service.sendPrompt("Hello", closeConversation = true).toList()

            assertEquals(1, conversationManager.closeConversationCount)
        }

    @Test
    fun `sendPrompt should not close conversation when closeConversation is false`() =
        runTest {
            val responses = flowOf(ConversationResponse(text = "answer"))
            conversation.responseFlow = responses

            service.sendPrompt("Hello", closeConversation = false).toList()

            assertEquals(0, conversationManager.closeConversationCount)
        }

    @Test
    fun `sendPrompt should process thinking tokens correctly`() =
        runTest {
            // Given - simulate the conversation returning responses with thought and text
            val responses =
                flowOf(
                    ConversationResponse(thought = "thinking"),
                    ConversationResponse(text = "answer"),
                    ConversationResponse(thought = "more thought", text = "more answer"),
                )
            conversation.responseFlow = responses

            val events = service.sendPrompt("Hello", closeConversation = false).toList()

            // Then - events should be processed
            assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "thinking" })
            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "answer" })
            assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "more thought" })
            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "more answer" })
            assertTrue(events.any { it is InferenceEvent.Finished })
        }

    @Test
    fun `closeSession closes conversation and engine`() =
        runTest {
            service.closeSession()

            assertEquals(1, conversationManager.closeConversationCount)
            assertEquals(1, conversationManager.closeEngineCount)
        }

    @Test
    fun `setHistory delegates to conversation manager`() =
        runTest {
            val history = listOf(ChatMessage(Role.USER, "Hello"))

            service.setHistory(history)

            assertEquals(listOf(history), conversationManager.historyCalls)
        }

    @Test
    fun `sendPrompt should emit Finished when LiteRT throws CancellationException`() =
        runTest {
            // Given - simulate LiteRT throwing CancellationException (user pressed stop)
            val cancelException = CancellationException("Generation cancelled")
            conversation.responseFlow =
                kotlinx.coroutines.flow.flow {
                    throw cancelException
                }

            // When
            val events = mutableListOf<InferenceEvent>()
            try {
                service.sendPrompt("Hello", closeConversation = false).collect { events.add(it) }
            } catch (_: CancellationException) {
                // Structured concurrency rethrow is expected after Finished is emitted.
            }

            // Then - should emit Finished instead of Error
            assertTrue(events.any { it is InferenceEvent.Finished })
            assertTrue(events.none { it is InferenceEvent.Error })
            assertEquals(listOf(1), conversationManager.endedRequestIds)
            assertEquals(0, conversationManager.cancelProcessCount)
        }
}
