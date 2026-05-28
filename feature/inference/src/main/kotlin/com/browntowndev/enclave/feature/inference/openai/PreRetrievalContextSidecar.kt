package com.browntowndev.enclave.feature.inference.openai

/**
 * OpenAI/xAI/OpenRouter pre-search and memory RAG payloads are delivered as ephemeral system
 * sidecars before the live user anchor so transcript USER bytes stay prefix-cache stable.
 */
internal object PreRetrievalContextSidecar {
    const val MARKER: String = "Pre-retrieval context:"

    fun format(volatileTurnContext: String): String {
        val trimmed = volatileTurnContext.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith(MARKER)) {
            trimmed
        } else {
            "$MARKER\n$trimmed"
        }
    }

    fun isSidecarText(text: String): Boolean = text.startsWith(MARKER)
}
