package com.browntowndev.enclave.app.telemetry

import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.port.telemetry.AutomaticErrorTrackingGate
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.AndroidLoggingAdapter
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetryLoggingAdapterTest {
    @Test
    fun `error with throwable emits detailed ExceptionRecord when tracking enabled`() =
        runTest {
            val gate = enabledGate()
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val adapter = adapter(this, gate, telemetryPort)
            val error = IllegalStateException("boom")
            adapter.error("TestTag", "failed", error)
            advanceUntilIdle()
            coVerify {
                telemetryPort.emitException(
                    match {
                        it.tag == "TestTag" &&
                            it.message == "failed" &&
                            it.throwableType == error::class.java.name &&
                            it.severity == TelemetrySeverity.ERROR &&
                            it.stackTrace.contains("IllegalStateException")
                    },
                )
            }
        }

    @Test
    fun `error with throwable emits redacted ExceptionRecord when tracking disabled`() =
        runTest {
            val gate = disabledGate()
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val captured = slot<com.browntowndev.enclave.domain.model.telemetry.ExceptionRecord>()
            val adapter = adapter(this, gate, telemetryPort)
            val error = IllegalStateException("secret exception message")
            adapter.error("TestTag", "secret log message", error)
            advanceUntilIdle()
            coVerify { telemetryPort.emitException(capture(captured)) }
            assertEquals(ExceptionRecordFactory.REDACTED, captured.captured.tag)
            assertEquals(ExceptionRecordFactory.REDACTED, captured.captured.message)
            assertEquals(ExceptionRecordFactory.REDACTED, captured.captured.stackTrace)
            assertEquals(error::class.java.name, captured.captured.throwableType)
        }

    private fun enabledGate(): AutomaticErrorTrackingGate =
        mockk {
            every { isAutomaticErrorTrackingEnabled() } returns true
        }

    private fun disabledGate(): AutomaticErrorTrackingGate =
        mockk {
            every { isAutomaticErrorTrackingEnabled() } returns false
        }

    private fun adapter(
        scope: kotlinx.coroutines.CoroutineScope,
        gate: AutomaticErrorTrackingGate,
        telemetryPort: TelemetryEventPort,
    ): TelemetryLoggingAdapter =
        TelemetryLoggingAdapter(
            delegate = mockk<AndroidLoggingAdapter>(relaxed = true),
            telemetryEventPort = telemetryPort,
            errorTrackingGate = gate,
            applicationScope = scope,
        )
}
