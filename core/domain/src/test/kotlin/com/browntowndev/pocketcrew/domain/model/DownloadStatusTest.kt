package com.browntowndev.pocketcrew.domain.model.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadStatusTest {
    @Test
    fun `enum contains all expected values`() {
        assertEquals(6, DownloadStatus.entries.size)
        assertEquals(DownloadStatus.IDLE, DownloadStatus.valueOf("IDLE"))
        assertEquals(DownloadStatus.CHECKING, DownloadStatus.valueOf("CHECKING"))
        assertEquals(DownloadStatus.DOWNLOADING, DownloadStatus.valueOf("DOWNLOADING"))
        assertEquals(DownloadStatus.READY, DownloadStatus.valueOf("READY"))
        assertEquals(DownloadStatus.PAUSED, DownloadStatus.valueOf("PAUSED"))
        assertEquals(DownloadStatus.ERROR, DownloadStatus.valueOf("ERROR"))
    }

    @Test
    fun `IDLE is the initial state when no downloads are needed`() {
        val status = DownloadStatus.IDLE
        assertEquals(DownloadStatus.IDLE, status)
    }

    @Test
    fun `DOWNLOADING can transition to PAUSED`() {
        val originalStatus = DownloadStatus.DOWNLOADING
        val newStatus = DownloadStatus.PAUSED
        assertEquals(DownloadStatus.PAUSED, newStatus)
    }

    @Test
    fun `DOWNLOADING can transition to READY when complete`() {
        val originalStatus = DownloadStatus.DOWNLOADING
        val newStatus = DownloadStatus.READY
        assertEquals(DownloadStatus.READY, newStatus)
    }

    @Test
    fun `DOWNLOADING can transition to ERROR on failure`() {
        val originalStatus = DownloadStatus.DOWNLOADING
        val newStatus = DownloadStatus.ERROR
        assertEquals(DownloadStatus.ERROR, newStatus)
    }

    @Test
    fun `IDLE cannot transition to PAUSED directly`() {
        val status = DownloadStatus.IDLE
        assertEquals(DownloadStatus.IDLE, status)
    }

    @Test
    fun `PAUSED can transition back to DOWNLOADING`() {
        val originalStatus = DownloadStatus.PAUSED
        val newStatus = DownloadStatus.DOWNLOADING
        assertEquals(DownloadStatus.DOWNLOADING, newStatus)
    }

    @Test
    fun `ERROR can transition to DOWNLOADING for retry`() {
        val originalStatus = DownloadStatus.ERROR
        val newStatus = DownloadStatus.DOWNLOADING
        assertEquals(DownloadStatus.DOWNLOADING, newStatus)
    }

    @Test
    fun `CHECKING indicates file validation in progress`() {
        val status = DownloadStatus.CHECKING
        assertEquals(DownloadStatus.CHECKING, status)
    }

    @Test
    fun `READY indicates all models are available`() {
        val status = DownloadStatus.READY
        assertEquals(DownloadStatus.READY, status)
    }

    @Test
    fun `enum ordinals are in expected order`() {
        assertEquals(0, DownloadStatus.IDLE.ordinal)
        assertEquals(1, DownloadStatus.CHECKING.ordinal)
        assertEquals(2, DownloadStatus.DOWNLOADING.ordinal)
        assertEquals(3, DownloadStatus.READY.ordinal)
        assertEquals(4, DownloadStatus.PAUSED.ordinal)
        assertEquals(5, DownloadStatus.ERROR.ordinal)
    }
}

