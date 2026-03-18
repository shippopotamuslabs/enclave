package com.browntowndev.pocketcrew.domain.usecase.inference

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of InferenceLockManager using reference counting.
 *
 * - ON_DEVICE uses a single "slot" - only one can run at a time
 * - BYOK uses reference counting - multiple can run concurrently
 * - isInferenceBlocked is true only when ON_DEVICE count > 0
 */
@Singleton
class InferenceLockManagerImpl @Inject constructor() : InferenceLockManager {

    private val _isInferenceBlocked = MutableStateFlow(false)
    override val isInferenceBlocked: StateFlow<Boolean> = _isInferenceBlocked.asStateFlow()

    // Count of active ON_DEVICE inferences (should be 0 or 1)
    private var onDeviceCount = 0

    // Count of active BYOK inferences (can be > 1)
    private var byokCount = 0

    @Synchronized
    override fun acquireLock(inferenceType: InferenceType): Boolean {
        return when (inferenceType) {
            InferenceType.ON_DEVICE -> {
                if (onDeviceCount > 0) {
                    // Already have on-device inference
                    false
                } else {
                    onDeviceCount = 1
                    _isInferenceBlocked.value = true
                    true
                }
            }
            InferenceType.BYOK -> {
                // BYOK never blocks - just increment count
                byokCount++
                true
            }
        }
    }

    @Synchronized
    override fun releaseLock() {
        // Determine which type to release (prefer ON_DEVICE if both active)
        when {
            onDeviceCount > 0 -> {
                onDeviceCount--
                if (onDeviceCount == 0) {
                    _isInferenceBlocked.value = false
                }
            }
            byokCount > 0 -> {
                byokCount--
                // isInferenceBlocked unaffected by BYOK
            }
        }
    }
}
