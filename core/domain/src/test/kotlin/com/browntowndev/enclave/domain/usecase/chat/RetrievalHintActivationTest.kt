package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RetrievalHintActivationTest {
    @Test
    fun toolsFromRetrievalHint_activates_read_context_reference() {
        assertEquals(
            listOf("read_context_reference"),
            RetrievalHintActivation.toolsFromRetrievalHint(
                """read_context_reference("tool-output-call-1")""",
            ),
        )
    }

    @Test
    fun toolsFromToolResults_activates_on_spill_threshold() {
        val oversized =
            AgentToolResult(
                callId = "call-1",
                resultJson = "x".repeat(RetrievalHintActivation.DEFAULT_TOOL_SPILL_THRESHOLD_BYTES + 1),
                truncated = false,
                softFailure = false,
            )

        assertEquals(
            listOf("read_context_reference"),
            RetrievalHintActivation.toolsFromToolResults(listOf(oversized)),
        )
    }

    @Test
    fun toolsFromToolResults_activates_on_spill_envelope() {
        val spilled =
            AgentToolResult(
                callId = "call-1",
                resultJson =
                    """{"context_ref":"tool-output-call-1","retrieval_hint":"read_context_reference(\"tool-output-call-1\")"}""",
                truncated = true,
                softFailure = false,
            )

        assertEquals(
            listOf("read_context_reference"),
            RetrievalHintActivation.toolsFromToolResults(listOf(spilled)),
        )
    }

    @Test
    fun toolsFromRetrievalHint_ignores_unknown_hints() {
        assertTrue(
            RetrievalHintActivation.toolsFromRetrievalHint("search_tools(\"memory\")").isEmpty(),
        )
    }
}