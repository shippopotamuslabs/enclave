package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationResponse
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerifySequence
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class LiteRtInferenceServiceImplTest {

    private lateinit var mockConversationManager: ConversationManagerPort
    private lateinit var mockConversation: ConversationPort
    private lateinit var mockToolExecutionEventPort: com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
    private lateinit var eventFlow: MutableSharedFlow<com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent>
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase
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
        mockToolExecutionEventPort = mockk(relaxed = true)
        eventFlow = MutableSharedFlow()
        every { mockToolExecutionEventPort.events } returns eventFlow
        processThinkingTokens = ProcessThinkingTokensUseCase()

        service = LiteRtInferenceServiceImpl(mockConversationManager, processThinkingTokens, mockToolExecutionEventPort, ModelType.FAST)

        coEvery { mockConversationManager.getConversation(any(), any(), any()) } returns mockConversation
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ========== Integration Tests (using real use case) ==========

    @Test
    fun `sendPrompt should emit events correctly`() = runTest {
        // Given - simulate the conversation returning responses with thought and text
        val responses = flowOf(
            ConversationResponse(thought = "thinking"),
            ConversationResponse(text = "answer"),
            ConversationResponse(thought = "more thought", text = "more answer")
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
    fun `sendPrompt should emit TavilyResults when specific ToolExecutionEvent Finished is received`() = runTest {
        val toolEventEmitted = kotlinx.coroutines.CompletableDeferred<Unit>()
        
        // Given
        val responses = kotlinx.coroutines.flow.flow {
            emit(ConversationResponse(text = "thinking"))
            toolEventEmitted.await()
            kotlinx.coroutines.delay(50) 
            emit(ConversationResponse(text = "answer"))
        }
        every { mockConversation.sendMessageAsync(any(), any()) } returns responses
        
        io.mockk.mockkObject(com.browntowndev.pocketcrew.domain.util.TavilyResultParser)
        every { 
            com.browntowndev.pocketcrew.domain.util.TavilyResultParser.parse(any(), any()) 
        } returns listOf(
            com.browntowndev.pocketcrew.domain.model.chat.TavilySource(
                messageId = com.browntowndev.pocketcrew.domain.model.chat.MessageId("msg-2"),
                title = "Test",
                url = "https://test.com",
                content = "test content"
            )
        )
        
        val options = GenerationOptions(
            reasoningBudget = 0,
            chatId = com.browntowndev.pocketcrew.domain.model.chat.ChatId("chat-1"),
            userMessageId = com.browntowndev.pocketcrew.domain.model.chat.MessageId("msg-1"),
            assistantMessageId = com.browntowndev.pocketcrew.domain.model.chat.MessageId("msg-2"),
            modelType = ModelType.FAST
        )
        
        // When
        val deferredEvents = async { service.sendPrompt("Hello", options, false).toList() }
        
        launch {
            while (eventFlow.subscriptionCount.value == 0) {
                kotlinx.coroutines.delay(10)
            }
            val resultJson = """{"results":[{"title":"Test","url":"https://test.com","content":"test content"}]}"""
            eventFlow.emit(
                com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent.Finished(
                    eventId = "event-1",
                    chatId = com.browntowndev.pocketcrew.domain.model.chat.ChatId("chat-1"),
                    userMessageId = com.browntowndev.pocketcrew.domain.model.chat.MessageId("msg-1"),
                    resultJson = resultJson
                )
            )
            toolEventEmitted.complete(Unit)
        }
        
        // Then
        val events = deferredEvents.await()
        val tavilyResultsEvent = events.filterIsInstance<InferenceEvent.TavilyResults>().firstOrNull()
        
        io.mockk.unmockkObject(com.browntowndev.pocketcrew.domain.util.TavilyResultParser)
        
        assertNotNull(tavilyResultsEvent)
        assertEquals(1, tavilyResultsEvent!!.sources.size)
        assertEquals("Test", tavilyResultsEvent.sources.first().title)
    }

    @Test
    fun `sendPrompt should handle errors correctly`() = runTest {
        // Given - simulate the conversation returning responses with thought and text
        val responses = flowOf(
            ConversationResponse(thought = "thinking"),
            ConversationResponse(text = "answer"),
            ConversationResponse(thought = "more thought", text = "more answer")
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
    fun `sendPrompt should close conversation when closeConversation is true`() = runTest {
        val responses = flowOf(ConversationResponse(text = "answer"))
        every { mockConversation.sendMessageAsync(any(), any()) } returns responses

        service.sendPrompt("Hello", closeConversation = true).toList()

        coVerify { mockConversationManager.closeConversation() }
    }

    @Test
    fun `sendPrompt should not close conversation when closeConversation is false`() = runTest {
        val responses = flowOf(ConversationResponse(text = "answer"))
        every { mockConversation.sendMessageAsync(any(), any()) } returns responses

        service.sendPrompt("Hello", closeConversation = false).toList()

        coVerify(exactly = 0) { mockConversationManager.closeConversation() }
    }

    @Test
    fun `sendPrompt should process thinking tokens correctly`() = runTest {
        // Given - simulate the conversation returning responses with thought and text
        val responses = flowOf(
            ConversationResponse(thought = "thinking"),
            ConversationResponse(text = "answer"),
            ConversationResponse(thought = "more thought", text = "more answer")
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
    fun `closeSession closes conversation and engine`() = runTest {
        service.closeSession()

        coVerify { mockConversationManager.closeConversation() }
        coVerify { mockConversationManager.closeEngine() }
    }

    @Test
    fun `setHistory delegates to conversation manager`() = runTest {
        val history = listOf(ChatMessage(Role.USER, "Hello"))

        service.setHistory(history)

        coVerify { mockConversationManager.setHistory(history) }
    }

    @Test
    fun `sendPrompt should emit Finished when LiteRT throws CancellationException`() = runTest {
        // Given - simulate LiteRT throwing CancellationException (user pressed stop)
        val cancelException = java.util.concurrent.CancellationException("Generation cancelled")
        every { mockConversation.sendMessageAsync(any(), any()) } returns kotlinx.coroutines.flow.flow {
            throw cancelException
        }

        // When
        val events = service.sendPrompt("Hello", closeConversation = false).toList()

        // Then - should emit Finished instead of Error
        assertTrue(events.any { it is InferenceEvent.Finished })
        assertTrue(events.none { it is InferenceEvent.Error })
    }
}
