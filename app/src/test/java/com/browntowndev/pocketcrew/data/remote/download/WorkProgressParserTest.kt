package com.browntowndev.pocketcrew.data.remote.download

import android.util.Log
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.domain.model.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.FileProgress
import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.data.download.WorkProgressParser
import com.browntowndev.pocketcrew.data.download.DownloadSessionManager
import com.browntowndev.pocketcrew.domain.model.DownloadKey
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.service.FileIntegrityValidator
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WorkProgressParser.
 * Tests parsing of file progress strings including modelTypes extraction.
 */
class WorkProgressParserTest {

    @MockK
    private lateinit var mockSessionManager: DownloadSessionManager

    @MockK
    private lateinit var mockFileIntegrityValidator: FileIntegrityValidator

    private lateinit var parser: WorkProgressParser

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        parser = WorkProgressParser(mockSessionManager, mockFileIntegrityValidator)
    }

    @Test
    fun `parseFileProgress parses modelTypes from new format with 6 parts`() {
        // Given - progress string with modelTypes (6 parts)
        // Format: filename|bytesDownloaded|totalBytes|status|speedMBs|modelTypes
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING|12.5|vision"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNotNull(result)
        result!!
        assertEquals("vision.litertlm", result.filename)
        assertEquals(50_000_000L, result.bytesDownloaded)
        assertEquals(100_000_000L, result.totalBytes)
        assertEquals(FileStatus.DOWNLOADING, result.status)
        assertEquals(12.5, result.speedMBs)
        assertTrue(result.modelTypes.isNotEmpty())
        assertEquals(ModelType.VISION, result.modelTypes.first())
    }

    @Test
    fun `parseFileProgress parses multiple modelTypes from comma-separated list`() {
        // Given - progress string with multiple modelTypes
        val progressString = "main.litertlm|0|200000000|QUEUED|0.0|main,draft"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNotNull(result)
        result!!
        assertEquals("main.litertlm", result.filename)
        assertEquals(2, result.modelTypes.size)
        assertTrue(result.modelTypes.contains(ModelType.MAIN))
        assertTrue(result.modelTypes.contains(ModelType.DRAFT))
    }

    @Test
    fun `parseFileProgress parses all modelTypes correctly`() {
        // Given - progress string with all modelTypes
        val progressString = "fast.litertlm|0|50000000|QUEUED|0.0|fast,vision,main,draft"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNotNull(result)
        result!!
        assertEquals(4, result.modelTypes.size)
        assertTrue(result.modelTypes.contains(ModelType.FAST))
        assertTrue(result.modelTypes.contains(ModelType.VISION))
        assertTrue(result.modelTypes.contains(ModelType.MAIN))
        assertTrue(result.modelTypes.contains(ModelType.DRAFT))
    }

    @Test
    fun `parseFileProgress handles old format without modelTypes`() {
        // Given - old format (4 parts, no modelTypes)
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNotNull(result)
        result!!
        assertEquals("vision.litertlm", result.filename)
        assertEquals(50_000_000L, result.bytesDownloaded)
        assertEquals(100_000_000L, result.totalBytes)
        assertEquals(FileStatus.DOWNLOADING, result.status)
        assertTrue(result.modelTypes.isEmpty()) // Old format has no modelTypes
    }

    @Test
    fun `parseFileProgress handles old format with speed but no modelTypes`() {
        // Given - old format with speed (5 parts, no modelTypes)
        val progressString = "vision.litertlm|50000000|100000000|DOWNLOADING|12.5"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNotNull(result)
        result!!
        assertEquals("vision.litertlm", result.filename)
        assertEquals(12.5, result.speedMBs)
        assertTrue(result.modelTypes.isEmpty()) // No modelTypes in old format
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
        assertNotNull(result)
        result!!
        assertTrue(result.modelTypes.isEmpty())
    }

    @Test
    fun `parseFileProgress defaults unknown modelTypes to MAIN`() {
        // Given - modelType that doesn't match any enum
        val progressString = "unknown.litertlm|0|100000000|QUEUED|0.0|unknown_model"

        // When
        val result = parser.parseFileProgress(progressString)

        // Then
        assertNotNull(result)
        result!!
        assertTrue(result.modelTypes.isNotEmpty())
        // Unknown values default to MAIN per ModelType.fromApiValue
        assertEquals(ModelType.MAIN, result.modelTypes.first())
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
        // Given - each ModelType as apiValue
        val visionString = "model.task|0|100|QUEUED|0.0|vision"
        val draftString = "model.task|0|100|QUEUED|0.0|draft"
        val mainString = "model.task|0|100|QUEUED|0.0|main"
        val fastString = "model.task|0|100|QUEUED|0.0|fast"

        // Then - each should parse to correct ModelType
        assertEquals(listOf(ModelType.VISION), parser.parseFileProgress(visionString)?.modelTypes)
        assertEquals(listOf(ModelType.DRAFT), parser.parseFileProgress(draftString)?.modelTypes)
        assertEquals(listOf(ModelType.MAIN), parser.parseFileProgress(mainString)?.modelTypes)
        assertEquals(listOf(ModelType.FAST), parser.parseFileProgress(fastString)?.modelTypes)
    }

    @Test
    fun `parseRunning merges multi-type modelTypes when filename matches any type`() {
        // Given - current downloads with multi-type model (DRAFT + VISION)
        val multiTypeDownload = FileProgress(
            filename = "vision.litertlm",
            modelTypes = listOf(ModelType.DRAFT, ModelType.VISION),
            bytesDownloaded = 0,
            totalBytes = 100_000_000,
            status = FileStatus.QUEUED,
            speedMBs = null
        )
        val currentDownloads = listOf(multiTypeDownload)

        // And - WorkInfo with progress for "vision.litertlm" (old format without embedded modelTypes)
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "vision.litertlm|50000000|100000000|DOWNLOADING|10.0"
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
        assertTrue(visionFileProgress!!.modelTypes.contains(ModelType.DRAFT))
        assertTrue(visionFileProgress.modelTypes.contains(ModelType.VISION))
        assertEquals(2, visionFileProgress.modelTypes.size)
    }

    @Test
    fun `parseRunning matches filename base against any modelType in download entry`() {
        // Given - download entry with multiple modelTypes (DRAFT and VISION)
        val multiTypeDownload = FileProgress(
            filename = "draft.litertlm",
            modelTypes = listOf(ModelType.DRAFT, ModelType.VISION),
            bytesDownloaded = 0,
            totalBytes = 100_000_000,
            status = FileStatus.QUEUED,
            speedMBs = null
        )
        val currentDownloads = listOf(multiTypeDownload)

        // And - WorkInfo with progress for "vision.litertlm" (different filename but matches VISION type)
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                // Filename is "vision.litertlm" but download has DRAFT+VISION
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "vision.litertlm|50000000|100000000|DOWNLOADING|10.0"
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
        assertTrue(visionFileProgress!!.modelTypes.contains(ModelType.DRAFT))
        assertTrue(visionFileProgress.modelTypes.contains(ModelType.VISION))
    }

    @Test
    fun `parseRunning derives modelTypes from filename when no matching download found`() {
        // Given - no existing downloads
        val currentDownloads = emptyList<FileProgress>()

        // And - WorkInfo with progress for "draft.litertlm"
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "draft.litertlm|50000000|100000000|DOWNLOADING|10.0"
                )
            }
        }

        // When
        val result = parser.parse(workInfo, currentDownloads)

        // Then - should derive modelType from filename
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        
        val draftFileProgress = result.currentDownloads!!.find { it.filename == "draft.litertlm" }
        assertNotNull(draftFileProgress)
        
        // Should derive DRAFT from filename "draft.litertlm"
        assertEquals(listOf(ModelType.DRAFT), draftFileProgress!!.modelTypes)
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

        // And - WorkInfo with lowercase filename "Main.litertlm"
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
                every { getDouble(DownloadKey.SPEED_MBPS.key, 0.0) } returns 10.0
                every { getLong(DownloadKey.ETA_SECONDS.key, -1L) } returns 100L
                every { getStringArray(DownloadKey.FILES_PROGRESS.key) } returns arrayOf(
                    "MAIN.litertlm|50000000|100000000|DOWNLOADING|10.0"
                )
            }
        }

        // When
        val result = parser.parse(workInfo, currentDownloads)

        // Then - should match regardless of case
        assertNotNull(result)
        assertNotNull(result!!.currentDownloads)
        
        val mainFileProgress = result.currentDownloads!!.find { it.filename == "MAIN.litertlm" }
        assertNotNull(mainFileProgress)
        assertEquals(listOf(ModelType.MAIN), mainFileProgress!!.modelTypes)
    }

    // ===== parseSucceeded tests =====

    @Test
    fun `parseSucceeded returns ready status when verification passes`() {
        // Given: WorkInfo with SUCCEEDED state
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(any()) } returns "current-session-id"
            }
        }

        every { mockSessionManager.isSessionStale("current-session-id") } returns false

        // And: FileIntegrityValidator returns true
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

        io.mockk.coEvery {
            mockFileIntegrityValidator.verifyModelsExist(any())
        } returns Result.success(true)

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Return READY status with clearSession=true
        assertNotNull(result)
        assertEquals(DownloadStatus.READY, result!!.status)
    }

    @Test
    fun `parseSucceeded returns error when verification fails`() {
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
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 1000000L,
                totalBytes = 1000000L,
                status = FileStatus.COMPLETE,
                speedMBs = null
            )
        )

        io.mockk.coEvery {
            mockFileIntegrityValidator.verifyModelsExist(any())
        } returns Result.failure(Exception("Verification failed"))

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Return ERROR status
        assertNotNull(result)
        assertEquals(DownloadStatus.ERROR, result!!.status)
        assertTrue(result.errorMessage?.contains("not found") == true)
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
    fun `parse returns checking status when blocked`() {
        // Given: WorkInfo with BLOCKED state
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.BLOCKED
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return CHECKING status
        assertNotNull(result)
        assertEquals(DownloadStatus.CHECKING, result!!.status)
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

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return null (no session to check)
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

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Return ERROR status with default message (cannot determine session)
        assertNotNull(result)
        assertEquals(DownloadStatus.ERROR, result!!.status)
    }

    // ===== Edge case tests for empty current downloads =====

    @Test
    fun `parse handles empty current downloads with running state`() {
        // Given: WorkInfo with RUNNING state and empty current downloads
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
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
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 0
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 1
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
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(DownloadKey.OVERALL_PROGRESS.key, 0f) } returns 0.5f
                every { getInt(DownloadKey.MODELS_COMPLETE.key, 0) } returns 1
                every { getInt(DownloadKey.MODELS_TOTAL.key, 1) } returns 2
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

    @Test
    fun `parseSucceeded handles verification failure with models not found`() {
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
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 1000000L,
                totalBytes = 1000000L,
                status = FileStatus.COMPLETE,
                speedMBs = null
            )
        )

        // And: FileIntegrityValidator returns failure (models not found)
        io.mockk.coEvery {
            mockFileIntegrityValidator.verifyModelsExist(any())
        } returns Result.failure(Exception("Models not found on device"))

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Return ERROR status with error message
        assertNotNull(result)
        assertEquals(DownloadStatus.ERROR, result!!.status)
        assertTrue(result.errorMessage?.contains("not found") == true)
    }
}

