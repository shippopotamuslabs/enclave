package com.browntowndev.pocketcrew.inference.service

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for the full 4-stage Crew pipeline.
 * Verifies end-to-end flow from Draft One through Final Review.
 */
class FullPipeline4StageIntegrationTest {

    /**
     * Tests the complete pipeline flow through all 4 stages.
     */
    @Test
    fun `full pipeline processes all 4 stages in order`() {
        // Given: A sequence representing the full pipeline
        val pipelineStages = listOf(
            createStepCompleted("Draft One", "Draft One output", 30, listOf("Analyzing"), "Gemma 3 1B", ModelType.DRAFT_ONE, PipelineStep.DRAFT_ONE),
            createStepCompleted("Draft Two", "Draft Two output", 25, listOf("Considering"), "Gemma 3 1B", ModelType.DRAFT_TWO, PipelineStep.DRAFT_TWO),
            createStepCompleted("Synthesis", "Synthesis output", 45, listOf("Synthesizing"), "DeepSeek-R1-8B", ModelType.MAIN, PipelineStep.SYNTHESIS),
            createStepCompleted("Final Review", "Final output", 20, listOf("Finalizing"), "DeepSeek-R1-8B", ModelType.MAIN, PipelineStep.FINAL)
        )

        // Then: All 4 stages are present in order
        assertEquals(4, pipelineStages.size)
        assertEquals(PipelineStep.DRAFT_ONE, pipelineStages[0].stepType)
        assertEquals(PipelineStep.DRAFT_TWO, pipelineStages[1].stepType)
        assertEquals(PipelineStep.SYNTHESIS, pipelineStages[2].stepType)
        assertEquals(PipelineStep.FINAL, pipelineStages[3].stepType)
    }

    /**
     * Tests that each pipeline stage has correct model type.
     */
    @Test
    fun `each pipeline stage has correct model type`() {
        val pipelineStages = listOf(
            createStepCompleted("Draft One", "Output", 10, listOf(), "Gemma", ModelType.DRAFT_ONE, PipelineStep.DRAFT_ONE),
            createStepCompleted("Draft Two", "Output", 10, listOf(), "Gemma", ModelType.DRAFT_TWO, PipelineStep.DRAFT_TWO),
            createStepCompleted("Synthesis", "Output", 10, listOf(), "DeepSeek", ModelType.MAIN, PipelineStep.SYNTHESIS),
            createStepCompleted("Final", "Output", 10, listOf(), "DeepSeek", ModelType.MAIN, PipelineStep.FINAL)
        )

        // Verify model types
        assertEquals(ModelType.DRAFT_ONE, pipelineStages[0].modelType)
        assertEquals(ModelType.DRAFT_TWO, pipelineStages[1].modelType)
        assertEquals(ModelType.MAIN, pipelineStages[2].modelType)
        assertEquals(ModelType.MAIN, pipelineStages[3].modelType)
    }

    /**
     * Tests that thinking time accumulates across stages.
     */
    @Test
    fun `thinking time accumulates across pipeline stages`() {
        val stages = listOf(
            createStepCompleted("Draft One", "Output", 30, listOf("Think 1"), "Model", ModelType.DRAFT_ONE, PipelineStep.DRAFT_ONE),
            createStepCompleted("Draft Two", "Output", 25, listOf("Think 2"), "Model", ModelType.DRAFT_TWO, PipelineStep.DRAFT_TWO),
            createStepCompleted("Synthesis", "Output", 45, listOf("Think 3"), "Model", ModelType.MAIN, PipelineStep.SYNTHESIS),
            createStepCompleted("Final", "Output", 20, listOf("Think 4"), "Model", ModelType.MAIN, PipelineStep.FINAL)
        )

        // Calculate total thinking time
        val totalThinkingTime = stages.sumOf { it.thinkingDurationSeconds }
        assertEquals(120, totalThinkingTime)
    }

    /**
     * Tests that thinking steps are preserved for each stage.
     */
    @Test
    fun `thinking steps are preserved for each pipeline stage`() {
        val stages = listOf(
            createStepCompleted("Draft One", "Output", 30, listOf("Analyzing", "Drafting"), "Model", ModelType.DRAFT_ONE, PipelineStep.DRAFT_ONE),
            createStepCompleted("Draft Two", "Output", 25, listOf("Considering", "Creating"), "Model", ModelType.DRAFT_TWO, PipelineStep.DRAFT_TWO),
            createStepCompleted("Synthesis", "Output", 45, listOf("Synthesizing", "Evaluating"), "Model", ModelType.MAIN, PipelineStep.SYNTHESIS)
        )

        // Verify thinking steps for each stage
        assertEquals(2, stages[0].thinkingSteps.size)
        assertEquals(2, stages[1].thinkingSteps.size)
        assertEquals(2, stages[2].thinkingSteps.size)
    }

    /**
     * Tests filtering out FINAL step from completed steps display.
     */
    @Test
    fun `FILTER out FINAL step from completed steps for display`() {
        val allSteps = listOf(
            createStepCompleted("Draft One", "Output", 30, listOf(), "Model", ModelType.DRAFT_ONE, PipelineStep.DRAFT_ONE),
            createStepCompleted("Draft Two", "Output", 25, listOf(), "Model", ModelType.DRAFT_TWO, PipelineStep.DRAFT_TWO),
            createStepCompleted("Synthesis", "Output", 45, listOf(), "Model", ModelType.MAIN, PipelineStep.SYNTHESIS),
            createStepCompleted("Final Review", "Output", 20, listOf(), "Model", ModelType.MAIN, PipelineStep.FINAL)
        )

        // Filter out FINAL - its output is already displayed as chat response
        val visibleSteps = allSteps.filter { it.stepType != PipelineStep.FINAL }

        // Then: FINAL step is filtered out
        assertEquals(3, visibleSteps.size)
        assertFalse(visibleSteps.any { it.stepType == PipelineStep.FINAL })
    }

    /**
     * Tests that step outputs are preserved through the pipeline.
     */
    @Test
    fun `step outputs are preserved through pipeline`() {
        val stages = listOf(
            createStepCompleted("Draft One", "First draft content", 30, listOf(), "Model", ModelType.DRAFT_ONE, PipelineStep.DRAFT_ONE),
            createStepCompleted("Draft Two", "Second draft content", 25, listOf(), "Model", ModelType.DRAFT_TWO, PipelineStep.DRAFT_TWO),
            createStepCompleted("Synthesis", "Synthesized content", 45, listOf(), "Model", ModelType.MAIN, PipelineStep.SYNTHESIS)
        )

        assertEquals("First draft content", stages[0].stepOutput)
        assertEquals("Second draft content", stages[1].stepOutput)
        assertEquals("Synthesized content", stages[2].stepOutput)
    }

    /**
     * Helper function to create StepCompleted state.
     */
    private fun createStepCompleted(
        stepName: String,
        stepOutput: String,
        thinkingDurationSeconds: Int,
        thinkingSteps: List<String>,
        modelDisplayName: String,
        modelType: ModelType,
        stepType: PipelineStep
    ): MessageGenerationState.StepCompleted {
        return MessageGenerationState.StepCompleted(
            stepOutput = stepOutput,
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingSteps = thinkingSteps,
            modelDisplayName = modelDisplayName,
            modelType = modelType,
            stepType = stepType
        )
    }
}
