package com.browntowndev.enclave.app.telemetry

import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import java.util.UUID

internal object ExceptionRecordFactory {
    const val REDACTED = "redacted"

    fun create(
        tag: String,
        message: String,
        throwable: Throwable,
        detailedTrackingEnabled: Boolean,
    ): ExceptionRecord =
        if (detailedTrackingEnabled) {
            ExceptionRecord(
                eventId = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                tag = tag,
                message = message,
                throwableType = throwable::class.java.name,
                stackTrace = throwable.stackTraceToString(),
                severity = TelemetrySeverity.ERROR,
            )
        } else {
            ExceptionRecord(
                eventId = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                tag = REDACTED,
                message = REDACTED,
                throwableType = throwable::class.java.name,
                stackTrace = REDACTED,
                severity = TelemetrySeverity.ERROR,
            )
        }
}
