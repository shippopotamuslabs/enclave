package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SidecarAnnotationRuleTest {
    private val composer = SearchToolPromptComposer()

    private fun catalogEntry(name: String, mode: ToolDisclosureMode): ToolCatalogEntry =
        ToolCatalogEntry(
            name = name,
            description = "$name description",
            disclosureMode = mode,
        )

    private val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    // Matches a live formatted timestamp (e.g. "Sent at Thursday, June 18, 2026"),
    // not the format description "Sent at ..." in the rule text.
    private val liveTimestampRegex = Regex("(Sent at|Current Time:) [A-Z][a-z]+, [A-Z][a-z]+ \\d+, \\d{4}")

    @Test
    @DisplayName("S-RULE-01 full metadataRules contains UUID-aware Sidecar rule")
    fun S_RULE_01_full_rule_contains_sidecar_annotation() {
        val prompt = composer.compose(
            baseSystemPrompt = "Be concise.",
            toolCatalog = listOf(catalogEntry("web_search", ToolDisclosureMode.PRELOADED)),
            activeTools = listOf(ToolDefinition.WEB_SEARCH),
            strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            compactStaticPrompt = false,
        )
        assertTrue(prompt.contains("[Sidecar-<uuid>:"), "full rule must reference [Sidecar-<uuid>:] form")
        assertTrue(prompt.contains("host-injected"), "full rule must mention host-injected context")
        assertTrue(
            prompt.contains("Do not quote") || prompt.contains("do not quote") ||
                prompt.contains("never") || prompt.contains("Never"),
            "full rule must instruct model not to quote/acknowledge blocks",
        )
    }

    @Test
    @DisplayName("S-RULE-02 compact metadataRules contains one-line sidecar rule")
    fun S_RULE_02_compact_rule_contains_sidecar_annotation() {
        val prompt = composer.compose(
            baseSystemPrompt = "Be concise.",
            toolCatalog = listOf(catalogEntry("web_search", ToolDisclosureMode.PRELOADED)),
            activeTools = listOf(ToolDefinition.WEB_SEARCH),
            strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            compactStaticPrompt = true,
        )
        assertTrue(prompt.contains("[Sidecar-"), "compact rule must reference [Sidecar- form")
        assertTrue(
            prompt.contains("not mention") || prompt.contains("never mention") || prompt.contains("Do not mention"),
            "compact rule must instruct model not to mention sidecar blocks",
        )
    }

    @Test
    @DisplayName("S-RULE-03 rule text contains no UUIDs or timestamps and is byte-stable")
    fun S_RULE_03_rule_text_byte_stable() {
        val prompt1 = composer.compose(
            baseSystemPrompt = "Be concise.",
            toolCatalog = listOf(catalogEntry("web_search", ToolDisclosureMode.PRELOADED)),
            activeTools = listOf(ToolDefinition.WEB_SEARCH),
            strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            compactStaticPrompt = false,
        )
        val prompt2 = composer.compose(
            baseSystemPrompt = "Be concise.",
            toolCatalog = listOf(catalogEntry("web_search", ToolDisclosureMode.PRELOADED)),
            activeTools = listOf(ToolDefinition.WEB_SEARCH),
            strategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            compactStaticPrompt = false,
        )
        assertEquals(prompt1, prompt2, "same inputs must produce byte-identical system prompt")
        assertFalse(uuidRegex.containsMatchIn(prompt1), "rule text must not contain a live UUID")
        assertFalse(liveTimestampRegex.containsMatchIn(prompt1), "rule text must not contain a live formatted timestamp")
    }
}