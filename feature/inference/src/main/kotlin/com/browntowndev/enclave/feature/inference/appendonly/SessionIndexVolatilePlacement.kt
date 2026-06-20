package com.browntowndev.enclave.feature.inference.appendonly

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper
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
            !EngineInternalMessageMapper.isEngineInternalMessage(sessionMessages[index].metadataJson)
        } ?: 0
    }

    fun sidecarMarkersForSessionIndex(
        sessionIndex: Int,
        frozenSidecars: List<FrozenSidecarSnapshot>,
        preRetrievalSidecar: ResolvedSidecar?,
        preRetrievalInsertIndex: Int?,
        liveSidecar: ResolvedSidecar?,
        tailInsertIndex: Int,
    ): List<SidecarMarker> =
        buildList {
            frozenSidecars
                .filter { it.mappedInsertIndex == sessionIndex }
                .forEach { snapshot ->
                    add(SidecarMarker(markerId = snapshot.markerId, body = snapshot.text))
                }
            if (
                preRetrievalSidecar != null &&
                preRetrievalInsertIndex != null &&
                sessionIndex == preRetrievalInsertIndex
            ) {
                add(
                    SidecarMarker(
                        markerId = preRetrievalSidecar.markerId,
                        body = preRetrievalSidecar.text,
                    ),
                )
            }
            if (liveSidecar != null && sessionIndex == tailInsertIndex) {
                add(SidecarMarker(markerId = liveSidecar.markerId, body = liveSidecar.text))
            }
        }

    private fun String.isToolCallMetadata(): Boolean {
        val metadata = toMetadataJsonObject() ?: return false
        return metadata["call_id"]?.jsonPrimitive?.contentOrNull != null
    }

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
