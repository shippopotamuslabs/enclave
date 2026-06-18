package com.browntowndev.enclave.feature.inference.telemetry

import com.openai.models.chat.completions.ChatCompletionMessageParam
import java.security.MessageDigest

/**
 * Hashes the portions of an OpenAI-compatible request that influence implicit
 * provider prompt-cache reuse (DeepSeek, OpenRouter sticky routes, xAI, OpenAI).
 *
 * Comparing snapshots across turns explains why cache hits drop — modeled after
 * Reasonix [PrefixShape] / [CompareShape].
 */
internal object PromptCachePrefixDiagnostics {
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

    fun formatWireHead(messages: List<ChatCompletionMessageParam>, maxMessages: Int = 4): String =
        messages
            .take(maxMessages)
            .joinToString(" | ") { param ->
                when {
                    param.isSystem() -> "sys:${param.asSystem().content().asText().take(WIRE_HEAD_PREVIEW_CHARS)}"
                    param.isUser() -> "user:${param.asUser().content().asText().take(WIRE_HEAD_PREVIEW_CHARS)}"
                    param.isAssistant() -> "asst"
                    param.isTool() -> "tool"
                    else -> "other"
                }
            }

    private fun List<ChatCompletionMessageParam>.toWireFingerprint(): String =
        joinToString("\n") { param ->
            when {
                param.isSystem() -> "S:${param.asSystem().content().asText()}"
                param.isUser() -> "U:${param.asUser().content().asText()}"
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
                            val content = assistant.content().get()
                            if (content.isText()) content.asText() else "content_parts"
                        } else {
                            ""
                        }
                    "A:text=$text;tools=$toolCallsText"
                }
                param.isTool() -> "T:${param.asTool().content().asText()}#${param.asTool().toolCallId()}"
                else -> param.toString()
            }
        }

    private fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    private const val WIRE_HEAD_PREVIEW_CHARS = 40
}
