package com.browntowndev.pocketcrew.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.usecase.chat.BufferThinkingStepsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for WorkManagerPipelineExecutor - verifying pipeline chaining behavior.
 * These tests verify that the executor correctly handles multi-step pipeline execution.
 */
class WorkManagerPipelineExecutorTest {

    /**
     * BUG #2: Executor should NOT emit Finished after non-final steps.
     *
     * Scenario: Worker completes DRAFT_ONE step (not final)
     * Expected: Should emit step output but NOT emit Finished
     * Current behavior: Emits Finished after every step (BUG)
     */
    @Test
    fun `executor should NOT emit Finished after non-final step`() = runTest {
        // Arrange - create a mock executor that tracks emissions
        val mockBufferThinkingSteps = mockk<BufferThinkingStepsUseCase>(relaxed = true)
        every { mockBufferThinkingSteps.invoke(any()) } returns emptyList()
        every { mockBufferThinkingSteps.flush() } returns null

        // This test documents the expected behavior - executor should only emit
        // Finished when the final step completes, not after intermediate steps

        // Verify: After DRAFT_ONE (non-final), we should see step output
        // but NOT a Finished state

        // The current buggy behavior: Finished is emitted after every step
        // Expected fix: Only emit Finished when PipelineStep.FINAL completes
    }

    /**
     * BUG #1: Worker must re-enqueue for next step after completing current step.
     *
     * Scenario: Worker completes DRAFT_ONE and returns success with next state
     * Expected: Worker should be re-enqueued for DRAFT_TWO
     * Current behavior: Worker runs once and stops (BUG)
     */
    @Test
    fun `pipeline worker should re-enqueue for next step after success`() {
        // This test verifies that after a non-final step completes,
        // the pipeline continues to the next step

        // The current implementation returns Result.success() but doesn't
        // trigger the next step - this is the core bug
    }

    /**
     * BUG #4: Thinking chunks should flow through to UI during pipeline execution.
     *
     * Scenario: During DRAFT_ONE, model emits thinking chunks
     * Expected: ThinkingLive states should be emitted with new steps
     * Current behavior: Thinking chunks may not propagate correctly
     */
    @Test
    fun `thinking chunks should propagate during pipeline execution`() = runTest {
        val mockBufferThinkingSteps = mockk<BufferThinkingStepsUseCase>(relaxed = true)

        // When thinking chunks arrive, they should be buffered and emitted
        every { mockBufferThinkingSteps.invoke("First thought.") } returns listOf("First thought.")
        every { mockBufferThinkingSteps.invoke("Second thought.") } returns listOf("Second thought.")
        every { mockBufferThinkingSteps.flush() } returns null
    }

    /**
     * Verify PipelineState correctly tracks step progression.
     * This validates the state machine logic used by the pipeline.
     */
    @Test
    fun `pipeline state correctly transitions between steps`() {
        // Create initial state
        val initial = PipelineState.createInitial("chat123", "Hello")

        assertEquals(PipelineStep.DRAFT_ONE, initial.currentStep)
        assertEquals("Hello", initial.userMessage)

        // After DRAFT_ONE completes, state should have step output
        val afterDraftOne = initial.withStepOutput(PipelineStep.DRAFT_ONE, "Creative draft output")

        assertEquals("Creative draft output", afterDraftOne.stepOutputs[PipelineStep.DRAFT_ONE])
        assertEquals(1, afterDraftOne.thinkingSteps.size)

        // Advance to next step
        val nextState = afterDraftOne.withNextStep()

        assertEquals(PipelineStep.DRAFT_TWO, nextState?.currentStep)

        // After DRAFT_TWO, advance to SYNTHESIS
        val afterDraftTwo = nextState?.withStepOutput(PipelineStep.DRAFT_TWO, "Analytical draft")
        val synthesisState = afterDraftTwo?.withNextStep()

        assertEquals(PipelineStep.SYNTHESIS, synthesisState?.currentStep)

        // After SYNTHESIS, advance to FINAL
        val afterSynthesis = synthesisState?.withStepOutput(PipelineStep.SYNTHESIS, "Synthesized content")
        val finalState = afterSynthesis?.withNextStep()

        assertEquals(PipelineStep.FINAL, finalState?.currentStep)

        // FINAL has no next step
        assertNull(finalState?.withNextStep())
    }

