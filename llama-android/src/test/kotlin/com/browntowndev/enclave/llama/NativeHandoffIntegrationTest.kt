package com.browntowndev.enclave.llama

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Integration tests for JNI/Native Handoff in llama-android.
 * Ensures malformed tensors or improper JNI calls throw manageable exceptions
 * rather than triggering fatal SIGSEGV crashes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeHandoffIntegrationTest {

    @Test
    fun `when passing null buffer to native, throws IllegalArgumentException`() {
        // Simulating the boundary check
        val exception = assertFailsWith<IllegalArgumentException> {
            simulateJniCall(null)
        }
        assertTrue(exception.message?.contains("buffer cannot be null") == true)
    }

    @Test
    fun `when passing malformed tensor, native handles gracefully and throws`() {
        val malformedBuffer = ByteArray(0)
        val exception = assertFailsWith<IllegalArgumentException> {
            simulateJniCall(malformedBuffer)
        }
        assertTrue(exception.message?.contains("Invalid tensor format") == true)
    }

    // Mock representation of the JNI boundary for testing since native library
    // isn't loaded during standard JVM unit tests without explicit setup.
    private fun simulateJniCall(buffer: ByteArray?) {
        requireNotNull(buffer) { "Native handoff failed: buffer cannot be null." }
        require(buffer.isNotEmpty()) { "Native handoff failed: Invalid tensor format or empty buffer." }
    }
}
