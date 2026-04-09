package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition

internal object SearchToolSupport {
    private val TOOL_NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val QUERY_REGEX = Regex(""""query"\s*:\s*"([^"]*)"""")
    private val TOOL_CALL_REGEX = Regex("""<tool_call>(.*?)</tool_call>""", setOf(RegexOption.DOT_MATCHES_ALL))

    data class LocalToolEnvelope(
        val toolName: String,
        val argumentsJson: String,
        val visiblePrefix: String,
        val visibleSuffix: String,
    )

    fun requireSupportedTool(toolName: String) {
        require(toolName == ToolDefinition.TAVILY_WEB_SEARCH.name) {
            "Unsupported tool: $toolName"
        }
    }

    fun buildArgumentsJson(query: String): String =
        """{"query":"${escapeJson(query)}"}"""

    fun extractRequiredQuery(argumentsJson: String): String {
        val query = QUERY_REGEX.find(argumentsJson)?.groupValues?.get(1)?.trim()
        require(!query.isNullOrBlank()) {
            "Tool argument 'query' is required"
        }
        return query
    }

    fun buildArgumentsJson(arguments: Map<String, *>): String {
        val query = (arguments["query"] as? String)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Tool argument 'query' is required")
        return buildArgumentsJson(query)
    }

    fun extractLocalToolEnvelope(text: String): LocalToolEnvelope? {
        if (!text.contains("<tool_call>")) {
            return null
        }

        val match = TOOL_CALL_REGEX.find(text)
            ?: throw IllegalStateException("Malformed tool_call envelope")
        val payload = match.groupValues[1]
        val toolName = TOOL_NAME_REGEX.find(payload)?.groupValues?.get(1)
            ?: throw IllegalStateException("Malformed tool_call envelope")

        return LocalToolEnvelope(
            toolName = toolName,
            argumentsJson = buildArgumentsJson(extractRequiredQuery(payload)),
            visiblePrefix = text.substring(0, match.range.first),
            visibleSuffix = text.substring(match.range.last + 1),
        )
    }

    fun hasLocalToolContract(systemPrompt: String?): Boolean =
        systemPrompt?.contains("<tool_call>") == true &&
            systemPrompt.contains(ToolDefinition.TAVILY_WEB_SEARCH.name)

    fun buildLocalToolResultMessage(resultJson: String): String =
        "<tool_result>$resultJson</tool_result>"

    fun stubResultJson(query: String): String =
        """{"query":"${escapeJson(query)}","results":[{"title":"Stub Tavily Result","url":"https://example.invalid/stub","content":"Pocket Crew stub result for query: ${escapeJson(query)}"}]}"""

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
}
