package com.browntowndev.enclave.core.data.download

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.model.download.DownloadKey
import com.browntowndev.enclave.domain.model.download.FileStatus
import com.browntowndev.enclave.domain.port.download.DownloadSpeedTrackerPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadProgressTrackerTest {
    private companion object {
        const val TEST_SESSION_ID = "test-session"
    }

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
        val specs =
            listOf(
                createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main"),
                createFileSpec("vocab.bin", 500L, sha256 = "sha256_vocab"),
            )

        tracker.initialize(
            TEST_SESSION_ID,specs)

        assertEquals(2, tracker.getTotalCount())
        assertEquals(1500L, tracker.getTotalSize())
    }

    @Test
    fun initialize_mapsFilesBySha256() {
        val specs =
            listOf(
                createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main"),
                createFileSpec("vocab.bin", 500L, sha256 = "sha256_vocab"),
            )

        tracker.initialize(
            TEST_SESSION_ID,specs)

        val states = tracker.getFileStates()
        assertTrue(states.containsKey("sha256_main"))
        assertTrue(states.containsKey("sha256_vocab"))
    }

    @Test
    fun initialize_deduplicatesBySha256() {
        // Duplicate SHA256 entries should be deduplicated
        val specs =
            listOf(
                createFileSpec("shared.gguf", 1000L, sha256 = "shared_sha256"),
                createFileSpec("shared.gguf", 1000L, sha256 = "shared_sha256"),
            )

        tracker.initialize(
            TEST_SESSION_ID,specs)

        // Should only have 1 entry since they share the same SHA256
        assertEquals(1, tracker.getTotalCount())
    }

    @Test
    fun initialize_handlesEmptyList() {
        tracker.initialize(
            TEST_SESSION_ID,emptyList())

        assertEquals(0, tracker.getTotalCount())
        assertEquals(0L, tracker.getTotalSize())
    }

    @Test
    fun updateFileState_changesFileStatus() {
        tracker.initialize(
            TEST_SESSION_ID,listOf(createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main")))

        tracker.updateFileState(TEST_SESSION_ID, "sha256_main") { state ->
            state.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L)
        }

        val state = tracker.getFileStates()["sha256_main"]
        assertEquals(FileStatus.DOWNLOADING, state?.status)
        assertEquals(500L, state?.bytesDownloaded)
    }

    @Test
    fun updateFileState_throwsForUnknownFile() {
        tracker.initialize(
            TEST_SESSION_ID,listOf(createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main")))

        val exception =
            Assertions.assertThrows(IllegalStateException::class.java) {
                tracker.updateFileState(TEST_SESSION_ID, "unknown_sha256") { state ->
                    state.copy(status = FileStatus.DOWNLOADING)
                }
            }
        assertTrue(requireNotNull(exception.message).contains("Cannot update file state"))
    }

    @Test
    fun computeOverallProgress_aggregatesMultipleFiles() {
        tracker.initialize(
            TEST_SESSION_ID,
            listOf(
                createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
                createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2"),
            ),
        )

        tracker.updateFileState(TEST_SESSION_ID, "sha256_file1") { it.copy(status = FileStatus.COMPLETE, bytesDownloaded = 1000L) }
        tracker.updateFileState(TEST_SESSION_ID, "sha256_file2") { it.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L) }

        val snapshot = tracker.computeOverallProgress()

        assertEquals(1, snapshot.completedFiles)
        assertEquals(2, snapshot.totalFiles)
        assertEquals(1500L, snapshot.totalBytesDownloaded)
        assertEquals(2000L, snapshot.totalSize)
    }

    @Test
    fun computeOverallProgress_calculatesCorrectPercentage() {
        tracker.initialize(
            TEST_SESSION_ID,listOf(createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main")))

        tracker.updateFileState(TEST_SESSION_ID, "sha256_main") { it.copy(bytesDownloaded = 250L) }

        val snapshot = tracker.computeOverallProgress()

        assertEquals(0.25f, snapshot.overallProgress, 0.01f)
    }

    @Test
    fun computeOverallProgress_sumsSpeedsOfConcurrentDownloads() {
        tracker.initialize(
            TEST_SESSION_ID,
            listOf(
                createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
                createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2"),
            ),
        )

        // Mock different speeds for each file
        every { mockSpeedTracker.calculateSpeedAndEta("file1.litertlm", any(), any()) } returns Pair(4.0, 100L)
        every { mockSpeedTracker.calculateSpeedAndEta("file2.litertlm", any(), any()) } returns Pair(6.0, 150L)

        tracker.updateFileState(TEST_SESSION_ID, "sha256_file1") { it.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L) }
        tracker.updateFileState(TEST_SESSION_ID, "sha256_file2") { it.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L) }

        val snapshot = tracker.computeOverallProgress()

        // Total speed should be 4.0 + 6.0 = 10.0 (sum), not (4.0 + 6.0) / 2 = 5.0 (average)
        assertEquals(10.0, snapshot.currentSpeedMBps, 0.01)
    }

    @Test
    fun computeOverallProgress_returnsZero_whenNoFiles() {
        tracker.initialize(
            TEST_SESSION_ID,emptyList())

        val snapshot = tracker.computeOverallProgress()

        assertEquals(0f, snapshot.overallProgress)
        assertEquals(0, snapshot.totalFiles)
    }

    @Test
    fun getCompletedCount_returnsCorrectCount() {
        tracker.initialize(
            TEST_SESSION_ID,
            listOf(
                createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
                createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2"),
                createFileSpec("file3.litertlm", 1000L, sha256 = "sha256_file3"),
            ),
        )

        tracker.updateFileState(TEST_SESSION_ID, "sha256_file1") { it.copy(status = FileStatus.COMPLETE) }
        tracker.updateFileState(TEST_SESSION_ID, "sha256_file2") { it.copy(status = FileStatus.COMPLETE) }

        assertEquals(2, tracker.getCompletedCount())
    }

    @Test
    fun isAllComplete_returnsTrue_whenAllComplete() {
        tracker.initialize(
            TEST_SESSION_ID,listOf(createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1")))

        tracker.updateFileState(TEST_SESSION_ID, "sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

        assertTrue(tracker.isAllComplete())
    }

    @Test
    fun isAllComplete_returnsFalse_whenNotAllComplete() {
        tracker.initialize(
            TEST_SESSION_ID,
            listOf(
                createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
                createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2"),
            ),
        )

        tracker.updateFileState(TEST_SESSION_ID, "sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

        assertFalse(tracker.isAllComplete())
    }

    @Test
    fun getCurrentDownloadingFile_returnsDownloadingFile() {
        tracker.initialize(
            TEST_SESSION_ID,
            listOf(
                createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1"),
                createFileSpec("file2.litertlm", 1000L, sha256 = "sha256_file2"),
            ),
        )

        tracker.updateFileState(TEST_SESSION_ID, "sha256_file2") { it.copy(status = FileStatus.DOWNLOADING) }

        assertEquals("file2.litertlm", tracker.getCurrentDownloadingFile())
    }

    @Test
    fun getCurrentDownloadingFile_returnsNull_whenNoneDownloading() {
        tracker.initialize(
            TEST_SESSION_ID,listOf(createFileSpec("file1.litertlm", 1000L, sha256 = "sha256_file1")))

        tracker.updateFileState(TEST_SESSION_ID, "sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

        assertEquals(null, tracker.getCurrentDownloadingFile())
    }

    @Test
    fun updateFileState_ignoresStaleSession() {
        tracker.initialize(TEST_SESSION_ID, listOf(createFileSpec("main.litertlm", 1000L, sha256 = "sha256_main")))

        tracker.updateFileState("other-session", "sha256_main") {
            it.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 999L)
        }

        val state = tracker.getFileStates()["sha256_main"]
        assertEquals(FileStatus.QUEUED, state?.status)
        assertEquals(0L, state?.bytesDownloaded)
    }

    @Test
    fun serializeToWorkData_escapesPipeInFilename() {
        tracker.initialize(
            TEST_SESSION_ID,
            listOf(createFileSpec("weird|name.litertlm", 1000L, sha256 = "sha256_pipe")),
        )

        val filesProgress = tracker.serializeToWorkData().getStringArray(DownloadKey.FILES_PROGRESS.key)
        assertTrue(requireNotNull(filesProgress).single().startsWith("weird%7Cname.litertlm|"))
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
        sha256: String = "abc123",
    ): DownloadFileSpec =
        DownloadFileSpec(
            remoteFileName = filename,
            localFileName = filename,
            sha256 = sha256,
            sizeInBytes = sizeBytes,
            huggingFaceModelName = "model/name",
            source = "HUGGING_FACE",
            modelFileFormat = "LITERTLM",
        )
}
