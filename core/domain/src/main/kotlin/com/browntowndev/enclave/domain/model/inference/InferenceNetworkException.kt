package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.model.error.InferenceIOException

/**
 * Provider-specific subtype of [InferenceIOException] thrown when a remote LLM
 * API call fails due to a network/connectivity error. Carries the provider tag
 * so callers (e.g. UI failure surfaces) can render a network-specific message
 * without inspecting the cause chain.
 */
class InferenceNetworkException(
    val provider: String,
    message: String,
    cause: Throwable? = null,
) : InferenceIOException("[$provider] Network error: $message", cause)
