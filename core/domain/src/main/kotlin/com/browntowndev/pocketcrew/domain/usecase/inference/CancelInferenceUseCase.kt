package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import javax.inject.Inject

class CancelInferenceUseCase @Inject constructor(
    private val conversationManager: ConversationManagerPort,
    private val chatInferenceExecutor: ChatInferenceExecutorPort,
) {
    operator fun invoke() {
        conversationManager.cancelCurrentGeneration()
        conversationManager.cancelProcess()
        chatInferenceExecutor.stop()
    }
}
