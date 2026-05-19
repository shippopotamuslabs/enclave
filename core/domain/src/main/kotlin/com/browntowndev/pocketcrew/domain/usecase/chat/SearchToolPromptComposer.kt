package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.port.inference.PromptComposerPort
import javax.inject.Inject

class SearchToolPromptComposer
    @Inject
    constructor() : PromptComposerPort {
        override fun compose(
            baseSystemPrompt: String?,
            includeSearchTool: Boolean,
            includeXSearchTool: Boolean,
            includeImageInspectTool: Boolean,
            includeMemoryTools: Boolean,
            includeArtifactTool: Boolean,
            includeImageGenerationTool: Boolean,
            includeVideoGenerationTool: Boolean,
            includeMusicGenerationTool: Boolean,
            currentChatId: String?,
            strategy: ToolCallStrategy,
            coreMemories: List<Memory>,
            retrievedMemories: List<Memory>,
        ): String =
            listOfNotNull(
                formatCoreMemories(coreMemories).takeIf(String::isNotEmpty),
                baseSystemPrompt?.trim()?.takeIf(String::isNotEmpty),
                formatRetrievedMemories(retrievedMemories).takeIf(String::isNotEmpty),
                localToolContract(
                    includeSearchTool = includeSearchTool,
                    includeXSearchTool = includeXSearchTool,
                    includeImageInspectTool = includeImageInspectTool,
                    includeMemoryTools = includeMemoryTools,
                    includeArtifactTool = includeArtifactTool,
                    includeImageGenerationTool = includeImageGenerationTool,
                    includeVideoGenerationTool = includeVideoGenerationTool,
                    includeMusicGenerationTool = includeMusicGenerationTool,
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

            private fun searchToolContract(strategy: ToolCallStrategy): String {
                val extractNote =
                    if (strategy == ToolCallStrategy.LITE_RT_NATIVE) {
                        " Note that 'urls' must be a SINGLE string where you list the URLs separated by commas. " +
                            "For very long pages, only the most relevant first part will be provided to fit the context window:"
                    } else {
                        ""
                    }

                return """
# TOOL USE MANDATE:
You have access to real-time information via '${ToolDefinition.WEB_SEARCH.name}' and '${ToolDefinition.WEB_EXTRACT.name}'. 
If the user's request involves ANY of the following, you MUST call the search tool before answering:
- Current events, news, or recent developments.
- Dates, times, or schedules.
- Verifying facts, statistics, or status of products/companies.
- Anything where your internal knowledge might be stale or incomplete.
- Any terms you don't recognize.

DO NOT ASSUME you know the answer. When in doubt, SEARCH.

To search:
${ToolDefinition.WEB_SEARCH.toExample(strategy)}

If you need to read the full content of a webpage from the search results, use ${ToolDefinition.WEB_EXTRACT.name}.$extractNote
${ToolDefinition.WEB_EXTRACT.toExample(strategy)}
                    """.trimIndent()
            }

            @Suppress("MaxLineLength")
            private fun metadataRules(): String =
                """
# TEMPORAL CONTEXT:
User messages include a `[Metadata: Sent at ...]` or `[Metadata: Current Time: ...]` footer. Use this to understand the current time and the timeline of the conversation. 
IMPORTANT: DO NOT include metadata tags or mentions of the footer in your responses. Respond naturally as if the metadata were invisible context.
                """.trimIndent()

            private fun xSearchToolContract(strategy: ToolCallStrategy): String =
                """
# X (TWITTER) SEARCH MANDATE:
You have access to '${ToolDefinition.X_SEARCH.name}' to search for recent posts on X (Twitter).
If the user's request involves ANY of the following, you SHOULD use this tool:
- Real-time trends or public sentiment on X.
- Recent breaking news or events being discussed on social media.
- Finding specific posts or comments from X users.

To search X:
${ToolDefinition.X_SEARCH.toExample(strategy)}
                """.trimIndent()

            private fun imageInspectContract(strategy: ToolCallStrategy): String =
                """
# IMAGE INSPECTION MANDATE:
When the user asks about a previously attached image, you MUST use '${ToolDefinition.ATTACHED_IMAGE_INSPECT.name}' to get details. 
DO NOT rely on memory if you need specific visual verification.

To inspect:
${ToolDefinition.ATTACHED_IMAGE_INSPECT.toExample(strategy)}
                """.trimIndent()

            @Suppress("MaxLineLength")
            private fun artifactContract(strategy: ToolCallStrategy): String =
                """
# ARTIFACT STUDIO MANDATE (Incremental Generation):
You MUST use the Artifact tools to create professional, structured documents (PDF or Word).
For large documents (reports, plans, books), you MUST generate the document incrementally using this loop:

1. **Initialize**: Call '${ToolDefinition.START_ARTIFACT.name}' first. It returns an 'artifactId' string.
   Example: ${ToolDefinition.START_ARTIFACT.toExample(strategy)}
   You MUST pass that exact 'artifactId' value as the 'artifactId' field in every '${ToolDefinition.APPEND_BLOCKS.name}' call.

2. **Append content**: Call '${ToolDefinition.APPEND_BLOCKS.name}' repeatedly to add content.
   - You MUST emit 10-15 blocks per call. NEVER exceed 15 blocks in a single call.
   - 'blocks' is a JSON array of block objects only (never send the full Document wrapper with version/metadata here).
   - Each block MUST include "type" as one of: heading (needs integer level, text), paragraph (text), list (ordered, items[]), table (header[], rows[][]), code (optional language, text), math (display, latex), image (src, optional alt), page_break (no extra fields).

3. **Finish (mandatory)**: On your FINAL '${ToolDefinition.APPEND_BLOCKS.name}' call you MUST set 'isComplete': true.
   If you skip this step, the finished document will NOT be delivered to the user even if you say you are done.

4. **Recall context**: If you need to recall specific details, names, or sections from earlier parts of the document that are no longer in your immediate context window, call '${ToolDefinition.SEARCH_CURRENT_ARTIFACT.name}'.
   Example: ${ToolDefinition.SEARCH_CURRENT_ARTIFACT.toExample(strategy)}

The tool produces a deterministic interactive preview and allows native PDF/Word export.
                """.trimIndent()

            @Suppress("MaxLineLength")
            private fun memoryToolsContract(
                currentChatId: String?,
                strategy: ToolCallStrategy,
            ): String =
                buildString {
                    append(
                        """
# CONVERSATION MEMORY TOOLS:
You have access to '${ToolDefinition.SEARCH_CHAT_HISTORY.name}', '${ToolDefinition.SEARCH_CHAT.name}', '${ToolDefinition.GET_MESSAGE_CONTEXT.name}', and '${ToolDefinition.MANAGE_MEMORIES.name}' to recall and manage information.

- Use '${ToolDefinition.SEARCH_CHAT_HISTORY.name}' when the user mentions or references a previous conversation, topic, or detail from a past chat. Provide multiple query variants (synonyms, alternate phrasings) to cast a wide net. It returns matching messages with surrounding context.
- Use '${ToolDefinition.SEARCH_CHAT.name}' when you need to search messages within a specific chat. You must provide the chat_id (from ${ToolDefinition.SEARCH_CHAT_HISTORY.name} results or the current chat ID noted below).
- Use '${ToolDefinition.GET_MESSAGE_CONTEXT.name}' when you found a relevant message but need more surrounding context to understand the conversation flow. Provide the message_id and how many messages before/after you need.
- Use '${ToolDefinition.MANAGE_MEMORIES.name}' to save, update, delete, or search long-term memories. Use this for facts about the user, project context, or important details that should persist across all conversations.
                        """.trimIndent(),
                    )

                    if (strategy == ToolCallStrategy.LITE_RT_NATIVE) {
                        append("\n\nFor '${ToolDefinition.SEARCH_CHAT_HISTORY.name}', provide queries as a single comma-separated string.")
                    }

                    if (currentChatId != null) {
                        append(
                            "\n\nThe current chat ID is \"" + currentChatId + "\". " +
                                "Use this ID with ${ToolDefinition.SEARCH_CHAT.name} to search messages in the current conversation.",
                        )
                    }
                    append(
                        """

To search past chats:
${ToolDefinition.SEARCH_CHAT_HISTORY.toExample(strategy)}

To search messages in a specific chat:
${ToolDefinition.SEARCH_CHAT.toExample(strategy)}

To get surrounding context for a message:
${ToolDefinition.GET_MESSAGE_CONTEXT.toExample(strategy)}

To manage long-term memories:
${ToolDefinition.MANAGE_MEMORIES.toExample(strategy)}
                        """.trimIndent(),
                    )
                }

            private fun mediaGenerationContract(
                includeImage: Boolean,
                includeVideo: Boolean,
                includeMusic: Boolean,
                strategy: ToolCallStrategy,
            ): String =
                buildString {
                    append("# MEDIA GENERATION MANDATE:\n")
                    append(
                        "You have access to professional media generation tools. " +
                            "Use these when the user asks you to create, generate, draw, or compose original content.\n\n",
                    )

                    if (includeImage) {
                        append(
                            "- Use '${ToolDefinition.GENERATE_IMAGE.name}' to generate images, " +
                                "illustrations, or pictures from text descriptions.\n",
                        )
                        append("To generate an image:\n")
                        append(ToolDefinition.GENERATE_IMAGE.toExample(strategy))
                        append("\n\n")
                    }

                    if (includeVideo) {
                        append(
                            "- Use '${ToolDefinition.GENERATE_VIDEO.name}' to generate short videos " +
                                "or animations from text descriptions.\n",
                        )
                        append("To generate a video:\n")
                        append(ToolDefinition.GENERATE_VIDEO.toExample(strategy))
                        append("\n\n")
                    }

                    if (includeMusic) {
                        append(
                            "- Use '${ToolDefinition.GENERATE_MUSIC.name}' to generate music, beats, or soundtracks from text descriptions.\n",
                        )
                        append("To generate music:\n")
                        append(ToolDefinition.GENERATE_MUSIC.toExample(strategy))
                        append("\n\n")
                    }

                    append(
                        "IMPORTANT: After calling any media tool, inform the user that the media has been generated " +
                            "and is available in their Studio. They can tap the generation card in the chat to open it.",
                    )
                }

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

            fun localToolContract(
                includeSearchTool: Boolean = true,
                includeXSearchTool: Boolean = false,
                includeImageInspectTool: Boolean = false,
                includeMemoryTools: Boolean = true,
                includeArtifactTool: Boolean = false,
                includeImageGenerationTool: Boolean = false,
                includeVideoGenerationTool: Boolean = false,
                includeMusicGenerationTool: Boolean = false,
                currentChatId: String? = null,
                strategy: ToolCallStrategy = ToolCallStrategy.JSON_XML_ENVELOPE,
            ): String {
                val noToolsIncluded =
                    !includeSearchTool &&
                        !includeXSearchTool &&
                        !includeImageInspectTool &&
                        !includeMemoryTools &&
                        !includeArtifactTool &&
                        !includeImageGenerationTool &&
                        !includeVideoGenerationTool &&
                        !includeMusicGenerationTool
                if (noToolsIncluded) return ""
                if (strategy == ToolCallStrategy.SDK_NATIVE) return ""

                return buildString {
                    append(metadataRules())
                    append("\n\n")
                    if (includeSearchTool) {
                        append(searchToolContract(strategy))
                    }
                    if (includeXSearchTool) {
                        if (isNotEmpty()) append("\n\n")
                        append(xSearchToolContract(strategy))
                    }
                    if (includeImageInspectTool) {
                        if (isNotEmpty()) append("\n\n")
                        append(imageInspectContract(strategy))
                    }
                    if (includeMemoryTools) {
                        if (isNotEmpty()) append("\n\n")
                        append(memoryToolsContract(currentChatId, strategy))
                    }
                    if (includeArtifactTool) {
                        if (isNotEmpty()) append("\n\n")
                        append(artifactContract(strategy))
                    }
                    if (includeImageGenerationTool || includeVideoGenerationTool || includeMusicGenerationTool) {
                        if (isNotEmpty()) append("\n\n")
                        append(
                            mediaGenerationContract(
                                includeImage = includeImageGenerationTool,
                                includeVideo = includeVideoGenerationTool,
                                includeMusic = includeMusicGenerationTool,
                                strategy = strategy,
                            ),
                        )
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
