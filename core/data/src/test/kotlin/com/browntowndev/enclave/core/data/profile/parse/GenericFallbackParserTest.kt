package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.MessageRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenericFallbackParserTest {
    private val parser = GenericFallbackParser()

    private val sampleJson = """
        [
          {
            "id": "conv-x",
            "title": "Hypothetical conversation",
            "created_at": "2024-03-01T00:00:00Z",
            "messages": [
              {"role": "user", "content": "Hello there!"},
              {"role": "assistant", "content": "Hi! How can I help?"}
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `PP-40 canHandle returns true for generic JSON array with json extension`() {
        assertTrue(parser.canHandle("export.json", "["))
    }

    @Test
    fun `PP-41 canHandle returns false for txt file`() {
        assertFalse(parser.canHandle("readme.txt", "[{}]"))
    }

    @Test
    fun `PP-42 parse extracts conversations using known field names`() {
        val (records, _) = parser.parse("export.json", sampleJson)
        assertEquals(1, records.size)
        assertEquals("conv-x", records[0].externalId)
        assertEquals("Hypothetical conversation", records[0].title)
        assertEquals(ConversationProvider.UNKNOWN, records[0].provider)
    }

    @Test
    fun `PP-43 parse maps role field to correct MessageRole`() {
        val (records, _) = parser.parse("export.json", sampleJson)
        assertEquals(MessageRole.USER, records[0].messages[0].role)
        assertEquals(MessageRole.ASSISTANT, records[0].messages[1].role)
    }

    @Test
    fun `PP-44 parse works with sender field instead of role`() {
        val withSender = sampleJson.replace("\"role\":", "\"sender\":")
        val (records, _) = parser.parse("export.json", withSender)
        assertEquals(2, records[0].messages.size)
    }

    @Test
    fun `PP-45 parse degrades gracefully on partially-structured data`() {
        val partial = """[{"messages":[{"role":"user","content":"hi"}]},{"not":"a-conversation"}]"""
        val (records, report) = parser.parse("export.json", partial)
        assertTrue(records.isNotEmpty())
        // Second element has no messages array so it is skipped
        assertTrue(report.skippedMalformed >= 1)
    }
}
