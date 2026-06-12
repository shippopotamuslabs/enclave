package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import javax.inject.Inject

class SearchToolPromptComposer
    @Inject
    constructor() : PromptComposerPort {
        override fun compose(
            baseSystemPrompt: String?,
            toolCatalog: List<ToolCatalogEntry>,
            activeTools: List<ToolDefinition>,
            currentChatId: String?,
            strategy: ToolCallStrategy,
            coreMemories: List<Memory>,
            retrievedMemories: List<Memory>,
            retrievedSearchResults: String?,
            retrievedXSearchResults: String?,
        ): String =
            listOfNotNull(
                formatCoreMemories(coreMemories).takeIf(String::isNotEmpty),
                formatPsychProfile(coreMemories).takeIf(String::isNotEmpty),
                baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
                catalogToolContract(
                    toolCatalog = toolCatalog,
                    activeTools = activeTools,
                    currentChatId = currentChatId,
                    strategy = strategy,
                    includeActiveExamples = true,
                ).takeIf(String::isNotEmpty),
            ).joinToString(separator = "\n\n")

        override fun composeVolatileTurnContext(
            retrievedMemories: List<Memory>,
            retrievedSearchResults: String?,
            retrievedXSearchResults: String?,
        ): String? =
            listOfNotNull(
                formatRetrievedMemories(retrievedMemories).takeIf(String::isNotEmpty),
                formatRetrievedSearchResults(retrievedSearchResults).takeIf(String::isNotEmpty),
                formatRetrievedXSearchResults(retrievedXSearchResults).takeIf(String::isNotEmpty),
            ).joinToString(separator = "\n\n").takeIf { it.isNotBlank() }

        companion object {
            const val TOOL_DISCOVERY_INSTRUCTION_TEXT =
                "Use the tool_discovery_tool to activate capabilities you need but do not see listed. Describe the capability, not the user's question."

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

            private fun formatPsychProfile(memories: List<Memory>): String {
                val profile = memories.filter { it.category == MemoryCategory.PSYCH_PROFILE }
                if (profile.isEmpty()) return ""

                return buildString {
                    append("# PSYCHOLOGICAL PROFILE:\n")
                    append("This dense profile was generated from the user's own AI conversation history. ")
                    append("Use it to calibrate depth, framing, and engagement — do not reference it explicitly unless asked.\n")
                    profile.forEach { append("${it.content}\n") }
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

            private fun formatRetrievedSearchResults(results: String?): String {
                if (results.isNullOrBlank()) return ""
                return """
# RELEVANT WEB SEARCH RESULTS:
The following search results were automatically retrieved based on the user's query:
$results
                    """.trimIndent()
            }

            private fun formatRetrievedXSearchResults(results: String?): String {
                if (results.isNullOrBlank()) return ""
                return """
# RELEVANT X SEARCH RESULTS:
The following X search results were automatically retrieved based on the user's query:
$results
                    """.trimIndent()
            }

            @Suppress("MaxLineLength")
            private fun metadataRules(): String =
                """
# TEMPORAL CONTEXT:
User messages include a `[Metadata: Sent at ...]` or `[Metadata: Current Time: ...]` footer. Use this to understand the current time and the timeline of the conversation.
IMPORTANT: DO NOT include metadata tags or mentions of the footer in your responses. Respond naturally as if the metadata were invisible context.
                """.trimIndent()

            private fun strictRules(strategy: ToolCallStrategy): String =
                when (strategy) {
                    ToolCallStrategy.JSON_XML_ENVELOPE ->
                        """
# STRICT EXECUTION RULES:
1. Respond ONLY with the <tool_call> tag. No preamble, no "Sure, let me search", no markdown code blocks.
2. After you receive a <tool_result>...</tool_result> message, use that data to provide a comprehensive answer.
3. NEVER expose raw tool JSON or <tool_call> tags in your final response to the user.
4. NEVER use call:tool_name{...} syntax. Use only the <tool_call> JSON envelope shown above.
                        """.trimIndent()
                    ToolCallStrategy.LITE_RT_NATIVE ->
                        """
# STRICT EXECUTION RULES:
1. Use exactly the 'call:tool_name{...}' format shown above.
2. Enclose all string arguments in <|"|> and <|"|>, not standard double quotes.
3. Close the argument list with exactly '}' (Do NOT add trailing brackets like ']').
4. Respond ONLY with the tool call. No conversational preamble or surrounding text.
5. NEVER wrap the tool call in special tokens or tags like <tool_call>, <tool_call|>, or [TOOL]. The response must start immediately with 'call:'.
                        """.trimIndent()
                    ToolCallStrategy.SDK_NATIVE -> ""
                }

            @Suppress("MaxLineLength")
            private fun formatActiveToolExamples(
                activeTools: List<ToolDefinition>,
                strategy: ToolCallStrategy,
            ): String =
                buildString {
                    val activeExamples =
                        activeTools.filter { it.name != ToolDefinition.SEARCH_TOOLS.name }
                    if (activeExamples.isEmpty()) return@buildString
                    append("# ACTIVE TOOL EXAMPLES:\n")
                    activeExamples.forEachIndexed { index, tool ->
                        if (index > 0) append("\n")
                        append(tool.toExample(strategy))
                        append("\n")
                    }
                }.trimEnd()

            private fun catalogToolContract(
                toolCatalog: List<ToolCatalogEntry>,
                activeTools: List<ToolDefinition>,
                currentChatId: String?,
                strategy: ToolCallStrategy,
                includeActiveExamples: Boolean,
            ): String {
                if (toolCatalog.isEmpty()) return ""
                if (strategy == ToolCallStrategy.SDK_NATIVE) return ""

                val discoveryBlock =
                    buildString {
                        append("# TOOL DISCOVERY:\n")
                        append(TOOL_DISCOVERY_INSTRUCTION_TEXT)
                        append("\n")
                        append("To activate:\n")
                        if (strategy == ToolCallStrategy.JSON_XML_ENVELOPE) {
                            append(
                                "<tool_call>{\"name\":\"${ToolDefinition.SEARCH_TOOLS.name}\",\"arguments\":" +
                                    "{\"capability\":\"describe the capability you need, e.g. send an email\"}}</tool_call>",
                            )
                        } else {
                            append(
                                "call:${ToolDefinition.SEARCH_TOOLS.name}{capability: <|\"|>describe the capability you need, e.g. send an email<|\"|>}",
                            )
                        }
                    }.trimEnd()

                val activeExamplesBlock =
                    if (includeActiveExamples) {
                        formatActiveToolExamples(activeTools = activeTools, strategy = strategy)
                    } else {
                        ""
                    }

                return buildString {
                    append(metadataRules())
                    append("\n\n")
                    append(discoveryBlock)
                    if (activeExamplesBlock.isNotEmpty()) {
                        append("\n\n")
                        append(activeExamplesBlock)
                    }
                    val rules = strictRules(strategy)
                    if (rules.isNotEmpty()) {
                        append("\n\n")
                        append(rules)
                    }
                    // Memory tool prose still useful when memory tools are part of the catalog (deferred).
                    if (toolCatalog.any { it.name == ToolDefinition.SEARCH_CHAT_HISTORY.name }) {
                        append("\n\n")
                        append(memoryDeferredNote(currentChatId))
                    }
                }
            }

            private fun memoryDeferredNote(currentChatId: String?): String =
                buildString {
                    append("# CONVERSATION MEMORY TOOLS:\n")
                    append(
                        "When you need past chats, message context, or long-term memories, call " +
                            "'${ToolDefinition.SEARCH_TOOLS.name}' with capabilities such as " +
                            "\"search past conversations\" or \"manage user memories\" before invoking memory tools.",
                    )
                    if (currentChatId != null) {
                        append(
                            "\nThe current chat ID is \"$currentChatId\". " +
                                "Use this chat_id with ${ToolDefinition.SEARCH_CHAT.name} after activation.",
                        )
                    }
                }
        }
    }
