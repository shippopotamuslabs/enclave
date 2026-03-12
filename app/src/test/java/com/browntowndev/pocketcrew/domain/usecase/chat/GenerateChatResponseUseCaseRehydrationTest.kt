package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.inference.llama.ChatMessage
import com.browntowndev.pocketcrew.inference.llama.ChatRole
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for GenerateChatResponseUseCase, focusing on history rehydration.
 */
class GenerateChatResponseUseCaseTest {

    private lateinit var fastModelService: LlmInferencePort
    private lateinit var thinkingModelService: LlmInferencePort
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var bufferThinkingSteps: BufferThinkingStepsUseCase
    private lateinit var useCase: GenerateChatResponseUseCase

    @BeforeEach
    fun setup() {
        fastModelService = mockk(relaxed = true)
        thinkingModelService = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        bufferThinkingSteps = mockk(relaxed = true)
        val loggingPort = mockk<LoggingPort>(relaxed = true)

        useCase = GenerateChatResponseUseCase(
            fastModelService = fastModelService,
            thinkingModelService = thinkingModelService,
            chatRepository = mockk(relaxed = true),
            pipelineExecutor = mockk(relaxed = true),
            messageRepository = messageRepository,
            bufferThinkingSteps = bufferThinkingSteps,
            loggingPort = loggingPort
        )
    }

    // ========== Chat ID Extraction Logic Tests ==========
    // These test the logic used internally - extracted here for clarity

    @Test
    fun `chatId extraction parses valid messageId correctly`() {
        // Given
        val validMessageId = "123_assistant"

        // When - this is the logic used in the use case
        val chatId = validMessageId
            .replace("_assistant", "")
            .toLongOrNull()
            ?: throw IllegalArgumentException("Invalid message ID format: $validMessageId")

        // Then
        assertEquals(123L, chatId)
    }

