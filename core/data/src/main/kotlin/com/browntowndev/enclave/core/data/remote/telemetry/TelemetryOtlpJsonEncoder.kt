package com.browntowndev.enclave.core.data.remote.telemetry

import com.browntowndev.enclave.core.data.local.TelemetryEventEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Builds OTLP/HTTP JSON payloads per ExportLogsServiceRequest (OpenTelemetry proto JSON mapping).
 */
@Suppress("MagicNumber")
internal object TelemetryOtlpJsonEncoder {
    private const val SERVICE_NAME = "enclave"
    private const val INSTRUMENTATION_SCOPE = "com.browntowndev.enclave.telemetry"
    private const val NANOS_PER_MILLIS = 1_000_000L

    @Suppress("SwallowedException")
    private val deviceManufacturer: String
        get() =
            try {
                android.os.Build.MANUFACTURER
            } catch (e: Throwable) {
                "Unknown"
            }

    @Suppress("SwallowedException")
    private val deviceModel: String
        get() =
            try {
                android.os.Build.MODEL
            } catch (e: Throwable) {
                "Unknown"
            }

    fun buildExportLogsRequest(events: List<TelemetryEventEntity>): String =
        buildJsonObject {
            put(
                "resourceLogs",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("resource", resourceAttributes())
                            put(
                                "scopeLogs",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("scope", instrumentationScope())
                                            put(
                                                "logRecords",
                                                buildJsonArray {
                                                    events.forEach { event ->
                                                        add(logRecord(event))
                                                    }
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

    private fun resourceAttributes(): JsonObject =
        buildJsonObject {
            put(
                "attributes",
                buildJsonArray {
                    add(stringAttribute("service.name", SERVICE_NAME))
                    add(stringAttribute("device.manufacturer", deviceManufacturer))
                    add(stringAttribute("device.model.name", deviceModel))
                },
            )
        }

    private fun instrumentationScope(): JsonObject =
        buildJsonObject {
            put("name", JsonPrimitive(INSTRUMENTATION_SCOPE))
        }

    private fun logRecord(event: TelemetryEventEntity): JsonObject {
        val severityNumber = severityNumber(event.severity)
        return buildJsonObject {
            put("timeUnixNano", JsonPrimitive(event.timestampMs * NANOS_PER_MILLIS))
            put("severityNumber", JsonPrimitive(severityNumber))
            put("severityText", JsonPrimitive(event.severity))
            put(
                "body",
                buildJsonObject {
                    put("stringValue", JsonPrimitive(event.eventType))
                },
            )
            put(
                "attributes",
                buildJsonArray {
                    add(stringAttribute("enclave.event.id", event.id))
                    add(stringAttribute("enclave.event.type", event.eventType))
                    add(stringAttribute("enclave.event.payload", event.payloadJson))
                },
            )
        }
    }

    private fun stringAttribute(
        key: String,
        value: String,
    ): JsonObject =
        buildJsonObject {
            put("key", JsonPrimitive(key))
            put(
                "value",
                buildJsonObject {
                    put("stringValue", JsonPrimitive(value))
                },
            )
        }

    private fun severityNumber(severity: String): Int =
        when (severity.uppercase()) {
            "DEBUG" -> 5
            "INFO" -> 9
            "WARNING" -> 13
            "ERROR" -> 17
            else -> 9
        }
}
