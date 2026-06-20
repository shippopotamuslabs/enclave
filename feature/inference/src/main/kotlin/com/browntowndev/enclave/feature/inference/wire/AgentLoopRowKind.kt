package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage

/**
 * Provider-agnostic classification of a single [AgentLoopMessage] row in the Rust agent-loop
 * transcript. Computed once by [classify] from `(role, id, metadata)`; each provider's
 * [ProviderWireMessageMapper] then renders the kind into its native SDK message type.
 *
 * Replaces the role/call_id/raw_tool_calls/compact-summary/engine-internal dispatch duplicated
 * across the OpenAI, Anthropic, Google, and Llama services. Per-provider policy on orphan tool
 * results and empty tool-call ids is delegated to the wire mapper via [ToolResult.isOrphan] and
 * [AssistantText.toolCalls] entries with nullable ids, so no behavior is silently unified away.
 */
sealed class AgentLoopRowKind {
    /** A `role: system` transcript row (separate from the stable system-prompt prepend). */
    data class SystemPrompt(val content: String) : AgentLoopRowKind()

    /** A `role: user` prompt row; [attachImages] is true only at the image-target index. */
    data class UserText(val content: String, val attachImages: Boolean) : AgentLoopRowKind()

    /**
     * Engine-internal infrastructure row (budget, nudge, envelope failure). [wireText] is the
     * [com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper.formatForProviderWire]
     * output; a blank wire text means the row is dropped entirely (returned as `null` by
     * [classify] so the dispatcher skips it).
     */
    data class EngineInternal(val wireText: String) : AgentLoopRowKind()

    /** Compaction-summary row produced by the Rust engine. */
    data class CompactSummary(val content: String) : AgentLoopRowKind()

    /**
     * A tool-result row (`role: user` + `call_id` metadata). [isOrphan] is true when the
     * addressed tool call is not present in the [pendingToolCallIds] set maintained by the
     * dispatcher; providers handle orphans differently (OpenAI rewrites to a user message).
     */
    data class ToolResult(
        val toolCallId: String,
        val content: String,
        val isOrphan: Boolean,
    ) : AgentLoopRowKind()

    /**
     * A `role: assistant` row carrying text and/or prior tool calls. [toolCalls] entries with a
     * `null` id are retained for providers that tolerate them (Anthropic, Google); the OpenAI
     * mapper drops them. Returns `null` from [classify] when both content is blank and no tool
     * calls remain after provider id filtering — but the classifier itself only returns null when
     * content is blank AND toolCalls is empty, leaving id-filtering policy to the mapper.
     */
    data class AssistantText(
        val content: String,
        val toolCalls: List<RawToolCallRef>,
        val reasoningContent: String?,
    ) : AgentLoopRowKind()
}
