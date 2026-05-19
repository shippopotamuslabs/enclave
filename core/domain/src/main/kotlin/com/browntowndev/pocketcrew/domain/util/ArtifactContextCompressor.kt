package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Utility for compressing chat history during an incremental artifact generation loop.
 * It replaces bulky APPEND_BLOCKS tool results with a concise summary of progress
 * to stay within context window limits.
 */
object ArtifactContextCompressor {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compresses the provided history by squashing consecutive APPEND_BLOCKS calls.
     * Retains the literal text of the very last block for continuity.
     */
    @Suppress("NestedBlockDepth")
    fun compress(history: List<ChatMessage>): List<ChatMessage> {
        val compressed = mutableListOf<ChatMessage>()
        var appendBlocksFound = false
        var lastBlockText: String? = null
        var totalBlocks = 0

        // Find the most recent append_blocks result to get the last block text
        for (i in history.indices.reversed()) {
            val message = history[i]
            if (message.role == Role.USER) {
                try {
                    val payload = json.parseToJsonElement(message.content).jsonObject
                    if (payload["status"]?.jsonPrimitive?.content == "success" &&
                        payload.containsKey("totalBlocks")
                    ) {
                        if (!appendBlocksFound) {
                            appendBlocksFound = true
                            totalBlocks = payload["totalBlocks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                            // Try to extract the last block text from the arguments of the matching assistant message
                            // if it's not in the result itself.
                            // Actually, let's look at the assistant message BEFORE this result.
                            if (i > 0) {
                                val assistantMessage = history[i - 1]
                                if (assistantMessage.role == Role.ASSISTANT) {
                                    lastBlockText = extractLastBlockTextFromAssistant(assistantMessage.content)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Not a JSON result or doesn't match artifact pattern
                }
            }
        }

        if (!appendBlocksFound) return history

        // Build the compressed history
        var inAppendLoop = false
        for (i in history.indices) {
            val message = history[i]
            val isAppendBlocks = isAppendBlocksMessage(message)

            if (isAppendBlocks) {
                if (!inAppendLoop) {
                    inAppendLoop = true
                    // This is where we will insert the summary later
                }
                continue // Skip these messages
            } else {
                if (inAppendLoop) {
                    // Loop ended, insert summary
                    val summary = buildSummary(totalBlocks, lastBlockText)
                    compressed.add(ChatMessage(Role.SYSTEM, summary))
                    inAppendLoop = false
                }
                compressed.add(message)
            }
        }

        return compressed
    }

    private fun isAppendBlocksMessage(message: ChatMessage): Boolean {
        // Catch both the tool call and the success result
        return message.content.contains(ToolDefinition.APPEND_BLOCKS.name) ||
            (message.content.contains("\"totalBlocks\"") && message.content.contains("\"success\""))
    }

    private fun extractLastBlockTextFromAssistant(content: String): String? {
        return try {
            // This is complex because different providers format tool calls differently.
            // For now, we'll try a regex to find the blocks array and get the last text field.
            val blocksMatch = Regex("\"blocks\"\\s*:\\s*(\\[.*?\\])", RegexOption.DOT_MATCHES_ALL).find(content)
            val blocksJson = blocksMatch?.groupValues?.get(1) ?: return null
            val blocks = json.parseToJsonElement(blocksJson).jsonArray
            val lastBlock = blocks.lastOrNull()?.jsonObject ?: return null

            // Handle different block types (heading, paragraph, etc.)
            lastBlock["text"]?.jsonPrimitive?.content
                ?: lastBlock["items"]
                    ?.jsonArray
                    ?.lastOrNull()
                    ?.jsonPrimitive
                    ?.content
                ?: lastBlock["latex"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSummary(
        totalBlocks: Int,
        lastBlockText: String?,
    ): String =
        buildString {
            append("System Note: Previous document blocks have been saved to the local artifact database. ")
            append("You have generated $totalBlocks blocks so far. ")
            if (lastBlockText != null) {
                append("The literal text of your last generated block was: \"$lastBlockText\". ")
                append("CONTINUE your generation seamlessly from this exact point, starting with the very next logical block. ")
            } else {
                append("Continue your generation from where you left off. ")
            }
            append("Do not repeat any previously generated content.")
        }
}
