package com.browntowndev.pocketcrew.core.data.remote.download

import android.util.Log
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.core.data.download.WorkProgressParser
import com.browntowndev.pocketcrew.core.data.download.DownloadSessionManager
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Unit tests for WorkProgressParser.
 * Tests parsing of file progress strings including sha256 extraction and role re-mapping.
 */
class WorkProgressParserTest {

    @MockK
    private lateinit var mockSessionManager: DownloadSessionManager

    private lateinit var parser: WorkProgressParser

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        parser = WorkProgressParser(mockSessionManager)
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
    fun `parse returns wifi_blocked status when blocked`() {
        // Given: WorkInfo with BLOCKED state
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.BLOCKED
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return WIFI_BLOCKED status
        assertNotNull(result)
        assertEquals(DownloadStatus.WIFI_BLOCKED, result!!.status)
        assertEquals(true, result.waitingForUnmeteredNetwork)
    }
}
