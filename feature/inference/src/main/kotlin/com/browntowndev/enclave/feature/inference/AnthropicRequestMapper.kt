package com.browntowndev.enclave.feature.inference

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigEnabled
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolChoice
import com.anthropic.models.messages.ToolChoiceNone
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toAnthropicJsonValue
import com.browntowndev.enclave.feature.inference.JsonSchemaWireSupport.toWireMap
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContext
import com.browntowndev.enclave.feature.inference.appendonly.ResolvedSidecar
import com.browntowndev.enclave.feature.inference.appendonly.SessionIndexVolatilePlacement
import com.browntowndev.enclave.feature.inference.appendonly.SidecarUserContentEmbedder
import com.browntowndev.enclave.feature.inference.wire.AgentLoopRowKind
import com.browntowndev.enclave.feature.inference.wire.AnthropicWireMessageMapper
import com.browntowndev.enclave.feature.inference.wire.classify
import com.browntowndev.enclave.feature.inference.wire.lastNonToolResultUserIndex
import kotlinx.serialization.json.JsonObject

/**
 * Anthropic Messages request builder for the Rust agent-loop path. Mirrors
 * `OpenAiRequestMapper.mapSessionToResponseParams`: takes the session transcript + stable
 * prefix + tools + sampler options and emits the provider's native request type.
 *
 * Extracted from `AnthropicInferenceServiceImpl.completeSingleTurn` so the full request
 * assembly is testable without an SDK client, matching the symmetry the OpenAI/xAI mappers
 * already provide.
 */
@Suppress("MagicNumber")
internal object AnthropicRequestMapper {
    private const val DEFAULT_MAX_TOKENS = 4096L

