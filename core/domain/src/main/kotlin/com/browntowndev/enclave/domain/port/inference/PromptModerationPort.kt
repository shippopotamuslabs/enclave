package com.browntowndev.enclave.domain.port.inference

data class PromptModerationScores(
    val scoresByLabel: Map<String, Double>,
    val labelAliases: Map<String, List<String>> = emptyMap(),
    val thresholds: Map<String, Double> = emptyMap(),
    val combinedIllegalLabels: List<String> = emptyList(),
)

interface PromptModerationPort {
    suspend fun classify(prompt: String): PromptModerationScores
}
