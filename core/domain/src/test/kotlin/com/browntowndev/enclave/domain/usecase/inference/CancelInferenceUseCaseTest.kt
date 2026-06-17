package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.domain.port.inference.OnDeviceInferenceCancellationPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CancelInferenceUseCaseTest {
    private val chatInferenceExecutor = mockk<ChatInferenceExecutorPort>(relaxed = true)
    private val onDeviceInferenceCancellation = mockk<OnDeviceInferenceCancellationPort>(relaxed = true)
    private val inferenceLockManager = mockk<InferenceLockManager>(relaxed = true)
    private val useCase =
        CancelInferenceUseCase(
            chatInferenceExecutor = chatInferenceExecutor,
            onDeviceInferenceCancellation = onDeviceInferenceCancellation,
            inferenceLockManager = inferenceLockManager,
        )

    @Test
    fun `invoke cancels native generation stops executor and clears stuck lock`() {
        useCase()

        verify(exactly = 1) { onDeviceInferenceCancellation.cancelActiveGeneration() }
        verify(exactly = 1) { chatInferenceExecutor.stop() }
        verify(exactly = 1) { inferenceLockManager.forceReleaseOnDeviceLock() }
    }
}