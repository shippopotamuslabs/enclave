package com.browntowndev.enclave.feature.inference.appendonly

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.feature.inference.openai.PreRetrievalContextSidecar
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Session-index sidecar placement for providers that map [AgentLoopMessage] rows directly
 * (Anthropic, Google) instead of OpenAI mapped chat-completion indices.
 */
internal object SessionIndexVolatilePlacement {
    fun resolvePreRetrievalInsertIndex(sessionMessages: List<AgentLoopMessage>): Int? {
        val userSessionIndex =
            sessionMessages.indexOfLast { message ->
                message.role == Role.USER && !message.metadataJson.isToolCallMetadata()
            }
        return userSessionIndex.takeIf { it >= 0 }
    }

    fun resolveTailInsertIndex(sessionMessages: List<AgentLoopMessage>): Int {
        if (sessionMessages.isEmpty()) return 0
        return sessionMessages.indices.lastOrNull { index ->
            val metadata = sessionMessages[index].metadataJson.toMetadataJsonObject()
            !metadata.isEngineInternalToolSidecar()
        } ?: 0
    }

    fun sidecarTextsBeforeSessionIndex(
        sessionIndex: Int,
        frozenSidecars: List<FrozenSidecarSnapshot>,
        preRetrievalSidecarText: String?,
        preRetrievalInsertIndex: Int?,
        currentSidecarText: String?,
        tailInsertIndex: Int,
    ): List<String> =
        buildList {
            frozenSidecars
                .filter { it.mappedInsertIndex == sessionIndex }
                .forEach { add(it.text) }
            if (
                !preRetrievalSidecarText.isNullOrBlank() &&
                preRetrievalInsertIndex != null &&
                sessionIndex == preRetrievalInsertIndex
            ) {
                add(preRetrievalSidecarText)
            }
            if (!currentSidecarText.isNullOrBlank() && sessionIndex == tailInsertIndex) {
                add(currentSidecarText)
            }
        }

    private fun String.isToolCallMetadata(): Boolean {
        val metadata = toMetadataJsonObject() ?: return false
        return metadata["call_id"]?.jsonPrimitive?.contentOrNull != null
    }

    private fun JsonObject?.isEngineInternalToolSidecar(): Boolean =
        this != null &&
            (
                containsKey("agent_tool_budget") ||
                    containsKey("tools_blocked_nudge") ||
                    containsKey("envelope_parse_failure")
            )

    private fun String.toMetadataJsonObject(): JsonObject? =
        if (isBlank() || this == "null") {
            null
        } else {
            runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull()
        }
}

internal fun ProviderAppendOnlyContextFactory.ensureAppendOnlyContext(
    current: ProviderAppendOnlyContext?,
    onLogCleared: (String) -> Unit,
): ProviderAppendOnlyContext =
    current ?: ProviderAppendOnlyContextFactory.create(onLogCleared = onLogCleared)
