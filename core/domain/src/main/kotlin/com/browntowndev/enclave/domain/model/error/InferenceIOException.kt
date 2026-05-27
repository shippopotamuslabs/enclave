package com.browntowndev.enclave.domain.model.error

import java.io.IOException

/**
 * Thrown when an I/O error occurs during inference.
 * Domain-specific extension of java.io.IOException so the agent loop can catch
 * both generic inference I/O failures and provider-specific subtypes
 * (e.g. InferenceNetworkException) via a single clause when desired.
 */
open class InferenceIOException(
    message: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
