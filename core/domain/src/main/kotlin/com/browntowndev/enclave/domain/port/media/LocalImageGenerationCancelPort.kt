package com.browntowndev.enclave.domain.port.media

/**
 * Signals the on-device diffusion native runtime to stop the current blocking generation.
 * Must be invoked from the service stop path; coroutine cancellation alone cannot interrupt JNI.
 */
interface LocalImageGenerationCancelPort {
    fun cancelActiveGeneration()
}
