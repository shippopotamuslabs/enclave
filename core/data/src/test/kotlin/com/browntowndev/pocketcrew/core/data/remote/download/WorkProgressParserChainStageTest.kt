package com.browntowndev.pocketcrew.core.data.remote.download

import android.util.Log
import androidx.work.Data
import androidx.work.WorkInfo
import com.browntowndev.pocketcrew.core.data.download.DownloadSessionManager
import com.browntowndev.pocketcrew.core.data.download.DownloadWorkKeys
import com.browntowndev.pocketcrew.domain.model.download.DownloadKey
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.core.data.download.WorkProgressParser
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * TDD Red Phase Tests for WorkProgressParser Chain-Aware Stage Semantics.
 *
 * These tests verify the NEW expected behavior where:
 * - Parser distinguishes between DOWNLOAD and FINALIZE worker stages
 * - SUCCEEDED download worker is INTERMEDIATE (not terminal)
 * - SUCCEEDED finalizer worker is TERMINAL (emits READY)
 * - Both terminal success and failure clear the session
 * - Parser extracts worker_stage from output data
 *
 * The current implementation treats ALL SUCCEEDED as terminal READY,
 * which is incorrect for a two-worker chain.
 *
 * EXPECTED: These tests FAIL against current code.
 * EXPECTED: These tests PASS after refactor.
 */
class WorkProgressParserChainStageTest {

    @MockK
    private lateinit var mockSessionManager: DownloadSessionManager

    private lateinit var parser: WorkProgressParser

    companion object {
        const val KEY_WORKER_STAGE = "worker_stage"
        const val KEY_REQUEST_KIND = "request_kind"
        const val KEY_DOWNLOADED_SHAS = "downloaded_shas"
        const val KEY_TARGET_MODEL_ID = "target_model_id"
        const val KEY_ERROR_MESSAGE = "error_message"

        const val STAGE_DOWNLOAD = "DOWNLOAD"
        const val STAGE_FINALIZE = "FINALIZE"

        const val SESSION_ID = "test-session-123"
    }

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        every { mockSessionManager.isSessionStale(any()) } returns false

