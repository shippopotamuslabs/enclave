package com.browntowndev.pocketcrew.domain.usecase.chat

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.AgentRole
import com.browntowndev.pocketcrew.domain.port.inference.ComplexityLevel
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.HeuristicPromptComplexityInterpreter
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.port.inference.PipelinePhase
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

    private lateinit var mockComplexityInterpreter: HeuristicPromptComplexityInterpreter
    private lateinit var mockThinkingPipelineOrchestrator: EnginePipelineOrchestrator
    private lateinit var mockChatRepository: ChatRepository

    private lateinit var useCase: GenerateChatResponseUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        mockComplexityInterpreter = mockk()
        mockThinkingPipelineOrchestrator = mockk(relaxed = true)
        mockChatRepository = mockk(relaxed = true)

        useCase = GenerateChatResponseUseCase(
            complexityInterpreter = mockComplexityInterpreter,
            thinkingPipelineOrchestrator = mockThinkingPipelineOrchestrator,
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
        // Given - complexity is SIMPLE so processSimplePrompt is called
        every { mockComplexityInterpreter.analyze(any()) } returns ComplexityLevel.SIMPLE

        every { mockThinkingPipelineOrchestrator.processSimplePrompt(any()) } returns flow {
            emit(PipelineEvent.Completed(
                finalResponse = "response",
                allThinkingSteps = emptyList(),
                pipelineDurationSeconds = 1
            ))
        }

        val result = useCase.invoke("test prompt", "message-id")

        // Just verify it doesn't throw
        result.collect { }
    }

    @Test
    fun `invoke emits thinking live when requires reasoning`() = runTest {
        // Given - complexity check returns COMPLEX (requires reasoning)
        every { mockComplexityInterpreter.analyze(any()) } returns ComplexityLevel.COMPLEX

        // Set up the thinking pipeline to emit pipeline events
        every { mockThinkingPipelineOrchestrator.processComplexPrompt(any()) } returns flow {
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
        // Given - complexity is SIMPLE (no reasoning needed)
        every { mockComplexityInterpreter.analyze(any()) } returns ComplexityLevel.SIMPLE

        // Simple response via processSimplePrompt
        every { mockThinkingPipelineOrchestrator.processSimplePrompt(any()) } returns flow {
            emit(PipelineEvent.TextChunk(
                agent = AgentRole.FAST_MODEL,
                chunk = "Hello ",
                accumulatedText = ""
            ))
            emit(PipelineEvent.TextChunk(
                agent = AgentRole.FAST_MODEL,
                chunk = "World",
                accumulatedText = "Hello "
            ))
            emit(PipelineEvent.Completed(
                finalResponse = "Hello World",
                allThinkingSteps = emptyList(),
                pipelineDurationSeconds = 1
            ))
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
        // Given - complexity is SIMPLE so it goes to simple path
        every { mockComplexityInterpreter.analyze(any()) } returns ComplexityLevel.SIMPLE

        // Safety blocks the thinking via orchestrator
        every { mockThinkingPipelineOrchestrator.processSimplePrompt(any()) } returns flow {
            emit(PipelineEvent.SafetyIntervention(
                reason = "Test safety reason",
                agent = AgentRole.WATCHDOG
            ))
        }

        // When
        val result = useCase.invoke("test prompt", "msg-id")
        val states = mutableListOf<MessageGenerationState>()
        result.collect { states.add(it) }

        // Then
        assertTrue(states.any { it is MessageGenerationState.Blocked })
    }

    @Test
    fun `invoke handles inference error gracefully`() = runTest {
        // Given - complexity check returns COMPLEX so it goes to reasoning path
        every { mockComplexityInterpreter.analyze(any()) } returns ComplexityLevel.COMPLEX

        // Error in pipeline
        every { mockThinkingPipelineOrchestrator.processComplexPrompt(any()) } returns flow {
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

        // Then - should emit Failed state
        assertTrue(states.any { it is MessageGenerationState.Failed })
    }
}
