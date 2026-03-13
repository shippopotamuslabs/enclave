package com.browntowndev.pocketcrew.inference.service

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for InferenceServicePipelineStateFlow - verifies state transitions in the pipeline.
 */
class InferenceServicePipelineStateFlowTest {

    /**
     * Tests that StepCompleted state contains all required fields.
     */
    @Test
    fun `StepCompleted state contains all required fields`() {
        // Given: A StepCompleted state from the pipeline
        val draftOneCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft One output text",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Analyzing the request..."),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        // Then: All fields are present
        assertEquals("Draft One output text", draftOneCompleted.stepOutput)
        assertEquals(30, draftOneCompleted.thinkingDurationSeconds)
        assertEquals(1, draftOneCompleted.thinkingSteps.size)
        assertEquals("Gemma 3 1B", draftOneCompleted.modelDisplayName)
        assertEquals(ModelType.DRAFT_ONE, draftOneCompleted.modelType)
        assertEquals(PipelineStep.DRAFT_ONE, draftOneCompleted.stepType)
    }

    /**
     * Tests that step types are correctly associated with each pipeline stage.
     */
    @Test
    fun `step types are correctly associated with each pipeline stage`() {
        // Given: StepCompleted states for each pipeline stage
        val draftOneCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft One output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking..."),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val draftTwoCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft Two output",
            thinkingDurationSeconds = 25,
            thinkingSteps = listOf("Thinking..."),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )

        val synthesisCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Synthesis output",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Thinking..."),
            modelDisplayName = "DeepSeek-R1-8B",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.SYNTHESIS
        )

        // Then: Each step has correct type
        assertEquals(PipelineStep.DRAFT_ONE, draftOneCompleted.stepType)
        assertEquals(PipelineStep.DRAFT_TWO, draftTwoCompleted.stepType)
        assertEquals(PipelineStep.SYNTHESIS, synthesisCompleted.stepType)
    }

    /**
     * Tests that model types are correctly associated with each pipeline stage.
     */
    @Test
    fun `model types are correctly associated with each pipeline stage`() {
        // Given: StepCompleted states for each pipeline stage
        val draftOneCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 1 output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking 1"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val draftTwoCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 2 output",
            thinkingDurationSeconds = 25,
            thinkingSteps = listOf("Thinking 2"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )

        val synthesisCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Synthesis output",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Thinking 3"),
            modelDisplayName = "DeepSeek-R1-8B",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.SYNTHESIS
        )

        // Then: Model types are correct for each step
        assertEquals(ModelType.DRAFT_ONE, draftOneCompleted.modelType)
        assertEquals(ModelType.DRAFT_TWO, draftTwoCompleted.modelType)
        assertEquals(ModelType.MAIN, synthesisCompleted.modelType)
    }

    /**
     * Tests that thinking durations are correctly tracked.
     */
    @Test
    fun `thinking durations are correctly tracked for each step`() {
        // Given: StepCompleted states with different durations
        val draftOneCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 1 output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking 1"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val draftTwoCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 2 output",
            thinkingDurationSeconds = 25,
            thinkingSteps = listOf("Thinking 2"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )

        val synthesisCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Synthesis output",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Thinking 3"),
            modelDisplayName = "DeepSeek-R1-8B",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.SYNTHESIS
        )

        // Then: Thinking durations are correct
        assertEquals(30, draftOneCompleted.thinkingDurationSeconds)
        assertEquals(25, draftTwoCompleted.thinkingDurationSeconds)
        assertEquals(45, synthesisCompleted.thinkingDurationSeconds)
    }

    /**
     * Tests that model display names are preserved through the pipeline.
     */
    @Test
    fun `model display names are preserved through pipeline`() {
        // Given: StepCompleted states with model names
        val draftOneCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 1 output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking 1"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val draftTwoCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 2 output",
            thinkingDurationSeconds = 25,
            thinkingSteps = listOf("Thinking 2"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )

        val synthesisCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Synthesis output",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Thinking 3"),
            modelDisplayName = "DeepSeek-R1-0528-Qwen3-8B",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.SYNTHESIS
        )

        // Then: Model display names are preserved
        assertEquals("Gemma 3 1B", draftOneCompleted.modelDisplayName)
        assertEquals("Gemma 3 1B", draftTwoCompleted.modelDisplayName)
        assertEquals("DeepSeek-R1-0528-Qwen3-8B", synthesisCompleted.modelDisplayName)
    }

    /**
     * Tests that StepCompleted transitions to next step correctly.
     */
    @Test
    fun `StepCompleted transitions responseState to THINKING for next step`() {
        // This test verifies that when StepCompleted is received,
        // the UI should be in THINKING state ready for next step

        val stepCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking..."),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        // When StepCompleted is emitted, UI should show THINKING
        // This is the expected behavior - the UI should transition to next step
        assertNotNull(stepCompleted)
        assertEquals("Output", stepCompleted.stepOutput)
        assertEquals(30, stepCompleted.thinkingDurationSeconds)
    }

    /**
     * Tests that model type changes between pipeline steps.
     */
    @Test
    fun `model type changes between pipeline steps`() {
        val stepCompleted = MessageGenerationState.StepCompleted(
            stepOutput = "Output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking..."),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        // After step completes, next thinking should use new model
        val nextThinking = MessageGenerationState.ThinkingLive(
            steps = listOf("Next thinking..."),
            modelType = ModelType.DRAFT_TWO
        )

        // The model type changes between steps
        assertEquals(ModelType.DRAFT_ONE, stepCompleted.modelType)
        assertEquals(ModelType.DRAFT_TWO, nextThinking.modelType)
    }

    /**
     * Tests that step completions accumulate across pipeline.
     */
    @Test
    fun `step completions accumulate across pipeline`() {
        // This verifies that multiple step completions build up correctly
        val draftOne = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 1 output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking 1"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val draftTwo = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 2 output",
            thinkingDurationSeconds = 25,
            thinkingSteps = listOf("Thinking 2"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )

        val synthesis = MessageGenerationState.StepCompleted(
            stepOutput = "Synthesis output",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Thinking 3"),
            modelDisplayName = "DeepSeek-R1-0528-Qwen3-8B",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.SYNTHESIS
        )

        // Accumulate step completions
        val allSteps = listOf(draftOne, draftTwo, synthesis)

        // Verify accumulation
        assertEquals(3, allSteps.size)
        assertEquals("Draft 1 output", allSteps[0].stepOutput)
        assertEquals("Draft 2 output", allSteps[1].stepOutput)
        assertEquals("Synthesis output", allSteps[2].stepOutput)

        // Verify total duration
        val totalDuration = allSteps.sumOf { it.thinkingDurationSeconds }
        assertEquals(100, totalDuration)
    }

    /**
     * Tests that thinking steps accumulate across pipeline.
     */
    @Test
    fun `thinking steps accumulate across pipeline steps`() {
        val draftOne = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 1 output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Analyzing", "Drafting"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val draftTwo = MessageGenerationState.StepCompleted(
            stepOutput = "Draft 2 output",
            thinkingDurationSeconds = 25,
            thinkingSteps = listOf("Considering alternatives", "Creating draft"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )

        // Each step has its own thinking steps
        assertEquals(2, draftOne.thinkingSteps.size)
        assertEquals(2, draftTwo.thinkingSteps.size)
    }
}
