package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
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
    inputTokens: Int? = null,
    cacheReadInputTokens: Int? = null,
    baseUrl: String? = null,
) {
    val port = this ?: return
    val completedAtMs = System.currentTimeMillis()
    val latencyMs = (completedAtMs - startedAtMs).coerceAtLeast(0)
    val cacheHitRate =
        cacheReadInputTokens?.let { cached ->
            val input = inputTokens ?: 0
            val total =
                if (TelemetryProviderKey.fromLabel(provider).reportsCacheExclusiveInput) {
                    // Only Anthropic reports input_tokens excluding cache; add cache read back.
                    input + cached
                } else {
                    input
                }
            if (total > 0) cached.toDouble() / total.toDouble() else 0.0
        }
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
            cacheHitRate = cacheHitRate,
            completedAtMs = completedAtMs,
            baseUrl = baseUrl,
        )
    }
}

private suspend fun emitProviderExecutionMetrics(
    port: TelemetryEventPort,
    provider: String,
    latencyMs: Long,
    tokenCount: Int?,
    cacheHitRate: Double?,
    completedAtMs: Long,
    baseUrl: String? = null,
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
        val tokensPerSec =
            if (latencyMs > 0) {
                tokens.toDouble() / (latencyMs.toDouble() / 1000.0)
            } else {
                0.0
            }
        port.emitProviderMetric(
            ProviderMetric(
                eventId = UUID.randomUUID().toString(),
                timestampMs = completedAtMs,
                provider = provider,
                metricName = "tokens_per_second",
                metricValue = tokensPerSec,
                severity = TelemetrySeverity.INFO,
            ),
        )
    }
    cacheHitRate?.let { rate ->
        port.emitProviderMetric(
            ProviderMetric(
                eventId = UUID.randomUUID().toString(),
                timestampMs = completedAtMs,
                provider = provider,
                metricName = "cache_hit_rate",
                metricValue = rate,
                severity = TelemetrySeverity.INFO,
                baseUrl = baseUrl?.takeIf { it.isNotBlank() && !provider.equals("Llama", ignoreCase = true) },
            ),
        )
    }
}
