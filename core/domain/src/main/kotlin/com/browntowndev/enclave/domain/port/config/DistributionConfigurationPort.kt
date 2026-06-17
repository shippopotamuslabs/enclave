package com.browntowndev.enclave.domain.port.config

/**
 * Exposes the build-variant distribution channel to domain and feature layers.
 *
 * Implemented in `:app` by reading the Gradle product flavor BuildConfig field.
 * Domain code cannot read BuildConfig directly (Clean Architecture dependency rule),
 * so this port is the single bridge for flavor-gated behavior.
 */
interface DistributionConfigurationPort {
    val isPlayStoreFlavor: Boolean
}
