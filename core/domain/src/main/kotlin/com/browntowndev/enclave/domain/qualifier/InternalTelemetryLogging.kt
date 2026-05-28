package com.browntowndev.enclave.domain.qualifier

import javax.inject.Qualifier

/**
 * Logging port used by telemetry persistence itself to avoid a Hilt cycle with
 * [com.browntowndev.enclave.domain.port.inference.LoggingPort] decorators that forward to telemetry.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FIELD,
)
annotation class InternalTelemetryLogging
