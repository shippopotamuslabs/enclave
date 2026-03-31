package com.browntowndev.pocketcrew.feature.inference.llama

import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationEvent
import com.browntowndev.pocketcrew.domain.model.inference.LlamaModelConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify

import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlamaChatSessionManagerTest {

    private lateinit var engine: LlamaEnginePort
    private lateinit var sessionManager: LlamaChatSessionManager

    @BeforeEach
    fun setup() {
        engine = mockk()
        sessionManager = LlamaChatSessionManager(engine)
    }

    @Test
    fun `initializeEngine calls engine initialize`() = runTest {
        // Given
        val config = mockk<LlamaModelConfig>()
        coEvery { engine.initialize(config) } returns Unit

        // When
        sessionManager.initializeEngine(config)

        // Then
        coVerify { engine.initialize(config) }
    }

    @Test
    fun `startNewConversation calls engine startConversation`() = runTest {
        // Given
        val systemPrompt = "Test prompt"
        coEvery { engine.startConversation(systemPrompt) } returns Unit

        // When
        sessionManager.startNewConversation(systemPrompt)

        // Then
        coVerify { engine.startConversation(systemPrompt) }
    }

    @Test
    fun `startNewConversation without prompt calls engine startConversation with null`() = runTest {
        // Given
        coEvery { engine.startConversation(null) } returns Unit

        // When
        sessionManager.startNewConversation()

        // Then
        coVerify { engine.startConversation(null) }
    }

    @Test
    fun `sendUserMessage creates DomainChatMessage and appends to engine`() = runTest {
        // Given
        val text = "Hello"

        coEvery { engine.appendMessage(any()) } returns Unit

        // When
        sessionManager.sendUserMessage(text)

        // Then
        coVerify {
            engine.appendMessage(match {
                it.content == text && it.role == Role.USER
            })
        }
    }

    @Test
    fun `setHistory calls engine setHistory`() = runTest {
        // Given
        val history = listOf(
            ChatMessage(Role.USER, "Hi"),
            ChatMessage(Role.ASSISTANT, "Hello!")
        )
        coEvery { engine.setHistory(history) } returns Unit

        // When
        sessionManager.setHistory(history)

        // Then
        coVerify { engine.setHistory(history) }
    }

    @Test
    fun `streamAssistantResponse returns flow from engine`() {
        // Given
        val expectedFlow = flowOf<GenerationEvent>()
        every { engine.generate() } returns expectedFlow

        // When
        val result = sessionManager.streamAssistantResponse()

        // Then
        assertEquals(expectedFlow, result)
        verify { engine.generate() }
    }

    @Test
    fun `stopCurrentGeneration calls engine stopGeneration`() = runTest {
        // Given
        coEvery { engine.stopGeneration() } returns Unit

        // When
        sessionManager.stopCurrentGeneration()

        // Then
        coVerify { engine.stopGeneration() }
    }

    @Test
    fun `clearConversation calls engine resetConversation`() = runTest {
        // Given
        val systemPrompt = "New system prompt"
        coEvery { engine.resetConversation(systemPrompt) } returns Unit

        // When
        sessionManager.clearConversation(systemPrompt)

        // Then
        coVerify { engine.resetConversation(systemPrompt) }
    }

    @Test
    fun `clearConversation without prompt calls engine resetConversation with null`() = runTest {
        // Given
        coEvery { engine.resetConversation(null) } returns Unit

        // When
        sessionManager.clearConversation()

        // Then
        coVerify { engine.resetConversation(null) }
    }

    @Test
    fun `shutdown calls engine unload`() = runTest {
        // Given
        coEvery { engine.unload() } returns Unit

        // When
        sessionManager.shutdown()

        // Then
        coVerify { engine.unload() }
    }
}
