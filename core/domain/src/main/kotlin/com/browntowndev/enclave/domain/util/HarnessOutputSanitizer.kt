package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.inference.AgentLoopWireKeys

/**
 * Strips harness-internal payloads that must never appear in user-visible assistant text.
 * Only excises matched infrastructure spans; never trims or collapses arbitrary whitespace.
 */
object HarnessOutputSanitizer {
    private val systemContextBlock =
        Regex(
            """${Regex.escape(AgentLoopWireKeys.SYSTEM_CONTEXT_PREFIX)}.*?</system_context>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    private const val LEGACY_AGENT_MARKER = "[Agent]"

    fun sanitize(text: String): String {
        var sanitized = text
        sanitized = systemContextBlock.replace(sanitized, "")
        sanitized = stripLegacyAgentBlocks(sanitized)
        sanitized = stripAgentJsonBlocks(sanitized)
        return sanitized
    }

    /**
     * Incremental sanitizer for streamed provider tokens. Holds back suffixes that may be the
     * start of an infrastructure marker split across chunks.
     */
    class StreamBuffer {
        private val pending = StringBuilder()

        fun append(chunk: String): String {
            if (chunk.isEmpty()) {
                return ""
            }
            pending.append(chunk)
            return drainEmitSafePrefix()
        }

        fun flush(): String {
            val remaining = pending.toString()
            pending.clear()
            return sanitize(remaining)
        }

        fun reset() {
            pending.clear()
        }

        private fun drainEmitSafePrefix(): String {
            val safeEnd = findSafeEmitEnd(pending.toString())
            if (safeEnd <= 0) {
                return ""
            }
            val toEmit = pending.substring(0, safeEnd)
            pending.delete(0, safeEnd)
            return sanitize(toEmit)
        }
    }

    private fun findSafeEmitEnd(text: String): Int {
        if (text.isEmpty()) {
            return 0
        }

        var safeEnd = text.length
        var incompleteJsonStart = -1

        val systemContextOpen = text.lastIndexOf(AgentLoopWireKeys.SYSTEM_CONTEXT_PREFIX)
        if (systemContextOpen >= 0 && text.indexOf("</system_context>", systemContextOpen) < 0) {
            safeEnd = minOf(safeEnd, systemContextOpen)
        }
        partialPrefixAtEnd(text, AgentLoopWireKeys.SYSTEM_CONTEXT_PREFIX).takeIf { it > 0 }?.let { partial ->
            safeEnd = minOf(safeEnd, text.length - partial)
        }
        partialPrefixAtEnd(text, "<system_context").takeIf { it > 0 }?.let { partial ->
            safeEnd = minOf(safeEnd, text.length - partial)
        }

        val agentMarkerIndex = text.lastIndexOf(LEGACY_AGENT_MARKER)
        if (agentMarkerIndex >= 0) {
            val afterMarker = skipWhitespace(text, agentMarkerIndex + LEGACY_AGENT_MARKER.length)
            when {
                afterMarker < text.length && text[afterMarker] == '{' -> {
                    if (findMatchingBraceEnd(text, afterMarker) < 0) {
                        safeEnd = minOf(safeEnd, agentMarkerIndex)
                    }
                }
                afterMarker == text.length -> safeEnd = minOf(safeEnd, agentMarkerIndex)
            }
        }
        partialPrefixAtEnd(text, LEGACY_AGENT_MARKER).takeIf { it > 0 }?.let { partial ->
            safeEnd = minOf(safeEnd, text.length - partial)
        }

        val agentKeyMarker = "\"${AgentLoopWireKeys.ENGINE_PAYLOAD_JSON_KEY}\""
        val agentKeyIndex = text.lastIndexOf(agentKeyMarker)
        if (agentKeyIndex >= 0) {
            val objectStart = text.lastIndexOf('{', agentKeyIndex)
            if (objectStart >= 0) {
                if (findMatchingBraceEnd(text, objectStart) < 0) {
                    incompleteJsonStart = objectStart
                    safeEnd = minOf(safeEnd, objectStart)
                }
            } else {
                safeEnd = minOf(safeEnd, agentKeyIndex)
            }
        } else {
            val openBraceIndex = text.lastIndexOf('{')
            if (openBraceIndex >= 0 && findMatchingBraceEnd(text, openBraceIndex) < 0) {
                incompleteJsonStart = openBraceIndex
                safeEnd = minOf(safeEnd, openBraceIndex)
            }
        }

        if (incompleteJsonStart < 0) {
            partialPrefixAtEnd(text, agentKeyMarker).takeIf { it > 0 }?.let { partial ->
                safeEnd = minOf(safeEnd, text.length - partial)
            }
            partialPrefixAtEnd(text, "{").takeIf { it > 0 }?.let { partial ->
                safeEnd = minOf(safeEnd, text.length - partial)
            }
        }

        return safeEnd
    }

    private fun partialPrefixAtEnd(
        text: String,
        marker: String,
    ): Int {
        for (length in marker.length - 1 downTo 1) {
            if (text.endsWith(marker.substring(0, length))) {
                return length
            }
        }
        return 0
    }

    private fun stripLegacyAgentBlocks(text: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < text.length) {
            val markerIndex = text.indexOf(LEGACY_AGENT_MARKER, index)
            if (markerIndex < 0) {
                builder.append(text.substring(index))
                return builder.toString()
            }
            builder.append(text.substring(index, markerIndex))
            val afterMarker = markerIndex + LEGACY_AGENT_MARKER.length
            val jsonStart = skipWhitespace(text, afterMarker)
            val objectEnd =
                if (jsonStart < text.length && text[jsonStart] == '{') {
                    findMatchingBraceEnd(text, jsonStart)
                } else {
                    -1
                }
            index =
                if (objectEnd >= 0) {
                    objectEnd + 1
                } else {
                    builder.append(LEGACY_AGENT_MARKER)
                    afterMarker
                }
        }
        return builder.toString()
    }

    private fun stripAgentJsonBlocks(text: String): String {
        val marker = "\"${AgentLoopWireKeys.ENGINE_PAYLOAD_JSON_KEY}\""
        val builder = StringBuilder()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf(marker, index)
            if (start < 0) {
                builder.append(text.substring(index))
                return builder.toString()
            }
            val objectStart = text.lastIndexOf('{', start)
            if (objectStart < index) {
                val nextIndex = start + marker.length
                builder.append(text.substring(index, nextIndex))
                index = nextIndex
            } else {
                val objectEnd = findMatchingBraceEnd(text, objectStart)
                if (objectEnd < 0) {
                    builder.append(text.substring(index))
                    return builder.toString()
                }
                builder.append(text.substring(index, objectStart))
                index = objectEnd + 1
            }
        }
        return builder.toString()
    }

    private fun skipWhitespace(
        text: String,
        startIndex: Int,
    ): Int {
        var cursor = startIndex
        while (cursor < text.length && text[cursor].isWhitespace()) {
            cursor++
        }
        return cursor
    }

    private fun findMatchingBraceEnd(
        text: String,
        openIndex: Int,
    ): Int {
        if (openIndex < 0 || openIndex >= text.length || text[openIndex] != '{') {
            return -1
        }
        var depth = 0
        var inString = false
        var escaped = false
        for (index in openIndex until text.length) {
            val char = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return index
                        }
                    }
                }
            }
        }
        return -1
    }
}
