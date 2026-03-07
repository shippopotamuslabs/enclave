package com.browntowndev.pocketcrew.domain.inference

import com.browntowndev.pocketcrew.domain.port.inference.AgentRole
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.port.inference.PipelinePhase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PipelineEventTest {

    @Nested
    inner class PhaseUpdateTests {
        @Test
        fun `PhaseUpdate with default agent creates correct event`() {
            val phase = PipelinePhase.DRAFTING
            val event = PipelineEvent.PhaseUpdate(phase)

            assertEquals(phase, event.phase)
            assertEquals(AgentRole.SYSTEM, event.activeAgent)
        }

        @Test
        fun `PhaseUpdate with custom agent creates correct event`() {
            val phase = PipelinePhase.SYNTHESIS
            val agent = AgentRole.SYNTHESIZER_ONE
            val event = PipelineEvent.PhaseUpdate(phase, agent)

            assertEquals(phase, event.phase)
            assertEquals(agent, event.activeAgent)
        }

        @Test
        fun `PhaseUpdate equality works correctly`() {
            val event1 = PipelineEvent.PhaseUpdate(PipelinePhase.DRAFTING, AgentRole.DRAFTER_ONE)
            val event2 = PipelineEvent.PhaseUpdate(PipelinePhase.DRAFTING, AgentRole.DRAFTER_ONE)
            val event3 = PipelineEvent.PhaseUpdate(PipelinePhase.SYNTHESIS, AgentRole.DRAFTER_ONE)

            assertEquals(event1, event2)
            assertNotEquals(event1, event3)
        }

        @Test
        fun `PhaseUpdate toString contains all properties`() {
            val event = PipelineEvent.PhaseUpdate(PipelinePhase.REFINEMENT, AgentRole.FINAL_THINKER)
            val str = event.toString()

            assertTrue(str.contains("PhaseUpdate"))
            assertTrue(str.contains("REFINEMENT"))
            assertTrue(str.contains("FINAL_THINKER"))
        }
    }

    @Nested
    inner class ReasoningChunkTests {
        @Test
        fun `ReasoningChunk creates correct event`() {
            val agent = AgentRole.DRAFTER_ONE
            val chunk = "Analyzing the query"
            val accumulatedThought = "The user is asking about AI"
            val event = PipelineEvent.ReasoningChunk(agent, chunk, accumulatedThought)

            assertEquals(agent, event.agent)
            assertEquals(chunk, event.chunk)
            assertEquals(accumulatedThought, event.accumulatedThought)
        }

        @Test
        fun `ReasoningChunk equality works correctly`() {
            val event1 = PipelineEvent.ReasoningChunk(AgentRole.DRAFTER_TWO, "test", "thought")
            val event2 = PipelineEvent.ReasoningChunk(AgentRole.DRAFTER_TWO, "test", "thought")
            val event3 = PipelineEvent.ReasoningChunk(AgentRole.DRAFTER_TWO, "different", "thought")

            assertEquals(event1, event2)
            assertNotEquals(event1, event3)
        }

        @Test
        fun `ReasoningChunk toString contains all properties`() {
            val event = PipelineEvent.ReasoningChunk(AgentRole.SYNTHESIZER_ONE, "chunk", "accumulated")
            val str = event.toString()

            assertTrue(str.contains("ReasoningChunk"))
            assertTrue(str.contains("chunk"))
            assertTrue(str.contains("accumulated"))
        }
    }

    @Nested
    inner class TextChunkTests {
        @Test
        fun `TextChunk creates correct event`() {
            val agent = AgentRole.FINAL_THINKER
            val chunk = "Hello"
            val accumulatedText = "Hello wor"
            val event = PipelineEvent.TextChunk(agent, chunk, accumulatedText)

            assertEquals(agent, event.agent)
            assertEquals(chunk, event.chunk)
            assertEquals(accumulatedText, event.accumulatedText)
        }

        @Test
        fun `TextChunk equality works correctly`() {
            val event1 = PipelineEvent.TextChunk(AgentRole.WATCHDOG, "hi", "hi")
            val event2 = PipelineEvent.TextChunk(AgentRole.WATCHDOG, "hi", "hi")
            val event3 = PipelineEvent.TextChunk(AgentRole.WATCHDOG, "bye", "hibye")

            assertEquals(event1, event2)
            assertNotEquals(event1, event3)
        }

        @Test
        fun `TextChunk toString contains all properties`() {
            val event = PipelineEvent.TextChunk(AgentRole.SYSTEM, "text", "full text")
            val str = event.toString()

            assertTrue(str.contains("TextChunk"))
            assertTrue(str.contains("text"))
            assertTrue(str.contains("full text"))
        }
    }

    @Nested
    inner class SafetyInterventionTests {
        @Test
        fun `SafetyIntervention with default agent creates correct event`() {
            val reason = "Content violates safety policy"
            val event = PipelineEvent.SafetyIntervention(reason)

            assertEquals(reason, event.reason)
            assertEquals(AgentRole.WATCHDOG, event.agent)
        }

        @Test
        fun `SafetyIntervention with custom agent creates correct event`() {
            val reason = "Policy violation detected"
            val agent = AgentRole.SYSTEM
            val event = PipelineEvent.SafetyIntervention(reason, agent)

            assertEquals(reason, event.reason)
            assertEquals(agent, event.agent)
        }

        @Test
        fun `SafetyIntervention equality works correctly`() {
            val event1 = PipelineEvent.SafetyIntervention("unsafe", AgentRole.WATCHDOG)
            val event2 = PipelineEvent.SafetyIntervention("unsafe", AgentRole.WATCHDOG)
            val event3 = PipelineEvent.SafetyIntervention("different", AgentRole.WATCHDOG)

            assertEquals(event1, event2)
            assertNotEquals(event1, event3)
        }

        @Test
        fun `SafetyIntervention toString contains all properties`() {
            val event = PipelineEvent.SafetyIntervention("blocked", AgentRole.SYSTEM)
            val str = event.toString()

            assertTrue(str.contains("SafetyIntervention"))
            assertTrue(str.contains("blocked"))
        }
    }

    @Nested
    inner class CompletedTests {
        @Test
        fun `Completed creates correct event`() {
            val finalResponse = "Final answer"
            val allThinkingSteps = listOf("Step 1", "Step 2", "Step 3")
            val pipelineDurationSeconds = 5
            val event = PipelineEvent.Completed(finalResponse, allThinkingSteps, pipelineDurationSeconds)

            assertEquals(finalResponse, event.finalResponse)
            assertEquals(allThinkingSteps, event.allThinkingSteps)
            assertEquals(pipelineDurationSeconds, event.pipelineDurationSeconds)
        }

        @Test
        fun `Completed with empty thinking steps creates correct event`() {
            val event = PipelineEvent.Completed("Answer", emptyList(), 0)

            assertEquals("Answer", event.finalResponse)
            assertTrue(event.allThinkingSteps.isEmpty())
            assertEquals(0, event.pipelineDurationSeconds)
        }

        @Test
        fun `Completed equality works correctly`() {
            val steps = listOf("a", "b")
            val event1 = PipelineEvent.Completed("resp", steps, 10)
            val event2 = PipelineEvent.Completed("resp", steps, 10)
            val event3 = PipelineEvent.Completed("other", steps, 10)

            assertEquals(event1, event2)
            assertNotEquals(event1, event3)
        }

        @Test
        fun `Completed toString contains all properties`() {
            val event = PipelineEvent.Completed("result", listOf("step"), 3)
            val str = event.toString()

            assertTrue(str.contains("Completed"))
            assertTrue(str.contains("result"))
            assertTrue(str.contains("step"))
        }
    }

    @Nested
    inner class ErrorTests {
        @Test
        fun `Error creates correct event`() {
            val cause = RuntimeException("Test error")
            val event = PipelineEvent.Error(cause)

            assertEquals(cause, event.cause)
        }

        @Test
        fun `Error equality works correctly`() {
            val cause = RuntimeException("test")
            val event1 = PipelineEvent.Error(cause)
            val event2 = PipelineEvent.Error(cause)

            assertEquals(event1, event2)
        }

        @Test
        fun `Error toString contains cause message`() {
            val cause = RuntimeException("error message")
            val event = PipelineEvent.Error(cause)
            val str = event.toString()

            assertTrue(str.contains("Error"))
            assertTrue(str.contains("error message"))
        }
    }

    @Nested
    inner class PipelinePhaseTests {
        @Test
        fun `all pipeline phases are defined`() {
            assertEquals(4, PipelinePhase.entries.size)
            assertNotNull(PipelinePhase.DRAFTING)
            assertNotNull(PipelinePhase.SYNTHESIS)
            assertNotNull(PipelinePhase.REFINEMENT)
            assertNotNull(PipelinePhase.SAFETY_CHECK)
        }
    }

    @Nested
    inner class AgentRoleTests {
        @Test
        fun `all agent roles are defined`() {
            assertEquals(9, AgentRole.entries.size)
            assertNotNull(AgentRole.DRAFTER_ONE)
            assertNotNull(AgentRole.DRAFTER_TWO)
            assertNotNull(AgentRole.DRAFTER_THREE)
            assertNotNull(AgentRole.DRAFTER_FOUR)
            assertNotNull(AgentRole.SYNTHESIZER_ONE)
            assertNotNull(AgentRole.SYNTHESIZER_TWO)
            assertNotNull(AgentRole.FINAL_THINKER)
            assertNotNull(AgentRole.WATCHDOG)
            assertNotNull(AgentRole.SYSTEM)
        }
    }
}

