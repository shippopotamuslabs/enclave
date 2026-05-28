package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DirectChatInferenceExecutorTelemetryTest {
    @Test
    fun `inference executor preserves telemetry provider labels when bridging events`() {
        val event =
            InferenceEvent.TelemetryCaptured(
                telemetry = TelemetryTestFixtures.telOpenAi1,
                modelType = ModelType.FAST,
            )
        val state =
            MessageGenerationState.TelemetryCaptured(
                telemetry = event.telemetry,
                modelType = event.modelType,
            )

        assertEquals("OpenAI", state.telemetry.providerLabel)
        assertEquals("gpt-4.1", state.telemetry.modelLabel)
    }
}
