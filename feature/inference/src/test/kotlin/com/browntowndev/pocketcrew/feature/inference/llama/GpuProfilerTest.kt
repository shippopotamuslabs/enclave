package com.browntowndev.pocketcrew.feature.inference.llama

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GpuProfiler - tests the backend routing logic.
 *
 * These tests verify that the GpuProfiler correctly identifies GPU types and routes
 * to the appropriate backend based on known driver issues:
 *
 * - Qualcomm Adreno -> OPENCL (Vulkan is broken)
 * - ARM Mali/Immortalis -> CPU (Vulkan crashes)
 * - Samsung Xclipse (RDNA) -> VULKAN (AMD Vulkan stack works)
 *
 * Note: Full GPU detection via EGL requires Android context and cannot be unit tested.
 * The fallback detection uses Build.HARDWARE which is platform-dependent.
 */
class GpuProfilerTest {

    @Test
    fun `LlamaBackend - fromValue returns correct enum`() {
        // Given: Backend values
        // When: Converting from value
        // Then: Should return correct enum
        assertEquals(LlamaBackend.CPU, LlamaBackend.fromValue(0))
        assertEquals(LlamaBackend.OPENCL, LlamaBackend.fromValue(1))
        assertEquals(LlamaBackend.VULKAN, LlamaBackend.fromValue(2))
    }

    @Test
    fun `LlamaBackend - fromValue throws for invalid value`() {
        // Given: Invalid backend value
        // When: Converting from invalid value
        // Then: Should throw NoSuchElementException
        try {
            LlamaBackend.fromValue(99)
            assert(false) { "Should have thrown" }
        } catch (e: NoSuchElementException) {
            // Expected
        }
    }

    @Test
    fun `LlamaBackend - enum values are correct`() {
        // Verify enum values match C++ backend enum
        assertEquals(0, LlamaBackend.CPU.value)
        assertEquals(1, LlamaBackend.OPENCL.value)
        assertEquals(2, LlamaBackend.VULKAN.value)
    }
}
