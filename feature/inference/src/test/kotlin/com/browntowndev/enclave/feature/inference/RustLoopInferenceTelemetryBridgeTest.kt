package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RustLoopInferenceTelemetryBridgeTest {
    @Test
    fun `rust loop telemetry surfaces on InferenceEvent TelemetryCaptured`() {
        val turnResult =
            AgentModelTurnResult(
                assistantText = "done",
                toolCalls = emptyList(),
                finishReason = AgentFinishReason.Stop,
                telemetry = TelemetryTestFixtures.telOpenAi1,
            )
        val event =
            InferenceEvent.TelemetryCaptured(
                telemetry = turnResult.telemetry!!,
                modelType = ModelType.FAST,
            )

        assertTrue(event is InferenceEvent.TelemetryCaptured)
        assertEquals(TelemetryTestFixtures.mOpenAi1, event.telemetry.messageId)
        assertEquals("0.002500", event.telemetry.costUsd?.toPlainString())
    }
}
