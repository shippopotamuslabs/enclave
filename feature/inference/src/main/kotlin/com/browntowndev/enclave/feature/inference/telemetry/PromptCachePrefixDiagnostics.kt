package com.browntowndev.enclave.feature.inference.telemetry

import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import java.security.MessageDigest

/**
 * Hashes the portions of an OpenAI-compatible request that influence implicit
 * provider prompt-cache reuse (DeepSeek, OpenRouter sticky routes, xAI, OpenAI).
 *
 * Comparing snapshots across turns explains why cache hits drop — modeled after
 * Reasonix [PrefixShape] / [CompareShape].
 */
internal object PromptCachePrefixDiagnostics {
    const val PREFIX_BREAK_WINDOW_CHARS = 120

    data class PrefixBreakReport(
        val matchChars: Int,
        val newChars: Int,
        val matchPct: Int,
        val divergenceIndex: Int,
        val contextBefore: String,
        val oldSnippet: String,
        val newSnippet: String,
    )

    data class PrefixShape(
        val systemHash: String,
        val toolsHash: String,
        val wireHash: String,
        val frozenSidecarCount: Int,
        val liveSidecarPresent: Boolean,
        val wireMessageCount: Int,
        val toolCount: Int,
    )

    data class Comparison(
        val prefixChanged: Boolean,
        val changeReasons: List<String>,
        val current: PrefixShape,
        val previousWireHash: String?,
        val inputTokenDelta: Int?,
    )

    fun captureShape(
        systemPrompt: String,
        toolNames: List<String>,
        messages: List<ChatCompletionMessageParam>,
        frozenSidecarCount: Int,
        liveSidecarPresent: Boolean,
    ): PrefixShape =
        PrefixShape(
            systemHash = shortHash(systemPrompt),
            toolsHash = shortHash(toolNames.sorted().joinToString(",")),
            wireHash = shortHash(messages.toWireFingerprint()),
            frozenSidecarCount = frozenSidecarCount,
            liveSidecarPresent = liveSidecarPresent,
            wireMessageCount = messages.size,
            toolCount = toolNames.size,
        )

    fun compare(
        previous: PrefixShape?,
        current: PrefixShape,
        previousInputTokens: Int?,
        currentInputTokens: Int?,
    ): Comparison {
        val reasons = mutableListOf<String>()
        if (previous != null) {
            if (previous.systemHash != current.systemHash) {
                reasons += "system"
            }
            if (previous.toolsHash != current.toolsHash) {
                reasons += "tools"
            }
            if (previous.wireHash != current.wireHash) {
                reasons += "wire"
            }
            if (previous.frozenSidecarCount != current.frozenSidecarCount) {
                reasons += "frozen_sidecar_count"
            }
            if (previous.liveSidecarPresent != current.liveSidecarPresent) {
                reasons += "live_sidecar"
            }
        }
        val inputDelta =
            if (previousInputTokens != null && currentInputTokens != null) {
                currentInputTokens - previousInputTokens
            } else {
                null
            }
        if (inputDelta != null && inputDelta < 0) {
            reasons += "input_shrunk"
        }
        return Comparison(
            prefixChanged = reasons.isNotEmpty(),
            changeReasons = reasons,
            current = current,
            previousWireHash = previous?.wireHash,
            inputTokenDelta = inputDelta,
        )
    }

    fun renderWireBody(
        systemPrompt: String,
        toolNames: List<String>,
        messages: List<ChatCompletionMessageParam>,
    ): String {
        val messageFingerprint = messages.toWireFingerprint()
        return buildString {
            append("TOOLS:")
            append(toolNames.joinToString(","))
            append('\n')
            append("S:")
            append(systemPrompt)
            if (messageFingerprint.isNotEmpty()) {
                append('\n')
                append(messageFingerprint)
            }
        }
    }

