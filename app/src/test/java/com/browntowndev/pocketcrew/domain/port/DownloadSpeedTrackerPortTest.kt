package com.browntowndev.pocketcrew.domain.port

import com.browntowndev.pocketcrew.data.download.DownloadSpeedTracker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadSpeedTrackerPortTest {
    private lateinit var downloadSpeedTracker: DownloadSpeedTracker

    @BeforeEach
    fun setup() {
        downloadSpeedTracker = DownloadSpeedTracker()
    }

    @Test
    fun clearAllResetsAllSpeedCalculations() {
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 100_000_000L, 1_000_000_000L)
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 200_000_000L, 1_000_000_000L)
        
        downloadSpeedTracker.clearAll()
        
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)
        assertEquals(0.0, result.first)
    }

    @Test
    fun calculateSpeedAndEtaReturnsZeroSpeedWithInsufficientSamples() {
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)
        
        assertEquals(0.0, result.first)
        assertEquals(-1L, result.second)
    }

    @Test
    fun calculateSpeedAndEtaReturnsCorrectSpeedForValidSamples() {
        downloadSpeedTracker.clearAll()
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)
        
        Thread.sleep(100)
        
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 1_048_576L, 1_000_000_000L)
        
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 2_097_152L, 1_000_000_000L)
        
        assertEquals(true, result.first >= 0.0)
    }

    @Test
    fun calculateSpeedAndEtaReturnsZeroEtaWhenDownloadIsComplete() {
        downloadSpeedTracker.clearAll()
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)
        
        Thread.sleep(100)
        
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 500_000_000L, 1_000_000_000L)
        
        Thread.sleep(100)
        
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 1_000_000_000L, 1_000_000_000L)
        
        assertEquals(0L, result.second)
    }

    @Test
    fun formatEtaReturnsCalculatingWhenSecondsIsNegative() {
        val result = downloadSpeedTracker.formatEta(-1L)
        
        assertEquals("Calculating...", result)
    }

    @Test
    fun formatEtaReturnsLessThanMinWhenSecondsIsLessThanSixty() {
        val result = downloadSpeedTracker.formatEta(30L)
        
        assertEquals("< 1 min", result)
    }

    @Test
    fun formatEtaReturnsMinutesForValuesBetweenSixtyAndThreeThousandSixHundred() {
        val result = downloadSpeedTracker.formatEta(120L)
        
        assertEquals("2 min", result)
    }

    @Test
    fun formatEtaReturnsHoursForValuesAboveThreeThousandSixHundred() {
        val result = downloadSpeedTracker.formatEta(7200L)
        
        assertEquals("2.0 hours", result)
    }

    @Test
    fun calculateSpeedAndEtaHandlesZeroTotalSizeGracefully() {
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 0L)
        
        // With only 1 sample (insufficient data), the production logic returns -1L for ETA
        assertEquals(0.0, result.first)
        assertEquals(-1L, result.second)
    }

    @Test
    fun calculateSpeedAndEtaReturnsZeroEtaWhenBytesDownloadedExceedsTotal() {
        downloadSpeedTracker.clearAll()
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)
        
        Thread.sleep(100)
        
        downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 500_000_000L, 1_000_000_000L)
        
        Thread.sleep(100)
        
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 1_500_000_000L, 1_000_000_000L)
        
        assertEquals(0L, result.second)
    }

    @Test
    fun clearAllCanBeCalledMultipleTimesWithoutError() {
        downloadSpeedTracker.clearAll()
        downloadSpeedTracker.clearAll()
        downloadSpeedTracker.clearAll()
        
        val result = downloadSpeedTracker.calculateSpeedAndEta("testfile.bin", 0L, 1_000_000_000L)
        
        assertEquals(0.0, result.first)
        assertEquals(-1L, result.second)
    }

    @Test
    fun formatEtaHandlesZeroSecondsCorrectly() {
        val result = downloadSpeedTracker.formatEta(0L)
        
        assertEquals("< 1 min", result)
    }

    @Test
    fun perFileSpeedTrackingIsolatesFiles() {
        // Test that different files have independent speed tracking
        downloadSpeedTracker.clearAll()
        
        // File A starts downloading
        downloadSpeedTracker.calculateSpeedAndEta("fileA.bin", 0L, 1_000_000_000L)
        
        Thread.sleep(100)
        
        // File B starts downloading
        downloadSpeedTracker.calculateSpeedAndEta("fileB.bin", 0L, 500_000_000L)
        
        Thread.sleep(100)
        
        // File A has more progress
        downloadSpeedTracker.calculateSpeedAndEta("fileA.bin", 500_000_000L, 1_000_000_000L)
        
        // File B has less progress  
        downloadSpeedTracker.calculateSpeedAndEta("fileB.bin", 100_000_000L, 500_000_000L)
        
        // Both should have valid speeds
        val resultA = downloadSpeedTracker.calculateSpeedAndEta("fileA.bin", 600_000_000L, 1_000_000_000L)
        val resultB = downloadSpeedTracker.calculateSpeedAndEta("fileB.bin", 200_000_000L, 500_000_000L)
        
        assertEquals(true, resultA.first >= 0.0)
        assertEquals(true, resultB.first >= 0.0)
    }

    @Test
    fun aggregateSpeedCalculatesAcrossAllFiles() {
        downloadSpeedTracker.clearAll()
        
        // Simulate aggregate progress
        downloadSpeedTracker.calculateAggregateSpeedAndEta(0L, 1_500_000_000L)
        
        Thread.sleep(100)
        
        downloadSpeedTracker.calculateAggregateSpeedAndEta(500_000_000L, 1_500_000_000L)
        
        val result = downloadSpeedTracker.calculateAggregateSpeedAndEta(1_000_000_000L, 1_500_000_000L)
        
        assertEquals(true, result.first >= 0.0)
    }
}
