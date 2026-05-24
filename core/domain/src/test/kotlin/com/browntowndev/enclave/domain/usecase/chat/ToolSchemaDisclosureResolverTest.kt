package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolSchemaDisclosureResolverTest {
    private val resolver = ToolSchemaDisclosureResolver()

    @Test
    fun `LTD-06 disclosure reports already active and unknown names exactly`() {
        val result =
            resolver.resolve(
                toolNames = listOf("web_search", "not_a_tool"),
                toolCatalog = listOf(catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND)),
                activeTools =
                    listOf(
                        ToolDefinition.DISCLOSE_TOOL_SCHEMAS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject

        assertAll(
            { assertEquals("error", json["status"]?.jsonPrimitive?.content) },
            { assertEquals("No disclosable tools resolved from request.", json["message"]?.jsonPrimitive?.content) },
            { assertEquals(emptyList<String>(), json["activated_tools"]?.jsonArray?.map { it.jsonPrimitive.content }) },
            { assertEquals(listOf("web_search"), json["already_active_tools"]?.jsonArray?.map { it.jsonPrimitive.content }) },
            { assertEquals(listOf("not_a_tool"), json["unknown_tools"]?.jsonArray?.map { it.jsonPrimitive.content }) },
        )
    }

    @Test
    fun `MUT-04 duplicate disclosure requests activate once`() {
        val result =
            resolver.resolve(
                toolNames = listOf("append_blocks", "append_blocks", "search_current_artifact"),
                toolCatalog =
                    listOf(
                        catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                        catalogEntry("search_current_artifact", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                    ),
                activeTools = listOf(ToolDefinition.DISCLOSE_TOOL_SCHEMAS, ToolDefinition.WEB_SEARCH),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        val nextActiveToolNames =
            listOf(ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name, ToolDefinition.WEB_SEARCH.name) + result.activatedToolNames

        assertAll(
            { assertEquals(listOf("append_blocks", "search_current_artifact"), result.activatedToolNames) },
            {
                assertEquals(
                    listOf("disclose_tool_schemas", "web_search", "append_blocks", "search_current_artifact"),
                    nextActiveToolNames,
                )
            },
        )
    }

    @Test
    fun `MUT-05 invalid disclosure request leaves active state unchanged`() {
        val result =
            resolver.resolve(
                toolNames = listOf("not_a_tool"),
                toolCatalog = listOf(catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND)),
                activeTools =
                    listOf(
                        ToolDefinition.DISCLOSE_TOOL_SCHEMAS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val nextActiveToolNames =
            listOf(
                ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name,
                ToolDefinition.WEB_SEARCH.name,
                ToolDefinition.WEB_EXTRACT.name,
            ) + result.activatedToolNames

        assertAll(
            { assertEquals("error", json["status"]?.jsonPrimitive?.content) },
            { assertEquals("No disclosable tools resolved from request.", json["message"]?.jsonPrimitive?.content) },
            { assertEquals(emptyList<String>(), result.activatedToolNames) },
            { assertEquals(listOf("disclose_tool_schemas", "web_search", "web_extract"), nextActiveToolNames) },
        )
    }

    @Test
    fun `disclosure call_example uses supplied strategy`() {
        val result =
            resolver.resolve(
                toolNames = listOf("append_blocks"),
                toolCatalog = listOf(catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND)),
                activeTools =
                    listOf(
                        ToolDefinition.DISCLOSE_TOOL_SCHEMAS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.LITE_RT_NATIVE,
            )

        assertTrue(result.resultJson.contains("call:append_blocks{"))
        assertFalse(result.resultJson.contains("ActionResult{\"name\":\"append_blocks\""))
    }

    private fun catalogEntry(
        name: String,
        disclosureMode: ToolDisclosureMode,
    ): ToolCatalogEntry =
        ToolCatalogEntry(
            name = name,
            description = "$name description",
            disclosureMode = disclosureMode,
        )
}
