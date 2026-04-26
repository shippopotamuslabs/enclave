package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import javax.inject.Inject

class SearchToolPromptComposer @Inject constructor() {

    fun compose(
        baseSystemPrompt: String?,
        includeSearchTool: Boolean = true,
        includeImageInspectTool: Boolean = false,
        includeMemoryTools: Boolean = true,
        currentChatId: String? = null,
        strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
        coreMemories: List<Memory> = emptyList(),
        retrievedMemories: List<Memory> = emptyList(),
    ): String =
        listOfNotNull(
            formatCoreMemories(coreMemories).takeIf(String::isNotEmpty),
            baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
            formatRetrievedMemories(retrievedMemories).takeIf(String::isNotEmpty),
            localToolContract(includeSearchTool, includeImageInspectTool, includeMemoryTools, currentChatId, strategy).takeIf(String::isNotEmpty),
        ).joinToString(separator = "\n\n")

    companion object {
        private fun formatCoreMemories(memories: List<Memory>): String {
            if (memories.isEmpty()) return ""
            
            val identity = memories.filter { it.category == MemoryCategory.CORE_IDENTITY }
            val preferences = memories.filter { it.category == MemoryCategory.PREFERENCES }
            
            return buildString {
                append("# ABOUT THE USER:\n")
                if (identity.isNotEmpty()) {
                    append("## Identity & Background:\n")
                    identity.forEach { append("- ${it.content}\n") }
                }
                if (preferences.isNotEmpty()) {
                    if (identity.isNotEmpty()) append("\n")
                    append("## Preferences & Style:\n")
                    preferences.forEach { append("- ${it.content}\n") }
                }
            }.trim()
        }

        private fun formatRetrievedMemories(memories: List<Memory>): String {
            if (memories.isEmpty()) return ""
            
            return buildString {
                append("# RELEVANT FACTS & CONTEXT:\n")
                append("The following information was retrieved from your long-term memory as relevant to the current conversation:\n")
                memories.forEach { append("- ${it.content}\n") }
            }.trim()
        }

        private fun searchToolContract(strategy: ToolCallStrategy): String {
            val extractNote = if (strategy == ToolCallStrategy.LITE_RT_NATIVE) {
                " Note that 'urls' must be a SINGLE string where you list the URLs separated by commas. For very long pages, only the most relevant first part will be provided to fit the context window:"
            } else ""

            return """
# TOOL USE MANDATE:
You have access to real-time information via '${ToolDefinition.TAVILY_WEB_SEARCH.name}' and '${ToolDefinition.TAVILY_EXTRACT.name}'. 
If the user's request involves ANY of the following, you MUST call the search tool before answering:
- Current events, news, or recent developments.
- Dates, times, or schedules (today is April 16, 2026).
- Verifying facts, statistics, or status of products/companies.
- Anything where your internal knowledge might be stale or incomplete.
- Any terms you don't recognize.

DO NOT ASSUME you know the answer. When in doubt, SEARCH.

To search:
${ToolDefinition.TAVILY_WEB_SEARCH.toExample(strategy)}

If you need to read the full content of a webpage from the search results, use ${ToolDefinition.TAVILY_EXTRACT.name}.$extractNote
${ToolDefinition.TAVILY_EXTRACT.toExample(strategy)}
""".trimIndent()
        }

        private fun imageInspectContract(strategy: ToolCallStrategy): String = """
# IMAGE INSPECTION MANDATE:
When the user asks about a previously attached image, you MUST use '${ToolDefinition.ATTACHED_IMAGE_INSPECT.name}' to get details. 
DO NOT rely on memory if you need specific visual verification.

To inspect:
${ToolDefinition.ATTACHED_IMAGE_INSPECT.toExample(strategy)}
""".trimIndent()

        private fun memoryToolsContract(currentChatId: String?, strategy: ToolCallStrategy): String = buildString {
            append("""
# CONVERSATION MEMORY TOOLS:
You have access to '${ToolDefinition.SEARCH_CHAT_HISTORY.name}', '${ToolDefinition.SEARCH_CHAT.name}', '${ToolDefinition.GET_MESSAGE_CONTEXT.name}', and '${ToolDefinition.MANAGE_MEMORIES.name}' to recall and manage information.

- Use '${ToolDefinition.SEARCH_CHAT_HISTORY.name}' when the user mentions or references a previous conversation, topic, or detail from a past chat. Provide multiple query variants (synonyms, alternate phrasings) to cast a wide net. It returns matching messages with surrounding context.
- Use '${ToolDefinition.SEARCH_CHAT.name}' when you need to search messages within a specific chat. You must provide the chat_id (from ${ToolDefinition.SEARCH_CHAT_HISTORY.name} results or the current chat ID noted below).
- Use '${ToolDefinition.GET_MESSAGE_CONTEXT.name}' when you found a relevant message but need more surrounding context to understand the conversation flow. Provide the message_id and how many messages before/after you need.
- Use '${ToolDefinition.MANAGE_MEMORIES.name}' to save, update, delete, or search long-term memories. Use this for facts about the user, project context, or important details that should persist across all conversations.
""".trimIndent())
            
            if (strategy == ToolCallStrategy.LITE_RT_NATIVE) {
                append("\n\nFor '${ToolDefinition.SEARCH_CHAT_HISTORY.name}', provide queries as a single comma-separated string.")
            }

            if (currentChatId != null) {
                append("\n\nThe current chat ID is \"" + currentChatId + "\". Use this ID with ${ToolDefinition.SEARCH_CHAT.name} to search messages in the current conversation.")
            }
            append("""

To search past chats:
${ToolDefinition.SEARCH_CHAT_HISTORY.toExample(strategy)}

To search messages in a specific chat:
${ToolDefinition.SEARCH_CHAT.toExample(strategy)}

To get surrounding context for a message:
${ToolDefinition.GET_MESSAGE_CONTEXT.toExample(strategy)}

To manage long-term memories:
${ToolDefinition.MANAGE_MEMORIES.toExample(strategy)}
""".trimIndent())
        }

        private fun strictRules(strategy: ToolCallStrategy): String = when (strategy) {
            ToolCallStrategy.JSON_XML_ENVELOPE -> """
# STRICT EXECUTION RULES:
1. Respond ONLY with the ipsis tag. No preamble, no "Sure, let me search", no markdown code blocks.
2. After you receive a <tool_result>...</tool_result> message, use that data to provide a comprehensive answer.
3. NEVER expose raw tool JSON or ipsis tags in your final response to the user.
""".trimIndent()
            ToolCallStrategy.LITE_RT_NATIVE -> """
# STRICT EXECUTION RULES:
1. Use exactly the 'call:tool_name{...}' format shown above.
2. Enclose all string arguments in <|"|> and <|"|>, not standard double quotes.
3. Close the argument list with exactly '}' (Do NOT add trailing brackets like ']').
4. Respond ONLY with the tool call. No conversational preamble or surrounding text.
5. NEVER wrap the tool call in special tokens or tags like ipsis, <tool_call|>, or [TOOL]. The response must start immediately with 'call:'.
""".trimIndent()
            ToolCallStrategy.SDK_NATIVE -> ""
        }

        fun localToolContract(
            includeSearchTool: Boolean = true,
            includeImageInspectTool: Boolean = false,
            includeMemoryTools: Boolean = true,
            currentChatId: String? = null,
            strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
        ): String {
            if (!includeSearchTool && !includeImageInspectTool && !includeMemoryTools) return ""
            if (strategy == ToolCallStrategy.SDK_NATIVE) return ""

            return buildString {
                if (includeSearchTool) {
                    append(searchToolContract(strategy))
                }
                if (includeImageInspectTool) {
                    if (isNotEmpty()) append("\n\n")
                    append(imageInspectContract(strategy))
                }
                if (includeMemoryTools) {
                    if (isNotEmpty()) append("\n\n")
                    append(memoryToolsContract(currentChatId, strategy))
                }
                val rules = strictRules(strategy)
                if (rules.isNotEmpty()) {
                    append("\n\n")
                    append(rules)
                }
            }
        }
    }
}
