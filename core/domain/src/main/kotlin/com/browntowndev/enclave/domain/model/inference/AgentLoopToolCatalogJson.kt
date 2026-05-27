package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Serializes host tool catalogs into Rust `ToolSpec` JSON for `merge_catalog_json`.
 */
object AgentLoopToolCatalogJson {
    const val EMPTY_ARRAY: String = "[]"

    /** Full bundled catalog file (`{"tools":[...]}`), for contract checks and default merges. */
    val fullCatalogFileJson: String
        get() = AgentLoopToolCatalogResources.bundledCatalogJson

    private val bundledSpecsByName: Map<String, JsonObject> by lazy {
        val root =
            kotlinx.serialization.json.Json
                .parseToJsonElement(AgentLoopToolCatalogResources.bundledCatalogJson)
                .jsonObject
        root.getValue("tools")
            .jsonArray
            .associate { element ->
                val spec = element.jsonObject
                spec.getValue("name").jsonPrimitive.content to spec
            }
    }

    /**
     * JSON array of `ToolSpec` objects for tools present in [entries] (plus disclosure helper when needed).
     */
    fun jsonForCatalog(entries: List<ToolCatalogEntry>): String {
        if (entries.isEmpty()) {
            return EMPTY_ARRAY
        }
        val names = LinkedHashSet<String>()
        entries.forEach { names.add(it.name) }
        if (names.any { it != ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name }) {
            names.add(ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name)
        }
        val specs =
            buildJsonArray {
                names.forEach { name ->
                    val bundled = bundledSpecsByName[name]
                    if (bundled != null) {
                        add(bundled)
                    } else {
                        entries.find { it.name == name }?.let { add(it.toFallbackToolSpec()) }
                    }
                }
            }
        return specs.toString()
    }

    fun jsonForToolNames(toolNames: Collection<String>): String =
        jsonForCatalog(toolNames.map { ToolCatalogEntry(name = it, description = "", disclosureMode = ToolDisclosureMode.PRELOADED) })

    private fun ToolCatalogEntry.toFallbackToolSpec(): JsonObject {
        val requiredFields =
            requiredArguments.map { hint ->
                buildJsonObject {
                    put("name", hint.name)
                    put("ty", hint.type)
                    put("description", hint.name)
                }
            }
        val schemaHint =
            if (requiredFields.isEmpty() && optionalArguments.isEmpty()) {
                null
            } else {
                buildJsonObject {
                    putJsonArray("required_fields") {
                        requiredFields.forEach { add(it) }
                    }
                    putJsonObject("defaults") {}
                }
            }
        return buildJsonObject {
            put("name", name)
            putJsonArray("aliases") {}
            put("reproducible", name !in NON_REPRODUCIBLE_TOOLS)
            if (schemaHint != null) {
                put("schema_hint", schemaHint)
            } else {
                put("schema_hint", JsonNull)
            }
        }
    }

    private val NON_REPRODUCIBLE_TOOLS =
        setOf(
            "start_artifact",
            "append_blocks",
            "manage_memories",
            "generate_image",
            "generate_video",
            "generate_music",
        )
}
