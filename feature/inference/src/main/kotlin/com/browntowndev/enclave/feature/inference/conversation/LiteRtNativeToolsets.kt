package com.browntowndev.enclave.feature.inference.conversation

import android.util.Log
import com.browntowndev.enclave.domain.model.inference.AppendBlocksParams
import com.browntowndev.enclave.domain.model.inference.AttachedImageInspectParams
import com.browntowndev.enclave.domain.model.inference.ExtractDepth
import com.browntowndev.enclave.domain.model.inference.ExtractFormat
import com.browntowndev.enclave.domain.model.inference.GetMessageContextParams
import com.browntowndev.enclave.domain.model.inference.SearchArtifactParams
import com.browntowndev.enclave.domain.model.inference.SearchChatHistoryParams
import com.browntowndev.enclave.domain.model.inference.SearchChatParams
import com.browntowndev.enclave.domain.model.inference.StartArtifactParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.WebExtractParams
import com.browntowndev.enclave.domain.model.inference.WebSearchParams
import com.browntowndev.enclave.domain.util.NativeToolResultFormatter
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import java.util.concurrent.CancellationException as JavaCancellationException
import kotlinx.serialization.json.Json as KJson

internal object LiteRtNativeToolsets {
    private const val TAG = "ConversationManager"

    fun buildToolList(
        activeToolNames: List<String>,
        context: LiteRtToolContext,
    ): List<ToolSet> =
        buildList {
            activeToolNames.forEach { name ->
                val set: ToolSet? =
                    when (name) {
                        "disclose_tool_schemas" -> LocalDiscloseToolset(context)
                        "web_search" -> LocalSearchToolset(context)
                        "web_extract" -> LocalExtractToolset(context)
                        "attached_image_inspect" -> LocalImageInspectToolset(context)
                        "search_chat_history" -> LocalSearchChatHistoryToolset(context)
                        "search_chat" -> LocalSearchChatToolset(context)
                        "get_message_context" -> LocalGetMessageContextToolset(context)
                        "start_artifact" -> LocalStartArtifactToolset(context)
                        "append_blocks" -> LocalAppendBlocksToolset(context)
                        "search_current_artifact" -> LocalSearchArtifactToolset(context)
                        else -> null
                    }
                if (set != null) add(set)
            }
        }

    /**
     * Production helper: build a list of LiteRT [com.google.ai.edge.litertlm.ToolProvider]
     * instances wrapped via the litertlm `tool(...)` factory. The active-name [buildToolList]
     * overload above is exposed as raw [ToolSet] so unit tests can introspect class identities
     * without depending on litertlm internals.
     */
    fun buildToolProviders(
        activeToolNames: List<String>,
        context: LiteRtToolContext,
    ): List<com.google.ai.edge.litertlm.ToolProvider> = buildToolList(activeToolNames, context).map { tool(it) }

