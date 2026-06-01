package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.Serializable

@Serializable
data class WebSearchParams(
    @ToolParam(description = "The search query to execute.")
    val query: String,
) : ToolParameters

@Serializable
enum class XSortOrder {
    relevancy,
    recency,
}

@Serializable
data class XSearchParams(
    @ToolParam(description = "Primary keywords to search for. All keywords must be present (AND).", required = false)
    val keywords: List<String>? = null,
    @ToolParam(description = "At least one of these keywords must be present (OR).", required = false)
    val any_keywords: List<String>? = null,
    @ToolParam(description = "Keywords to exclude from results (NOT).", required = false)
    val exclude_keywords: List<String>? = null,
    @ToolParam(description = "Exact phrases to search for.", required = false)
    val phrases: List<String>? = null,
    @ToolParam(description = "Hashtags to include (without #).", required = false)
    val hashtags: List<String>? = null,
    @ToolParam(description = "Users mentioned in the post (without @).", required = false)
    val mentions: List<String>? = null,
    @ToolParam(description = "Limit results to posts from this user.", required = false)
    val from_user: String? = null,
    @ToolParam(description = "Limit results to replies to this user.", required = false)
    val to_user: String? = null,
    @ToolParam(description = "If true, only return posts with geo-tagging information.", required = false)
    val has_geo: Boolean = false,
    @ToolParam(description = "If true, only return posts with any media (images, videos, or gifs).", required = false)
    val has_media: Boolean = false,
    @ToolParam(description = "Limit results to a specific country code (ISO 3166-1 alpha-2, e.g. 'GB', 'US').", required = false)
    val place_country: String? = null,
    @ToolParam(description = "If true, only return posts with images.", required = false)
    val has_images: Boolean = false,
    @ToolParam(description = "If true, only return posts with videos.", required = false)
    val has_videos: Boolean = false,
    @ToolParam(description = "If true, only return posts with links.", required = false)
    val has_links: Boolean = false,
    @ToolParam(description = "The ISO 639-1 language code (e.g. 'en', 'es'). Defaults to 'en'.", required = false)
    val language: String? = "en",
    @ToolParam(description = "The order in which to return results.", required = false)
    val sort_order: XSortOrder = XSortOrder.relevancy,
    @ToolParam(description = "If true, include replies in the search results. Defaults to false for higher signal.", required = false)
    val include_replies: Boolean = false,
    @ToolParam(description = "DEPRECATED: Use structured fields instead. Raw search query.", required = false)
    val query: String? = null,
) : ToolParameters

@Serializable
enum class ExtractDepth {
    basic,
    advanced,
}

@Serializable
enum class ExtractFormat {
    markdown,
    text,
}

@Serializable
data class WebExtractParams(
    @ToolParam(description = "List of URLs to extract content from.")
    val urls: List<String>,
    @ToolParam(description = "Extraction depth. 'basic' is faster, 'advanced' is more comprehensive but slower.")
    val extract_depth: ExtractDepth = ExtractDepth.basic,
    @ToolParam(description = "Output format. 'markdown' returns formatted content, 'text' returns plain text.")
    val format: ExtractFormat = ExtractFormat.markdown,
) : ToolParameters

@Serializable
data class AttachedImageInspectParams(
    @ToolParam(description = "The question about the image.")
    val question: String,
) : ToolParameters

@Serializable
data class SearchChatHistoryParams(
    @ToolParam(
        description =
            "Search queries to find relevant messages across all past conversations. " +
                "Provide multiple variants (e.g. synonyms, alternate phrasings, related terms) to maximize recall. " +
                "Any match across any query is returned.",
    )
    val queries: List<String> = emptyList(),
    @ToolParam(description = "Deprecated: Use queries array instead. Single search query to find relevant messages.", required = false)
    val query: String? = null,
) : ToolParameters

@Serializable
data class SearchChatParams(
    @ToolParam(
        description =
            "The ID of the chat to search. Use the current chat ID from your instructions " +
                "for the current conversation, or a chat_id returned by search_chat_history.",
    )
    val chat_id: String,
    @ToolParam(description = "The search query.")
    val query: String,
) : ToolParameters

@Serializable
data class GetMessageContextParams(
    @ToolParam(description = "The ID of the anchor message to get context around.")
    val message_id: String,
    @ToolParam(description = "Number of messages before the anchor message to return. Default 5.", required = false)
    val before: Int = 5,
    @ToolParam(description = "Number of messages after the anchor message to return. Default 5.", required = false)
    val after: Int = 5,
) : ToolParameters

