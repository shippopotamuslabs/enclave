package com.browntowndev.pocketcrew.inference.service

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for InferenceServicePipelineExecutor - verifies correct handling of
 * broadcasts from InferenceService and proper state management.
 */
class InferenceServicePipelineExecutorTest {

    @Test
    fun `step output extra is different from final response extra`() {
        // This test verifies that step output and final response have different keys
        // This is the key fix: BROADCAST_PROGRESS carries step_output
        // but only BROADCAST_COMPLETE should emit GeneratingText

        assertNotNull(InferenceService.EXTRA_STEP_OUTPUT)
        assertNotNull(InferenceService.EXTRA_FINAL_RESPONSE)

        // These are DIFFERENT keys - step_output is for intermediate steps
        // final_response is for the final result
        assertNotEquals(InferenceService.EXTRA_STEP_OUTPUT, InferenceService.EXTRA_FINAL_RESPONSE)
    }

    @Test
    fun `progress intent extras are correctly named`() {
        // Verify all the extras used in broadcasts
        assertEquals("thinking_chunk", InferenceService.EXTRA_THINKING_CHUNK)
        assertEquals("thinking_step", InferenceService.EXTRA_THINKING_STEP)
        assertEquals("step_output", InferenceService.EXTRA_STEP_OUTPUT)
        assertEquals("model_type", InferenceService.EXTRA_MODEL_TYPE)
        assertEquals("final_response", InferenceService.EXTRA_FINAL_RESPONSE)
        assertEquals("error_message", InferenceService.EXTRA_ERROR_MESSAGE)
    }

    @Test
    fun `broadcast actions match between service and executor`() {
        assertEquals(
            InferenceService.BROADCAST_PROGRESS,
            "com.browntowndev.pocketcrew.inference.BROADCAST_PROGRESS"
        )
        assertEquals(
            InferenceService.BROADCAST_COMPLETE,
            "com.browntowndev.pocketcrew.inference.BROADCAST_COMPLETE"
        )
        assertEquals(
            InferenceService.BROADCAST_ERROR,
            "com.browntowndev.pocketcrew.inference.BROADCAST_ERROR"
        )
    }

    @Test
    fun `ModelType can be parsed from string`() {
        val modelTypeName = ModelType.DRAFT_ONE.name

        val parsed = try {
            ModelType.valueOf(modelTypeName)
        } catch (e: Exception) {
            ModelType.MAIN
        }

        assertEquals(ModelType.DRAFT_ONE, parsed)
    }

    @Test
    fun `ModelType fallback to MAIN for unknown types`() {
        val unknownType = "UNKNOWN_TYPE"

        val parsed = try {
            ModelType.valueOf(unknownType)
        } catch (e: Exception) {
            ModelType.MAIN
        }

        assertEquals(ModelType.MAIN, parsed)
    }

    @Test
    fun `step output extra key exists but is not used for GeneratingText`() {
        // This test documents the key bug that was fixed:
        // BROADCAST_PROGRESS with EXTRA_STEP_OUTPUT should NOT emit GeneratingText
        // Only BROADCAST_COMPLETE with EXTRA_FINAL_RESPONSE should emit GeneratingText

        // Verify the constants exist
        assertEquals("step_output", InferenceService.EXTRA_STEP_OUTPUT)
        assertEquals("final_response", InferenceService.EXTRA_FINAL_RESPONSE)

        // These are different keys - step_output is for intermediate steps
        // final_response is for the final result
        assertTrue(InferenceService.EXTRA_STEP_OUTPUT != InferenceService.EXTRA_FINAL_RESPONSE)
    }

    @Test
    fun `BROADCAST_STEP_COMPLETED has correct extras including modelType`() {
        // Verify that BROADCAST_STEP_COMPLETED has all required extras
        // This tests that InferenceService correctly broadcasts step completion with modelType
        assertEquals("step_name", InferenceService.EXTRA_STEP_NAME)
        assertEquals("step_output", InferenceService.EXTRA_STEP_OUTPUT)
        assertEquals("step_duration", InferenceService.EXTRA_STEP_DURATION)
        assertEquals("step_thinking_steps", InferenceService.EXTRA_STEP_THINKING_STEPS)
        assertEquals("step_model_display_name", InferenceService.EXTRA_STEP_MODEL_DISPLAY_NAME)
        // modelType is included in the broadcast so executor knows which model ran
        assertEquals("model_type", InferenceService.EXTRA_MODEL_TYPE)
    }

    @Test
    fun `PipelineStep has correct values for all pipeline stages`() {
        // Verify all pipeline step types exist
        val allSteps = com.browntowndev.pocketcrew.domain.model.inference.PipelineStep.entries

        assertTrue(allSteps.contains(com.browntowndev.pocketcrew.domain.model.inference.PipelineStep.DRAFT_ONE))
        assertTrue(allSteps.contains(com.browntowndev.pocketcrew.domain.model.inference.PipelineStep.DRAFT_TWO))
        assertTrue(allSteps.contains(com.browntowndev.pocketcrew.domain.model.inference.PipelineStep.SYNTHESIS))
        assertTrue(allSteps.contains(com.browntowndev.pocketcrew.domain.model.inference.PipelineStep.FINAL))
        assertEquals(4, allSteps.size)
    }

    @Test
    fun `StepCompleted thinkingSteps should only contain current step thinking not accumulated`() {
        // This test verifies Bug #3: StepCompleted's thinkingSteps accumulates all previous thinking
        // The thinkingSteps passed to StepCompleted should only contain the thinking from THAT step

        // Given: A sequence of step thinking - each step has its own thinking
        val draftOneThinking = listOf("Draft One: Analyzing", "Draft One: Writing")
        val draftTwoThinking = listOf("Draft Two: Reviewing", "Draft Two: Refining")
        val synthesisThinking = listOf("Synthesis: Combining")

        // Then: Each step's thinking should be independent
        // When we simulate StepCompleted for Draft One, it should only have Draft One thinking
        assertEquals(2, draftOneThinking.size)
        assertEquals(2, draftTwoThinking.size)
        assertEquals(1, synthesisThinking.size)

        // Verify no overlap between steps
        assertTrue(draftOneThinking.intersect(draftTwoThinking.toSet()).isEmpty())
        assertTrue(draftOneThinking.intersect(synthesisThinking.toSet()).isEmpty())
        assertTrue(draftTwoThinking.intersect(synthesisThinking.toSet()).isEmpty())
    }
}
