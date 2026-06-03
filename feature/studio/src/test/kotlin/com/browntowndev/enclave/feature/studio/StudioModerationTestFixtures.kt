package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase

internal fun allowPromptModerationUseCase(): ModeratePromptUseCase =
    ModeratePromptUseCase(
        object : PromptModerationPort {
            override suspend fun classify(prompt: String): PromptModerationScores =
                PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
        },
    )
