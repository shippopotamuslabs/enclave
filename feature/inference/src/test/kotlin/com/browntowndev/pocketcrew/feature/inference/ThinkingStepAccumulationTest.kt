package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for thinking step accumulation across pipeline steps.
 * Verifies that thinking steps from completed steps are properly accumulated and available
 * through the PipelineState methods.
 */
class ThinkingStepAccumulationTest {

    /**
     * Test that step output is correctly stored for each pipeline step.
     * Each step needs access to previous outputs for context.
     */
    @Test
    fun `step outputs are stored in stepOutputs map`() {
        // Create state with outputs from previous steps
        var state = PipelineState.createInitial("chat1", "Test prompt?")

        // After DRAFT_ONE
        state = state.withStepOutput(PipelineStep.DRAFT_ONE, "Creative draft")

        // Verify output is stored
        assertEquals("Creative draft", state.stepOutputs[PipelineStep.DRAFT_ONE])

        // Advance to DRAFT_TWO
        state = state.withNextStep()!!

        // After DRAFT_TWO - should have access to DRAFT_ONE output
        state = state.withStepOutput(PipelineStep.DRAFT_TWO, "Analytical draft")

        // Verify both outputs are available for SYNTHESIS
        assertEquals("Creative draft", state.stepOutputs[PipelineStep.DRAFT_ONE])
        assertEquals("Analytical draft", state.stepOutputs[PipelineStep.DRAFT_TWO])
    }

    /**
     * Test that thinking steps accumulate in PipelineState when withStepOutput is called.
     */
    @Test
    fun `thinking steps accumulate when withStepOutput is called`() {
        var state = PipelineState.createInitial("chat1", "Test?")

        // After DRAFT_ONE completes
        state = state.withStepOutput(PipelineStep.DRAFT_ONE, "Creative answer here")
        assertEquals(1, state.thinkingSteps.size)
        assertTrue(state.thinkingSteps[0].contains("Creative answer"))

        // Move to DRAFT_TWO
        state = state.withNextStep()!!

        // After DRAFT_TWO completes
        state = state.withStepOutput(PipelineStep.DRAFT_TWO, "Analytical answer here")
        assertEquals(2, state.thinkingSteps.size)
    }

    /**
     * Test that withNextStep advances to the next step correctly.
     */
    @Test
    fun `withNextStep advances to correct next step`() {
        var state = PipelineState.createInitial("chat1", "Test?")

        // Initial step should be DRAFT_ONE
        assertEquals(PipelineStep.DRAFT_ONE, state.currentStep)

        // Advance to DRAFT_TWO
        state = state.withNextStep()!!
        assertEquals(PipelineStep.DRAFT_TWO, state.currentStep)

        // Advance to SYNTHESIS
        state = state.withNextStep()!!
        assertEquals(PipelineStep.SYNTHESIS, state.currentStep)

        // Advance to FINAL
        state = state.withNextStep()!!
        assertEquals(PipelineStep.FINAL, state.currentStep)
    }

    /**
     * Test that withNextStep returns null when already at FINAL step.
     */
    @Test
    fun `withNextStep returns null at FINAL step`() {
        var state = PipelineState.createInitial("chat1", "Test?")

        // Advance through all steps
        state = state.withNextStep()!!
        state = state.withNextStep()!!
        state = state.withNextStep()!!

        // Now at FINAL - should return null
        val result = state.withNextStep()
        assertNull(result)
    }

    /**
     * Test that accumulatedThinking returns formatted thinking from all steps.
     */
    @Test
    fun `accumulatedThinking returns formatted thinking from all steps`() {
        var state = PipelineState.createInitial("chat1", "Test?")

        state = state.withStepOutput(PipelineStep.DRAFT_ONE, "Creative draft")
        state = state.withNextStep()!!
        state = state.withStepOutput(PipelineStep.DRAFT_TWO, "Analytical draft")

        val accumulated = state.accumulatedThinking()

        assertNotNull(accumulated)
        assertTrue(accumulated.contains("Draft One"))
        assertTrue(accumulated.contains("Creative draft"))
        assertTrue(accumulated.contains("Draft Two"))
        assertTrue(accumulated.contains("Analytical draft"))
    }
}
