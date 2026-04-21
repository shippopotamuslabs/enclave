package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.ConversationManagerPort
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

class CancelInferenceUseCaseTest {

    private val conversationManager = mockk<ConversationManagerPort>(relaxed = true)
    private val chatInferenceExecutor = mockk<ChatInferenceExecutorPort>(relaxed = true)
    private val useCase = CancelInferenceUseCase(
        conversationManager = conversationManager,
        chatInferenceExecutor = chatInferenceExecutor,
    )

    @Test
    fun `invoke cancels current generation via conversationManager`() {
        useCase()

        verify { conversationManager.cancelCurrentGeneration() }
    }

    @Test
    fun `invoke cancels process via conversationManager`() {
        useCase()

        verify { conversationManager.cancelProcess() }
    }

    @Test
    fun `invoke stops chat inference executor`() {
        useCase()

        verify { chatInferenceExecutor.stop() }
    }

    @Test
    fun `invoke calls all cancellation methods in order`() {
        useCase()

        verifyOrder {
            conversationManager.cancelCurrentGeneration()
            conversationManager.cancelProcess()
            chatInferenceExecutor.stop()
        }
    }
}