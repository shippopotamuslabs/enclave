package com.browntowndev.enclave.core.data.profile.parse

import com.browntowndev.enclave.domain.model.profile.ConversationMessage
import com.browntowndev.enclave.domain.model.profile.ConversationProvider
import com.browntowndev.enclave.domain.model.profile.ConversationRecord
import com.browntowndev.enclave.domain.model.profile.MessageRole
import com.browntowndev.enclave.domain.model.profile.ParseHealthReport
import com.browntowndev.enclave.domain.port.profile.ConversationExportParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Parses OpenAI's `conversations.json` export format.
 *
 * Schema: top-level array of conversation objects. Each conversation has a `mapping` dict
 * (node_id → node) plus a `current_node` pointer. Messages are reconstructed by walking
 * parent links from `current_node` up to the root and reversing.
 *
 * Parser is tolerant: missing fields are skipped, not thrown.
 */
@Suppress("ReturnCount")
class OpenAiExportParser
    @javax.inject.Inject
    constructor() : ConversationExportParser {
    override val provider = ConversationProvider.OPENAI

    override fun canHandle(entryName: String, contentPreview: String): Boolean =
        entryName.endsWith("conversations.json") &&
            contentPreview.trimStart().startsWith("[") &&
            contentPreview.contains("\"mapping\"")

    override fun parse(entryName: String, content: String): Pair<List<ConversationRecord>, ParseHealthReport> {
        val topLevel = runCatching { parserJson.parseToJsonElement(content) }.getOrNull()
            ?: return emptyList<ConversationRecord>() to emptyReport(0)

        val array = when (topLevel) {
            is JsonArray -> topLevel
            else -> return emptyList<ConversationRecord>() to emptyReport(0)
        }

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
        val id = obj.stringOrNull("id") ?: obj.stringOrNull("uuid") ?: return null
        val title = obj.stringOrNull("title") ?: "Untitled"
        val createdAt = obj.doubleOrNull("create_time")?.let { (it * 1000).toLong() }
        val mapping = obj["mapping"] as? JsonObject ?: return null
        val currentNode = obj.stringOrNull("current_node") ?: return null

        val path = walkPath(mapping, currentNode)
        val messages = path.mapIndexedNotNull { index, nodeId ->
            extractMessage(mapping[nodeId] as? JsonObject, index)
        }

        return ConversationRecord(
            externalId = id,
            title = title,
            provider = provider,
            createdAtMs = createdAt,
            messages = messages,
        )
    }

    private fun walkPath(mapping: JsonObject, leafNodeId: String): List<String> {
        val path = mutableListOf<String>()
        var current: String? = leafNodeId
        val visited = mutableSetOf<String>()
        while (current != null && current !in visited) {
            visited.add(current)
            path.add(current)
            current = (mapping[current] as? JsonObject)?.stringOrNull("parent")
        }
        path.reverse()
        return path
    }

    private fun extractMessage(node: JsonObject?, orderIndex: Int): ConversationMessage? {
        val message = node?.get("message") as? JsonObject ?: return null
        val role = (message["author"] as? JsonObject)?.stringOrNull("role")
            ?: message.stringOrNull("role")
            ?: return null
        if (role == "system" || role == "tool") return null

        val content = extractContent(message["content"]) ?: return null
        if (content.isBlank()) return null

        val createdAt = message.doubleOrNull("create_time")?.let { (it * 1000).toLong() }

        return ConversationMessage(
            role = role.toMessageRole(),
            content = content,
            createdAtMs = createdAt,
            orderIndex = orderIndex,
        )
    }

    private fun extractContent(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        return when (element) {
            is JsonObject -> {
                when (element.stringOrNull("content_type")) {
                    "text" -> (element["parts"] as? JsonArray)?.joinToString("") { part ->
                        when (part) {
                            is JsonPrimitive -> part.contentOrNull ?: ""
                            else -> ""
                        }
                    }
                    "multimodal_text" -> (element["parts"] as? JsonArray)?.joinToString("") { part ->
                        when (part) {
                            is JsonPrimitive -> part.contentOrNull ?: ""
                            is JsonObject -> part.stringOrNull("text") ?: ""
                            else -> ""
                        }
                    }
                    "code" -> element.stringOrNull("text")
                    else -> element.stringOrNull("text") ?: element.stringOrNull("content")
                }
            }
            is JsonPrimitive -> element.contentOrNull
            else -> null
        }
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

private fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun String.toMessageRole() = when (lowercase()) {
    "user" -> MessageRole.USER
    "assistant" -> MessageRole.ASSISTANT
    "system" -> MessageRole.SYSTEM
    "tool" -> MessageRole.TOOL
    else -> MessageRole.UNKNOWN
}
