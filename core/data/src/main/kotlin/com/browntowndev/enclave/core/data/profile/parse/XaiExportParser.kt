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
import java.io.Reader
import java.io.StringReader

/**
 * Parses xAI Grok conversation exports (prod-grok-backend.json format).
 *
 * Top-level: {"conversations":[...]} — object wrapping an array.
 * Each conversations item: {"conversation":{id, title, create_time, anon_user_id, ...}, "responses":[...]}
 * Each response item: {"response":{_id, message, sender, create_time, model}, "share_link":...}
 * Response create_time uses MongoDB extended JSON: {"$date":{"$numberLong":"epoch-ms-as-string"}}
 *
 * Streaming: reads one conversation object at a time via JsonObjectChunker. beginArray() scans
 * past the {"conversations":[ prefix to the array, then nextObject() yields each item.
 * Memory is O(largest single conversation), not O(total file size).
 */
class XaiExportParser
    @javax.inject.Inject
    constructor() : ConversationExportParser {
        override val provider = ConversationProvider.XAI

        override fun canHandle(
            entryName: String,
            contentPreview: String,
        ): Boolean =
            entryName.endsWith(".json") &&
                contentPreview.trimStart().startsWith("{") &&
                contentPreview.contains("\"conversations\"") &&
                contentPreview.contains("\"anon_user_id\"")

        override fun parse(
            entryName: String,
            content: String,
        ): Pair<List<ConversationRecord>, ParseHealthReport> = parseReader(entryName, StringReader(content))

        override fun parseReader(
            entryName: String,
            reader: Reader,
        ): Pair<List<ConversationRecord>, ParseHealthReport> {
            val records = mutableListOf<ConversationRecord>()
            var totalEntries = 0
            var totalMessages = 0
            var messagesWithText = 0
            var skipped = 0

            runCatching {
                val chunker = JsonObjectChunker(reader)
                // beginArray() scans forward until it finds '[', which lands on the conversations array
                if (!chunker.beginArray()) return emptyList<ConversationRecord>() to emptyReport(0)
                var chunk = chunker.nextObject()
                while (chunk != null) {
                    totalEntries++
                    runCatching {
                        val obj = parserJson.parseToJsonElement(chunk) as? JsonObject
                        if (obj != null) {
                            val record = parseConversation(obj)
                            if (record != null) {
                                records.add(record)
                                totalMessages += record.messages.size
                                messagesWithText += record.messages.count { it.content.isNotBlank() }
                            } else {
                                skipped++
                            }
                        } else {
                            skipped++
                        }
                    }.onFailure { skipped++ }
                    chunk = chunker.nextObject()
                }
            }

            return records to
                ParseHealthReport(
                    provider = provider,
                    totalEntries = totalEntries,
                    extractedConversations = records.size,
                    totalMessages = totalMessages,
                    messagesWithText = messagesWithText,
                    skippedMalformed = skipped,
                )
        }

        private fun parseConversation(obj: JsonObject): ConversationRecord? {
            val convObj = obj["conversation"] as? JsonObject ?: return null
            val id = convObj.xaiString("id") ?: return null
            val title = convObj.xaiString("title") ?: "Untitled"
            val createdAtMs = convObj.xaiString("create_time")?.parseIso8601Ms()

            val responsesArray = obj["responses"] as? JsonArray ?: return null
            val messages = mutableListOf<ConversationMessage>()

            responsesArray.forEachIndexed { index, el ->
                val wrapper = el as? JsonObject ?: return@forEachIndexed
                val responseObj = wrapper["response"] as? JsonObject ?: return@forEachIndexed
                val msg = parseMessage(responseObj, index) ?: return@forEachIndexed
                messages.add(msg)
            }

            return ConversationRecord(
                externalId = id,
                title = title,
                provider = provider,
                createdAtMs = createdAtMs,
                messages = messages,
            )
        }

        private fun parseMessage(
            obj: JsonObject,
            orderIndex: Int,
        ): ConversationMessage? {
            val role = obj.xaiString("sender") ?: return null
            val content = obj.xaiString("message") ?: return null
            if (content.isBlank()) return null

            val normalizedRole = role.lowercase()
            if (normalizedRole == "system") return null

            val createdAtMs = obj.mongoDateMs("create_time")

            return ConversationMessage(
                role = normalizedRole.toXaiRole(),
                content = content,
                createdAtMs = createdAtMs,
                orderIndex = orderIndex,
            )
        }

        private fun emptyReport(total: Int) =
            ParseHealthReport(
                provider = provider,
                totalEntries = total,
                extractedConversations = 0,
                totalMessages = 0,
                messagesWithText = 0,
                skippedMalformed = total,
            )
    }

private fun JsonObject.xaiString(key: String): String? =
    when (val el = this[key]) {
        null -> null
        is JsonPrimitive -> el.contentOrNull
        is JsonArray ->
            el
                .filterIsInstance<JsonObject>()
                .mapNotNull { (it["text"] as? JsonPrimitive)?.contentOrNull }
                .joinToString("\n")
                .takeIf { it.isNotBlank() }
        else -> null
    }

/**
 * Extracts epoch-ms from a plain-string ISO-8601 timestamp or MongoDB extended JSON:
 * {"$date":{"$numberLong":"<epoch-ms>"}}
 */
private fun JsonObject.mongoDateMs(key: String): Long? {
    val el = this[key] ?: return null
    if (el is JsonPrimitive) return el.contentOrNull?.parseIso8601Ms()
    if (el is JsonObject) {
        val dateEl = el["\$date"]
        if (dateEl is JsonObject) return (dateEl["\$numberLong"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        if (dateEl is JsonPrimitive) return dateEl.contentOrNull?.toLongOrNull() ?: dateEl.contentOrNull?.parseIso8601Ms()
    }
    return null
}

private fun String.toXaiRole(): MessageRole =
    when (lowercase()) {
        "user", "human" -> MessageRole.USER
        "assistant", "ai", "bot" -> MessageRole.ASSISTANT
        "system" -> MessageRole.SYSTEM
        else -> MessageRole.UNKNOWN
    }

private fun String.parseIso8601Ms(): Long? =
    runCatching {
        java.time.Instant
            .parse(this)
            .toEpochMilli()
    }.getOrNull()

/**
 * Streams a top-level JSON array, yielding one complete object string per [nextObject] call.
 * Parses only enough to find object boundaries — never holds more than one object in memory.
 */
internal class JsonObjectChunker(
    private val reader: Reader,
) {
    private val cb = CharArray(DEFAULT_BUFFER_SIZE)
    private var cbLen = 0
    private var cbPos = 0
    private var done = false

    fun beginArray(): Boolean {
        while (true) {
            val c = nextChar() ?: return false
            if (c == '[') return true
        }
    }

    fun nextObject(): String? {
        while (true) {
            val c = nextChar() ?: return null
            when (c) {
                '{' -> return readObject()
                ']' -> return null
            }
        }
    }

    private fun readObject(): String {
        val sb = StringBuilder()
        sb.append('{')
        var depth = 1
        var inString = false
        var escaped = false

        while (depth > 0) {
            val c = nextChar() ?: break
            sb.append(c)
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> depth--
            }
        }
        return sb.toString()
    }

    private fun nextChar(): Char? {
        if (cbPos >= cbLen) {
            cbLen = reader.read(cb)
            cbPos = 0
            if (cbLen == -1) {
                done = true
                return null
            }
        }
        return cb[cbPos++]
    }
}
