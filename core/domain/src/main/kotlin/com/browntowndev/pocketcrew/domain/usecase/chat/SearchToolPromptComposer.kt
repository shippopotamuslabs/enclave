package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

class SearchToolPromptComposer @Inject constructor() {

    fun compose(
        baseSystemPrompt: String?,
        includeSearchTool: Boolean = true,
        includeImageInspectTool: Boolean = false,
        includeMemoryTools: Boolean = true,
        currentChatId: String? = null,
        strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
    ): String =
        listOfNotNull(
            baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
            localToolContract(includeSearchTool, includeImageInspectTool, includeMemoryTools, currentChatId, strategy).takeIf(String::isNotEmpty),
        ).joinToString(separator = "\n\n")

    companion object {
        private const val SEARCH_TOOL_CONTRACT: String = """
# TOOL USE MANDATE:
You have access to real-time information via 'tavily_web_search' and 'tavily_extract'. 
If the user's request involves ANY of the following, you MUST call the search tool before answering:
- Current events, news, or recent developments.
- Dates, times, or schedules (today is April 15, 2026).
- Verifying facts, statistics, or status of products/companies.
- Anything where your internal knowledge might be stale or incomplete.
- Any terms you don't recognize.

DO NOT ASSUME you know the answer. When in doubt, SEARCH.

To search, respond with exactly one tool call and no surrounding prose:
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>

If you need to read the full content of a webpage from the search results, use tavily_extract with the URL(s):
<tool_call>{"name":"tavily_extract","arguments":{"urls":["..."],"extract_depth":"basic","format":"markdown"}}</tool_call>
"""

        private const val IMAGE_INSPECT_CONTRACT: String = """
# IMAGE INSPECTION MANDATE:
When the user asks about a previously attached image, you MUST use 'attached_image_inspect' to get details. 
DO NOT rely on memory if you need specific visual verification.

To inspect, respond with exactly one tool call and no surrounding prose:
<tool_call>{"name":"attached_image_inspect","arguments":{"question":"..."}}</tool_call>
"""

        private fun memoryToolsContract(currentChatId: String?): String = buildString {
            append("""
# CONVERSATION MEMORY TOOLS:
You have access to 'search_chat_history' and 'search_chat' to recall information.

- Use 'search_chat_history' when the user mentions or references a previous conversation, topic, or detail from a past chat. It returns chat IDs and titles.
- Use 'search_chat' when you need to search messages within a specific chat. You must provide the chat_id (from search_chat_history results or the current chat ID noted below).
""".trimIndent())
            if (currentChatId != null) {
                append("\n\nThe current chat ID is \"" + currentChatId + "\". Use this ID with search_chat to search messages in the current conversation.")
            }
            append("""

To search past chats:
<tool_call>{"name":"search_chat_history","arguments":{"query":"..."}}</tool_call>

To search messages in a specific chat:
<tool_call>{"name":"search_chat","arguments":{"chat_id":"...","query":"..."}}</tool_call>
""".trimIndent())
        }

        private const val STRICT_RULES: String = """
# STRICT EXECUTION RULES:
1. Respond ONLY with the <tool_call> tag. No preamble, no "Sure, let me search", no markdown code blocks.
2. After you receive a <tool_result>...</tool_result> message, use that data to provide a comprehensive answer.
3. NEVER expose raw tool JSON or <tool_call> tags in your final response to the user.
"""

        private const val LITE_RT_SEARCH_CONTRACT: String = """
# TOOL USE MANDATE:
You have access to real-time information via 'tavily_web_search' and 'tavily_extract'. 
If the user's request involves ANY of the following, you MUST call the search tool before answering:
- Current events, news, or recent developments.
- Dates, times, or schedules (today is April 15, 2026).
- Verifying facts, statistics, or status of products/companies.
- Anything where your internal knowledge might be stale or incomplete.
- Any terms you don't recognize.

DO NOT ASSUME you know the answer. When in doubt, SEARCH.

To search, respond with a tool call using this exact format:
call:tavily_web_search{query: <|"|>...<|"|>}

If you need to read the full content of a webpage from the search results, use tavily_extract. Note that 'urls' must be a SINGLE string where you list the URLs separated by commas. For very long pages, only the most relevant first part will be provided to fit the context window:
call:tavily_extract{urls: <|"|>https://example.com/page1,https://example.com/page2<|"|>, extract_depth: <|"|>basic<|"|>, format: <|"|>markdown<|"|>}
"""

        private const val LITE_RT_IMAGE_INSPECT_CONTRACT: String = """
# IMAGE INSPECTION MANDATE:
When the user asks about a previously attached image, you MUST use 'attached_image_inspect' to get details. 
DO NOT rely on memory if you need specific visual verification.

To inspect, respond with a tool call using this exact format:
call:attached_image_inspect{question: <|"|>...<|"|>}
"""

        private fun liteRtMemoryToolsContract(currentChatId: String?): String = buildString {
            append("""
# CONVERSATION MEMORY TOOLS:
You have access to 'search_chat_history' and 'search_chat' to recall information.

- Use 'search_chat_history' when the user mentions or references a previous conversation, topic, or detail from a past chat. It returns chat IDs and titles.
- Use 'search_chat' when you need to search messages within a specific chat. You must provide the chat_id (from search_chat_history results or the current chat ID noted below).
""".trimIndent())
            if (currentChatId != null) {
                append("\n\nThe current chat ID is \"" + currentChatId + "\". Use this ID with search_chat to search messages in the current conversation.")
            }
            append("""

To search past chats:
call:search_chat_history{query: <|"|>...<|"|>}

To search messages in a specific chat:
call:search_chat{chat_id: <|"|>...<|"|>, query: <|"|>...<|"|>}
""".trimIndent())
        }

        private const val LITE_RT_STRICT_RULES: String = """
# STRICT EXECUTION RULES:
1. Use exactly the 'call:tool_name{...}' format shown above.
2. Enclose all string arguments in <|"|> and <|"|>, not standard double quotes.
3. Close the argument list with exactly '}' (Do NOT add trailing brackets like ']').
4. Respond ONLY with the tool call. No conversational preamble or surrounding text.
5. NEVER wrap the tool call in special tokens or tags like <tool_call>, <tool_call|>, or [TOOL]. The response must start immediately with 'call:'.
"""

        fun localToolContract(
            includeSearchTool: Boolean = true,
            includeImageInspectTool: Boolean = false,
            includeMemoryTools: Boolean = true,
            currentChatId: String? = null,
            strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
        ): String {
            if (!includeSearchTool && !includeImageInspectTool && !includeMemoryTools) return ""
            
            return when (strategy) {
                ToolCallStrategy.JSON_XML_ENVELOPE -> buildString {
                    if (includeSearchTool) {
                        append(SEARCH_TOOL_CONTRACT.trim())
                    }
                    if (includeImageInspectTool) {
                        if (isNotEmpty()) {
                            append("\n\n")
                        }
                        append(IMAGE_INSPECT_CONTRACT.trim())
                    }
                    if (includeMemoryTools) {
                        if (isNotEmpty()) {
                            append("\n\n")
                        }
                        append(memoryToolsContract(currentChatId))
                    }
                    if (isNotEmpty()) {
                        append("\n\n")
                        append(STRICT_RULES.trim())
                    }
                }
                ToolCallStrategy.LITE_RT_NATIVE -> buildString {
                    if (includeSearchTool) {
                        append(LITE_RT_SEARCH_CONTRACT.trim())
                    }
                    if (includeImageInspectTool) {
                        if (isNotEmpty()) {
                            append("\n\n")
                        }
                        append(LITE_RT_IMAGE_INSPECT_CONTRACT.trim())
                    }
                    if (includeMemoryTools) {
                        if (isNotEmpty()) {
                            append("\n\n")
                        }
                        append(liteRtMemoryToolsContract(currentChatId))
                    }
                    if (isNotEmpty()) {
                        append("\n\n")
                        append(LITE_RT_STRICT_RULES.trim())
                    }
                }
                ToolCallStrategy.SDK_NATIVE -> "" // The SDK handles it via metadata, not system prompt
            }
        }
    }
}
