package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.feature.inference.appendonly.FrozenSidecarSnapshot
import com.browntowndev.enclave.feature.inference.appendonly.ResolvedSidecar
import com.browntowndev.enclave.feature.inference.appendonly.SidecarMarker
import com.browntowndev.enclave.feature.inference.appendonly.SidecarUserContentEmbedder
import com.openai.models.responses.ResponseInputItem

/**
 * Embeds volatile sidecars as UUID-stamped `[Sidecar-<uuid>:]` suffix
 * annotations on user `ResponseWireSegment.Input` content (INV-1, INV-2,
 * INV-4). No sidecar `ResponseWireSegment.SystemText` segments are emitted.
 */
internal object OpenAiCompatibleResponseSidecarPlacement {
    fun remapFrozenSidecarsForResponses(
        frozenSidecars: List<FrozenSidecarSnapshot>,
        chatSessionIndexToMappedIndex: IntArray,
        responseSessionIndexToMappedIndex: IntArray,
    ): List<FrozenSidecarSnapshot> =
        frozenSidecars.map { snapshot ->
            val sessionAnchor =
                chatSessionIndexToMappedIndex.indices.firstOrNull { index ->
                    chatSessionIndexToMappedIndex[index] == snapshot.mappedInsertIndex
                }
            val responseIndex =
                sessionAnchor
                    ?.let { responseSessionIndexToMappedIndex.getOrNull(it) }
                    ?.takeIf { it >= 0 }
                    ?: snapshot.mappedInsertIndex
            snapshot.copy(mappedInsertIndex = responseIndex)
        }

    fun buildSegmentsWithSidecars(
        mappedSegments: List<ResponseWireSegment>,
        frozenSidecars: List<FrozenSidecarSnapshot>,
        preRetrievalSidecar: ResolvedSidecar?,
        preRetrievalInsertIndex: Int?,
        currentSidecar: ResolvedSidecar?,
        tailInsertIndex: Int,
        prependedSystemPrompt: String?,
    ): List<ResponseWireSegment> {
        val result = mutableListOf<ResponseWireSegment>()
        prependedSystemPrompt
            ?.takeIf { it.isNotBlank() }
            ?.let { result.add(ResponseWireSegment.SystemText(it)) }

        if (mappedSegments.isEmpty()) return result

        // 1. Identify all real user Input segments in mappedSegments.
        val realUserInputIndices = mappedSegments.indices.filter { index ->
            isRealUserInputSegment(mappedSegments[index])
        }

        // If no user inputs exist, we cannot embed sidecars, so we just append the mappedSegments as-is.
        if (realUserInputIndices.isEmpty()) {
            result.addAll(mappedSegments)
            return result
        }

        // Helper to anchor any target index to the last real user input segment at or before that index.
        fun findUserAnchorIndex(targetIndex: Int): Int {
            val anchor = realUserInputIndices.lastOrNull { it <= targetIndex }
            return anchor ?: realUserInputIndices.first()
        }

        // Group markers by their re-anchored user input index.
        val markersByUserIndex = mutableMapOf<Int, MutableList<SidecarMarker>>()
        fun addMarker(userInputIndex: Int, marker: SidecarMarker) {
            markersByUserIndex.getOrPut(userInputIndex) { mutableListOf() }.add(marker)
        }

        // Add frozen markers.
        for (snapshot in frozenSidecars) {
            val anchorIndex = findUserAnchorIndex(snapshot.mappedInsertIndex)
            addMarker(anchorIndex, SidecarMarker(snapshot.markerId, snapshot.text))
        }

        // Add pre-retrieval marker.
        if (preRetrievalSidecar != null && preRetrievalInsertIndex != null) {
            val anchorIndex = findUserAnchorIndex(preRetrievalInsertIndex)
            addMarker(anchorIndex, SidecarMarker(preRetrievalSidecar.markerId, preRetrievalSidecar.text))
        }

        // Add live marker.
        if (currentSidecar != null) {
            val anchorIndex = findUserAnchorIndex(tailInsertIndex)
            addMarker(anchorIndex, SidecarMarker(currentSidecar.markerId, currentSidecar.text))
        }

        // Rebuild mappedSegments and add them.
        for (index in mappedSegments.indices) {
            val segment = mappedSegments[index]
            val markers = markersByUserIndex[index]
            if (markers != null && markers.isNotEmpty() && segment is ResponseWireSegment.Input) {
                result.add(embedMarkersIntoInputSegment(segment, markers))
            } else {
                result.add(segment)
            }
        }

        return result
    }

    private fun isRealUserInputSegment(segment: ResponseWireSegment): Boolean {
        if (segment !is ResponseWireSegment.Input) return false
        if (!isUserInputItem(segment.item)) return false
        val cleanText = extractUserText(segment.item).orEmpty()
        return !EngineInternalMessageMapper.isEngineInternalWireText(cleanText)
    }

