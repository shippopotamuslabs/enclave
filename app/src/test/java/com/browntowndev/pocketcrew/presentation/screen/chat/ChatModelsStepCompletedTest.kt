package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState.StepCompleted
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ChatModels.StepCompletionData (presentation layer).
 */
class ChatModelsStepCompletedTest {

    @Test
    fun `StepCompletionData stores all fields correctly`() {
        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData(
            stepOutput = "Test output",
            thinkingDurationSeconds = 30,
            thinkingSteps = listOf("Thinking 1", "Thinking 2"),
            stepType = PipelineStep.DRAFT_ONE,
            modelType = ModelType.DRAFT_ONE,
            modelDisplayName = "Gemma 3 1B"
        )

        assertEquals("Test output", stepData.stepOutput)
        assertEquals(30, stepData.thinkingDurationSeconds)
        assertEquals(2, stepData.thinkingSteps.size)
        assertEquals(PipelineStep.DRAFT_ONE, stepData.stepType)
        assertEquals(ModelType.DRAFT_ONE, stepData.modelType)
        assertEquals("Gemma 3 1B", stepData.modelDisplayName)
    }

    @Test
    fun `StepCompletionData derives stepName from stepType for Draft One`() {
        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.DRAFT_ONE,
            modelType = ModelType.DRAFT_ONE,
            modelDisplayName = "Model 1"
        )

        assertEquals("Draft One", stepData.stepName)
    }

    @Test
    fun `StepCompletionData derives stepName from stepType for Draft Two`() {
        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.DRAFT_TWO,
            modelType = ModelType.DRAFT_TWO,
            modelDisplayName = "Model 2"
        )

        assertEquals("Draft Two", stepData.stepName)
    }

    @Test
    fun `StepCompletionData derives stepName from stepType for Synthesis`() {
        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.SYNTHESIS,
            modelType = ModelType.MAIN,
            modelDisplayName = "Model 3"
        )

        assertEquals("Synthesis", stepData.stepName)
    }

    @Test
    fun `StepCompletionData derives stepName from stepType for Final`() {
        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf(),
            stepType = PipelineStep.FINAL,
            modelType = ModelType.MAIN,
            modelDisplayName = "Model 4"
        )

        assertEquals("Final Review", stepData.stepName)
    }

    @Test
    fun `StepCompletionData fromMessageGenerationState maps correctly`() {
        val state = StepCompleted(
            stepOutput = "Step output text",
            thinkingDurationSeconds = 45,
            thinkingSteps = listOf("Thought 1", "Thought 2"),
            modelDisplayName = "Gemma 3 1B",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData.fromMessageGenerationState(state, "Gemma 3 1B")

        assertEquals("Step output text", stepData.stepOutput)
        assertEquals(45, stepData.thinkingDurationSeconds)
        assertEquals(2, stepData.thinkingSteps.size)
        assertEquals(PipelineStep.DRAFT_ONE, stepData.stepType)
        assertEquals(ModelType.DRAFT_ONE, stepData.modelType)
        assertEquals("Gemma 3 1B", stepData.modelDisplayName)
        assertEquals("Draft One", stepData.stepName)
    }

    @Test
    fun `StepCompletionData handles empty thinking steps`() {
        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData(
            stepOutput = "Quick output",
            thinkingDurationSeconds = 5,
            thinkingSteps = emptyList(),
            stepType = PipelineStep.DRAFT_ONE,
            modelType = ModelType.DRAFT_ONE,
            modelDisplayName = "Fast Model"
        )

        assertEquals("Quick output", stepData.stepOutput)
        assertEquals(5, stepData.thinkingDurationSeconds)
        assertTrue(stepData.thinkingSteps.isEmpty())
    }

    @Test
    fun `StepCompletionData modelDisplayName is preserved from ModelRegistry`() {
        val stepData = com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData(
            stepOutput = "Output",
            thinkingDurationSeconds = 10,
            thinkingSteps = listOf("Thinking"),
            stepType = PipelineStep.SYNTHESIS,
            modelType = ModelType.MAIN,
            modelDisplayName = "DeepSeek-R1-8B-Qwen3"
        )

        assertEquals("DeepSeek-R1-8B-Qwen3", stepData.modelDisplayName)
    }
}
