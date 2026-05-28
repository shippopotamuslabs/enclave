package com.browntowndev.enclave.core.data.remote.telemetry

import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TelemetryOtlpJsonEncoderTest {
    @Test
    fun `buildExportLogsRequest uses OTLP resourceLogs envelope`() {
        val payload =
            TelemetryOtlpJsonEncoder.buildExportLogsRequest(
                listOf(
                    TelemetryEventEntity(
                        id = "evt-1",
                        eventType = "InferenceOutcome",
                        payloadJson = "{}",
                        severity = "INFO",
                        timestampMs = 1716854400000L,
                    ),
                ),
            )
        assertTrue(payload.contains("resourceLogs"))
        assertTrue(payload.contains("enclave.event.id"))
        assertTrue(payload.contains("evt-1"))
        assertTrue(payload.contains("device.manufacturer"))
        assertTrue(payload.contains("device.model.name"))
    }

    @Test
    fun `resolveLogsEndpoint appends v1 logs path`() {
        val transport =
            OkHttpTelemetryOtlpTransport(
                okHttpClient = okhttp3.OkHttpClient(),
                config =
                    com.browntowndev.enclave.core.data.sync.TelemetryUploadConfig(
                        endpointUrl = "https://otlp.example.com/otlp",
                        apiKey = "test-key",
                    ),
                loggingPort = mockk(relaxed = true),
            )
        assertEquals(
            "https://otlp.example.com/otlp/v1/logs",
            transport.resolveLogsEndpoint("https://otlp.example.com/otlp"),
        )
    }
}
