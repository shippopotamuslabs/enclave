package com.browntowndev.enclave.app.telemetry

import com.browntowndev.enclave.core.testing.FakeAutomaticErrorTrackingGate
import com.browntowndev.enclave.core.testing.FakeTelemetryEventPort
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.AndroidLoggingAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34], application = android.app.Application::class)
class TelemetryLoggingAdapterTest {
    @Test
    fun `error with throwable emits detailed ExceptionRecord when tracking enabled`() =
        runTest {
            val gate = FakeAutomaticErrorTrackingGate(enabled = true)
            val telemetryPort = FakeTelemetryEventPort()
            val adapter = adapter(this, gate, telemetryPort)
            val error = IllegalStateException("boom")
            adapter.error("TestTag", "failed", error)
            advanceUntilIdle()

            val record = telemetryPort.emittedExceptions.single()
            assertEquals("TestTag", record.tag)
            assertEquals("failed", record.message)
            assertEquals(error::class.java.name, record.throwableType)
            assertEquals(TelemetrySeverity.ERROR, record.severity)
            assertTrue(record.stackTrace.contains("IllegalStateException"))
        }

    @Test
    fun `error with throwable emits redacted ExceptionRecord when tracking disabled`() =
        runTest {
            val gate = FakeAutomaticErrorTrackingGate(enabled = false)
            val telemetryPort = FakeTelemetryEventPort()
            val adapter = adapter(this, gate, telemetryPort)
            val error = IllegalStateException("secret exception message")
            adapter.error("TestTag", "secret log message", error)
            advanceUntilIdle()

            val record = telemetryPort.emittedExceptions.single()
            assertEquals(ExceptionRecordFactory.REDACTED, record.tag)
            assertEquals(ExceptionRecordFactory.REDACTED, record.message)
            assertEquals(ExceptionRecordFactory.REDACTED, record.stackTrace)
            assertEquals(error::class.java.name, record.throwableType)
        }

    private fun adapter(
        scope: CoroutineScope,
        gate: FakeAutomaticErrorTrackingGate,
        telemetryPort: TelemetryEventPort,
    ): TelemetryLoggingAdapter =
        TelemetryLoggingAdapter(
            delegate = AndroidLoggingAdapter(gate),
            telemetryEventPort = telemetryPort,
            errorTrackingGate = gate,
            applicationScope = scope,
        )
}
