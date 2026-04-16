package com.browntowndev.pocketcrew.domain.model.inference

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String,
) {
    companion object {
        val TAVILY_WEB_SEARCH = ToolDefinition(
            name = "tavily_web_search",
            description = "Search the web and return ranked results.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string"
                    }
                  },
                  "required": ["query"]
                }
            """.trimIndent(),
        )

        val TAVILY_EXTRACT = ToolDefinition(
            name = "tavily_extract",
            description = "Extract and parse the full content from a list of URLs. Use this to read the details of webpages returned by tavily_web_search.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "urls": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      },
                      "description": "List of URLs to extract content from."
                    },
                    "extract_depth": {
                      "type": "string",
                      "enum": ["basic", "advanced"],
                      "description": "Extraction depth. 'basic' is faster, 'advanced' is more comprehensive but slower."
                    },
                    "format": {
                      "type": "string",
                      "enum": ["markdown", "text"],
                      "description": "Output format. 'markdown' returns formatted content, 'text' returns plain text."
                    }
                  },
                  "required": ["urls", "extract_depth", "format"]
                }
            """.trimIndent(),
        )

        val ATTACHED_IMAGE_INSPECT = ToolDefinition(
            name = "attached_image_inspect",
            description = "Inspect an attached image to answer a question about it.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "question": {
                      "type": "string"
                    }
                  },
                  "required": ["question"]
                }
            """.trimIndent(),
        )

        val SEARCH_CHAT_HISTORY = ToolDefinition(
            name = "search_chat_history",
            description = "Search the user's past conversation history for context. Use when the user mentions or references a previous conversation, topic, or detail from a past chat.",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string"
                    }
                  },
                  "required": ["query"]
                }
            """.trimIndent(),
        )

        val SEARCH_CHAT = ToolDefinition(
            name = "search_chat",
            description = "Search messages in a specific chat for details no longer in the context window due to summarization, compaction, or FIFO eviction. Use when the user references something from earlier in a conversation that you cannot recall. Requires a chat_id (obtained from search_chat_history or from the current chat ID provided in your instructions).",
            parametersJson = """
                {
                  "type": "object",
                  "properties": {
                    "chat_id": {
                      "type": "string",
                      "description": "The ID of the chat to search. Use the current chat ID from your instructions for the current conversation, or a chat_id returned by search_chat_history."
                    },
                    "query": {
                      "type": "string"
                    }
                  },
                  "required": ["chat_id", "query"]
                }
            """.trimIndent(),
        )
    }
}