    private fun embedMarkersIntoInputSegment(
        segment: ResponseWireSegment.Input,
        markers: List<SidecarMarker>,
    ): ResponseWireSegment.Input {
        val item = segment.item
        if (item.isMessage()) {
            return embedMarkersIntoMessageItem(item, markers) ?: segment
        }

        val embeddedText = extractAndEmbed(item, markers) ?: return segment
        return ResponseWireSegment.Input(
            ResponsesInputMessageSupport.textInputItem(
                role = com.browntowndev.enclave.domain.model.chat.Role.USER,
                content = embeddedText,
            ),
        )
    }

    private fun embedMarkersIntoMessageItem(
        item: ResponseInputItem,
        markers: List<SidecarMarker>,
    ): ResponseWireSegment.Input? {
        val msg = item.asMessage()
        val role = msg.role()
        if (!role.toString().contains("user")) return null
        val contentParts = msg.content()
        if (contentParts.any { part -> !part.isInputText() && !part.isInputImage() }) {
            // The SDK builder path here only round-trips typed text/image parts; keep
            // unknown content intact instead of silently dropping it during sidecar embed.
            return null
        }
        val firstTextIndex = contentParts.indexOfFirst { it.isInputText() }
        if (firstTextIndex >= 0) {
            return ResponseWireSegment.Input(
                ResponseInputItem.ofMessage(
                    buildTextBearingMessage(
                        message = msg,
                        firstTextIndex = firstTextIndex,
                        markers = markers,
                    ),
                ),
            )
        }
        if (contentParts.isEmpty() || contentParts.any { it.isInputImage() }) {
            return ResponseWireSegment.Input(
                ResponseInputItem.ofMessage(
                    buildImageOnlyMessage(
                        message = msg,
                        markers = markers,
                    ),
                ),
            )
        }
        return null
    }

    private fun buildTextBearingMessage(
        message: ResponseInputItem.Message,
        firstTextIndex: Int,
        markers: List<SidecarMarker>,
    ): ResponseInputItem.Message {
        val role = message.role()
        val contentParts = message.content()
        val builder = ResponseInputItem.Message.builder().role(role)
        for (i in contentParts.indices) {
            val part = contentParts[i]
            if (i == firstTextIndex) {
                val baseText = part.asInputText().text()
                val embeddedText = SidecarUserContentEmbedder.embed(baseText, markers)
                builder.addInputTextContent(embeddedText)
            } else if (part.isInputText()) {
                builder.addInputTextContent(part.asInputText().text())
            } else if (part.isInputImage()) {
                builder.addContent(part.asInputImage())
            }
        }
        return builder.build()
    }

    private fun buildImageOnlyMessage(
        message: ResponseInputItem.Message,
        markers: List<SidecarMarker>,
    ): ResponseInputItem.Message {
        val role = message.role()
        val contentParts = message.content()
        val builder =
            ResponseInputItem.Message
                .builder()
                .role(role)
                .addInputTextContent(sidecarOnlyText(markers))
        contentParts
            .filter { it.isInputImage() }
            .forEach { part -> builder.addContent(part.asInputImage()) }
        return builder.build()
    }

    /**
     * Extracts the current text content from a [ResponseInputItem] and embeds
     * sidecar markers as a suffix. Returns null if the item is not a user
     * text message.
     */
    private fun extractAndEmbed(
        item: ResponseInputItem,
        markers: List<SidecarMarker>,
    ): String? =
        extractUserText(item)?.let { baseText ->
            SidecarUserContentEmbedder.embed(baseText, markers)
        }

    private fun isUserInputItem(item: ResponseInputItem): Boolean =
        when {
            item.isMessage() -> item.asMessage().role().toString().contains("user")
            item.isEasyInputMessage() -> item.asEasyInputMessage().role().toString().contains("user")
            else -> false
        }

    private fun extractUserText(item: ResponseInputItem): String? =
        when {
            item.isMessage() -> {
                val msg = item.asMessage()
                if (!msg.role().toString().contains("user")) return null
                val textParts = msg.content().filter { it.isInputText() }
                if (textParts.isEmpty()) return null
                textParts.joinToString("") { it.asInputText().text() }
            }
            item.isEasyInputMessage() -> {
                val msg = item.asEasyInputMessage()
                if (!msg.role().toString().contains("user")) return null
                val content = msg.content()
                if (!content.isTextInput()) return null
                content.asTextInput()
            }
            else -> null
        }

    private fun sidecarOnlyText(markers: List<SidecarMarker>): String =
        SidecarUserContentEmbedder.embed(baseContent = "", markers = markers).trimStart()
}
