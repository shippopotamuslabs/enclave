package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
internal fun allowPromptModerationUseCase(): ModeratePromptUseCase =
    ModeratePromptUseCase(
        object : PromptModerationPort {
            override suspend fun classify(prompt: String): PromptModerationScores =
                PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
        },
        NoOpLoggingPort,
    )

internal fun allowShouldModeratePromptUseCase(shouldModerate: Boolean = true): ShouldModeratePromptUseCase =
    FakeShouldModeratePromptUseCase(shouldModerate)

internal object NoOpLoggingPort : LoggingPort {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warning(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}
