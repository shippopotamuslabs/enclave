package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * [LlmInferencePort] fake (ENC-28). Every prompt records its input and replays
 * [eventsToEmit] (a single terminal Finished event by default, which completes a
 * pipeline step with empty output).
 */
class FakeLlmInferencePort : LlmInferencePort {
    data class RecordedPrompt(
        val prompt: String,
        val options: GenerationOptions?,
        val closeConversation: Boolean,
    )

    var eventsToEmit: List<InferenceEvent> = listOf(InferenceEvent.Finished(ModelType.FAST))

    /** When set, prompts return this flow instead of [eventsToEmit] (e.g. a hanging stream). */
    var promptFlowOverride: Flow<InferenceEvent>? = null

    val recordedPrompts = mutableListOf<RecordedPrompt>()
    val historyCalls = mutableListOf<List<ChatMessage>>()
    var closeSessionCount = 0
        private set

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        recordedPrompts += RecordedPrompt(prompt, null, closeConversation)
        return promptFlowOverride ?: eventsToEmit.asFlow()
    }

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> {
        recordedPrompts += RecordedPrompt(prompt, options, closeConversation)
        return promptFlowOverride ?: eventsToEmit.asFlow()
    }

    override suspend fun setHistory(messages: List<ChatMessage>) {
        historyCalls += messages
    }

    override suspend fun closeSession() {
        closeSessionCount++
    }
}
