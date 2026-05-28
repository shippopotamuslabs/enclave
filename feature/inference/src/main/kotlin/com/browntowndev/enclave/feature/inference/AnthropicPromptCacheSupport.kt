package com.browntowndev.enclave.feature.inference

import com.anthropic.models.messages.CacheControlEphemeral
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy

internal object AnthropicPromptCacheSupport {
    fun supportsPromptCaching(modelId: String): Boolean = ApiProviderModelPolicy.supportsAnthropicPromptCaching(modelId)

    fun ephemeralCacheControl(): CacheControlEphemeral = CacheControlEphemeral.builder().build()

    /**
     * Index of the last message in the stable history prefix (excludes the live turn).
     * Cache breakpoint #3 belongs here, not on the live user message that carries volatile sidecars.
     */
    fun stableHistoryTailIndex(sessionMessages: List<AgentLoopMessage>): Int? =
        if (sessionMessages.size >= 2) {
            sessionMessages.size - 2
        } else {
            null
        }

    fun shouldAttachMessageCacheBreakpoint(
        messageIndex: Int,
        stableTailIndex: Int?,
    ): Boolean = stableTailIndex != null && messageIndex == stableTailIndex
}
