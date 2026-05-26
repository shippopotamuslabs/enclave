package com.browntowndev.enclave.feature.chat.stats

import com.browntowndev.enclave.domain.model.telemetry.ChatTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.MessageTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import javax.inject.Inject

class ChatTelemetryUiMapper
    @Inject
    constructor() {
        fun mapResponseStats(summary: MessageTelemetrySummary): ResponseStatsSheetUiState {
            if (summary.requestCount == 0) {
                return ResponseStatsSheetUiState(
                    title = "Response statistics",
                    availabilityMessage = "Telemetry unavailable for this response",
                    providerLabel = NOT_AVAILABLE,
                    modelLabel = NOT_AVAILABLE,
                    metricRows =
                        listOf(
                            responseMetric("Provider", NOT_AVAILABLE),
                            responseMetric("Cost", NOT_AVAILABLE),
                        ),
                )
            }
            val row = summary.requestRows.singleOrNull()
            return ResponseStatsSheetUiState(
                title = "Response statistics",
                providerLabel = summary.providerLabel ?: row?.providerLabel ?: NOT_AVAILABLE,
                modelLabel = summary.modelLabel ?: row?.modelLabel ?: NOT_AVAILABLE,
                metricRows = responseMetricRows(summary),
            )
        }

        fun mapChatStats(summary: ChatTelemetrySummary): ChatStatsSheetUiState =
            ChatStatsSheetUiState(
                title = "Chat statistics",
                summaryRows =
                    listOf(
                        summaryMetric("Total cost", formatCost(summary.totalCostUsd, hasAnyCost = summary.totalCostUsd > BigDecimal.ZERO)),
                        summaryMetric("Average TTFT", formatTtft(summary.averageTtftMs)),
                        summaryMetric("Average output speed", formatSpeed(summary.averageOutputTokensPerSecond)),
                        summaryMetric("Output tokens", summary.totalOutputTokens.toString()),
                        summaryMetric("Reasoning tokens", summary.totalReasoningTokens.toString()),
                        summaryMetric("Cache hit rate", formatPercent(summary.cacheHitRatePercent)),
                    ),
                providerBreakdownRows =
                    summary.providerBreakdown.map { row ->
                        ProviderBreakdownRowUi(
                            label = row.providerLabel,
                            costLabel =
                                formatCost(
                                    row.totalCostUsd,
                                    hasAnyCost = row.totalCostUsd != null,
                                ),
                            turnCount = row.turnCount,
                            requestCount = row.requestCount.takeIf { it > 1 },
                        )
                    },
                modelBreakdownRows =
                    summary.modelBreakdown.map { row ->
                        ModelBreakdownRowUi(
                            label = row.modelLabel,
                            costLabel =
                                formatCost(
                                    row.totalCostUsd,
                                    hasAnyCost = row.totalCostUsd != null,
                                ),
                            outputTokens = row.outputTokens,
                        )
                    },
            )

        private fun responseMetricRows(summary: MessageTelemetrySummary): List<ResponseMetricRowUi> {
            val rows = summary.requestRows
            val single = rows.singleOrNull()
            val metrics =
                mutableListOf(
                    responseMetric("Provider", summary.providerLabel ?: single?.providerLabel ?: NOT_AVAILABLE),
                    responseMetric("Model", summary.modelLabel ?: single?.modelLabel ?: NOT_AVAILABLE),
                    responseMetric("Model requests", summary.requestCount.toString()),
                    responseMetric("Time to first token", formatTtft(summary.averageTtftMs ?: single?.ttftMs())),
                    responseMetric("Output speed", formatSpeed(summary.averageOutputTokensPerSecond ?: single?.outputTokensPerSecond())),
                    responseMetric("Output tokens", formatInt(summary.totalOutputTokens ?: single?.outputTokens)),
                    responseMetric("Reasoning tokens", formatInt(summary.totalReasoningTokens ?: single?.reasoningTokens)),
                    responseMetric("Input tokens", formatInt(summary.totalInputTokens ?: single?.inputTokens)),
                    responseMetric(
                        "Cache read input tokens",
                        formatInt(summary.totalCacheReadInputTokens ?: single?.cacheReadInputTokens),
                    ),
                    responseMetric("Cache hit rate", formatPercent(summary.cacheHitRatePercent)),
                    responseMetric("Cost", formatCost(summary.totalCostUsd, summary.costStatus)),
                    responseMetric("Cost status", formatCostStatus(summary.costStatus)),
                )
            val costSource = rows.singleOrNull()?.costSource
            if (costSource == TelemetryCostSource.SAVED_MODEL_PRICING) {
                metrics.add(responseMetric("Cost source", "Saved model pricing"))
            }
            return metrics
        }

        private fun TurnTelemetry.ttftMs(): Long? =
            firstOutputTokenAtMs?.let { first -> first - requestStartedAtMs }

        private fun TurnTelemetry.outputTokensPerSecond(): Double? {
            val output = outputTokens ?: return null
            val first = firstOutputTokenAtMs ?: return null
            val completed = completedAtMs ?: return null
            val durationMs = completed - first
            if (durationMs <= 0L) return null
            return (output.toDouble() / (durationMs.toDouble() / 1000.0))
                .toBigDecimal()
                .setScale(1, RoundingMode.HALF_UP)
                .toDouble()
        }

        private fun responseMetric(
            label: String,
            value: String,
        ) = ResponseMetricRowUi(label = label, value = value)

        private fun summaryMetric(
            label: String,
            value: String,
        ) = SummaryMetricRowUi(label = label, value = value)

        private fun formatCost(
            value: BigDecimal?,
            costStatus: TelemetryCostStatus = TelemetryCostStatus.EXACT,
        ): String =
            when {
                value != null -> "$" + value.setScale(6, RoundingMode.HALF_UP).toPlainString()
                costStatus == TelemetryCostStatus.ESTIMATED -> UNKNOWN
                costStatus == TelemetryCostStatus.UNAVAILABLE -> NOT_AVAILABLE
                else -> UNKNOWN
            }

        private fun formatCost(
            value: BigDecimal?,
            hasAnyCost: Boolean,
        ): String =
            when {
                value != null -> "$" + value.setScale(6, RoundingMode.HALF_UP).toPlainString()
                !hasAnyCost -> UNKNOWN
                else -> UNKNOWN
            }

        private fun formatCostStatus(status: TelemetryCostStatus): String =
            when (status) {
                TelemetryCostStatus.EXACT -> "Exact"
                TelemetryCostStatus.ESTIMATED -> "Estimated"
                TelemetryCostStatus.UNAVAILABLE -> "Unavailable"
            }

        private fun formatTtft(ms: Long?): String =
            if (ms == null) {
                NOT_AVAILABLE
            } else {
                "$ms ms"
            }

        private fun formatSpeed(speed: Double?): String =
            if (speed == null) {
                NOT_AVAILABLE
            } else {
                String.format(Locale.US, "%.1f tok/s", speed)
            }

        private fun formatPercent(value: Double?): String =
            if (value == null) {
                NOT_AVAILABLE
            } else {
                String.format(Locale.US, "%.1f%%", value)
            }

        private fun formatInt(value: Int?): String = value?.toString() ?: NOT_AVAILABLE

        companion object {
            private const val NOT_AVAILABLE = "Not available"
            private const val UNKNOWN = "Unknown"
        }
    }
