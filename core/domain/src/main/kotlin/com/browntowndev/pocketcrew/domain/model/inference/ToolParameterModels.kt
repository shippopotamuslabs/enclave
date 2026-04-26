package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class TavilyWebSearchParams(
    @ToolParam(description = "The search query to execute.")
    val query: String
) : ToolParameters

@Serializable
enum class ExtractDepth {
    basic, advanced
}

@Serializable
enum class ExtractFormat {
    markdown, text
}

@Serializable
data class TavilyExtractParams(
    @ToolParam(description = "List of URLs to extract content from.")
    val urls: List<String>,
    @ToolParam(description = "Extraction depth. 'basic' is faster, 'advanced' is more comprehensive but slower.")
    val extract_depth: ExtractDepth = ExtractDepth.basic,
    @ToolParam(description = "Output format. 'markdown' returns formatted content, 'text' returns plain text.")
    val format: ExtractFormat = ExtractFormat.markdown
) : ToolParameters

@Serializable
data class AttachedImageInspectParams(
    @ToolParam(description = "The question about the image.")
    val question: String
) : ToolParameters

@Serializable
data class SearchChatHistoryParams(
    @ToolParam(description = "Search queries to find relevant messages across all past conversations. Provide multiple variants (e.g. synonyms, alternate phrasings, related terms) to maximize recall. Any match across any query is returned.")
    val queries: List<String> = emptyList(),
    @ToolParam(description = "Deprecated: Use queries array instead. Single search query to find relevant messages.", required = false)
    val query: String? = null
) : ToolParameters

@Serializable
data class SearchChatParams(
    @ToolParam(description = "The ID of the chat to search. Use the current chat ID from your instructions for the current conversation, or a chat_id returned by search_chat_history.")
    val chat_id: String,
    @ToolParam(description = "The search query.")
    val query: String
) : ToolParameters

@Serializable
data class GetMessageContextParams(
    @ToolParam(description = "The ID of the anchor message to get context around.")
    val message_id: String,
    @ToolParam(description = "Number of messages before the anchor message to return. Default 5.", required = false)
    val before: Int = 5,
    @ToolParam(description = "Number of messages after the anchor message to return. Default 5.", required = false)
    val after: Int = 5
) : ToolParameters

@Serializable
enum class MemoryAction {
    save, update, delete, search
}

@Serializable
data class ManageMemoriesParams(
    @ToolParam(description = "The action to perform: 'save', 'update', 'delete', or 'search'.")
    val action: MemoryAction,
    @ToolParam(description = "The content of the memory to save or update, or the search query.", required = false)
    val content: String? = null,
    @ToolParam(description = "The category of the memory (for 'save' only): 'CORE_IDENTITY', 'PREFERENCES', 'FACTS', 'PROJECT_CONTEXT'.", required = false)
    val category: String? = null,
    @ToolParam(description = "The ID of the memory (for 'update' or 'delete' only).", required = false)
    val id: String? = null
) : ToolParameters
