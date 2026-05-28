package com.browntowndev.enclave.domain.model.telemetry

import java.math.BigDecimal

data class InferenceOutcome(
    val eventId: String,
    val timestampMs: Long,
    val provider: String,
    val modelId: String,
    val latencyMs: Long,
    val tokenCount: Int?,
    val costUsd: BigDecimal?,
    val success: Boolean,
    val errorMessage: String?,
    val severity: TelemetrySeverity,
)

data class ToolOutcome(
    val eventId: String,
    val timestampMs: Long,
    val toolName: String,
    val latencyMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val severity: TelemetrySeverity,
)

data class ExceptionRecord(
    val eventId: String,
    val timestampMs: Long,
    val tag: String,
    val message: String,
    val throwableType: String,
    val stackTrace: String,
    val severity: TelemetrySeverity,
)

data class ProviderMetric(
    val eventId: String,
    val timestampMs: Long,
    val provider: String,
    val metricName: String,
    val metricValue: Double,
    val severity: TelemetrySeverity,
)
