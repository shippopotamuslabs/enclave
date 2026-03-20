package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake implementation of InferenceLockManager for testing.
 * Tracks lock acquisition/release and allows simulating blocked states.
 */
class FakeInferenceLockManager : InferenceLockManager {
    
    private val _isInferenceBlocked = MutableStateFlow(false)
    override val isInferenceBlocked: StateFlow<Boolean> = _isInferenceBlocked
    
    private var isLocked = false
    private var lockCount = 0
    private var releaseCount = 0
    
    override fun acquireLock(inferenceType: InferenceType): Boolean {
        return if (!isLocked) {
            isLocked = true
            lockCount++
            _isInferenceBlocked.value = true
            true
        } else {
            false
        }
    }
    
    override fun releaseLock() {
        isLocked = false
        releaseCount++
        _isInferenceBlocked.value = false
    }
    
    fun getLockCount(): Int = lockCount
    fun getReleaseCount(): Int = releaseCount
    fun isCurrentlyLocked(): Boolean = isLocked
    
    fun reset() {
        isLocked = false
        lockCount = 0
        releaseCount = 0
        _isInferenceBlocked.value = false
    }
}
