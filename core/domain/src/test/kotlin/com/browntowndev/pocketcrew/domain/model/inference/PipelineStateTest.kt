package com.browntowndev.pocketcrew.domain.model.inference

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class PipelineStateTest {

    @Test
    fun `createInitial creates state with DRAFT_ONE step`() {
        val state = PipelineState.createInitial("chat123", "Hello")

        assertEquals("chat123", state.chatId)
        assertEquals(PipelineStep.DRAFT_ONE, state.currentStep)
        assertEquals("Hello", state.userMessage)
        assertEquals(emptyMap<PipelineStep, String>(), state.stepOutputs)
        assertEquals(emptyList<String>(), state.thinkingSteps)
    }

    @Test
    fun `next returns correct next step for each pipeline step`() {
        assertEquals(PipelineStep.DRAFT_TWO, PipelineStep.DRAFT_ONE.next())
        assertEquals(PipelineStep.SYNTHESIS, PipelineStep.DRAFT_TWO.next())
        assertEquals(PipelineStep.FINAL, PipelineStep.SYNTHESIS.next())
        assertNull(PipelineStep.FINAL.next())
    }

    @Test
    fun `displayName returns human-readable name`() {
        assertEquals("Draft One", PipelineStep.DRAFT_ONE.displayName())
        assertEquals("Draft Two", PipelineStep.DRAFT_TWO.displayName())
        assertEquals("Synthesis", PipelineStep.SYNTHESIS.displayName())
        assertEquals("Final Review", PipelineStep.FINAL.displayName())
    }

    @Test
    fun `fromString parses valid step names`() {
        assertEquals(PipelineStep.DRAFT_ONE, PipelineStep.fromString("DRAFT_ONE"))
        assertEquals(PipelineStep.DRAFT_TWO, PipelineStep.fromString("DRAFT_TWO"))
        assertEquals(PipelineStep.SYNTHESIS, PipelineStep.fromString("SYNTHESIS"))
        assertEquals(PipelineStep.FINAL, PipelineStep.fromString("FINAL"))
    }

    @Test
    fun `fromString defaults to DRAFT_ONE for invalid input`() {
        assertEquals(PipelineStep.DRAFT_ONE, PipelineStep.fromString("INVALID"))
        assertEquals(PipelineStep.DRAFT_ONE, PipelineStep.fromString(""))
    }

    @Test
    fun `withStepOutput adds output to stepOutputs and thinkingSteps`() {
        val initial = PipelineState.createInitial("chat123", "Hello")
        val updated = initial.withStepOutput(PipelineStep.DRAFT_ONE, "Creative draft content")

        assertEquals(1, updated.stepOutputs.size)
        assertEquals("Creative draft content", updated.stepOutputs[PipelineStep.DRAFT_ONE])
        assertEquals(1, updated.thinkingSteps.size)
        assertTrue(updated.thinkingSteps[0].startsWith("Draft One:"))
    }

    @Test
    fun `withNextStep advances to next step`() {
        val state = PipelineState(
            chatId = "chat123",
            currentStep = PipelineStep.DRAFT_ONE,
            userMessage = "Hello"
        )
        val next = state.withNextStep()

        assertNotNull(next)
        assertEquals(PipelineStep.DRAFT_TWO, requireNotNull(next).currentStep)
    }

    @Test
    fun `withNextStep returns null for FINAL step`() {
        val state = PipelineState(
            chatId = "chat123",
            currentStep = PipelineStep.FINAL,
            userMessage = "Hello"
        )
        val next = state.withNextStep()

        assertNull(next)
    }

    @Test
    @Disabled("Requires Robolectric with Android manifest - skipped in unit tests")
    fun `toJson and fromJson round-trip serialization`() {
        val original = PipelineState(
            chatId = "chat123",
            currentStep = PipelineStep.DRAFT_TWO,
            userMessage = "Test prompt",
            stepOutputs = mapOf(
                PipelineStep.DRAFT_ONE to "Draft one output",
                PipelineStep.DRAFT_TWO to "Draft two output"
            ),
            thinkingSteps = listOf("Step 1", "Step 2"),
            startTimeMs = 1000L
        )

        val json = original.toJson()
        val restored = PipelineState.fromJson(json)

        assertEquals(original.chatId, restored.chatId)
        assertEquals(original.currentStep, restored.currentStep)
        assertEquals(original.userMessage, restored.userMessage)
        assertEquals(original.stepOutputs[PipelineStep.DRAFT_ONE], restored.stepOutputs[PipelineStep.DRAFT_ONE])
        assertEquals(original.stepOutputs[PipelineStep.DRAFT_TWO], restored.stepOutputs[PipelineStep.DRAFT_TWO])
        assertEquals(original.thinkingSteps, restored.thinkingSteps)
    }

    @Test
    fun `accumulatedThinking joins all step outputs`() {
        val state = PipelineState(
            chatId = "chat123",
            currentStep = PipelineStep.SYNTHESIS,
            userMessage = "Hello",
            stepOutputs = mapOf(
                PipelineStep.DRAFT_ONE to "Creative draft",
                PipelineStep.DRAFT_TWO to "Analytical draft"
            )
        )

        val accumulated = state.accumulatedThinking()

        assertTrue(accumulated.contains("Draft One"))
        assertTrue(accumulated.contains("Creative draft"))
        assertTrue(accumulated.contains("Draft Two"))
        assertTrue(accumulated.contains("Analytical draft"))
    }

    @Test
    fun `durationSeconds calculates correct elapsed time`() {
        val startTime = System.currentTimeMillis() - 5000 // 5 seconds ago
        val state = PipelineState(
            chatId = "chat123",
            currentStep = PipelineStep.FINAL,
            userMessage = "Hello",
            startTimeMs = startTime
        )

        val duration = state.durationSeconds()

        // Should be approximately 5 seconds (allow some tolerance)
        assertTrue(duration >= 4 && duration <= 6)
    }
}
