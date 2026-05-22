package com.browntowndev.enclave.domain.model.error

/**
 * Thrown when an I/O error occurs during inference.
 * This is a domain-specific replacement for java.io.IOException.
 */
class InferenceIOException(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
