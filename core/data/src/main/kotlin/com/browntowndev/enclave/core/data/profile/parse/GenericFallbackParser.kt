package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationMessage
import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.ConversationRecord
import com.browntowndev.enclave.domain.model.profile.MessageRole
import com.browntowndev.enclave.domain.model.profile.ParseHealthReport
import com.browntowndev.enclave.domain.port.profile.ConversationExportParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Last-resort parser for JSON exports with no matching provider parser.
 *
 * Strategy: walk the top-level structure looking for arrays of objects that appear to be
 * conversation turns (have role-like and text-like fields). Degrades gracefully — produces
 * partial signal rather than zero.
 */
class GenericFallbackParser
    @javax.inject.Inject
    constructor() : ConversationExportParser {
    override val provider = ConversationProvider.UNKNOWN

    override fun canHandle(entryName: String, contentPreview: String): Boolean =
        entryName.endsWith(".json") && contentPreview.trimStart().startsWith("[")

    override fun parse(entryName: String, content: String): Pair<List<ConversationRecord>, ParseHealthReport> {
        val topLevel = runCatching { parserJson.parseToJsonElement(content) }.getOrNull()
            ?: return emptyList<ConversationRecord>() to emptyReport(0)

        val array = topLevel as? JsonArray ?: return emptyList<ConversationRecord>() to emptyReport(0)

        val totalEntries = array.size
        val records = mutableListOf<ConversationRecord>()
        var totalMessages = 0
        var messagesWithText = 0
        var skipped = 0

        for ((idx, element) in array.withIndex()) {
            val obj = element as? JsonObject ?: run { skipped++; continue }
            runCatching {
                val record = tryParseAsConversation(obj, idx.toString())
                if (record != null && record.messages.isNotEmpty()) {
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

    private fun tryParseAsConversation(obj: JsonObject, fallbackId: String): ConversationRecord? {
        val id = KNOWN_ID_KEYS.firstNotNullOfOrNull { obj.stringOrNull(it) } ?: fallbackId
        val title = KNOWN_TITLE_KEYS.firstNotNullOfOrNull { obj.stringOrNull(it) } ?: "Untitled"
        val createdAt = KNOWN_DATE_KEYS.firstNotNullOfOrNull { obj.stringOrNull(it) }?.parseIso8601Ms()

        // Try to find a messages array in any known key
        val messagesArray = KNOWN_MESSAGES_KEYS
            .firstNotNullOfOrNull { key -> obj[key]?.let { it as? JsonArray } }
            ?: return null

        val messages = messagesArray.mapIndexedNotNull { index, msgEl ->
            (msgEl as? JsonObject)?.let { tryExtractMessage(it, index) }
        }

        return ConversationRecord(
            externalId = id,
            title = title,
            provider = provider,
            createdAtMs = createdAt,
            messages = messages,
        )
    }

    private fun tryExtractMessage(obj: JsonObject, orderIndex: Int): ConversationMessage? {
        val role = KNOWN_ROLE_KEYS.firstNotNullOfOrNull { obj.stringOrNull(it) } ?: return null
        val text = KNOWN_TEXT_KEYS.firstNotNullOfOrNull { obj.stringOrNull(it) } ?: return null
        if (text.isBlank()) return null

        return ConversationMessage(
            role = role.toGenericRole(),
            content = text,
            createdAtMs = KNOWN_DATE_KEYS.firstNotNullOfOrNull { obj.stringOrNull(it) }?.parseIso8601Ms(),
            orderIndex = orderIndex,
        )
    }

    private fun emptyReport(total: Int) = ParseHealthReport(
        provider = provider,
        totalEntries = total,
        extractedConversations = 0,
        totalMessages = 0,
        messagesWithText = 0,
        skippedMalformed = total,
    )

    companion object {
        private val KNOWN_ID_KEYS = listOf("id", "uuid", "conversation_id")
        private val KNOWN_TITLE_KEYS = listOf("title", "name", "subject", "topic")
        private val KNOWN_DATE_KEYS = listOf("created_at", "create_time", "timestamp", "date")
        private val KNOWN_MESSAGES_KEYS = listOf("messages", "chat_messages", "turns", "history", "conversation")
        private val KNOWN_ROLE_KEYS = listOf("role", "sender", "author", "from", "type")
        private val KNOWN_TEXT_KEYS = listOf("text", "content", "message", "body", "value")
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun String.toGenericRole() = when (lowercase()) {
    "user", "human", "person" -> MessageRole.USER
    "assistant", "ai", "bot", "model", "system" -> MessageRole.ASSISTANT
    else -> MessageRole.UNKNOWN
}

private fun String.parseIso8601Ms(): Long? = runCatching {
    java.time.Instant.parse(this).toEpochMilli()
}.getOrNull()
