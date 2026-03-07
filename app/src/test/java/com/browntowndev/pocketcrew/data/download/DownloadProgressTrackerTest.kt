package com.browntowndev.pocketcrew.data.download

import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadProgressTrackerTest {

    private lateinit var mockSpeedTracker: DownloadSpeedTrackerPort
    private lateinit var tracker: DownloadProgressTracker

    @BeforeEach
    fun setup() {
        mockSpeedTracker = mockk(relaxed = true)
        every { mockSpeedTracker.calculateSpeedAndEta(any(), any(), any()) } returns Pair(1.0, 60L)
        every { mockSpeedTracker.calculateAggregateSpeedAndEta(any(), any()) } returns Pair(2.0, 30L)
        every { mockSpeedTracker.formatEta(any()) } returns "1 min"
        tracker = DownloadProgressTracker(mockSpeedTracker)
    }

    @Test
    fun initialize_setsInitialFileStates() {
        val models = listOf(
            createModelFile("main.litertlm", 1000L),
            createModelFile("vocab.bin", 500L)
        )

        tracker.initialize(models)

        assertEquals(2, tracker.getTotalCount())
        assertEquals(1500L, tracker.getTotalSize())
    }

    @Test
    fun initialize_mapsFilesByOriginalFilename() {
        val models = listOf(
            createModelFile("main.litertlm", 1000L),
            createModelFile("vocab.bin", 500L)
        )

        tracker.initialize(models)

        val states = tracker.getFileStates()
        assertTrue(states.containsKey("main.litertlm"))
        assertTrue(states.containsKey("vocab.bin"))
    }

    @Test
    fun updateFileState_changesFileStatus() {
        tracker.initialize(listOf(createModelFile("main.litertlm", 1000L)))

        tracker.updateFileState("main.litertlm") { state ->
            state.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L)
        }

        val state = tracker.getFileStates()["main.litertlm"]
        assertEquals(FileStatus.DOWNLOADING, state?.status)
        assertEquals(500L, state?.bytesDownloaded)
    }

    @Test
    fun updateFileState_handlesUnknownFile() {
        tracker.updateFileState("unknown.litertlm") { state ->
            state.copy(status = FileStatus.DOWNLOADING)
        }

        val state = tracker.getFileStates()["unknown.litertlm"]
        assertEquals(FileStatus.DOWNLOADING, state?.status)
    }

    @Test
    fun computeOverallProgress_aggregatesMultipleFiles() {
        tracker.initialize(listOf(
            createModelFile("file1.litertlm", 1000L),
            createModelFile("file2.litertlm", 1000L)
        ))

        tracker.updateFileState("file1.litertlm") { it.copy(status = FileStatus.COMPLETE, bytesDownloaded = 1000L) }
        tracker.updateFileState("file2.litertlm") { it.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L) }

        val snapshot = tracker.computeOverallProgress()

        assertEquals(1, snapshot.completedFiles)
        assertEquals(2, snapshot.totalFiles)
        assertEquals(1500L, snapshot.totalBytesDownloaded)
        assertEquals(2000L, snapshot.totalSize)
    }

    @Test
    fun computeOverallProgress_calculatesCorrectPercentage() {
        tracker.initialize(listOf(createModelFile("main.litertlm", 1000L)))

        tracker.updateFileState("main.litertlm") { it.copy(bytesDownloaded = 250L) }

        val snapshot = tracker.computeOverallProgress()

        assertEquals(0.25f, snapshot.overallProgress, 0.01f)
    }

    @Test
    fun computeOverallProgress_returnsZero_whenNoFiles() {
        tracker.initialize(emptyList())

        val snapshot = tracker.computeOverallProgress()

        assertEquals(0f, snapshot.overallProgress)
        assertEquals(0, snapshot.totalFiles)
    }

    @Test
    fun getCompletedCount_returnsCorrectCount() {
        tracker.initialize(listOf(
            createModelFile("file1.litertlm", 1000L),
            createModelFile("file2.litertlm", 1000L),
            createModelFile("file3.litertlm", 1000L)
        ))

        tracker.updateFileState("file1.litertlm") { it.copy(status = FileStatus.COMPLETE) }
        tracker.updateFileState("file2.litertlm") { it.copy(status = FileStatus.COMPLETE) }

        assertEquals(2, tracker.getCompletedCount())
    }

    @Test
    fun isAllComplete_returnsTrue_whenAllComplete() {
        tracker.initialize(listOf(createModelFile("file1.litertlm", 1000L)))

        tracker.updateFileState("file1.litertlm") { it.copy(status = FileStatus.COMPLETE) }

        assertTrue(tracker.isAllComplete())
    }

    @Test
    fun isAllComplete_returnsFalse_whenNotAllComplete() {
        tracker.initialize(listOf(
            createModelFile("file1.litertlm", 1000L),
            createModelFile("file2.litertlm", 1000L)
        ))

        tracker.updateFileState("file1.litertlm") { it.copy(status = FileStatus.COMPLETE) }

        assertFalse(tracker.isAllComplete())
    }

    @Test
    fun getCurrentDownloadingFile_returnsDownloadingFile() {
        tracker.initialize(listOf(
            createModelFile("file1.litertlm", 1000L),
            createModelFile("file2.litertlm", 1000L)
        ))

        tracker.updateFileState("file2.litertlm") { it.copy(status = FileStatus.DOWNLOADING) }

        assertEquals("file2.litertlm", tracker.getCurrentDownloadingFile())
    }

    @Test
    fun getCurrentDownloadingFile_returnsNull_whenNoneDownloading() {
        tracker.initialize(listOf(createModelFile("file1.litertlm", 1000L)))

        tracker.updateFileState("file1.litertlm") { it.copy(status = FileStatus.COMPLETE) }

        assertEquals(null, tracker.getCurrentDownloadingFile())
    }

    @Test
    fun shouldUpdateProgress_throttlesUpdates() {
        assertTrue(tracker.shouldUpdateProgress())

        tracker.markProgressUpdated()

        assertFalse(tracker.shouldUpdateProgress())
    }

    @Test
    fun shouldLogTrace_throttlesLogging() {
        assertTrue(tracker.shouldLogTrace())

        tracker.markTraceLogged()

        assertFalse(tracker.shouldLogTrace())
    }

    private fun createModelFile(filename: String, sizeBytes: Long): ModelConfiguration {
        return ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/name",
                remoteFileName = filename,
                localFileName = filename,
                displayName = "Test Model",
                md5 = "abc123",
                sizeInBytes = sizeBytes,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are helpful")
        )
    }
}
