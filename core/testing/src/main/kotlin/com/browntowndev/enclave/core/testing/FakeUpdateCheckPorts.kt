package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.update.LatestRelease
import com.browntowndev.enclave.domain.port.update.InstalledAppVersionPort
import com.browntowndev.enclave.domain.port.update.UpdateCheckPreferencesPort
import com.browntowndev.enclave.domain.port.update.UpdateMetadataPort

/** In-memory [UpdateMetadataPort] fake with a fetch counter (ENC-250). */
class FakeUpdateMetadataPort(
    var release: LatestRelease? = null,
) : UpdateMetadataPort {
    var fetchCount: Int = 0
        private set

    override suspend fun fetchLatestRelease(): LatestRelease? {
        fetchCount++
        return release
    }
}

/** In-memory [InstalledAppVersionPort] fake (ENC-250). */
class FakeInstalledAppVersionPort(
    override var installedVersionCode: Int = 1,
) : InstalledAppVersionPort

/** In-memory [UpdateCheckPreferencesPort] fake (ENC-250). */
class FakeUpdateCheckPreferencesPort(
    initialLastCheckMillis: Long = 0L,
    initialDismissedVersionCode: Int = 0,
) : UpdateCheckPreferencesPort {
    private var lastCheckMillis: Long = initialLastCheckMillis
    private var dismissedVersionCode: Int = initialDismissedVersionCode

    override suspend fun lastCheckMillis(): Long = lastCheckMillis

    override suspend fun setLastCheckMillis(value: Long) {
        lastCheckMillis = value
    }

    override suspend fun dismissedVersionCode(): Int = dismissedVersionCode

    override suspend fun setDismissedVersionCode(value: Int) {
        dismissedVersionCode = value
    }
}
