package com.browntowndev.pocketcrew.feature.inference.llama

import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach

class LlamaChatSessionManagerTest {

    private lateinit var engine: LlamaEnginePort
    private lateinit var sessionManager: LlamaChatSessionManager

    @BeforeEach
    fun setup() {
        engine = mockk()
        sessionManager = LlamaChatSessionManager(engine)
    }

    @org.junit.jupiter.api.Test
    fun `initializeEngine calls engine initialize`() = runTest {
        // Given
        val config = mockk<com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig>()
        io.mockk.coEvery { engine.initialize(config) } returns Unit

        // When
        sessionManager.initializeEngine(config)

        // Then
        io.mockk.coVerify { engine.initialize(config) }
    }

    @org.junit.jupiter.api.Test
    fun `startNewConversation calls engine startConversation`() = runTest {
        // Given
        val systemPrompt = "Test prompt"
        io.mockk.coEvery { engine.startConversation(systemPrompt) } returns Unit

        // When
        sessionManager.startNewConversation(systemPrompt)

        // Then
        io.mockk.coVerify { engine.startConversation(systemPrompt) }
    }

    @org.junit.jupiter.api.Test
    fun `startNewConversation without prompt calls engine startConversation with null`() = runTest {
        // Given
        io.mockk.coEvery { engine.startConversation(null) } returns Unit

        // When
        sessionManager.startNewConversation()

        // Then
        io.mockk.coVerify { engine.startConversation(null) }
    }

    @org.junit.jupiter.api.Test
    fun `sendUserMessage creates DomainChatMessage and appends to engine`() = runTest {
        // Given
        val text = "Hello"
        val expectedMessage = com.browntowndev.pocketcrew.domain.model.chat.ChatMessage(
            role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER,
            content = text
        )
        io.mockk.coEvery { engine.appendMessage(expectedMessage) } returns Unit

        // When
        sessionManager.sendUserMessage(text)

        // Then
        io.mockk.coVerify { engine.appendMessage(expectedMessage) }
    }

    @org.junit.jupiter.api.Test
    fun `setHistory calls engine setHistory`() = runTest {
        // Given
        val history = listOf(
            com.browntowndev.pocketcrew.domain.model.chat.ChatMessage(com.browntowndev.pocketcrew.domain.model.chat.Role.USER, "Hi"),
            com.browntowndev.pocketcrew.domain.model.chat.ChatMessage(com.browntowndev.pocketcrew.domain.model.chat.Role.ASSISTANT, "Hello!")
        )
        io.mockk.coEvery { engine.setHistory(history) } returns Unit

        // When
        sessionManager.setHistory(history)

        // Then
        io.mockk.coVerify { engine.setHistory(history) }
    }

    @org.junit.jupiter.api.Test
    fun `streamAssistantResponse returns flow from engine`() {
        // Given
        val expectedFlow = kotlinx.coroutines.flow.flowOf<com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent>()
        io.mockk.every { engine.generate() } returns expectedFlow

        // When
        val result = sessionManager.streamAssistantResponse()

        // Then
        org.junit.jupiter.api.Assertions.assertEquals(expectedFlow, result)
        io.mockk.verify { engine.generate() }
    }

    @org.junit.jupiter.api.Test
    fun `stopCurrentGeneration calls engine stopGeneration`() = runTest {
        // Given
        io.mockk.coEvery { engine.stopGeneration() } returns Unit

        // When
        sessionManager.stopCurrentGeneration()

        // Then
        io.mockk.coVerify { engine.stopGeneration() }
    }

    @org.junit.jupiter.api.Test
    fun `clearConversation calls engine resetConversation`() = runTest {
        // Given
        val systemPrompt = "New system prompt"
        io.mockk.coEvery { engine.resetConversation(systemPrompt) } returns Unit

        // When
        sessionManager.clearConversation(systemPrompt)

        // Then
        io.mockk.coVerify { engine.resetConversation(systemPrompt) }
    }

    @org.junit.jupiter.api.Test
    fun `clearConversation without prompt calls engine resetConversation with null`() = runTest {
        // Given
        io.mockk.coEvery { engine.resetConversation(null) } returns Unit

        // When
        sessionManager.clearConversation()

        // Then
        io.mockk.coVerify { engine.resetConversation(null) }
    }

    @org.junit.jupiter.api.Test
    fun `shutdown calls engine unload`() = runTest {
        // Given
        io.mockk.coEvery { engine.unload() } returns Unit

        // When
        sessionManager.shutdown()

        // Then
        io.mockk.coVerify { engine.unload() }
    }
}
