package com.browntowndev.enclave.feature.inference.appendonly

import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam

/**
 * Inserts volatile sidecars immediately before the live tail anchor
 * ([sessionLastIndex] in the session-mapped body, not index 1).
 */
internal object OpenAiCompatibleVolatilePlacement {
    /**
     * Index in [mappedBody] where the live volatile sidecar is inserted (immediately before that slot).
     * Must use mapped indices — session indices diverge when messages are dropped or remapped.
     */
    fun tailMappedInsertIndex(mappedBodySize: Int, isToolResult: Boolean = false): Int =
        if (mappedBodySize == 0) {
            0
        } else if (isToolResult) {
            mappedBodySize
        } else {
            (mappedBodySize - 1).coerceAtLeast(0)
        }

    fun buildMappedBodyWithSidecars(
        mappedBody: List<ChatCompletionMessageParam>,
        frozenSidecars: List<FrozenSidecarSnapshot>,
        preRetrievalSidecarText: String?,
        preRetrievalInsertIndex: Int?,
        currentSidecarText: String?,
        tailInsertIndex: Int,
    ): List<ChatCompletionMessageParam> {
        val frozenByIndex =
            frozenSidecars
                .groupBy { it.mappedInsertIndex }
                .mapValues { (_, snapshots) -> snapshots.map { it.text } }
        val result = mutableListOf<ChatCompletionMessageParam>()
        for (index in mappedBody.indices) {
            frozenByIndex[index]?.forEach { frozenText ->
                result.add(systemSidecarMessage(frozenText))
            }
            if (
                !preRetrievalSidecarText.isNullOrBlank() &&
                preRetrievalInsertIndex != null &&
                index == preRetrievalInsertIndex
            ) {
                result.add(systemSidecarMessage(preRetrievalSidecarText))
            }
            if (!currentSidecarText.isNullOrBlank() && index == tailInsertIndex) {
                result.add(systemSidecarMessage(currentSidecarText))
            }
            result.add(mappedBody[index])
        }
        val trailingFrozenStart = mappedBody.size
        frozenByIndex
            .filterKeys { it >= trailingFrozenStart }
            .toSortedMap()
            .values
            .flatten()
            .forEach { frozenText ->
                result.add(systemSidecarMessage(frozenText))
            }
        if (!currentSidecarText.isNullOrBlank() && tailInsertIndex >= mappedBody.size) {
            result.add(systemSidecarMessage(currentSidecarText))
        }
        return result
    }

    private fun systemSidecarMessage(text: String): ChatCompletionMessageParam =
        ChatCompletionMessageParam.ofSystem(
            ChatCompletionSystemMessageParam
                .builder()
                .content(text)
                .build(),
        )
}
