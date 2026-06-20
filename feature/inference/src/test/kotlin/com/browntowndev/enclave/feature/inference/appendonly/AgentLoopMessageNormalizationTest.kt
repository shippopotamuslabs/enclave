package com.browntowndev.enclave.feature.inference.appendonly

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentLoopMessageNormalizationTest {
    @Test
    fun toAppendOnlySyncMessages_excludes_engine_internal_metadata() {
        val messages =
            listOf(
                AgentLoopMessage(id = "u1", role = Role.USER, content = "Search"),
                AgentLoopMessage(
                    id = "a1",
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson =
                        """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{}"}]}""",
                ),
                AgentLoopMessage(
                    id = "t1",
                    role = Role.USER,
                    content = """{"results":[]}""",
                    metadataJson = """{"call_id":"call_1"}""",
                ),
                AgentLoopMessage(
                    id = "budget",
                    role = Role.ASSISTANT,
                    content = "<system_context>\nTool budget: 1/35 used.\n</system_context>",
                    metadataJson = """{"agent_tool_budget":true}""",
                ),
            )

        val syncMessages = messages.toAppendOnlySyncMessages()
        val allNormalized = messages.toProviderNormalizedMessages()

        assertEquals(3, syncMessages.size)
        assertEquals(4, allNormalized.size)
        assertEquals("Search", syncMessages[0].content)
        assertEquals("call_1", syncMessages[2].toolCallId)
    }

    @Test
    fun toAppendOnlySyncMessages_matches_committed_transcript_cardinality() {
        val context = ProviderAppendOnlyContext()
        val turnOneSession =
            listOf(
                AgentLoopMessage(id = "u1", role = Role.USER, content = "prompt"),
                AgentLoopMessage(
                    id = "a1",
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson =
                        """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{}"}]}""",
                ),
                AgentLoopMessage(
                    id = "t1",
                    role = Role.USER,
                    content = """{"results":[]}""",
                    metadataJson = """{"call_id":"call_1"}""",
                ),
                AgentLoopMessage(
                    id = "budget",
                    role = Role.USER,
                    content = "<system_context>\nTool budget: 1/35 used.\n</system_context>",
                    metadataJson = """{"agent_tool_budget":true}""",
                ),
            )
        context.syncMessages(turnOneSession.toAppendOnlySyncMessages())
        assertEquals(3, context.committedMessageCount())

        val turnTwoRehydrated =
            listOf(
                AgentLoopMessage(id = "u1", role = Role.USER, content = "prompt"),
                AgentLoopMessage(
                    id = "a1",
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson =
                        """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{}"}]}""",
                ),
                AgentLoopMessage(
                    id = "t1",
                    role = Role.USER,
                    content = """{"results":[]}""",
                    metadataJson = """{"call_id":"call_1"}""",
                ),
                AgentLoopMessage(id = "u2", role = Role.USER, content = "follow up"),
            )
        context.syncMessages(turnTwoRehydrated.toAppendOnlySyncMessages())

        assertEquals(0, context.clearCount)
        assertEquals(4, context.committedMessageCount())
    }
}
