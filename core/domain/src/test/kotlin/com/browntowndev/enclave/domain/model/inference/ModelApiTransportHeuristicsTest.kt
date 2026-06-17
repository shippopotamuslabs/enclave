package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelApiTransportHeuristicsTest {
    @Test
    fun `idle timeout is transient transport message`() {
        assertTrue(
            ModelApiTransportHeuristics.isTransientTransportMessage(
                "sseexception: upstream idle timeout exceeded provider_unavailable",
            ),
        )
    }

    @Test
    fun `http 408 is transient status`() {
        assertTrue(ModelApiTransportHeuristics.isTransientHttpStatus(408))
        assertFalse(ModelApiTransportHeuristics.isPermanentHttpStatus(408))
    }

    @Test
    fun `recoverable stream termination includes idle timeout`() {
        assertTrue(
            ModelApiTransportHeuristics.isRecoverableStreamTermination(
                "Upstream idle timeout exceeded",
            ),
        )
    }

    @Test
    fun `stream failed is transient transport message`() {
        assertTrue(
            ModelApiTransportHeuristics.isTransientTransportMessage(
                "interruptedioexception: stream failed [stream failed]",
            ),
        )
    }

    @Test
    fun `interrupted io stream failed should retry model api failure`() {
        val root = java.io.InterruptedIOException("Stream failed")
        assertTrue(
            ModelApiTransportHeuristics.shouldRetryModelApiFailure(
                error = root,
                normalizedMessage = "interruptedioexception: stream failed [stream failed]",
                httpStatus = null,
            ),
        )
    }

    @Test
    fun `invalid api key should not retry model api failure`() {
        assertFalse(
            ModelApiTransportHeuristics.shouldRetryModelApiFailure(
                error = RuntimeException("HTTP 401: Invalid API key"),
                normalizedMessage = "http 401: invalid api key",
                httpStatus = 401,
            ),
        )
    }

    @Test
    fun `unknown internal error should retry model api failure`() {
        assertTrue(
            ModelApiTransportHeuristics.shouldRetryModelApiFailure(
                error = IllegalStateException("Unexpected serializer state"),
                normalizedMessage = "illegalstateexception: unexpected serializer state",
                httpStatus = null,
            ),
        )
    }
}