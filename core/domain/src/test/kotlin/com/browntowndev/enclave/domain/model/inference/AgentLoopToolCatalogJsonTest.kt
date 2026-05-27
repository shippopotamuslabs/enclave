package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentLoopToolCatalogJsonTest {
    @Test
    fun jsonForCatalog_emitsToolSpecShape() {
        val json =
            AgentLoopToolCatalogJson.jsonForCatalog(
                listOf(
                    ToolCatalogEntry(
                        name = "web_search",
                        description = "Search the web",
                        disclosureMode = ToolDisclosureMode.PRELOADED,
                    ),
                ),
            )
        val array = Json.parseToJsonElement(json).jsonArray
        assertTrue(array.isNotEmpty())
        val webSearch =
            array.first { element ->
                element.jsonObject.getValue("name").jsonPrimitive.content == "web_search"
            }
        val spec = webSearch.jsonObject
        assertTrue(spec.containsKey("aliases"))
        assertTrue(spec.containsKey("reproducible"))
        assertEquals("true", spec.getValue("reproducible").jsonPrimitive.content)
        assertTrue(spec.containsKey("schema_hint"))
    }

    @Test
    fun jsonForCatalog_rejectsLegacyKotlinCatalogShape() {
        val legacyShape =
            """[{"name":"web_search","description":"x","disclosureMode":"PRELOADED"}]"""
        val merged =
            AgentLoopToolCatalogJson.jsonForCatalog(
                listOf(
                    ToolCatalogEntry(
                        name = "web_search",
                        description = "x",
                        disclosureMode = ToolDisclosureMode.PRELOADED,
                    ),
                ),
            )
        assertFalse(merged.contains("disclosureMode"))
        assertFalse(legacyShape.contains("\"aliases\""))
    }

    @Test
    fun jsonForCatalog_includesDisclosureHelperWhenToolsPresent() {
        val json =
            AgentLoopToolCatalogJson.jsonForCatalog(
                listOf(
                    ToolCatalogEntry(
                        name = "append_blocks",
                        description = "Append",
                        disclosureMode = ToolDisclosureMode.DISCLOSE_ON_DEMAND,
                    ),
                ),
            )
        val names =
            Json.parseToJsonElement(json).jsonArray.map { element ->
                element.jsonObject.getValue("name").jsonPrimitive.content
            }
        assertTrue(ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name in names)
        assertTrue("append_blocks" in names)
    }

    @Test
    fun fullCatalogFileJson_matchesBundledResource() {
        assertTrue(AgentLoopToolCatalogJson.fullCatalogFileJson.contains("\"tools\""))
    }
}
