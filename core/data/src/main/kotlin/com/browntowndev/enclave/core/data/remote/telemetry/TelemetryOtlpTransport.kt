package com.browntowndev.enclave.core.data.remote.telemetry

/**
 * Network transport for OTLP upload requests. Separated for testability.
 */
fun interface TelemetryOtlpTransport {
    suspend fun send(payload: String): Result<Unit>
}
