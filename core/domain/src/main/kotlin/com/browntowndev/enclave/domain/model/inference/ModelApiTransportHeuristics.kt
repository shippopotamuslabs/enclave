package com.browntowndev.enclave.domain.model.inference

/**
 * Shared transport-failure heuristics for model API streaming and retry classification.
 */
object ModelApiTransportHeuristics {
    fun isRecoverableStreamTermination(message: String?): Boolean {
        val normalized = message?.lowercase() ?: return false
        return isTransientTransportMessage(normalized)
    }

    fun isTransientHttpStatus(httpStatus: Int?): Boolean =
        when (httpStatus) {
            408, 429, 502, 503, 504 -> true
            else -> false
        }

    fun isPermanentHttpStatus(httpStatus: Int?): Boolean =
        when (httpStatus) {
            401, 403, 404 -> true
            else -> false
        }

    fun isTransientTransportMessage(normalizedMessage: String): Boolean =
        normalizedMessage.contains("idle timeout") ||
            normalizedMessage.contains("upstream idle timeout") ||
            normalizedMessage.contains("provider_unavailable") ||
            normalizedMessage.contains("rate limit") ||
            normalizedMessage.contains("too many requests") ||
            normalizedMessage.contains("timeout") ||
            normalizedMessage.contains("timed out") ||
            normalizedMessage.contains("connection reset") ||
            normalizedMessage.contains("connection refused") ||
            normalizedMessage.contains("temporarily unavailable") ||
            normalizedMessage.contains("service unavailable") ||
            normalizedMessage.contains("bad gateway") ||
            normalizedMessage.contains("gateway timeout") ||
            normalizedMessage.contains("stream ended unexpectedly") ||
            normalizedMessage.contains("internal stream ended unexpectedly") ||
            normalizedMessage.contains("eof") ||
            normalizedMessage.contains("socket")

    fun isTransientTransportErrorType(error: Throwable): Boolean {
        val name = error::class.simpleName?.lowercase().orEmpty()
        return name.contains("timeout") ||
            name.contains("socket") ||
            name.contains("connect") ||
            name.contains("sse")
    }

    fun isPermanentTransportMessage(normalizedMessage: String): Boolean =
        normalizedMessage.contains("invalid api key") ||
            normalizedMessage.contains("incorrect api key") ||
            normalizedMessage.contains("authentication") ||
            normalizedMessage.contains("unauthorized") ||
            normalizedMessage.contains("permission denied") ||
            normalizedMessage.contains("model not found") ||
            normalizedMessage.contains("unsupported model")
}