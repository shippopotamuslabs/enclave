package com.browntowndev.enclave.feature.inference.appendonly

import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam

/**
 * Embeds volatile sidecars as UUID-stamped `[Sidecar-<uuid>:]` suffix
 * annotations on `role: user` message content (INV-1, INV-2, INV-4).
 * No `role: system` sidecar messages are emitted on OpenAI-compatible wire.
 */
internal object OpenAiCompatibleVolatilePlacement {
    /**
     * Index in [mappedBody] where the live volatile sidecar is inserted
     * (appended to that user message's content). Must use mapped indices.
     */
    fun tailMappedInsertIndex(mappedBodySize: Int, isToolResult: Boolean = false): Int =
        if (mappedBodySize == 0) {
            0
        } else if (isToolResult) {
            mappedBodySize
        } else {
            (mappedBodySize - 1).coerceAtLeast(0)
        }

    fun embedSidecarsIntoUserMessages(
        mappedBody: List<ChatCompletionMessageParam>,
        frozenSidecars: List<FrozenSidecarSnapshot>,
        preRetrievalSidecar: ResolvedSidecar?,
        preRetrievalInsertIndex: Int?,
        liveSidecar: ResolvedSidecar?,
        tailInsertIndex: Int,
    ): List<ChatCompletionMessageParam> {
        if (mappedBody.isEmpty()) return mappedBody

        // 1. Identify all real user message indices in mappedBody.
        val realUserIndices = mappedBody.indices.filter { index ->
            isRealUserMessage(mappedBody[index])
        }

        // If no user messages exist, we cannot embed sidecars.
        if (realUserIndices.isEmpty()) return mappedBody

        // Helper to anchor any target index to the last real user message at or before that index.
        fun findUserAnchorIndex(targetIndex: Int): Int {
            val anchor = realUserIndices.lastOrNull { it <= targetIndex }
            return anchor ?: realUserIndices.first()
        }

        // Group markers by their re-anchored user message index.
        val markersByUserIndex = mutableMapOf<Int, MutableList<SidecarMarker>>()
        fun addMarker(userIndex: Int, marker: SidecarMarker) {
            markersByUserIndex.getOrPut(userIndex) { mutableListOf() }.add(marker)
        }

        // Add frozen markers in order.
        for (snapshot in frozenSidecars) {
            val anchorIndex = findUserAnchorIndex(snapshot.mappedInsertIndex)
            addMarker(anchorIndex, SidecarMarker(snapshot.markerId, snapshot.text))
        }

        // Add pre-retrieval marker.
        if (preRetrievalSidecar != null && preRetrievalInsertIndex != null) {
            val anchorIndex = findUserAnchorIndex(preRetrievalInsertIndex)
            addMarker(anchorIndex, SidecarMarker(preRetrievalSidecar.markerId, preRetrievalSidecar.text))
        }

        // Add live sidecar marker.
        if (liveSidecar != null) {
            val anchorIndex = findUserAnchorIndex(tailInsertIndex)
            addMarker(anchorIndex, SidecarMarker(liveSidecar.markerId, liveSidecar.text))
        }

        // Rebuild mappedBody with embedded sidecars.
        return mappedBody.mapIndexed { index, param ->
            val markers = markersByUserIndex[index]
            if (markers != null && markers.isNotEmpty()) {
                embedMarkersIntoUserParam(param, markers)
            } else {
                param
            }
        }
    }

    private fun isRealUserMessage(param: ChatCompletionMessageParam): Boolean {
        if (!param.isUser()) return false
        val content = param.asUser().content()
        return if (content.isText()) {
            val text = content.asText()
            !com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper.isEngineInternalWireText(text)
        } else if (content.isArrayOfContentParts()) {
            val firstText = content.asArrayOfContentParts().firstOrNull { it.isText() }?.asText()?.text()
            if (firstText != null) {
                !com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper.isEngineInternalWireText(firstText)
            } else {
                true
            }
        } else {
            true
        }
    }

    private fun embedMarkersIntoUserParam(
        param: ChatCompletionMessageParam,
        markers: List<SidecarMarker>,
    ): ChatCompletionMessageParam {
        val userParam = param.asUser()
        val content = userParam.content()
        return if (content.isText()) {
            val baseText = content.asText()
            ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                    .content(SidecarUserContentEmbedder.embed(baseText, markers))
                    .build(),
            )
        } else if (content.isArrayOfContentParts()) {
            // Image-bearing user message: append sidecar to the first text part.
            val parts = content.asArrayOfContentParts().toMutableList()
            val firstTextIndex = parts.indexOfFirst { it.isText() }
            if (firstTextIndex >= 0) {
                val originalTextPart = parts[firstTextIndex].asText()
                val baseText = originalTextPart.text()
                val embeddedText = SidecarUserContentEmbedder.embed(baseText, markers)
                parts[firstTextIndex] = ChatCompletionContentPart.ofText(
                    originalTextPart.toBuilder()
                        .text(embeddedText)
                        .build(),
                )
            }
            ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                    .contentOfArrayOfContentParts(parts)
                    .build(),
            )
        } else {
            param
        }
    }
}
