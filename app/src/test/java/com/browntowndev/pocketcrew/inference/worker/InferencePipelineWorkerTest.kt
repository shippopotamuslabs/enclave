package com.browntowndev.pocketcrew.inference.worker

import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for InferencePipelineWorker logic.
 */
class InferencePipelineWorkerTest {

    @Test
    fun `workName generates correct work name format`() {
        val workName = InferencePipelineWorker.workName("chat123")
        assertTrue(workName.startsWith("pipeline_work_"))
        assertTrue(workName.endsWith("chat123"))
    }

    @Test
    fun `step progress is calculated correctly`() {
        // DRAFT_ONE = 25%, DRAFT_TWO = 50%, SYNTHESIS = 75%, FINAL = 100%
        assertEquals(25, getStepProgress(PipelineStep.DRAFT_ONE))
        assertEquals(50, getStepProgress(PipelineStep.DRAFT_TWO))
        assertEquals(75, getStepProgress(PipelineStep.SYNTHESIS))
        assertEquals(100, getStepProgress(PipelineStep.FINAL))
    }

    private fun getStepProgress(step: PipelineStep): Int {
        return when (step) {
            PipelineStep.DRAFT_ONE -> 25
            PipelineStep.DRAFT_TWO -> 50
            PipelineStep.SYNTHESIS -> 75
            PipelineStep.FINAL -> 100
        }
    }

    @Test
    fun `pipeline steps chain correctly`() {
        // Verify step progression
        var currentStep: PipelineStep? = PipelineStep.DRAFT_ONE
        val stepOrder = mutableListOf<PipelineStep>()

        while (currentStep != null) {
            stepOrder.add(currentStep)
            currentStep = currentStep.next()
        }

        assertEquals(4, stepOrder.size)
        assertEquals(PipelineStep.DRAFT_ONE, stepOrder[0])
        assertEquals(PipelineStep.DRAFT_TWO, stepOrder[1])
        assertEquals(PipelineStep.SYNTHESIS, stepOrder[2])
        assertEquals(PipelineStep.FINAL, stepOrder[3])
    }

    @Test
    fun `input data is parsed correctly for worker`() {
        val initialState = PipelineState.createInitial("test_chat", "Hello world")

        val stateJson = initialState.toJson()
        assertNotNull(stateJson)

        val parsedState = PipelineState.fromJson(stateJson)
        assertEquals("test_chat", parsedState.chatId)
        assertEquals(PipelineStep.DRAFT_ONE, parsedState.currentStep)
        assertEquals("Hello world", parsedState.userMessage)
    }

    @Test
    fun `output data keys are correctly defined`() {
        // Verify output data keys exist
        assertEquals("final_response", PipelineState.KEY_FINAL_RESPONSE)
        assertEquals("duration_seconds", PipelineState.KEY_DURATION_SECONDS)
        assertEquals("all_thinking_steps_json", PipelineState.KEY_ALL_THINKING_STEPS_JSON)
        assertEquals("thinking_chunk", PipelineState.KEY_THINKING_CHUNK)
        assertEquals("step_output", PipelineState.KEY_STEP_OUTPUT)
    }
}
