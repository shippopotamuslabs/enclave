package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.AgentRawToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentTurnTextGateTest {

    @Test
    fun `onTextToken buffers chunked DSML tool calls instead of emitting them`() = runTest {
        val gate = AgentTurnTextGate(allowToolCall = true)
        val emittedText = StringBuilder()
        val leakedText =
            """
            < | DSML | tool_calls>< | DSML | invoke name="append_blocks">
            < | DSML | parameter name="artifactId" string="true">V8B4OgY3SsNCOApkcyVwZtBK7IF3</ | DSML | parameter>
            < | DSML | parameter name="blocks" string="false">[{"type":"heading","level":1,"text":"Research Document"}]</ | DSML | parameter>
            < | DSML | parameter name="isComplete" string="false">false</ | DSML | parameter>
            </ | DSML | invoke></ | DSML | tool_calls>
            """.trimIndent()

        leakedText.chunked(17).forEach { chunk ->
            gate.onTextToken(chunk) { emittedText.append(it) }
        }
        val toolCalls = gate.leakedToolCalls(emptyList())
        val assistantText = gate.visibleAssistantText(toolCalls) { emittedText.append(it) }

        assertEquals("", emittedText.toString())
        assertNull(assistantText)
        assertEquals(1, toolCalls.size)
        assertEquals("append_blocks", toolCalls[0].name)
        assertEquals(
            """{"artifactId":"V8B4OgY3SsNCOApkcyVwZtBK7IF3","blocks":[{"type":"heading","level":1,"text":"Research Document"}],"isComplete":false}""",
            toolCalls[0].argumentsJson,
        )
    }

    @Test
    fun `visibleAssistantText flushes buffered text when no tool call leaked`() = runTest {
        val gate = AgentTurnTextGate(allowToolCall = true)
        val emittedText = StringBuilder()
        val responseText = "This is a normal answer without a tool call."

        responseText.chunked(8).forEach { chunk ->
            gate.onTextToken(chunk) { emittedText.append(it) }
        }
        val assistantText = gate.visibleAssistantText(emptyList<AgentRawToolCall>()) { emittedText.append(it) }

        assertEquals(responseText, assistantText)
        assertEquals(responseText, emittedText.toString())
    }

    @Test
    fun `onTextToken buffers fullwidth DSML tool calls instead of emitting them`() = runTest {
        val gate = AgentTurnTextGate(allowToolCall = true)
        val emittedText = StringBuilder()
        val leakedText =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">android api 36</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        leakedText.chunked(11).forEach { chunk ->
            gate.onTextToken(chunk) { emittedText.append(it) }
        }
        val toolCalls = gate.leakedToolCalls(emptyList())
        val assistantText = gate.visibleAssistantText(toolCalls) { emittedText.append(it) }

        assertEquals("", emittedText.toString())
        assertNull(assistantText)
        assertEquals(1, toolCalls.size)
        assertEquals("web_search", toolCalls[0].name)
        assertEquals("""{"query":"android api 36"}""", toolCalls[0].argumentsJson)
    }
}
