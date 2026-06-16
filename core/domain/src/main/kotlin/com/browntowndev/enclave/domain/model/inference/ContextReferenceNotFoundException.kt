package com.browntowndev.enclave.domain.model.inference

/**
 * Raised when [AgentLoopPort.readContextReference] cannot resolve an exact reference id.
 */
class ContextReferenceNotFoundException(
    val referenceId: String,
    cause: Throwable? = null,
) : Exception("Context reference not found: $referenceId", cause)
