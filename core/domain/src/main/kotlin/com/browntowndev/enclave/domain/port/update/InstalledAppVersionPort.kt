package com.browntowndev.enclave.domain.port.update

/**
 * Exposes the installed app version code to the domain layer.
 *
 * Implemented in `:app` by reading the package info (domain code cannot touch
 * `PackageManager` directly, following the [com.browntowndev.enclave.domain.port.config.DistributionConfigurationPort]
 * precedent for the installed-version bridge).
 */
interface InstalledAppVersionPort {
    val installedVersionCode: Int
}
