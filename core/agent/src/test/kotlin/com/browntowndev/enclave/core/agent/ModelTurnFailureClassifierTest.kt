package com.browntowndev.enclave.core.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelTurnFailureClassifierTest {
    @Test
    fun `MAR-K-01 openrouter idle timeout is transient`() {
        val error =
            RuntimeException(
                "SseException: 200: Upstream idle timeout exceeded " +
                    "{code=502, message=Upstream idle timeout exceeded, error_type=provider_unavailable}",
            )

        val report = ModelTurnFailureClassifier.classify(error, partialOutputChars = 1200)

        assertEquals(FfiModelApiErrorClass.TRANSIENT, report.errorClass)
        assertEquals(502.toUShort(), report.httpStatus)
        assertTrue(report.hadPartialOutput)
        assertEquals(1200u, report.partialOutputChars)
    }

    @Test
    fun `MAR-K-02 invalid api key is permanent`() {
        val error = RuntimeException("HTTP 401: Invalid API key")

        val report = ModelTurnFailureClassifier.classify(error, partialOutputChars = 0)

        assertEquals(FfiModelApiErrorClass.PERMANENT, report.errorClass)
        assertEquals(401.toUShort(), report.httpStatus)
        assertFalse(report.hadPartialOutput)
    }

    @Test
    fun `MAR-K-03 rate limit is transient`() {
        val error = RuntimeException("HTTP 429: Rate limit exceeded retry-after=5000")

        val report = ModelTurnFailureClassifier.classify(error, partialOutputChars = 0)

        assertEquals(FfiModelApiErrorClass.TRANSIENT, report.errorClass)
        assertEquals(429.toUShort(), report.httpStatus)
        assertEquals(5000uL, report.retryAfterMs)
    }

    @Test
    fun `MAR-K-05 http 408 is transient`() {
        val report =
            ModelTurnFailureClassifier.classify(
                RuntimeException("HTTP 408: Request Timeout"),
                partialOutputChars = 0,
            )

        assertEquals(FfiModelApiErrorClass.TRANSIENT, report.errorClass)
        assertEquals(408.toUShort(), report.httpStatus)
    }

    @Test
    fun `MAR-K-06 unknown internal error defaults to transient retry`() {
        val report =
            ModelTurnFailureClassifier.classify(
                IllegalStateException("Unexpected serializer state"),
                partialOutputChars = 0,
            )

        assertEquals(FfiModelApiErrorClass.TRANSIENT, report.errorClass)
    }

    @Test
    fun `MAR-K-07 interrupted io stream failed is transient with partial output`() {
        val root = java.io.InterruptedIOException("Stream failed")
        val error = RuntimeException("Stream failed", root)

        val report = ModelTurnFailureClassifier.classify(error, partialOutputChars = 842)

        assertEquals(FfiModelApiErrorClass.TRANSIENT, report.errorClass)
        assertTrue(report.hadPartialOutput)
        assertEquals(842u, report.partialOutputChars)
        assertTrue(report.message.contains("InterruptedIOException"))
        assertTrue(report.message.contains("Stream failed"))
    }
}