class EnginePipelineOrchestratorTest {

    private lateinit var mockOrchestrator: EnginePipelineOrchestrator

    @BeforeEach
    fun setup() {
        mockOrchestrator = mockk<EnginePipelineOrchestrator>()
    }

    @Test
    fun `processPrompt returns flow of PipelineEvents`() = runTest {
        val prompt = "Hello AI"
        val events = listOf(
            PipelineEvent.PhaseUpdate(PipelinePhase.DRAFTING),
            PipelineEvent.ReasoningChunk(AgentRole.DRAFTER_ONE, "thinking", "thought"),
            PipelineEvent.TextChunk(AgentRole.FINAL_THINKER, "Hi", "Hi"),
            PipelineEvent.Completed("Hi there!", listOf("thought"), 2)
        )

        every { mockOrchestrator.processPrompt(prompt, false) } returns flow {
            events.forEach { emit(it) }
        }

        val result = mockOrchestrator.processPrompt(prompt, false).toList()

        assertEquals(4, result.size)
        assertTrue(result[0] is PipelineEvent.PhaseUpdate)
        assertTrue(result[1] is PipelineEvent.ReasoningChunk)
        assertTrue(result[2] is PipelineEvent.TextChunk)
        assertTrue(result[3] is PipelineEvent.Completed)
    }

