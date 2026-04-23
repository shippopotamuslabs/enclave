package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import javax.inject.Inject

class CancelInferenceUseCase @Inject constructor(
    private val chatInferenceExecutor: ChatInferenceExecutorPort,
) {
    operator fun invoke() {
        chatInferenceExecutor.stop()
    }
}
