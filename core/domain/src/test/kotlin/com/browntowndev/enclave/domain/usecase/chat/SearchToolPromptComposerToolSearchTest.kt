package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchToolPromptComposerToolSearchTest {
    private val composer = SearchToolPromptComposer()

    @Test
    fun `TSD-02 JSON XML prompt uses discovery block without catalog`() {
        val prompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog =
                    listOf(
                        catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                        catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                    ),
                activeTools =
                    listOf(
                        ToolDefinition.SEARCH_TOOLS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        assertAll(
            { assertTrue(prompt.contains("# TOOL DISCOVERY:")) },
            { assertTrue(prompt.contains("tool_discovery_tool")) },
            { assertFalse(prompt.contains("# TOOL CATALOG:")) },
            { assertFalse(prompt.contains("# SCHEMA DISCLOSURE:")) },
            { assertTrue(prompt.contains("<tool_call>{\"name\":\"web_search\"")) },
            { assertFalse(prompt.contains("<tool_call>{\"name\":\"append_blocks\"")) },
        )
    }

    @Test
    fun `TSD-03 SDK_NATIVE composer routes via discovery without enumerating the catalog`() {
        val prompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog = listOf(catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND)),
                activeTools = listOf(ToolDefinition.SEARCH_TOOLS, ToolDefinition.WEB_SEARCH),
                strategy = ToolCallStrategy.SDK_NATIVE,
            )

        assertAll(
            { assertTrue(prompt.contains("Anti-leak:")) },
            { assertTrue(prompt.contains("# TOOL ROUTING:")) },
            { assertTrue(prompt.contains("request tools parameter")) },
            { assertTrue(prompt.contains("tool_discovery_tool")) },
            { assertFalse(prompt.contains("# CONTEXT RETRIEVAL ROUTING:")) },
            { assertFalse(prompt.contains("registered tools")) },
            { assertFalse(prompt.contains("advertised_tools")) },
            // Discovery is real: the deferred catalog tool must not be listed up front.
            { assertFalse(prompt.contains("append_blocks")) },
            { assertFalse(prompt.contains("# TOOL GROUPS:")) },
            { assertFalse(prompt.contains("# TOOL CATALOG:")) },
            // SDK-native uses provider-native tool calls, never the envelope syntax.
            { assertFalse(prompt.contains("<tool_call>")) },
        )
    }

    @Test
    fun `MUT-01 local prompt must never include TOOL CATALOG header`() {
        val catalog =
            (1..8).map { index ->
                catalogEntry("deferred_tool_$index", ToolDisclosureMode.DISCLOSE_ON_DEMAND)
            }
        val active =
            listOf(
                ToolDefinition.SEARCH_TOOLS,
                ToolDefinition.WEB_SEARCH,
                ToolDefinition.WEB_EXTRACT,
            )

        val jsonPrompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog = catalog,
                activeTools = active,
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )
        val liteRtPrompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog = catalog,
                activeTools = active,
                strategy = ToolCallStrategy.LITE_RT_NATIVE,
            )

        assertAll(
            { assertFalse(jsonPrompt.contains("# TOOL CATALOG:")) },
            { assertFalse(jsonPrompt.contains("# SCHEMA DISCLOSURE:")) },
            { assertFalse(liteRtPrompt.contains("# TOOL CATALOG:")) },
            { assertFalse(liteRtPrompt.contains("# SCHEMA DISCLOSURE:")) },
        )
    }

    @Test
    fun `MUT-05 system prompt does not enumerate deferred tool names`() {
        val prompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog =
                    listOf(
                        catalogEntry("tool_discovery_tool", ToolDisclosureMode.PRELOADED),
                        catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                        catalogEntry("web_extract", ToolDisclosureMode.PRELOADED),
                        catalogEntry("search_chat_history", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                        catalogEntry("manage_memories", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                        catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                    ),
                activeTools =
                    listOf(
                        ToolDefinition.SEARCH_TOOLS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        assertAll(
            { assertFalse(prompt.contains("- search_chat_history")) },
            { assertFalse(prompt.contains("- append_blocks")) },
            { assertFalse(prompt.contains("deferred")) },
        )
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
