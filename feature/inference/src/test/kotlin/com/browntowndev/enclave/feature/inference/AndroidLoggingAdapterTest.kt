package com.browntowndev.enclave.feature.inference

import android.util.Log
import com.browntowndev.enclave.domain.port.telemetry.AutomaticErrorTrackingGate
import com.browntowndev.enclave.domain.util.telemetry.TelemetryPiiScrubber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AndroidLoggingAdapterTest {
    private lateinit var adapter: AndroidLoggingAdapter
    private lateinit var crashlytics: FirebaseCrashlytics
    private lateinit var errorTrackingGate: AutomaticErrorTrackingGate

    @BeforeEach
    fun setup() {
        mockkStatic(FirebaseCrashlytics::class)
        crashlytics = mockk(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        errorTrackingGate =
            mockk {
                every { isAutomaticErrorTrackingEnabled() } returns true
            }
        adapter = AndroidLoggingAdapter(errorTrackingGate)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `error with throwable calls Crashlytics when tracking enabled`() {
        val tag = "TestTag"
        val message = "Test message"
        val throwable = RuntimeException("Test exception")

        adapter.error(tag, message, throwable)

        verify {
            crashlytics.log(match { it.contains(tag) && it.contains(message) })
            crashlytics.recordException(throwable)
        }
    }

    @Test
    fun `error with throwable skips Crashlytics when tracking disabled`() {
        every { errorTrackingGate.isAutomaticErrorTrackingEnabled() } returns false
        adapter.error("TestTag", "Test message", RuntimeException("Test exception"))
        verify(exactly = 0) { crashlytics.log(any()) }
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `error scrubs email from Crashlytics breadcrumb`() {
        val tag = "TestTag"
        val message = "Failed for user@example.com"
        val throwable = RuntimeException("Test exception")

        adapter.error(tag, message, throwable)

        verify {
            crashlytics.log(
                match {
                    it.contains(TelemetryPiiScrubber.REDACTED_EMAIL) &&
                        !it.contains("user@example.com")
                },
            )
        }
    }

    @Test
    fun `error handles Crashlytics failure gracefully`() {
        every { FirebaseCrashlytics.getInstance() } throws RuntimeException("Firebase not initialized")

        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0

        val tag = "TestTag"
        val message = "Test message"
        val throwable = RuntimeException("Test exception")

        adapter.error(tag, message, throwable)

        verify {
            Log.e("AndroidLoggingAdapter", "Crashlytics not available. Error: $message", throwable)
        }
    }
}
