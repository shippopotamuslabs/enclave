package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaPipeInferenceServiceImplTest {

    private lateinit var mockLlmInferenceWrapper: LlmInferenceWrapper
    private lateinit var mockSession: LlmSessionPort
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var processThinkingTokens: ProcessThinkingTokensUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        mockkStatic(LlmInferenceSession.LlmInferenceSessionOptions::class)

        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockLlmInferenceWrapper = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        processThinkingTokens = ProcessThinkingTokensUseCase()

        // Mock session creation via wrapper
        val mockBuilder = mockk<LlmInferenceSession.LlmInferenceSessionOptions.Builder>(relaxed = true)
        every { LlmInferenceSession.LlmInferenceSessionOptions.builder() } returns mockBuilder
        every { mockBuilder.setTopK(any()) } returns mockBuilder
        every { mockBuilder.setTopP(any()) } returns mockBuilder
        every { mockBuilder.setTemperature(any()) } returns mockBuilder
        val mockOptions = mockk<LlmInferenceSession.LlmInferenceSessionOptions>(relaxed = true)
        every { mockBuilder.build() } returns mockOptions
        
        every { mockLlmInferenceWrapper.createSession(any()) } returns mockSession
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(LlmInferenceSession.LlmInferenceSessionOptions::class)
    }

    @Test
    fun `sendPrompt seeds history via sequential addQueryChunk calls before first prompt`() = runTest {
        // Given
        val service = MediaPipeInferenceServiceImpl(mockLlmInferenceWrapper, ModelType.FAST, mockModelRegistry, processThinkingTokens)
        val history = listOf(
            ChatMessage(Role.USER, "What is AI?"),
            ChatMessage(Role.ASSISTANT, "AI is...")
        )
        
        // Mock generateResponseAsync to complete immediately
        val mockFuture = mockk<ListenableFuture<String>>(relaxed = true)
        every { mockSession.generateResponseAsync(any()) } answers {
            val callback = it.invocation.args[0] as ProgressListener<String>
            callback.run("Response", true)
            mockFuture
        }

        // When
        service.setHistory(history)
        service.sendPrompt("Explain it simpler", closeConversation = false).toList()

        // Then
        verifyOrder {
            mockSession.addQueryChunk("User: What is AI?\n")
            mockSession.addQueryChunk("Assistant: AI is...\n")
            mockSession.addQueryChunk("Explain it simpler")
            mockSession.generateResponseAsync(any())
        }
    }

    @Test
    fun `sendPrompt does not re-seed history if session persists`() = runTest {
        // Given
        val service = MediaPipeInferenceServiceImpl(mockLlmInferenceWrapper, ModelType.FAST, mockModelRegistry, processThinkingTokens)
        val history = listOf(ChatMessage(Role.USER, "What is AI?"))
        
        val mockFuture = mockk<ListenableFuture<String>>(relaxed = true)
        every { mockSession.generateResponseAsync(any()) } answers {
            val callback = it.invocation.args[0] as ProgressListener<String>
            callback.run("Response", true)
            mockFuture
        }

        // When
        service.setHistory(history)
        service.sendPrompt("Prompt 1", closeConversation = false).toList()
        service.sendPrompt("Prompt 2", closeConversation = false).toList()

        // Then
        verify(exactly = 1) { mockSession.addQueryChunk("User: What is AI?\n") }
        verify(exactly = 1) { mockSession.addQueryChunk("Prompt 1") }
        verify(exactly = 1) { mockSession.addQueryChunk("Prompt 2") }
    }

    @Test
    fun `sendPrompt re-seeds history if conversation was closed`() = runTest {
        // Given
        val service = MediaPipeInferenceServiceImpl(mockLlmInferenceWrapper, ModelType.FAST, mockModelRegistry, processThinkingTokens)
        val history = listOf(ChatMessage(Role.USER, "What is AI?"))
        
        val mockFuture = mockk<ListenableFuture<String>>(relaxed = true)
        every { mockSession.generateResponseAsync(any()) } answers {
            val callback = it.invocation.args[0] as ProgressListener<String>
            callback.run("Response", true)
            mockFuture
        }

        // When
        service.setHistory(history)
        service.sendPrompt("Prompt 1", closeConversation = true).toList()
        service.sendPrompt("Prompt 2", closeConversation = false).toList()

        // Then
        verify(exactly = 2) { mockSession.addQueryChunk("User: What is AI?\n") }
    }

    @Test
    fun `sendPrompt with empty history does not call addQueryChunk for history`() = runTest {
        // Given
        val service = MediaPipeInferenceServiceImpl(mockLlmInferenceWrapper, ModelType.FAST, mockModelRegistry, processThinkingTokens)
        val history = emptyList<ChatMessage>()
        
        val mockFuture = mockk<ListenableFuture<String>>(relaxed = true)
        every { mockSession.generateResponseAsync(any()) } answers {
            val callback = it.invocation.args[0] as ProgressListener<String>
            callback.run("Response", true)
            mockFuture
        }

        // When
        service.setHistory(history)
        service.sendPrompt("Hello", closeConversation = false).toList()

        // Then
        verify(exactly = 1) { mockSession.addQueryChunk("Hello") }
        verify(exactly = 0) { mockSession.addQueryChunk(match { it.contains("User:") || it.contains("Assistant:") || it.contains("System:") }) }
    }

    @Test
    fun `error during session creation emits Error event`() = runTest {
        // Given
        val service = MediaPipeInferenceServiceImpl(mockLlmInferenceWrapper, ModelType.FAST, mockModelRegistry, processThinkingTokens)
        every { mockLlmInferenceWrapper.createSession(any()) } throws RuntimeException("Session creation failed")

        // When
        val events = service.sendPrompt("Hello", closeConversation = false).toList()

        // Then
        assertTrue(events.any { it is InferenceEvent.Error })
    }

    @Test
    fun `sendPrompt correctly identifies and emits thinking events from tags`() = runTest {
        // Given
        val service = MediaPipeInferenceServiceImpl(mockLlmInferenceWrapper, ModelType.FAST, mockModelRegistry, processThinkingTokens)
        val mockFuture = mockk<ListenableFuture<String>>(relaxed = true)
        
        every { mockSession.generateResponseAsync(any()) } answers {
            val callback = it.invocation.args[0] as ProgressListener<String>
            callback.run("<think>", false)
            callback.run("reasoning", false)
            callback.run("</think>", false)
            callback.run("final answer", true)
            mockFuture
        }

        // When
        val events = service.sendPrompt("Hello", closeConversation = false).toList()

        // Then
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "reasoning" })
        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "final answer" })
        assertTrue(events.any { it is InferenceEvent.Finished })
    }

    @Test
    fun `sendPrompt recreates session when options change`() = runTest {
        // Given
        val service = MediaPipeInferenceServiceImpl(mockLlmInferenceWrapper, ModelType.FAST, mockModelRegistry, processThinkingTokens)
        val mockFuture = mockk<ListenableFuture<String>>(relaxed = true)
        every { mockSession.generateResponseAsync(any()) } answers {
            val callback = it.invocation.args[0] as ProgressListener<String>
            callback.run("Response", true)
            mockFuture
        }

        val options1 = GenerationOptions(reasoningBudget = 0, temperature = 0.7f)
        val options2 = GenerationOptions(reasoningBudget = 2048, temperature = 0.7f)

        // When
        service.sendPrompt("Prompt 1", options1, closeConversation = false).toList()
        service.sendPrompt("Prompt 2", options2, closeConversation = false).toList()

        // Then
        verify(exactly = 2) { mockLlmInferenceWrapper.createSession(any()) }
    }
}
