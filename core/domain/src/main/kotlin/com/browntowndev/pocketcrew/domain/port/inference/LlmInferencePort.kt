package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

/**
 * Core domain abstraction for LLM inference.
 * The implementation (in the inference layer) will wrap LiteRT-LM or other inference runtimes.
 */
interface LlmInferencePort {
    /**
     * Sends a prompt to the LLM and returns a flow of events.
     * @param prompt The prompt to send to the LLM.
     * @param closeConversation Whether to close the conversation after sending the prompt.
     */
    fun sendPrompt(
        prompt: String,
        closeConversation: Boolean = false,
    ): Flow<InferenceEvent>

    /**
     * Sends a prompt with per-response generation options.
     * @param prompt The prompt to send to the LLM.
     * @param options Generation options for this specific request.
     * @param closeConversation Whether to close the conversation after sending the prompt.
     */
    fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean = false,
    ): Flow<InferenceEvent>

    /**
     * Replaces the entire conversation history with the given messages.
     * Used for rehydrating context from database on new session.
     *
     * @param messages List of messages to set as history (excluding system prompt)
     */
    suspend fun setHistory(messages: List<ChatMessage>)

    /**
     * Closes the underlying session and releases resources. Both the engine
     * and conversation are closed.
     */
    suspend fun closeSession()
}

/**
 * Represents the discrete states of a streaming LLM response.
 * This is a pure domain type with no framework dependencies.
 */
sealed interface InferenceEvent {
    /**
     * Emitted when the on-device engine is being loaded from disk or initialized.
     * @param modelType The type of model being loaded.
     */
    data class EngineLoading(
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when the engine is loaded and waiting for the first token.
     * @param modelType The type of model that is processing.
     */
    data class Processing(
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when the model is generating reasoning/Chain-of-Thought.
     * @param chunk The raw chunk of thought just generated.
     * @param modelType The type of model used to generate this chunk.
     */
    data class Thinking(
        val chunk: String,
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when the model is generating the actual user-facing response.
     * @param chunk The raw text chunk just generated.
     * @param modelType The type of model used to generate this chunk.
     */
    data class PartialResponse(
        val chunk: String,
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when a web search tool execution completes with results.
     * @param sources The list of search results returned by the provider.
     * @param modelType The type of model that triggered the search.
     */
    data class WebSearchResults(
        val sources: List<com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource>,
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when an artifact generation tool execution completes.
     * @param artifacts The list of artifacts generated.
     * @param modelType The type of model that triggered the generation.
     */
    data class Artifacts(
        val artifacts: List<com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest>,
        val modelType: ModelType,
    ) : InferenceEvent

    data class Media(
        val mediaUris: List<String>,
        val mediaIds: List<String> = emptyList(),
        val mediaTypes: List<String> = emptyList(),
        val prompts: List<String> = emptyList(),
        val modelType: ModelType,
    ) : InferenceEvent

    data class CompactionApplied(
        val tier: String,
        val tokensBefore: Int,
        val tokensAfter: Int,
        val modelType: ModelType,
        val trigger: String,
    ) : InferenceEvent

    data class PreCompact(
        val trigger: String,
        val modelType: ModelType,
    ) : InferenceEvent

    data class PostCompact(
        val trigger: String,
        val summaryRef: String?,
        val historyRef: String?,
        val modelType: ModelType,
    ) : InferenceEvent

    data class ToolRepaired(
        val originalName: String,
        val resolvedName: String,
        val confidence: Float,
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when the generation is complete.
     * @param modelType The type of model used to generate this chunk.
     */
    data class Finished(
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when generation is blocked by safety checks.
     * @param reason The reason for blocking.
     */
    data class SafetyBlocked(
        val reason: String,
        val modelType: ModelType,
    ) : InferenceEvent

    /**
     * Emitted when an error occurs during generation.
     * @param cause The exception that caused the error.
     */
    data class Error(
        val cause: Throwable,
        val modelType: ModelType,
    ) : InferenceEvent
}
