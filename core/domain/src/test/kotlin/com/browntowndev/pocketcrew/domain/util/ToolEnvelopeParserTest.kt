package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeFailure
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeFailureReason
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeField
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ToolEnvelopeParserTest {
    private lateinit var fakePort: FakeToolEnvelopeParserPort

    @BeforeEach
    fun installFakeToolEnvelopeParserPort() {
        fakePort = FakeToolEnvelopeParserPort()
        ToolEnvelopeParser.portProvider = { fakePort }
    }

    @AfterEach
    fun resetToolEnvelopeParserPort() {
        ToolEnvelopeParser.portProvider = null
    }

    @Test
    fun extractLocalToolEnvelope_returnsFirstPortSuccess() {
        fakePort.parseResponses["INPUT"] =
            ToolEnvelopeBatch(
                successes = listOf(ToolEnvelopeField("web_search", """{"query":"q1"}""", false, emptyList())),
                failures = emptyList(),
                visiblePrefix = "before",
                visibleSuffix = "after",
            )

        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope("INPUT")

        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"q1"}""", envelope.argumentsJson)
        assertEquals("before", envelope.visiblePrefix)
        assertEquals("after", envelope.visibleSuffix)
        assertEquals(listOf("INPUT"), fakePort.parseCalls)
    }

    @Test
    fun extractLocalToolEnvelopes_returnsMultiplePortSuccesses() {
        fakePort.parseResponses["INPUT"] =
            ToolEnvelopeBatch(
                successes =
                    listOf(
                        ToolEnvelopeField("web_search", """{"query":"first"}""", false, emptyList()),
                        ToolEnvelopeField("web_search", """{"query":"second"}""", false, emptyList()),
                    ),
                failures = emptyList(),
                visiblePrefix = "",
                visibleSuffix = "",
            )

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes("INPUT")

        assertEquals(2, result.size)
        assertEquals("""{"query":"first"}""", result[0].argumentsJson)
        assertEquals("""{"query":"second"}""", result[1].argumentsJson)
    }

    @Test
    fun extractLocalToolEnvelopes_returnsEmptyWhenPortReturnsNoSuccessesOrFailures() {
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes("plain assistant text")

        assertEquals(emptyList<ToolEnvelopeParser.LocalToolEnvelope>(), result)
        assertEquals(listOf("plain assistant text"), fakePort.parseCalls)
    }

    @Test
    fun malformedEnvelopeMessages_areMappedFromStructuredFailures() {
        fakePort.parseResponses["tool_call"] =
            failureBatch(
                ToolEnvelopeFailure(ToolEnvelopeFailureReason.MalformedEnvelope, "raw"),
            )
        fakePort.parseResponses["dsml"] =
            failureBatch(
                ToolEnvelopeFailure(ToolEnvelopeFailureReason.MalformedEnvelope, "raw", formatHint = "dsml"),
            )

        val nonDsml =
            assertFailsWith<IllegalStateException> {
                ToolEnvelopeParser.extractLocalToolEnvelopes("tool_call")
            }
        val dsml =
            assertFailsWith<IllegalStateException> {
                ToolEnvelopeParser.extractLocalToolEnvelopes("dsml")
            }

        assertEquals("Malformed tool_call envelope", nonDsml.message)
        assertEquals("Malformed DSML tool envelope", dsml.message)
    }

    @Test
    fun validationFailures_areMappedToStableExceptionMessages() {
        fakePort.parseResponses["missing"] =
            failureBatch(
                ToolEnvelopeFailure(
                    reason = ToolEnvelopeFailureReason.MissingRequiredParameter("web_search", "query"),
                    original = "raw",
                ),
            )
        fakePort.parseResponses["missing_urls"] =
            failureBatch(
                ToolEnvelopeFailure(
                    reason = ToolEnvelopeFailureReason.MissingRequiredParameter("web_extract", "urls"),
                    original = "raw",
                ),
            )
        fakePort.parseResponses["unknown_tool"] =
            failureBatch(
                ToolEnvelopeFailure(ToolEnvelopeFailureReason.UnknownTool("unknown_tool"), "raw"),
            )
        fakePort.parseResponses["unknown_param"] =
            failureBatch(
                ToolEnvelopeFailure(ToolEnvelopeFailureReason.UnknownParameter("web_search", "extra"), "raw"),
            )
        fakePort.parseResponses["duplicate"] =
            failureBatch(
                ToolEnvelopeFailure(ToolEnvelopeFailureReason.DuplicateParameter("web_search", "query"), "raw"),
            )

        assertEquals(
            "Tool argument 'query' is required",
            assertFailsWith<IllegalArgumentException> {
                ToolEnvelopeParser.extractLocalToolEnvelopes("missing")
            }.message,
        )
        assertEquals(
            "Tool argument 'urls' is required and must be a non-empty array",
            assertFailsWith<IllegalArgumentException> {
                ToolEnvelopeParser.extractLocalToolEnvelopes("missing_urls")
            }.message,
        )
        assertEquals(
            "Unsupported tool: unknown_tool",
            assertFailsWith<IllegalArgumentException> {
                ToolEnvelopeParser.extractLocalToolEnvelopes("unknown_tool")
            }.message,
        )
        assertEquals(
            "Unknown parameter 'extra' for tool 'web_search'",
            assertFailsWith<IllegalArgumentException> {
                ToolEnvelopeParser.extractLocalToolEnvelopes("unknown_param")
            }.message,
        )
        assertEquals(
            "Duplicate parameter 'query' for tool 'web_search'",
            assertFailsWith<IllegalArgumentException> {
                ToolEnvelopeParser.extractLocalToolEnvelopes("duplicate")
            }.message,
        )
    }

    @Test
    fun buildArgumentsJson_delegatesToPortCoercion() {
        fakePort.coerceProviderArgumentsResponses[
            "web_search" to """{"query":"weather in London"}""",
        ] = ToolEnvelopeField("web_search", """{"query":"weather in London"}""", false, emptyList())

        val json =
            ToolEnvelopeParser.buildArgumentsJson(
                "web_search",
                mapOf("query" to "weather in London"),
            )

        assertEquals("""{"query":"weather in London"}""", json)
        assertEquals(
            listOf("web_search" to """{"query":"weather in London"}"""),
            fakePort.coerceProviderArgumentsCalls,
        )
    }

    @Test
    fun hasLocalToolContract_delegatesToPort() {
        val withTools = "Use DSML format to call tools. web_search is available."
        fakePort.hasLocalToolContractResponses[withTools] = true
        fakePort.hasLocalToolContractResponses["just chat"] = false

        assertEquals(true, ToolEnvelopeParser.hasLocalToolContract(withTools))
        assertEquals(false, ToolEnvelopeParser.hasLocalToolContract("just chat"))
        assertEquals(listOf<String?>(withTools, "just chat"), fakePort.hasLocalToolContractCalls)
    }

    @Test
    fun extractRequiredUrls_usesPortForMalformedArguments() {
        val malformedArguments = """{"urls":"[\"https://example.com\"]"}"""
        val wrapped = """<tool_call>{"name":"web_extract","arguments":$malformedArguments}</tool_call>"""
        fakePort.parseResponses[wrapped] =
            ToolEnvelopeBatch(
                successes =
                    listOf(
                        ToolEnvelopeField(
                            toolName = "web_extract",
                            argumentsJson = """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
                            repaired = true,
                            repairs = emptyList(),
                        ),
                    ),
                failures = emptyList(),
                visiblePrefix = "",
                visibleSuffix = "",
            )

        val urls = ToolEnvelopeParser.extractRequiredUrls(malformedArguments)

        assertEquals(listOf("https://example.com"), urls)
        assertEquals(listOf(wrapped), fakePort.parseCalls)
    }

    @Test
    fun directHelpers_preserveSourceCompatibility() {
        assertEquals("""{"query":"hello"}""", ToolEnvelopeParser.buildArgumentsJson("hello"))
        assertEquals(
            """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
            ToolEnvelopeParser.buildExtractArgumentsJson(listOf("https://example.com")),
        )
        assertEquals(listOf("url1", "url2"), ToolEnvelopeParser.extractRequiredUrls("""{"urls":["url1","url2"]}"""))
    }

    private fun failureBatch(failure: ToolEnvelopeFailure): ToolEnvelopeBatch =
        ToolEnvelopeBatch(
            successes = emptyList(),
            failures = listOf(failure),
            visiblePrefix = "",
            visibleSuffix = "",
        )
}
