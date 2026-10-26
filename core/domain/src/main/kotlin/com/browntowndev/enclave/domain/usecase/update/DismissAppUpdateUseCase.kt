package com.browntowndev.enclave.domain.usecase.update

import com.browntowndev.enclave.domain.port.update.UpdateCheckPreferencesPort
import javax.inject.Inject

/**
 * Records that the user dismissed the update prompt for [versionCode], so the
 * next [CheckForAppUpdateUseCase] run stays quiet for that release.
 */
open class DismissAppUpdateUseCase
    @Inject
    constructor(
        private val preferences: UpdateCheckPreferencesPort,
    ) {
        open suspend operator fun invoke(versionCode: Int) {
            preferences.setDismissedVersionCode(versionCode)
        }
    }
