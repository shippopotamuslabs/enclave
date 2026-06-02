package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.MessageRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnthropicExportParserTest {
    private val parser = AnthropicExportParser()

    private val sampleJson =
        """
        [
          {
            "uuid": "anth-conv-001",
            "name": "Test conversation",
            "created_at": "2024-01-15T10:30:00.000000+00:00",
            "updated_at": "2024-01-15T10:31:00.000000+00:00",
            "chat_messages": [
              {
                "uuid": "msg-001",
                "sender": "human",
                "text": "What is Rayleigh scattering?",
                "created_at": "2024-01-15T10:30:00.000000+00:00"
              },
              {
                "uuid": "msg-002",
                "sender": "assistant",
                "text": "Rayleigh scattering is the scattering of light by particles much smaller than the wavelength.",
                "created_at": "2024-01-15T10:30:05.000000+00:00"
              }
            ]
          }
        ]
        """.trimIndent()

    @Test
    fun `PP-30 canHandle returns true for Anthropic chat_messages JSON`() {
        assertTrue(parser.canHandle("conversations.json", sampleJson.take(512)))
    }

    @Test
    fun `PP-31 canHandle returns false for OpenAI mapping JSON`() {
        val preview = """[{"id":"x","mapping":{},"current_node":"n"}]"""
        assertFalse(parser.canHandle("conversations.json", preview))
    }

    @Test
    fun `PP-32 parse extracts conversation uuid and name`() {
        val (records, _) = parser.parse("conversations.json", sampleJson)
        assertEquals(1, records.size)
        assertEquals("anth-conv-001", records[0].externalId)
        assertEquals("Test conversation", records[0].title)
        assertEquals(ConversationProvider.ANTHROPIC, records[0].provider)
    }

    @Test
    fun `PP-33 parse maps human sender to USER role`() {
        val (records, _) = parser.parse("conversations.json", sampleJson)
        assertEquals(MessageRole.USER, records[0].messages[0].role)
        assertEquals("What is Rayleigh scattering?", records[0].messages[0].content)
    }

    @Test
    fun `PP-34 parse maps assistant sender to ASSISTANT role`() {
        val (records, _) = parser.parse("conversations.json", sampleJson)
        assertEquals(MessageRole.ASSISTANT, records[0].messages[1].role)
    }

    @Test
    fun `PP-35 parse extracts ISO-8601 created_at as epoch millis`() {
        val (records, _) = parser.parse("conversations.json", sampleJson)
        assertEquals(1705314600000L, records[0].createdAtMs)
    }

    @Test
    fun `PP-36 parse handles renamed field title instead of name`() {
        val renamed = sampleJson.replace("\"name\":", "\"title\":")
        val (records, _) = parser.parse("conversations.json", renamed)
        assertEquals("Test conversation", records[0].title)
    }

    @Test
    fun `PP-37 parse health report is healthy for well-formed export`() {
        val (_, report) = parser.parse("conversations.json", sampleJson)
        assertTrue(report.isHealthy)
        assertEquals(1, report.extractedConversations)
        assertEquals(2, report.messagesWithText)
    }

    @Test
    fun `PP-38 malformed entry is skipped and counted`() {
        val withJunk =
            """[$sampleJson[0], "not-an-object"]""".let {
                """[${sampleJson.removePrefix("[").removeSuffix("]")}, "not-an-object"]"""
            }
        val (records, report) = parser.parse("conversations.json", withJunk)
        assertTrue(report.skippedMalformed >= 1)
        assertTrue(records.isNotEmpty())
    }
}
