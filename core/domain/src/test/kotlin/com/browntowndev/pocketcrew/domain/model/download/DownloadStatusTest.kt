package com.browntowndev.pocketcrew.domain.model.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for DownloadStatus enum completeness.
 * Note: Tests verifying enum values equal themselves (e.g., assertEquals(IDLE, IDLE))
 * are tautological and have been removed per anti-reward-hacking rules.
 */
class DownloadStatusTest {
    @Test
    fun `enum contains all expected values`() {
        assertEquals(7, DownloadStatus.entries.size)
        assertEquals(DownloadStatus.IDLE, DownloadStatus.valueOf("IDLE"))
        assertEquals(DownloadStatus.CHECKING, DownloadStatus.valueOf("CHECKING"))
        assertEquals(DownloadStatus.DOWNLOADING, DownloadStatus.valueOf("DOWNLOADING"))
        assertEquals(DownloadStatus.WIFI_BLOCKED, DownloadStatus.valueOf("WIFI_BLOCKED"))
        assertEquals(DownloadStatus.READY, DownloadStatus.valueOf("READY"))
        assertEquals(DownloadStatus.PAUSED, DownloadStatus.valueOf("PAUSED"))
        assertEquals(DownloadStatus.ERROR, DownloadStatus.valueOf("ERROR"))
    }
}
