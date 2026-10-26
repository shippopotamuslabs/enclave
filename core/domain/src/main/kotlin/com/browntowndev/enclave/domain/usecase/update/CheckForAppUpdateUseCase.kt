package com.browntowndev.enclave.domain.usecase.update

import com.browntowndev.enclave.domain.model.update.LatestRelease
import com.browntowndev.enclave.domain.port.update.InstalledAppVersionPort
import com.browntowndev.enclave.domain.port.update.UpdateCheckPreferencesPort
import com.browntowndev.enclave.domain.port.update.UpdateMetadataPort
import com.browntowndev.enclave.domain.util.Clock
import javax.inject.Inject

/**
 * Result of an in-app update check against GitHub Releases.
 */
sealed interface AppUpdateResult {
    data class UpdateAvailable(
        val release: LatestRelease,
    ) : AppUpdateResult

    data object None : AppUpdateResult
}

/**
 * Checks whether a newer Enclave release is published (ENC-250).
 *
 * Throttles to one network check per 24 hours: when the last recorded check is
 * recent, returns [AppUpdateResult.None] without fetching. The last-check
 * timestamp is recorded before fetching, so fetch failures also throttle.
 * Never throws for a quiet check — a `null` fetch result means [AppUpdateResult.None].
 */
open class CheckForAppUpdateUseCase
    @Inject
    constructor(
        private val metadata: UpdateMetadataPort,
        private val installed: InstalledAppVersionPort,
        private val preferences: UpdateCheckPreferencesPort,
        private val clock: Clock,
    ) {
        companion object {
            const val CHECK_INTERVAL_MILLIS: Long = 24L * 60L * 60L * 1000L
        }

        open suspend operator fun invoke(): AppUpdateResult {
            val now = clock.currentTimeMillis()
            if (now - preferences.lastCheckMillis() < CHECK_INTERVAL_MILLIS) {
                return AppUpdateResult.None
            }
            preferences.setLastCheckMillis(now)
            val release = metadata.fetchLatestRelease() ?: return AppUpdateResult.None
            if (release.versionCode <= installed.installedVersionCode) {
                return AppUpdateResult.None
            }
            if (release.versionCode == preferences.dismissedVersionCode()) {
                return AppUpdateResult.None
            }
            return AppUpdateResult.UpdateAvailable(release)
        }
    }
