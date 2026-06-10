package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.openai.models.responses.ResponseInputItem

internal object ResponseWireSegmentSupport {
    data class ResolvedResponseWire(
        val input: List<ResponseInputItem>,
        val instructions: String?,
    )

    fun resolveForOpenAi(segments: List<ResponseWireSegment>): ResolvedResponseWire {
        val instructionLines = mutableListOf<String>()
        val input = mutableListOf<ResponseInputItem>()
        segments.forEach { segment ->
            when (segment) {
                is ResponseWireSegment.SystemText ->
                    segment.text
                        .takeIf { it.isNotBlank() }
                        ?.let(instructionLines::add)
                is ResponseWireSegment.Input -> input.add(segment.item)
            }
        }
        return ResolvedResponseWire(
            input = input,
            instructions =
                instructionLines
                    .joinToString(separator = "\n\n")
                    .takeIf { it.isNotBlank() },
        )
    }

    fun resolveForXai(segments: List<ResponseWireSegment>): ResolvedResponseWire {
        val input = mutableListOf<ResponseInputItem>()
        segments.forEach { segment ->
            when (segment) {
                is ResponseWireSegment.SystemText ->
                    segment.text
                        .takeIf { it.isNotBlank() }
                        ?.let { text ->
                            input.add(ResponsesInputMessageSupport.textInputItem(Role.SYSTEM, text))
                        }
                is ResponseWireSegment.Input -> input.add(segment.item)
            }
        }
        return ResolvedResponseWire(
            input = input,
            instructions = null,
        )
    }
}
