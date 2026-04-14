package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition

/**
 * Generic tool envelope parser that supports both search and image-inspect tools.
 * Refactored from SearchToolSupport to handle multiple tool types.
 */
object ToolEnvelopeParser {
    private val TOOL_NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val CDATA_TOOL_CALL_REGEX = Regex("""(?s)<!\[CDATA\[<tool>\s*(\{.*?\})\s*</tool>\]\]>""")
    private val WRAPPED_TOOL_CALL_REGEX = Regex("""(?s)<tool_call>\s*(\{.*?\})\s*</tool_call>""")
    private val RAW_TOOL_CALL_REGEX = Regex("""(?s)(.*?)(?:,\s*)?(\{"name"\s*:\s*"[^"]+"\s*,\s*"arguments"\s*:\s*\{.*?\}\})(?:\s*,)?(.*)""")

    data class LocalToolEnvelope(
        val toolName: String,
        val argumentsJson: String,
        val visiblePrefix: String,
        val visibleSuffix: String,
    )

    fun requireSupportedTool(toolName: String) {
        require(toolName == ToolDefinition.TAVILY_WEB_SEARCH.name || toolName == ToolDefinition.ATTACHED_IMAGE_INSPECT.name) {
            "Unsupported tool: $toolName"
        }
    }

    fun buildArgumentsJson(query: String): String =
        buildSingleFieldArgumentsJson("query", query)

    fun buildArgumentsJson(arguments: Map<String, *>): String {
        val query = (arguments["query"] as? String)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (query != null) {
            return buildArgumentsJson(query)
        }

        val question = (arguments["question"] as? String)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (question != null) {
            return buildImageInspectArgumentsJson(question)
        }

        throw IllegalArgumentException("Tool argument 'query' or 'question' is required")
    }

    fun buildImageInspectArgumentsJson(question: String): String =
        buildSingleFieldArgumentsJson("question", question)

    fun extractRequiredQuery(argumentsJson: String): String {
        return extractRequiredString(argumentsJson, "query")
    }

    fun extractRequiredQuestion(argumentsJson: String): String {
        return extractRequiredString(argumentsJson, "question")
    }

    fun extractLocalToolEnvelope(text: String): LocalToolEnvelope? {
        val cdataMatch = if (text.contains("<![CDATA[<tool")) {
            CDATA_TOOL_CALL_REGEX.find(text)
                ?: throw IllegalStateException("Malformed tool_call envelope")
        } else {
            null
        }

        val wrappedMatch = if (text.contains("<tool_call")) {
            WRAPPED_TOOL_CALL_REGEX.find(text)
                ?: throw IllegalStateException("Malformed tool_call envelope")
        } else {
            null
        }

        val payload = when {
            cdataMatch != null -> cdataMatch.groupValues[1]
            wrappedMatch != null -> wrappedMatch.groupValues[1]
            else -> RAW_TOOL_CALL_REGEX.matchEntire(text)?.groupValues?.get(2)
        } ?: run {
            if (text.contains("{\"name\"") || text.contains(""""name":""")) {
                throw IllegalStateException("Malformed tool_call envelope")
            }
            return null
        }
        val toolName = TOOL_NAME_REGEX.find(payload)?.groupValues?.get(1)
            ?: throw IllegalStateException("Malformed tool_call envelope")
        val argumentsJson = when (toolName) {
            ToolDefinition.TAVILY_WEB_SEARCH.name -> buildArgumentsJson(extractRequiredQuery(payload))
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name -> buildImageInspectArgumentsJson(extractRequiredQuestion(payload))
            else -> throw IllegalArgumentException("Unsupported tool: $toolName")
        }

        if (cdataMatch != null) {
            return LocalToolEnvelope(
                toolName = toolName,
                argumentsJson = argumentsJson,
                visiblePrefix = text.substring(0, cdataMatch.range.first),
                visibleSuffix = text.substring(cdataMatch.range.last + 1),
            )
        }

        if (wrappedMatch != null) {
            return LocalToolEnvelope(
                toolName = toolName,
                argumentsJson = argumentsJson,
                visiblePrefix = text.substring(0, wrappedMatch.range.first),
                visibleSuffix = text.substring(wrappedMatch.range.last + 1),
            )
        }

        val rawMatch = RAW_TOOL_CALL_REGEX.matchEntire(text)
            ?: throw IllegalStateException("Malformed tool_call envelope")
        return LocalToolEnvelope(
            toolName = toolName,
            argumentsJson = argumentsJson,
            visiblePrefix = rawMatch.groupValues[1].trimEnd(',', ' '),
            visibleSuffix = rawMatch.groupValues[3].trimStart(',', ' '),
        )
    }

    fun hasLocalToolContract(systemPrompt: String?): Boolean =
        systemPrompt != null &&
            (systemPrompt.contains("<tool_call>") || systemPrompt.contains("{\"name\"") || systemPrompt.contains("<![CDATA[<tool")) &&
            (
                systemPrompt.contains(ToolDefinition.TAVILY_WEB_SEARCH.name) ||
                    systemPrompt.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT.name)
                )

    fun buildLocalToolResultMessage(resultJson: String): String =
        "<tool_result>$resultJson</tool_result>"

    fun stubResultJson(query: String): String =
        """{"query":"${escapeJson(query)}","results":[{"title":"Stub Tavily Result","url":"https://example.invalid/stub","content":"${escapeJson("Pocket Crew stub result for query: $query")}"}]}"""

    private fun extractRequiredString(argumentsJson: String, fieldName: String): String {
        try {
            val json = org.json.JSONObject(argumentsJson)
            val value = json.optString(fieldName, "")
            if (value.isNotBlank()) {
                return value.trim()
            }
        } catch (e: Exception) {
            // Fallback to regex if JSON parsing fails (common in partial local model streams)
        }

        return jsonStringFieldRegex(fieldName)
            .find(argumentsJson)
            ?.groupValues
            ?.get(1)
            ?.let(::unescapeJson)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Tool argument '$fieldName' is required")
    }

    private fun buildSingleFieldArgumentsJson(fieldName: String, value: String): String =
        """{"$fieldName":"${escapeJson(value)}"}"""

    private fun jsonStringFieldRegex(fieldName: String): Regex =
        // Final quote is optional to support partial streams
        Regex(""""$fieldName"\s*:\s*"((?:\\.|[^"\\])*)"?""")

    private fun escapeJson(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private fun unescapeJson(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.lastIndex) {
                result.append(char)
                index += 1
                continue
            }

            when (val escaped = value[index + 1]) {
                '\\' -> result.append('\\')
                '"' -> result.append('"')
                '/' -> result.append('/')
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    val hexStart = index + 2
                    val hexEnd = hexStart + 4
                    require(hexEnd <= value.length) {
                        "Invalid unicode escape sequence"
                    }
                    val codePoint = value.substring(hexStart, hexEnd).toInt(16)
                    result.append(codePoint.toChar())
                    index = hexEnd
                    continue
                }
                else -> result.append(escaped)
            }
            index += 2
        }
        return result.toString()
    }
}
