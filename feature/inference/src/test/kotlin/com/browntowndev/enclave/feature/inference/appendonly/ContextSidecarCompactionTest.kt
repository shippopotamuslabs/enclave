package com.browntowndev.enclave.feature.inference.appendonly

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextSidecarCompactionTest {
    @Test
    fun compactStateLedgerJson_minifies_whitespace() {
        val raw =
            """
            {
              "key": "value"
            }
            """.trimIndent()
        assertEquals("""{"key":"value"}""", ContextSidecarCompaction.compactStateLedgerJson(raw))
    }

    @Test
    fun compactToolContextIndexJson_reduces_advertised_tools_to_names() {
        val raw =
            """{"advertised_tools":[{"name":"web_search"},{"name":"web_extract"}]}"""
        val compacted = ContextSidecarCompaction.compactToolContextIndexJson(raw)
        assertEquals("""{"advertised_tools":["web_search","web_extract"]}""", compacted)
    }

    @Test
    fun compactStateLedgerJson_strips_transcript_redundant_bookkeeping() {
        val raw =
            """
            {
              "tool_decisions": [{"call_id": "c1", "name": "web_search"}],
              "summary_refs": ["summary-turn-2"],
              "history_refs": ["history-turn-1"],
              "session_revision": 42
            }
            """.trimIndent()
        val compacted = ContextSidecarCompaction.compactStateLedgerJson(raw)
        val root = Json.parseToJsonElement(compacted).jsonObject
        assertFalse("tool_decisions" in root)
        assertFalse("summary_refs" in root)
        assertFalse("history_refs" in root)
        assertEquals(42, root["session_revision"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun compactStateLedgerJson_is_byte_stable_across_tool_only_hops() {
        val beforeHop =
            """
            {
              "tool_decisions": [{"call_id": "c1", "name": "web_search"}],
              "summary_refs": [],
              "history_refs": [],
              "session_revision": 7
            }
            """.trimIndent()
        val afterHop =
            """
            {
              "tool_decisions": [
                {"call_id": "c1", "name": "web_search"},
                {"call_id": "c2", "name": "append_blocks"}
              ],
              "summary_refs": ["summary-turn-3"],
              "history_refs": ["history-turn-2"],
              "session_revision": 7
            }
            """.trimIndent()
        val compactBefore = ContextSidecarCompaction.compactStateLedgerJson(beforeHop)
        val compactAfter = ContextSidecarCompaction.compactStateLedgerJson(afterHop)
        assertEquals(compactBefore, compactAfter)
        assertEquals("""{"session_revision":7}""", compactBefore)
    }

    @Test
    fun compactToolContextIndexJson_omits_long_full_schema_ref() {
        val longRef = "x".repeat(100)
        val raw = """{"full_schema_ref":"$longRef"}"""
        val compacted = ContextSidecarCompaction.compactToolContextIndexJson(raw)
        assertTrue(compacted.contains("<omitted:100 chars>"))
        assertFalse(compacted.contains(longRef))
    }

    @Test
    fun compactFileContextIndexJson_preserves_ref_delta_changed_and_removed() {
        val raw =
            """{"ref":"file-index-full","delta":{"changed":[{"path":"src/a.kt","condensation_level":"full"}],"removed":["old/path.kt"]}}"""
        val compacted = ContextSidecarCompaction.compactFileContextIndexJson(raw)
        assertEquals(
            """{"ref":"file-index-full","delta":{"changed":[{"path":"src/a.kt","condensation_level":"full"}],"removed":["old/path.kt"]}}""",
            compacted,
        )
    }
}
