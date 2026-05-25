package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
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
                baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
                formatRetrievedMemories(retrievedMemories).takeIf(String::isNotEmpty),
                formatRetrievedSearchResults(retrievedSearchResults).takeIf(String::isNotEmpty),
                formatRetrievedXSearchResults(retrievedXSearchResults).takeIf(String::isNotEmpty),
                catalogToolContract(
                    toolCatalog = toolCatalog,
                    activeTools = activeTools,
                    currentChatId = currentChatId,
                    strategy = strategy,
                ).takeIf(String::isNotEmpty),
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
1. Respond ONLY with the ipsis tag. No preamble, no "Sure, let me search", no markdown code blocks.
2. After you receive a <tool_result>...</tool_result> message, use that data to provide a comprehensive answer.
3. NEVER expose raw tool JSON or ipsis tags in your final response to the user.
                        """.trimIndent()
                    ToolCallStrategy.LITE_RT_NATIVE ->
                        """
# STRICT EXECUTION RULES:
1. Use exactly the 'call:tool_name{...}' format shown above.
2. Enclose all string arguments in <|"|> and <|"|>, not standard double quotes.
3. Close the argument list with exactly '}' (Do NOT add trailing brackets like ']').
4. Respond ONLY with the tool call. No conversational preamble or surrounding text.
5. NEVER wrap the tool call in special tokens or tags like ipsis, <tool_call|>, or [TOOL]. The response must start immediately with 'call:'.
                        """.trimIndent()
                    ToolCallStrategy.SDK_NATIVE -> ""
                }

            @Suppress("MaxLineLength")
            private fun catalogToolContract(
                toolCatalog: List<ToolCatalogEntry>,
                activeTools: List<ToolDefinition>,
                currentChatId: String?,
                strategy: ToolCallStrategy,
            ): String {
                if (toolCatalog.isEmpty()) return ""
                if (strategy == ToolCallStrategy.SDK_NATIVE) return ""

                val activeNames = activeTools.map { it.name }.toSet()
                val deferredNames =
                    toolCatalog
                        .filter { it.disclosureMode == ToolDisclosureMode.DISCLOSE_ON_DEMAND }
                        .map { it.name }
                        .filter { it !in activeNames }

                val catalogBlock =
                    buildString {
                        append("# TOOL CATALOG:\n")
                        toolCatalog.forEach { entry ->
                            val args =
                                (entry.requiredArguments.map { it.name to "required" } +
                                    entry.optionalArguments.map { it.name to "optional" })
                                    .joinToString(", ") { (name, kind) -> "$name:$kind" }
                            val disclosureTag =
                                when (entry.disclosureMode) {
                                    ToolDisclosureMode.PRELOADED -> "preloaded"
                                    ToolDisclosureMode.INTENT_ACTIVATED -> "intent-activated"
                                    ToolDisclosureMode.DISCLOSE_ON_DEMAND -> "deferred"
                                }
                            append("- ${entry.name} [$disclosureTag]: ${entry.description}")
                            if (args.isNotEmpty()) append(" args($args)")
                            append("\n")
                        }
                    }.trimEnd()

                val disclosureBlock =
                    buildString {
                        append("# SCHEMA DISCLOSURE:\n")
                        append(
                            "Deferred tools above are listed without full schemas. Before calling a deferred tool, " +
                                "invoke '${ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name}' with the names you want to activate.\n",
                        )
                        append("To disclose:\n")
                        if (strategy == ToolCallStrategy.JSON_XML_ENVELOPE) {
                            val example = deferredNames.take(1).ifEmpty { listOf("tool_name") }
                            append(
                                "ActionResult{\"name\":\"${ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name}\",\"arguments\":" +
                                    "{\"tool_names\":${example.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }}}}ActionResult",
                            )
                        } else {
                            val example = deferredNames.take(1).ifEmpty { listOf("tool_name") }
                            val arrayLiteral =
                                example.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                            append(
                                "call:${ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name}{tool_names: <|\"|>$arrayLiteral<|\"|>}",
                            )
                        }
                    }.trimEnd()

                val activeExamplesBlock =
                    buildString {
                        val activeNonDisclosure =
                            activeTools.filter { it.name != ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name }
                        if (activeNonDisclosure.isEmpty()) return@buildString
                        append("# ACTIVE TOOL EXAMPLES:\n")
                        activeNonDisclosure.forEachIndexed { index, tool ->
                            if (index > 0) append("\n")
                            append(tool.toExample(strategy))
                            append("\n")
                        }
                    }.trimEnd()

                return buildString {
                    append(metadataRules())
                    append("\n\n")
                    append(catalogBlock)
                    append("\n\n")
                    append(disclosureBlock)
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
                    append("# CONVERSATION MEMORY TOOLS (deferred):\n")
                    append(
                        "Memory tools ('${ToolDefinition.SEARCH_CHAT_HISTORY.name}', '${ToolDefinition.SEARCH_CHAT.name}', " +
                            "'${ToolDefinition.GET_MESSAGE_CONTEXT.name}', '${ToolDefinition.MANAGE_MEMORIES.name}') " +
                            "are available via disclosure when needed for recalling chats, message_id-anchored context, " +
                            "or long-term memories.",
                    )
                    if (currentChatId != null) {
                        append(
                            "\nThe current chat ID is \"$currentChatId\". " +
                                "Use this chat_id with ${ToolDefinition.SEARCH_CHAT.name} after disclosure.",
                        )
                    }
                }
        }
    }
