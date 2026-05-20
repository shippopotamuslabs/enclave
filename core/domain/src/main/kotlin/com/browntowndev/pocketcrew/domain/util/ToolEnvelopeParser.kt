package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.inference.AttachedImageInspectParams
import com.browntowndev.pocketcrew.domain.model.inference.ExtractDepth
import com.browntowndev.pocketcrew.domain.model.inference.ExtractFormat
import com.browntowndev.pocketcrew.domain.model.inference.GetMessageContextParams
import com.browntowndev.pocketcrew.domain.model.inference.SearchChatParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeFailure
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeFailureReason
import com.browntowndev.pocketcrew.domain.model.inference.WebExtractParams
import com.browntowndev.pocketcrew.domain.model.inference.WebSearchParams
import com.browntowndev.pocketcrew.domain.port.inference.ToolEnvelopeParserPort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Thin domain facade for the canonical Rust-backed tool envelope parser.
 */
@Suppress("TooManyFunctions")
object ToolEnvelopeParser {
    @Suppress("MemberVisibilityCanBePrivate")
    var portProvider: (() -> ToolEnvelopeParserPort)? = null

    data class LocalToolEnvelope(
        val toolName: String,
        val argumentsJson: String,
        val visiblePrefix: String,
        val visibleSuffix: String,
    )

    fun installPort(port: ToolEnvelopeParserPort) {
        portProvider = { port }
    }

    fun requireSupportedTool(toolName: String) {
        require(ToolDefinition.fromName(toolName) != null) {
            "Unsupported tool: $toolName"
        }
    }

    fun buildArgumentsJson(query: String): String = ToolDefinition.WEB_SEARCH.encodeArguments(WebSearchParams(query))

    fun buildArgumentsJson(
        toolName: String,
        arguments: Map<String, *>,
    ): String {
        val jsonObject = buildJsonObjectFromMap(arguments)
        return requirePort()
            .coerceProviderArguments(toolName, jsonObject.toString())
            .argumentsJson
    }

    @Deprecated(
        "Use buildArgumentsJson(toolName, arguments) for robust parsing",
        ReplaceWith("buildArgumentsJson(toolName, arguments)"),
    )
    fun buildArgumentsJson(arguments: Map<String, *>): String {
        val query =
            (arguments["query"] as? String)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        if (query != null) {
            return buildArgumentsJson(query)
        }

        val question =
            (arguments["question"] as? String)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        if (question != null) {
            return buildImageInspectArgumentsJson(question)
        }

        val urls =
            (arguments["urls"] as? List<*>)
                ?.filterIsInstance<String>()
                ?.takeIf(List<String>::isNotEmpty)
        if (urls != null) {
            val extractDepth = arguments["extract_depth"] as? String
            val format = arguments["format"] as? String
            return buildExtractArgumentsJson(urls, extractDepth, format)
        }

        return buildJsonObjectFromMap(arguments).toString()
    }

    fun buildImageInspectArgumentsJson(question: String): String =
        ToolDefinition.ATTACHED_IMAGE_INSPECT.encodeArguments(AttachedImageInspectParams(question))

    fun buildExtractArgumentsJson(
        urls: List<String>,
        extractDepth: String? = null,
        format: String? = null,
    ): String =
        ToolDefinition.WEB_EXTRACT.encodeArguments(
            WebExtractParams(
                urls = urls,
                extract_depth = extractDepth?.let { ExtractDepth.valueOf(it) } ?: ExtractDepth.basic,
                format = format?.let { ExtractFormat.valueOf(it) } ?: ExtractFormat.markdown,
            ),
        )

    fun extractRequiredQuery(argumentsJson: String): String = extractRequiredString(argumentsJson, "query")

    fun extractRequiredQuestion(argumentsJson: String): String = extractRequiredString(argumentsJson, "question")

    fun extractRequiredChatId(argumentsJson: String): String = extractRequiredString(argumentsJson, "chat_id")

    fun buildSearchChatArgumentsJson(
        chatId: String,
        query: String,
    ): String = ToolDefinition.SEARCH_CHAT.encodeArguments(SearchChatParams(chatId, query))

    fun buildGetMessageContextArgumentsJson(
        messageId: String,
        before: Int? = null,
        after: Int? = null,
    ): String =
        ToolDefinition.GET_MESSAGE_CONTEXT.encodeArguments(
            GetMessageContextParams(
                message_id = messageId,
                before = before ?: 5,
                after = after ?: 5,
            ),
        )

    fun extractRequiredMessageId(argumentsJson: String): String = extractRequiredString(argumentsJson, "message_id")

    @Suppress("ThrowsCount")
    fun extractRequiredUrls(argumentsJson: String): List<String> {
        parseUrls(argumentsJson)?.let { return it }

        val port =
            portProvider?.invoke()
                ?: throw IllegalArgumentException("Tool argument 'urls' is required and must be a non-empty array")
        val batch = port.parse("""<tool_call>{"name":"web_extract","arguments":$argumentsJson}</tool_call>""")
        batch.failures.firstOrNull()?.let { throw it.toException() }
        val repairedArguments = batch.successes.firstOrNull()?.argumentsJson
        if (repairedArguments != null) {
            parseUrls(repairedArguments)?.let { return it }
        }

        throw IllegalArgumentException("Tool argument 'urls' is required and must be a non-empty array")
    }

    fun extractLocalToolEnvelopes(text: String): List<LocalToolEnvelope> = envelopesFromPortBatch(requirePort().parse(text))

    fun extractLocalToolEnvelope(text: String): LocalToolEnvelope? = extractLocalToolEnvelopes(text).firstOrNull()

