package com.browntowndev.enclave.feature.inference.appendonly

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pi-aligned FNV-style rolling digest over normalized message fields.
 */
internal object ProviderMessageDigest {
    fun compute(messages: List<ProviderNormalizedMessage>): UInt {
        var hash = 0
        for (message in messages) {
            val payload =
                Json.encodeToString(
                    JsonObject.serializer(),
                    message.digestPayload(),
                )
            for (char in payload) {
                hash = (hash shl 5) - hash + char.code
            }
        }
        return hash.toUInt()
    }

    private fun ProviderNormalizedMessage.digestPayload(): JsonObject =
        buildJsonObject {
            put("r", role)
            put("c", content)
            put("tc", toolCalls)
            put("tcid", toolCallId)
            put("n", name)
            put("id", id)
        }
}
