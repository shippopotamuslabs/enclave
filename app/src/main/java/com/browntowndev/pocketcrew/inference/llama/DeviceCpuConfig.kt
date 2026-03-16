package com.browntowndev.pocketcrew.inference.llama

import android.util.Log

private const val TAG = "DeviceCpuConfig"

/**
 * Functional interface for reading CPU core frequencies.
 * Allows for testing without actual file I/O.
 */
fun interface CpuFrequencyReader {
    /**
     * Read the maximum frequency for a given core.
     * @param coreId The CPU core ID (e.g., 0, 1, 2...)
     * @return The frequency in kHz, or null if not available
     */
    fun readMaxFrequency(coreId: Int): Int?
}

/**
 * Default implementation that reads from sysfs.
 */
object SysfsCpuFrequencyReader : CpuFrequencyReader {
    override fun readMaxFrequency(coreId: Int): Int? {
        return try {
            val freqFile = java.io.File("/sys/devices/system/cpu/cpu$coreId/cpufreq/cpuinfo_max_freq")
            if (freqFile.exists()) {
                freqFile.readText().trim().toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Singleton that holds CPU configuration for llama.cpp inference.
 *
 * This class dynamically profiles the device's CPU to detect big.LITTLE architecture
 * and calculates the optimal number of threads for both generation and prompt processing.
 *
 * - `numThreads`: Used during token-by-token generation. On big.LITTLE devices,
 *   this only uses the fast "big" cores to avoid bottlenecking on slow efficiency cores.
 * - `batchThreads`: Used during prompt evaluation (reading the initial prompt).
 *   This can use all available cores since prompt processing scales well across cores.
 *
 * Initialization happens at app startup (in MainViewModel) to avoid blocking I/O during DI.
 */
object DeviceCpuConfig {

    /**
     * Number of threads for token generation (n_threads in llama.cpp).
     * On big.LITTLE devices, this is limited to fast cores only.
     */
    var numThreads: Int = DEFAULT_THREADS
        private set

    /**
     * Number of threads for prompt processing (n_threads_batch in llama.cpp).
     * Uses all available cores for better prompt evaluation performance.
     */
    var batchThreads: Int = DEFAULT_THREADS
        private set

    /**
     * Whether the CPU configuration has been initialized with profiled values.
     */
    var isInitialized: Boolean = false
        private set

    private const val DEFAULT_THREADS = 4

    /**
     * Initialize the CPU configuration by profiling the device's CPU cores.
     * This should be called once at app startup in a coroutine.
     *
     * @param availableProcessors The number of available processors from Runtime
     */
    fun initialize(availableProcessors: Int) {
        val profile = profileCpuCores(availableProcessors, SysfsCpuFrequencyReader)

        numThreads = profile.generationThreads
        batchThreads = profile.batchThreads
        isInitialized = true

        Log.i(TAG, "Device CPU configured: generationThreads=$numThreads, batchThreads=$batchThreads")
    }

    /**
     * Create a configuration profile from a device with the given number of processors.
     * This is used for testing with a custom frequency reader.
     *
     * @param availableProcessors Number of logical processors
     * @param frequencyReader Reader for CPU frequencies (for testing)
     * @return CpuProfile with calculated thread counts
     */
    fun fromDeviceProfile(availableProcessors: Int, frequencyReader: CpuFrequencyReader): CpuProfile {
        return profileCpuCores(availableProcessors, frequencyReader)
    }

    /**
     * Profiles CPU cores to detect big.LITTLE architecture and calculate optimal threads.
     */
    private fun profileCpuCores(availableProcessors: Int, frequencyReader: CpuFrequencyReader): CpuProfile {
        val coreFreqs = mutableListOf<Int>()

        // Read max frequency for each core
        for (i in 0 until availableProcessors) {
            val freq = frequencyReader.readMaxFrequency(i)
            if (freq != null && freq > 0) {
                coreFreqs.add(freq)
            }
        }

        // If we couldn't read any frequencies, use fallback calculation
        if (coreFreqs.isEmpty()) {
            Log.w(TAG, "Could not read CPU frequencies, using fallback calculation")
            return fallbackProfile(availableProcessors)
        }

        val maxFreq = coreFreqs.maxOrNull() ?: 0
        val minFreq = coreFreqs.minOrNull() ?: 0

        // If all cores have the same max frequency, it's not a big.LITTLE architecture.
        // Use fallback (leave 1-2 cores for OS/UI).
        if (maxFreq == minFreq) {
            Log.i(TAG, "Homogeneous CPU detected (all cores at ${maxFreq / 1000}MHz), using fallback")
            return fallbackProfile(availableProcessors)
        }

        // Calculate threshold to identify fast cores.
        // A core is considered "fast" if its max frequency is in the top tier.
        // Using 2/3 threshold: cores in the top 1/3 of frequency range are considered "big" cores.
        val threshold = minFreq + ((maxFreq - minFreq) * 2 / 3)

        // Count fast cores (above threshold)
        val fastCoreCount = coreFreqs.count { it >= threshold }

        // For generation: use only fast cores, but at least 1
        val generationThreads = fastCoreCount.coerceAtLeast(1)

        // For batch processing: use all cores
        val batchThreads = availableProcessors

        Log.i(TAG, "big.LITTLE detected: $fastCoreCount fast cores (${maxFreq / 1000}MHz), " +
                "$availableProcessors total. Generation: $generationThreads, Batch: $batchThreads")

        return CpuProfile(
            generationThreads = generationThreads,
            batchThreads = batchThreads
        )
    }

    /**
     * Fallback calculation when sysfs is not accessible.
     * Leaves 1-2 cores for the OS and UI thread.
     */
    private fun fallbackProfile(availableProcessors: Int): CpuProfile {
        val generationThreads = when {
            availableProcessors <= 2 -> availableProcessors
            else -> maxOf(availableProcessors - 2, 2)
        }

        return CpuProfile(
            generationThreads = generationThreads,
            batchThreads = availableProcessors
        )
    }

    /**
     * Reset for testing purposes only.
     */
    fun resetForTest() {
        numThreads = DEFAULT_THREADS
        batchThreads = DEFAULT_THREADS
        isInitialized = false
    }

    /**
     * Data class representing CPU configuration profile.
     */
    data class CpuProfile(
        val generationThreads: Int,
        val batchThreads: Int
    ) {
        val numThreads: Int get() = generationThreads
    }
}
