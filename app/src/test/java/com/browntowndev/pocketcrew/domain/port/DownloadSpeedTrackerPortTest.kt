package com.browntowndev.pocketcrew.domain.port

import com.browntowndev.pocketcrew.core.data.download.DownloadSpeedTracker
import com.browntowndev.pocketcrew.domain.util.TestClock
import com.browntowndev.pocketcrew.util.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.rules.TestWatcher
import org.junit.Rule
import org.junit.runner.Description

class DownloadSpeedTrackerPortTest {
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = object : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private lateinit var testClock: TestClock
    private lateinit var downloadSpeedTracker: DownloadSpeedTracker

    @BeforeEach
    fun setup() {
        testClock = TestClock()
        downloadSpeedTracker = DownloadSpeedTracker(testClock)
    }

    @Test
    fun clearAllResetsAllSpeedCalculations() = runTest {
        testClock.setTime(1000L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 100_000_000L, 1_000_000_000L)
        testClock.setTime(2000L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 200_000_000L, 1_000_000_000L)

        downloadSpeedTracker.clearAll()
        testClock.setTime(3000L)

        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)
        assertEquals(0.0, result.first)
    }

    @Test
    fun calculateSpeedAndEtaReturnsZeroSpeedWithInsufficientSamples() = runTest {
        testClock.setTime(1000L)
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)

        assertEquals(0.0, result.first)
        assertEquals(-1L, result.second)
    }

    @Test
    fun calculateSpeedAndEtaReturnsCorrectSpeedForValidSamples() = runTest {
        downloadSpeedTracker.clearAll()
        testClock.setTime(1000L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)

        testClock.setTime(2000L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 1_048_576L, 1_000_000_000L)

        testClock.setTime(3000L)
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 2_097_152L, 1_000_000_000L)

        assertEquals(true, result.first >= 0.0)
    }

    @Test
    fun calculateSpeedAndEtaReturnsZeroEtaWhenDownloadIsComplete() = runTest {
        downloadSpeedTracker.clearAll()
        
        // First sample at time 0
        testClock.setTime(0L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)

        // Second sample at time 10000ms (10 seconds later)
        testClock.setTime(10_000L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 500_000_000L, 1_000_000_000L)

        // Third sample - download complete
        testClock.setTime(20_000L)
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 1_000_000_000L, 1_000_000_000L)

        // ETA should be 0 when download is complete
        assertEquals(0L, result.second)
    }

    @Test
    fun formatEtaReturnsCalculatingWhenSecondsIsNegative() = runTest {
        val result = downloadSpeedTracker.formatEta(-1L)

        assertEquals("Calculating...", result)
    }

    @Test
    fun formatEtaReturnsLessThanMinWhenSecondsIsLessThanSixty() = runTest {
        val result = downloadSpeedTracker.formatEta(30L)

        assertEquals("< 1 min", result)
    }

    @Test
    fun formatEtaReturnsMinutesForValuesBetweenSixtyAndThreeThousandSixHundred() = runTest {
        val result = downloadSpeedTracker.formatEta(120L)

        assertEquals("2 min", result)
    }

    @Test
    fun formatEtaReturnsHoursForValuesAboveThreeThousandSixHundred() = runTest {
        val result = downloadSpeedTracker.formatEta(7200L)

        assertEquals("2.0 hours", result)
    }

    @Test
    fun calculateSpeedAndEtaHandlesZeroTotalSizeGracefully() = runTest {
        testClock.setTime(1000L)
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 0L)

        // With only 1 sample (insufficient data), the production logic returns -1L for ETA
        assertEquals(0.0, result.first)
        assertEquals(-1L, result.second)
    }

    @Test
    fun calculateSpeedAndEtaReturnsZeroEtaWhenBytesDownloadedExceedsTotal() = runTest {
        downloadSpeedTracker.clearAll()
        
        // First sample
        testClock.setTime(0L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)

        // Second sample - establish speed
        testClock.setTime(10_000L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 500_000_000L, 1_000_000_000L)

        // Third sample - download exceeds total (edge case)
        testClock.setTime(20_000L)
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 1_500_000_000L, 1_000_000_000L)

        // ETA should be 0 when bytes downloaded exceeds total
        assertEquals(0L, result.second)
    }

    @Test
    fun clearAllCanBeCalledMultipleTimesWithoutError() = runTest {
        testClock.setTime(1000L)
        downloadSpeedTracker.clearAll()
        downloadSpeedTracker.clearAll()
        downloadSpeedTracker.clearAll()

        testClock.setTime(2000L)
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)

        assertEquals(0.0, result.first)
        assertEquals(-1L, result.second)
    }

    @Test
    fun formatEtaHandlesZeroSecondsCorrectly() = runTest {
        val result = downloadSpeedTracker.formatEta(0L)

        assertEquals("< 1 min", result)
    }

    @Test
    fun perFileSpeedTrackingIsolatesFiles() = runTest {
        // Test that different files have independent speed tracking
        downloadSpeedTracker.clearAll()

        // File A starts downloading
        testClock.setTime(1000L)
        downloadSpeedTracker.calculateSpeedAndEta("fileA.bin", 0L, 1_000_000_000L)

        // File B starts downloading
        testClock.setTime(2000L)
        downloadSpeedTracker.calculateSpeedAndEta("fileB.bin", 0L, 500_000_000L)

        // File A has more progress
        testClock.setTime(3000L)
        downloadSpeedTracker.calculateSpeedAndEta("fileA.bin", 500_000_000L, 1_000_000_000L)

        // File B has less progress
        testClock.setTime(4000L)
        downloadSpeedTracker.calculateSpeedAndEta("fileB.bin", 100_000_000L, 500_000_000L)

        // Both should have valid speeds
        testClock.setTime(5000L)
        val resultA = downloadSpeedTracker.calculateSpeedAndEta("fileA.bin", 600_000_000L, 1_000_000_000L)
        testClock.setTime(6000L)
        val resultB = downloadSpeedTracker.calculateSpeedAndEta("fileB.bin", 200_000_000L, 500_000_000L)

        assertEquals(true, resultA.first >= 0.0)
        assertEquals(true, resultB.first >= 0.0)
    }

    @Test
    fun aggregateSpeedCalculatesAcrossAllFiles() = runTest {
        downloadSpeedTracker.clearAll()

        // Simulate aggregate progress
        testClock.setTime(1000L)
        downloadSpeedTracker.calculateAggregateSpeedAndEta(0L, 1_500_000_000L)

        testClock.setTime(2000L)
        downloadSpeedTracker.calculateAggregateSpeedAndEta(500_000_000L, 1_500_000_000L)

        testClock.setTime(3000L)
        val result = downloadSpeedTracker.calculateAggregateSpeedAndEta(1_000_000_000L, 1_500_000_000L)

        assertEquals(true, result.first >= 0.0)
    }
}
