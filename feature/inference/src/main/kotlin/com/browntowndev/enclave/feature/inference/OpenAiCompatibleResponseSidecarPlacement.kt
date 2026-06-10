package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.feature.inference.appendonly.FrozenSidecarSnapshot

/**
 * Inserts volatile sidecars into Responses wire segments at the same mapped indices
 * used by [com.browntowndev.enclave.feature.inference.appendonly.OpenAiCompatibleVolatilePlacement].
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
        preRetrievalSidecarText: String?,
        preRetrievalInsertIndex: Int?,
        currentSidecarText: String?,
        tailInsertIndex: Int,
        prependedSystemPrompt: String?,
    ): List<ResponseWireSegment> {
        val result = mutableListOf<ResponseWireSegment>()
        prependedSystemPrompt
            ?.takeIf { it.isNotBlank() }
            ?.let { result.add(ResponseWireSegment.SystemText(it)) }

        val frozenByIndex =
            frozenSidecars
                .groupBy { it.mappedInsertIndex }
                .mapValues { (_, snapshots) -> snapshots.map { it.text } }

        for (index in mappedSegments.indices) {
            frozenByIndex[index]?.forEach { frozenText ->
                result.add(ResponseWireSegment.SystemText(frozenText))
            }
            if (
                !preRetrievalSidecarText.isNullOrBlank() &&
                preRetrievalInsertIndex != null &&
                index == preRetrievalInsertIndex
            ) {
                result.add(ResponseWireSegment.SystemText(preRetrievalSidecarText))
            }
            if (!currentSidecarText.isNullOrBlank() && index == tailInsertIndex) {
                result.add(ResponseWireSegment.SystemText(currentSidecarText))
            }
            result.add(mappedSegments[index])
        }

        val trailingFrozenStart = mappedSegments.size
        frozenByIndex
            .filterKeys { it >= trailingFrozenStart }
            .toSortedMap()
            .values
            .flatten()
            .forEach { frozenText ->
                result.add(ResponseWireSegment.SystemText(frozenText))
            }
        if (!currentSidecarText.isNullOrBlank() && tailInsertIndex >= mappedSegments.size) {
            result.add(ResponseWireSegment.SystemText(currentSidecarText))
        }
        return result
    }
}
