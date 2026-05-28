package com.browntowndev.enclave.core.data.mapper.telemetry

import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.InferenceOutcome
import com.browntowndev.enclave.domain.model.telemetry.ProviderMetric
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.model.telemetry.ToolOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

private const val EVENT_INFERENCE_OUTCOME = "INFERENCE_OUTCOME"
private const val EVENT_TOOL_OUTCOME = "TOOL_OUTCOME"
private const val EVENT_EXCEPTION_RECORD = "EXCEPTION_RECORD"
private const val EVENT_PROVIDER_METRIC = "PROVIDER_METRIC"

private val telemetryJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

fun InferenceOutcome.toEntity(): TelemetryEventEntity =
    TelemetryEventEntity(
        id = eventId,
        eventType = EVENT_INFERENCE_OUTCOME,
        timestampMs = timestampMs,
        severity = severity.name,
        payloadJson = encodeInferenceOutcome(redactInferenceOutcome(this)),
    )

fun ToolOutcome.toEntity(): TelemetryEventEntity =
    TelemetryEventEntity(
        id = eventId,
        eventType = EVENT_TOOL_OUTCOME,
        timestampMs = timestampMs,
        severity = severity.name,
        payloadJson = encodeToolOutcome(redactToolOutcome(this)),
    )

fun ExceptionRecord.toEntity(): TelemetryEventEntity =
    TelemetryEventEntity(
        id = eventId,
        eventType = EVENT_EXCEPTION_RECORD,
        timestampMs = timestampMs,
        severity = severity.name,
        payloadJson = encodeExceptionRecord(redactExceptionRecord(this)),
    )

fun ProviderMetric.toEntity(): TelemetryEventEntity =
    TelemetryEventEntity(
        id = eventId,
        eventType = EVENT_PROVIDER_METRIC,
        timestampMs = timestampMs,
        severity = severity.name,
        payloadJson = encodeProviderMetric(this),
    )

fun TelemetryEventEntity.toDomain(): Any =
    when (eventType) {
        EVENT_INFERENCE_OUTCOME -> decodeInferenceOutcome(payloadJson)
        EVENT_TOOL_OUTCOME -> decodeToolOutcome(payloadJson)
        EVENT_EXCEPTION_RECORD -> decodeExceptionRecord(payloadJson)
        EVENT_PROVIDER_METRIC -> decodeProviderMetric(payloadJson)
        else -> error("Unsupported telemetry event type: $eventType")
    }

private fun redactInferenceOutcome(outcome: InferenceOutcome): InferenceOutcome =
    outcome.copy(errorMessage = TelemetryPayloadRedactor.redact(outcome.errorMessage))

private fun redactToolOutcome(outcome: ToolOutcome): ToolOutcome =
    outcome.copy(errorMessage = TelemetryPayloadRedactor.redact(outcome.errorMessage))

private fun redactExceptionRecord(record: ExceptionRecord): ExceptionRecord =
    record.copy(
        tag = TelemetryPayloadRedactor.redact(record.tag).orEmpty(),
        message = TelemetryPayloadRedactor.redact(record.message).orEmpty(),
        stackTrace = TelemetryPayloadRedactor.redact(record.stackTrace).orEmpty(),
    )

private fun encodeInferenceOutcome(outcome: InferenceOutcome): String =
    buildJsonObject {
        put("eventId", JsonPrimitive(outcome.eventId))
        put("timestampMs", JsonPrimitive(outcome.timestampMs))
        put("provider", JsonPrimitive(outcome.provider))
        put("modelId", JsonPrimitive(outcome.modelId))
        put("latencyMs", JsonPrimitive(outcome.latencyMs))
        outcome.tokenCount?.let { put("tokenCount", JsonPrimitive(it)) }
        outcome.costUsd?.let { put("costUsd", JsonPrimitive(it.toPlainString())) }
        put("success", JsonPrimitive(outcome.success))
        outcome.errorMessage?.let { put("errorMessage", JsonPrimitive(it)) }
        put("severity", JsonPrimitive(outcome.severity.name))
    }.toString()

