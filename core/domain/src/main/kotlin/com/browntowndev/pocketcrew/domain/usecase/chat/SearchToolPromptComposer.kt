package com.browntowndev.pocketcrew.domain.usecase.chat

import javax.inject.Inject

class SearchToolPromptComposer @Inject constructor() {

    fun compose(
        baseSystemPrompt: String?,
        includeSearchTool: Boolean = true,
        includeImageInspectTool: Boolean = false,
        strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
    ): String =
        listOfNotNull(
            baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
            localToolContract(includeSearchTool, includeImageInspectTool, strategy).takeIf(String::isNotEmpty),
        ).joinToString(separator = "\n\n")

    companion object {
        private const val SEARCH_TOOL_CONTRACT: String = """
# TOOL USE MANDATE:
You have access to real-time information via 'tavily_web_search'. 
If the user's request involves ANY of the following, you MUST call the search tool before answering:
- Current events, news, or recent developments.
- Dates, times, or schedules (today is April 12, 2026).
- Verifying facts, statistics, or status of products/companies.
- Anything where your internal knowledge might be stale or incomplete.
- Any terms you don't recognize.

DO NOT ASSUME you know the answer. When in doubt, SEARCH.

To search, respond with exactly one tool call and no surrounding prose:
<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>
"""

        private const val IMAGE_INSPECT_CONTRACT: String = """
# IMAGE INSPECTION MANDATE:
When the user asks about a previously attached image, you MUST use 'attached_image_inspect' to get details. 
DO NOT rely on memory if you need specific visual verification.

To inspect, respond with exactly one tool call and no surrounding prose:
<tool_call>{"name":"attached_image_inspect","arguments":{"question":"..."}}</tool_call>
"""

        private const val STRICT_RULES: String = """
# STRICT EXECUTION RULES:
1. Respond ONLY with the <tool_call> tag. No preamble, no "Sure, let me search", no markdown code blocks.
2. After you receive a <tool_result>...</tool_result> message, use that data to provide a comprehensive answer.
3. NEVER expose raw tool JSON or <tool_call> tags in your final response to the user.
"""

        private const val LITE_RT_SEARCH_CONTRACT: String = """
# TOOL USE MANDATE:
You have access to real-time information via 'tavily_web_search'. 
If the user's request involves ANY of the following, you MUST call the search tool before answering:
- Current events, news, or recent developments.
- Dates, times, or schedules (today is April 12, 2026).
- Verifying facts, statistics, or status of products/companies.
- Anything where your internal knowledge might be stale or incomplete.
- Any terms you don't recognize.

DO NOT ASSUME you know the answer. When in doubt, SEARCH.

To search, respond with a tool call using this exact format:
call:tavily_web_search{query: <|"|>...<|"|>}
"""

        private const val LITE_RT_IMAGE_INSPECT_CONTRACT: String = """
# IMAGE INSPECTION MANDATE:
When the user asks about a previously attached image, you MUST use 'attached_image_inspect' to get details. 
DO NOT rely on memory if you need specific visual verification.

To inspect, respond with a tool call using this exact format:
call:attached_image_inspect{question: <|"|>...<|"|>}
"""

        private const val LITE_RT_STRICT_RULES: String = """
# STRICT EXECUTION RULES:
1. Use exactly the 'call:tool_name{...}' format shown above.
2. Enclose all string arguments in <|"|> and <|"|>, not standard double quotes.
3. Close the argument list with exactly '}' (Do NOT add trailing brackets like ']').
4. Respond ONLY with the tool call. No conversational preamble or surrounding text.
"""

        fun localToolContract(
            includeSearchTool: Boolean = true,
            includeImageInspectTool: Boolean = false,
            strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
        ): String {
            if (!includeSearchTool && !includeImageInspectTool) return ""
            
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
