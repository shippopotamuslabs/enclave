package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import java.util.UUID

internal suspend fun TelemetryEventPort?.emitToolExecutionOutcome(
    startedAtMs: Long,
    toolName: String,
    success: Boolean,
    errorMessage: String? = null,
) {
    val port = this ?: return
    val completedAtMs = System.currentTimeMillis()
    runCatching {
        port.emitToolOutcome(
            ToolOutcome(
                eventId = UUID.randomUUID().toString(),
                timestampMs = completedAtMs,
                toolName = telemetryToolName(toolName),
                latencyMs = (completedAtMs - startedAtMs).coerceAtLeast(0),
                success = success,
                errorMessage = errorMessage,
                severity = if (success) TelemetrySeverity.INFO else TelemetrySeverity.ERROR,
            ),
        )
    }
}

internal fun telemetryToolName(rawToolName: String): String =
    when (rawToolName) {
        ToolDefinition.WEB_SEARCH.name -> "search"
        ToolDefinition.WEB_EXTRACT.name -> "extract"
        else -> rawToolName.removePrefix("web_")
    }

internal fun toolResultIndicatesFailure(resultJson: String): Boolean = resultJson.contains("\"success\":false")

internal fun toolResultErrorMessage(resultJson: String): String? {
    val marker = "\"error\":\""
    val start = resultJson.indexOf(marker)
    if (start < 0) return null
    val valueStart = start + marker.length
    val valueEnd = resultJson.indexOf('"', valueStart)
    if (valueEnd < 0) return null
    return resultJson.substring(valueStart, valueEnd)
}
