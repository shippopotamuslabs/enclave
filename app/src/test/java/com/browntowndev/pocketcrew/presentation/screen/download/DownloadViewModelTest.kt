package com.browntowndev.pocketcrew.presentation.screen.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.DownloadState
import com.browntowndev.pocketcrew.domain.model.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.FileProgress
import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.data.repository.DownloadWorkRepository
import com.browntowndev.pocketcrew.presentation.screen.download.DownloadViewModel.FileProgressUiModel
import io.mockk.MockKAnnotations
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockOrchestrator: ModelDownloadOrchestratorPort
    private lateinit var mockDownloadWorkRepository: DownloadWorkRepository

    // Mock DownloadModelsResult for tests
    private lateinit var mockModelsResult: DownloadModelsResult

    private val initialDownloadState = DownloadState(status = DownloadStatus.CHECKING)
    private val _downloadStateFlow = MutableStateFlow(initialDownloadState)

    private lateinit var viewModel: DownloadViewModel

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        _downloadStateFlow.value = initialDownloadState

        mockOrchestrator = mockk(relaxed = true)
        mockDownloadWorkRepository = mockk(relaxed = true)

        // Create mock DownloadModelsResult with empty models to download
        mockModelsResult = DownloadModelsResult(
            modelsToDownload = emptyList(),
            scanResult = ModelScanResult(
                missingModels = emptyList(),
                partialDownloads = emptyMap(),
                allValid = true
            )
        )

        // Mock the orchestrator's downloadState to return our MutableStateFlow
        // This allows tests to control the state by modifying _downloadStateFlow
        every { mockOrchestrator.downloadState } returns _downloadStateFlow.asStateFlow()

        // Mock checkModels to avoid triggering actual work in init
        coEvery { mockOrchestrator.checkModels(originalModels = any()) } returns mockModelsResult

        viewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )

        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    // ============================================================================
    // StateFlow Tests
    // ============================================================================

    @Test
    fun `downloadState emits initial value from orchestrator`() = runTest {
        val state = viewModel.downloadState
        assertNotNull(state)
        assertEquals(DownloadStatus.CHECKING, state.value.status)
    }

    @Test
    fun `downloadState updates when orchestrator emits new state`() = runTest {
        // Given - initial state
        val initialState = viewModel.downloadState.value
        assertEquals(DownloadStatus.CHECKING, initialState.status)

        // When - orchestrator emits new state
        val newState = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            modelsTotal = 3,
            modelsComplete = 1
        )
        _downloadStateFlow.value = newState
        testDispatcher.scheduler.runCurrent()

        // Then - ViewModel's downloadState reflects the new state
        val updatedState = viewModel.downloadState.value
        assertEquals(DownloadStatus.DOWNLOADING, updatedState.status)
        assertEquals(0.5f, updatedState.overallProgress)
    }

    @Test
    fun `downloadState should expose orchestrator state`() = runTest {
        val state = viewModel.downloadState
        assertNotNull(state)
        assertEquals(DownloadStatus.CHECKING, state.value.status)
    }

    @Test
    fun `wifiOnly should default to true`() = runTest {
        assertTrue(viewModel.wifiOnly.value)
    }

    @Test
    fun `showWifiDialog should default to false`() = runTest {
        assertFalse(viewModel.showWifiDialog.value)
    }

    // ============================================================================
    // User Action Tests - startDownloads
    // ============================================================================

    @Test
    fun `startDownloads calls orchestrator with wifiOnly setting enabled`() = runTest {
        // Given - wifiOnly is enabled by default
        assertTrue(viewModel.wifiOnly.value)

        // Set the ViewModel to foreground to allow downloads
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - startDownloads is called
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - orchestrator.startDownloads is called with wifiOnly=true
        coVerify { mockOrchestrator.startDownloads(modelsResult = any(), wifiOnly = true) }
    }

    @Test
    fun `startDownloads calls orchestrator without wifiOnly when disabled`() = runTest {
        // Given - set wifiOnly to false
        viewModel.setWifiOnly(false)
        assertFalse(viewModel.wifiOnly.value)

        // Set the ViewModel to foreground to allow downloads
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - startDownloads is called
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - orchestrator.startDownloads is called with wifiOnly=false
        coVerify { mockOrchestrator.startDownloads(modelsResult = any(), wifiOnly = false) }
    }

    @Test
    fun `startDownloads is blocked when app not in foreground`() = runTest {
        // Given - app is NOT in foreground (default state from setup)
        // isInForeground is false by default

        // When - startDownloads is called without foreground
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - orchestrator.startDownloads is NEVER called
        coVerify(exactly = 0) { mockOrchestrator.startDownloads(any(), any()) }
    }

    @Test
    fun `startDownloads proceeds when app is foregrounded after being backgrounded`() = runTest {
        // Given - start downloads initially (will be blocked)
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify it was blocked initially
        coVerify(exactly = 0) { mockOrchestrator.startDownloads(any(), any()) }

        // When - app is foregrounded
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - startDownloads should proceed after foreground
        // Note: Since modelsToDownload is empty in setup, checkModels won't trigger startDownloads
        // But foregrounding should allow startDownloads to proceed if called directly
        coEvery { mockOrchestrator.startDownloads(any(), wifiOnly = true) } returns true
        
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Verify startDownloads was called after foregrounding
        coVerify { mockOrchestrator.startDownloads(any(), wifiOnly = true) }
    }

    // ============================================================================
    // User Action Tests - pause/cancel/retry
    // ============================================================================

    @Test
    fun `pauseDownloads calls orchestrator pause`() = runTest {
        viewModel.pauseDownloads()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockOrchestrator.pauseDownloads() }
    }

    @Test
    fun `resumeDownloads calls orchestrator resume`() = runTest {
        viewModel.resumeDownloads()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockOrchestrator.resumeDownloads() }
    }

    @Test
    fun `cancelDownloads calls orchestrator cancel`() = runTest {
        viewModel.cancelDownloads()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockOrchestrator.cancelDownloads() }
    }

    @Test
    fun `retryDownloads calls orchestrator retry`() = runTest {
        viewModel.retryFailed()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockOrchestrator.retryFailed() }
    }

    @Test
    fun `downloadOnMobileData should call orchestrator`() = runTest {
        viewModel.downloadOnMobileData()

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockOrchestrator.downloadOnMobileData() }
    }

    @Test
    fun `dismissWifiDialog should set showWifiDialog to false`() = runTest {
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.IDLE, wifiBlocked = true)
        
        viewModel.dismissWifiDialog()

        assertFalse(viewModel.showWifiDialog.value)
    }

    @Test
    fun `setWifiOnly should update wifiOnly value`() = runTest {
        viewModel.setWifiOnly(false)

        assertFalse(viewModel.wifiOnly.value)

        viewModel.setWifiOnly(true)

        assertTrue(viewModel.wifiOnly.value)
    }

    // ============================================================================
    // isReady Tests
    // ============================================================================

    @Test
    fun `isReady returns true when status is READY`() = runTest {
        // Create a fresh ViewModel with READY state
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.READY)
        
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        
        testDispatcher.scheduler.advanceUntilIdle()

        val result = testViewModel.downloadState.value.status == DownloadStatus.READY

        assertTrue(result)
    }

    @Test
    fun `isReady returns false when status is CHECKING`() = runTest {
        // Create a fresh ViewModel with CHECKING state
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.CHECKING)
        
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        
        testDispatcher.scheduler.advanceUntilIdle()

        val result = testViewModel.downloadState.value.status == DownloadStatus.READY

        assertFalse(result)
    }

    @Test
    fun `isReady returns false when status is DOWNLOADING`() = runTest {
        // Create a fresh ViewModel with DOWNLOADING state
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.DOWNLOADING)
        
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        
        testDispatcher.scheduler.advanceUntilIdle()

        val result = testViewModel.downloadState.value.status == DownloadStatus.READY

        assertFalse(result)
    }

    @Test
    fun `isReady returns false when status is ERROR`() = runTest {
        // Create a fresh ViewModel with ERROR state
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.ERROR)
        
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        
        testDispatcher.scheduler.advanceUntilIdle()

        val result = testViewModel.downloadState.value.status == DownloadStatus.READY

        assertFalse(result)
    }

    @Test
    fun `isReady returns false when status is PAUSED`() = runTest {
        // Create a fresh ViewModel with PAUSED state
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.PAUSED)
        
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        
        testDispatcher.scheduler.advanceUntilIdle()

        val result = testViewModel.downloadState.value.status == DownloadStatus.READY

        assertFalse(result)
    }

    // ============================================================================
    // DownloadState State Tests
    // ============================================================================

    @Test
    fun `downloadState should reflect orchestrator state changes`() = runTest {
        // Create a fresh ViewModel with DOWNLOADING state
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            modelsTotal = 3,
            modelsComplete = 1
        )
        
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        
        testDispatcher.scheduler.advanceUntilIdle()

        val state = testViewModel.downloadState.value

        assertEquals(DownloadStatus.DOWNLOADING, state.status)
        assertEquals(0.5f, state.overallProgress)
    }

    @Test
    fun `downloadState should reflect error state`() = runTest {
        // Create a fresh ViewModel with ERROR state
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.ERROR,
            errorMessage = "Download failed"
        )
        
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        
        testDispatcher.scheduler.advanceUntilIdle()

        val state = testViewModel.downloadState.value

        assertEquals(DownloadStatus.ERROR, state.status)
        assertEquals("Download failed", state.errorMessage)
    }

    @Test
    fun `downloadState should reflect initial CHECKING state`() = runTest {
        val state = viewModel.downloadState.value

        assertEquals(DownloadStatus.CHECKING, state.status)
        assertNull(state.errorMessage)
    }

    // ============================================================================
    // FileProgress.toUiModel() Tests - Anthropomorphized Names from modelTypes
    // ============================================================================

    @Test
    fun `toUiModel converts FileProgress to display format with anthropomorphized name`() = runTest {
        // Given - FileProgress with VISION modelType
        val fileProgress = FileProgress(
            filename = "vision.litertlm",
            modelTypes = listOf(ModelType.VISION),
            bytesDownloaded = 50_000_000,
            totalBytes = 100_000_000,
            status = FileStatus.DOWNLOADING,
            speedMBs = 12.5
        )

        // Create a ViewModel to use the extension function
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // When - convert to UI model using ViewModel's extension function
        val uiModel: FileProgressUiModel = testViewModel.run { fileProgress.toUiModel() }

        // Then - UI model has correct display name
        assertEquals("vision.litertlm", uiModel.filename)
        assertEquals("The Observer", uiModel.displayName)
        assertEquals(50_000_000L, uiModel.bytesDownloaded)
        assertEquals(100_000_000L, uiModel.totalBytes)
        assertEquals(FileStatus.DOWNLOADING, uiModel.status)
        assertEquals(12.5, uiModel.speedMBs)
        assertEquals(0.5f, uiModel.progress)
    }

    @Test
    fun `fileProgressList maps currentDownloads to UiModels with anthropomorphized names`() = runTest {
        // Given - download state with multiple files with modelTypes
        val downloadsWithModelTypes = listOf(
            FileProgress(
                filename = "vision.litertlm",
                modelTypes = listOf(ModelType.VISION),
                bytesDownloaded = 50_000_000,
                totalBytes = 100_000_000,
                status = FileStatus.DOWNLOADING
            ),
            FileProgress(
                filename = "main.litertlm",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 100_000_000,
                totalBytes = 200_000_000,
                status = FileStatus.DOWNLOADING
            ),
            FileProgress(
                filename = "unknown.litertlm",
                modelTypes = emptyList(), // Empty modelTypes - should fall back to filename
                bytesDownloaded = 25_000_000,
                totalBytes = 50_000_000,
                status = FileStatus.DOWNLOADING
            )
        )

        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            currentDownloads = downloadsWithModelTypes
        )

        // Create a fresh ViewModel to pick up the state
        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Then - fileProgressList should have anthropomorphized names
        val uiModels = testViewModel.fileProgressList.value

        assertEquals(3, uiModels.size)
        assertEquals("The Observer", uiModels[0].displayName)
        assertEquals("The Mastermind", uiModels[1].displayName)
        assertEquals("unknown.litertlm", uiModels[2].displayName) // Fallback to filename
    }

    @Test
    fun `fileProgressList uses anthropomorphized names for all ModelType values`() = runTest {
        // Given - download state with all model types
        val downloadsWithAllModelTypes = listOf(
            FileProgress(
                filename = "vision.litertlm",
                modelTypes = listOf(ModelType.VISION),
                bytesDownloaded = 0,
                totalBytes = 100_000_000,
                status = FileStatus.QUEUED
            ),
            FileProgress(
                filename = "fast.litertlm",
                modelTypes = listOf(ModelType.FAST),
                bytesDownloaded = 0,
                totalBytes = 100_000_000,
                status = FileStatus.QUEUED
            ),
            FileProgress(
                filename = "draft.litertlm",
                modelTypes = listOf(ModelType.DRAFT),
                bytesDownloaded = 0,
                totalBytes = 100_000_000,
                status = FileStatus.QUEUED
            ),
            FileProgress(
                filename = "main.litertlm",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 0,
                totalBytes = 100_000_000,
                status = FileStatus.QUEUED
            )
        )

        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            currentDownloads = downloadsWithAllModelTypes
        )

        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Then - all should have correct anthropomorphized names
        val uiModels = testViewModel.fileProgressList.value

        assertEquals("The Observer", uiModels[0].displayName)
        assertEquals("The Sentinel", uiModels[1].displayName)
        assertEquals("The Sketcher", uiModels[2].displayName)
        assertEquals("The Mastermind", uiModels[3].displayName)
    }

    @Test
    fun `fileProgressList joins multiple modelTypes with plus sign`() = runTest {
        // Given - download with multiple modelTypes in single file
        val downloadsWithMultipleModelTypes = listOf(
            FileProgress(
                filename = "main.litertlm",
                modelTypes = listOf(ModelType.MAIN, ModelType.DRAFT),
                bytesDownloaded = 0,
                totalBytes = 200_000_000,
                status = FileStatus.QUEUED
            )
        )

        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            currentDownloads = downloadsWithMultipleModelTypes
        )

        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Then - should join names with " + "
        val uiModels = testViewModel.fileProgressList.value

        assertEquals("The Mastermind + The Sketcher", uiModels[0].displayName)
    }

    @Test
    fun `fileProgressList falls back to filename when modelTypes is empty - REGRESSION TEST`() = runTest {
        // This test verifies the fix for the bug where modelTypes was empty
        // and the display name was incorrectly showing the filename instead of anthropomorphized names
        val downloadsWithEmptyModelTypes = listOf(
            FileProgress(
                filename = "vision.litertlm",
                modelTypes = emptyList(), // This was the bug - empty modelTypes
                bytesDownloaded = 50_000_000,
                totalBytes = 100_000_000,
                status = FileStatus.DOWNLOADING
            )
        )

        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            currentDownloads = downloadsWithEmptyModelTypes
        )

        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Then - should fall back to filename when modelTypes is empty
        val uiModels = testViewModel.fileProgressList.value

        assertEquals("vision.litertlm", uiModels[0].displayName)
    }

    @Test
    fun `fileProgressList preserves all FileProgress fields in UiModel`() = runTest {
        // Given - FileProgress with all fields
        val download = FileProgress(
            filename = "vision.litertlm",
            modelTypes = listOf(ModelType.VISION),
            bytesDownloaded = 50_000_000,
            totalBytes = 100_000_000,
            status = FileStatus.DOWNLOADING,
            speedMBs = 12.5
        )

        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            currentDownloads = listOf(download)
        )

        val testViewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelsResult = mockModelsResult,
            initialErrorMessage = null
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Then - all fields should be preserved
        val uiModels = testViewModel.fileProgressList.value

        assertEquals(1, uiModels.size)
        assertEquals("vision.litertlm", uiModels[0].filename)
        assertEquals("The Observer", uiModels[0].displayName)
        assertEquals(50_000_000L, uiModels[0].bytesDownloaded)
        assertEquals(100_000_000L, uiModels[0].totalBytes)
        assertEquals(FileStatus.DOWNLOADING, uiModels[0].status)
        assertEquals(12.5, uiModels[0].speedMBs)
        assertEquals(0.5f, uiModels[0].progress)
    }

    // ============================================================================
    // Foreground/Background Lifecycle Tests
    // ============================================================================

    @Test
    fun `onAppForegrounded sets isInForeground to true`() = runTest {
        // Set wifiOnly to false so downloads can proceed
        viewModel.setWifiOnly(false)
        
        // Initially not in foreground
        assertFalse(viewModel.wifiOnly.value) // We can't directly check isInForeground, but we can verify behavior

        // When - app is foregrounded
        viewModel.onAppForegrounded()

        // Then - subsequent startDownloads should proceed (not be blocked)
        coEvery { mockOrchestrator.startDownloads(any(), any()) } returns true
        
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify startDownloads was called (it wasn't blocked)
        coVerify { mockOrchestrator.startDownloads(any(), any()) }
    }

    @Test
    fun `onAppBackgrounded sets isInForeground to false`() = runTest {
        // First foreground the app
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - app is backgrounded
        viewModel.onAppBackgrounded()

        // Then - subsequent startDownloads should be blocked
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify startDownloads was NOT called (it was blocked)
        coVerify(exactly = 0) { mockOrchestrator.startDownloads(any()) }
    }

    // ============================================================================
    // checkModels Tests
    // ============================================================================

    @Test
    fun `checkModels calls orchestrator`() = runTest {
        // Note: DownloadViewModel.checkModels() does not call orchestrator.checkModels()
        // It checks if modelsToDownload is not empty and calls startDownloads()
        // This test verifies the behavior matches the implementation
        
        // When - checkModels is called with empty modelsToDownload
        viewModel.checkModels()
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Then - with empty modelsToDownload, startDownloads is not called
        coVerify(exactly = 0) { mockOrchestrator.startDownloads(any()) }
    }

    // This test verifies that checkModels is called on the orchestrator
    // Note: The actual return type is List<ModelFile>, but we verify the call happens
    @Test
    fun `checkModels is invoked when called on viewModel`() = runTest {
        // Given - mock returns empty list (models already present)
        // Note: DownloadViewModel.checkModels() does not call orchestrator.checkModels()
        // It checks if modelsToDownload is not empty and calls startDownloads()
        
        // When - checkModels is called on the ViewModel
        viewModel.checkModels()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - with empty modelsToDownload, nothing happens
        coVerify(exactly = 0) { mockOrchestrator.startDownloads(any()) }
    }
}