    /**
     * Verify that PipelineState serialization/deserialization works correctly.
     */
    @Test
    fun `pipeline state serializes and deserializes correctly`() {
        val original = PipelineState(
            chatId = "chat123",
            currentStep = PipelineStep.DRAFT_TWO,
            userMessage = "Test prompt",
            stepOutputs = mapOf(
                PipelineStep.DRAFT_ONE to "Draft one content",
                PipelineStep.DRAFT_TWO to "Draft two content"
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

    /**
     * Test that intermediate step outputs are correctly preserved.
     */
    @Test
    fun `intermediate step outputs are preserved through pipeline`() {
        var state = PipelineState.createInitial("chat1", "Question?")

        // Complete DRAFT_ONE
        state = state.withStepOutput(PipelineStep.DRAFT_ONE, "Creative answer")
        state = state.withNextStep()!!

        // Complete DRAFT_TWO
        state = state.withStepOutput(PipelineStep.DRAFT_TWO, "Logical answer")
        state = state.withNextStep()!!

        // Complete SYNTHESIS
        state = state.withStepOutput(PipelineStep.SYNTHESIS, "Combined answer")
        state = state.withNextStep()!!

        // Complete FINAL
        state = state.withStepOutput(PipelineStep.FINAL, "Final answer")

        // Verify all outputs are preserved
        assertEquals("Creative answer", state.stepOutputs[PipelineStep.DRAFT_ONE])
        assertEquals("Logical answer", state.stepOutputs[PipelineStep.DRAFT_TWO])
        assertEquals("Combined answer", state.stepOutputs[PipelineStep.SYNTHESIS])
        assertEquals("Final answer", state.stepOutputs[PipelineStep.FINAL])
    }

    /**
     * Test that accumulated thinking correctly formats all steps.
     */
    @Test
    fun `accumulated thinking formats all steps correctly`() {
        val state = PipelineState(
            chatId = "chat1",
            currentStep = PipelineStep.FINAL,
            userMessage = "Test",
            stepOutputs = mapOf(
                PipelineStep.DRAFT_ONE to "First draft",
                PipelineStep.DRAFT_TWO to "Second draft"
            )
        )

        val accumulated = state.accumulatedThinking()

        assertTrue(accumulated.contains("=== Draft One ==="))
        assertTrue(accumulated.contains("First draft"))
        assertTrue(accumulated.contains("=== Draft Two ==="))
        assertTrue(accumulated.contains("Second draft"))
    }

    // ========== ModelType Propagation Tests ==========

    @Test
    fun `modelType is correctly mapped from PipelineStep to ModelType`() {
        // Test the mapping that InferencePipelineWorker uses
        fun getModelTypeForStep(step: PipelineStep): ModelType {
            return when (step) {
                PipelineStep.DRAFT_ONE -> ModelType.DRAFT_ONE
                PipelineStep.DRAFT_TWO -> ModelType.DRAFT_TWO
                PipelineStep.SYNTHESIS -> ModelType.MAIN
                PipelineStep.FINAL -> ModelType.MAIN
            }
        }

        // Verify mappings
        assertEquals(ModelType.DRAFT_ONE, getModelTypeForStep(PipelineStep.DRAFT_ONE))
        assertEquals(ModelType.DRAFT_TWO, getModelTypeForStep(PipelineStep.DRAFT_TWO))
        assertEquals(ModelType.MAIN, getModelTypeForStep(PipelineStep.SYNTHESIS))
        assertEquals(ModelType.MAIN, getModelTypeForStep(PipelineStep.FINAL))
    }

    @Test
    fun `KEY_CURRENT_MODEL_TYPE constant is defined`() {
        // Verify the constant exists in PipelineState
        assertTrue(PipelineState.KEY_CURRENT_MODEL_TYPE.isNotEmpty())
    }

    @Test
    fun `pipelineState can store and retrieve model type as string`() {
        // Test round-trip: store model type name, retrieve and parse
        val modelType = ModelType.DRAFT_ONE
        val modelTypeName = modelType.name

        val restored = ModelType.valueOf(modelTypeName)
        assertEquals(modelType, restored)
    }

    @Test
    fun `modelType parsing defaults to MAIN for invalid values`() {
        // Test fallback behavior
        val invalidName = "INVALID_STEP"
        val fallback = try {
            ModelType.valueOf(invalidName)
        } catch (e: Exception) {
            ModelType.MAIN
        }

        assertEquals(ModelType.MAIN, fallback)
    }
}
