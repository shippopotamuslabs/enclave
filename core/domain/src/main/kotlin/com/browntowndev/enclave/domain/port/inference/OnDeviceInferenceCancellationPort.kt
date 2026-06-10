package com.browntowndev.enclave.domain.port.inference

/**
 * Best-effort interrupt of in-flight on-device generation (Llama.cpp / LiteRT).
 * Implementations must be safe to call when no generation is active.
 */
interface OnDeviceInferenceCancellationPort {
    fun cancelActiveGeneration()
}
