package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.repository.DeviceEnvironmentRepositoryPort

/** In-memory [DeviceEnvironmentRepositoryPort] fake (ENC-28): knob vars plus call recording. */
class FakeDeviceEnvironmentRepository : DeviceEnvironmentRepositoryPort {
    /** Value returned by [isWifiConnected]. */
    var wifiConnected: Boolean = true

    /** Value returned by [hasRequiredStorage]. */
    var hasStorage: Boolean = true

    /** Value returned by [isNetworkAvailable]. */
    var networkAvailable: Boolean = true

    var isWifiConnectedCallCount = 0
    var isNetworkAvailableCallCount = 0

    /** Every requiredBytes value passed to [hasRequiredStorage], in call order. */
    val hasRequiredStorageCalls = mutableListOf<Long>()

    override suspend fun isWifiConnected(): Boolean {
        isWifiConnectedCallCount++
        return wifiConnected
    }

    override suspend fun hasRequiredStorage(requiredBytes: Long): Boolean {
        hasRequiredStorageCalls += requiredBytes
        return hasStorage
    }

    override suspend fun isNetworkAvailable(): Boolean {
        isNetworkAvailableCallCount++
        return networkAvailable
    }
}
