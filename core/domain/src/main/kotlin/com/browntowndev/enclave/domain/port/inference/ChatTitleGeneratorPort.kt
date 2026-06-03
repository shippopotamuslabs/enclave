package com.browntowndev.enclave.domain.port.inference

/**
 * Domain port for on-device chat title generation.
 *
 * Implementations run a small auxiliary model (ONNX Runtime, encoder-decoder) on a
 * background dispatcher, in parallel with the main chat LLM and outside the inference
 * lock. The generated title is an *upgrade* over the truncation fallback produced at
 * chat-creation time, so any failure must degrade silently.
 */
interface ChatTitleGeneratorPort {
    /**
     * Generates a short, natural chat title from the first user [prompt].
     *
     * @return the generated title, or `null` on failure / low-confidence output, in which
     *   case the caller keeps the existing (truncated) title.
     */
    suspend fun generateTitle(prompt: String): String?

    /** No-op implementation: always falls back. Used as a default and in tests. */
    object Dummy : ChatTitleGeneratorPort {
        override suspend fun generateTitle(prompt: String): String? = null
    }
}