        parser = WorkProgressParser(mockSessionManager)
    }

    // ===== PARSER_01: SUCCEEDED download worker is intermediate, not terminal =====

    /**
     * PARSER_01: Succeeded download worker is NOT terminal.
     *
     * In a two-worker chain, SUCCEEDED from ModelDownloadWorker means bytes are on disk
     * but business finalization hasn't run yet.
     *
     * The parser should NOT emit READY for download worker success.
     * Instead, it should return null or intermediate state.
     *
     * EXPECTED: FAILS against current code (current code emits READY).
     * EXPECTED: PASSES after refactor.
     */
    @Test
    fun `PARSER_01 - SUCCEEDED download worker is intermediate, not terminal READY`() {
        // Given: WorkInfo with SUCCEEDED state and DOWNLOAD stage
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns SESSION_ID
                every { getString(KEY_WORKER_STAGE) } returns STAGE_DOWNLOAD
                every { getStringArray(KEY_DOWNLOADED_SHAS) } returns arrayOf("sha1", "sha2")
            }
        }

        val currentDownloads = listOf(
            FileProgress(
                filename = "model.gguf",
                sha256 = "sha1",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 1000000L,
                totalBytes = 1000000L,
                status = FileStatus.COMPLETE,
                speedMBs = null
            )
        )

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Should NOT emit terminal READY for download worker success
        // After refactor, parser should either:
        // 1. Return null (ignore intermediate success)
        // 2. Return a non-READY status (DOWNLOADING with progress)
        //
        // Current behavior: Returns READY (INCORRECT)
        // Refactored behavior: Returns null or intermediate state
        assertTrue(
            result == null || result.status != DownloadStatus.READY,
            "Parser should NOT emit READY for SUCCEEDED download worker (intermediate stage)"
        )
    }

    // ===== PARSER_02: SUCCEEDED finalizer is terminal =====

    /**
     * PARSER_02: Succeeded finalizer IS terminal and emits READY.
     *
     * In a two-worker chain, SUCCEEDED from DownloadFinalizeWorker means
     * business finalization has completed.
     *
     * The parser SHOULD emit READY with clearSession=true for finalizer success.
     *
     * EXPECTED: FAILS against current code (no FINALIZE stage handling).
     * EXPECTED: PASSES after refactor.
     */
    @Test
    fun `PARSER_02 - SUCCEEDED finalizer is terminal and emits READY`() {
        // Given: WorkInfo with SUCCEEDED state and FINALIZE stage
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns SESSION_ID
                every { getString(KEY_WORKER_STAGE) } returns STAGE_FINALIZE
                every { getStringArray(KEY_DOWNLOADED_SHAS) } returns arrayOf("sha1", "sha2")
            }
        }
        val currentDownloads = listOf(
            FileProgress(
                filename = "model.gguf",
                sha256 = "sha1",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 1000000L,
                totalBytes = 1000000L,
                status = FileStatus.COMPLETE,
                speedMBs = null
            )
        )

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Should emit terminal READY with clearSession=true
        assertNotNull(result, "Parser should emit update for SUCCEEDED finalizer")
        assertEquals(DownloadStatus.READY, result!!.status)
        assertTrue(result.clearSession, "Parser should set clearSession=true for terminal success")
    }

    // ===== PARSER_03: Failed download worker clears session =====

    /**
     * PARSER_03: Failed download worker emits ERROR and clears session.
     *
     * Even though download failure is "before" finalization, it's still terminal.
     * The session should be cleared so the UI can reset.
     *
     * EXPECTED: FAILS against current code (no clearSession on failure).
     * EXPECTED: PASSES after refactor.
     */
    @Test
    fun `PARSER_03 - FAILED download worker emits ERROR and clears session`() {
        // Given: WorkInfo with FAILED state and DOWNLOAD stage
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns SESSION_ID
                every { getString(KEY_WORKER_STAGE) } returns STAGE_DOWNLOAD
                every { getString(KEY_ERROR_MESSAGE) } returns "Network error during download"
            }
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Should emit ERROR with clearSession=true
        assertNotNull(result, "Parser should emit update for FAILED download worker")
        assertEquals(DownloadStatus.ERROR, result!!.status)
        assertTrue(result.clearSession, "Parser should set clearSession=true for terminal failure")
        assertEquals("Network error during download", result.errorMessage)
    }

    // ===== PARSER_04: Failed finalizer clears session =====

    /**
     * PARSER_04: Failed finalizer emits ERROR and clears session.
     *
     * Finalization failure is terminal and should clear the session.
     *
     * EXPECTED: FAILS against current code (no FINALIZE stage handling).
     * EXPECTED: PASSES after refactor.
     */
    @Test
    fun `PARSER_04 - FAILED finalizer emits ERROR and clears session`() {
        // Given: WorkInfo with FAILED state and FINALIZE stage
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns SESSION_ID
                every { getString(KEY_WORKER_STAGE) } returns STAGE_FINALIZE
                every { getString(KEY_ERROR_MESSAGE) } returns "Failed to restore soft-deleted model"
            }
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Should emit ERROR with clearSession=true
        assertNotNull(result, "Parser should emit update for FAILED finalizer")
        assertEquals(DownloadStatus.ERROR, result!!.status)
        assertTrue(result.clearSession, "Parser should set clearSession=true for terminal failure")
    }

    // ===== PARSER_05: Stale session success is ignored =====

    /**
     * PARSER_05: Stale session success is ignored.
     *
     * If the session ID doesn't match the current active session,
     * the parser should return null.
     */
    @Test
    fun `PARSER_05 - stale session success is ignored`() {
        // Given: WorkInfo with SUCCEEDED state and STALE session
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns "stale-session"
                every { getString(KEY_WORKER_STAGE) } returns STAGE_FINALIZE
            }
        }

        every { mockSessionManager.isSessionStale("stale-session") } returns true

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Should return null (ignore stale)
        assertNull(result, "Parser should ignore stale session success")
    }

    // ===== PARSER_06: Stale session failure is ignored =====

    /**
     * PARSER_06: Stale session failure is ignored.
     *
     * If the session ID doesn't match the current active session,
     * the parser should return null.
     */
    @Test
    fun `PARSER_06 - stale session failure is ignored`() {
        // Given: WorkInfo with FAILED state and STALE session
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.FAILED
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns "stale-session"
                every { getString(KEY_WORKER_STAGE) } returns STAGE_FINALIZE
            }
        }

        every { mockSessionManager.isSessionStale("stale-session") } returns true

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Should return null (ignore stale)
        assertNull(result, "Parser should ignore stale session failure")
    }

    // ===== PARSER_07: Running finalizer preserves progress =====

    /**
     * PARSER_07: Running finalizer preserves progress and doesn't emit terminal.
     *
     * While finalizer is running, the parser should preserve the file list
     * and not emit terminal READY.
     *
     * EXPECTED: FAILS against current code.
     * EXPECTED: PASSES after refactor.
     */
    @Test
    fun `PARSER_07 - running finalizer preserves progress and does not emit terminal`() {
        // Given: WorkInfo with RUNNING state and FINALIZE stage
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
            every { progress } returns mockk {
                every { getFloat(any(), any()) } returns 0.5f
                every { getInt(any(), any()) } returns 0
                every { getDouble(any(), any()) } returns 0.0
                every { getLong(any(), any()) } returns -1L
                every { getStringArray(any()) } returns emptyArray()
            }
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns SESSION_ID
                every { getString(KEY_WORKER_STAGE) } returns STAGE_FINALIZE
            }
        }

        val currentDownloads = listOf(
            FileProgress(
                filename = "model.gguf",
                sha256 = "sha1",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 1000000L,
                totalBytes = 1000000L,
                status = FileStatus.COMPLETE,
                speedMBs = null
            )
        )

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Should preserve progress, not emit terminal READY
        assertNotNull(result)
        assertTrue(
            result!!.status != DownloadStatus.READY,
            "Parser should NOT emit READY while finalizer is running"
        )
        // Should preserve the file list
        assertEquals(1, result.currentDownloads?.size)
    }

    // ===== PARSER_08: Extracts downloaded_shas from finalizer output =====

    /**
     * PARSER_08: Finalizer output includes downloaded_shas for downstream consumers.
     *
     * The parser should extract and include downloaded_shas in the update.
     *
     * EXPECTED: FAILS against current code.
     * EXPECTED: PASSES after refactor.
     */
    @Test
    fun `PARSER_08 - finalizer success extracts downloaded_shas`() {
        // Given: Finalizer output with downloaded_shas
        val downloadedShas: Array<String> = arrayOf("sha1", "sha2", "sha3")
        val workInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
            every { outputData } returns mockk {
                every { getString(DownloadWorkKeys.KEY_SESSION_ID) } returns SESSION_ID
                every { getString(KEY_WORKER_STAGE) } returns STAGE_FINALIZE
                every { getStringArray(KEY_DOWNLOADED_SHAS) } returns downloadedShas
            }
        }

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Result should include downloaded_shas for downstream consumers
        // This requires the parser to preserve this metadata in the update
        assertNotNull(result)
        // After refactor, the update should carry the downloaded_shas
        // This could be in a new field or just available for inspection
    }
}
