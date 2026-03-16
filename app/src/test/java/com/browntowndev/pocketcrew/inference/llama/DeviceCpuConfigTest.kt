package com.browntowndev.pocketcrew.inference.llama

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for DeviceCpuConfig CPU thread calculation with big.LITTLE support.
 */
class DeviceCpuConfigTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /**
     * Creates a mock frequency reader that returns the given frequencies for each core.
     */
    private fun createFrequencyReader(coreFreqs: Map<Int, Int?>): CpuFrequencyReader {
        return CpuFrequencyReader { coreId -> coreFreqs[coreId] }
    }

    @Test
    fun `single core device returns 1 thread`() {
        // Given: single core device at 1.8GHz
        val frequencyReader = createFrequencyReader(mapOf(0 to 1800000))

        // When: calculating CPU config for single core
        val config = DeviceCpuConfig.fromDeviceProfile(1, frequencyReader)

        // Then: returns 1 thread
        assertEquals(1, config.numThreads)
        assertEquals(1, config.batchThreads)
    }

    @Test
    fun `two core device returns 2 threads`() {
        // Given: two core device
        val frequencyReader = createFrequencyReader(mapOf(0 to 1800000, 1 to 1800000))

        // When: calculating CPU config for 2 cores
        val config = DeviceCpuConfig.fromDeviceProfile(2, frequencyReader)

        // Then: returns 2 threads
        assertEquals(2, config.numThreads)
        assertEquals(2, config.batchThreads)
    }

    @Test
    fun `homogeneous multi-core device uses fallback calculation`() {
        // Given: 8-core device with all cores at same frequency (not big.LITTLE)
        val coreFreqs = (0 until 8).associateWith { 2400000 }
        val frequencyReader = createFrequencyReader(coreFreqs)

        // When: calculating CPU config
        val config = DeviceCpuConfig.fromDeviceProfile(8, frequencyReader)

        // Then: uses fallback (cpuCount - 2)
        assertEquals(6, config.numThreads)
        assertEquals(8, config.batchThreads)
    }

    @Test
    fun `big LITTLE device returns only fast cores`() {
        // Given: 9-core big.LITTLE device (5 fast @ 2.8GHz, 4 slow @ 1.8GHz) - like Pixel 8
        val coreFreqs = mutableMapOf<Int, Int?>()
        // Fast cores (big) - cores 0-4
        for (i in 0 until 5) {
            coreFreqs[i] = 2800000
        }
        // Slow cores (LITTLE) - cores 5-8
        for (i in 5 until 9) {
            coreFreqs[i] = 1800000
        }
        val frequencyReader = createFrequencyReader(coreFreqs)

        // When: calculating CPU config
        val config = DeviceCpuConfig.fromDeviceProfile(9, frequencyReader)

        // Then: returns only fast cores (5), batch uses all (9)
        assertEquals(5, config.numThreads)
        assertEquals(9, config.batchThreads)
    }

    @Test
    fun `sysfs failure falls back to naive calculation`() {
        // Given: all cores return null (sysfs unavailable)
        val frequencyReader = createFrequencyReader(mapOf(
            0 to null, 1 to null, 2 to null, 3 to null,
            4 to null, 5 to null, 6 to null, 7 to null
        ))

        // When: calculating CPU config for 8 cores
        val config = DeviceCpuConfig.fromDeviceProfile(8, frequencyReader)

        // Then: falls back to cpuCount - 2
        assertEquals(6, config.numThreads)
        assertEquals(8, config.batchThreads)
    }

    @Test
    fun `batch threads always uses all available cores`() {
        // Given: 4-core device with mixed frequencies
        val frequencyReader = createFrequencyReader(mapOf(
            0 to 2800000,  // fast
            1 to 2800000,  // fast
            2 to 1800000,  // slow
            3 to 1800000   // slow
        ))

        // When: calculating CPU config
        val config = DeviceCpuConfig.fromDeviceProfile(4, frequencyReader)

        // Then: batch threads uses all cores
        assertEquals(4, config.batchThreads)
    }

    @Test
    fun `returns at least 1 thread even with unusual frequency distribution`() {
        // Given: unusual config with very low frequencies
        val frequencyReader = createFrequencyReader(mapOf(
            0 to 1000000,  // very slow
            1 to 1000000   // very slow
        ))

        // When: calculating CPU config for 2 cores
        val config = DeviceCpuConfig.fromDeviceProfile(2, frequencyReader)

        // Then: at least 1 thread guaranteed
        assertTrue(config.numThreads >= 1)
    }

    @Test
    fun `uninitialized config returns safe defaults`() {
        // Given: DeviceCpuConfig has not been initialized
        DeviceCpuConfig.resetForTest()

        // When: accessing values before initialization
        val numThreads = DeviceCpuConfig.numThreads
        val batchThreads = DeviceCpuConfig.batchThreads
        val isInitialized = DeviceCpuConfig.isInitialized

        // Then: returns safe defaults
        assertEquals(4, numThreads) // Safe default
        assertEquals(4, batchThreads) // Safe default
        assertEquals(false, isInitialized)
    }

    @Test
    fun `three core device uses fallback`() {
        // Given: 3 cores (fallback leaves 1 for OS: max(3-2, 2) = 2)
        val frequencyReader = createFrequencyReader(mapOf(0 to 1800000, 1 to 1800000, 2 to 1800000))

        // When: calculating CPU config
        val config = DeviceCpuConfig.fromDeviceProfile(3, frequencyReader)

        // Then: uses 2 cores for generation (1 left for OS), 3 for batch
        assertEquals(2, config.numThreads)
        assertEquals(3, config.batchThreads)
    }

    @Test
    fun `four core device uses fallback subtract 2`() {
        // Given: 4 cores
        val frequencyReader = createFrequencyReader(mapOf(0 to 1800000, 1 to 1800000, 2 to 1800000, 3 to 1800000))

        // When: calculating CPU config
        val config = DeviceCpuConfig.fromDeviceProfile(4, frequencyReader)

        // Then: uses 2 threads (4 - 2)
        assertEquals(2, config.numThreads)
        assertEquals(4, config.batchThreads)
    }
}
