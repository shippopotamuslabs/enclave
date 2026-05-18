package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.inference.AttachedImageInspectParams
import com.browntowndev.pocketcrew.domain.model.inference.ExtractDepth
import com.browntowndev.pocketcrew.domain.model.inference.ExtractFormat
import com.browntowndev.pocketcrew.domain.model.inference.GetMessageContextParams
import com.browntowndev.pocketcrew.domain.model.inference.SearchChatHistoryParams
import com.browntowndev.pocketcrew.domain.model.inference.SearchChatParams
import com.browntowndev.pocketcrew.domain.model.inference.WebExtractParams
import com.browntowndev.pocketcrew.domain.model.inference.WebSearchParams
import com.browntowndev.pocketcrew.domain.model.inference.XSearchParams
import com.browntowndev.pocketcrew.domain.model.inference.GenerateImageToolParams
import com.browntowndev.pocketcrew.domain.model.inference.GenerateVideoToolParams
import com.browntowndev.pocketcrew.domain.model.inference.GenerateMusicToolParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolParameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Generic tool envelope parser that supports both search and image-inspect tools.
 * Refactored from SearchToolSupport to handle multiple tool types.
 */
@Suppress("LargeClass", "TooManyFunctions")
object ToolEnvelopeParser {
    private val TOOL_NAME_REGEX = Regex(""""name"\s*:\s*"([^"]+)"""")
    private val CDATA_TOOL_CALL_REGEX = Regex("""(?s)<!\[CDATA\[<tool>\s*(\{.*?\})\s*</tool>\]\]>""")
    private val WRAPPED_TOOL_CALL_REGEX = Regex("""(?s)<tool_call>\s*(\{.*?\})\s*</tool_call>""")
    @Suppress("MaxLineLength")
    private val GLM_TOOL_CALL_REGEX = Regex("""(?s)<tool_call>\s*([a-zA-Z0-9_]+)\s*<arg_key>([^<]+)</arg_key>\s*<arg_value>(.*?)</arg_value>\s*</tool_call>""")
    @Suppress("MaxLineLength")
    private val RAW_TOOL_CALL_REGEX = Regex("""(?s)(.*?)(?:,\s*)?(\{"name"\s*:\s*"[^"]+"\s*,\s*"arguments"\s*:\s*\{.*?\}\})(?:\s*,)?(.*)""")
    @Suppress("MaxLineLength")
    private val DSML_INVOKE_REGEX = Regex("""(?is)<\s*(?:\|+\s*)?DSML\s*(?:\|+\s*)?invoke\s+name\s*=\s*"([^"]+)"\s*>""")
    @Suppress("MaxLineLength")
    private val DSML_PARAMETER_REGEX = Regex("""(?is)<\s*(?:\|+\s*)?DSML\s*(?:\|+\s*)?parameter\s+name\s*=\s*"([^"]+)"(?:\s+string\s*=\s*"([^"]*)")?\s*>""")
    @Suppress("MaxLineLength")
    private val DSML_TAG_REGEX = Regex("""(?is)</?\s*(?:\|+\s*)?DSML\s*(?:\|+\s*)?[^>]*>""")
    private val DSML_TOOL_CALLS_OPEN_REGEX = Regex("""(?is)<\s*(?:\|+\s*)?DSML\s*(?:\|+\s*)?tool_calls\s*>""")
    private val DSML_TOOL_CALLS_CLOSE_REGEX = Regex("""(?is)</\s*(?:\|+\s*)?DSML\s*(?:\|+\s*)?tool_calls\s*>""")
    private val DSML_INVOKE_CLOSE_REGEX = Regex("""(?is)</\s*(?:\|+\s*)?DSML\s*(?:\|+\s*)?invoke\s*>""")
    private val DSML_PARAMETER_CLOSE_REGEX = Regex("""(?is)</\s*(?:\|+\s*)?DSML\s*(?:\|+\s*)?parameter\s*>""")
    @Suppress("MaxLineLength")
    private val MARKDOWN_FENCE_REGEX = Regex("""(?m)^[ \t]*```[a-zA-Z0-9_-]*[ \t]*\r?\n|\r?\n?[ \t]*```[ \t]*$""")
    private val PARAMETERS_KEY_REGEX = Regex(""""parameters"\s*:""")

    data class LocalToolEnvelope(
        val toolName: String,
        val argumentsJson: String,
        val visiblePrefix: String,
        val visibleSuffix: String,
    )

    fun requireSupportedTool(toolName: String) {
        require(ToolDefinition.fromName(toolName) != null) {
            "Unsupported tool: $toolName"
        }
    }

    fun buildArgumentsJson(query: String): String =
        ToolDefinition.WEB_SEARCH.encodeArguments(WebSearchParams(query))

    /**
     * Converts a raw Map (usually from an SDK like Google or Anthropic) into a JSON string.
     * Uses the toolName to ensure the correct ToolDefinition is used for encoding, which
     * respects @SerialName annotations and default values.
     */
    fun buildArgumentsJson(toolName: String, arguments: Map<String, *>): String {
        val toolDef = ToolDefinition.fromName(toolName)
        val jsonObject = buildJsonObjectFromMap(arguments)

        if (toolDef != null) {
            return try {
                // This ensures we validate the arguments against the ToolParameters model
                // and apply any @SerialName mapping.
                val params = toolDef.decodeArguments<ToolParameters>(jsonObject.toString())
                toolDef.encodeArguments(params)
            } catch (_: Exception) {
                // If decoding fails (missing fields etc), fall back to raw JSON
                // so the executor can decide how to handle it.
                jsonObject.toString()
            }
        }

        return jsonObject.toString()
    }

    @Deprecated("Use buildArgumentsJson(toolName, arguments) for robust parsing",
        ReplaceWith("buildArgumentsJson(toolName, arguments)"))
    fun buildArgumentsJson(arguments: Map<String, *>): String {
        // Fallback for search tools if toolName is unknown
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

        val urls = (arguments["urls"] as? List<*>)
            ?.filterIsInstance<String>()
            ?.takeIf(List<String>::isNotEmpty)
        if (urls != null) {
            val extractDepth = arguments["extract_depth"] as? String
            val format = arguments["format"] as? String
            return buildExtractArgumentsJson(urls, extractDepth, format)
        }

        // Generic fallback instead of throwing
        return buildJsonObjectFromMap(arguments).toString()
    }

    private fun buildJsonObjectFromMap(map: Map<String, *>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, toJsonElement(value))
            }
        }
    }

    private fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> @Suppress("UNCHECKED_CAST") (buildJsonObjectFromMap(value as Map<String, *>))
            is List<*> -> buildJsonArray {
                value.forEach { add(toJsonElement(it)) }
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    fun buildImageInspectArgumentsJson(question: String): String =
        ToolDefinition.ATTACHED_IMAGE_INSPECT.encodeArguments(AttachedImageInspectParams(question))

    fun buildExtractArgumentsJson(
        urls: List<String>,
        extractDepth: String? = null,
        format: String? = null,
    ): String = ToolDefinition.WEB_EXTRACT.encodeArguments(
        WebExtractParams(
            urls = urls,
            extract_depth = extractDepth?.let { ExtractDepth.valueOf(it) } ?: ExtractDepth.basic,
            format = format?.let { ExtractFormat.valueOf(it) } ?: ExtractFormat.markdown
        )
    )

    fun extractRequiredQuery(argumentsJson: String): String {
        return extractRequiredString(argumentsJson, "query")
    }

    fun extractRequiredQuestion(argumentsJson: String): String {
        return extractRequiredString(argumentsJson, "question")
    }

    fun extractRequiredChatId(argumentsJson: String): String {
        return extractRequiredString(argumentsJson, "chat_id")
    }

    fun buildSearchChatArgumentsJson(chatId: String, query: String): String =
        ToolDefinition.SEARCH_CHAT.encodeArguments(SearchChatParams(chatId, query))

    fun buildGetMessageContextArgumentsJson(messageId: String, before: Int? = null, after: Int? = null): String =
        ToolDefinition.GET_MESSAGE_CONTEXT.encodeArguments(
            GetMessageContextParams(
                message_id = messageId,
                before = before ?: 5,
                after = after ?: 5
            )
        )

    fun extractRequiredMessageId(argumentsJson: String): String {
        return extractRequiredString(argumentsJson, "message_id")
    }

    fun extractRequiredUrls(argumentsJson: String): List<String> {
        try {
            val json = Json.parseToJsonElement(argumentsJson).jsonObject
            val urlsValue = (
                json["urls"]
                    ?: json["arguments"]?.jsonObject?.get("urls")
                    ?: json["parameters"]?.jsonObject?.get("urls")
            )
            val urls = urlsValue?.toUrlList()
            if (!urls.isNullOrEmpty()) {
                return urls
            }
        } catch (_: Exception) {
            // Fall through to tolerant extraction for malformed provider text.
        }
        extractJsonLikeField(argumentsJson, "urls")
            ?.let(::parseJsonLikeValue)
            ?.let(::normalizeUrlValue)
            ?.takeIf(List<String>::isNotEmpty)
            ?.let { return it }

        throw IllegalArgumentException("Tool argument 'urls' is required and must be a non-empty array")
    }

    private data class WrappedToolCallParse(val payload: String, val envelopeRange: IntRange)

    private fun indexOfMatchingClosingBrace(text: String, openBraceIndex: Int): Int? {
        var depth = 0
        var inString = false
        var stringDelim: Char? = null
        var escape = false
        var i = openBraceIndex
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else when (c) {
                    '\\' -> escape = true
                    stringDelim -> inString = false
                }
            } else {
                when (c) {
                    '"', '\'' -> {
                        inString = true
                        stringDelim = c
                    }
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return null
    }

    /**
     * Finds the index of the matching closing bracket `]` for the `[` at [openBracketIndex].
     * Uses a bracket-depth scan that respects string literals and escape sequences.
     */
    private fun indexOfMatchingClosingBracket(text: String, openBracketIndex: Int): Int? {
        var depth = 0
        var inString = false
        var stringDelim: Char? = null
        var escape = false
        var i = openBracketIndex
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else when (c) {
                    '\\' -> escape = true
                    stringDelim -> inString = false
                }
            } else {
                when (c) {
                    '"', '\'' -> {
                        inString = true
                        stringDelim = c
                    }
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return null
    }

    /**
     * Splits a JSON array body (the text between the outer `[` and `]`) into individual `{...}` object strings.
     */
    private fun splitJsonObjects(arrayBody: String): List<String> {
        val objects = mutableListOf<String>()
        var i = 0
        while (i < arrayBody.length) {
            if (arrayBody[i] == '{') {
                val closeIndex = indexOfMatchingClosingBrace(arrayBody, i) ?: break
                objects.add(arrayBody.substring(i, closeIndex + 1))
                i = closeIndex + 1
            } else {
                i++
            }
        }
        return objects
    }

    private fun normalizeSingleQuotedToolCallPayload(payload: String): String {
        val t = payload.trim()
        return if (t.startsWith("{") && payload.contains("'name'")) {
            payload.replace('\'', '"')
        } else {
            payload
        }
    }

    private fun normalizeTypographyQuotesInExtractedField(value: String): String =
        value.replace('“', '"').replace('”', '"')

    /**
     * If [payload] contains `"parameters":` as a top-level key but not `"arguments":`,
     * replaces `"parameters":` with `"arguments":` so all downstream field-extraction
     * functions work without modification.
     */
    private fun normalizeParametersKey(payload: String): String {
        return if (PARAMETERS_KEY_REGEX.containsMatchIn(payload) && !payload.contains("\"arguments\":")) {
            PARAMETERS_KEY_REGEX.replace(payload, "\"arguments\":")
        } else {
            payload
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun resolveArgumentsJson(toolName: String, payload: String): String =
        when (toolName) {
            ToolDefinition.WEB_SEARCH.name -> buildArgumentsJson(extractRequiredQuery(payload))
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name -> buildImageInspectArgumentsJson(extractRequiredQuestion(payload))
            ToolDefinition.WEB_EXTRACT.name -> {
                val urls = extractRequiredUrls(payload)
                buildExtractArgumentsJson(urls)
            }
            ToolDefinition.SEARCH_CHAT_HISTORY.name -> ToolDefinition.SEARCH_CHAT_HISTORY.encodeArguments(
                SearchChatHistoryParams(queries = listOf(extractRequiredQuery(payload)))
            )
            ToolDefinition.SEARCH_CHAT.name -> buildSearchChatArgumentsJson(extractRequiredChatId(payload), extractRequiredQuery(payload))
            ToolDefinition.GET_MESSAGE_CONTEXT.name -> buildGetMessageContextArgumentsJson(extractRequiredMessageId(payload))
            ToolDefinition.X_SEARCH.name -> ToolDefinition.X_SEARCH.encodeArguments(
                XSearchParams(query = extractRequiredQuery(payload))
            )
            ToolDefinition.GENERATE_IMAGE.name -> {
                val parsed = try {
                    ToolDefinition.GENERATE_IMAGE.decodeArguments<GenerateImageToolParams>(payload)
                } catch (_: Exception) {
                    GenerateImageToolParams(prompt = extractRequiredString(payload, "prompt"))
                }
                ToolDefinition.GENERATE_IMAGE.encodeArguments(parsed)
            }
            ToolDefinition.GENERATE_VIDEO.name -> {
                val parsed = try {
                    ToolDefinition.GENERATE_VIDEO.decodeArguments<GenerateVideoToolParams>(payload)
                } catch (_: Exception) {
                    GenerateVideoToolParams(prompt = extractRequiredString(payload, "prompt"))
                }
                ToolDefinition.GENERATE_VIDEO.encodeArguments(parsed)
            }
            ToolDefinition.GENERATE_MUSIC.name -> {
                val parsed = try {
                    ToolDefinition.GENERATE_MUSIC.decodeArguments<GenerateMusicToolParams>(payload)
                } catch (_: Exception) {
                    GenerateMusicToolParams(prompt = extractRequiredString(payload, "prompt"))
                }
                ToolDefinition.GENERATE_MUSIC.encodeArguments(parsed)
            }
            else -> throw IllegalArgumentException("Unsupported tool: $toolName")
        }

    private fun parseWrappedToolCall(text: String): WrappedToolCallParse? {
        val tagOpen = "<tool_call>"
        val start = text.indexOf(tagOpen, ignoreCase = true)
        if (start < 0) return null
        val jsonStart = text.indexOf('{', start + tagOpen.length)
        if (jsonStart < 0) return null
        val jsonEnd = indexOfMatchingClosingBrace(text, jsonStart) ?: return null
        val rawPayload = text.substring(jsonStart, jsonEnd + 1)
        val payload = normalizeSingleQuotedToolCallPayload(rawPayload)
        val close = text.indexOf("</tool_call>", jsonEnd + 1, ignoreCase = true)
        if (close < 0) return null
        val exclusiveEnd = close + "</tool_call>".length
        return WrappedToolCallParse(payload, start until exclusiveEnd)
    }

    /**
     * Parses a `<tool_call>` tag that wraps a JSON array `[{...},{...}]`.
     * Returns null if the tag is absent or the payload starts with `{` (object, not array).
     */
    @Suppress("ThrowsCount")
    private fun parseWrappedToolCallArray(text: String): List<LocalToolEnvelope>? {
        val tagOpen = "<tool_call>"
        val start = text.indexOf(tagOpen, ignoreCase = true)
        if (start < 0) return null

        val contentStart = start + tagOpen.length
        val arrayStart = run {
            var idx = contentStart
            while (idx < text.length && text[idx].isWhitespace()) idx++
            idx
        }
        if (arrayStart >= text.length || text[arrayStart] != '[') return null

        val arrayEnd = indexOfMatchingClosingBracket(text, arrayStart)
            ?: throw IllegalStateException("Malformed tool_call envelope")

        val close = text.indexOf("</tool_call>", arrayEnd + 1, ignoreCase = true)
        if (close < 0) throw IllegalStateException("Malformed tool_call envelope")
        val exclusiveEnd = close + "</tool_call>".length

        val arrayBody = text.substring(arrayStart + 1, arrayEnd)
        val objects = splitJsonObjects(arrayBody)
        if (objects.isEmpty()) throw IllegalStateException("Malformed tool_call envelope")

        val visiblePrefix = text.substring(0, start)
        val visibleSuffix = text.substring(exclusiveEnd)

        return objects.map { rawObj ->
            val obj = normalizeParametersKey(normalizeSingleQuotedToolCallPayload(rawObj))
            val toolName = TOOL_NAME_REGEX.find(obj)?.groupValues?.get(1)
                ?: throw IllegalStateException("Malformed tool_call envelope")
            val argumentsJson = resolveArgumentsJson(toolName, obj)
            LocalToolEnvelope(
                toolName = toolName,
                argumentsJson = argumentsJson,
                visiblePrefix = visiblePrefix,
                visibleSuffix = visibleSuffix,
            )
        }
    }

    /**
     * Detects and parses the Mistral [TOOL_CALLS] prefix format.
     *
     * Returns a non-empty list when text.contains("[TOOL_CALLS]") and the JSON array
     * following the sentinel can be parsed.
     * Returns an empty list when the sentinel is absent.
     * Throws IllegalStateException("Malformed tool_call envelope") when the sentinel is present
     * but no valid JSON array follows.
     */
    @Suppress("ThrowsCount")
    private fun parseMistralToolCalls(text: String): List<LocalToolEnvelope> {
        val sentinel = "[TOOL_CALLS]"
        if (!text.contains(sentinel)) return emptyList()

        val sentinelEnd = text.indexOf(sentinel) + sentinel.length
        val arrayStart = text.indexOf('[', sentinelEnd)
        if (arrayStart < 0) throw IllegalStateException("Malformed tool_call envelope")

        val arrayEnd = indexOfMatchingClosingBracket(text, arrayStart)
            ?: throw IllegalStateException("Malformed tool_call envelope")

        val arrayBody = text.substring(arrayStart + 1, arrayEnd)
        val objects = splitJsonObjects(arrayBody)
        if (objects.isEmpty()) throw IllegalStateException("Malformed tool_call envelope")

        val visiblePrefix = text.substringBefore(sentinel).trim()
        return objects.map { rawObj ->
            val obj = normalizeParametersKey(normalizeSingleQuotedToolCallPayload(rawObj))
            val toolName = TOOL_NAME_REGEX.find(obj)?.groupValues?.get(1)
                ?: throw IllegalStateException("Malformed tool_call envelope")
            val argumentsJson = resolveArgumentsJson(toolName, obj)
            LocalToolEnvelope(
                toolName = toolName,
                argumentsJson = argumentsJson,
                visiblePrefix = visiblePrefix,
                visibleSuffix = "",
            )
        }
    }

    private data class DsmlBlock(
        val content: String,
        val range: IntRange,
    )

    private data class DsmlParameter(
        val name: String,
        val stringAttribute: Boolean,
        val body: String,
    )

    private fun normalizeToolText(text: String): String =
        text
            .replace('｜', '|')
            .replace(MARKDOWN_FENCE_REGEX, "")

    private fun parseDsmlToolCalls(text: String): List<LocalToolEnvelope> {
        val blocks = extractDsmlBlocks(text)
        if (blocks.isEmpty()) {
            if (text.contains("DSML", ignoreCase = true)) {
                throw IllegalStateException("Malformed DSML tool envelope")
            }
            return emptyList()
        }

        return blocks.flatMap { block ->
            val invokes = extractDsmlInvokes(block.content)
            if (invokes.isEmpty()) {
                throw IllegalStateException("Malformed DSML tool envelope")
            }
            invokes.map { invoke ->
                val visiblePrefix = text.substring(0, block.range.first)
                val visibleSuffix = text.substring(block.range.last + 1)
                invoke.copy(
                    visiblePrefix = visiblePrefix,
                    visibleSuffix = visibleSuffix,
                )
            }
        }
    }

    private fun extractDsmlBlocks(text: String): List<DsmlBlock> {
        val starts = DSML_TOOL_CALLS_OPEN_REGEX.findAll(text).toList()
        if (starts.isEmpty()) return emptyList()

        return starts.map { start ->
            val close = DSML_TOOL_CALLS_CLOSE_REGEX.find(text, start.range.last + 1)
                ?: throw IllegalStateException("Malformed DSML tool envelope")
            DsmlBlock(
                content = text.substring(start.range.last + 1, close.range.first),
                range = start.range.first..close.range.last,
            )
        }
    }

    private fun extractDsmlInvokes(block: String): List<LocalToolEnvelope> {
        val invokes = DSML_INVOKE_REGEX.findAll(block).toList()
        return invokes.map { invoke ->
            val close = DSML_INVOKE_CLOSE_REGEX.find(block, invoke.range.last + 1)
                ?: throw IllegalStateException("Malformed DSML tool envelope")
            val betweenInvokes = block.substring(invoke.range.last + 1, close.range.first)
            val toolName = invoke.groupValues[1].trim()
            requireSupportedTool(toolName)
            val params = extractDsmlParameters(toolName, betweenInvokes)
            LocalToolEnvelope(
                toolName = toolName,
                argumentsJson = buildArgumentsJson(toolName, params),
                visiblePrefix = "",
                visibleSuffix = "",
            )
        }
    }

    @Suppress("ThrowsCount")
    private fun extractDsmlParameters(toolName: String, invokeBody: String): Map<String, Any?> {
        val matches = DSML_PARAMETER_REGEX.findAll(invokeBody).toList()
        if (matches.isEmpty()) {
            throw IllegalStateException("Malformed DSML tool envelope")
        }

        val allowedParameters = expectedParameterNames(toolName)
        val parameters = linkedMapOf<String, Any?>()
        matches.forEach { open ->
            val name = open.groupValues[1].trim()
            require(name !in parameters) {
                "Duplicate parameter '$name' for tool '$toolName'"
            }
            require(name in allowedParameters) {
                "Unknown parameter '$name' for tool '$toolName'"
            }

            val close = DSML_PARAMETER_CLOSE_REGEX.find(invokeBody, open.range.last + 1)
                ?: throw IllegalStateException("Malformed DSML tool envelope")
            val body = invokeBody.substring(open.range.last + 1, close.range.first)
            val parameter = DsmlParameter(
                name = name,
                stringAttribute = open.groupValues.getOrNull(2)?.equals("true", ignoreCase = true) == true,
                body = body,
            )
            parameters[name] = parseDsmlParameterValue(toolName, parameter)
        }
        validateRequiredDsmlParameters(toolName, parameters)
        return parameters
    }

    private fun expectedParameterNames(toolName: String): Set<String> =
        ToolDefinition.fromName(toolName)
            ?.schema
            ?.properties
            ?.keys
            ?.toSet()
            ?: emptySet()

    private fun validateRequiredDsmlParameters(toolName: String, parameters: Map<String, Any?>) {
        val required = essentialRequiredParameterNames(toolName)
        required.forEach { name ->
            val value = parameters[name]
            val missing = when (value) {
                null -> true
                is String -> value.isBlank()
                is List<*> -> value.isEmpty()
                else -> false
            }
            if (missing) {
                require(false) {
                    "Tool argument '$name' is required"
                }
            }
        }
    }

    private fun essentialRequiredParameterNames(toolName: String): Set<String> =
        when (toolName) {
            ToolDefinition.WEB_SEARCH.name -> setOf("query")
            ToolDefinition.WEB_EXTRACT.name -> setOf("urls")
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name -> setOf("question")
            ToolDefinition.SEARCH_CHAT_HISTORY.name -> setOf("queries")
            ToolDefinition.SEARCH_CHAT.name -> setOf("chat_id", "query")
            ToolDefinition.GET_MESSAGE_CONTEXT.name -> setOf("message_id")
            ToolDefinition.X_SEARCH.name -> setOf("query")
            ToolDefinition.GENERATE_IMAGE.name,
            ToolDefinition.GENERATE_VIDEO.name,
            ToolDefinition.GENERATE_MUSIC.name,
            -> setOf("prompt")
            else -> ToolDefinition.fromName(toolName)?.schema?.required.orEmpty().toSet()
        }

    private fun parseDsmlParameterValue(toolName: String, parameter: DsmlParameter): Any? {
        val value = parameter.body
            .replace(DSML_TAG_REGEX, "")
            .trim()
        if (value.isEmpty()) {
            return ""
        }

        val expectsStructured = parameterExpectsStructuredValue(toolName, parameter.name)
        if (parameter.stringAttribute && !expectsStructured) {
            return unescapeJson(value)
        }

        return parseJsonLikeValue(value)
            ?.let { normalizeDsmlPlainValue(parameter.name, it) }
            ?: unescapeJson(value)
    }

    private fun parameterExpectsStructuredValue(toolName: String, parameterName: String): Boolean {
        val type = ToolDefinition.fromName(toolName)
            ?.schema
            ?.properties
            ?.get(parameterName)
            ?.jsonObject
            ?.get("type")
            ?.jsonPrimitive
            ?.content
        return type == "array" || type == "object" || type == "boolean" || type == "integer" || type == "number"
    }

    private fun parseJsonLikeValue(value: String): Any? =
        parseJsonValue(value)
            ?: repairJsonLikeValue(value)?.let(::parseJsonValue)

    private fun extractJsonLikeField(payload: String, fieldName: String): String? {
        val fieldMatch = Regex(""""$fieldName"\s*:""").find(payload) ?: return null
        var valueStart = fieldMatch.range.last + 1
        while (valueStart < payload.length && payload[valueStart].isWhitespace()) {
            valueStart += 1
        }
        if (valueStart >= payload.length) return null

        return when (payload[valueStart]) {
            '[' -> extractBalancedOrTrailingValue(payload, valueStart, '[', ']')
            '{' -> extractBalancedOrTrailingValue(payload, valueStart, '{', '}')
            '"' -> extractJsonStringLiteral(payload, valueStart)
            else -> payload.substring(valueStart)
                .substringBefore(',')
                .substringBefore('}')
                .trim()
        }
    }

    private fun extractBalancedOrTrailingValue(
        payload: String,
        start: Int,
        open: Char,
        close: Char,
    ): String {
        var depth = 0
        var inString = false
        var escape = false
        var index = start
        while (index < payload.length) {
            val char = payload[index]
            if (inString) {
                if (escape) {
                    escape = false
                } else when (char) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    open -> depth += 1
                    close -> {
                        depth -= 1
                        if (depth == 0) return payload.substring(start, index + 1)
                    }
                }
            }
            index += 1
        }
        return closeJsonBrackets(payload.substring(start).trimEnd('}', ']'))
    }

    private fun extractJsonStringLiteral(payload: String, start: Int): String {
        var escape = false
        var index = start + 1
        while (index < payload.length) {
            val char = payload[index]
            if (escape) {
                escape = false
            } else when (char) {
                '\\' -> escape = true
                '"' -> return payload.substring(start, index + 1)
            }
            index += 1
        }
        return payload.substring(start)
    }

    private fun parseJsonValue(value: String): Any? =
        runCatching {
            when (val element = Json.parseToJsonElement(value)) {
                is JsonObject -> element.toPlainMap()
                is kotlinx.serialization.json.JsonArray -> element.map { it.toPlainValue() }
                else -> element.toPlainValue()
            }
        }.getOrNull()

    private fun repairJsonLikeValue(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val withoutTrailingCommas = trimmed.replace(Regex(""",\s*([}\]])"""), "$1")
        val closed = closeJsonBrackets(withoutTrailingCommas)
        val quotedBareUrls = quoteBareUrlArrayItems(closed)
        return quotedBareUrls.takeIf { it != trimmed }
    }

    private fun closeJsonBrackets(value: String): String {
        val stack = mutableListOf<Char>()
        var inString = false
        var escape = false
        value.forEach { char ->
            if (inString) {
                if (escape) {
                    escape = false
                } else when (char) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '[' -> stack.add(']')
                    '{' -> stack.add('}')
                    ']', '}' -> if (stack.lastOrNull() == char) stack.removeAt(stack.lastIndex)
                }
            }
        }
        return buildString {
            append(value)
            stack.asReversed().forEach(::append)
        }
    }

    private fun quoteBareUrlArrayItems(value: String): String {
        if (!value.trimStart().startsWith("[")) return value
        val withoutWrappedBrackets = value.replace(Regex(""""[\[]+(https?://[^",\]]+)"""")) {
            """"${it.groupValues[1]}""""
        }
        return withoutWrappedBrackets.replace(Regex("""(?<=\[|,)\s*(https?://[^",\]\s]+)""")) {
            """"${it.groupValues[1]}""""
        }
    }

    private fun normalizeDsmlPlainValue(name: String, value: Any?): Any? =
        if (name == "urls" && value is List<*>) {
            value.map { item ->
                when (item) {
                    is String -> item.trim().trimStart('[')
                    else -> item
                }
            }
        } else {
            value
        }

    private fun JsonObject.toPlainMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key to value.toPlainValue() }

    private fun JsonElement.toPlainValue(): Any? =
        when (this) {
            is JsonObject -> toPlainMap()
            is kotlinx.serialization.json.JsonArray -> map { it.toPlainValue() }
            is JsonPrimitive -> when {
                isString -> content
                content == "true" -> true
                content == "false" -> false
                else -> content.toLongOrNull() ?: content.toDoubleOrNull() ?: content
            }
            JsonNull -> null
        }

    private fun JsonElement.toUrlList(): List<String>? =
        normalizeUrlValue(toPlainValue())

    private fun normalizeUrlValue(value: Any?): List<String>? =
        when (value) {
            is List<*> -> value
                .mapNotNull { item ->
                    (item as? String)
                        ?.trim()
                        ?.trimStart('[')
                        ?.takeIf(String::isNotEmpty)
                }
                .takeIf(List<String>::isNotEmpty)
            is String -> {
                val trimmed = value.trim()
                when {
                    trimmed.startsWith("[") ->
                        parseJsonLikeValue(trimmed)
                            ?.let(::normalizeUrlValue)
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
                        listOf(trimmed.trimStart('['))
                    else -> null
                }
            }
            else -> null
        }

    /**
     * Parses all tool-call envelopes from [text].
     *
     * Supports the following formats in priority order:
     *   1. Mistral [TOOL_CALLS] followed by a JSON array
     *   2. DSML  (<|DSML| invoke name="...">)
     *   3. GLM   (<tool_call> name <arg_key>...</arg_key> <arg_value>...</arg_value> </tool_call>)
     *   4. CDATA (<![CDATA[<tool>...</tool>]]>)
     *   5. <tool_call> wrapping either a JSON array or a JSON object
     *   6. Raw JSON object matching {"name":..., "arguments":...} or {"name":..., "parameters":...}
     *
     * Returns an empty list when no envelope is present.
     * Throws IllegalStateException("Malformed tool_call envelope") when an envelope marker is
     * detected but the payload cannot be parsed.
     * Throws IllegalArgumentException when the payload is valid but a required field is absent.
     *
     * The "parameters" key in any format is normalized to "arguments" before field extraction.
     */
    @Suppress("ThrowsCount", "ReturnCount")
    fun extractLocalToolEnvelopes(text: String): List<LocalToolEnvelope> {
        val normalizedText = normalizeToolText(text)

        // Priority 1: Mistral [TOOL_CALLS] format
        val mistral = parseMistralToolCalls(normalizedText)
        if (mistral.isNotEmpty()) return mistral

        // Priority 2: DSML format
        val dsml = parseDsmlToolCalls(normalizedText)
        if (dsml.isNotEmpty()) return dsml

        // Priority 3: GLM format
        val glmMatch = GLM_TOOL_CALL_REGEX.find(normalizedText)
        if (glmMatch != null) {
            val toolName = glmMatch.groupValues[1]
            val argKey = glmMatch.groupValues[2]
            val argVal = glmMatch.groupValues[3].trim('"')
            val argumentsJson = buildSingleFieldArgumentsJson(argKey, argVal)
            return listOf(
                LocalToolEnvelope(
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    visiblePrefix = normalizedText.substring(0, glmMatch.range.first),
                    visibleSuffix = normalizedText.substring(glmMatch.range.last + 1),
                )
            )
        }

        // Priority 4: CDATA format
        if (normalizedText.contains("<![CDATA[<tool")) {
            val cdataMatch = CDATA_TOOL_CALL_REGEX.find(normalizedText)
                ?: throw IllegalStateException("Malformed tool_call envelope")
            val rawPayload = normalizeSingleQuotedToolCallPayload(cdataMatch.groupValues[1])
            val payload = normalizeParametersKey(rawPayload)
            val toolName = TOOL_NAME_REGEX.find(payload)?.groupValues?.get(1)
                ?: throw IllegalStateException("Malformed tool_call envelope")
            val argumentsJson = resolveArgumentsJson(toolName, payload)
            return listOf(
                LocalToolEnvelope(
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    visiblePrefix = normalizedText.substring(0, cdataMatch.range.first),
                    visibleSuffix = normalizedText.substring(cdataMatch.range.last + 1),
                )
            )
        }

        // Priority 5: <tool_call> tag — array or object payload
        if (normalizedText.contains("<tool_call", ignoreCase = true)) {
            // 5a: Array payload
            val arrayResult = parseWrappedToolCallArray(normalizedText)
            if (arrayResult != null) return arrayResult

            // 5b: Object payload
            val wrapped = parseWrappedToolCall(normalizedText)
                ?: WRAPPED_TOOL_CALL_REGEX.find(normalizedText)?.let {
                    WrappedToolCallParse(
                        normalizeSingleQuotedToolCallPayload(it.groupValues[1]),
                        it.range,
                    )
                }

            if (wrapped == null) throw IllegalStateException("Malformed tool_call envelope")

            val payload = normalizeParametersKey(wrapped.payload)
            val toolName = TOOL_NAME_REGEX.find(payload)?.groupValues?.get(1)
                ?: throw IllegalStateException("Malformed tool_call envelope")
            val argumentsJson = resolveArgumentsJson(toolName, payload)
            return listOf(
                LocalToolEnvelope(
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    visiblePrefix = normalizedText.substring(0, wrapped.envelopeRange.first),
                    visibleSuffix = normalizedText.substring(wrapped.envelopeRange.last + 1),
                )
            )
        }

        // Priority 6: Raw JSON object — try original text first, then normalized (handles "parameters" key)
        val normalizedJsonText = normalizeParametersKey(normalizedText)
        val rawMatch = RAW_TOOL_CALL_REGEX.matchEntire(normalizedJsonText)
        if (rawMatch != null) {
            val rawPayload = normalizeSingleQuotedToolCallPayload(rawMatch.groupValues[2])
            val payload = normalizeParametersKey(rawPayload)
            val toolName = TOOL_NAME_REGEX.find(payload)?.groupValues?.get(1)
                ?: throw IllegalStateException("Malformed tool_call envelope")
            val argumentsJson = resolveArgumentsJson(toolName, payload)
            return listOf(
                LocalToolEnvelope(
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    visiblePrefix = rawMatch.groupValues[1].trimEnd(',', ' '),
                    visibleSuffix = rawMatch.groupValues[3].trimStart(',', ' '),
                )
            )
        }

        if (normalizedText.contains("{\"name\"") || normalizedText.contains(""""name":""")) {
            throw IllegalStateException("Malformed tool_call envelope")
        }

        return emptyList()
    }

    /**
     * Returns the first envelope from extractLocalToolEnvelopes, or null if the list is empty.
     * Preserves the exact existing error-propagation contract.
     */
    fun extractLocalToolEnvelope(text: String): LocalToolEnvelope? =
        extractLocalToolEnvelopes(text).firstOrNull()

    fun hasLocalToolContract(systemPrompt: String?): Boolean =
        systemPrompt != null &&
            (
                systemPrompt.contains("<tool_call>") ||
                    systemPrompt.contains("{\"name\"") ||
                    systemPrompt.contains("<![CDATA[<tool") ||
                    systemPrompt.contains("DSML") ||
                    systemPrompt.contains("invoke name=")
            ) &&
            ToolDefinition.ALL_TOOLS.any { systemPrompt.contains(it.name) }

    fun buildLocalToolResultMessage(resultJson: String): String =
        "<tool_result>$resultJson</tool_result>"

    @Suppress("MaxLineLength")
    fun stubResultJson(query: String): String =
        """{"query":"${escapeJson(query)}","results":[{"title":"Stub Tavily Result","url":"https://example.invalid/stub","content":"${escapeJson("Pocket Crew stub result for query: $query")}"}]}"""

    private fun extractRequiredString(argumentsJson: String, fieldName: String): String {
        try {
            val json = Json.parseToJsonElement(argumentsJson).jsonObject
            val value = json[fieldName]?.jsonPrimitive?.content
            if (!value.isNullOrBlank()) {
                return normalizeTypographyQuotesInExtractedField(value.trim())
            }
        } catch (_: Exception) {
            // Fallback to regex if JSON parsing fails (common in partial local model streams)
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
