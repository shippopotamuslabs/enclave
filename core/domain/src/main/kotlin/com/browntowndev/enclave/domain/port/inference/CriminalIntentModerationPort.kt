package com.browntowndev.enclave.domain.port.inference

interface CriminalIntentModerationPort {
    suspend fun classify(prompt: String): PromptModerationScores

    companion object {
        val Dummy =
            object : CriminalIntentModerationPort {
                override suspend fun classify(prompt: String): PromptModerationScores =
                    PromptModerationScores(
                        scoresByLabel = emptyMap(),
                    )
            }
    }
}