private fun encodeToolOutcome(outcome: ToolOutcome): String =
    buildJsonObject {
        put("eventId", JsonPrimitive(outcome.eventId))
        put("timestampMs", JsonPrimitive(outcome.timestampMs))
        put("toolName", JsonPrimitive(outcome.toolName))
        put("latencyMs", JsonPrimitive(outcome.latencyMs))
        put("success", JsonPrimitive(outcome.success))
        outcome.errorMessage?.let { put("errorMessage", JsonPrimitive(it)) }
        put("severity", JsonPrimitive(outcome.severity.name))
        outcome.provider?.let { put("provider", JsonPrimitive(it)) }
        outcome.modelId?.let { put("modelId", JsonPrimitive(it)) }
    }.toString()

private fun encodeExceptionRecord(record: ExceptionRecord): String =
    buildJsonObject {
        put("eventId", JsonPrimitive(record.eventId))
        put("timestampMs", JsonPrimitive(record.timestampMs))
        put("tag", JsonPrimitive(record.tag))
        put("message", JsonPrimitive(record.message))
        put("throwableType", JsonPrimitive(record.throwableType))
        put("stackTrace", JsonPrimitive(record.stackTrace))
        put("severity", JsonPrimitive(record.severity.name))
    }.toString()

private fun encodeProviderMetric(metric: ProviderMetric): String =
    buildJsonObject {
        put("eventId", JsonPrimitive(metric.eventId))
        put("timestampMs", JsonPrimitive(metric.timestampMs))
        put("provider", JsonPrimitive(metric.provider))
        put("metricName", JsonPrimitive(metric.metricName))
        put("metricValue", JsonPrimitive(metric.metricValue))
        put("severity", JsonPrimitive(metric.severity.name))
    }.toString()

private fun decodeInferenceOutcome(payloadJson: String): InferenceOutcome {
    val json = telemetryJson.parseToJsonElement(payloadJson).jsonObject
    return InferenceOutcome(
        eventId = json.string("eventId"),
        timestampMs = json.long("timestampMs"),
        provider = json.string("provider"),
        modelId = json.string("modelId"),
        latencyMs = json.long("latencyMs"),
        tokenCount = json.intOrNull("tokenCount"),
        costUsd = json.stringOrNull("costUsd")?.let { BigDecimal(it) },
        success = json.boolean("success"),
        errorMessage = json.stringOrNull("errorMessage"),
        severity = TelemetrySeverity.valueOf(json.string("severity")),
    )
}

private fun decodeToolOutcome(payloadJson: String): ToolOutcome {
    val json = telemetryJson.parseToJsonElement(payloadJson).jsonObject
    return ToolOutcome(
        eventId = json.string("eventId"),
        timestampMs = json.long("timestampMs"),
        toolName = json.string("toolName"),
        latencyMs = json.long("latencyMs"),
        success = json.boolean("success"),
        errorMessage = json.stringOrNull("errorMessage"),
        severity = TelemetrySeverity.valueOf(json.string("severity")),
        provider = json.stringOrNull("provider"),
        modelId = json.stringOrNull("modelId"),
    )
}

private fun decodeExceptionRecord(payloadJson: String): ExceptionRecord {
    val json = telemetryJson.parseToJsonElement(payloadJson).jsonObject
    return ExceptionRecord(
        eventId = json.string("eventId"),
        timestampMs = json.long("timestampMs"),
        tag = json.string("tag"),
        message = json.string("message"),
        throwableType = json.string("throwableType"),
        stackTrace = json.string("stackTrace"),
        severity = TelemetrySeverity.valueOf(json.string("severity")),
    )
}

private fun decodeProviderMetric(payloadJson: String): ProviderMetric {
    val json = telemetryJson.parseToJsonElement(payloadJson).jsonObject
    return ProviderMetric(
        eventId = json.string("eventId"),
        timestampMs = json.long("timestampMs"),
        provider = json.string("provider"),
        metricName = json.string("metricName"),
        metricValue = json.double("metricValue"),
        severity = TelemetrySeverity.valueOf(json.string("severity")),
    )
}

private fun JsonObject.string(key: String): String = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.stringOrNull(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

private fun JsonObject.long(key: String): Long = get(key)?.jsonPrimitive?.content?.toLong() ?: 0L

private fun JsonObject.intOrNull(key: String): Int? = get(key)?.jsonPrimitive?.content?.toIntOrNull()

private fun JsonObject.double(key: String): Double = get(key)?.jsonPrimitive?.content?.toDouble() ?: 0.0

private fun JsonObject.boolean(key: String): Boolean = get(key)?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
