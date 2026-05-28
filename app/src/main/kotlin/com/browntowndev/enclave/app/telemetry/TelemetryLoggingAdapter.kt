package com.browntowndev.enclave.app.telemetry

import com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.feature.inference.AndroidLoggingAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Forwards [LoggingPort.error] throwables to [TelemetryEventPort.emitException] while preserving
 * Crashlytics reporting via [AndroidLoggingAdapter].
 */
@Singleton
class TelemetryLoggingAdapter
    @Inject
    constructor(
        private val delegate: AndroidLoggingAdapter,
        private val telemetryEventPort: TelemetryEventPort,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : LoggingPort {
        override fun debug(
            tag: String,
            message: String,
        ) {
            delegate.debug(tag, message)
        }

        override fun info(
            tag: String,
            message: String,
        ) {
            delegate.info(tag, message)
        }

        override fun warning(
            tag: String,
            message: String,
        ) {
            delegate.warning(tag, message)
        }

        override fun error(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            delegate.error(tag, message, throwable)
            if (throwable == null) {
                return
            }
            applicationScope.launch {
                runCatching {
                    telemetryEventPort.emitException(
                        ExceptionRecord(
                            eventId = UUID.randomUUID().toString(),
                            timestampMs = System.currentTimeMillis(),
                            tag = tag,
                            message = message,
                            throwableType = throwable::class.java.name,
                            stackTrace = throwable.stackTraceToString(),
                            severity = TelemetrySeverity.ERROR,
                        ),
                    )
                }
            }
        }
    }
