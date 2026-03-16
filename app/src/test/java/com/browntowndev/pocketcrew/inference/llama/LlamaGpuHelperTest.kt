package com.browntowndev.pocketcrew.inference.llama

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for dynamic GPU layer calculation in LlamaGpuHelper.
 */
class LlamaGpuHelperTest {

    private lateinit var mockContext: Context
    private lateinit var mockActivityManager: ActivityManager

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockActivityManager = mockk()

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { mockContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockActivityManager
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /**
     * Scenario: Available RAM is insufficient for any GPU layers.
     * Expected: Returns 0 (CPU only mode).
     */
    @Test
    fun `calculateDynamicGpuLayers returns 0 when available RAM is insufficient`() {
        // Given: Available RAM is 1GB (less than 1.5GB safety buffer + 500MB context)
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = args[0] as ActivityManager.MemoryInfo
            info.availMem = 1024L * 1024 * 1024 // 1GB available
            info.totalMem = 4L * 1024 * 1024 * 1024 // 4GB total
        }

        // When: Model is 4GB with 32 layers
        val result = LlamaGpuHelper.calculateDynamicGpuLayers(
            context = mockContext,
            modelSizeBytes = 4L * 1024 * 1024 * 1024, // 4GB model
            totalModelLayers = 32,
            contextSizeBytes = 500L * 1024 * 1024 // 500MB context
        )

        // Then: Returns 0 (not enough RAM)
        assertEquals(0, result)
    }

    /**
     * Scenario: Available RAM is sufficient for some but not all layers.
     * Expected: Returns the calculated number of layers that fit in available RAM.
     */
    @Test
    fun `calculateDynamicGpuLayers returns correct layers when RAM is sufficient for some layers`() {
        // Given: Available RAM is 6GB, safety buffer 1.5GB, context 500MB = 4GB usable
        // Model is 8GB with 32 layers = 256MB per layer
        // 4GB / 256MB = 16 layers
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = args[0] as ActivityManager.MemoryInfo
            info.availMem = 6L * 1024 * 1024 * 1024 // 6GB available
            info.totalMem = 8L * 1024 * 1024 * 1024 // 8GB total
        }

        // When: Model is 8GB with 32 layers
        val result = LlamaGpuHelper.calculateDynamicGpuLayers(
            context = mockContext,
            modelSizeBytes = 8L * 1024 * 1024 * 1024, // 8GB model
            totalModelLayers = 32,
            contextSizeBytes = 500L * 1024 * 1024 // 500MB context
        )

        // Then: Returns 16 layers (4GB usable / 256MB per layer)
        assertEquals(16, result)
    }

    /**
     * Scenario: Available RAM is more than enough for all model layers.
     * Expected: Returns totalModelLayers (maximum).
     */
    @Test
    fun `calculateDynamicGpuLayers returns totalModelLayers when RAM exceeds model needs`() {
        // Given: Available RAM is 12GB, safety buffer 1.5GB, context 500MB = 10GB usable
        // Model is 4GB with 32 layers = 128MB per layer
        // 10GB / 128MB = 80 layers, but capped at 32
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = args[0] as ActivityManager.MemoryInfo
            info.availMem = 12L * 1024 * 1024 * 1024 // 12GB available
            info.totalMem = 16L * 1024 * 1024 * 1024 // 16GB total
        }

        // When: Model is 4GB with 32 layers
        val result = LlamaGpuHelper.calculateDynamicGpuLayers(
            context = mockContext,
            modelSizeBytes = 4L * 1024 * 1024 * 1024, // 4GB model
            totalModelLayers = 32,
            contextSizeBytes = 500L * 1024 * 1024 // 500MB context
        )

        // Then: Returns 32 (all layers)
        assertEquals(32, result)
    }

    /**
     * Scenario: Model size is 0 (edge case).
     * Expected: Returns 0 to avoid division by zero.
     */
    @Test
    fun `calculateDynamicGpuLayers returns 0 when model size is 0`() {
        // Given: Plenty of available RAM
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = args[0] as ActivityManager.MemoryInfo
            info.availMem = 8L * 1024 * 1024 * 1024 // 8GB available
            info.totalMem = 8L * 1024 * 1024 * 1024 // 8GB total
        }

        // When: Model size is 0
        val result = LlamaGpuHelper.calculateDynamicGpuLayers(
            context = mockContext,
            modelSizeBytes = 0,
            totalModelLayers = 32,
            contextSizeBytes = 500L * 1024 * 1024
        )

        // Then: Returns 0 (safe fallback)
        assertEquals(0, result)
    }

    /**
     * Scenario: Total model layers is 0 (edge case).
     * Expected: Returns 0 to avoid division by zero.
     */
    @Test
    fun `calculateDynamicGpuLayers returns 0 when total model layers is 0`() {
        // Given: Plenty of available RAM
        every { mockActivityManager.getMemoryInfo(any()) } answers {
            val info = args[0] as ActivityManager.MemoryInfo
            info.availMem = 8L * 1024 * 1024 * 1024 // 8GB available
            info.totalMem = 8L * 1024 * 1024 * 1024 // 8GB total
        }

        // When: Total layers is 0
        val result = LlamaGpuHelper.calculateDynamicGpuLayers(
            context = mockContext,
            modelSizeBytes = 4L * 1024 * 1024 * 1024,
            totalModelLayers = 0,
            contextSizeBytes = 500L * 1024 * 1024
        )

        // Then: Returns 0 (safe fallback)
        assertEquals(0, result)
    }

    /**
     * Scenario: Exception occurs when getting memory info.
     * Expected: Returns 0 (safe fallback).
     */
    @Test
    fun `calculateDynamicGpuLayers returns 0 when exception occurs`() {
        // Given: ActivityManager throws an exception
        every { mockActivityManager.getMemoryInfo(any()) } throws RuntimeException("Test exception")

        // When: Calculating GPU layers
        val result = LlamaGpuHelper.calculateDynamicGpuLayers(
            context = mockContext,
            modelSizeBytes = 4L * 1024 * 1024 * 1024,
            totalModelLayers = 32,
            contextSizeBytes = 500L * 1024 * 1024
        )

        // Then: Returns 0 (safe fallback)
        assertEquals(0, result)
    }
}
