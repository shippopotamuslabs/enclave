package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition

/**
 * Generic tool envelope parser that supports both search and image-inspect tools.
 * Refactored from SearchToolSupport to handle multiple tool types.
 */
internal object ToolEnvelopeParser {
    private val TOOL_NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val QUERY_REGEX = Regex(""""query"\s*:\s*"([^"]*)"""")
    private val QUESTION_REGEX = Regex(""""question"\s*:\s*"([^"]*)"""")
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
        """{"query":"${escapeJson(query)}"}"""

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
        """{"question":"${escapeJson(question)}"}"""

    fun extractRequiredQuery(argumentsJson: String): String {
        val query = QUERY_REGEX.find(argumentsJson)?.groupValues?.get(1)?.trim()
        require(!query.isNullOrBlank()) {
            "Tool argument 'query' is required"
        }
        return query
    }

    fun extractRequiredQuestion(argumentsJson: String): String {
        val question = QUESTION_REGEX.find(argumentsJson)?.groupValues?.get(1)?.trim()
        require(!question.isNullOrBlank()) {
            "Tool argument 'question' is required"
        }
        return question
    }

    fun extractLocalToolEnvelope(text: String): LocalToolEnvelope? {
        val wrappedMatch = if (text.contains("<tool_call")) {
            WRAPPED_TOOL_CALL_REGEX.find(text)
                ?: throw IllegalStateException("Malformed tool_call envelope")
        } else {
            null
        }

        val payload = when {
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
            (systemPrompt.contains("<tool_call>") || systemPrompt.contains("{\"name\"")) &&
            (
                systemPrompt.contains(ToolDefinition.TAVILY_WEB_SEARCH.name) ||
                    systemPrompt.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT.name)
                )

    fun buildLocalToolResultMessage(resultJson: String): String =
        "<tool_result>$resultJson</tool_result>"

    fun stubResultJson(query: String): String =
        """{"query":"${escapeJson(query)}","results":[{"title":"Stub Tavily Result","url":"https://example.invalid/stub","content":"Pocket Crew stub result for query: ${escapeJson(query)}"}]}"""

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
}
