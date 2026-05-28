package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import java.math.BigDecimal
import java.util.UUID

internal suspend fun TelemetryEventPort?.emitInferenceExecutionOutcome(
    startedAtMs: Long,
    provider: String,
    modelId: String,
    success: Boolean,
    errorMessage: String? = null,
    tokenCount: Int? = null,
    costUsd: BigDecimal? = null,
) {
    val port = this ?: return
    val completedAtMs = System.currentTimeMillis()
    val latencyMs = (completedAtMs - startedAtMs).coerceAtLeast(0)
    runCatching {
        port.emitInferenceOutcome(
            InferenceOutcome(
                eventId = UUID.randomUUID().toString(),
                timestampMs = completedAtMs,
                provider = provider,
                modelId = modelId,
                latencyMs = latencyMs,
                tokenCount = tokenCount,
                costUsd = costUsd,
                success = success,
                errorMessage = errorMessage,
                severity = if (success) TelemetrySeverity.INFO else TelemetrySeverity.ERROR,
            ),
        )
        emitProviderExecutionMetrics(
            port = port,
            provider = provider,
            latencyMs = latencyMs,
            tokenCount = tokenCount,
            completedAtMs = completedAtMs,
        )
    }
}

private suspend fun emitProviderExecutionMetrics(
    port: TelemetryEventPort,
    provider: String,
    latencyMs: Long,
    tokenCount: Int?,
    completedAtMs: Long,
) {
    port.emitProviderMetric(
        ProviderMetric(
            eventId = UUID.randomUUID().toString(),
            timestampMs = completedAtMs,
            provider = provider,
            metricName = "latency_ms",
            metricValue = latencyMs.toDouble(),
            severity = TelemetrySeverity.INFO,
        ),
    )
    tokenCount?.let { tokens ->
        port.emitProviderMetric(
            ProviderMetric(
                eventId = UUID.randomUUID().toString(),
                timestampMs = completedAtMs,
                provider = provider,
                metricName = "output_tokens",
                metricValue = tokens.toDouble(),
                severity = TelemetrySeverity.INFO,
            ),
        )
    }
}
