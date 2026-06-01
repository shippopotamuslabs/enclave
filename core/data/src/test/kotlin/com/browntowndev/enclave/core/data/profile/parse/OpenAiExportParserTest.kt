package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.MessageRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiExportParserTest {
    private val parser = OpenAiExportParser()

    // Minimal but realistic sample: two-turn conversation
    private val sampleJson = """
        [
          {
            "id": "conv-001",
            "title": "Why is the sky blue?",
            "create_time": 1700000000.0,
            "mapping": {
              "root": {
                "id": "root",
                "message": null,
                "parent": null,
                "children": ["msg-user-1"]
              },
              "msg-user-1": {
                "id": "msg-user-1",
                "message": {
                  "id": "msg-user-1",
                  "author": {"role": "user"},
                  "create_time": 1700000001.0,
                  "content": {
                    "content_type": "text",
                    "parts": ["Why is the sky blue?"]
                  }
                },
                "parent": "root",
                "children": ["msg-asst-1"]
              },
              "msg-asst-1": {
                "id": "msg-asst-1",
                "message": {
                  "id": "msg-asst-1",
                  "author": {"role": "assistant"},
                  "create_time": 1700000002.0,
                  "content": {
                    "content_type": "text",
                    "parts": ["Rayleigh scattering causes blue light to scatter more."]
                  }
                },
                "parent": "msg-user-1",
                "children": []
              }
            },
            "current_node": "msg-asst-1"
          }
        ]
    """.trimIndent()

    @Test
    fun `PP-20 canHandle returns true for OpenAI conversations json`() {
        val preview = sampleJson.take(512)
        assertTrue(parser.canHandle("conversations.json", preview))
    }

    @Test
    fun `PP-21 canHandle returns false for non-mapping JSON`() {
        val preview = """[{"uuid":"x","chat_messages":[]}]"""
        assertFalse(parser.canHandle("conversations.json", preview))
    }

    @Test
    fun `PP-22 parse extracts conversation id and title`() {
        val (records, _) = parser.parse("conversations.json", sampleJson)
        assertEquals(1, records.size)
        assertEquals("conv-001", records[0].externalId)
        assertEquals("Why is the sky blue?", records[0].title)
        assertEquals(ConversationProvider.OPENAI, records[0].provider)
    }

    @Test
    fun `PP-23 parse reconstructs messages in correct order`() {
        val (records, _) = parser.parse("conversations.json", sampleJson)
        val messages = records[0].messages
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("Why is the sky blue?", messages[0].content)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertTrue(messages[1].content.contains("Rayleigh"))
    }

    @Test
    fun `PP-24 health report reflects extraction success`() {
        val (_, report) = parser.parse("conversations.json", sampleJson)
        assertEquals(ConversationProvider.OPENAI, report.provider)
        assertEquals(1, report.extractedConversations)
        assertTrue(report.isHealthy)
    }

    @Test
    fun `PP-25 malformed entry is skipped not thrown`() {
        val malformed = """[{"id":"ok","title":"t","mapping":{"n":{"id":"n","parent":null}},"current_node":"n"}, "not-an-object"]"""
        val (records, report) = parser.parse("conversations.json", malformed)
        // Malformed element counted in skipped, valid ones extracted
        assertTrue(report.skippedMalformed >= 1)
    }

    @Test
    fun `PP-26 empty JSON array produces healthy empty report`() {
        val (records, report) = parser.parse("conversations.json", "[]")
        assertEquals(0, records.size)
        assertEquals(0, report.totalEntries)
        assertTrue(report.isHealthy)
    }

    @Test
    fun `PP-27 parse extracts create_time as epoch millis`() {
        val (records, _) = parser.parse("conversations.json", sampleJson)
        assertEquals(1700000000L * 1000, records[0].createdAtMs)
    }
}