    @Suppress("FunctionNaming")
    internal class LocalDiscloseToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(
            description = "Disclose full schemas for deferred tools so the model may call them next turn.",
        )
        fun disclose_tool_schemas(
            @ToolParam(description = "JSON array of deferred tool names to activate.") tool_names: String,
        ): String =
            runBlocking(Dispatchers.IO) {
                context.toolLoop.handleDisclosure(
                    runCatching {
                        KJson
                            .parseToJsonElement(tool_names)
                            .jsonArray
                            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    }.getOrDefault(emptyList()),
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalSearchToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(description = "Search the web for information.")
        fun tavily_web_search(
            @ToolParam(description = "The search query.") query: String,
        ): String =
            dispatchTool(context, ToolDefinition.WEB_SEARCH) {
                ToolCallRequest(
                    toolName = ToolDefinition.WEB_SEARCH.name,
                    argumentsJson = ToolDefinition.WEB_SEARCH.encodeArguments(WebSearchParams(query)),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalExtractToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(description = "Extract and parse the full content from a list of URLs.")
        fun tavily_extract(
            @ToolParam(description = "JSON array of URLs to extract content from.") urls: String,
            @ToolParam(description = "Extraction depth: basic or advanced.") extract_depth: String = "basic",
            @ToolParam(description = "Output format: markdown or text.") format: String = "markdown",
        ): String =
            dispatchTool(context, ToolDefinition.WEB_EXTRACT) {
                val parsedUrls = NativeToolResultFormatter.parseUrls(urls)
                val params =
                    WebExtractParams(
                        urls = parsedUrls,
                        extract_depth =
                            extract_depth.takeIf { it.isNotBlank() }?.let { ExtractDepth.valueOf(it) }
                                ?: ExtractDepth.basic,
                        format = format.takeIf { it.isNotBlank() }?.let { ExtractFormat.valueOf(it) } ?: ExtractFormat.markdown,
                    )
                ToolCallRequest(
                    toolName = ToolDefinition.WEB_EXTRACT.name,
                    argumentsJson = ToolDefinition.WEB_EXTRACT.encodeArguments(params),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalImageInspectToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(description = "Inspect a previously attached image.")
        fun attached_image_inspect(
            @ToolParam(description = "The question about the image.") question: String,
        ): String =
            dispatchTool(context, ToolDefinition.ATTACHED_IMAGE_INSPECT) {
                ToolCallRequest(
                    toolName = ToolDefinition.ATTACHED_IMAGE_INSPECT.name,
                    argumentsJson =
                        ToolDefinition.ATTACHED_IMAGE_INSPECT.encodeArguments(
                            AttachedImageInspectParams(question),
                        ),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalSearchChatHistoryToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(description = "Search the user's past conversation history for context.")
        fun search_chat_history(
            @ToolParam(description = "The search queries separated by commas.") queries: String,
        ): String =
            dispatchTool(context, ToolDefinition.SEARCH_CHAT_HISTORY) {
                val queryList =
                    queries
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                ToolCallRequest(
                    toolName = ToolDefinition.SEARCH_CHAT_HISTORY.name,
                    argumentsJson =
                        ToolDefinition.SEARCH_CHAT_HISTORY.encodeArguments(
                            SearchChatHistoryParams(queries = queryList),
                        ),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalSearchChatToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(description = "Search messages in a specific chat for details no longer in context.")
        fun search_chat(
            @ToolParam(description = "The ID of the chat to search.") chat_id: String,
            @ToolParam(description = "The search query.") query: String,
        ): String =
            dispatchTool(context, ToolDefinition.SEARCH_CHAT) {
                ToolCallRequest(
                    toolName = ToolDefinition.SEARCH_CHAT.name,
                    argumentsJson =
                        ToolDefinition.SEARCH_CHAT.encodeArguments(
                            SearchChatParams(chat_id = chat_id, query = query),
                        ),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalGetMessageContextToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(description = "Get messages surrounding a specific message in its chat for more context.")
        fun get_message_context(
            @ToolParam(description = "The ID of the anchor message to get context around.") message_id: String,
            @ToolParam(description = "Number of messages before the anchor message. Default 5.") before: Int = 5,
            @ToolParam(description = "Number of messages after the anchor message. Default 5.") after: Int = 5,
        ): String =
            dispatchTool(context, ToolDefinition.GET_MESSAGE_CONTEXT) {
                val params = GetMessageContextParams(message_id, before, after)
                ToolCallRequest(
                    toolName = ToolDefinition.GET_MESSAGE_CONTEXT.name,
                    argumentsJson = ToolDefinition.GET_MESSAGE_CONTEXT.encodeArguments(params),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalStartArtifactToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(
            description =
                "Start a downloadable PDF or Word (.docx) document. Returns artifact_id for append_blocks. " +
                    "Use when the user asks to make a document, PDF, or Word file. Call once before append_blocks.",
        )
        fun start_artifact(
            @ToolParam(description = "The document title as a top-level string.") title: String,
            @ToolParam(description = "The document author. Leave empty if not specified.") author: String = "",
        ): String =
            dispatchTool(context, ToolDefinition.START_ARTIFACT) {
                val params =
                    StartArtifactParams(
                        title = title,
                        author = author.takeIf { it.isNotBlank() },
                    )
                ToolCallRequest(
                    toolName = ToolDefinition.START_ARTIFACT.name,
                    argumentsJson = ToolDefinition.START_ARTIFACT.encodeArguments(params),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                    assistantMessageId = context.toolLoop.latestAssistantMessageId(),
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalAppendBlocksToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(
            description =
                "Add sections to the in-progress PDF/Word document. Emit 10-15 blocks per call. " +
                    "Set is_complete=true on the final call to finish export.",
        )
        fun append_blocks(
            @ToolParam(description = "The artifact ID returned by start_artifact.") artifact_id: String,
            @ToolParam(description = "JSON array of document blocks to append.") blocks: String,
            @ToolParam(description = "Set to true when this is the final batch of blocks.") is_complete: Boolean = false,
        ): String =
            dispatchTool(context, ToolDefinition.APPEND_BLOCKS) {
                val blockList =
                    runCatching { KJson.parseToJsonElement(blocks).jsonArray.toList() }
                        .getOrDefault(emptyList())
                val params =
                    AppendBlocksParams(
                        artifactId = artifact_id,
                        blocks = blockList,
                        isComplete = is_complete,
                    )
                ToolCallRequest(
                    toolName = ToolDefinition.APPEND_BLOCKS.name,
                    argumentsJson = ToolDefinition.APPEND_BLOCKS.encodeArguments(params),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                    assistantMessageId = context.toolLoop.latestAssistantMessageId(),
                )
            }
    }

    @Suppress("FunctionNaming")
    internal class LocalSearchArtifactToolset(
        private val context: LiteRtToolContext,
    ) : ToolSet {
        @com.google.ai.edge.litertlm.Tool(
            description =
                "Search the PDF/Word document currently being written to recall details lost from context.",
        )
        fun search_current_artifact(
            @ToolParam(description = "The artifact ID to search within.") artifact_id: String,
            @ToolParam(description = "The semantic search query.") query: String,
        ): String =
            dispatchTool(context, ToolDefinition.SEARCH_CURRENT_ARTIFACT) {
                val params =
                    SearchArtifactParams(
                        artifactId = artifact_id,
                        query = query,
                    )
                ToolCallRequest(
                    toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                    argumentsJson = ToolDefinition.SEARCH_CURRENT_ARTIFACT.encodeArguments(params),
                    provider = "LITERT",
                    modelType = context.modelType,
                    chatId = context.chatId,
                    userMessageId = context.userMessageId,
                    assistantMessageId = context.toolLoop.latestAssistantMessageId(),
                )
            }
    }

    private fun dispatchTool(
        context: LiteRtToolContext,
        toolDefinition: ToolDefinition,
        buildRequest: () -> ToolCallRequest,
    ): String {
        Log.d(TAG, "Executing native tool call: ${toolDefinition.name}")
        context.toolLoop.loggingPort.info(
            TAG,
            "Native tool call requested tool=${toolDefinition.name} modelType=${context.modelType}",
        )
        return try {
            runBlocking(Dispatchers.IO) {
                val request = buildRequest()
                context.toolLoop.loggingPort.info(
                    TAG,
                    "Dispatching native tool call tool=${request.toolName} provider=${request.provider} modelType=${request.modelType}",
                )
                context.toolLoop.executeToolSafely(request)
            }
        } catch (e: JavaCancellationException) {
            throw e
        } catch (t: Throwable) {
            val rootCause = ConversationErrorMapper.rootCause(t)
            context.toolLoop.loggingPort.error(
                TAG,
                "Native tool method failed before safe execution tool=${toolDefinition.name} " +
                    "cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                rootCause,
            )
            ConversationErrorMapper.executionFailed(toolDefinition.name, rootCause)
        }
    }
}
