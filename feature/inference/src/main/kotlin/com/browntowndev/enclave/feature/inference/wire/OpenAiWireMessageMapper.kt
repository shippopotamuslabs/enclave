package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.feature.inference.OpenRouterPromptCacheSupport
import com.browntowndev.enclave.feature.inference.ImagePayloads
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam

/**
 * OpenAI Chat Completions wire mapper. Renders [AgentLoopRowKind]s into
 * [ChatCompletionMessageParam]s, extracting the message-body logic previously inlined in
 * [com.browntowndev.enclave.feature.inference.BaseOpenAiSdkInferenceService].
 *
 * Assistant replay is parameterized by [assistantPolicy] so the base class's
 * `normalizeAssistantContentForReplay` / `applyAssistantContent` /
 * `shouldAttachProviderReasoningContent` hooks — and their DeepSeek/OpenRouter overrides —
 * continue to flow through without the mapper knowing about subclasses.
 *
 * Tool-call ids that are blank are dropped with a warning, matching the prior base behavior.
 * Orphan tool results are rewritten as user messages, also matching the prior base behavior.
 */
internal class OpenAiWireMessageMapper(
    private val tag: String,
    private val modelId: String,
    private val loggingPort: LoggingPort,
    private val assistantPolicy: OpenAiAssistantReplayPolicy,
) : ProviderWireMessageMapper<ChatCompletionMessageParam> {

    /** Result of rendering an assistant row, carrying the kept tool-call ids for pending tracking. */
    data class AssistantRender(
        val param: ChatCompletionMessageParam,
        val toolCallIds: List<String>,
    )

    /** Renders an assistant row and reports the kept tool-call ids (non-blank only). */
    @Suppress("UnusedParameter") // useCacheControl is not applied to assistant rows on OpenAI.
    fun assistantText(
        content: String,
        toolCalls: List<RawToolCallRef>,
        reasoningContent: String?,
        useCacheControl: Boolean,
        requestModelId: String,
        options: GenerationOptions,
        messageId: String,
    ): AssistantRender? {
        val assistantBuilder = ChatCompletionAssistantMessageParam.builder()
        val hasToolCalls = toolCalls.isNotEmpty()
        val normalizedContent = assistantPolicy.normalizeContent(
            requestModelId = requestModelId,
            content = content,
            options = options,
            hasToolCalls = hasToolCalls,
        )
        assistantPolicy.applyContent(assistantBuilder, normalizedContent)
        if (assistantPolicy.shouldAttachReasoning(requestModelId, options, reasoningContent)) {
            reasoningContent?.let {
                assistantBuilder.putAdditionalProperty("reasoning_content", JsonValue.from(it))
            }
        }
        val keptToolCallIds = mutableListOf<String>()
        toolCalls.forEach { ref ->
            val rawCallId = ref.id
            if (rawCallId.isNullOrBlank()) {
                loggingPort.warning(
                    tag,
                    "Dropping assistant tool call without provider id model=$modelId messageId=$messageId tool=${ref.name}",
                )
                return@forEach
            }
            assistantBuilder.addToolCall(
                ChatCompletionMessageFunctionToolCall
                    .builder()
                    .id(rawCallId)
                    .function(
                        ChatCompletionMessageFunctionToolCall.Function
                            .builder()
                            .name(ref.name)
                            .arguments(ref.argumentsJson)
                            .build(),
                    ).build(),
            )
            keptToolCallIds.add(rawCallId)
        }
        if (normalizedContent.isBlank() && keptToolCallIds.isEmpty()) {
            loggingPort.warning(tag, "Skipping empty assistant chat message model=$modelId messageId=$messageId")
            return null
        }
        return AssistantRender(
            param = ChatCompletionMessageParam.ofAssistant(assistantBuilder.build()),
            toolCallIds = keptToolCallIds,
        )
    }

    override fun systemPrompt(content: String, useCacheControl: Boolean): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofSystem(buildSystemPromptMessage(content, useCacheControl))

    override fun userText(
        content: String,
        imageUris: List<String>,
        useCacheControl: Boolean,
    ): ChatCompletionMessageParam =
        if (imageUris.isEmpty()) {
            ChatCompletionMessageParam.ofUser(buildUserMessage(content, useCacheControl))
        } else {
            ChatCompletionMessageParam.ofUser(buildUserMessageWithImages(content, imageUris))
        }

    override fun engineInternal(wireText: String): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(wireText).build(),
        )

    override fun compactSummary(content: String, useCacheControl: Boolean): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam.builder().content(formatCompactSummary(content)).build(),
        )

    override fun toolResult(
        toolCallId: String,
        content: String,
        isOrphan: Boolean,
        useCacheControl: Boolean,
    ): ChatCompletionMessageParam {
        if (!isOrphan) {
            return ChatCompletionMessageParam.ofTool(
                ChatCompletionToolMessageParam
                    .builder()
                    .content(content.ifBlank { "{}" })
                    .toolCallId(toolCallId)
                    .build(),
            )
        }
        loggingPort.warning(
            tag,
            "Converting orphan tool result to user message model=$modelId callId=$toolCallId",
        )
        return ChatCompletionMessageParam.ofUser(
            ChatCompletionUserMessageParam
                .builder()
                .content("[Tool result]\n${content.ifBlank { "{}" }}")
                .build(),
        )
    }

    @Suppress("UnusedParameter") // Interface contract; OpenAI uses the 6-arg overload instead.
    override fun assistantText(
        content: String,
        toolCalls: List<RawToolCallRef>,
        reasoningContent: String?,
        useCacheControl: Boolean,
    ): ChatCompletionMessageParam? = error(
        "Use the 6-argument assistantText overload with requestModelId/options/messageId; " +
            "the interface override is not used for OpenAI Chat Completions.",
    )

    private fun buildSystemPromptMessage(
        content: String,
        useCacheControl: Boolean,
    ): ChatCompletionSystemMessageParam =
        if (useCacheControl) {
            ChatCompletionSystemMessageParam
                .builder()
                .contentOfArrayOfContentParts(
                    listOf(OpenRouterPromptCacheSupport.cachedSystemTextPart(content)),
                ).build()
        } else {
            ChatCompletionSystemMessageParam.builder().content(content).build()
        }

    private fun buildUserMessage(
        content: String,
        useCacheControl: Boolean,
    ): ChatCompletionUserMessageParam =
        if (useCacheControl) {
            ChatCompletionUserMessageParam
                .builder()
                .contentOfArrayOfContentParts(
                    listOf(OpenRouterPromptCacheSupport.cachedUserTextPart(content)),
                ).build()
        } else {
            ChatCompletionUserMessageParam.builder().content(content).build()
        }

    private fun buildUserMessageWithImages(
        content: String,
        imageUris: List<String>,
    ): ChatCompletionUserMessageParam {
        val parts =
            buildList {
                add(
                    ChatCompletionContentPart.ofText(
                        ChatCompletionContentPartText.builder().text(content).build(),
                    ),
                )
                ImagePayloads.fromUris(imageUris).forEach { payload ->
                    ImagePayloads.validate(payload)
                    add(
                        ChatCompletionContentPart.ofImageUrl(
                            ChatCompletionContentPartImage
                                .builder()
                                .imageUrl(
                                    ChatCompletionContentPartImage.ImageUrl
                                        .builder()
                                        .url(payload.dataUrl)
                                        .build(),
                                ).build(),
                        ),
                    )
                }
            }
        return ChatCompletionUserMessageParam.builder().contentOfArrayOfContentParts(parts).build()
    }
}

/**
 * Strategy hooks for OpenAI assistant replay, sourced from the base class's `protected open`
 * methods so subclass overrides (DeepSeek reasoning stripping, base content normalization)
 * continue to apply.
 */
internal interface OpenAiAssistantReplayPolicy {
    fun normalizeContent(
        requestModelId: String,
        content: String,
        options: GenerationOptions,
        hasToolCalls: Boolean,
    ): String

    fun applyContent(
        builder: ChatCompletionAssistantMessageParam.Builder,
        normalizedContent: String,
    )

    fun shouldAttachReasoning(
        requestModelId: String,
        options: GenerationOptions,
        reasoningContent: String?,
    ): Boolean
}
