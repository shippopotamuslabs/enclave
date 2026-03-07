package com.browntowndev.pocketcrew.domain.usecase.chat

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.AgentRole
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.port.inference.PipelinePhase
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GenerateChatResponseUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockFastModelServiceProvider: Provider<LlmInferencePort>
    private lateinit var mockThinkingPipelineOrchestrator: EnginePipelineOrchestrator
    private lateinit var mockSafetyProbe: SafetyProbe
    private lateinit var mockChatRepository: ChatRepository

    private lateinit var useCase: GenerateChatResponseUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        mockFastModelServiceProvider = mockk()
        mockThinkingPipelineOrchestrator = mockk(relaxed = true)
        mockSafetyProbe = mockk(relaxed = true)
        mockChatRepository = mockk(relaxed = true)

        useCase = GenerateChatResponseUseCase(
            fastModelServiceProvider = mockFastModelServiceProvider,
            thinkingPipelineOrchestrator = mockThinkingPipelineOrchestrator,
            safetyProbe = mockSafetyProbe,
            chatRepository = mockChatRepository
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `invoke returns flow of MessageGenerationState`() = runTest {
        val mockService = mockk<LlmInferencePort>(relaxed = true)
        every { mockFastModelServiceProvider.get() } returns mockService

        coEvery { mockService.sendPrompt(any(), closeConversation = any()) } returns flow {
            // Empty flow for test
        }

        val result = useCase.invoke("test prompt", "message-id")

        // Just verify it doesn't throw
        result.collect { }
    }

    @Test
    fun `invoke emits thinking live when requires reasoning`() = runTest {
        // Given - complexity check returns true (requires reasoning)
        val mockService = mockk<LlmInferencePort>(relaxed = true)
        every { mockFastModelServiceProvider.get() } returns mockService

        // First call: complexity check returns true
        coEvery { mockService.sendPrompt(any(), closeConversation = true) } returns flow {
            emit(InferenceEvent.Completed("""{"requires_reasoning": true, "reason": "test"}""", null))
        }
        // Second call: reasoning pipeline - return thinking event
        coEvery { mockService.sendPrompt(any(), closeConversation = false) } returns flow {
            emit(InferenceEvent.Thinking("step1", "step1"))
        }

        // Set up the thinking pipeline to emit pipeline events
        every { mockThinkingPipelineOrchestrator.processPrompt(any()) } returns flow {
            emit(PipelineEvent.PhaseUpdate(
                phase = PipelinePhase.DRAFTING,
                activeAgent = AgentRole.DRAFTER_ONE
            ))
            emit(PipelineEvent.ReasoningChunk(
                agent = AgentRole.DRAFTER_ONE,
                chunk = "step1",
                accumulatedThought = "step1"
            ))
            emit(PipelineEvent.Completed(
                finalResponse = "Final response",
                allThinkingSteps = listOf("step1"),
                pipelineDurationSeconds = 1
            ))
        }

        // When
        val result = useCase.invoke("complex prompt", "msg-id")
        val states = mutableListOf<MessageGenerationState>()
        result.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is MessageGenerationState.ThinkingLive })
    }

    @Test
    fun `invoke emits generating text for simple prompt`() = runTest {
        // Given
        val mockService = mockk<LlmInferencePort>(relaxed = true)
        every { mockFastModelServiceProvider.get() } returns mockService

        // Complexity check returns false (no reasoning needed)
        coEvery { mockService.sendPrompt(any(), closeConversation = true) } returns flow {
            emit(InferenceEvent.Completed("""{"requires_reasoning": false}""", null))
        }
        // Simple response
        coEvery { mockService.sendPrompt(any(), closeConversation = any()) } returns flow {
            emit(InferenceEvent.PartialResponse("Hello "))
            emit(InferenceEvent.PartialResponse("World"))
            emit(InferenceEvent.Completed("Hello World", null))
        }

        // When
        val result = useCase.invoke("hello", "msg-id")
        val states = mutableListOf<MessageGenerationState>()
        result.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is MessageGenerationState.GeneratingText })
    }

    @Test
    fun `invoke emits blocked when safety blocks content`() = runTest {
        // Given
        val mockService = mockk<LlmInferencePort>(relaxed = true)
        every { mockFastModelServiceProvider.get() } returns mockService

        // Complexity returns false - goes to simple path with safety check
        coEvery { mockService.sendPrompt(any(), closeConversation = true) } returns flow {
            emit(InferenceEvent.Completed("""{"requires_reasoning": false}""", null))
        }

        // Safety blocks the thinking
        coEvery { mockService.sendPrompt(any(), closeConversation = any()) } returns flow {
            emit(InferenceEvent.Thinking("harmful content", "harmful content"))
        }

        // Setup safety probe to return false
        every { mockSafetyProbe.isSafe(any()) } returns false

        // When
        val result = useCase.invoke("test prompt", "msg-id")
        val states = mutableListOf<MessageGenerationState>()
        result.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is MessageGenerationState.Blocked })
    }

    @Test
    fun `invoke handles inference error gracefully`() = runTest {
        // Given
        val mockService = mockk<LlmInferencePort>(relaxed = true)
        every { mockFastModelServiceProvider.get() } returns mockService

        // Error during complexity check - defaults to reasoning for safety
        coEvery { mockService.sendPrompt(any(), closeConversation = true) } returns flow {
            emit(InferenceEvent.Error(RuntimeException("Network error")))
        }

        // When error happens, it defaults to reasoning mode - need to mock the pipeline
        every { mockThinkingPipelineOrchestrator.processPrompt(any()) } returns flow {
            emit(PipelineEvent.PhaseUpdate(
                phase = PipelinePhase.DRAFTING,
                activeAgent = AgentRole.DRAFTER_ONE
            ))
            emit(PipelineEvent.Error(RuntimeException("Pipeline error")))
        }

        // When
        val result = useCase.invoke("test prompt", "msg-id")
        val states = mutableListOf<MessageGenerationState>()
        result.collect { states.add(it) }

        // Then - should either fail or try reasoning (safety first)
        assertTrue(states.any { it is MessageGenerationState.Failed || it is MessageGenerationState.ThinkingLive })
    }

    @Test
    fun `invoke closes session in finally block`() = runTest {
        // Given
        val mockService = mockk<LlmInferencePort>(relaxed = true)
        every { mockFastModelServiceProvider.get() } returns mockService

        coEvery { mockService.sendPrompt(any(), closeConversation = any()) } returns flow {
            emit(InferenceEvent.Completed("response", null))
        }

        // When
        val result = useCase.invoke("test", "msg-id")
        result.collect { }

        // Then - session should be closed
        verify { mockService.closeSession() }
    }
}
