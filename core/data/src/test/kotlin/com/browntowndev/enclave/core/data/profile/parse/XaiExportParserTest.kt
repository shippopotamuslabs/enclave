package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.MessageRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader

class XaiExportParserTest {
    private val parser = XaiExportParser()

    // Real xAI export format: {"conversations":[{"conversation":{...},"responses":[{"response":{...}},...]},...]}
    // Response timestamps use MongoDB extended JSON: {"$date":{"$numberLong":"<epoch-ms>"}}

    private fun mongoTs(ms: Long): String = "{\"\$date\":{\"\$numberLong\":\"$ms\"}}"

    private fun buildExport(vararg conversations: String): String = "{\"conversations\":[${conversations.joinToString(",")}]}"

    private fun buildConversation(
        id: String = "conv-001",
        title: String = "Test Conversation",
        createTime: String = "2024-11-15T10:30:00.000000Z",
        responses: String,
    ): String =
        "{\"conversation\":{\"id\":\"$id\",\"title\":\"$title\"," +
            "\"create_time\":\"$createTime\",\"anon_user_id\":null}," +
            "\"responses\":[$responses]}"

    private fun buildResponse(
        id: String = "resp-001",
        sender: String,
        message: String,
        ms: Long = 1731666600000L,
    ): String =
        "{\"response\":{\"_id\":\"$id\",\"sender\":\"$sender\"," +
            "\"message\":\"${message.replace("\"", "\\\"")}\",\"create_time\":${mongoTs(ms)}},\"share_link\":null}"

    private val singleConversation =
        buildExport(
            buildConversation(
                id = "conv-xai-001",
                title = "What is Grok?",
                responses =
                    buildResponse(id = "r1", sender = "human", message = "What is Grok?", ms = 1731666601000L) +
                        "," +
                        buildResponse(
                            id = "r2",
                            sender = "ASSISTANT",
                            message = "Grok is an AI assistant by xAI.",
                            ms = 1731666602000L,
                        ),
            ),
        )

    // --- canHandle tests ---

    @Test
    fun `PP-200 detects xAI format by object wrapper with conversations and anon_user_id`() {
        val preview = "{\"conversations\":[{\"conversation\":{\"id\":\"c1\",\"anon_user_id\":null,\"title\":\"T\"},\"responses\":[]}]}"
        assertTrue(parser.canHandle("prod-grok-backend.json", preview))
    }

    @Test
    fun `PP-201 detects xAI format regardless of json filename as long as it ends in dot json`() {
        val preview = "{\"conversations\":[{\"conversation\":{\"id\":\"c1\",\"anon_user_id\":null},\"responses\":[]}]}"
        assertTrue(parser.canHandle("prod-grok-backend.json", preview))
        assertTrue(parser.canHandle("grok-export.json", preview))
        assertFalse(parser.canHandle("prod-grok-backend.txt", preview))
    }

    @Test
    fun `PP-202 rejects array-format JSON — real xAI export is an object not a top-level array`() {
        val preview = "[{\"id\":\"c1\",\"anon_user_id\":null,\"conversations\":[]}]"
        assertFalse(parser.canHandle("conversations.json", preview))
    }

    @Test
    fun `PP-203 rejects json missing anon_user_id fingerprint`() {
        val preview = "{\"conversations\":[{\"conversation\":{\"id\":\"c1\",\"title\":\"T\"},\"responses\":[]}]}"
        assertFalse(parser.canHandle("export.json", preview))
    }

    @Test
    fun `PP-204 rejects non-json file`() {
        assertFalse(parser.canHandle("readme.txt", "This is a readme file."))
        assertFalse(parser.canHandle("data.csv", "id,title,message"))
    }

    @Test
    fun `PP-205 rejects json missing conversations key`() {
        val preview = "{\"messages\":[{\"anon_user_id\":null,\"sender\":\"human\",\"message\":\"hi\"}]}"
        assertFalse(parser.canHandle("export.json", preview))
    }

    // --- parse (String) tests ---

    @Test
    fun `PP-210 parse extracts conversation id and title`() {
        val (records, _) = parser.parse("prod-grok-backend.json", singleConversation)
        assertEquals(1, records.size)
        assertEquals("conv-xai-001", records[0].externalId)
        assertEquals("What is Grok?", records[0].title)
    }

    @Test
    fun `PP-211 parse extracts messages with correct roles from sender field`() {
        val (records, _) = parser.parse("prod-grok-backend.json", singleConversation)
        val messages = records[0].messages
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
    }

    @Test
    fun `PP-212 parse extracts message content from message field`() {
        val (records, _) = parser.parse("prod-grok-backend.json", singleConversation)
        assertEquals("What is Grok?", records[0].messages[0].content)
        assertEquals("Grok is an AI assistant by xAI.", records[0].messages[1].content)
    }

    @Test
    fun `PP-213 parse decodes MongoDB extended JSON timestamp on responses`() {
        val ms = 1731666601000L
        val json =
            buildExport(
                buildConversation(responses = buildResponse(sender = "human", message = "hi", ms = ms)),
            )
        val (records, _) = parser.parse("prod-grok-backend.json", json)
        assertEquals(ms, records[0].messages[0].createdAtMs)
    }

    @Test
    fun `PP-214 parse sets provider to XAI`() {
        val (records, _) = parser.parse("prod-grok-backend.json", singleConversation)
        assertEquals(ConversationProvider.XAI, records[0].provider)
    }

    @Test
    fun `PP-215 parse returns health report with correct counts`() {
        val (records, report) = parser.parse("prod-grok-backend.json", singleConversation)
        assertEquals(1, report.totalEntries)
        assertEquals(1, report.extractedConversations)
        assertEquals(2, report.totalMessages)
        assertEquals(2, report.messagesWithText)
        assertEquals(0, report.skippedMalformed)
    }

    @Test
    fun `PP-216 parse handles conversation with empty responses array`() {
        val json = buildExport(buildConversation(responses = ""))
        val (records, report) = parser.parse("prod-grok-backend.json", json)
        assertEquals(1, records.size)
        assertEquals(0, records[0].messages.size)
    }

    @Test
    fun `PP-217 parse skips malformed entries without throwing`() {
        val good =
            buildConversation(
                id = "ok",
                responses = buildResponse(sender = "human", message = "hello"),
            )
        val bad = "{\"not_valid\":true}"
        val good2 =
            buildConversation(
                id = "ok2",
                responses = buildResponse(sender = "human", message = "world"),
            )
        val json = buildExport(good, bad, good2)
        val (records, report) = parser.parse("prod-grok-backend.json", json)
        assertEquals(2, records.size)
        assertEquals(1, report.skippedMalformed)
    }

    @Test
    fun `PP-218 parse skips blank message content`() {
        val json =
            buildExport(
                buildConversation(
                    responses =
                        buildResponse(sender = "human", message = "   ") +
                            "," + buildResponse(id = "r2", sender = "ASSISTANT", message = "real content"),
                ),
            )
        val (records, _) = parser.parse("prod-grok-backend.json", json)
        assertEquals(1, records[0].messages.size)
        assertEquals("real content", records[0].messages[0].content)
    }

    @Test
    fun `PP-219 parse handles multiple conversations`() {
        val json =
            buildExport(
                buildConversation(id = "c1", title = "First", responses = buildResponse(sender = "human", message = "hello")),
                buildConversation(id = "c2", title = "Second", responses = buildResponse(sender = "human", message = "world")),
            )
        val (records, _) = parser.parse("prod-grok-backend.json", json)
        assertEquals(2, records.size)
        assertEquals("c1", records[0].externalId)
        assertEquals("c2", records[1].externalId)
    }

    // --- parseReader (streaming) tests ---

    @Test
    fun `PP-220 parseReader produces same result as parse`() {
        val (recordsFromString, _) = parser.parse("prod-grok-backend.json", singleConversation)
        val (recordsFromReader, _) = parser.parseReader("prod-grok-backend.json", StringReader(singleConversation))
        assertEquals(recordsFromString.size, recordsFromReader.size)
        assertEquals(recordsFromString[0].externalId, recordsFromReader[0].externalId)
        assertEquals(recordsFromString[0].messages.size, recordsFromReader[0].messages.size)
    }

    @Test
    fun `PP-221 parseReader handles large chunked input without loading full file`() {
        val conversations =
            (0 until 100).joinToString(",") { i ->
                buildConversation(
                    id = "conv-$i",
                    title = "Convo $i",
                    responses = buildResponse(id = "r$i", sender = "human", message = "msg $i"),
                )
            }
        val json = buildExport(conversations)
        val (records, report) = parser.parseReader("prod-grok-backend.json", StringReader(json))
        assertEquals(100, records.size)
        assertEquals(100, report.extractedConversations)
    }

    @Test
    fun `PP-222 parse normalises uppercase ASSISTANT sender to ASSISTANT role`() {
        val json =
            buildExport(
                buildConversation(
                    responses =
                        buildResponse(sender = "human", message = "q") +
                            "," + buildResponse(id = "r2", sender = "ASSISTANT", message = "a"),
                ),
            )
        val (records, _) = parser.parse("prod-grok-backend.json", json)
        assertEquals(MessageRole.USER, records[0].messages[0].role)
        assertEquals(MessageRole.ASSISTANT, records[0].messages[1].role)
    }

    @Test
    fun `PP-223 parse uses ISO-8601 create_time from conversation object`() {
        val json =
            buildExport(
                buildConversation(
                    createTime = "2024-11-15T10:30:00Z",
                    responses = buildResponse(sender = "human", message = "hi"),
                ),
            )
        val (records, _) = parser.parse("prod-grok-backend.json", json)
        assertNotNull(records[0].createdAtMs)
        assertEquals(1731666600000L, records[0].createdAtMs)
    }
}
