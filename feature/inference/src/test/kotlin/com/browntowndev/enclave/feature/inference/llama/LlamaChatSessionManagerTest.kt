package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationEvent
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.feature.inference.FakeLlamaEnginePort
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LlamaChatSessionManagerTest {
    private lateinit var engine: FakeLlamaEnginePort
    private lateinit var sessionManager: LlamaChatSessionManager

    @BeforeEach
    fun setup() {
        engine = FakeLlamaEnginePort()
        sessionManager = LlamaChatSessionManager(engine)
    }

    @Test
    fun `initializeEngine calls engine initialize`() =
        runTest {
            // Given
            val config = LlamaModelConfig(modelPath = "test", systemPrompt = "test")

            // When
            sessionManager.initializeEngine(config)

            // Then
            assertEquals(listOf(config), engine.initializeCalls)
        }

    @Test
    fun `startNewConversation calls engine startConversation`() =
        runTest {
            // Given
            val systemPrompt = "Test prompt"

            // When
            sessionManager.startNewConversation(systemPrompt)

            // Then
            assertEquals(listOf(systemPrompt), engine.startConversationCalls)
        }

    @Test
    fun `startNewConversation without prompt calls engine startConversation with null`() =
        runTest {
            // When
            sessionManager.startNewConversation()

            // Then
            assertEquals(listOf<String?>(null), engine.startConversationCalls)
        }

    @Test
    fun `sendUserMessage creates DomainChatMessage and appends to engine`() =
        runTest {
            // Given
            val text = "Hello"

            // When
            sessionManager.sendUserMessage(text)

            // Then
            val appended = engine.appendedMessages.single()
            assertEquals(text, appended.content)
            assertEquals(Role.USER, appended.role)
        }

    @Test
    fun `setHistory calls engine setHistory`() =
        runTest {
            // Given
            val history =
                listOf(
                    ChatMessage(Role.USER, "Hi"),
                    ChatMessage(Role.ASSISTANT, "Hello!"),
                )

            // When
            sessionManager.setHistory(history)

            // Then
            assertEquals(listOf(history), engine.setHistoryCalls)
        }

    @Test
    fun `streamAssistantResponse returns flow from engine`() {
        // Given
        val expectedFlow = flowOf<GenerationEvent>()
        engine.generateFlow = expectedFlow

        // When
        val result = sessionManager.streamAssistantResponse()

        // Then
        assertEquals(expectedFlow, result)
        assertEquals(1, engine.generateCallCount)
    }

    @Test
    fun `streamAssistantResponseWithOptions forwards continuation metadata`() {
        val options = GenerationOptions(reasoningBudget = 0)
        val expectedFlow = flowOf<GenerationEvent>()
        engine.generateWithOptionsResult = expectedFlow

        val result =
            sessionManager.streamAssistantResponseWithOptions(
                options = options,
                continuation = true,
                prevMessageCount = 4,
            )

        assertEquals(expectedFlow, result)
        assertEquals(
            listOf(FakeLlamaEnginePort.GenerateWithOptionsCall(options, true, 4)),
            engine.generateWithOptionsCalls,
        )
    }

    @Test
    fun `stopCurrentGeneration calls engine stopGeneration`() =
        runTest {
            // When
            sessionManager.stopCurrentGeneration()

            // Then
            assertEquals(1, engine.stopGenerationCount)
        }

    @Test
    fun `clearConversation calls engine resetConversation`() =
        runTest {
            // Given
            val systemPrompt = "New system prompt"

            // When
            sessionManager.clearConversation(systemPrompt)

            // Then
            assertEquals(listOf(systemPrompt), engine.resetConversationCalls)
        }

    @Test
    fun `clearConversation without prompt calls engine resetConversation with null`() =
        runTest {
            // When
            sessionManager.clearConversation()

            // Then
            assertEquals(listOf<String?>(null), engine.resetConversationCalls)
        }

    @Test
    fun `shutdown calls engine unload`() =
        runTest {
            // When
            sessionManager.shutdown()

            // Then
            assertEquals(1, engine.unloadCount)
        }
}
