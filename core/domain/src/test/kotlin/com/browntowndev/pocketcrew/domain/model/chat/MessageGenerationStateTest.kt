package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageGenerationStateTest {

    @Test
    fun `isTerminal returns true for Finished`() {
        assertTrue(MessageGenerationState.Finished(ModelType.MAIN).isTerminal)
    }

    @Test
    fun `isTerminal returns true for Failed`() {
        assertTrue(MessageGenerationState.Failed(RuntimeException("err"), ModelType.MAIN).isTerminal)
    }

    @Test
    fun `isTerminal returns true for Blocked`() {
        assertTrue(MessageGenerationState.Blocked("reason", ModelType.MAIN).isTerminal)
    }

    @Test
    fun `isTerminal returns false for Processing`() {
        assertFalse(MessageGenerationState.Processing(ModelType.MAIN).isTerminal)
    }

    @Test
    fun `isTerminal returns false for ThinkingLive`() {
        assertFalse(MessageGenerationState.ThinkingLive("chunk", ModelType.MAIN).isTerminal)
    }

    @Test
    fun `isTerminal returns false for GeneratingText`() {
        assertFalse(MessageGenerationState.GeneratingText("delta", ModelType.MAIN).isTerminal)
    }

    @Test
    fun `isTerminal returns false for EngineLoading`() {
        assertFalse(MessageGenerationState.EngineLoading(ModelType.MAIN).isTerminal)
    }

    @Test
    fun `isTerminal returns false for StepCompleted regardless of step type`() {
        // Base isTerminal should be false for StepCompleted even with FINAL step
        val finalStep = MessageGenerationState.StepCompleted(
            stepOutput = "",
            modelDisplayName = "Test",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.FINAL,
        )
        assertFalse(finalStep.isTerminal)

        val draftStep = MessageGenerationState.StepCompleted(
            stepOutput = "",
            modelDisplayName = "Test",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE,
        )
        assertFalse(draftStep.isTerminal)
    }

    @Test
    fun `isTerminal returns false for TavilySourcesAttached`() {
        assertFalse(
            MessageGenerationState.TavilySourcesAttached(
                sources = emptyList(),
                modelType = ModelType.MAIN,
            ).isTerminal
        )
    }

    // --- isPipelineTerminal ---

    @Test
    fun `isPipelineTerminal returns true for Finished`() {
        assertTrue(MessageGenerationState.Finished(ModelType.MAIN).isPipelineTerminal)
    }

    @Test
    fun `isPipelineTerminal returns true for Failed`() {
        assertTrue(MessageGenerationState.Failed(RuntimeException("err"), ModelType.MAIN).isPipelineTerminal)
    }

    @Test
    fun `isPipelineTerminal returns true for Blocked`() {
        assertTrue(MessageGenerationState.Blocked("reason", ModelType.MAIN).isPipelineTerminal)
    }

    @Test
    fun `isPipelineTerminal returns true for StepCompleted with FINAL step`() {
        val state = MessageGenerationState.StepCompleted(
            stepOutput = "",
            modelDisplayName = "Test",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.FINAL,
        )
        assertTrue(state.isPipelineTerminal)
    }

    @Test
    fun `isPipelineTerminal returns false for StepCompleted with non-FINAL step`() {
        val state = MessageGenerationState.StepCompleted(
            stepOutput = "",
            modelDisplayName = "Test",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE,
        )
        assertFalse(state.isPipelineTerminal)
    }

    @Test
    fun `isPipelineTerminal returns false for Processing`() {
        assertFalse(MessageGenerationState.Processing(ModelType.MAIN).isPipelineTerminal)
    }

    @Test
    fun `isPipelineTerminal returns false for GeneratingText`() {
        assertFalse(MessageGenerationState.GeneratingText("delta", ModelType.MAIN).isPipelineTerminal)
    }
}