package com.browntowndev.enclave.feature.inference

/**
 * Merges per-turn context sidecars for providers that prepend volatile text on the live user tail
 * (Anthropic, Google). OpenAI/xAI/OpenRouter deliver pre-retrieval via [PreRetrievalContextSidecar].
 */
internal object VolatileTurnContextWireSupport {
    fun combinedLatestUserPrepends(
        volatileTurnContext: String?,
        contextSidecars: String?,
    ): String? =
        listOfNotNull(
            volatileTurnContext?.trim()?.takeIf { it.isNotEmpty() },
            contextSidecars?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(separator = "\n\n").takeIf { it.isNotBlank() }

    @Deprecated("Use RustLoopInferenceService.buildInitialMessages instead")
    fun prependToUserContent(
        userContent: String,
        volatileTurnContext: String?,
    ): String {
        val prefix = volatileTurnContext?.trim()?.takeIf { it.isNotEmpty() } ?: return userContent
        return "$prefix\n\n$userContent"
    }
}
