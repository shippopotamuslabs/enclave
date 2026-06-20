package com.browntowndev.enclave.feature.inference.wire

/**
 * Per-provider strategy that renders a classified [AgentLoopRowKind] into a provider-native
 * wire message.
 *
 * Each provider (OpenAI Chat Completions, Anthropic Messages, Google GenerateContent) implements
 * this interface with its SDK-specific builder calls. The shared dispatcher walks the transcript,
 * calls [classify] once per row, and delegates rendering here. This collapses the duplicated
 * role/tool-call/tool-result/summary dispatch while preserving every provider's leaf-builder
 * behavior and per-provider policy on orphan tool results and null-id tool calls.
 *
 * `WireMessage` is the provider's native message type:
 * - OpenAI: `com.openai.models.chat.completions.ChatCompletionMessageParam`
 * - Anthropic: `com.anthropic.models.messages.MessageParam`
 * - Google: `com.google.genai.types.Content`
 *
 * Methods return `null` when the provider drops the row (e.g. Google drops `role: system`
 * because system instructions ride on the config, not the contents list).
 */
interface ProviderWireMessageMapper<WireMessage> {
    /**
     * Render a `role: system` transcript row. [useCacheControl] enables OpenRouter cache parts.
     * Returns `null` when the provider drops system rows from the contents list (Google folds
     * system instructions onto the config; Anthropic sets them on the params builder).
     */
    fun systemPrompt(content: String, useCacheControl: Boolean): WireMessage?

    /** Render a `role: user` prompt row, attaching [imageUris] when non-empty. */
    fun userText(content: String, imageUris: List<String>, useCacheControl: Boolean): WireMessage

    /** Render an engine-internal row; return `null` to drop (e.g. when wire text is empty). */
    fun engineInternal(wireText: String): WireMessage?

    /** Render a compaction-summary row. */
    fun compactSummary(content: String, useCacheControl: Boolean): WireMessage

    /** Render a tool-result row; [isOrphan] flags results whose tool call is not in the transcript. */
    fun toolResult(
        toolCallId: String,
        content: String,
        isOrphan: Boolean,
        useCacheControl: Boolean,
    ): WireMessage

    /**
     * Render a `role: assistant` row. Returns `null` when the provider drops it (e.g. OpenAI
     * drops an assistant row whose normalized content is blank and whose tool calls all lack ids).
     * [reasoningContent] is the provider reasoning captured on a prior turn, replayed only when
     * the provider's policy permits it.
     */
    fun assistantText(
        content: String,
        toolCalls: List<RawToolCallRef>,
        reasoningContent: String?,
        useCacheControl: Boolean,
    ): WireMessage?
}
