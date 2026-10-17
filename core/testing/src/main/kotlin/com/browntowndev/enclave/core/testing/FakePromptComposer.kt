package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy

/** [PromptComposerPort] fake (ENC-28): returns the base prompt unchanged and records calls. */
class FakePromptComposer : PromptComposerPort {
    data class ComposeCall(
        val baseSystemPrompt: String?,
        val activeTools: List<ToolDefinition>,
        val currentChatId: String?,
    )

    val composeCalls = mutableListOf<ComposeCall>()
    var volatileContext: String? = null

    /** When set, compose returns this instead of the base prompt. */
    var resultOverride: String? = null

    override fun compose(
        baseSystemPrompt: String?,
        toolCatalog: List<ToolCatalogEntry>,
        activeTools: List<ToolDefinition>,
        currentChatId: String?,
        strategy: ToolCallStrategy,
        coreMemories: List<Memory>,
        retrievedMemories: List<Memory>,
        retrievedSearchResults: String?,
        retrievedXSearchResults: String?,
        compactStaticPrompt: Boolean,
    ): String {
        composeCalls += ComposeCall(baseSystemPrompt, activeTools, currentChatId)
        return resultOverride ?: baseSystemPrompt.orEmpty()
    }

    override fun composeVolatileTurnContext(
        retrievedMemories: List<Memory>,
        retrievedSearchResults: String?,
        retrievedXSearchResults: String?,
    ): String? = volatileContext
}
