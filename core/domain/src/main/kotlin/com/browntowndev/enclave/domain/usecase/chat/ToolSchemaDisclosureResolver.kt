package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure-domain resolver for the reserved `disclose_tool_schemas` tool.
 *
 * The resolver classifies caller-supplied tool names into three buckets:
 * - activated: deferred tools whose schemas should now become callable
 * - already-active: names already in the active set
 * - unknown: names that do not appear in the catalog
 *
 * Activation is reported via [ToolSchemaDisclosureResult.activatedToolNames]; the host
 * (Rust agent session or LiteRT cache) owns the authoritative active-tool state.
 */
class ToolSchemaDisclosureResolver {
    @Suppress("MaxLineLength")
    fun resolve(
        toolNames: List<String>,
        toolCatalog: List<ToolCatalogEntry>,
        activeTools: List<ToolDefinition>,
        strategy: ToolCallStrategy,
    ): ToolSchemaDisclosureResult {
        val activeNames = activeTools.map { it.name }.toSet()
        val catalogByName = toolCatalog.associateBy { it.name }

        val activated = mutableListOf<String>()
        val alreadyActive = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        toolNames.forEach { name ->
            if (!seen.add(name)) return@forEach
            when {
                name in activeNames -> alreadyActive += name
                catalogByName.containsKey(name) -> activated += name
                else -> unknown += name
            }
        }

        val resultJson =
            if (activated.isEmpty()) {
                buildJsonObject {
                    put("status", "error")
                    put("message", ERROR_MESSAGE)
                    put("activated_tools", emptyJsonStringArray())
                    put("already_active_tools", alreadyActive.toJsonStringArray())
                    put("unknown_tools", unknown.toJsonStringArray())
                }.toString()
            } else {
                val toolsArray =
                    buildJsonArray {
                        activated.forEach { name ->
                            val entry = catalogByName.getValue(name)
                            val def = ToolDefinition.fromName(name)
                            add(
                                buildJsonObject {
                                    put("name", entry.name)
                                    put("description", entry.description)
                                    put(
                                        "parameters",
                                        def?.schema?.toJsonObject() ?: emptyJsonObject(),
                                    )
                                    if (def != null) {
                                        put("call_example", def.toExample(strategy))
                                    }
                                },
                            )
                        }
                    }
                buildJsonObject {
                    put("status", "ok")
                    put("activated_tools", activated.toJsonStringArray())
                    put("already_active_tools", alreadyActive.toJsonStringArray())
                    put("unknown_tools", unknown.toJsonStringArray())
                    put("tools", toolsArray)
                }.toString()
            }

        return ToolSchemaDisclosureResult(
            resultJson = resultJson,
            activatedToolNames = activated,
        )
    }

    private fun List<String>.toJsonStringArray(): JsonArray =
        buildJsonArray { forEach { add(JsonPrimitive(it)) } }

    private fun emptyJsonStringArray(): JsonArray = buildJsonArray {}

    private fun emptyJsonObject(): JsonElement = JsonObject(emptyMap())

    companion object {
        const val ERROR_MESSAGE: String = "No disclosable tools resolved from request."
    }
}

data class ToolSchemaDisclosureResult(
    val resultJson: String,
    val activatedToolNames: List<String>,
)