    fun hasLocalToolContract(systemPrompt: String?): Boolean = requirePort().hasLocalToolContract(systemPrompt)

    fun buildLocalToolResultMessage(resultJson: String): String = requirePort().buildToolResultMessage(resultJson)

    @Suppress("MaxLineLength")
    fun stubResultJson(query: String): String =
        """{"query":"${escapeJson(
            query,
        )}","results":[{"title":"Stub Tavily Result","url":"https://example.invalid/stub","content":"${escapeJson(
            "Pocket Crew stub result for query: $query",
        )}"}]}"""

    private fun envelopesFromPortBatch(batch: ToolEnvelopeBatch): List<LocalToolEnvelope> {
        batch.failures.firstOrNull()?.let { failure ->
            throw failure.toException()
        }
        return batch.successes.map { field ->
            LocalToolEnvelope(
                toolName = field.toolName,
                argumentsJson = field.argumentsJson,
                visiblePrefix = batch.visiblePrefix,
                visibleSuffix = batch.visibleSuffix,
            )
        }
    }

    private fun ToolEnvelopeFailure.toException(): RuntimeException =
        when (val failureReason = reason) {
            ToolEnvelopeFailureReason.MalformedEnvelope ->
                IllegalStateException(
                    if (formatHint == "dsml") {
                        "Malformed DSML tool envelope"
                    } else {
                        "Malformed tool_call envelope"
                    },
                )
            is ToolEnvelopeFailureReason.InvalidJson ->
                IllegalStateException("Malformed tool_call envelope")
            is ToolEnvelopeFailureReason.UnknownTool ->
                IllegalArgumentException("Unsupported tool: ${failureReason.name}")
            is ToolEnvelopeFailureReason.UnknownParameter ->
                IllegalArgumentException("Unknown parameter '${failureReason.param}' for tool '${failureReason.tool}'")
            is ToolEnvelopeFailureReason.DuplicateParameter ->
                IllegalArgumentException("Duplicate parameter '${failureReason.param}' for tool '${failureReason.tool}'")
            is ToolEnvelopeFailureReason.MissingRequiredParameter ->
                IllegalArgumentException(missingRequiredMessage(failureReason.tool, failureReason.param))
            is ToolEnvelopeFailureReason.SchemaCoercionFailed ->
                IllegalArgumentException("Tool argument '${failureReason.param}' is required")
        }

    private fun missingRequiredMessage(
        tool: String,
        param: String,
    ): String =
        if (tool == ToolDefinition.WEB_EXTRACT.name && param == "urls") {
            "Tool argument 'urls' is required and must be a non-empty array"
        } else {
            "Tool argument '$param' is required"
        }

    private fun buildJsonObjectFromMap(map: Map<String, *>): JsonObject =
        buildJsonObject {
            map.forEach { (key, value) ->
                put(key, toJsonElement(value))
            }
        }

    private fun toJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> ->
                @Suppress("UNCHECKED_CAST")
                (buildJsonObjectFromMap(value as Map<String, *>))
            is List<*> ->
                buildJsonArray {
                    value.forEach { add(toJsonElement(it)) }
                }
            else -> JsonPrimitive(value.toString())
        }

    private fun parseUrls(argumentsJson: String): List<String>? =
        runCatching {
            val json = Json.parseToJsonElement(argumentsJson).jsonObject
            val urlsValue = (
                json["urls"]
                    ?: json["arguments"]?.jsonObject?.get("urls")
                    ?: json["parameters"]?.jsonObject?.get("urls")
            )
            urlsValue?.let(::normalizeUrlValue)
        }.getOrNull()
            ?.takeIf(List<String>::isNotEmpty)

    private fun normalizeUrlValue(value: JsonElement): List<String>? =
        when (value) {
            is JsonArray ->
                value.mapNotNull { item ->
                    item.jsonPrimitive.content
                        .trim()
                        .trimStart('[')
                        .takeIf(String::isNotEmpty)
                }
            is JsonPrimitive -> {
                val trimmed = value.content.trim()
                when {
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") -> listOf(trimmed.trimStart('['))
                    else -> null
                }
            }
            else -> null
        }

    private fun extractRequiredString(
        argumentsJson: String,
        fieldName: String,
    ): String {
        try {
            val json = Json.parseToJsonElement(argumentsJson).jsonObject
            val value = json[fieldName]?.jsonPrimitive?.content
            if (!value.isNullOrBlank()) {
                return normalizeTypographyQuotesInExtractedField(value.trim())
            }
        } catch (_: Exception) {
            // Preserve source-compatible argument extraction for partially streamed JSON values.
        }

        return jsonStringFieldRegex(fieldName)
            .find(argumentsJson)
            ?.groupValues
            ?.get(1)
            ?.let(::unescapeJson)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::normalizeTypographyQuotesInExtractedField)
            ?: throw IllegalArgumentException("Tool argument '$fieldName' is required")
    }

    private fun jsonStringFieldRegex(fieldName: String): Regex = Regex(""""$fieldName"\s*:\s*"((?:\\.|[^"\\])*)"?""")

    private fun normalizeTypographyQuotesInExtractedField(value: String): String = value.replace('“', '"').replace('”', '"')

    private fun requirePort(): ToolEnvelopeParserPort =
        portProvider?.invoke()
            ?: throw IllegalStateException("ToolEnvelopeParserPort is not installed")

    private fun escapeJson(value: String): String =
        buildString(value.length) {
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

    @Suppress("LoopWithTooManyJumpStatements")
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