    @Test
    fun `processPrompt with hasImage flag passes correctly`() = runTest {
        val prompt = "Describe this image"
        val events = listOf(PipelineEvent.Completed("A cat", emptyList(), 1))

        every { mockOrchestrator.processPrompt(prompt, true) } returns flow {
            events.forEach { emit(it) }
        }

        val result = mockOrchestrator.processPrompt(prompt, true).toList()

        assertEquals(1, result.size)
        verify { mockOrchestrator.processPrompt(prompt, true) }
    }

    @Test
    fun `processPrompt emits error event on failure`() = runTest {
        val prompt = "Test prompt"
        val errorCause = RuntimeException("Inference failed")
        val events = listOf(
            PipelineEvent.PhaseUpdate(PipelinePhase.DRAFTING),
            PipelineEvent.Error(errorCause)
        )

        every { mockOrchestrator.processPrompt(prompt, false) } returns flow {
            events.forEach { emit(it) }
        }

        val result = mockOrchestrator.processPrompt(prompt, false).toList()

        assertEquals(2, result.size)
        assertTrue(result[1] is PipelineEvent.Error)
        assertEquals(errorCause, (result[1] as PipelineEvent.Error).cause)
    }

    @Test
    fun `processPrompt emits safety intervention when blocked`() = runTest {
        val prompt = "Generate harmful content"
        val events = listOf(
            PipelineEvent.PhaseUpdate(PipelinePhase.SAFETY_CHECK),
            PipelineEvent.SafetyIntervention("Content blocked by safety policy")
        )

        every { mockOrchestrator.processPrompt(prompt, false) } returns flow {
            events.forEach { emit(it) }
        }

        val result = mockOrchestrator.processPrompt(prompt, false).toList()

        assertEquals(2, result.size)
        assertTrue(result[1] is PipelineEvent.SafetyIntervention)
        assertEquals("Content blocked by safety policy", (result[1] as PipelineEvent.SafetyIntervention).reason)
    }

    @Test
    fun `cancelPipeline invokes cancel method`() {
        every { mockOrchestrator.cancelPipeline() } returns Unit

        mockOrchestrator.cancelPipeline()

        verify { mockOrchestrator.cancelPipeline() }
    }

    @Test
    fun `cancelPipeline can be called multiple times`() {
        every { mockOrchestrator.cancelPipeline() } returns Unit

        mockOrchestrator.cancelPipeline()
        mockOrchestrator.cancelPipeline()
        mockOrchestrator.cancelPipeline()

        verify(exactly = 3) { mockOrchestrator.cancelPipeline() }
    }
}

