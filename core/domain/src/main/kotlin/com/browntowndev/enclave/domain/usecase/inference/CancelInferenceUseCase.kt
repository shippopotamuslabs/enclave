package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.domain.port.inference.OnDeviceInferenceCancellationPort
import javax.inject.Inject

open class CancelInferenceUseCase
    @Inject
    constructor(
        private val chatInferenceExecutor: ChatInferenceExecutorPort,
        private val onDeviceInferenceCancellation: OnDeviceInferenceCancellationPort,
        private val inferenceLockManager: InferenceLockManager,
    ) {
        open operator fun invoke() {
            onDeviceInferenceCancellation.cancelActiveGeneration()
            chatInferenceExecutor.stop()
            inferenceLockManager.forceReleaseOnDeviceLock()
        }
    }
