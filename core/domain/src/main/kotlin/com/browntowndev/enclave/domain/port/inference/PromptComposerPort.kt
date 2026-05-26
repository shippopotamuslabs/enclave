package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy

/**
 * Port for composing system prompts with tool contracts and memory context.
 */
interface PromptComposerPort {
    fun compose(
        baseSystemPrompt: String?,
        toolCatalog: List<ToolCatalogEntry>,
        activeTools: List<ToolDefinition>,
        currentChatId: String? = null,
        strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
        coreMemories: List<Memory> = emptyList(),
        retrievedMemories: List<Memory> = emptyList(),
        retrievedSearchResults: String? = null,
        retrievedXSearchResults: String? = null,
    ): String

    /**
     * Query-specific memories and pre-retrieval search payloads. Must not be folded into [compose].
     */
    fun composeVolatileTurnContext(
        retrievedMemories: List<Memory> = emptyList(),
        retrievedSearchResults: String? = null,
        retrievedXSearchResults: String? = null,
    ): String? = null
}
