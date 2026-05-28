package com.browntowndev.enclave.core.data.remote.telemetry

import com.browntowndev.enclave.core.data.local.TelemetryEventEntity

interface TelemetryOtlpClient {
    suspend fun uploadBatch(events: List<TelemetryEventEntity>): Result<Unit>
}