@Serializable
enum class MemoryAction {
    save,
    update,
    delete,
    search,
}

@Serializable
data class ManageMemoriesParams(
    @ToolParam(description = "The action to perform: 'save', 'update', 'delete', or 'search'.")
    val action: MemoryAction,
    @ToolParam(description = "The content of the memory to save or update, or the search query.", required = false)
    val content: String? = null,
    @ToolParam(
        description = "The category of the memory (for 'save' only): 'CORE_IDENTITY', 'PREFERENCES', 'FACTS', 'PROJECT_CONTEXT'.",
        required = false,
    )
    val category: String? = null,
    @ToolParam(description = "The ID of the memory (for 'update' or 'delete' only).", required = false)
    val id: String? = null,
) : ToolParameters

@Serializable
data class XGetThreadParams(
    @ToolParam(
        description =
            "The URL or ID of the tweet to fetch. Response includes requested_post for this exact post, " +
                "not the parent it replies to.",
    )
    val tweet_url: String,
) : ToolParameters

@Serializable
data class XTrendingParams(
    @ToolParam(
        description = "The location to get trending topics for (e.g. 'worldwide', 'united states'). Defaults to 'worldwide'.",
        required = false,
    )
    val location: String = "worldwide",
) : ToolParameters

@Serializable
data class XUserTweetsParams(
    @ToolParam(description = "The user screen name (e.g. 'elonmusk' or '@elonmusk').")
    val username: String,
    @ToolParam(
        description =
            "Optional keyword filter. When omitted, returns the user's timeline " +
                "newest-first. When set, searches that user's recent posts for the keyword.",
        required = false,
    )
    val search_text: String? = null,
    @ToolParam(
        description =
            "Pagination token from a prior x_user_tweets response (next_token field). " +
                "Omit entirely on the first request — do not pass null or an empty string.",
        required = false,
    )
    val next_token: String? = null,
    @ToolParam(
        description =
            "Must match the prior response pagination_source (timeline or search). " +
                "Pass through unchanged when using next_token.",
        required = false,
    )
    val pagination_source: String? = null,
    @ToolParam(
        description = "Maximum posts to return for this page (5–100). Defaults to 10.",
        required = false,
    )
    val max_results: Int = 10,
) : ToolParameters

@Serializable
data class XMentionSearchParams(
    @ToolParam(description = "The user screen name whose mentions to search (e.g. 'elonmusk' or '@elonmusk').")
    val username: String,
    @ToolParam(description = "Keywords to search within the mentions.")
    val search_text: String,
    @ToolParam(description = "Optional pagination token to retrieve subsequent pages.", required = false)
    val next_token: String? = null,
) : ToolParameters

// ── Profile analysis tools (used only in the psychoanalysis session, never in chat) ──────────

@Serializable
data class ListConversationsParams(
    @ToolParam(description = "Page number (1-based).", required = false)
    val page: Int = 1,
    @ToolParam(description = "Number of conversations per page (max 50).", required = false)
    val page_size: Int = 20,
    @ToolParam(description = "Only return conversations with signal_score >= this value.", required = false)
    val min_score: Float? = null,
) : ToolParameters

@Serializable
data class ReadConversationParams(
    @ToolParam(description = "Internal conversation ID from list_conversations.")
    val conversation_id: String,
    @ToolParam(description = "Page of messages to retrieve (1-based, 15 messages per page).", required = false)
    val page: Int = 1,
) : ToolParameters

@Serializable
data class SearchConversationsParams(
    @ToolParam(description = "Keyword or phrase to search across conversation titles and user messages.")
    val query: String,
    @ToolParam(description = "Maximum number of results to return.", required = false)
    val limit: Int = 5,
) : ToolParameters

enum class FindingCategory {
    EVIDENCE,
    PATTERN,
    SYNTHESIS,
    CRITIQUE,
}

@Serializable
data class RecordFindingParams(
    @ToolParam(description = "The finding content to record.")
    val content: String,
    @ToolParam(description = "Category: EVIDENCE, PATTERN, SYNTHESIS, or CRITIQUE.", required = false)
    val category: FindingCategory = FindingCategory.EVIDENCE,
) : ToolParameters

@Serializable
data class ReadFindingsParams(
    @ToolParam(description = "Optional category filter: EVIDENCE, PATTERN, SYNTHESIS, or CRITIQUE.", required = false)
    val category: FindingCategory? = null,
) : ToolParameters
