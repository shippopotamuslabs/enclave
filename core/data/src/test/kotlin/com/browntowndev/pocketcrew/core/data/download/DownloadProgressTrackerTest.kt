package com.browntowndev.pocketcrew.core.data.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        every { mockSpeedTracker.formatEta(any()) } returns "1 min"
        tracker = DownloadProgressTracker(mockSpeedTracker)
    }

    @Test
    fun initialize_setsInitialFileStates() {
        val specs = listOf(
            createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main"),
            createFileSpec("vocab.bin", 500L, sha256 = "sha256_vocab")
        )

        tracker.initialize(specs)

        assertEquals(2, tracker.getTotalCount())
        assertEquals(1500L, tracker.getTotalSize())
    }

    @Test
    fun initialize_mapsFilesBySha256() {
        val specs = listOf(
            createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main"),
            createFileSpec("vocab.bin", 500L, sha256 = "sha256_vocab")
        )

        tracker.initialize(specs)

        val states = tracker.getFileStates()
        assertTrue(states.containsKey("sha256_main"))
        assertTrue(states.containsKey("sha256_vocab"))
    }

    @Test
    fun initialize_deduplicatesBySha256() {
        // Duplicate SHA256 entries should be deduplicated
        val specs = listOf(
            createFileSpec("shared.gguf", 1000L, sha256 = "shared_sha256"),
            createFileSpec("shared.gguf", 1000L, sha256 = "shared_sha256")
        )

        tracker.initialize(specs)

        // Should only have 1 entry since they share the same SHA256
        assertEquals(1, tracker.getTotalCount())
    }

    @Test
    fun initialize_handlesEmptyList() {
        tracker.initialize(emptyList())

        assertEquals(0, tracker.getTotalCount())
        assertEquals(0L, tracker.getTotalSize())
    }

    @Test
    fun updateFileState_changesFileStatus() {
        tracker.initialize(listOf(createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main")))

        tracker.updateFileState("sha256_main") { state ->
            state.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L)
        }

        val state = tracker.getFileStates()["sha256_main"]
        assertEquals(FileStatus.DOWNLOADING, state?.status)
        assertEquals(500L, state?.bytesDownloaded)
    }

    @Test
    fun updateFileState_throwsForUnknownFile() {
        tracker.initialize(listOf(createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main")))

        val exception = Assertions.assertThrows(IllegalStateException::class.java) {
            tracker.updateFileState("unknown_sha256") { state ->
                state.copy(status = FileStatus.DOWNLOADING)
            }
        }
        assertTrue(requireNotNull(exception.message).contains("Cannot update file state"))
    }

    @Test
    fun computeOverallProgress_aggregatesMultipleFiles() {
        tracker.initialize(listOf(
            createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2")
        ))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE, bytesDownloaded = 1000L) }
        tracker.updateFileState("sha256_file2") { it.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L) }

        val snapshot = tracker.computeOverallProgress()

        assertEquals(1, snapshot.completedFiles)
        assertEquals(2, snapshot.totalFiles)
        assertEquals(1500L, snapshot.totalBytesDownloaded)
        assertEquals(2000L, snapshot.totalSize)
    }

    @Test
    fun computeOverallProgress_calculatesCorrectPercentage() {
        tracker.initialize(listOf(createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main")))

        tracker.updateFileState("sha256_main") { it.copy(bytesDownloaded = 250L) }

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
            createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2"),
            createFileSpec("file3.litertlm", 1000L, sha256 = "sha256_file3")
        ))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE) }
        tracker.updateFileState("sha256_file2") { it.copy(status = FileStatus.COMPLETE) }

        assertEquals(2, tracker.getCompletedCount())
    }

    @Test
    fun isAllComplete_returnsTrue_whenAllComplete() {
        tracker.initialize(listOf(createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1")))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

        assertTrue(tracker.isAllComplete())
    }

    @Test
    fun isAllComplete_returnsFalse_whenNotAllComplete() {
        tracker.initialize(listOf(
            createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2")
        ))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

        assertFalse(tracker.isAllComplete())
    }

    @Test
    fun getCurrentDownloadingFile_returnsDownloadingFile() {
        tracker.initialize(listOf(
            createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2")
        ))

        tracker.updateFileState("sha256_file2") { it.copy(status = FileStatus.DOWNLOADING) }

        assertEquals("file2.litertlm", tracker.getCurrentDownloadingFile())
    }

    @Test
    fun getCurrentDownloadingFile_returnsNull_whenNoneDownloading() {
        tracker.initialize(listOf(createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1")))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

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

    private fun createFileSpec(
        filename: String,
        sizeBytes: Long,
        sha256: String = "abc123"
    ): DownloadFileSpec {
        return DownloadFileSpec(
            remoteFileName = filename,
            localFileName = filename,
            sha256 = sha256,
            sizeInBytes = sizeBytes,
            huggingFaceModelName = "model/name",
            source = "HUGGING_FACE",
            modelFileFormat = "LITERTLM"
        )
    }
}