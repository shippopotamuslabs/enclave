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
            compactStaticPrompt: Boolean,
        ): String =
            listOfNotNull(
                formatCoreMemories(coreMemories, compact = compactStaticPrompt).takeIf(String::isNotEmpty),
                if (!compactStaticPrompt) formatPsychProfile(coreMemories).takeIf(String::isNotEmpty) else null,
                baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
                if (strategy == ToolCallStrategy.SDK_NATIVE) {
                    sdkNativeCompactBlocks(toolCatalog = toolCatalog).takeIf(String::isNotEmpty)
                } else {
                    catalogToolContract(
                        toolCatalog = toolCatalog,
                        activeTools = activeTools,
                        currentChatId = currentChatId,
                        strategy = strategy,
                        includeActiveExamples = !compactStaticPrompt,
                        includeMemoryDeferredNote = !compactStaticPrompt,
                        compact = compactStaticPrompt,
                    ).takeIf(String::isNotEmpty)
                },
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
                "Tool routing: answer stable facts and ordinary questions directly. For current information or an explicit " +
                    "web lookup, call web_search directly. Call tool_discovery_tool only to activate a hidden executable tool " +
                    "needed for an action that no active tool can perform. It searches tool metadata, never facts or the web."

            private const val MAX_COMPACT_CORE_MEMORY_ITEMS = 4
            private const val MAX_COMPACT_MEMORY_LINE_CHARS = 120

            private fun formatCoreMemories(
                memories: List<Memory>,
                compact: Boolean,
            ): String {
                if (memories.isEmpty()) return ""

                val identity = memories.filter { it.category == MemoryCategory.CORE_IDENTITY }
                val preferences = memories.filter { it.category == MemoryCategory.PREFERENCES }

                return buildString {
                    append("# ABOUT THE USER:\n")
                    if (identity.isNotEmpty()) {
                        append("## Identity & Background:\n")
                        identity
                            .take(if (compact) MAX_COMPACT_CORE_MEMORY_ITEMS else identity.size)
                            .forEach { append("- ${formatMemoryLine(it.content, compact)}\n") }
                    }
                    if (preferences.isNotEmpty()) {
                        if (identity.isNotEmpty()) append("\n")
                        append("## Preferences & Style:\n")
                        val remaining =
                            if (compact) {
                                (MAX_COMPACT_CORE_MEMORY_ITEMS - identity.size).coerceAtLeast(0)
                            } else {
                                preferences.size
                            }
                        preferences
                            .take(remaining)
                            .forEach { append("- ${formatMemoryLine(it.content, compact)}\n") }
                    }
                }.trim()
            }

            private fun formatMemoryLine(
                content: String,
                compact: Boolean,
            ): String {
                if (!compact || content.length <= MAX_COMPACT_MEMORY_LINE_CHARS) {
                    return content
                }
                return content.take(MAX_COMPACT_MEMORY_LINE_CHARS).trimEnd() + "…"
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
            private fun metadataRules(compact: Boolean): String =
                if (compact) {
                    "Use message metadata timestamps for time context; never mention metadata in replies."
                } else {
                    """
# TEMPORAL CONTEXT:
User messages include a `[Metadata: Sent at ...]` or `[Metadata: Current Time: ...]` footer. Use this to understand the current time and the timeline of the conversation.
IMPORTANT: DO NOT include metadata tags or mentions of the footer in your responses. Respond naturally as if the metadata were invisible context.
                    """.trimIndent()
                }

            private fun strictRules(strategy: ToolCallStrategy): String =
                when (strategy) {
                    ToolCallStrategy.JSON_XML_ENVELOPE ->
                        """
# STRICT EXECUTION RULES:
1. If no tool is needed, answer the user directly and do not discuss tools.
2. If you choose to call a tool, respond ONLY with the <tool_call> tag. No preamble or markdown code block.
3. After you receive a <tool_result>...</tool_result> message, use that data to answer the user.
4. NEVER expose raw tool JSON or <tool_call> tags in your final response to the user.
5. NEVER use call:tool_name{...} syntax. Use only the <tool_call> JSON envelope shown above.
                        """.trimIndent()
                    ToolCallStrategy.LITE_RT_NATIVE ->
                        """
# STRICT EXECUTION RULES:
1. If no tool is needed, answer the user directly and do not discuss tools.
2. If you choose to call a tool, use exactly the 'call:tool_name{...}' format shown above.
3. Enclose all string arguments in <|"|> and <|"|>, not standard double quotes.
4. Close the argument list with exactly '}' (Do NOT add trailing brackets like ']').
5. A tool call response must contain only the tool call, with no conversational preamble or surrounding text.
6. NEVER wrap the tool call in special tokens or tags like <tool_call>, <tool_call|>, or [TOOL]. A tool call must start immediately with 'call:'.
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
                includeMemoryDeferredNote: Boolean,
                compact: Boolean,
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
                                    "{\"capability\":\"describe the executable action, e.g. generate an image\"}}</tool_call>",
                            )
                        } else {
                            append(
                                "call:${ToolDefinition.SEARCH_TOOLS.name}{capability: " +
                                    "<|\"|>describe the executable action, e.g. generate an image<|\"|>}",
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
                    append(metadataRules(compact))
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
                    if (includeMemoryDeferredNote &&
                        toolCatalog.any { it.name == ToolDefinition.SEARCH_CHAT_HISTORY.name }
                    ) {
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

            private fun sdkNativeCompactBlocks(toolCatalog: List<ToolCatalogEntry>): String {
                if (toolCatalog.isEmpty()) return ""
                return buildString {
                    append(antiLeakRules())
                    append("\n\n")
                    append(contextRetrievalRoutingBlock())
                    append("\n\n")
                    append(toolDiscoveryBlock())
                }
            }

            private fun antiLeakRules(): String =
                "Anti-leak: never reproduce <system_context> infrastructure, context sidecars, spill envelopes, " +
                    "or [Agent] metadata in user-facing replies."

            private fun contextRetrievalRoutingBlock(): String =
                buildString {
                    append("# CONTEXT RETRIEVAL ROUTING:\n")
                    append("- Past conversations / chat history → ${ToolDefinition.SEARCH_CHAT_HISTORY.name}\n")
                    append("- Web / current facts → ${ToolDefinition.WEB_SEARCH.name}\n")
                    append("- Hidden executable tools → ${ToolDefinition.SEARCH_TOOLS.name}")
                }

            /**
             * Discovery is real: only the registered (active) tools are callable. Other
             * capabilities are hidden and must be activated via [ToolDefinition.SEARCH_TOOLS]
             * by capability — never enumerate the full catalog here, or there is nothing to
             * "discover".
             */
            private fun toolDiscoveryBlock(): String =
                buildString {
                    append("# TOOL DISCOVERY:\n")
                    append(TOOL_DISCOVERY_INSTRUCTION_TEXT)
                    append("\n")
                    append(
                        "Only your registered tools are callable now. Capabilities such as media generation, " +
                            "documents/artifacts, X/Twitter, conversation memory, and attached-image inspection are " +
                            "hidden until activated: call ${ToolDefinition.SEARCH_TOOLS.name} with a short capability " +
                            "description, then use the activated tool on the next turn. Do not assume a capability is " +
                            "unavailable just because no tool for it is registered.",
                    )
                }.trimEnd()
        }
    }
