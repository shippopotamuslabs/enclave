package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.core.testing.FakeChatInferenceExecutorPort
import com.browntowndev.enclave.core.testing.FakeOnDeviceInferenceCancellationPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CancelInferenceUseCaseTest {
    private val chatInferenceExecutor = FakeChatInferenceExecutorPort()
    private val onDeviceInferenceCancellation = FakeOnDeviceInferenceCancellationPort()
    private val inferenceLockManager = FakeInferenceLockManager()
    private val useCase =
        CancelInferenceUseCase(
            chatInferenceExecutor = chatInferenceExecutor,
            onDeviceInferenceCancellation = onDeviceInferenceCancellation,
            inferenceLockManager = inferenceLockManager,
        )

    @Test
    fun `invoke cancels native generation stops executor and clears stuck lock`() {
        useCase()

        assertEquals(1, onDeviceInferenceCancellation.cancelCount)
        assertEquals(1, chatInferenceExecutor.stopCount)
        assertEquals(1, inferenceLockManager.forceReleaseOnDeviceLockCount)
    }

    /** Recording [InferenceLockManager] fake: only the force-release path is exercised here. */
    private class FakeInferenceLockManager : InferenceLockManager {
        override val isInferenceBlocked: StateFlow<Boolean> = MutableStateFlow(false)

        var forceReleaseOnDeviceLockCount = 0

        override fun acquireLock(inferenceType: InferenceType): Boolean = true

        override fun releaseLock(inferenceType: InferenceType) = Unit

        override fun forceReleaseOnDeviceLock() {
            forceReleaseOnDeviceLockCount++
        }
    }
}