    fun computePrefixBreak(
        previousBody: String,
        newBody: String,
        windowChars: Int,
    ): PrefixBreakReport {
        var matchChars = 0
        val sharedLength = minOf(previousBody.length, newBody.length)
        while (matchChars < sharedLength && previousBody[matchChars] == newBody[matchChars]) {
            matchChars++
        }
        val divergenceIndex = matchChars
        val newChars = newBody.length
        val matchPct =
            if (newChars == 0) {
                100
            } else {
                (matchChars * 100) / newChars
            }
        val contextStart = (divergenceIndex - windowChars).coerceAtLeast(0)
        val contextBefore = previousBody.substring(contextStart, divergenceIndex)
        val halfWindow = windowChars / 2
        val oldStart = (divergenceIndex - halfWindow).coerceAtLeast(0)
        val oldEnd = (divergenceIndex + halfWindow).coerceAtMost(previousBody.length)
        val oldSnippet = previousBody.substring(oldStart, oldEnd)
        val newStart = (divergenceIndex - halfWindow).coerceAtLeast(0)
        val newEnd = (divergenceIndex + halfWindow).coerceAtMost(newBody.length)
        val newSnippet = newBody.substring(newStart, newEnd)
        return PrefixBreakReport(
            matchChars = matchChars,
            newChars = newChars,
            matchPct = matchPct,
            divergenceIndex = divergenceIndex,
            contextBefore = contextBefore,
            oldSnippet = oldSnippet,
            newSnippet = newSnippet,
        )
    }

    fun sanitizeForLog(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    fun formatWireHead(messages: List<ChatCompletionMessageParam>, maxMessages: Int = 4): String =
        messages
            .take(maxMessages)
            .joinToString(" | ") { param ->
                when {
                    param.isSystem() ->
                        "sys:${param.asSystem().content().wireContentText().take(WIRE_HEAD_PREVIEW_CHARS)}"
                    param.isUser() ->
                        "user:${param.asUser().content().wireContentText().take(WIRE_HEAD_PREVIEW_CHARS)}"
                    param.isAssistant() -> "asst"
                    param.isTool() -> "tool"
                    else -> "other"
                }
            }

    private fun List<ChatCompletionMessageParam>.toWireFingerprint(): String =
        joinToString("\n") { param ->
            when {
                param.isSystem() -> "S:${param.asSystem().content().wireContentText()}"
                param.isUser() -> "U:${param.asUser().content().wireContentText()}"
                param.isAssistant() -> {
                    val assistant = param.asAssistant()
                    val toolCallsText =
                        if (assistant.toolCalls().isPresent) {
                            assistant.toolCalls().get().joinToString { tc ->
                                "${tc.asFunction().id()}:${tc.asFunction().function().name()}:${tc.asFunction().function().arguments()}"
                            }
                        } else {
                            "no_tools"
                        }
                    val text =
                        if (assistant.content().isPresent) {
                            assistant.content().get().wireContentText()
                        } else {
                            ""
                        }
                    "A:text=$text;tools=$toolCallsText"
                }
                param.isTool() -> "T:${param.asTool().content().wireContentText()}#${param.asTool().toolCallId()}"
                else -> param.toString()
            }
        }

    private fun ChatCompletionSystemMessageParam.Content.wireContentText(): String =
        when {
            isText() -> asText()
            isArrayOfContentParts() -> asArrayOfContentParts().joinToString("|") { it.text() }
            else -> "content_parts"
        }

    private fun ChatCompletionUserMessageParam.Content.wireContentText(): String =
        when {
            isText() -> asText()
            isArrayOfContentParts() ->
                asArrayOfContentParts().joinToString("|") { part ->
                    if (part.isText()) part.asText().text() else "content_part"
                }
            else -> "content_parts"
        }

    private fun ChatCompletionAssistantMessageParam.Content.wireContentText(): String =
        when {
            isText() -> asText()
            isArrayOfContentParts() -> "content_parts"
            else -> "content_parts"
        }

    private fun ChatCompletionToolMessageParam.Content.wireContentText(): String =
        when {
            isText() -> asText()
            isArrayOfContentParts() -> asArrayOfContentParts().joinToString("|") { it.text() }
            else -> "content_parts"
        }

    private fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private const val WIRE_HEAD_PREVIEW_CHARS = 40
}
