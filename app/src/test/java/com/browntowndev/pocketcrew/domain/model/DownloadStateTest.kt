package com.browntowndev.pocketcrew.domain.model.download

import com.browntowndev.pocketcrew.util.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadStateTest {
    @Test
    fun `copy preserves unchanged fields`() {
        val original = TestFixtures.downloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            modelsTotal = 3,
            modelsComplete = 1
        )
        
        val copied = original.copy(status = DownloadStatus.READY)
        
        assertEquals(DownloadStatus.READY, copied.status)
        assertEquals(0.5f, copied.overallProgress)
        assertEquals(3, copied.modelsTotal)
        assertEquals(1, copied.modelsComplete)
    }

    @Test
    fun `copy updates specified fields only`() {
        val original = TestFixtures.downloadState(
            status = DownloadStatus.IDLE,
            overallProgress = 0f,
            errorMessage = null
        )
        
        val copied = original.copy(
            status = DownloadStatus.ERROR,
            errorMessage = "Network failed"
        )
        
        assertEquals(DownloadStatus.ERROR, copied.status)
        assertEquals(0f, copied.overallProgress)
        assertEquals("Network failed", copied.errorMessage)
    }

    @Test
    fun `overallProgress can be set to zero`() {
        val state = TestFixtures.downloadState(overallProgress = 0f)
        assertEquals(0f, state.overallProgress)
    }

    @Test
    fun `overallProgress can be set to one`() {
        val state = TestFixtures.downloadState(overallProgress = 1f)
        assertEquals(1f, state.overallProgress)
    }

    @Test
    fun `overallProgress accepts intermediate values`() {
        val state = TestFixtures.downloadState(overallProgress = 0.75f)
        assertEquals(0.75f, state.overallProgress)
    }

    @Test
    fun `empty currentDownloads list is valid`() {
        val state = TestFixtures.downloadState(
            currentDownloads = emptyList()
        )
        assertTrue(state.currentDownloads.isEmpty())
    }

    @Test
    fun `modelsTotal can be zero`() {
        val state = TestFixtures.downloadState(modelsTotal = 0)
        assertEquals(0, state.modelsTotal)
    }

    @Test
    fun `modelsComplete can be zero`() {
        val state = TestFixtures.downloadState(modelsComplete = 0)
        assertEquals(0, state.modelsComplete)
    }

    @Test
    fun `estimatedTimeRemaining can be null`() {
        val state = TestFixtures.downloadState(estimatedTimeRemaining = null)
        assertNull(state.estimatedTimeRemaining)
    }

    @Test
    fun `estimatedTimeRemaining can have value`() {
        val state = TestFixtures.downloadState(estimatedTimeRemaining = "23 min")
        assertEquals("23 min", state.estimatedTimeRemaining)
    }

    @Test
    fun `currentSpeedMBs can be null when idle`() {
        val state = TestFixtures.downloadState(
            status = DownloadStatus.IDLE,
            currentSpeedMBs = null
        )
        assertNull(state.currentSpeedMBs)
    }

    @Test
    fun `currentSpeedMBs can have positive value`() {
        val state = TestFixtures.downloadState(currentSpeedMBs = 12.4)
        assertEquals(12.4, state.currentSpeedMBs)
    }

    @Test
    fun `errorMessage can be null when no error`() {
        val state = TestFixtures.downloadState(errorMessage = null)
        assertNull(state.errorMessage)
    }

    @Test
    fun `errorMessage can have error description`() {
        val state = TestFixtures.downloadState(errorMessage = "Download failed")
        assertEquals("Download failed", state.errorMessage)
    }

    @Test
    fun `wifiBlocked defaults to false`() {
        val state = TestFixtures.downloadState()
        assertFalse(state.wifiBlocked)
    }

    @Test
    fun `wifiBlocked can be true when WiFi-only setting blocks download`() {
        val state = TestFixtures.downloadState(wifiBlocked = true)
        assertTrue(state.wifiBlocked)
    }

    @Test
    fun `default values are correct`() {
        val state = DownloadState(status = DownloadStatus.IDLE)
        
        assertEquals(DownloadStatus.IDLE, state.status)
        assertEquals(0f, state.overallProgress)
        assertEquals(0, state.modelsTotal)
        assertEquals(0, state.modelsComplete)
        assertTrue(state.currentDownloads.isEmpty())
        assertNull(state.estimatedTimeRemaining)
        assertNull(state.currentSpeedMBs)
        assertNull(state.errorMessage)
        assertFalse(state.wifiBlocked)
    }

    @Test
    fun `equality works correctly for equal states`() {
        val state1 = TestFixtures.downloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f
        )
        val state2 = TestFixtures.downloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f
        )
        
        assertEquals(state1, state2)
    }

    @Test
    fun `equality fails for different states`() {
        val state1 = TestFixtures.downloadState(
            status = DownloadStatus.DOWNLOADING
        )
        val state2 = TestFixtures.downloadState(
            status = DownloadStatus.PAUSED
        )
        
        assertFalse(state1 == state2)
    }

    @Test
    fun `toString contains all fields`() {
        val state = TestFixtures.downloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            modelsTotal = 3,
            modelsComplete = 1
        )
        
        val stringRepresentation = state.toString()
        
        assertTrue(stringRepresentation.contains("DOWNLOADING"))
        assertTrue(stringRepresentation.contains("0.5"))
        assertTrue(stringRepresentation.contains("3"))
        assertTrue(stringRepresentation.contains("1"))
    }
}

