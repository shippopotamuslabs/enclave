package com.browntowndev.enclave.core.data.remote.telemetry

import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryOtlpClientImpl
    @Inject
    constructor(
        private val transport: TelemetryOtlpTransport,
    ) : TelemetryOtlpClient {
        override suspend fun uploadBatch(events: List<TelemetryEventEntity>): Result<Unit> {
            val payload = buildOtlpRequestBodyForTest(events)
            return transport.send(payload)
        }

        fun buildOtlpRequestBodyForTest(events: List<TelemetryEventEntity>): String =
            TelemetryOtlpJsonEncoder.buildExportLogsRequest(events)
    }
