package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.security.BiometricPort

/** In-memory [BiometricPort] fake (ENC-28). */
class FakeBiometricPort : BiometricPort {
    var isAvailable: Boolean = false

    /** Value returned by [hasSecureValue] once [hasSecureValueQueue] is drained. */
    var hasSecureValueValue: Boolean = false

    /** Queued [hasSecureValue] results (returnsMany-style); the last value sticks. */
    val hasSecureValueQueue = ArrayDeque<Boolean>()
    private var lastQueuedHasSecureValue: Boolean? = null

    private val secureValues = mutableMapOf<String, String>()
    val clearSecureValueCalls = mutableListOf<String>()
    var authenticateResult: Result<Unit> = Result.success(Unit)

    fun seedSecureValue(key: String, value: String = "sealed") {
        secureValues[key] = value
    }

    override fun isBiometricAvailable(): Boolean = isAvailable

    override suspend fun authenticate(title: String, subtitle: String): Result<Unit> = authenticateResult

    override suspend fun storeSecureValue(key: String, value: String): Result<Unit> {
        secureValues[key] = value
        return Result.success(Unit)
    }

    override suspend fun getSecureValue(
        key: String,
        promptTitle: String,
        promptSubtitle: String,
    ): Result<String?> = Result.success(secureValues[key])

    override fun hasSecureValue(key: String): Boolean {
        if (hasSecureValueQueue.isNotEmpty()) {
            lastQueuedHasSecureValue = hasSecureValueQueue.removeFirst()
        }
        lastQueuedHasSecureValue?.let { return it }
        if (key in secureValues) return true
        return hasSecureValueValue
    }

    override fun clearSecureValue(key: String) {
        clearSecureValueCalls += key
        secureValues.remove(key)
    }
}
