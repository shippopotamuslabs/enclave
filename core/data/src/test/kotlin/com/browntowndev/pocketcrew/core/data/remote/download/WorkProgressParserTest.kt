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
 * Tests parsing of file progress strings including modelTypes extraction.
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
    fun `parseFileProgress parses modelTypes from new format with 6 parts`() {
        // Given - progress string with modelTypes (6 parts)
        // Format: filename|bytesDownloaded|totalBytes|status|speedMBs|modelTypes
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING|12.5|vision"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        val safeResult = requireNotNull(result)
        assertEquals("vision.litertlm", safeResult.filename)
        assertEquals(50_000_000L, safeResult.bytesDownloaded)
        assertEquals(100_000_000L, safeResult.totalBytes)
        assertEquals(FileStatus.DOWNLOADING, safeResult.status)
        assertEquals(12.5, safeResult.speedMBs)
        assertTrue(safeResult.modelTypes.isNotEmpty())
        assertEquals(ModelType.VISION, safeResult.modelTypes.first())
    }

    @Test
    fun `parseFileProgress parses multiple modelTypes from comma-separated list`() {
        // Given - progress string with multiple modelTypes
        val progressString = "main.litertlm|0|200000000|QUEUED|0.0|main,draft_one"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        val safeResult = requireNotNull(result)
        assertEquals("main.litertlm", safeResult.filename)
        assertEquals(2, safeResult.modelTypes.size)
        assertTrue(safeResult.modelTypes.contains(ModelType.MAIN))
        assertTrue(safeResult.modelTypes.contains(ModelType.DRAFT_ONE))
    }

    @Test
    fun `parseFileProgress parses all modelTypes correctly`() {
        // Given - progress string with all modelTypes (using correct apiValue strings)
        val progressString = "fast.litertlm|0|50000000|QUEUED|0.0|fast,vision,main,draft_one,draft_two"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        val safeResult = requireNotNull(result)
        assertEquals(5, safeResult.modelTypes.size)
        assertTrue(safeResult.modelTypes.contains(ModelType.FAST))
        assertTrue(safeResult.modelTypes.contains(ModelType.VISION))
        assertTrue(safeResult.modelTypes.contains(ModelType.MAIN))
        assertTrue(safeResult.modelTypes.contains(ModelType.DRAFT_ONE))
        assertTrue(safeResult.modelTypes.contains(ModelType.DRAFT_TWO))
    }

    @Test
    fun `parseFileProgress handles old format without modelTypes returns null`() {
        // Given - old format (4 parts, no modelTypes) - this now returns null because we need 6 parts
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then - old format returns null because it doesn't have enough parts
        assertNull(result)
    }

    @Test
    fun `parseFileProgress handles old format with speed but no modelTypes returns null`() {
        // Given - old format with speed (5 parts, no modelTypes) - this now returns null
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING|12.5"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then - old format returns null because it doesn't have enough parts
        assertNull(result)
    }

    @Test
    fun `parseFileProgress returns null for invalid format`() {
        // Given - invalid format (less than 4 parts)
        val progressString = "vision.litertlm|50000000"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNull(result)
    }

    @Test
    fun `parseFileProgress handles blank modelTypes field`() {
        // Given - 6th part is blank
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING|12.5|"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        val safeResult = requireNotNull(result)
        assertTrue(safeResult.modelTypes.isEmpty())
    }

    @Test
    fun `parseFileProgress defaults unknown modelTypes to MAIN`() {
        // Given - modelType that doesn't match any enum
        val progressString = "unknown.litertlm|0|100000000|QUEUED|0.0|unknown_model"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        val safeResult = requireNotNull(result)
        assertTrue(safeResult.modelTypes.isNotEmpty())
        // Unknown values default to MAIN per ModelType.fromApiValue
        assertEquals(ModelType.MAIN, safeResult.modelTypes.first())
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
        val queuedString = "vision.litertlm|0|100000000|QUEUED|0.0|vision"
        val downloadingString = "main.litertlm|50000000|100000000|DOWNLOADING|10.0|main"
        val completeString = "draft.litertlm|100000000|100000000|COMPLETE|0.0|draft"
        val failedString = "fast.litertlm|30000000|100000000|FAILED|5.0|fast"

        // Then - all should parse correctly
        assertEquals(FileStatus.QUEUED, parser.parseFileProgress(queuedString)?.status)
        assertEquals(FileStatus.DOWNLOADING, parser.parseFileProgress(downloadingString)?.status)
        assertEquals(FileStatus.COMPLETE, parser.parseFileProgress(completeString)?.status)
        assertEquals(FileStatus.FAILED, parser.parseFileProgress(failedString)?.status)
    }

    @Test
    fun `parseFileProgress handles all ModelType enum values`() {
        // Given - each ModelType as apiValue (using correct apiValue strings)
        val visionString = "model.litertlm|0|100|QUEUED|0.0|vision"
        val draftOneString = "model.litertlm|0|100|QUEUED|0.0|draft_one"
        val draftTwoString = "model.litertlm|0|100|QUEUED|0.0|draft_two"
        val mainString = "model.litertlm|0|100|QUEUED|0.0|main"
        val fastString = "model.litertlm|0|100|QUEUED|0.0|fast"
        val thinkingString = "model.litertlm|0|100|QUEUED|0.0|thinking"

        // Then - each should parse to correct ModelType
        assertEquals(listOf(ModelType.VISION), parser.parseFileProgress(visionString)?.modelTypes)
        assertEquals(listOf(ModelType.DRAFT_ONE), parser.parseFileProgress(draftOneString)?.modelTypes)
        assertEquals(listOf(ModelType.DRAFT_TWO), parser.parseFileProgress(draftTwoString)?.modelTypes)
        assertEquals(listOf(ModelType.MAIN), parser.parseFileProgress(mainString)?.modelTypes)
        assertEquals(listOf(ModelType.FAST), parser.parseFileProgress(fastString)?.modelTypes)
        assertEquals(listOf(ModelType.THINKING), parser.parseFileProgress(thinkingString)?.modelTypes)
    }

    @Test
    fun `parseRunning merges multi-type modelTypes when filename matches any type`() {
        // Given - current downloads with multi-type model (DRAFT + VISION)
        val multiTypeDownload = FileProgress(
            filename = "vision.litertlm",
            modelTypes = listOf(ModelType.DRAFT_ONE, ModelType.VISION),
            bytesDownloaded = 0,
            totalBytes = 100_000_000,
            status = FileStatus.QUEUED,
            speedMBs = null
        )
        val currentDownloads = listOf(multiTypeDownload)

        // And - WorkInfo with progress for "vision.litertlm" (with embedded modelTypes for merging test)
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                // Add modelTypes to test merging with existing download entry
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "vision.litertlm|50000000|100000000|DOWNLOADING|10.0|vision"
                )
            }
        }

        // When
        val result = parser.parse(workInfo, currentDownloads)

        // Then - the result should have both DRAFT and VISION modelTypes
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        assertTrue(result.currentDownloads!!.isNotEmpty())
        
        val visionFileProgress = result.currentDownloads!!.find { it.filename == "vision.litertlm" }
        assertNotNull(visionFileProgress)
        
        // BUG FIX VERIFICATION: Should have BOTH modelTypes, not just one
        assertTrue(visionFileProgress!!.modelTypes.contains(ModelType.DRAFT_ONE))
        assertTrue(visionFileProgress.modelTypes.contains(ModelType.VISION))
        assertEquals(2, visionFileProgress.modelTypes.size)
    }

    @Test
    fun `parseRunning matches filename base against any modelType in download entry`() {
        // Given - download entry with multiple modelTypes (DRAFT and VISION)
        // This tests merging modelTypes when filename matches
        val multiTypeDownload = FileProgress(
            filename = "vision.litertlm",
            modelTypes = listOf(ModelType.DRAFT_ONE, ModelType.VISION),
            bytesDownloaded = 0,
            totalBytes = 100_000_000,
            status = FileStatus.QUEUED,
            speedMBs = null
        )
        val currentDownloads = listOf(multiTypeDownload)

        // And - WorkInfo with progress for "vision.litertlm" (same filename, different modelTypes in work)
        // Using 6 parts with empty modelTypes to trigger merge with currentDownloads
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                // Filename matches currentDownloads, but empty modelTypes (will trigger merge)
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "vision.litertlm|50000000|100000000|DOWNLOADING|10.0|"
                )
            }
        }

        // When
        val result = parser.parse(workInfo, currentDownloads)

        // Then - should match "vision" against the multi-type download and merge modelTypes
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)

        val visionFileProgress = result.currentDownloads!!.find { it.filename == "vision.litertlm" }
        assertNotNull(visionFileProgress)

        // BUG FIX VERIFICATION: Should have BOTH DRAFT and VISION from the matched download
        assertTrue(visionFileProgress!!.modelTypes.contains(ModelType.DRAFT_ONE))
        assertTrue(visionFileProgress.modelTypes.contains(ModelType.VISION))
    }

    @Test
    fun `parseRunning derives modelTypes from filename when no matching download found`() {
        // Given - no existing downloads
        val currentDownloads = emptyList<FileProgress>()

        // And - WorkInfo with progress for "vision.litertlm" (can be derived)
        // Note: parser uses currentDownloads.size as default, which is 0 for empty list
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 0) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                // Using 6 parts with empty modelTypes to trigger derivation from filename
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "vision.litertlm|50000000|100000000|DOWNLOADING|10.0|"
                )
            }
        }

        // When
        val result = parser.parse(workInfo, currentDownloads)

        // Then - should derive modelType from filename
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)

        val visionFileProgress = result.currentDownloads!!.find { it.filename == "vision.litertlm" }
        assertNotNull(visionFileProgress)

        // Should derive VISION from filename "vision.litertlm"
        assertEquals(listOf(ModelType.VISION), visionFileProgress!!.modelTypes)
    }

    @Test
    fun `parseRunning case insensitive modelType matching`() {
        // Given - download with uppercase modelType name
        val download = FileProgress(
            filename = "main.litertlm",
            modelTypes = listOf(ModelType.MAIN),
            bytesDownloaded = 0,
            totalBytes = 100_000_000,
            status = FileStatus.QUEUED,
            speedMBs = null
        )
        val currentDownloads = listOf(download)

        // And - WorkInfo with filename "main.litertlm" (matching case)
        // Using 6 parts with empty modelTypes to trigger merge
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "main.litertlm|50000000|100000000|DOWNLOADING|10.0|"
                )
            }
        }

        // When
        val result = parser.parse(workInfo, currentDownloads)

        // Then - should match regardless of case
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)

        val mainFileProgress = result.currentDownloads!!.find { it.filename == "main.litertlm" }
        assertNotNull(mainFileProgress)
        assertEquals(listOf(ModelType.MAIN), mainFileProgress!!.modelTypes)
    }

    // ===== parseSucceeded tests =====

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

        // SHA-256 validation is now done in HttpFileDownloader during streaming
        // When work succeeds, we assume files are valid
        val currentDownloads = listOf(
            FileProgress(
                filename = "main.litertlm",
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
    fun `parseSucceeded ignores stale session`() {
        // Given: Stale session ID
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(any()) } returns "stale-session-id"
            }
        }

        every { mockSessionManager.isSessionStale("stale-session-id") } returns true

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return null (ignore stale result)
        assertNull(result)
    }

    // ===== parseFailed tests =====

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
    fun `parseFailed ignores stale session`() {
        // Given: Stale session ID
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
            every { outputData } returns mockk {
                every { getString(any()) } returns "stale-session-id"
            }
        }

        every { mockSessionManager.isSessionStale("stale-session-id") } returns true

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return null (ignore stale result)
        assertNull(result)
    }

    @Test
    fun `parseFailed provides default message when none provided`() {
        // Given: WorkInfo with FAILED state and no error message
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
            every { outputData } returns mockk {
                every { getString(any()) } returns "current-session-id"
                every { getString("error_message") } returns null
            }
        }

        every { mockSessionManager.isSessionStale("current-session-id") } returns false

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return default "Download failed" message
        assertNotNull(result)
        assertEquals(DownloadStatus.ERROR, result!!.status)
        assertEquals("Download failed", result.errorMessage)
    }

    // ===== parse with CANCELLED state =====

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

    // ===== Edge case tests for ENQUEUED and BLOCKED states =====

    @Test
    fun `parse returns checking status when enqueued`() {
        // Given: WorkInfo with ENQUEUED state
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return CHECKING status
        assertNotNull(result)
        assertEquals(DownloadStatus.CHECKING, result!!.status)
    }

    @Test
    fun `parse returns wifi_blocked status when blocked`() {
        // Given: WorkInfo with BLOCKED state (WiFi-only constraint not met)
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.BLOCKED
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return WIFI_BLOCKED status with waitingForUnmeteredNetwork=true
        assertNotNull(result)
        assertEquals(DownloadStatus.WIFI_BLOCKED, result!!.status)
        assertEquals(true, result.waitingForUnmeteredNetwork)
    }

    // ===== Edge case tests for stale session handling =====

    @Test
    fun `parseSucceeds handles stale session`() {
        // Given: Stale session ID
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(any()) } returns "stale-session-id"
            }
        }

        every { mockSessionManager.isSessionStale("stale-session-id") } returns true

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return null (ignore stale result)
        assertNull(result)
    }

    @Test
    fun `parse handles null session id in succeeded state`() {
        // Given: WorkInfo with SUCCEEDED state but no session ID
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(any()) } returns null
            }
        }

        // And: Mock session manager - null session ID is treated as stale
        every { mockSessionManager.isSessionStale(null) } returns true

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return null (stale session)
        assertNull(result)
    }

    @Test
    fun `parse handles null session id in failed state`() {
        // Given: WorkInfo with FAILED state but no session ID
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
            every { outputData } returns mockk {
                every { getString(any()) } returns null
            }
        }

        // And: Mock session manager - null session ID is treated as stale
        every { mockSessionManager.isSessionStale(null) } returns true

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return null (stale session - ignore old failure)
        assertNull(result)
    }

    // ===== Edge case tests for empty current downloads =====

    @Test
    fun `parse handles empty current downloads with running state`() {
        // Given: WorkInfo with RUNNING state and empty current downloads
        // Note: parser uses currentDownloads.size as default, which is 0 for empty list
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 0) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "vision.litertlm|50000000|100000000|DOWNLOADING|10.0|vision"
                )
            }
        }

        // And: Empty current downloads
        val currentDownloads = emptyList<FileProgress>()

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Should still return result with derived modelTypes from filename
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        assertTrue(result.currentDownloads!!.isNotEmpty())

        val visionFile = result.currentDownloads!!.find { it.filename == "vision.litertlm" }
        assertNotNull(visionFile)
        // Should derive VISION from filename since currentDownloads is empty
        assertEquals(listOf(ModelType.VISION), visionFile!!.modelTypes)
    }

    // ===== Additional edge case tests =====

    @Test
    fun `parse handles empty files progress array`() {
        // Given: WorkInfo with RUNNING state and empty files progress
        // Note: parser uses currentDownloads.size as default, which is 0 for empty list
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 0
                every { getInt(DownloadKey.MODELS_TOTAL.key, 0) } returns 1
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 0.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns -1L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns emptyArray()
            }
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Should return result with empty currentDownloads
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        assertTrue(result.currentDownloads!!.isEmpty())
    }

    @Test
    fun `parse handles invalid file progress entries in array`() {
        // Given: WorkInfo with RUNNING state and mixed valid/invalid file entries
        // Note: parser uses currentDownloads.size as default, which is 0 for empty list
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 0) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                // Mix of valid and invalid entries
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "valid.litertlm|50000000|100000000|DOWNLOADING|10.0|main",
                    "invalid-entry",  // Invalid - will be filtered out
                    ""  // Empty - will be filtered out
                )
            }
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Should return result with only valid entries
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        assertEquals(1, result.currentDownloads!!.size)
        assertEquals("valid.litertlm", result.currentDownloads!!.first().filename)
    }
}

