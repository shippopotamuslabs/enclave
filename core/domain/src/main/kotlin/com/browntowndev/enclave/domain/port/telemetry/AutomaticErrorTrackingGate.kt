package com.browntowndev.enclave.domain.port.telemetry

/**
 * Synchronous read of the user's automatic error-tracking consent.
 */
interface AutomaticErrorTrackingGate {
    fun isAutomaticErrorTrackingEnabled(): Boolean
}
