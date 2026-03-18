package com.browntowndev.pocketcrew.domain.usecase.inference

import kotlinx.coroutines.flow.StateFlow

/**
 * Manages global inference lock to prevent concurrent on-device model loading.
 *
 * This prevents OOM errors when users send messages from multiple chat windows
 * while an inference is already in progress. The lock distinguishes between:
 * - ON_DEVICE: Local model inference (blocks concurrent on-device inference)
 * - BYOK: External API inference (allows concurrent - no local model loading)
 *
 * Design:
 * - Only ON_DEVICE inference blocks new ON_DEVICE inferences
 * - BYOK inferences don't block anything (they use external APIs)
 * - Multiple BYOK inferences can run concurrently
 * - ON_DEVICE + BYOK can run concurrently (ON_DEVICE doesn't block BYOK)
 */
interface InferenceLockManager {
    /**
     * Flow that emits true when any on-device inference is in progress.
     * Use this to disable input in UI across all chat screens.
     */
    val isInferenceBlocked: StateFlow<Boolean>

    /**
     * Attempts to acquire the inference lock for the given type.
     * @param inferenceType The type of inference (ON_DEVICE or BYOK)
     * @return true if lock acquired, false if blocked by another inference
     */
    fun acquireLock(inferenceType: InferenceType): Boolean

    /**
     * Releases the previously acquired lock.
     * Must be called exactly once for each successful acquireLock().
     */
    fun releaseLock()
}

/**
 * Type of inference - determines lock behavior.
 */
enum class InferenceType {
    /** On-device local model inference - blocks concurrent on-device */
    ON_DEVICE,

    /** External API inference (future BYOK feature) - allows concurrent */
    BYOK
}
