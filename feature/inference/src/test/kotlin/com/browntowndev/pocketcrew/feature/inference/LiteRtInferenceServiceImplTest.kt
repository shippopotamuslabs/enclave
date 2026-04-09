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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class LiteRtInferenceServiceImplTest {

    private lateinit var mockConversationManager: ConversationManagerPort
    private lateinit var mockConversation: ConversationPort
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
        processThinkingTokens = ProcessThinkingTokensUseCase()

        service = LiteRtInferenceServiceImpl(mockConversationManager, processThinkingTokens, ModelType.FAST)

        coEvery { mockConversationManager.getConversation(any(), any()) } returns mockConversation
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
}
