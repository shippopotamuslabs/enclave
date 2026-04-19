package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.openai.models.Reasoning
import com.openai.models.ReasoningEffort

internal object ReasoningMapper {
    fun toSdkEffort(effort: ApiReasoningEffort): ReasoningEffort = when (effort) {
        ApiReasoningEffort.LOW -> ReasoningEffort.LOW
        ApiReasoningEffort.MEDIUM -> ReasoningEffort.MEDIUM
        ApiReasoningEffort.HIGH -> ReasoningEffort.HIGH
        ApiReasoningEffort.XHIGH -> ReasoningEffort.XHIGH
    }

    fun toSdkReasoning(effort: ApiReasoningEffort): Reasoning =
        Reasoning.builder()
            .effort(toSdkEffort(effort))
            .summary(Reasoning.Summary.CONCISE)
            .build()

    fun toSdkReasoningEffortOnly(effort: ApiReasoningEffort): Reasoning =
        Reasoning.builder()
            .effort(toSdkEffort(effort))
            .build()
}
