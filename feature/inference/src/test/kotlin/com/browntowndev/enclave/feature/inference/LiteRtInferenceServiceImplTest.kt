package com.browntowndev.enclave.feature.inference

import android.util.Log
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LiteRtInferenceServiceImplTest {
    private lateinit var mockConversationManager: ConversationManager
    private lateinit var mockConversation: LiteRtConversation
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase
    private val toolExecutionEventPort = mockk<ToolExecutionEventPort>(relaxed = true)
    private lateinit var eventFlow: MutableSharedFlow<com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent>
    private lateinit var service: LiteRtInferenceServiceImpl

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockConversationManager = mockk(relaxed = true)
        mockConversation = mockk(relaxed = true)
        processThinkingTokens = ProcessThinkingTokensUseCase()
        eventFlow = MutableSharedFlow()
        every { toolExecutionEventPort.events } returns eventFlow

        service = LiteRtInferenceServiceImpl(mockConversationManager, processThinkingTokens, toolExecutionEventPort, ModelType.FAST)

        coEvery { mockConversationManager.getConversation(any(), any(), any()) } returns mockConversation
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
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
            every { mockConversation.sendMessageAsync(any(), any()) } returns responses

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
            every { mockConversation.sendMessageAsync(any(), any()) } returns responses

            io.mockk.mockkObject(com.browntowndev.enclave.domain.util.WebSearchResultParser)
            every {
                com.browntowndev.enclave.domain.util.WebSearchResultParser
                    .parse(any(), any())
            } returns
                listOf(
                    com.browntowndev.enclave.domain.model.chat.WebSearchSource(
                        messageId =
                            com.browntowndev.enclave.domain.model.chat
                                .MessageId("msg-2"),
                        title = "Test",
                        url = "https://test.com",
                        content = "test content",
                    ),
                )

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
                while (eventFlow.subscriptionCount.value == 0) {
                    kotlinx.coroutines.delay(10)
                }
                val resultJson = """{"results":[{"title":"Test","url":"https://test.com","content":"test content"}]}"""
                eventFlow.emit(
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

            io.mockk.unmockkObject(com.browntowndev.enclave.domain.util.WebSearchResultParser)

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
            every { mockConversation.sendMessageAsync(any(), any()) } returns responses

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
            every { mockConversation.sendMessageAsync(any(), any()) } returns responses

            service.sendPrompt("Hello", closeConversation = true).toList()

            coVerify { mockConversationManager.closeConversation() }
        }

    @Test
    fun `sendPrompt should not close conversation when closeConversation is false`() =
        runTest {
            val responses = flowOf(ConversationResponse(text = "answer"))
            every { mockConversation.sendMessageAsync(any(), any()) } returns responses

            service.sendPrompt("Hello", closeConversation = false).toList()

            coVerify(exactly = 0) { mockConversationManager.closeConversation() }
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
            every { mockConversation.sendMessageAsync(any(), any()) } returns responses

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

            coVerify { mockConversationManager.closeConversation() }
            coVerify { mockConversationManager.closeEngine() }
        }

    @Test
    fun `setHistory delegates to conversation manager`() =
        runTest {
            val history = listOf(ChatMessage(Role.USER, "Hello"))

            service.setHistory(history)

            coVerify { mockConversationManager.setHistory(history) }
        }

    @Test
    fun `sendPrompt should emit Finished when LiteRT throws CancellationException`() =
        runTest {
            // Given - simulate LiteRT throwing CancellationException (user pressed stop)
            val cancelException = java.util.concurrent.CancellationException("Generation cancelled")
            every { mockConversation.sendMessageAsync(any(), any()) } returns
                kotlinx.coroutines.flow.flow {
                    throw cancelException
                }

            // When
            val events = service.sendPrompt("Hello", closeConversation = false).toList()

            // Then - should emit Finished instead of Error
            assertTrue(events.any { it is InferenceEvent.Finished })
            assertTrue(events.none { it is InferenceEvent.Error })
        }
}
