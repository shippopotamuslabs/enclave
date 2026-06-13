package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.UtilityOnnxRuntimeRequirements

/**
 * On-device compaction summarizer backed by the utility ONNX lane (LFM2.5).
 *
 * Runs outside the chat inference lock and degrades silently on failure so the
 * agent loop can fall back to the active turn model or FIFO tiers.
 */
interface CompactionSummarizerPort {
    /** Whether the downloaded utility model artifacts are present and checksum-valid. */
    suspend fun isModelInstalled(): Boolean

    /**
     * Generates a compaction summary for a fully assembled [prompt].
     *
     * @return summary text, or `null` on load/decode failure.
     */
    suspend fun summarize(prompt: String): String?

    companion object {
        /** @see UtilityOnnxRuntimeRequirements.LFM2_SESSION_BYTES */
        val ESTIMATED_RUNTIME_BYTES: Long
            get() = UtilityOnnxRuntimeRequirements.LFM2_SESSION_BYTES
    }

    object Dummy : CompactionSummarizerPort {
        override suspend fun isModelInstalled(): Boolean = false

        override suspend fun summarize(prompt: String): String? = null
    }
}
