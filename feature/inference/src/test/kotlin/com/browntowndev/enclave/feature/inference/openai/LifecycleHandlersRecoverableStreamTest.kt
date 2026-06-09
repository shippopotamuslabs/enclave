package com.browntowndev.enclave.feature.inference.openai

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LifecycleHandlersRecoverableStreamTest {
    @Test
    fun `MAR-K-04 openrouter idle timeout is recoverable stream termination`() {
        assertTrue(
            isRecoverableStreamTermination(
                "SseException: 200: Upstream idle timeout exceeded provider_unavailable",
            ),
        )
    }
}