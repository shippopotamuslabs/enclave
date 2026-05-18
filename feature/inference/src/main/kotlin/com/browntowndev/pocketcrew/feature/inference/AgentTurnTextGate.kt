package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.AgentRawToolCall
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser

internal class AgentTurnTextGate(
    private val allowToolCall: Boolean,
) {
    private val text = StringBuilder()
    private var emittedLength = 0

    suspend fun onTextToken(
        chunk: String,
        emit: suspend (String) -> Unit,
    ) {
        text.append(chunk)
        if (!allowToolCall) {
            emittedLength = text.length
            emit(chunk)
            return
        }

        emitSafeTextPrefix(emit)
    }

    suspend fun visibleAssistantText(
        nativeToolCalls: List<AgentRawToolCall>,
        emit: suspend (String) -> Unit,
    ): String? {
        val buffered = text.toString()
        if (buffered.isBlank()) {
            return null
        }
        if (!allowToolCall) {
            return buffered
        }
        if (nativeToolCalls.isNotEmpty()) {
            return null
        }
        val leakedToolCall = parseLeakedToolCall(buffered)
        if (leakedToolCall != null) {
            return null
        }
        emitRemainingText(buffered, emit)
        return buffered
    }

    fun leakedToolCalls(nativeToolCalls: List<AgentRawToolCall>): List<AgentRawToolCall> {
        if (!allowToolCall || nativeToolCalls.isNotEmpty()) {
            return emptyList()
        }
        val envelope = parseLeakedToolCall(text.toString()) ?: return emptyList()
        return listOf(
            AgentRawToolCall(
                id = "text-tool-${System.nanoTime()}",
                name = envelope.toolName,
                argumentsJson = envelope.argumentsJson,
            ),
        )
    }

    private suspend fun emitSafeTextPrefix(emit: suspend (String) -> Unit) {
        val buffered = text.toString()
        val toolMarkerStart = firstToolMarkerStart(buffered)
        val safeEnd = toolMarkerStart ?: (buffered.length - TOOL_MARKER_HOLDBACK_CHARS).coerceAtLeast(0)
        if (safeEnd > emittedLength) {
            emit(buffered.substring(emittedLength, safeEnd))
            emittedLength = safeEnd
        }
    }

    private suspend fun emitRemainingText(
        buffered: String,
        emit: suspend (String) -> Unit,
    ) {
        if (buffered.length > emittedLength) {
            emit(buffered.substring(emittedLength))
            emittedLength = buffered.length
        }
    }

    private fun parseLeakedToolCall(text: String): ToolEnvelopeParser.LocalToolEnvelope? =
        runCatching { ToolEnvelopeParser.extractLocalToolEnvelope(text) }.getOrNull()

    private fun firstToolMarkerStart(text: String): Int? =
        TOOL_MARKER_REGEXES
            .mapNotNull { regex -> regex.find(text)?.range?.first }
            .minOrNull()

    private companion object {
        private const val TOOL_MARKER_HOLDBACK_CHARS = 64

        private val TOOL_MARKER_REGEXES = listOf(
            Regex("""(?is)<\s*(?:[|｜]+\s*)?DSML\s*(?:[|｜]+\s*)?tool_calls"""),
            Regex("""(?is)<\s*(?:[|｜]+\s*)?DSML\s*(?:[|｜]+\s*)?invoke\s+name\s*="""),
            Regex("""(?is)<\s*tool_call\b"""),
            Regex("""(?is)<!\[CDATA\[<tool"""),
            Regex("""(?is)\[TOOL_CALLS]"""),
            Regex("""(?is)\{\s*["']name["']\s*:"""),
        )
    }
}
