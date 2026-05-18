package com.browntowndev.pocketcrew

import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeField
import com.browntowndev.pocketcrew.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ToolEnvelopeParserFacadeInstallerTest {

    @AfterEach
    fun resetToolEnvelopeParserPort() {
        ToolEnvelopeParser.portProvider = null
    }

    @Test
    fun install_wiresFacadeToInjectedPort() {
        val port = RecordingToolEnvelopeParserPort()

        ToolEnvelopeParserFacadeInstaller(port).install()

        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope("raw")

        assertEquals(listOf("raw"), port.parseCalls)
        assertEquals("web_search", envelope?.toolName)
    }

    private class RecordingToolEnvelopeParserPort : ToolEnvelopeParserPort {
        val parseCalls = mutableListOf<String>()

        override fun parse(raw: String): ToolEnvelopeBatch {
            parseCalls.add(raw)
            return ToolEnvelopeBatch(
                successes = listOf(
                    ToolEnvelopeField(
                        toolName = "web_search",
                        argumentsJson = """{"query":"android"}""",
                        repaired = false,
                        repairs = emptyList(),
                    ),
                ),
                failures = emptyList(),
                visiblePrefix = "",
                visibleSuffix = "",
            )
        }

        override fun coerceProviderArguments(toolName: String, argsJson: String): ToolEnvelopeField =
            ToolEnvelopeField(
                toolName = toolName,
                argumentsJson = argsJson,
                repaired = false,
                repairs = emptyList(),
            )

        override fun hasLocalToolContract(systemPrompt: String?): Boolean = false

        override fun buildToolResultMessage(resultJson: String): String =
            "<tool_result>$resultJson</tool_result>"
    }
}
