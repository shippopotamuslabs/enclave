package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy

/**
 * Port for composing system prompts with tool contracts and memory context.
 */
interface PromptComposerPort {
    fun compose(
        baseSystemPrompt: String?,
        includeSearchTool: Boolean = true,
        includeXSearchTool: Boolean = false,
        includeImageInspectTool: Boolean = false,
        includeMemoryTools: Boolean = true,
        includeArtifactTool: Boolean = false,
        includeImageGenerationTool: Boolean = false,
        includeVideoGenerationTool: Boolean = false,
        includeMusicGenerationTool: Boolean = false,
        currentChatId: String? = null,
        strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
        coreMemories: List<Memory> = emptyList(),
        retrievedMemories: List<Memory> = emptyList(),
    ): String
}
