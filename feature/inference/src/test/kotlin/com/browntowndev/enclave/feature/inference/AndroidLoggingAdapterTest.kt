package com.browntowndev.enclave.feature.inference

import android.util.Log
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

    @BeforeEach
    fun setup() {
        mockkStatic(FirebaseCrashlytics::class)
        crashlytics = mockk(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        adapter = AndroidLoggingAdapter()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `error with throwable calls Crashlytics`() {
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
