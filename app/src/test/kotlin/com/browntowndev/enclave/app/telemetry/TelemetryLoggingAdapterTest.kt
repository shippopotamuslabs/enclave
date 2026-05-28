package com.browntowndev.enclave.app.telemetry

import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.AndroidLoggingAdapter
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TelemetryLoggingAdapterTest {
    @Test
    fun `error with throwable forwards ExceptionRecord to telemetry port`() =
        runTest {
            val delegate = mockk<AndroidLoggingAdapter>(relaxed = true)
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val adapter =
                TelemetryLoggingAdapter(
                    delegate = delegate,
                    telemetryEventPort = telemetryPort,
                    applicationScope = this,
                )
            val error = IllegalStateException("boom")
            adapter.error("TestTag", "failed", error)
            advanceUntilIdle()
            coVerify {
                telemetryPort.emitException(
                    match {
                        it.tag == "TestTag" &&
                            it.message == "failed" &&
                            it.throwableType == error::class.java.name &&
                            it.severity == TelemetrySeverity.ERROR
                    },
                )
            }
        }
}
