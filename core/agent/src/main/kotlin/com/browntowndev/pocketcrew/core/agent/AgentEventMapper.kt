package com.browntowndev.pocketcrew.core.agent

import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort

object AgentEventMapper {
    private const val TAG = "AgentCore"

    fun toDomain(event: FfiAgentEvent): AgentLoopEvent? =
        when (event) {
            is FfiAgentEvent.TurnStarted -> AgentLoopEvent.TurnStarted(
                index = event.turnIndex.toInt(),
            )
            is FfiAgentEvent.TurnCompleted -> AgentLoopEvent.TurnCompleted(
                index = event.turnIndex.toInt(),
                toolCallCount = event.toolCallCount.toInt(),
            )
            is FfiAgentEvent.CompactionApplied -> AgentLoopEvent.CompactionApplied(
                tier = event.event.tier.name,
                tokensBefore = event.event.tokensBefore.toInt(),
                tokensAfter = event.event.tokensAfter.toInt(),
                trigger = event.event.trigger.name,
            )
            is FfiAgentEvent.ToolRepaired -> AgentLoopEvent.ToolRepaired(
                originalName = event.originalName,
                resolvedName = event.resolvedName,
                confidence = event.confidence,
            )
            is FfiAgentEvent.KvCacheInvalidated -> AgentLoopEvent.KvCacheInvalidated(event.reason)
            is FfiAgentEvent.PreCompact -> AgentLoopEvent.PreCompact(event.trigger.name)
            is FfiAgentEvent.PostCompact -> AgentLoopEvent.PostCompact(
                trigger = event.trigger.name,
                summaryRef = event.summaryRef,
                historyRef = event.historyRef,
            )
            is FfiAgentEvent.ContextPressure,
            is FfiAgentEvent.ToolSoftFailure,
            -> null
        }

    fun log(event: FfiAgentEvent, loggingPort: LoggingPort) {
        when (event) {
            is FfiAgentEvent.CompactionApplied -> loggingPort.info(
                TAG,
                "compaction_applied tier=${event.event.tier.name} " +
                    "trigger=${event.event.trigger.name} " +
                    "tokensBefore=${event.event.tokensBefore} tokensAfter=${event.event.tokensAfter}",
            )
            is FfiAgentEvent.ToolRepaired -> loggingPort.info(
                TAG,
                "tool_repaired original=${event.originalName} " +
                    "resolved=${event.resolvedName} confidence=${event.confidence}",
            )
            is FfiAgentEvent.KvCacheInvalidated -> loggingPort.info(
                TAG,
                "kv_cache_invalidated reason=${event.reason}",
            )
            is FfiAgentEvent.ContextPressure -> loggingPort.debug(
                TAG,
                "context_pressure ratio=${event.ratio}",
            )
            is FfiAgentEvent.PreCompact -> loggingPort.debug(
                TAG,
                "pre_compact trigger=${event.trigger.name}",
            )
            is FfiAgentEvent.PostCompact -> loggingPort.debug(
                TAG,
                "post_compact trigger=${event.trigger.name} historyRef=${event.historyRef}",
            )
            is FfiAgentEvent.ToolSoftFailure -> loggingPort.warning(
                TAG,
                "tool_soft_failure tool=${event.toolName} turn=${event.turnIndex}",
            )
            is FfiAgentEvent.TurnStarted,
            is FfiAgentEvent.TurnCompleted,
            -> Unit
        }
    }
}
