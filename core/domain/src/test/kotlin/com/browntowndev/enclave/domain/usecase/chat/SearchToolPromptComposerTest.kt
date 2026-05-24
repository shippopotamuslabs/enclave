package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchToolPromptComposerTest {
    private val composer = SearchToolPromptComposer()

    @Test
    fun `LTD-02 JSON XML prompt keeps deferred examples out of the base prompt`() {
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
                        ToolDefinition.DISCLOSE_TOOL_SCHEMAS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        assertTrue(prompt.contains("# TOOL CATALOG:"))
        assertTrue(prompt.contains("# SCHEMA DISCLOSURE:"))
        assertTrue(prompt.contains("ActionResult{\"name\":\"web_search\""))
        assertTrue(prompt.contains("ActionResult{\"name\":\"disclose_tool_schemas\""))
        assertFalse(prompt.contains("ActionResult{\"name\":\"append_blocks\""))
        assertFalse(prompt.contains("ActionResult{\"name\":\"search_current_artifact\""))
    }

    @Test
    fun `LTD-03 LiteRT prompt keeps deferred native examples out of the base prompt`() {
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
                        ToolDefinition.DISCLOSE_TOOL_SCHEMAS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.LITE_RT_NATIVE,
            )

        assertTrue(prompt.contains("call:web_search{query: <|\"|>...<|\"|>}"))
        assertTrue(prompt.contains("call:disclose_tool_schemas{tool_names: <|\"|>[\"append_blocks\"]<|\"|>}"))
        assertFalse(prompt.contains("call:append_blocks{"))
        assertFalse(prompt.contains("call:search_current_artifact{"))
    }

    @Test
    fun `MUT-02 base prompt never prints deferred examples`() {
        val prompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog = listOf(catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND)),
                activeTools =
                    listOf(
                        ToolDefinition.DISCLOSE_TOOL_SCHEMAS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
            )

        assertFalse(prompt.contains("ActionResult{\"name\":\"append_blocks\""))
        assertFalse(prompt.contains("call:append_blocks{"))
    }

    @Test
    fun `compose formats core memories correctly`() {
        val memories =
            listOf(
                Memory(category = MemoryCategory.CORE_IDENTITY, content = "I am a helpful assistant."),
                Memory(category = MemoryCategory.PREFERENCES, content = "User prefers short answers."),
            )
        val composed =
            composer.compose(
                baseSystemPrompt = "Base",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = memories,
            )

        assertTrue(composed.contains("# ABOUT THE USER:"))
        assertTrue(composed.contains("## Identity & Background:"))
        assertTrue(composed.contains("- I am a helpful assistant."))
        assertTrue(composed.contains("## Preferences & Style:"))
        assertTrue(composed.contains("- User prefers short answers."))
    }

    @Test
    fun `compose formats retrieved memories correctly`() {
        val memories =
            listOf(
                Memory(category = MemoryCategory.FACTS, content = "The sky is blue."),
            )
        val composed =
            composer.compose(
                baseSystemPrompt = "Base",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                retrievedMemories = memories,
            )

        assertTrue(composed.contains("# RELEVANT FACTS & CONTEXT:"))
        assertTrue(composed.contains("The following information was retrieved from your long-term memory"))
        assertTrue(composed.contains("- The sky is blue."))
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
