package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.usecase.chat.ToolCallStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersClass: KClass<out ToolParameters>,
) {
    val schema: ToolSchema by lazy {
        ToolSchemaGenerator.generateSchema(parametersClass)
    }

    val parametersJson: String by lazy {
        schema.toString()
    }

    fun toExample(strategy: ToolCallStrategy): String = when (strategy) {
        ToolCallStrategy.JSON_XML_ENVELOPE -> {
            val arguments = buildExampleJsonMap()
            "ActionResult{\"name\":\"$name\",\"arguments\":$arguments}ActionResult"
        }
        ToolCallStrategy.LITE_RT_NATIVE -> {
            val args = buildExampleLiteRtArgs()
            "call:$name{$args}"
        }
        ToolCallStrategy.SDK_NATIVE -> ""
    }

    private fun buildExampleJsonMap(): String {
        val properties = schema.properties
        val json = buildJsonObject {
            properties.forEach { (key, value) ->
                val jsonObject = value as JsonObject
                val type = jsonObject["type"]?.jsonPrimitive?.content ?: "string"
                val enumValues = jsonObject["enum"]?.jsonArray
                
                if (enumValues != null && enumValues.isNotEmpty()) {
                    put(key, enumValues[0].jsonPrimitive.content)
                } else {
                    when (type) {
                        "string" -> put(key, "...")
                        "integer", "number" -> put(key, 0)
                        "boolean" -> put(key, false)
                        "array" -> {
                            val itemType = jsonObject["items"]?.jsonObject?.get("type")?.jsonPrimitive?.content ?: "string"
                            putJsonArray(key) {
                                if (itemType == "string") add(JsonPrimitive("...")) else add(JsonPrimitive(0))
                            }
                        }
                        else -> putJsonObject(key) {}
                    }
                }
            }
        }
        return json.toString()
    }

    private fun buildExampleLiteRtArgs(): String {
        val properties = schema.properties
        return properties.entries.joinToString(", ") { (key, value) ->
            val jsonObject = value as JsonObject
            val type = jsonObject["type"]?.jsonPrimitive?.content ?: "string"
            val enumValues = jsonObject["enum"]?.jsonArray
            
            val exampleVal = if (enumValues != null && enumValues.isNotEmpty()) {
                "<|\"|>${enumValues[0].jsonPrimitive.content}<|\"|>"
            } else {
                when (type) {
                    "string" -> "<|\"|>...<|\"|>"
                    "integer", "number" -> "0"
                    "boolean" -> "false"
                    "array" -> "<|\"|>...<|\"|>" // LiteRT expects comma-sep string for arrays
                    else -> "<|\"|>...<|\"|>"
                }
            }
            "$key: $exampleVal"
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ToolParameters> decodeArguments(argumentsJson: String): T {
        val serializer = json.serializersModule.serializer(parametersClass.java) as KSerializer<T>
        return json.decodeFromString(serializer, argumentsJson)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ToolParameters> encodeArguments(params: T): String {
        val serializer = json.serializersModule.serializer(parametersClass.java) as KSerializer<T>
        return json.encodeToString(serializer, params)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }

        val TAVILY_WEB_SEARCH = ToolDefinition(
            name = "tavily_web_search",
            description = "Search the web and return ranked results.",
            parametersClass = TavilyWebSearchParams::class,
        )

        val TAVILY_EXTRACT = ToolDefinition(
            name = "tavily_extract",
            description = "Extract and parse the full content from a list of URLs. Use this to read the details of webpages returned by tavily_web_search.",
            parametersClass = TavilyExtractParams::class,
        )

        val ATTACHED_IMAGE_INSPECT = ToolDefinition(
            name = "attached_image_inspect",
            description = "Inspect an attached image to answer a question about it.",
            parametersClass = AttachedImageInspectParams::class,
        )

        val SEARCH_CHAT_HISTORY = ToolDefinition(
            name = "search_chat_history",
            description = "Search the user's past conversation history for messages matching queries. Returns matching messages from across all chats, each with its chat_id and chat_name. Use when the user mentions or references something from a past conversation. Provide multiple query variants (synonyms, alternate phrasings, related terms) to cast a wide net. If you find a relevant message and need more context from that chat, use search_chat with the chat_id.",
            parametersClass = SearchChatHistoryParams::class,
        )

        val SEARCH_CHAT = ToolDefinition(
            name = "search_chat",
            description = "Search messages in a specific chat for details no longer in the context window due to summarization or FIFO eviction. Use when the user references something from earlier in a conversation that you cannot recall. Requires a chat_id (obtained from search_chat_history or from the current chat ID provided in your instructions).",
            parametersClass = SearchChatParams::class,
        )

        val GET_MESSAGE_CONTEXT = ToolDefinition(
            name = "get_message_context",
            description = "Get messages surrounding a specific message in its chat. Use when you found a relevant message via search but need more surrounding context to understand the conversation. Returns messages before and after the specified message in chronological order.",
            parametersClass = GetMessageContextParams::class,
        )

        val ALL_TOOLS = listOf(
            TAVILY_WEB_SEARCH,
            TAVILY_EXTRACT,
            ATTACHED_IMAGE_INSPECT,
            SEARCH_CHAT_HISTORY,
            SEARCH_CHAT,
            GET_MESSAGE_CONTEXT
        )

        fun fromName(name: String): ToolDefinition? {
            return ALL_TOOLS.find { it.name == name }
        }
    }
}