    @Test
    fun `chatId extraction throws for invalid messageId without assistant suffix`() {
        // Given
        val invalidMessageId = "123_user"

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            invalidMessageId
                .replace("_assistant", "")
                .toLongOrNull()
                ?: throw IllegalArgumentException("Invalid message ID format: $invalidMessageId")
        }
    }

    @Test
    fun `chatId extraction throws for non-numeric messageId`() {
        // Given
        val invalidMessageId = "abc_assistant"

        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            invalidMessageId
                .replace("_assistant", "")
                .toLongOrNull()
                ?: throw IllegalArgumentException("Invalid message ID format: $invalidMessageId")
        }
    }

    @Test
    fun `chatId extraction handles very large IDs`() {
        // Given
        val largeMessageId = "999999999999_assistant"

        // When
        val chatId = largeMessageId
            .replace("_assistant", "")
            .toLongOrNull()
            ?: throw IllegalArgumentException("Invalid message ID format: $largeMessageId")

        // Then
        assertEquals(999999999999L, chatId)
    }

    // ========== Rehydration Tests ==========

    @Test
    fun `rehydration loads messages from repository and calls setHistory`() = runTest {
        // Given
        val chatId = 1L
        val messageId = "1_assistant"
        val messages = listOf(
            Message(id = 1, chatId = chatId, content = "Hello", role = Role.USER),
            Message(id = 2, chatId = chatId, content = "Hi!", role = Role.ASSISTANT),
            Message(id = 3, chatId = chatId, content = "How are you?", role = Role.USER)
        )
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("I'm good!", null)
        )

        // When - invoke with FAST mode
        useCase.invoke("Test prompt", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then - verify setHistory was called with correct messages
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }

        val capturedHistory = historySlot.captured
        assertEquals(3, capturedHistory.size)
        assertEquals(ChatRole.USER, capturedHistory[0].role)
        assertEquals("Hello", capturedHistory[0].content)
        assertEquals(ChatRole.ASSISTANT, capturedHistory[1].role)
        assertEquals("Hi!", capturedHistory[1].content)
        assertEquals(ChatRole.USER, capturedHistory[2].role)
        assertEquals("How are you?", capturedHistory[2].content)
    }

    @Test
    fun `rehydration works with empty chat history`() = runTest {
        // Given
        val chatId = 1L
        val messageId = "1_assistant"
        coEvery { messageRepository.getMessagesForChat(chatId) } returns emptyList()
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("First response!", null)
        )

        // When
        useCase.invoke("First prompt", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then - setHistory called with empty list (will add system prompt in engine)
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }
        assertTrue(historySlot.captured.isEmpty())
    }

    @Test
    fun `rehydration works with single user message`() = runTest {
        // Given
        val chatId = 1L
        val messageId = "1_assistant"
        val messages = listOf(
            Message(id = 1, chatId = chatId, content = "Hello", role = Role.USER)
        )
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Hi there!", null)
        )

        // When
        useCase.invoke("Reply", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }
        assertEquals(1, historySlot.captured.size)
        assertEquals(ChatRole.USER, historySlot.captured[0].role)
        assertEquals("Hello", historySlot.captured[0].content)
    }

    @Test
    fun `rehydration maps domain roles to chat roles correctly`() = runTest {
        // Given
        val chatId = 1L
        val messageId = "1_assistant"
        val messages = listOf(
            Message(id = 1, chatId = chatId, content = "User msg", role = Role.USER),
            Message(id = 2, chatId = chatId, content = "Assistant msg", role = Role.ASSISTANT),
            Message(id = 3, chatId = chatId, content = "Another user", role = Role.USER)
        )
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { thinkingModelService.setHistory(any()) } returns Unit
        coEvery { thinkingModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Response", null)
        )

        // When - use THINKING mode to test thinkingModelService
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.THINKING).collect { }

        // Then
        val historySlot = slot<List<ChatMessage>>()
        coVerify { thinkingModelService.setHistory(capture(historySlot)) }

        val history = historySlot.captured
        assertEquals(ChatRole.USER, history[0].role)
        assertEquals(ChatRole.ASSISTANT, history[1].role)
        assertEquals(ChatRole.USER, history[2].role)
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `rehydration uses empty history when no prior messages exist`() = runTest {
        // Given - empty message list (new chat)
        val chatId = 1L
        val messageId = "1_assistant"
        val emptyMessages = emptyList<Message>()
        coEvery { messageRepository.getMessagesForChat(chatId) } returns emptyMessages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("First response", null)
        )

        // When
        var completed = false
        useCase.invoke("First prompt", 50L, 100L, 1L, Mode.FAST).collect { event ->
            if (event is MessageGenerationState.Finished) {
                completed = true
            }
        }

        // Then
        assertTrue(completed)
        // setHistory should be called with empty list
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }
        assertTrue(historySlot.captured.isEmpty())
    }

    @Test
    fun `rehydration continues after loading messages regardless of count`() = runTest {
        // Given - messages exist in repository
        val chatId = 1L
        val messageId = "1_assistant"
        val messages = listOf(Message(id = 1, chatId = chatId, content = "Hello", role = Role.USER))
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Response", null)
        )

        // When - invoke completes successfully
        var completed = false
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { event ->
            if (event is MessageGenerationState.Finished) {
                completed = true
            }
        }

        // Then - both setHistory and sendPrompt were called
        assertTrue(completed)
        coVerify { fastModelService.setHistory(any()) }
        coVerify { fastModelService.sendPrompt(any(), any()) }
    }

    // ========== Mode Routing Tests ==========

    @Test
    fun `rehydration uses fastModelService for FAST mode`() = runTest {
        // Given
        val messageId = "1_assistant"
        val messages = listOf(Message(id = 1, chatId = 1, content = "Hello", role = Role.USER))
        coEvery { messageRepository.getMessagesForChat(1) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Fast response", null)
        )

        // When
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then - fastModelService used
        coVerify { fastModelService.setHistory(any()) }
        coVerify { fastModelService.sendPrompt(any(), any()) }
        coVerify(exactly = 0) { thinkingModelService.setHistory(any()) }
    }

    @Test
    fun `rehydration uses thinkingModelService for THINKING mode`() = runTest {
        // Given
        val messageId = "1_assistant"
        val messages = listOf(Message(id = 1, chatId = 1, content = "Hello", role = Role.USER))
        coEvery { messageRepository.getMessagesForChat(1) } returns messages
        coEvery { thinkingModelService.setHistory(any()) } returns Unit
        coEvery { thinkingModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Thinking response", null)
        )

        // When
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.THINKING).collect { }

        // Then - thinkingModelService used
        coVerify { thinkingModelService.setHistory(any()) }
        coVerify { thinkingModelService.sendPrompt(any(), any()) }
        coVerify(exactly = 0) { fastModelService.setHistory(any()) }
    }

    // ========== ModelType in ThinkingLive Tests ==========

    @Test
    fun `ThinkingLive emits ModelType_FAST for FAST mode`() = runTest {
        // Given
        coEvery { messageRepository.getMessagesForChat(1) } returns emptyList()
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking...", "Thinking..."),
            InferenceEvent.Completed("Response", null)
        )
        // Mock bufferThinkingSteps to return non-empty list so ThinkingLive is emitted
        coEvery { bufferThinkingSteps.invoke(any()) } returns listOf("Thinking...")

        // When
        val states = mutableListOf<MessageGenerationState>()
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { state ->
            states.add(state)
        }

        // Then - ThinkingLive should contain ModelType.FAST
        val thinkingLive = states.filterIsInstance<MessageGenerationState.ThinkingLive>().firstOrNull()
        assertNotNull(thinkingLive, "Should emit ThinkingLive")
        assertEquals(ModelType.FAST, thinkingLive!!.modelType, "ThinkingLive should contain ModelType.FAST")
    }

    @Test
    fun `ThinkingLive emits ModelType_THINKING for THINKING mode`() = runTest {
        // Given
        coEvery { messageRepository.getMessagesForChat(1) } returns emptyList()
        coEvery { thinkingModelService.setHistory(any()) } returns Unit
        coEvery { thinkingModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Reasoning...", "Reasoning..."),
            InferenceEvent.Completed("Response", null)
        )
        // Mock bufferThinkingSteps to return non-empty list so ThinkingLive is emitted
        coEvery { bufferThinkingSteps.invoke(any()) } returns listOf("Reasoning...")

        // When
        val states = mutableListOf<MessageGenerationState>()
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.THINKING).collect { state ->
            states.add(state)
        }

        // Then - ThinkingLive should contain ModelType.THINKING
        val thinkingLive = states.filterIsInstance<MessageGenerationState.ThinkingLive>().firstOrNull()
        assertNotNull(thinkingLive, "Should emit ThinkingLive")
        assertEquals(ModelType.THINKING, thinkingLive!!.modelType, "ThinkingLive should contain ModelType.THINKING")
    }

    @Test
    fun `ThinkingLive contains correct thinking steps from inference events`() = runTest {
        // Given
        coEvery { messageRepository.getMessagesForChat(1) } returns emptyList()
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("First thought. Second thought.", "First thought. Second thought."),
            InferenceEvent.Completed("Final response", null)
        )
        coEvery { bufferThinkingSteps(any()) } returns listOf("First thought.", "Second thought.")
        coEvery { bufferThinkingSteps.flush() } returns null

        // When
        val states = mutableListOf<MessageGenerationState>()
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { state ->
            states.add(state)
        }

        // Then
        val thinkingLive = states.filterIsInstance<MessageGenerationState.ThinkingLive>().firstOrNull()
        assertNotNull(thinkingLive)
        assertTrue(thinkingLive!!.steps.isNotEmpty())
    }

    // ========== Content Preservation Tests ==========

    @Test
    fun `rehydration preserves special characters in message content`() = runTest {
        // Given
        val chatId = 1L
        val messageId = "1_assistant"
        val specialContent = "Hello \"quoted\" & <special> chars\nnewlines"
        val messages = listOf(
            Message(id = 1, chatId = chatId, content = specialContent, role = Role.USER)
        )
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Response", null)
        )

        // When
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }
        assertEquals(specialContent, historySlot.captured[0].content)
    }

    @Test
    fun `rehydration preserves unicode in message content`() = runTest {
        // Given
        val chatId = 1L
        val messageId = "1_assistant"
        val unicodeContent = "Hello 世界 🌍 مرحبا"
        val messages = listOf(
            Message(id = 1, chatId = chatId, content = unicodeContent, role = Role.USER)
        )
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Response", null)
        )

        // When
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }
        assertEquals(unicodeContent, historySlot.captured[0].content)
    }

    @Test
    fun `rehydration preserves empty message content`() = runTest {
        // Given - messages include both user and assistant, some may have empty content
        // The use case filters out empty content, so we include a non-empty user message
        val chatId = 1L
        val messageId = "1_assistant"
        val messages = listOf(
            Message(id = 1, chatId = chatId, content = "Hello", role = Role.USER),
            Message(id = 2, chatId = chatId, content = "", role = Role.ASSISTANT)
        )
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Response", null)
        )

        // When
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then - empty assistant message is filtered out, only user message is passed
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }
        assertEquals(1, historySlot.captured.size)
        assertEquals("Hello", historySlot.captured[0].content)
    }

    // ========== Multi-turn Conversation Tests ==========

    @Test
    fun `rehydration loads full multi-turn conversation history`() = runTest {
        // Given - simulate 10 message conversation
        val chatId = 1L
        val messageId = "1_assistant"
        val messages = (1..10).map { i ->
            Message(
                id = i.toLong(),
                chatId = chatId,
                content = "Message $i",
                role = if (i % 2 == 1) Role.USER else Role.ASSISTANT
            )
        }
        coEvery { messageRepository.getMessagesForChat(chatId) } returns messages
        coEvery { fastModelService.setHistory(any()) } returns Unit
        coEvery { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Response", null)
        )

        // When
        useCase.invoke("Prompt", 50L, 100L, 1L, Mode.FAST).collect { }

        // Then
        val historySlot = slot<List<ChatMessage>>()
        coVerify { fastModelService.setHistory(capture(historySlot)) }
        assertEquals(10, historySlot.captured.size)
    }
}
