package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake implementation of PipelineExecutorPort for testing CREW mode.
 * Allows controlling the flow of MessageGenerationState events.
 */
class FakePipelineExecutor : PipelineExecutorPort {
    
    private val eventsToEmit = mutableListOf<MessageGenerationState>()
    var shouldThrow: Throwable? = null
    var executePipelineCalled = false
    var stopPipelineCalled = false
    var resumeFromStateCalled = false
    var lastChatId: String? = null
    var lastUserMessage: String? = null
    var lastPipelineId: String? = null
    
    override fun executePipeline(
        chatId: String,
        userMessage: String
    ): Flow<MessageGenerationState> = flow {
        executePipelineCalled = true
        lastChatId = chatId
        lastUserMessage = userMessage
        
        if (shouldThrow != null) {
            throw shouldThrow!!
        }
        
        for (event in eventsToEmit) {
            emit(event)
        }
    }
    
    override suspend fun stopPipeline(pipelineId: String) {
        stopPipelineCalled = true
        lastPipelineId = pipelineId
    }
    
    override suspend fun resumeFromState(
        chatId: String,
        pipelineId: String,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Flow<MessageGenerationState> {
        resumeFromStateCalled = true
        lastChatId = chatId
        lastPipelineId = pipelineId
        
        return executePipeline(chatId, "")
    }
    
    /**
     * Add a Processing event to be emitted.
     * Used to simulate a step starting (before thinking/generation).
     */
    fun addProcessingEvent(modelType: ModelType = ModelType.DRAFT_ONE) {
        eventsToEmit.add(MessageGenerationState.Processing(modelType))
    }
    
    /**
     * Add a ThinkingLive event to be emitted.
     */
    fun addThinkingLiveEvent(chunk: String, modelType: ModelType = ModelType.DRAFT_ONE) {
        eventsToEmit.add(MessageGenerationState.ThinkingLive(chunk, modelType))
    }
    
    /**
     * Add a GeneratingText event to be emitted.
     */
    fun addGeneratingTextEvent(text: String, modelType: ModelType = ModelType.DRAFT_ONE) {
        eventsToEmit.add(MessageGenerationState.GeneratingText(text, modelType))
    }
    
    /**
     * Add a StepCompleted event to be emitted.
     */
    fun addStepCompletedEvent(
        stepOutput: String,
        modelDisplayName: String,
        modelType: ModelType = ModelType.DRAFT_ONE,
        stepType: PipelineStep = PipelineStep.DRAFT_ONE
    ) {
        eventsToEmit.add(
            MessageGenerationState.StepCompleted(
                stepOutput = stepOutput,
                modelDisplayName = modelDisplayName,
                modelType = modelType,
                stepType = stepType
            )
        )
    }
    
    /**
     * Add a Finished event to be emitted.
     */
    fun addFinishedEvent(modelType: ModelType = ModelType.DRAFT_ONE) {
        eventsToEmit.add(MessageGenerationState.Finished(modelType))
    }
    
    /**
     * Add a Blocked event to be emitted.
     */
    fun addBlockedEvent(reason: String, modelType: ModelType = ModelType.DRAFT_ONE) {
        eventsToEmit.add(MessageGenerationState.Blocked(reason, modelType))
    }
    
    /**
     * Add a Failed event to be emitted.
     */
    fun addFailedEvent(error: Throwable, modelType: ModelType = ModelType.DRAFT_ONE) {
        eventsToEmit.add(MessageGenerationState.Failed(error, modelType))
    }
    
    /**
     * Configure a complete CREW pipeline simulation (4 steps).
     */
    fun configureCompleteCrewPipeline() {
        eventsToEmit.clear()

        // DRAFT_ONE
        addProcessingEvent(ModelType.DRAFT_ONE)
        addThinkingLiveEvent("# Analyzing the problem...\n", ModelType.DRAFT_ONE)
        addThinkingLiveEvent("Let me break this down step by step.", ModelType.DRAFT_ONE)
        addGeneratingTextEvent("Here is my first approach:", ModelType.DRAFT_ONE)
        addStepCompletedEvent(
            stepOutput = "First approach completed.",
            modelDisplayName = "Draft One",
            modelType = ModelType.DRAFT_ONE,
            stepType = PipelineStep.DRAFT_ONE
        )

        // DRAFT_TWO
        addProcessingEvent(ModelType.DRAFT_TWO)
        addThinkingLiveEvent("# Alternative approach...\n", ModelType.DRAFT_TWO)
        addGeneratingTextEvent("Let me try a different angle:", ModelType.DRAFT_TWO)
        addStepCompletedEvent(
            stepOutput = "Alternative approach completed.",
            modelDisplayName = "Draft Two",
            modelType = ModelType.DRAFT_TWO,
            stepType = PipelineStep.DRAFT_TWO
        )

        // SYNTHESIS
        addProcessingEvent(ModelType.MAIN)
        addThinkingLiveEvent("# Combining insights...\n", ModelType.MAIN)
        addGeneratingTextEvent("Combining both approaches:", ModelType.MAIN)
        addStepCompletedEvent(
            stepOutput = "Synthesis completed.",
            modelDisplayName = "Main",
            modelType = ModelType.MAIN,
            stepType = PipelineStep.SYNTHESIS
        )

        // FINAL
        addProcessingEvent(ModelType.FINAL_SYNTHESIS)
        addThinkingLiveEvent("# Final review...\n", ModelType.FINAL_SYNTHESIS)
        addGeneratingTextEvent("Final response:\n\n", ModelType.FINAL_SYNTHESIS)
        addFinishedEvent(ModelType.FINAL_SYNTHESIS)
    }
    
    fun reset() {
        eventsToEmit.clear()
        shouldThrow = null
        executePipelineCalled = false
        stopPipelineCalled = false
        resumeFromStateCalled = false
        lastChatId = null
        lastUserMessage = null
        lastPipelineId = null
    }
}
