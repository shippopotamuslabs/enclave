package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase

import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
import io.mockk.coEvery
import io.mockk.mockk

internal fun allowPromptModerationUseCase(): ModeratePromptUseCase =
    ModeratePromptUseCase(
        object : PromptModerationPort {
            override suspend fun classify(prompt: String): PromptModerationScores =
                PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
        },
    )

internal fun allowShouldModeratePromptUseCase(shouldModerate: Boolean = true): ShouldModeratePromptUseCase =
    mockk(relaxed = true) {
        coEvery { this@mockk.invoke(any()) } returns shouldModerate
    }
