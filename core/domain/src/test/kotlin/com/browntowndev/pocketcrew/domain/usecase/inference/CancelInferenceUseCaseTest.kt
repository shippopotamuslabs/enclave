package com.browntowndev.pocketcrew.domain.usecase.inference

import com.browntowndev.pocketcrew.domain.port.inference.ChatInferenceExecutorPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CancelInferenceUseCaseTest {
    private val chatInferenceExecutor = mockk<ChatInferenceExecutorPort>(relaxed = true)
    private val useCase = CancelInferenceUseCase(
        chatInferenceExecutor = chatInferenceExecutor,
    )

    @Test
    fun `invoke should stop chat inference executor`() {
        // When
        useCase()

        // Then
        verify { chatInferenceExecutor.stop() }
    }
}