    /**
     * Builds a [MessageCreateParams] for one agent-loop turn.
     *
     * @param toolInputProperties parses a tool-call arguments JSON string into the Anthropic SDK
     *   input map; supplied by the service so the stream-decode and replay paths share one parser.
     * @param wireSidecar the live volatile sidecar resolved by the append-only context, or null.
     */
    fun mapSessionToMessageParams(
        modelId: String,
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        options: GenerationOptions,
        appendOnly: ProviderAppendOnlyContext,
        wireSidecar: ResolvedSidecar?,
        toolInputProperties: (String) -> Map<String, JsonValue>,
    ): MessageCreateParams {
        val requestModelId = options.modelId ?: modelId
        val lastUserIndex = sessionMessages.lastNonToolResultUserIndex()
        val imageUris = options.imageUris.take(20)
        val builder =
            MessageCreateParams
                .builder()
                .model(requestModelId)

        var maxTokens =
            if (!options.maxTokensDisabled) {
                options.maxTokens?.toLong() ?: DEFAULT_MAX_TOKENS
            } else {
                DEFAULT_MAX_TOKENS
            }
        val thinkingBudget = resolveThinkingBudget(options)
        if (thinkingBudget != null) {
            if (maxTokens <= thinkingBudget) {
                // Thinking tokens count against max_tokens; +1 would leave a single token for the
                // visible answer and the turn would end mid-thought with stop_reason=max_tokens.
                maxTokens = thinkingBudget + DEFAULT_MAX_TOKENS
            }
            val clampedBudget = thinkingBudget.coerceAtMost(maxTokens - 1)
            builder.thinking(
                ThinkingConfigEnabled
                    .builder()
                    .budgetTokens(clampedBudget)
                    .build(),
            )
        }
        builder.maxTokens(maxTokens)

        val useCaching = AnthropicPromptCacheSupport.supportsPromptCaching(requestModelId)
        val stableTailIndex = AnthropicPromptCacheSupport.stableHistoryTailIndex(sessionMessages)

        if (systemPrompt.isNotBlank()) {
            val systemBlockBuilder = TextBlockParam.builder().text(systemPrompt)
            if (useCaching) {
                systemBlockBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
            }
            builder.systemOfTextBlockParams(listOf(systemBlockBuilder.build()))
        }

        val liveSidecarInsertIndex = lastUserIndex.coerceAtLeast(0)
        val wireMapper = AnthropicWireMessageMapper(toolInputProperties = toolInputProperties)
        val pendingToolCallIds = mutableSetOf<String>()
        sessionMessages.forEachIndexed { index, msg ->
            val attachCacheBreakpoint =
                useCaching &&
                    AnthropicPromptCacheSupport.shouldAttachMessageCacheBreakpoint(index, stableTailIndex)
            val embeddedContent = embedIndexSidecars(
                index = index,
                baseContent = msg.content,
                appendOnly = appendOnly,
                wireSidecar = wireSidecar,
                liveSidecarInsertIndex = liveSidecarInsertIndex,
            )
            val kind = msg.classify(
                attachImages = index == lastUserIndex && imageUris.isNotEmpty(),
                pendingToolCallIds = pendingToolCallIds,
            )
            val mapped: MessageParam? =
                when (kind) {
                    is AgentLoopRowKind.SystemPrompt -> null // systemPrompt set on the builder above
                    is AgentLoopRowKind.UserText ->
                        wireMapper.userText(
                            content = embeddedContent,
                            imageUris = if (kind.attachImages) imageUris else emptyList(),
                            useCacheControl = attachCacheBreakpoint,
                        )
                    is AgentLoopRowKind.EngineInternal ->
                        wireMapper.engineInternal(kind.wireText, useCacheControl = attachCacheBreakpoint)
                    is AgentLoopRowKind.CompactSummary ->
                        wireMapper.compactSummary(embeddedContent, attachCacheBreakpoint)
                    is AgentLoopRowKind.ToolResult ->
                        wireMapper.toolResult(
                            toolCallId = kind.toolCallId,
                            content = embeddedContent,
                            isOrphan = kind.isOrphan,
                            useCacheControl = attachCacheBreakpoint,
                        )
                    is AgentLoopRowKind.AssistantText ->
                        wireMapper.assistantText(
                            content = embeddedContent,
                            toolCalls = kind.toolCalls,
                            reasoningContent = kind.reasoningContent,
                            useCacheControl = attachCacheBreakpoint,
                        )
                    null -> null
                }
            mapped?.let {
                builder.addMessage(it)
                if (kind is AgentLoopRowKind.AssistantText) {
                    pendingToolCallIds.addAll(kind.toolCalls.mapNotNull { tc -> tc.id })
                }
            }
        }

        if (availableTools.isNotEmpty()) {
            availableTools.forEachIndexed { idx, toolDef ->
                val isLast = idx == availableTools.size - 1
                val cacheControl =
                    if (useCaching && isLast) {
                        AnthropicPromptCacheSupport.ephemeralCacheControl()
                    } else {
                        null
                    }
                builder.addTool(toolDef.toAnthropicTool(cacheControl))
            }
            if (!allowToolCall) {
                builder.toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()))
            }
        }
        return builder.build()
    }

    private fun embedIndexSidecars(
        index: Int,
        baseContent: String,
        appendOnly: ProviderAppendOnlyContext,
        wireSidecar: ResolvedSidecar?,
        liveSidecarInsertIndex: Int,
    ): String {
        val markers =
            SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                sessionIndex = index,
                frozenSidecars = appendOnly.frozenSidecarsForWireReplay(),
                preRetrievalSidecar = null,
                preRetrievalInsertIndex = null,
                liveSidecar = wireSidecar,
                tailInsertIndex = liveSidecarInsertIndex,
            )
        return SidecarUserContentEmbedder.embed(baseContent, markers)
    }

    private fun resolveThinkingBudget(options: GenerationOptions): Long? =
        when {
            options.reasoningBudget > 0 -> options.reasoningBudget.toLong().coerceAtLeast(1024L)
            else ->
                when (options.reasoningEffort) {
                    null -> null
                    ApiReasoningEffort.NONE -> null
                    ApiReasoningEffort.LOW -> 1024L
                    ApiReasoningEffort.MEDIUM -> ProviderInferenceConstants.ANTHROPIC_THINKING_BUDGET_MEDIUM
                    ApiReasoningEffort.HIGH -> 2048L
                    ApiReasoningEffort.XHIGH -> ProviderInferenceConstants.ANTHROPIC_THINKING_BUDGET_XHIGH
                }
        }
}

internal fun ToolDefinition.toAnthropicTool(cacheControl: CacheControlEphemeral? = null): Tool {
    val builder =
        Tool
            .builder()
            .name(name)
            .description(description)
            .inputSchema(
                Tool.InputSchema
                    .builder()
                    .type(com.anthropic.core.JsonValue.from("object"))
                    .properties(anthropicToolProperties())
                    .required(schema.required)
                    .build(),
            ).strict(true)
    if (cacheControl != null) {
        builder.cacheControl(cacheControl)
    }
    return builder.build()
}

private fun ToolDefinition.anthropicToolProperties(): Tool.InputSchema.Properties {
    val builder = Tool.InputSchema.Properties.builder()
    schema.properties.forEach { (key, value) ->
        builder.putAdditionalProperty(key, value.toAnthropicJsonValue())
    }
    schema.fullSchemaOverride?.get("definitions")?.let { defs ->
        if (defs is JsonObject) {
            builder.putAdditionalProperty("definitions", com.anthropic.core.JsonValue.from(defs.toWireMap()))
        }
    }
    schema.fullSchemaOverride?.get("\$defs")?.let { defs ->
        if (defs is JsonObject) {
            builder.putAdditionalProperty("\$defs", com.anthropic.core.JsonValue.from(defs.toWireMap()))
        }
    }
    return builder.build()
}
