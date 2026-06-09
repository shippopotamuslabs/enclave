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
}