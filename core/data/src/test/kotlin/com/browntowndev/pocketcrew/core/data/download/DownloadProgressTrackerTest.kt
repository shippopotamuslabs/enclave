package com.browntowndev.pocketcrew.core.data.download

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
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
        val models = mapOf(
            ModelType.MAIN to createModelFile("main.litertlm", 1000L, sha256 = "sha256_main"),
            ModelType.FAST to createModelFile("vocab.bin", 500L, sha256 = "sha256_vocab")
        )

        tracker.initialize(models)

        assertEquals(2, tracker.getTotalCount())
        assertEquals(1500L, tracker.getTotalSize())
    }

    @Test
    fun initialize_mapsFilesBySha256() {
        val models = mapOf(
            ModelType.MAIN to createModelFile("main.litertlm", 1000L, sha256 = "sha256_main"),
            ModelType.FAST to createModelFile("vocab.bin", 500L, sha256 = "sha256_vocab")
        )

        tracker.initialize(models)

        val states = tracker.getFileStates()
        assertTrue(states.containsKey("sha256_main"))
        assertTrue(states.containsKey("sha256_vocab"))
    }

    @Test
    fun initialize_combinesMultipleModelTypesWithSameSha256() {
        // Create models with same SHA256 but different model types (simulating shared files)
        val models = mapOf(
            ModelType.MAIN to createModelFile("shared.gguf", 1000L, sha256 = "shared_sha256"),
            ModelType.FAST to createModelFile("shared.gguf", 1000L, sha256 = "shared_sha256")
        )

        tracker.initialize(models)

        // Should only have 1 entry since they share the same SHA256
        assertEquals(1, tracker.getTotalCount())

        // The combined entry should have both model types
        val state = tracker.getFileStates()["shared_sha256"]
        assertEquals(2, state?.allModelTypesForFile?.size)
        assertTrue(state?.allModelTypesForFile?.contains(ModelType.MAIN) == true)
        assertTrue(state?.allModelTypesForFile?.contains(ModelType.FAST) == true)
    }

    @Test
    fun serializeToWorkData_includesAllModelTypesForSharedFile() {
        val models = mapOf(
            ModelType.MAIN to createModelFile("shared.gguf", 1000L, sha256 = "shared_sha256"),
            ModelType.FAST to createModelFile("shared.gguf", 1000L, sha256 = "shared_sha256")
        )

        tracker.initialize(models)

        val workData = tracker.serializeToWorkData()
        val filesProgress = workData.getStringArray("files_progress")

        assertEquals(1, filesProgress?.size)
        // Should contain both model types separated by comma
        assertTrue(filesProgress?.get(0)?.contains("main,fast") == true)
    }

    @Test
    fun updateFileState_changesFileStatus() {
        tracker.initialize(mapOf(ModelType.MAIN to createModelFile("main.litertlm", 1000L, sha256 = "sha256_main")))

        tracker.updateFileState("sha256_main") { state ->
            state.copy(status = FileStatus.DOWNLOADING, bytesDownloaded = 500L)
        }

        val state = tracker.getFileStates()["sha256_main"]
        assertEquals(FileStatus.DOWNLOADING, state?.status)
        assertEquals(500L, state?.bytesDownloaded)
    }

    @Test
    fun updateFileState_throwsForUnknownFile() {
        tracker.initialize(mapOf(ModelType.MAIN to createModelFile("main.litertlm", 1000L, sha256 = "sha256_main")))

        val exception = Assertions.assertThrows(IllegalStateException::class.java) {
            tracker.updateFileState("unknown_sha256") { state ->
                state.copy(status = FileStatus.DOWNLOADING)
            }
        }
        assertTrue(requireNotNull(exception.message).contains("Cannot update file state"))
    }

    @Test
    fun computeOverallProgress_aggregatesMultipleFiles() {
        tracker.initialize(mapOf(
            ModelType.MAIN to createModelFile("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            ModelType.FAST to createModelFile("file2.litertlm", 1000L, sha256 = "sha256_file2")
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
        tracker.initialize(mapOf(ModelType.MAIN to createModelFile("main.litertlm", 1000L, sha256 = "sha256_main")))

        tracker.updateFileState("sha256_main") { it.copy(bytesDownloaded = 250L) }

        val snapshot = tracker.computeOverallProgress()

        assertEquals(0.25f, snapshot.overallProgress, 0.01f)
    }

    @Test
    fun computeOverallProgress_returnsZero_whenNoFiles() {
        tracker.initialize(emptyMap())

        val snapshot = tracker.computeOverallProgress()

        assertEquals(0f, snapshot.overallProgress)
        assertEquals(0, snapshot.totalFiles)
    }

    @Test
    fun getCompletedCount_returnsCorrectCount() {
        tracker.initialize(mapOf(
            ModelType.MAIN to createModelFile("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            ModelType.FAST to createModelFile("file2.litertlm", 1000L, sha256 = "sha256_file2"),
            ModelType.VISION to createModelFile("file3.litertlm", 1000L, sha256 = "sha256_file3")
        ))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE) }
        tracker.updateFileState("sha256_file2") { it.copy(status = FileStatus.COMPLETE) }

        assertEquals(2, tracker.getCompletedCount())
    }

    @Test
    fun isAllComplete_returnsTrue_whenAllComplete() {
        tracker.initialize(mapOf(ModelType.MAIN to createModelFile("file1.litertlm", 1000L, sha256 = "sha256_file1")))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

        assertTrue(tracker.isAllComplete())
    }

    @Test
    fun isAllComplete_returnsFalse_whenNotAllComplete() {
        tracker.initialize(mapOf(
            ModelType.MAIN to createModelFile("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            ModelType.FAST to createModelFile("file2.litertlm", 1000L, sha256 = "sha256_file2")
        ))

        tracker.updateFileState("sha256_file1") { it.copy(status = FileStatus.COMPLETE) }

        assertFalse(tracker.isAllComplete())
    }

    @Test
    fun getCurrentDownloadingFile_returnsDownloadingFile() {
        tracker.initialize(mapOf(
            ModelType.MAIN to createModelFile("file1.litertlm", 1000L, sha256 = "sha256_file1"),
            ModelType.FAST to createModelFile("file2.litertlm", 1000L, sha256 = "sha256_file2")
        ))

        tracker.updateFileState("sha256_file2") { it.copy(status = FileStatus.DOWNLOADING) }

        assertEquals("file2.litertlm", tracker.getCurrentDownloadingFile())
    }

    @Test
    fun getCurrentDownloadingFile_returnsNull_whenNoneDownloading() {
        tracker.initialize(mapOf(ModelType.MAIN to createModelFile("file1.litertlm", 1000L, sha256 = "sha256_file1")))

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

    private fun createModelFile(filename: String, sizeBytes: Long, sha256: String = "abc123"): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "model/name",
                remoteFileName = filename,
                localFileName = filename,
                displayName = "Test Model",
                sha256 = sha256,
                sizeInBytes = sizeBytes,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            configurations = emptyList()
        )
    }
}
