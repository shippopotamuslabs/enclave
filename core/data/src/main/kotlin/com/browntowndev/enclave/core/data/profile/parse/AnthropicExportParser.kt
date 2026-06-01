package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationMessage
import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.ConversationRecord
import com.browntowndev.enclave.domain.model.profile.MessageRole
import com.browntowndev.enclave.domain.model.profile.ParseHealthReport
import com.browntowndev.enclave.domain.port.profile.ConversationExportParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Parses Anthropic's `conversations.json` export format.
 *
 * Schema: top-level array of conversations. Each conversation has:
 *   - `uuid` or `id` for the conversation ID
 *   - `name` for the title
 *   - `created_at` ISO-8601 timestamp
 *   - `chat_messages` array with `sender` ("human"/"assistant") and `text` fields
 *
 * Multi-path extraction: tries multiple known field names for resilience to schema changes.
 */
class AnthropicExportParser
    @javax.inject.Inject
    constructor() : ConversationExportParser {
    override val provider = ConversationProvider.ANTHROPIC

    override fun canHandle(entryName: String, contentPreview: String): Boolean =
        entryName.endsWith("conversations.json") &&
            contentPreview.trimStart().startsWith("[") &&
            (contentPreview.contains("\"chat_messages\"") || contentPreview.contains("\"sender\""))

    override fun parse(entryName: String, content: String): Pair<List<ConversationRecord>, ParseHealthReport> {
        val topLevel = runCatching { parserJson.parseToJsonElement(content) }.getOrNull()
            ?: return emptyList<ConversationRecord>() to emptyReport(0)

        val array = topLevel as? JsonArray
            ?: return emptyList<ConversationRecord>() to emptyReport(0)

        val totalEntries = array.size
        val records = mutableListOf<ConversationRecord>()
        var totalMessages = 0
        var messagesWithText = 0
        var skipped = 0

        for (element in array) {
            val obj = element as? JsonObject ?: run { skipped++; continue }
            runCatching {
                val record = parseConversation(obj)
                if (record != null) {
                    records.add(record)
                    totalMessages += record.messages.size
                    messagesWithText += record.messages.count { it.content.isNotBlank() }
                } else {
                    skipped++
                }
            }.onFailure { skipped++ }
        }

        return records to ParseHealthReport(
            provider = provider,
            totalEntries = totalEntries,
            extractedConversations = records.size,
            totalMessages = totalMessages,
            messagesWithText = messagesWithText,
            skippedMalformed = skipped,
        )
    }

    private fun parseConversation(obj: JsonObject): ConversationRecord? {
        // Multi-path: try uuid, id
        val id = obj.stringOrNull("uuid") ?: obj.stringOrNull("id") ?: return null
        // Multi-path: name, title
        val title = obj.stringOrNull("name") ?: obj.stringOrNull("title") ?: "Untitled"
        val createdAt = obj.stringOrNull("created_at")?.parseIso8601Ms()

        // Multi-path: chat_messages, messages
        val messagesArray = (obj["chat_messages"] ?: obj["messages"]) as? JsonArray
            ?: return null

        val messages = messagesArray.mapIndexedNotNull { index, msgElement ->
            (msgElement as? JsonObject)?.let { extractMessage(it, index) }
        }

        return ConversationRecord(
            externalId = id,
            title = title,
            provider = provider,
            createdAtMs = createdAt,
            messages = messages,
        )
    }

    private fun extractMessage(obj: JsonObject, orderIndex: Int): ConversationMessage? {
        // Multi-path: sender ("human"/"assistant"), role
        val sender = obj.stringOrNull("sender") ?: obj.stringOrNull("role") ?: return null
        // Multi-path: text, content
        val text = obj.stringOrNull("text") ?: obj.stringOrNull("content")
            ?: extractContentBlocks(obj)
            ?: return null
        if (text.isBlank()) return null

        val createdAt = obj.stringOrNull("created_at")?.parseIso8601Ms()

        return ConversationMessage(
            role = sender.anthropicToRole(),
            content = text,
            createdAtMs = createdAt,
            orderIndex = orderIndex,
        )
    }

    private fun extractContentBlocks(obj: JsonObject): String? {
        // Newer Anthropic format may use content array with {type, text} blocks
        val blocks = obj["content"] as? JsonArray ?: return null
        return blocks.mapNotNull { block ->
            (block as? JsonObject)?.let {
                if (it.stringOrNull("type") == "text") it.stringOrNull("text") else null
            }
        }.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun emptyReport(total: Int) = ParseHealthReport(
        provider = provider,
        totalEntries = total,
        extractedConversations = 0,
        totalMessages = 0,
        messagesWithText = 0,
        skippedMalformed = total,
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun String.anthropicToRole() = when (lowercase()) {
    "human" -> MessageRole.USER
    "user" -> MessageRole.USER
    "assistant" -> MessageRole.ASSISTANT
    else -> MessageRole.UNKNOWN
}

private fun String.parseIso8601Ms(): Long? = runCatching {
    java.time.Instant.parse(this).toEpochMilli()
}.getOrNull()
