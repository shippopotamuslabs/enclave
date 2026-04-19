package com.browntowndev.pocketcrew.core.data.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadProgressUpdate
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.download.FileProgressInitResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadStateManagerTest {

    private lateinit var stateFlow: MutableStateFlow<DownloadState>
    private lateinit var stateManager: DownloadStateManager

    @BeforeEach
    fun setup() {
        stateFlow = MutableStateFlow(DownloadState(status = DownloadStatus.IDLE))
        stateManager = DownloadStateManager(stateFlow)
    }

    @Test
    fun `updateStatus sets status on state flow`() {
        // When
        stateManager.updateStatus(DownloadStatus.DOWNLOADING)

        // Then
        assertEquals(DownloadStatus.DOWNLOADING, stateFlow.value.status)
    }

    @Test
    fun `updateState applies transform to state`() {
        // When
        stateManager.updateState { copy(overallProgress = 0.5f) }

        // Then
        assertEquals(0.5f, stateFlow.value.overallProgress)
    }

    @Test
    fun `applyProgressInit sets all fields from init result`() {
        // Given
        val files = listOf(
            FileProgress("a.bin", "sha-a", listOf(ModelType.MAIN), 100L, 1000L, FileStatus.QUEUED, null)
        )
        val initResult = FileProgressInitResult(
            fileProgressList = files,
            modelsTotal = 3,
            modelsComplete = 0,
            overallProgress = 0f
        )

        // When
        stateManager.applyProgressInit(initResult)

        // Then
        assertEquals(files, stateFlow.value.currentDownloads)
        assertEquals(3, stateFlow.value.modelsTotal)
        assertEquals(0, stateFlow.value.modelsComplete)
        assertEquals(0f, stateFlow.value.overallProgress)
    }

    @Test
    fun `applyProgressUpdate with null currentDownloads preserves existing list`() {
        // Given - existing state with downloads
        val existingFiles = listOf(
            FileProgress("a.bin", "sha-a", listOf(ModelType.MAIN), 500L, 1000L, FileStatus.DOWNLOADING, 10.0)
        )
        stateFlow.value = stateFlow.value.copy(currentDownloads = existingFiles)

        val update = DownloadProgressUpdate(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            currentDownloads = null // No update to downloads
        )

        // When
        stateManager.applyProgressUpdate(update)

        // Then - existing list is preserved
        assertEquals(existingFiles, stateFlow.value.currentDownloads)
        assertEquals(0.5f, stateFlow.value.overallProgress)
    }

    @Test
    fun `applyProgressUpdate with non-null currentDownloads replaces existing list`() {
        // Given - existing state with downloads
        val existingFiles = listOf(
            FileProgress("a.bin", "sha-a", listOf(ModelType.MAIN), 500L, 1000L, FileStatus.DOWNLOADING, 10.0)
        )
        stateFlow.value = stateFlow.value.copy(currentDownloads = existingFiles)

        val newFiles = listOf(
            FileProgress("a.bin", "sha-a", listOf(ModelType.MAIN), 1000L, 1000L, FileStatus.COMPLETE, null),
            FileProgress("b.bin", "sha-b", listOf(ModelType.VISION), 200L, 2000L, FileStatus.DOWNLOADING, 5.0)
        )

        val update = DownloadProgressUpdate(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.75f,
            currentDownloads = newFiles
        )

        // When
        stateManager.applyProgressUpdate(update)

        // Then - new list replaces existing
        assertEquals(newFiles, stateFlow.value.currentDownloads)
        assertEquals(0.75f, stateFlow.value.overallProgress)
    }

    @Test
    fun `applyProgressUpdate with empty list replaces existing list`() {
        // Given - existing state with downloads
        val existingFiles = listOf(
            FileProgress("a.bin", "sha-a", listOf(ModelType.MAIN), 1000L, 1000L, FileStatus.COMPLETE, null)
        )
        stateFlow.value = stateFlow.value.copy(currentDownloads = existingFiles)

        val update = DownloadProgressUpdate(
            status = DownloadStatus.READY,
            overallProgress = 1.0f,
            currentDownloads = emptyList() // Explicit empty list
        )

        // When
        stateManager.applyProgressUpdate(update)

        // Then - empty list replaces existing (no preservation)
        assertEquals(emptyList<FileProgress>(), stateFlow.value.currentDownloads)
        assertEquals(DownloadStatus.READY, stateFlow.value.status)
    }

    @Test
    fun `applyProgressUpdate sets errorMessage from update`() {
        // Given
        val update = DownloadProgressUpdate(
            status = DownloadStatus.ERROR,
            errorMessage = "Network timeout"
        )

        // When
        stateManager.applyProgressUpdate(update)

        // Then
        assertEquals(DownloadStatus.ERROR, stateFlow.value.status)
        assertEquals("Network timeout", stateFlow.value.errorMessage)
    }

    @Test
    fun `applyProgressUpdate clears errorMessage when update has null errorMessage`() {
        // Given - existing error
        stateFlow.value = stateFlow.value.copy(
            status = DownloadStatus.ERROR,
            errorMessage = "Previous error"
        )

        val update = DownloadProgressUpdate(
            status = DownloadStatus.IDLE,
            errorMessage = null // Explicitly null
        )

        // When
        stateManager.applyProgressUpdate(update)

        // Then - errorMessage is cleared
        assertNull(stateFlow.value.errorMessage)
    }

    @Test
    fun `applyProgressUpdate preserves fields when update has null optional values`() {
        // Given - existing state with all fields set
        stateFlow.value = stateFlow.value.copy(
            overallProgress = 0.5f,
            modelsComplete = 1,
            modelsTotal = 3,
            estimatedTimeRemaining = "5 min",
            currentSpeedMBs = 12.5,
            waitingForUnmeteredNetwork = false
        )

        val update = DownloadProgressUpdate(
            status = DownloadStatus.DOWNLOADING
            // All optional fields are null — existing values should be preserved
        )

        // When
        stateManager.applyProgressUpdate(update)

        // Then - all existing values preserved
        assertEquals(0.5f, stateFlow.value.overallProgress)
        assertEquals(1, stateFlow.value.modelsComplete)
        assertEquals(3, stateFlow.value.modelsTotal)
        assertEquals("5 min", stateFlow.value.estimatedTimeRemaining)
        assertEquals(12.5, stateFlow.value.currentSpeedMBs)
        assertEquals(false, stateFlow.value.waitingForUnmeteredNetwork)
    }

    @Test
    fun `getCurrentState returns current state flow value`() {
        // Given
        stateFlow.value = stateFlow.value.copy(status = DownloadStatus.DOWNLOADING, overallProgress = 0.8f)

        // Then
        assertEquals(DownloadStatus.DOWNLOADING, stateManager.getCurrentState().status)
        assertEquals(0.8f, stateManager.getCurrentState().overallProgress)
    }
}