package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper

/**
 * Classifies an [AgentLoopMessage] into a provider-agnostic [AgentLoopRowKind].
 *
 * [pendingToolCallIds] is the dispatcher's mutable set of tool-call ids emitted by prior
 * assistant rows that have not yet been addressed by a tool result. When a tool-result row's
 * `call_id` is present in the set, the entry is removed and [AgentLoopRowKind.ToolResult.isOrphan]
 * is false; otherwise the result is an orphan. The set is mutated in place so the caller can
 * keep tracking across rows.
 *
 * Returns `null` when the row should be dropped from the wire:
 * - engine-internal rows whose [EngineInternalMessageMapper.formatForProviderWire] output is blank;
 * - assistant rows whose content is blank and whose `raw_tool_calls` is empty/absent.
 *
 * Per-provider policy on null-id tool calls and orphan rendering is delegated to the wire mapper;
 * the classifier only reports the raw shape.
 */
fun AgentLoopMessage.classify(
    attachImages: Boolean,
    pendingToolCallIds: MutableSet<String>,
): AgentLoopRowKind? {
    if (EngineInternalMessageMapper.isEngineInternalMessage(metadataJson)) {
        val wireText = EngineInternalMessageMapper.formatForProviderWire(content)
        if (wireText.isBlank()) return null
        return AgentLoopRowKind.EngineInternal(wireText = wireText)
    }

    val metadata = metadata()
    val toolCallId = metadata.toolCallId
    if (toolCallId != null) {
        val isOrphan = toolCallId.isBlank() || !pendingToolCallIds.remove(toolCallId)
        return AgentLoopRowKind.ToolResult(
            toolCallId = toolCallId,
            content = content,
            isOrphan = isOrphan,
        )
    }

    return when (role) {
        Role.SYSTEM -> AgentLoopRowKind.SystemPrompt(content = content)
        Role.USER -> AgentLoopRowKind.UserText(content = content, attachImages = attachImages)
        Role.ASSISTANT -> classifyAssistant(metadata)
    }
}

private fun AgentLoopMessage.classifyAssistant(
    metadata: AgentLoopMessageMetadata,
): AgentLoopRowKind? {
    if (isCompactSummary()) {
        return AgentLoopRowKind.CompactSummary(content = content)
    }
    val toolCalls = metadata.rawToolCalls
    if (content.isBlank() && toolCalls.isEmpty()) {
        return null
    }
    return AgentLoopRowKind.AssistantText(
        content = content,
        toolCalls = toolCalls,
        reasoningContent = metadata.providerReasoningContent,
    )
}
