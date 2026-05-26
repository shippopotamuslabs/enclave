package com.browntowndev.enclave.feature.inference.appendonly

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
    fun compactToolContextIndexJson_omits_long_full_schema_ref() {
        val longRef = "x".repeat(100)
        val raw = """{"full_schema_ref":"$longRef"}"""
        val compacted = ContextSidecarCompaction.compactToolContextIndexJson(raw)
        assertTrue(compacted.contains("<omitted:100 chars>"))
        assertFalse(compacted.contains(longRef))
    }
}
