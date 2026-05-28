package com.browntowndev.enclave.core.agent

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TOOL-PARSE-002 K-01, K-02 — JNI contract for [RustToolStreamGate].
 */
class RustToolStreamGateContractTest {
    private val gatePort = RustToolStreamGate()

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadAgentCoreLibrary() {
            RustToolParserTestLibrary.loadAgentCoreLibrary()
        }
    }

    private val dsmlSample =
        """
        <|DSML|tool_calls><|DSML|invoke name="web_search">
        <|DSML|parameter name="query" string="true">kotlin</|DSML|parameter>
        </|DSML|invoke></|DSML|tool_calls>
        """.trimIndent()

    @Test
    fun k01ChunkedDsmlNeverEmittedViaPort() {
        val handle = gatePort.create(allowToolCall = true)
        var emitted = ""
        dsmlSample.chunked(17).forEach { chunk ->
            emitted += gatePort.push(handle, chunk).emittedPrefix
        }
        val finish = gatePort.finish(handle, hasNativeToolCalls = false)
        assertEquals("", emitted, "K-01 emitted prefix")
        assertEquals("", finish.trailingFlush, "K-01 trailing_flush")
        assertTrue(finish.hasToolMarker, "K-01 has_tool_marker")
    }

    @Test
    fun k02BufferedTextRetainsFullDsmlSample() {
        val handle = gatePort.create(allowToolCall = true)
        gatePort.push(handle, dsmlSample)
        assertEquals(dsmlSample, gatePort.bufferedText(handle), "K-02 bufferedText")
    }
}
