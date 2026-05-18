package com.browntowndev.pocketcrew.core.agent

import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class AgentEventMapperTest {
    @Test
    fun toDomain_compactionApplied_mapsMetadataOnly() {
        val event = FfiAgentEvent.CompactionApplied(
            FfiCompactionEvent(
                tier = FfiCompactionTier.ARTIFACT_SQUASH,
                tokensBefore = 100u,
                tokensAfter = 40u,
                trigger = FfiCompactionTrigger.MID_TURN,
            ),
        )

        val mapped = assertIs<AgentLoopEvent.CompactionApplied>(AgentEventMapper.toDomain(event))

        assertEquals("ARTIFACT_SQUASH", mapped.tier)
        assertEquals(100, mapped.tokensBefore)
        assertEquals(40, mapped.tokensAfter)
        assertEquals("MID_TURN", mapped.trigger)
    }

    @Test
    fun toDomain_lifecycleEvents_mapToDomainEvents() {
        val pre = assertIs<AgentLoopEvent.PreCompact>(
            AgentEventMapper.toDomain(FfiAgentEvent.PreCompact(FfiCompactionTrigger.PREFLIGHT)),
        )
        val post = assertIs<AgentLoopEvent.PostCompact>(
            AgentEventMapper.toDomain(
                FfiAgentEvent.PostCompact(
                    trigger = FfiCompactionTrigger.PREFLIGHT,
                    summaryRef = null,
                    historyRef = "history-u0-a0",
                ),
            ),
        )

        assertEquals("PREFLIGHT", pre.trigger)
        assertEquals("history-u0-a0", post.historyRef)
    }

    @Test
    fun log_toolRepaired_omitsArguments() {
        val logger = RecordingLogger()

        AgentEventMapper.log(
            FfiAgentEvent.ToolRepaired(
                originalName = "web_serch",
                resolvedName = "web_search",
                confidence = 0.9f,
            ),
            logger,
        )

        val message = logger.messages.single()
        assertFalse(message.contains("arguments", ignoreCase = true))
        assertFalse(message.contains("{"))
    }

    private class RecordingLogger : LoggingPort {
        val messages = mutableListOf<String>()

        override fun debug(tag: String, message: String) {
            messages += message
        }

        override fun info(tag: String, message: String) {
            messages += message
        }

        override fun warning(tag: String, message: String) {
            messages += message
        }

        override fun error(tag: String, message: String, throwable: Throwable?) {
            messages += message
        }

        override fun recordException(tag: String, message: String, throwable: Throwable) {
            messages += message
        }
    }
}
