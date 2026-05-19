package com.browntowndev.pocketcrew.core.agent

import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeFailureReason
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TOOL-PARSE-001 LI-03: real UniFFI round-trip through [RustToolEnvelopeParser].
 */
class RustToolEnvelopeParserContractTest {
    private val parser = RustToolEnvelopeParser()

    @Test
    fun li03RealAdapterRoundTripsAllEnvelopeShapes() {
        val toolCallRaw =
            """<tool_call>{"name":"web_search","arguments":{"query":"android compose"}}</tool_call>"""
        val toolCallBatch = parser.parse(toolCallRaw)
        assertEquals(1, toolCallBatch.successes.size)
        assertEquals("web_search", toolCallBatch.successes[0].toolName)
        assertEquals("""{"query":"android compose"}""", toolCallBatch.successes[0].argumentsJson)
        assertTrue(toolCallBatch.failures.isEmpty())

        val mistralRaw =
            """[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"news"}},""" +
                """{"name":"search_chat_history","arguments":{"query":"old chats"}}]"""
        val mistralBatch = parser.parse(mistralRaw)
        assertEquals(2, mistralBatch.successes.size)
        assertEquals("search_chat_history", mistralBatch.successes[1].toolName)
        assertTrue(
            mistralBatch.successes[1].argumentsJson.contains(""""queries":["old chats"]"""),
            "expected coerced search_chat_history args, got ${mistralBatch.successes[1].argumentsJson}",
        )

        val dsmlRaw =
            "<｜｜DSML｜｜tool_calls>" +
                "<｜｜DSML｜｜invoke name=\"unknown_tool\">" +
                "<｜｜DSML｜｜parameter name=\"query\" string=\"true\">x" +
                "</｜｜DSML｜｜parameter>" +
                "</｜｜DSML｜｜invoke>" +
                "</｜｜DSML｜｜tool_calls>"
        val dsmlBatch = parser.parse(dsmlRaw)
        assertTrue(dsmlBatch.successes.isEmpty())
        assertEquals(1, dsmlBatch.failures.size)
        val reason = dsmlBatch.failures[0].reason
        assertTrue(
            reason is ToolEnvelopeFailureReason.UnknownTool && reason.name == "unknown_tool",
            "expected UnknownTool(unknown_tool), got $reason",
        )

        val appendBlocksRaw =
            """<tool_call>{"name":"append_blocks","arguments":{"artifactId":"artifact-1","blocks":[{"type":"heading","level":1,"text":"Research Document"}]}}</tool_call>"""
        val appendBlocksBatch = parser.parse(appendBlocksRaw)
        assertTrue(appendBlocksBatch.failures.isEmpty())
        assertEquals(1, appendBlocksBatch.successes.size)
        assertEquals("append_blocks", appendBlocksBatch.successes[0].toolName)
        assertTrue(
            appendBlocksBatch.successes[0].argumentsJson.contains(""""isComplete":false"""),
            "expected append_blocks default, got ${appendBlocksBatch.successes[0].argumentsJson}",
        )

        val startArtifactBatch =
            parser.parse(
                """<tool_call>{"name":"start_artifact","arguments":{"title":"Research Brief"}}</tool_call>""",
            )
        assertTrue(startArtifactBatch.failures.isEmpty())
        assertEquals("start_artifact", startArtifactBatch.successes.single().toolName)

        val manageMemoriesBatch =
            parser.parse(
                """<tool_call>{"name":"manage_memories","arguments":{"action":"search"}}</tool_call>""",
            )
        assertTrue(manageMemoriesBatch.failures.isEmpty())
        assertEquals("manage_memories", manageMemoriesBatch.successes.single().toolName)

        val searchArtifactBatch =
            parser.parse(
                """<tool_call>{"name":"search_current_artifact","arguments":{"artifactId":"artifact-1","query":"summary"}}</tool_call>""",
            )
        assertTrue(searchArtifactBatch.failures.isEmpty())
        assertEquals("search_current_artifact", searchArtifactBatch.successes.single().toolName)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadAgentCoreLibrary() {
            RustToolParserTestLibrary.loadAgentCoreLibrary()
        }
    }
}
