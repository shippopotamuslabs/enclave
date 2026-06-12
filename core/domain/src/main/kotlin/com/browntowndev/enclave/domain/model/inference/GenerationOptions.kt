package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy

/**
 * Per-response generation options passed at request time.
 * For llama.cpp presets, derive [reasoningBudget] from the configured local-model budget when
 * thinking is enabled. Other local runtimes receive zero from the preset path.
 */
data class GenerationOptions(
    val reasoningBudget: Int, // 0 = reasoning OFF, >0 = reasoning ON with budget
    val modelType: ModelType? = null, // The requesting role (FAST, THINKING, etc.)
    val modelId: String? = null,
    val apiProvider: ApiProvider? = null,
    val apiModelPricing: ApiModelSavedPricing? = null,
    val systemPrompt: String? = null,
    /**
     * Per-turn RAG and pre-retrieval payloads (memories, web/X search). Never merged into
     * [systemPrompt]. OpenAI/xAI/OpenRouter deliver this as a frozen system sidecar before the
     * user anchor; Anthropic/Google prepend it on the live user message instead.
     */
    val volatileTurnContext: String? = null,
    val imageUris: List<String> = emptyList(),
    val reasoningEffort: ApiReasoningEffort? = null,
    val temperature: Float? = null,
    val topK: Int? = null,
    val topP: Float? = null,
    val minP: Float? = null,
    val maxTokens: Int? = null,
    val contextWindow: Int? = null,
    val maxTokensDisabled: Boolean = false,
    val temperatureDisabled: Boolean = false,
    val topPDisabled: Boolean = false,
    val topKDisabled: Boolean = false,
    val minPDisabled: Boolean = false,
    val frequencyPenaltyDisabled: Boolean = false,
    val presencePenaltyDisabled: Boolean = false,
    val toolingEnabled: Boolean = false,
    val toolCatalog: List<ToolCatalogEntry> = emptyList(),
    val activeTools: List<ToolDefinition> = emptyList(),
    val toolSearchIndex: ToolSearchIndexSnapshot? = null,
    val toolCallStrategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
    val maxToolCalls: Int = 10,
    val chatId: ChatId? = null,
    val userMessageId: MessageId? = null,
    val assistantMessageId: MessageId? = null,
    /** Monotonic index for each provider HTTP request within one assistant turn (tool loops). */
    val telemetrySequenceIndex: Int = 0,
    val includedFiles: List<AgentIncludedFile> = emptyList(),
    val transcriptRevision: Long? = null,
)

data class AgentIncludedFile(
    val path: String,
    val tokenEstimate: UInt,
    val content: String,
)
