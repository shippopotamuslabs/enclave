package com.browntowndev.enclave.feature.inference.appendonly

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shrinks volatile sidecar JSON before it is sent on the wire.
 *
 * Semantic fields the model needs (ledger entries, advertised tool names) are preserved;
 * pretty-printing and redundant tool schema blobs in the index are removed.
 */
internal object ContextSidecarCompaction {
    private const val FULL_SCHEMA_REF_OMIT_THRESHOLD = 64

    /**
     * State-ledger fields that are pure bookkeeping: they grow on every tool hop (or
     * compaction) yet duplicate information already present in the transcript (tool
     * calls/results) or the context store (summary/history refs). Re-emitting them
     * mutates the wire sidecar every hop, which defeats the append-only dedupe gate and
     * caps the provider prompt-cache prefix at the static system+tools header.
     */
    private val VOLATILE_LEDGER_FIELDS = setOf("tool_decisions", "summary_refs", "history_refs")

    private val json = Json { ignoreUnknownKeys = true }

    fun compactStateLedgerJson(raw: String): String =
        runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val durable =
                buildMap<String, JsonElement> {
                    root.forEach { (key, value) ->
                        if (key !in VOLATILE_LEDGER_FIELDS) {
                            put(key, value)
                        }
                    }
                }
            JsonObject(durable).toString()
        }.getOrElse { compactJsonObject(raw) }

    fun compactToolContextIndexJson(raw: String): String =
        runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val compacted =
                buildMap<String, JsonElement> {
                    root.forEach { (key, value) ->
                        when (key) {
                            "advertised_tools" -> put(key, compactAdvertisedTools(value))
                            "full_schema_ref" -> put(key, compactFullSchemaRef(value))
                            else -> put(key, value)
                        }
                    }
                }
            JsonObject(compacted).toString()
        }.getOrElse { compactJsonObject(raw) }

    fun compactFileContextIndexJson(raw: String): String = compactJsonObject(raw)

    /** Native cloud providers: omit tool index sidecar when only deferral metadata remains. */
    fun shouldOmitToolContextIndexFromWire(compacted: String): Boolean =
        runCatching {
            val root = json.parseToJsonElement(compacted).jsonObject
            val advertised = root["advertised_tools"]?.jsonArray ?: return false
            advertised.isEmpty()
        }.getOrElse { false }

    private fun compactAdvertisedTools(value: JsonElement): JsonElement {
        val advertised = value.jsonArray
        val names =
            advertised.mapNotNull { element ->
                when {
                    element is JsonPrimitive -> element.contentOrNull
                    else -> element.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                }
            }
        if (names.isEmpty()) return value
        return JsonArray(names.map(::JsonPrimitive))
    }

    private fun compactFullSchemaRef(value: JsonElement): JsonElement {
        val ref = value.jsonPrimitive.contentOrNull ?: return value
        if (ref.length <= FULL_SCHEMA_REF_OMIT_THRESHOLD) return value
        return JsonPrimitive("<omitted:${ref.length} chars>")
    }

    private fun compactJsonObject(raw: String): String =
        runCatching {
            val element = json.parseToJsonElement(raw)
            if (element is JsonObject) element.toString() else raw.trim()
        }.getOrElse { raw.trim() }
}
