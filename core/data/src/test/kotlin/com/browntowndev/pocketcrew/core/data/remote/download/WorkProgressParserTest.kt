package com.browntowndev.pocketcrew.core.data.remote.download

import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.core.data.download.DownloadFinalizeWorker
import com.browntowndev.pocketcrew.core.data.download.DownloadSessionManager
import com.browntowndev.pocketcrew.core.data.download.ModelDownloadWorker
import com.browntowndev.pocketcrew.core.data.download.WorkProgressParser
import com.browntowndev.pocketcrew.core.testing.TestClock
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for WorkProgressParser.
 * Tests parsing of file progress strings including sha256 extraction and role re-mapping.
 */
class WorkProgressParserTest {

    @MockK
    private lateinit var mockSessionManager: DownloadSessionManager

    private lateinit var testClock: TestClock
    private lateinit var parser: WorkProgressParser

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        testClock = TestClock()
        parser = WorkProgressParser(mockSessionManager, testClock)
    }

    @Test
    fun `parseFileProgress parses sha256 from new format with 6 parts`() {
        // Given - progress string with sha256 (6 parts)
        // Format: filename|bytesDownloaded|totalBytes|status|speedMBs|sha256
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING|12.5|abc123sha"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        val safeResult = requireNotNull(result)
        assertEquals("vision.litertlm", safeResult.filename)
        assertEquals("abc123sha", safeResult.sha256)
        assertEquals(50_000_000L, safeResult.bytesDownloaded)
        assertEquals(100_000_000L, safeResult.totalBytes)
        assertEquals(FileStatus.DOWNLOADING, safeResult.status)
        assertEquals(12.5, safeResult.speedMBs)
    }

    @Test
    fun `parseFileProgress returns null for invalid format`() {
        // Given - invalid format (less than 6 parts)
        val progressString = "vision.litertlm|50000000"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNull(result)
    }

    @Test
    fun `parseFileProgress handles empty string gracefully`() {
        // Given - empty string
        val progressString = ""

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNull(result)
    }

    @Test
    fun `parseFileProgress handles valid status values`() {
        // Given - different status values
        val queuedString = "vision.litertlm|0|100000000|QUEUED|0.0|sha1"
        val downloadingString = "main.litertlm|50000000|100000000|DOWNLOADING|10.0|sha2"
        val completeString = "draft.litertlm|100000000|100000000|COMPLETE|0.0|sha3"
        val failedString = "fast.litertlm|30000000|100000000|FAILED|5.0|sha4"

        // Then - all should parse correctly
        assertEquals(FileStatus.QUEUED, parser.parseFileProgress(queuedString)?.status)
        assertEquals(FileStatus.DOWNLOADING, parser.parseFileProgress(downloadingString)?.status)
        assertEquals(FileStatus.COMPLETE, parser.parseFileProgress(completeString)?.status)
        assertEquals(FileStatus.FAILED, parser.parseFileProgress(failedString)?.status)
    }

    @Test
    fun `parseRunning merges modelTypes from currentDownloads using sha256`() {
        // Given - current downloads with multi-type model (DRAFT + VISION)
        val multiTypeDownload = FileProgress(
            filename = "vision.litertlm",
            sha256 = "abc123sha",
            modelTypes = listOf(ModelType.DRAFT_ONE, ModelType.VISION),
            bytesDownloaded = 0,
            totalBytes = 100_000_000,
            status = FileStatus.QUEUED,
            speedMBs = null
        )
        val currentDownloads = listOf(multiTypeDownload)

        // And - WorkInfo with progress for "vision.litertlm" (carrying the sha256)
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "vision.litertlm|50000000|100000000|DOWNLOADING|10.0|abc123sha"
                )
            }
        }

        // When
        val result = parser.parse(workInfo, currentDownloads)

        // Then - the result should have roles re-mapped from currentDownloads
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        
        val visionFileProgress = result.currentDownloads!!.find { it.sha256 == "abc123sha" }
        assertNotNull(visionFileProgress)
        
        assertTrue(visionFileProgress!!.modelTypes.contains(ModelType.DRAFT_ONE))
        assertTrue(visionFileProgress.modelTypes.contains(ModelType.VISION))
        assertEquals(2, visionFileProgress.modelTypes.size)
    }

    @Test
    fun `parseSucceeded returns ready status when work succeeds`() {
        // Given: WorkInfo with SUCCEEDED state
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(any()) } returns "current-session-id"
            }
        }

        every { mockSessionManager.isSessionStale("current-session-id") } returns false

        val currentDownloads = listOf(
            FileProgress(
                filename = "main.litertlm",
                sha256 = "sha2",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 1000000L,
                totalBytes = 1000000L,
                status = FileStatus.COMPLETE,
                speedMBs = null
            )
        )

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Return READY status with clearSession=true
        assertNotNull(result)
        assertEquals(DownloadStatus.READY, result!!.status)
    }

    @Test
    fun `parseFailed returns error with message`() {
        // Given: WorkInfo with FAILED state
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
            every { outputData } returns mockk {
                every { getString(any()) } returns "current-session-id"
                every { getString("error_message") } returns "Network error"
            }
        }

        every { mockSessionManager.isSessionStale("current-session-id") } returns false

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return ERROR status with errorMessage
        assertNotNull(result)
        assertEquals(DownloadStatus.ERROR, result!!.status)
        assertEquals("Network error", result.errorMessage)
    }

    @Test
    fun `parse returns paused status for CANCELLED`() {
        // Given: WorkInfo with CANCELLED state
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.CANCELLED
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return PAUSED status
        assertNotNull(result)
        assertEquals(DownloadStatus.PAUSED, result!!.status)
    }

    @Test
    fun `parse returns null for BLOCKED state (chain dependency, not a network issue)`() {
        // BLOCKED = chain prerequisite not satisfied (finalizer waiting on downloader).
        // This must NOT surface as WIFI_BLOCKED — it would incorrectly freeze the UI.
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.BLOCKED
            every { tags } returns setOf(DownloadFinalizeWorker::class.java.name)
            every { outputData } returns androidx.work.Data.EMPTY
            every { progress } returns androidx.work.Data.EMPTY
        }

        val result = parser.parse(workInfo, emptyList())

        assertNull(result)
    }

    @Test
    fun `parse returns CHECKING for ENQUEUED downloader within debounce window`() {
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
            every { tags } returns setOf(ModelDownloadWorker::class.java.name)
            every { outputData } returns androidx.work.Data.EMPTY
            every { progress } returns androidx.work.Data.EMPTY
            every { constraints } returns Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED).build()
        }
        testClock.setTime(1_000L)

        val result = parser.parse(workInfo, emptyList())

        assertNotNull(result)
        assertEquals(DownloadStatus.CHECKING, result!!.status)
    }

    @Test
    fun `parse returns WIFI_BLOCKED for ENQUEUED downloader after debounce with UNMETERED constraint`() {
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
            every { tags } returns setOf(ModelDownloadWorker::class.java.name)
            every { outputData } returns androidx.work.Data.EMPTY
            every { progress } returns androidx.work.Data.EMPTY
            every { constraints } returns Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED).build()
        }
        // First call sets firstEnqueuedTime = 0
        testClock.setTime(0L)
        parser.parse(workInfo, emptyList())

        // Second call is 6s later — past the 5s threshold
        testClock.setTime(6_000L)
        val result = parser.parse(workInfo, emptyList())

        assertNotNull(result)
        assertEquals(DownloadStatus.WIFI_BLOCKED, result!!.status)
        assertEquals(true, result.waitingForUnmeteredNetwork)
    }

    @Test
    fun `parse returns CHECKING for ENQUEUED downloader after debounce but with CONNECTED constraint`() {
        // Constraint is CONNECTED (not UNMETERED), so WIFI_BLOCKED should never fire
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
            every { tags } returns setOf(ModelDownloadWorker::class.java.name)
            every { outputData } returns androidx.work.Data.EMPTY
            every { progress } returns androidx.work.Data.EMPTY
            every { constraints } returns Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build()
        }
        testClock.setTime(0L)
        parser.parse(workInfo, emptyList())
        testClock.setTime(10_000L)

        val result = parser.parse(workInfo, emptyList())

        assertNotNull(result)
        assertEquals(DownloadStatus.CHECKING, result!!.status)
    }

    @Test
    fun `parse resets debounce timer when state transitions away from ENQUEUED`() {
        val enqueuedWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
            every { tags } returns setOf(ModelDownloadWorker::class.java.name)
            every { outputData } returns androidx.work.Data.EMPTY
            every { progress } returns androidx.work.Data.EMPTY
            every { constraints } returns Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED).build()
        }
        val runningWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { outputData } returns androidx.work.Data.EMPTY
            every { tags } returns setOf(ModelDownloadWorker::class.java.name)
            every { progress } returns mockk {
                every { getFloat(any(), any()) } returns 0f
                every { getInt(any(), any()) } returns 0
                every { getDouble(any(), any()) } returns 0.0
                every { getLong(any(), any()) } returns -1L
                every { getStringArray(any()) } returns null
            }
        }
        testClock.setTime(0L)
        parser.parse(enqueuedWorkInfo, emptyList())  // sets firstEnqueuedTime = 0

        parser.parse(runningWorkInfo, emptyList())   // state != ENQUEUED → resets timer

        // Enqueued again: timer starts fresh from 1s
        testClock.setTime(1_000L)
        val result = parser.parse(enqueuedWorkInfo, emptyList())

        assertNotNull(result)
        assertEquals(DownloadStatus.CHECKING, result!!.status)  // < 5s threshold
    }

    @Test
    fun `parseRunning with null FILES_PROGRESS returns null currentDownloads`() {
        // Given: WorkInfo in RUNNING state where FILES_PROGRESS key is not set
        // This happens when the worker reports aggregate progress without per-file data
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.3f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 0
                every { getInt(DownloadKey.MODELS_TOTAL.key, any()) } returns 1
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 5.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 60L
                // FILES_PROGRESS key not set — returns null
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns null
            }
            every { outputData } returns mockk {
                every { getString(any()) } returns null
            }
        }

        val currentDownloads = listOf(
            FileProgress(
                filename = "model.gguf",
                sha256 = "sha1",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 500_000L,
                totalBytes = 1_000_000L,
                status = FileStatus.DOWNLOADING,
                speedMBs = 5.0
            )
        )

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Returns update with null currentDownloads so state manager preserves existing list
        assertNotNull(result)
        assertEquals(DownloadStatus.DOWNLOADING, result!!.status)
        assertNull(result.currentDownloads, "currentDownloads should be null when FILES_PROGRESS is not set")
        assertEquals(0.3f, result.overallProgress)
    }

    @Test
    fun `parseRunning with FILES_PROGRESS set returns parsed currentDownloads`() {
        // Given: WorkInfo in RUNNING state with FILES_PROGRESS set
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, any()) } returns 1
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 12.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 120L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "model.gguf|1000000|1000000|COMPLETE|0.0|sha1"
                )
            }
            every { outputData } returns mockk {
                every { getString(any()) } returns null
            }
        }

        val currentDownloads = listOf(
            FileProgress(
                filename = "model.gguf",
                sha256 = "sha1",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 500_000L,
                totalBytes = 1_000_000L,
                status = FileStatus.DOWNLOADING,
                speedMBs = 12.0
            )
        )

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Returns update with parsed file progress from worker
        assertNotNull(result)
        assertEquals(DownloadStatus.DOWNLOADING, result!!.status)
        assertNotNull(result.currentDownloads)
        assertEquals(1, result.currentDownloads!!.size)
        assertEquals(FileStatus.COMPLETE, result.currentDownloads!![0].status)
        assertEquals(1_000_000L, result.currentDownloads!![0].bytesDownloaded)
    }

    @Test
    fun `parseRunning with empty FILES_PROGRESS returns empty currentDownloads`() {
        // Given: WorkInfo in RUNNING state with empty FILES_PROGRESS array
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 0
                every { getInt(DownloadKey.MODELS_TOTAL.key, any()) } returns 0
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 0.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns -1L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns emptyArray()
            }
            every { outputData } returns mockk {
                every { getString(any()) } returns null
            }
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Returns update with empty (non-null) currentDownloads
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        assertEquals(0, result.currentDownloads!!.size)
    }
}
