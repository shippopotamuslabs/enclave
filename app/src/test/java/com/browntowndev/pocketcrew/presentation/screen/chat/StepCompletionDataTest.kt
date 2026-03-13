package com.browntowndev.pocketcrew.presentation.screen.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ChatViewModel's handling of StepCompletionData.
 */
class StepCompletionDataTest {

    @Test
    fun `StepCompletionData derives stepName from stepType`() {
        val stepData = StepCompletionData(
            stepOutput = "Test output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking 1"),
            stepType = PipelineStep.DRAFT_ONE,
            modelType = ModelType.DRAFT_ONE,
            modelDisplayName = "Gemma 3 1B"
        )

        assertEquals("Draft One", stepData.stepName)
    }

    @Test
    fun `StepCompletionData derives stepName for all step types`() {
        val draftOne = StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.DRAFT_ONE,
            modelType = ModelType.DRAFT_ONE,
            modelDisplayName = "Model 1"
        )
        assertEquals("Draft One", draftOne.stepName)

        val draftTwo = StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.DRAFT_TWO,
            modelType = ModelType.DRAFT_TWO,
            modelDisplayName = "Model 2"
        )
        assertEquals("Draft Two", draftTwo.stepName)

        val synthesis = StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.SYNTHESIS,
            modelType = ModelType.MAIN,
            modelDisplayName = "Model 3"
        )
        assertEquals("Synthesis", synthesis.stepName)

        val final = StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.FINAL,
            modelType = ModelType.MAIN,
            modelDisplayName = "Model 4"
        )
        assertEquals("Final Review", final.stepName)
    }

    @Test
    fun `StepCompletionData fromMessageGenerationState maps correctly`() {
        val state = MessageGenerationState.StepCompleted(
            stepOutput = "Step output text",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Thought 1", "Thought 2"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val stepData = StepCompletionData.fromMessageGenerationState(state, "Gemma 3 1B")

        assertEquals("Step output text", stepData.stepOutput)
        assertEquals(45, stepData.thinkingDurationSeconds)
        assertEquals(2, stepData.thinkingSteps.size)
        assertEquals(PipelineStep.DRAFT_ONE, stepData.stepType)
        assertEquals(ModelType.DRAFT_ONE, stepData.modelType)
        assertEquals("Gemma 3 1B", stepData.modelDisplayName)
        assertEquals("Draft One", stepData.stepName)
    }
}
