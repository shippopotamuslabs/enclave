package com.browntowndev.enclave.domain.model.inference

/**
 * Shared transport-failure heuristics for model API streaming and retry classification.
 */
object ModelApiTransportHeuristics {
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_BAD_GATEWAY = 502
    private const val HTTP_SERVICE_UNAVAILABLE = 503
    private const val HTTP_GATEWAY_TIMEOUT = 504

    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404

    fun isRecoverableStreamTermination(message: String?): Boolean {
        val normalized = message?.lowercase() ?: return false
        return isTransientTransportMessage(normalized)
    }

    fun isTransientHttpStatus(httpStatus: Int?): Boolean =
        when (httpStatus) {
            HTTP_REQUEST_TIMEOUT,
            HTTP_TOO_MANY_REQUESTS,
            HTTP_BAD_GATEWAY,
            HTTP_SERVICE_UNAVAILABLE,
            HTTP_GATEWAY_TIMEOUT,
            -> true
            else -> false
        }

    fun isPermanentHttpStatus(httpStatus: Int?): Boolean =
        when (httpStatus) {
            HTTP_UNAUTHORIZED,
            HTTP_FORBIDDEN,
            HTTP_NOT_FOUND,
            -> true
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
