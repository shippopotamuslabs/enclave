package com.browntowndev.enclave.feature.inference.wire

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlockParam
import com.browntowndev.enclave.feature.inference.AnthropicPromptCacheSupport
import com.browntowndev.enclave.feature.inference.ImagePayloads

/**
 * Anthropic Messages wire mapper. Renders [AgentLoopRowKind]s into [MessageParam]s, extracting
 * the message-body logic previously inlined in
 * [com.browntowndev.enclave.feature.inference.AnthropicInferenceServiceImpl.completeSingleTurn].
 *
 * [useCacheControl] carries the per-row cache-breakpoint decision computed by the dispatcher from
 * `AnthropicPromptCacheSupport.shouldAttachMessageCacheBreakpoint(index, stableTailIndex)`. The
 * mapper attaches the ephemeral cache-control marker to the last content block of each row
 * (text block when there are no tool calls, last tool-use block otherwise), matching the prior
 * inline behavior.
 *
 * [toolInputProperties] parses a tool-call arguments JSON string into the Anthropic SDK's
 * `Map<String, JsonValue>` input shape; supplied by the service so the stream-decode path and the
 * replay path share one parser. Anthropic tolerates empty tool-call ids, so null/blank ids are
 * retained (unlike the OpenAI mapper).
 */
internal class AnthropicWireMessageMapper(
    private val toolInputProperties: (String) -> Map<String, JsonValue>,
) : ProviderWireMessageMapper<MessageParam> {
    private val cacheControl get() = AnthropicPromptCacheSupport.ephemeralCacheControl()

    override fun systemPrompt(content: String, useCacheControl: Boolean): MessageParam =
        // Anthropic maps the stable system prompt via builder.systemOfTextBlockParams in the
        // service, not through this per-row path. Transcript system rows are dropped here.
        error("Anthropic system prompt is set on the MessageCreateParams builder, not as a MessageParam row.")

    override fun userText(
        content: String,
        imageUris: List<String>,
        useCacheControl: Boolean,
    ): MessageParam =
        if (imageUris.isEmpty()) {
            userTextMessage(content, useCacheControl)
        } else {
            userImageMessage(content, imageUris, useCacheControl)
        }

    override fun engineInternal(wireText: String): MessageParam? = engineInternal(wireText, useCacheControl = false)

    /**
     * Renders an engine-internal row with an optional cache breakpoint on the text block.
     * Returns `null` for blank wire text (row dropped).
     */
    fun engineInternal(wireText: String, useCacheControl: Boolean): MessageParam? =
        if (wireText.isBlank()) {
            null
        } else {
            userTextMessage(wireText, cacheBreakpoint = useCacheControl)
        }

    override fun compactSummary(content: String, useCacheControl: Boolean): MessageParam =
        userTextMessage(formatCompactSummary(content), useCacheControl)

    override fun toolResult(
        toolCallId: String,
        content: String,
        isOrphan: Boolean,
        useCacheControl: Boolean,
    ): MessageParam {
        // Anthropic renders tool results as role: user with a tool_result block; orphan results
        // are still addressed to the (possibly empty) tool_use_id, matching the prior behavior.
        val toolResultBuilder = ToolResultBlockParam.builder().toolUseId(toolCallId).content(content)
        if (useCacheControl) {
            toolResultBuilder.cacheControl(cacheControl)
        }
        return MessageParam
            .builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(listOf(ContentBlockParam.ofToolResult(toolResultBuilder.build())))
            .build()
    }

    override fun assistantText(
        content: String,
        toolCalls: List<RawToolCallRef>,
        reasoningContent: String?,
        useCacheControl: Boolean,
    ): MessageParam? {
        val contentBlocks = mutableListOf<ContentBlockParam>()
        if (content.isNotBlank()) {
            val textBlockBuilder = TextBlockParam.builder().text(content)
            // Cache breakpoint attaches to the text block only when there are no tool-use blocks.
            val isTextLastBlock = useCacheControl && toolCalls.isEmpty()
            if (isTextLastBlock) {
                textBlockBuilder.cacheControl(cacheControl)
            }
            contentBlocks.add(ContentBlockParam.ofText(textBlockBuilder.build()))
        }
        toolCalls.forEachIndexed { tcIndex, ref ->
            val toolUseBuilder =
                ToolUseBlockParam
                    .builder()
                    .id(ref.id ?: "")
                    .name(ref.name)
                    .input(
                        ToolUseBlockParam.Input
                            .builder()
                            .putAllAdditionalProperties(toolInputProperties(ref.argumentsJson))
                            .build(),
                    )
            val isToolUseLastBlock = useCacheControl && (tcIndex == toolCalls.size - 1)
            if (isToolUseLastBlock) {
                toolUseBuilder.cacheControl(cacheControl)
            }
            contentBlocks.add(ContentBlockParam.ofToolUse(toolUseBuilder.build()))
        }
        if (contentBlocks.isEmpty()) return null
        return MessageParam
            .builder()
            .role(MessageParam.Role.ASSISTANT)
            .contentOfBlockParams(contentBlocks)
            .build()
    }

    private fun userTextMessage(text: String, cacheBreakpoint: Boolean): MessageParam {
        val textBlockBuilder = TextBlockParam.builder().text(text)
        if (cacheBreakpoint) {
            textBlockBuilder.cacheControl(cacheControl)
        }
        return MessageParam
            .builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(listOf(ContentBlockParam.ofText(textBlockBuilder.build())))
            .build()
    }

    private fun userImageMessage(
        content: String,
        imageUris: List<String>,
        cacheBreakpoint: Boolean,
    ): MessageParam {
        val contentBlocks = mutableListOf<ContentBlockParam>()
        ImagePayloads.fromUris(imageUris).forEach { payload ->
            val mediaType =
                when (payload.mimeType) {
                    "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG
                    "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF
                    "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP
                    else -> Base64ImageSource.MediaType.IMAGE_JPEG
                }
            contentBlocks.add(
                ContentBlockParam.ofImage(
                    ImageBlockParam
                        .builder()
                        .source(
                            Base64ImageSource
                                .builder()
                                .data(payload.base64)
                                .mediaType(mediaType)
                                .build(),
                        ).build(),
                ),
            )
        }
        val textBlockBuilder = TextBlockParam.builder().text(content)
        if (cacheBreakpoint) {
            textBlockBuilder.cacheControl(cacheControl)
        }
        contentBlocks.add(ContentBlockParam.ofText(textBlockBuilder.build()))
        return MessageParam
            .builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(contentBlocks)
            .build()
    }
}
