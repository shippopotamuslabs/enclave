package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.usecase.chat.ToolCallStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersClass: KClass<out ToolParameters>,
    val displayName: String? = null,
    val schemaOverride: ToolSchema? = null,
    val disclosureMode: ToolDisclosureMode = ToolDisclosureMode.DISCLOSE_ON_DEMAND,
    val supportsLazyDisclosure: Boolean = true,
) {
    val schema: ToolSchema by lazy {
        schemaProvider?.getSchema(name)?.let { ToolSchema.fromFullSchema(it) }
            ?: schemaOverride
            ?: ToolSchemaGenerator.generateSchema(parametersClass)
    }

    val parametersJson: String by lazy {
        schema.toString()
    }

    fun toExample(strategy: ToolCallStrategy): String =
        when (strategy) {
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

    fun catalogEntry(): ToolCatalogEntry {
        val (required, optional) = ToolParameters.extractArgumentHints(schema)
        return ToolCatalogEntry(
            name = name,
            description = description,
            disclosureMode = disclosureMode,
            requiredArguments = required,
            optionalArguments = optional,
        )
    }

    private fun buildExampleJsonMap(): String {
        val properties = schema.properties
        val json =
            buildJsonObject {
                properties.forEach { (key, value) ->
                    val jsonObject = value as JsonObject
                    val type = jsonObject.resolveSchemaType() ?: "string"
                    val enumValue = jsonObject.firstEnumValue()

                    if (enumValue != null) {
                        put(key, enumValue)
                    } else {
                        when (type) {
                            "string" -> put(key, "...")
                            "integer", "number" -> put(key, 0)
                            "boolean" -> put(key, false)
                            "array" -> {
                                val itemsObj = jsonObject["items"]?.jsonObject
                                val exampleElement: JsonElement =
                                    when {
                                        itemsObj?.firstEnumValue() != null -> {
                                            itemsObj.firstEnumValue()!!
                                        }
                                        itemsObj?.containsKey("oneOf") == true ||
                                            itemsObj?.containsKey("anyOf") == true ||
                                            itemsObj?.resolveSchemaType() == "object" -> {
                                            buildJsonObject {
                                                put("type", "paragraph")
                                                put("text", "...")
                                            }
                                        }
                                        else -> {
                                            val itemType = itemsObj?.resolveSchemaType() ?: "string"
                                            when (itemType) {
                                                "string" -> JsonPrimitive("...")
                                                "integer", "number" -> JsonPrimitive(0)
                                                "boolean" -> JsonPrimitive(false)
                                                else -> JsonPrimitive("...")
                                            }
                                        }
                                    }
                                putJsonArray(key) {
                                    add(exampleElement)
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
            val type = jsonObject.resolveSchemaType() ?: "string"
            val enumValue = jsonObject.firstEnumValue()

            val exampleVal =
                if (enumValue != null) {
                    "<|\"|>${enumValue.exampleLiteral()}<|\"|>"
                } else {
                    when (type) {
                        "string" -> "<|\"|>...<|\"|>"
                        "integer", "number" -> "0"
                        "boolean" -> "false"
                        "array" -> {
                            val itemsObj = jsonObject["items"]?.jsonObject
                            val inner =
                                when {
                                    itemsObj?.firstEnumValue() != null -> {
                                        itemsObj.firstEnumValue()!!.exampleLiteral()
                                    }
                                    itemsObj?.containsKey("oneOf") == true ||
                                        itemsObj?.containsKey("anyOf") == true ||
                                        itemsObj?.resolveSchemaType() == "object" -> {
                                        """[{"type":"paragraph","text":"..."}]"""
                                    }
                                    else -> "..."
                                }
                            "<|\"|>$inner<|\"|>"
                        }
                        else -> "<|\"|>...<|\"|>"
                    }
                }
            "$key: $exampleVal"
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ToolParameters> decodeArguments(argumentsJson: String): T {
        val normalized = ToolArgumentsJsonNormalizer.normalize(schema, argumentsJson)
        val serializer = json.serializersModule.serializer(parametersClass.java) as KSerializer<T>
        return json.decodeFromString(serializer, normalized)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : ToolParameters> encodeArguments(params: T): String {
        val serializer = json.serializersModule.serializer(parametersClass.java) as KSerializer<T>
        return json.encodeToString(serializer, params)
    }

    companion object {
        var schemaProvider: DynamicToolSchemaProvider? = null

        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
                coerceInputValues = true
            }

        val WEB_SEARCH =
            ToolDefinition(
                name = "web_search",
                description = "Search the web and return ranked results.",
                parametersClass = WebSearchParams::class,
                displayName = "Searching the web...",
                disclosureMode = ToolDisclosureMode.PRELOADED,
            )

        @Suppress("MaxLineLength")
        val WEB_EXTRACT =
            ToolDefinition(
                name = "web_extract",
                description =
                    "Extract and parse the full content from a list of URLs. " +
                        "Use this to read the details of webpages returned by web_search.",
                parametersClass = WebExtractParams::class,
                displayName = "Reading webpage content...",
                disclosureMode = ToolDisclosureMode.PRELOADED,
            )

        val ATTACHED_IMAGE_INSPECT =
            ToolDefinition(
                name = "attached_image_inspect",
                description = "Inspect an attached image to answer a question about it.",
                parametersClass = AttachedImageInspectParams::class,
                displayName = "Inspecting image...",
                disclosureMode = ToolDisclosureMode.PRELOADED,
            )

        val DISCLOSE_TOOL_SCHEMAS =
            ToolDefinition(
                name = "disclose_tool_schemas",
                description = "Disclose full schemas for deferred tools before calling them.",
                parametersClass = DiscloseToolSchemasParams::class,
                disclosureMode = ToolDisclosureMode.PRELOADED,
                supportsLazyDisclosure = false,
            )

        @Suppress("MaxLineLength")
        val SEARCH_CHAT_HISTORY =
            ToolDefinition(
                name = "search_chat_history",
                description = "Search the user's past conversation history for messages matching queries. Returns matching messages from across all chats, each with its chat_id and chat_name. Use when the user mentions or references something from a past conversation. Provide multiple query variants (synonyms, alternate phrasings, related terms) to cast a wide net. If you find a relevant message and need more context from that chat, use search_chat with the chat_id.",
                parametersClass = SearchChatHistoryParams::class,
                displayName = "Searching chat history...",
            )

        @Suppress("MaxLineLength")
        val SEARCH_CHAT =
            ToolDefinition(
                name = "search_chat",
                description = "Search messages in a specific chat for details no longer in the context window due to summarization or FIFO eviction. Use when the user references something from earlier in a conversation that you cannot recall. Requires a chat_id (obtained from search_chat_history or from the current chat ID provided in your instructions).",
                parametersClass = SearchChatParams::class,
                displayName = "Searching chat...",
            )

        val GET_MESSAGE_CONTEXT =
            ToolDefinition(
                name = "get_message_context",
                description =
                    "Get messages surrounding a specific message in its chat. " +
                        "Use when you found a relevant message via search but need more surrounding context " +
                        "to understand the conversation. Returns messages before and after the specified message " +
                        "in chronological order.",
                parametersClass = GetMessageContextParams::class,
            )

        val START_ARTIFACT =
            ToolDefinition(
                name = "start_artifact",
                description =
                    "Start creating a downloadable document for the user (exported as PDF or Word .docx). " +
                        "Use when they ask to make or write a document, report, plan, PDF, or Word file — " +
                        "not for casual chat replies. Returns artifact_id for append_blocks. " +
                        "Call once per document before any append_blocks. " +
                        "Required: top-level `title` (string). Optional: `author`. " +
                        "Do not put the title only inside nested metadata; " +
                        "after this succeeds once, use append_blocks only (do not call start_artifact again).",
                parametersClass = StartArtifactParams::class,
                displayName = "Creating document...",
            )

        val APPEND_BLOCKS =
            ToolDefinition(
                name = "append_blocks",
                description =
                    "Add content to the in-progress PDF/Word document started with start_artifact. " +
                        "ONLY include NEW blocks in each call. Do NOT resend previously appended blocks. " +
                        "Use artifactId from start_artifact. " +
                        "Include the final blocks AND set isComplete:true in the same call — " +
                        "do not make a separate empty-blocks finalization call. " +
                        "When isComplete is true, the user can export the finished PDF or Word document.",
                parametersClass = AppendBlocksParams::class,
                displayName = "Writing document...",
            )

        val SEARCH_CURRENT_ARTIFACT =
            ToolDefinition(
                name = "search_current_artifact",
                description =
                    "Search within the PDF/Word document currently being written for names, " +
                        "figures, or sections that may have been compressed out of context. " +
                        "Requires artifactId from start_artifact.",
                parametersClass = SearchArtifactParams::class,
                displayName = "Searching document...",
            )

        @Suppress("MaxLineLength")
        val MANAGE_MEMORIES =
            ToolDefinition(
                name = "manage_memories",
                description =
                    "Manage the user's memories. Use this to save important information " +
                        "about the user (identity, preferences, ideologies, etc.) or project context " +
                        "that you should remember across all conversations. You can also update, " +
                        "delete, or search existing memories.",
                parametersClass = ManageMemoriesParams::class,
            )

        @Suppress("MaxLineLength")
        val X_SEARCH =
            ToolDefinition(
                name = "search_x_recent",
                description = "Search for recent posts on X (Twitter). Returns a ranked list of high-impact posts matching the query. Use this to find trending topics, recent events, or public sentiment on X. Results include post text, author information, engagement metrics (impact_score), and media metadata where available. Pruned to surface the most relevant and high-engagement content.",
                parametersClass = XSearchParams::class,
                displayName = "Searching X...",
            )

        val GENERATE_IMAGE =
            ToolDefinition(
                name = "generate_image",
                description = "Generate an image from a text description.",
                parametersClass = GenerateImageToolParams::class,
                displayName = "Generating Image...",
            )

        val GENERATE_VIDEO =
            ToolDefinition(
                name = "generate_video",
                description = "Generate a video from a text description.",
                parametersClass = GenerateVideoToolParams::class,
                displayName = "Generating Video...",
            )

        val GENERATE_MUSIC =
            ToolDefinition(
                name = "generate_music",
                description = "Generate music from a text description.",
                parametersClass = GenerateMusicToolParams::class,
                displayName = "Generating Music...",
            )

        val ALL_TOOLS =
            listOf(
                DISCLOSE_TOOL_SCHEMAS,
                WEB_SEARCH,
                WEB_EXTRACT,
                ATTACHED_IMAGE_INSPECT,
                SEARCH_CHAT_HISTORY,
                SEARCH_CHAT,
                GET_MESSAGE_CONTEXT,
                MANAGE_MEMORIES,
                START_ARTIFACT,
                APPEND_BLOCKS,
                SEARCH_CURRENT_ARTIFACT,
                X_SEARCH,
                GENERATE_IMAGE,
                GENERATE_VIDEO,
                GENERATE_MUSIC,
            )

        fun fromName(name: String): ToolDefinition? = ALL_TOOLS.find { it.name == name }
    }
}

private fun JsonObject.resolveSchemaType(): String? {
    val typeElement = this["type"]
    return when (typeElement) {
        is JsonPrimitive -> typeElement.content
        is JsonArray -> typeElement.resolveArraySchemaType()
        else -> {
            this["oneOf"]?.jsonArray?.resolveCompositeSchemaType()
                ?: this["anyOf"]?.jsonArray?.resolveCompositeSchemaType()
        }
    }
}

private fun JsonArray.resolveArraySchemaType(): String? {
    val primitiveTypes = mapNotNull { (it as? JsonPrimitive)?.content }
    return primitiveTypes.firstOrNull { it != "null" }
}

private fun JsonArray.resolveCompositeSchemaType(): String? =
    mapNotNull { candidate ->
        when (candidate) {
            is JsonObject -> candidate.resolveSchemaType()
            is JsonPrimitive -> candidate.content.takeIf { it != "null" }
            else -> null
        }
    }.firstOrNull()

private fun JsonObject.firstEnumValue(): JsonElement? = (this["enum"] as? JsonArray)?.firstOrNull()

private fun JsonElement.exampleLiteral(): String =
    when (this) {
        is JsonPrimitive -> content
        else -> toString()
    }
