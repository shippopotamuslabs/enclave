package com.browntowndev.enclave.domain.port.update

/**
 * Persists in-app update-check bookkeeping: when the last check ran and which
 * release version the user dismissed.
 */
interface UpdateCheckPreferencesPort {
    suspend fun lastCheckMillis(): Long

    suspend fun setLastCheckMillis(value: Long)

    suspend fun dismissedVersionCode(): Int

    suspend fun setDismissedVersionCode(value: Int)
}
