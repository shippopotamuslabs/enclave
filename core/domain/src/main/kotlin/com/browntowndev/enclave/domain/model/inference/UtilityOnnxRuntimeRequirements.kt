package com.browntowndev.enclave.domain.model.inference

/**
 * Shared RAM budget estimates for utility ONNX lanes (title gen, compaction summarizer).
 */
object UtilityOnnxRuntimeRequirements {
    /** Per-call mmap cost for the LFM2.5 q4 decoder graph (~350 MiB weights + activations). */
    const val LFM2_SESSION_BYTES: Long = 400L * 1024 * 1024

    /**
     * Additional headroom while the chat LLM is loaded and holding the ON_DEVICE lock.
     * Conservative estimate for llama.cpp/LiteRT resident weights on 8–12 GiB devices.
     */
    const val IN_FLIGHT_CHAT_MODEL_BYTES: Long = 512L * 1024 * 1024

    fun requiredHeadroomBytes(chatModelLoaded: Boolean): Long =
        if (chatModelLoaded) {
            LFM2_SESSION_BYTES + IN_FLIGHT_CHAT_MODEL_BYTES
        } else {
            LFM2_SESSION_BYTES
        }
}
