package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalToolResultPolicyTest {

    @Test
    fun `evaluate appends stop warning even when truncation returns an error payload`() {
        val decision = LocalToolResultPolicy.evaluate(
            rawResultJson = """{"results":[{"title":"One","url":"https://example.com","content":"short"}]}""",
            contextWindowTokens = 2_000,
            currentSystemPrompt = "x ".repeat(4_000),
            history = listOf(ChatMessage(Role.USER, "hello")),
            transientToolResultTokens = 0,
            modelId = null,
        )

        assertTrue(
            decision.finalResult.contains("""{"error": "cannot read page, context window too full"}"""),
            "Expected tool result truncation to return a compact error payload.",
        )
        assertTrue(
            decision.finalResult.contains(ContextWindowPlanner.STOP_TOOLS_WARNING),
            "Expected stop-tools warning to be appended even when truncation returns an error payload.",
        )
        assertTrue(decision.contextFull)
        assertFalse(decision.shouldTrackTransientToolResult)
    }

    @Test
    fun `buildContextWindowExceededToolError returns a compact warning payload`() {
        val result = LocalToolResultPolicy.buildContextWindowExceededToolError("tavily_web_search")

        assertTrue(result.contains("tool_execution_failed"))
        assertTrue(result.contains("Context window exceeded"))
    }
}
