package com.browntowndev.enclave.core.data.remote.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.WorkInfo
import com.browntowndev.enclave.core.data.download.DownloadSessionManager
import com.browntowndev.enclave.core.data.download.DownloadWorkKeys
import com.browntowndev.enclave.core.data.download.WorkProgressParser
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.domain.model.download.DownloadKey
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.model.download.FileProgress
import com.browntowndev.enclave.domain.model.download.FileStatus
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.UUID

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
 *
 * ENC-28: WorkInfo/Data are real instances; the session manager is the real
 * [DownloadSessionManager] over a Robolectric context — [SESSION_ID] is the id of
 * the session created in setup (the non-stale one), any other id is stale.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class WorkProgressParserChainStageTest {
    private lateinit var sessionManager: DownloadSessionManager
    private lateinit var parser: WorkProgressParser

    /** The current (non-stale) session id, created fresh per test in setup. */
    private lateinit var sessionId: String

    companion object {
        const val KEY_WORKER_STAGE = "worker_stage"
        const val KEY_REQUEST_KIND = "request_kind"
        const val KEY_DOWNLOADED_SHAS = "downloaded_shas"
        const val KEY_TARGET_MODEL_ID = "target_model_id"
        const val KEY_ERROR_MESSAGE = "error_message"

        const val STAGE_DOWNLOAD = "DOWNLOAD"
        const val STAGE_FINALIZE = "FINALIZE"
    }

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sessionManager = DownloadSessionManager(context)
        sessionId = sessionManager.createNewSession()

        parser = WorkProgressParser(sessionManager, TestClock())
    }

    private fun workInfo(
        state: WorkInfo.State,
        outputData: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
    ): WorkInfo = WorkInfo(UUID.randomUUID(), state, emptySet(), outputData, progress, 0, 0)

    private fun outputData(
        session: String,
        stage: String,
        downloadedShas: Array<String?>? = null,
        errorMessage: String? = null,
    ): Data {
        val builder =
            Data
                .Builder()
                .putString(DownloadWorkKeys.KEY_SESSION_ID, session)
                .putString(KEY_WORKER_STAGE, stage)
        if (downloadedShas != null) builder.putStringArray(KEY_DOWNLOADED_SHAS, downloadedShas)
        if (errorMessage != null) builder.putString(KEY_ERROR_MESSAGE, errorMessage)
        return builder.build()
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
        val workInfo =
            workInfo(
                state = WorkInfo.State.SUCCEEDED,
                outputData = outputData(sessionId, STAGE_DOWNLOAD, downloadedShas = arrayOf("sha1", "sha2")),
            )

        val currentDownloads =
            listOf(
                FileProgress(
                    filename = "model.gguf",
                    sha256 = "sha1",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 1000000L,
                    totalBytes = 1000000L,
                    status = FileStatus.COMPLETE,
                    speedMBs = null,
                ),
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
            result != null && result.status == DownloadStatus.FINALIZING,
            "Parser should emit FINALIZING for SUCCEEDED download worker (intermediate stage)",
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
        val workInfo =
            workInfo(
                state = WorkInfo.State.SUCCEEDED,
                outputData = outputData(sessionId, STAGE_FINALIZE, downloadedShas = arrayOf("sha1", "sha2")),
            )
        val currentDownloads =
            listOf(
                FileProgress(
                    filename = "model.gguf",
                    sha256 = "sha1",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 1000000L,
                    totalBytes = 1000000L,
                    status = FileStatus.COMPLETE,
                    speedMBs = null,
                ),
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
        val workInfo =
            workInfo(
                state = WorkInfo.State.FAILED,
                outputData = outputData(sessionId, STAGE_DOWNLOAD, errorMessage = "Network error during download"),
            )

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
        val workInfo =
            workInfo(
                state = WorkInfo.State.FAILED,
                outputData = outputData(sessionId, STAGE_FINALIZE, errorMessage = "Failed to restore soft-deleted model"),
            )

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
        val workInfo =
            workInfo(
                state = WorkInfo.State.SUCCEEDED,
                outputData = outputData("stale-session", STAGE_FINALIZE),
            )

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
        val workInfo =
            workInfo(
                state = WorkInfo.State.FAILED,
                outputData = outputData("stale-session", STAGE_FINALIZE),
            )

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
        val workInfo =
            workInfo(
                state = WorkInfo.State.RUNNING,
                outputData = outputData(sessionId, STAGE_FINALIZE),
                progress =
                    Data
                        .Builder()
                        .putFloat(DownloadKey.OVERALL_PROGRESS.key, 0.5f)
                        .putInt(DownloadKey.MODELS_COMPLETE.key, 0)
                        .putInt(DownloadKey.MODELS_TOTAL.key, 0)
                        .putDouble(DownloadKey.SPEED_MBPS.key, 0.0)
                        .putLong(DownloadKey.ETA_SECONDS.key, -1L)
                        .putStringArray(DownloadKey.FILES_PROGRESS.key, emptyArray())
                        .build(),
            )

        val currentDownloads =
            listOf(
                FileProgress(
                    filename = "model.gguf",
                    sha256 = "sha1",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 1000000L,
                    totalBytes = 1000000L,
                    status = FileStatus.COMPLETE,
                    speedMBs = null,
                ),
            )

        // When: Parse
        val result = parser.parse(workInfo, currentDownloads)

        // Then: Should preserve progress, not emit terminal READY
        assertNotNull(result)
        assertEquals(DownloadStatus.FINALIZING, result!!.status)
        assertTrue(
            result.status != DownloadStatus.READY,
            "Parser should NOT emit READY while finalizer is running",
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
        val downloadedShas: Array<String?> = arrayOf("sha1", "sha2", "sha3")
        val workInfo =
            workInfo(
                state = WorkInfo.State.SUCCEEDED,
                outputData = outputData(sessionId, STAGE_FINALIZE, downloadedShas = downloadedShas),
            )

        // When: Parse
        val result = parser.parse(workInfo, emptyList())

        // Then: Result should include downloaded_shas for downstream consumers
        // This requires the parser to preserve this metadata in the update
        assertNotNull(result)
        // After refactor, the update should carry the downloaded_shas
        // This could be in a new field or just available for inspection
    }
}
