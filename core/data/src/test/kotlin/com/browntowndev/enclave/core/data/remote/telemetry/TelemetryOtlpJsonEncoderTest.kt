package com.browntowndev.enclave.core.data.remote.telemetry

import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
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
                        eventType = "INFERENCE_OUTCOME",
                        timestampMs = 1L,
                        severity = "INFO",
                        payloadJson = "{}",
                    ),
                ),
            )
        assertTrue(payload.contains("resourceLogs"))
        assertTrue(payload.contains("enclave.event.id"))
        assertTrue(payload.contains("evt-1"))
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
            )
        assertEquals(
            "https://otlp.example.com/otlp/v1/logs",
            transport.resolveLogsEndpoint("https://otlp.example.com/otlp"),
        )
    }
}
