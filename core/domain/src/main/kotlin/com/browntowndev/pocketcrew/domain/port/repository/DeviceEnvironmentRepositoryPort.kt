package com.browntowndev.pocketcrew.domain.port.repository

/**
 * Port interface for device environment checks.
 * Implemented by DeviceEnvironmentRepository in the data layer.
 */
interface DeviceEnvironmentRepositoryPort {
    /**
     * Check if device is connected to WiFi.
     */
    suspend fun isWifiConnected(): Boolean

    /**
     * Check if device has required storage space.
     * @param requiredBytes Minimum required storage in bytes
     * @return true if device has at least requiredBytes available
     */
    suspend fun hasRequiredStorage(requiredBytes: Long): Boolean
}
