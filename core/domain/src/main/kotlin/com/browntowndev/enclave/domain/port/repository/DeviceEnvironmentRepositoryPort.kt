package com.browntowndev.enclave.domain.port.repository

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

    /**
     * Check if network connection is available (internet access).
     */
    suspend fun isNetworkAvailable(): Boolean
}

/**
 * No-op implementation of [DeviceEnvironmentRepositoryPort] that assumes all checks succeed.
 * Useful for tests and fallback/default construction.
 */
object NoopDeviceEnvironmentRepository : DeviceEnvironmentRepositoryPort {
    override suspend fun isWifiConnected(): Boolean = true
    override suspend fun hasRequiredStorage(requiredBytes: Long): Boolean = true
    override suspend fun isNetworkAvailable(): Boolean = true
}

