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
                        ToolDefinition.SEARCH_TOOLS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        assertTrue(prompt.contains("# TOOL DISCOVERY:"))
        assertFalse(prompt.contains("# TOOL CATALOG:"))
        assertFalse(prompt.contains("# SCHEMA DISCLOSURE:"))
        assertTrue(prompt.contains("<tool_call>{\"name\":\"web_search\""))
        assertTrue(prompt.contains("<tool_call>{\"name\":\"tool_discovery_tool\""))
        assertFalse(prompt.contains("<tool_call>{\"name\":\"append_blocks\""))
        assertFalse(prompt.contains("<tool_call>{\"name\":\"search_current_artifact\""))
        assertFalse(prompt.contains("ActionResult"))
        assertFalse(prompt.contains("ipsis"))
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
                        ToolDefinition.SEARCH_TOOLS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
                strategy = ToolCallStrategy.LITE_RT_NATIVE,
            )

        assertTrue(prompt.contains("call:web_search{query: <|\"|>...<|\"|>}"))
        assertTrue(prompt.contains("call:tool_discovery_tool{capability: <|\"|>describe the capability you need<|\"|>}"))
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
                        ToolDefinition.SEARCH_TOOLS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                    ),
            )

        assertFalse(prompt.contains("<tool_call>{\"name\":\"append_blocks\""))
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
    fun `composeVolatileTurnContext formats retrieved memories correctly`() {
        val memories =
            listOf(
                Memory(category = MemoryCategory.FACTS, content = "The sky is blue."),
            )
        val composed =
            composer.composeVolatileTurnContext(
                retrievedMemories = memories,
            )

        assertTrue(composed!!.contains("# RELEVANT FACTS & CONTEXT:"))
        assertTrue(composed.contains("The following information was retrieved from your long-term memory"))
        assertTrue(composed.contains("- The sky is blue."))
    }

    @Test
    fun `compose omits retrieved memories from stable system prompt`() {
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

        assertFalse(composed.contains("# RELEVANT FACTS & CONTEXT:"))
    }

    @Test
    fun `IGTA-10 JSON XML prompt includes search_x_recent example when intent-activated`() {
        val prompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog =
                    listOf(
                        catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                        catalogEntry("search_x_recent", ToolDisclosureMode.INTENT_ACTIVATED),
                    ),
                activeTools =
                    listOf(
                        ToolDefinition.SEARCH_TOOLS,
                        ToolDefinition.WEB_SEARCH,
                        ToolDefinition.WEB_EXTRACT,
                        ToolDefinition.X_SEARCH,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        assertTrue(prompt.contains("<tool_call>{\"name\":\"search_x_recent\""))
        assertFalse(prompt.contains("# TOOL CATALOG:"))
    }

    @Test
    fun `IGTA-11 JSON XML prompt includes start_artifact example when intent-activated`() {
        val prompt =
            composer.compose(
                baseSystemPrompt = "Be concise.",
                toolCatalog =
                    listOf(
                        catalogEntry("start_artifact", ToolDisclosureMode.INTENT_ACTIVATED),
                        catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                    ),
                activeTools =
                    listOf(
                        ToolDefinition.SEARCH_TOOLS,
                        ToolDefinition.START_ARTIFACT,
                    ),
                strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            )

        assertTrue(prompt.contains("<tool_call>{\"name\":\"start_artifact\""))
        assertFalse(prompt.contains("tool_names\":[\"start_artifact\"]"))
    }

    @Test
    fun `composeVolatileTurnContext formats retrieved X search results correctly`() {
        val composed =
            composer.composeVolatileTurnContext(
                retrievedXSearchResults = "Mock X Search Result Data",
            )

        assertTrue(composed!!.contains("# RELEVANT X SEARCH RESULTS:"))
        assertTrue(composed.contains("The following X search results were automatically retrieved based on the user's query:"))
        assertTrue(composed.contains("Mock X Search Result Data"))
    }

    // Phase 0 — PSYCH_PROFILE injection

    @Test
    fun `PP-01 compose renders PSYCHOLOGICAL PROFILE section when profile memory present`() {
        val memories =
            listOf(
                Memory(category = MemoryCategory.PSYCH_PROFILE, content = "User avoids confrontation in disagreements."),
            )
        val composed =
            composer.compose(
                baseSystemPrompt = "Base",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = memories,
            )

        assertTrue(composed.contains("# PSYCHOLOGICAL PROFILE:"))
        assertTrue(composed.contains("User avoids confrontation in disagreements."))
    }

    @Test
    fun `PP-02 compose omits PSYCHOLOGICAL PROFILE section when no profile memory present`() {
        val memories =
            listOf(
                Memory(category = MemoryCategory.CORE_IDENTITY, content = "Software engineer."),
            )
        val composed =
            composer.compose(
                baseSystemPrompt = "Base",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = memories,
            )

        assertFalse(composed.contains("# PSYCHOLOGICAL PROFILE:"))
    }

    @Test
    fun `PP-03 compose renders PSYCH_PROFILE section after ABOUT THE USER block`() {
        val memories =
            listOf(
                Memory(category = MemoryCategory.CORE_IDENTITY, content = "Developer."),
                Memory(category = MemoryCategory.PSYCH_PROFILE, content = "Tends toward perfectionism."),
            )
        val composed =
            composer.compose(
                baseSystemPrompt = "Base",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = memories,
            )

        val aboutIndex = composed.indexOf("# ABOUT THE USER:")
        val profileIndex = composed.indexOf("# PSYCHOLOGICAL PROFILE:")
        assertTrue(aboutIndex >= 0)
        assertTrue(profileIndex > aboutIndex)
    }

    @Test
    fun `PP-04 PSYCH_PROFILE content appears in stable compose output not volatile context`() {
        // The retrieval layer already excludes PSYCH_PROFILE (MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES).
        // This test verifies the stable system prompt is the correct injection path.
        val profileMemory = Memory(category = MemoryCategory.PSYCH_PROFILE, content = "Avoids vulnerability.")
        val composed =
            composer.compose(
                baseSystemPrompt = "Base",
                toolCatalog = emptyList(),
                activeTools = emptyList(),
                coreMemories = listOf(profileMemory),
            )
        assertTrue(composed.contains("Avoids vulnerability."))

        // composeVolatileTurnContext receives only retrieved memories (FACTS/PROJECT_CONTEXT)
        // — PSYCH_PROFILE never reaches it. Volatile context with no retrieved memories is null.
        val volatile = composer.composeVolatileTurnContext(retrievedMemories = emptyList())
        assertTrue(volatile == null || !volatile.contains("Avoids vulnerability."))
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
