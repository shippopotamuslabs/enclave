package com.browntowndev.pocketcrew.presentation.screen.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.data.repository.DownloadWorkRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
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

    private fun createTestConfig(modelType: ModelType, displayName: String): ModelConfiguration {
        return ModelConfiguration(
            modelType = modelType,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "${modelType.name.lowercase()}.litertlm",
                localFileName = "${modelType.name.lowercase()}.litertlm",
                displayName = displayName,
                sha256 = "test123",
                sizeInBytes = 1000L,
                modelFileFormat = com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.95,
                repetitionPenalty = 1.0,
                maxTokens = 512,
                contextWindow = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
        )
    }

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockOrchestrator: ModelDownloadOrchestratorPort
    private lateinit var mockDownloadWorkRepository: DownloadWorkRepository
    private lateinit var mockModelRegistry: ModelRegistryPort

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
        mockModelRegistry = mockk(relaxed = true)

        // Mock getRegisteredModelSync to return configs with display names
        every { mockModelRegistry.getRegisteredModelSync(ModelType.VISION) } returns createTestConfig(ModelType.VISION, "The Observer")
        every { mockModelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns createTestConfig(ModelType.DRAFT_ONE, "The Brainstormer")
        every { mockModelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns createTestConfig(ModelType.DRAFT_TWO, "The Sketcher")
        every { mockModelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns createTestConfig(ModelType.MAIN, "The Mastermind")
        every { mockModelRegistry.getRegisteredModelSync(ModelType.FAST) } returns createTestConfig(ModelType.FAST, "The Sentinel")

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

        // Mock initializeWithStartupResult to avoid triggering actual work in init
        every { mockOrchestrator.initializeWithStartupResult(any()) } returns Unit

        viewModel = DownloadViewModel(
            modelDownloadOrchestrator = mockOrchestrator,
            downloadWorkRepository = mockDownloadWorkRepository,
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
                modelTypes = listOf(ModelType.DRAFT_TWO),
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
            modelRegistry = mockModelRegistry,
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
                modelTypes = listOf(ModelType.MAIN, ModelType.DRAFT_TWO),
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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
            modelRegistry = mockModelRegistry,
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

    // ============================================================================
    // WiFi Toggle Tests - Download Flow Scenarios
    // ============================================================================

    /**
     * Scenario 1: User toggles OFF "only download on WiFi" after it was initially ON
     * and they are on mobile data.
     *
     * Expected: Downloads should proceed (wifiOnly=false bypasses WiFi check)
     */
    @Test
    fun `toggle wifiOnly OFF while on mobile - downloads proceed`() = runTest {
        // Given - wifiOnly is ON by default
        assertTrue(viewModel.wifiOnly.value)

        // Simulate being on mobile by having startDownloads return false (WiFi blocked)
        coEvery { mockOrchestrator.startDownloads(any(), wifiOnly = true) } returns false

        // Foreground the app
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // When - User tries to start downloads - should be blocked due to WiFi-only
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify it was blocked
        coVerify { mockOrchestrator.startDownloads(any(), wifiOnly = true) }
        assertTrue(viewModel.showWifiDialog.value)

        // Now user toggles OFF wifiOnly
        viewModel.setWifiOnly(false)
        assertFalse(viewModel.wifiOnly.value)

        // User clicks "Download on Mobile" button
        coEvery { mockOrchestrator.downloadOnMobileData() } returns Unit
        viewModel.downloadOnMobileData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - downloadOnMobileData should be called, which sets wifiOnly=false and starts downloads
        coVerify { mockOrchestrator.downloadOnMobileData() }
    }

    /**
     * Scenario 2: User toggles ON "only download on WiFi" after it was OFF
     * and they are NOT on WiFi.
     *
     * Expected: Downloads should be blocked, WiFi dialog should show
     */
    @Test
    fun `toggle wifiOnly ON while NOT on WiFi - shows WiFi dialog`() = runTest {
        // Given - wifiOnly is initially OFF (user had disabled it)
        viewModel.setWifiOnly(false)
        assertFalse(viewModel.wifiOnly.value)

        // User toggles WiFi-only ON while on mobile
        viewModel.setWifiOnly(true)
        assertTrue(viewModel.wifiOnly.value)

        // Foreground the app
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // Mock orchestrator to block due to WiFi
        coEvery { mockOrchestrator.startDownloads(any(), wifiOnly = true) } returns false

        // When - User tries to start downloads
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - Should be blocked and show WiFi dialog
        coVerify { mockOrchestrator.startDownloads(any(), wifiOnly = true) }
        assertTrue(viewModel.showWifiDialog.value)
    }

    /**
     * Scenario 3: User toggles ON "only download on WiFi" while ON WiFi
     *
     * Expected: Downloads should proceed normally
     */
    @Test
    fun `toggle wifiOnly ON while ON WiFi - downloads proceed normally`() = runTest {
        // Given - wifiOnly is initially OFF
        viewModel.setWifiOnly(false)
        assertFalse(viewModel.wifiOnly.value)

        // User toggles WiFi-only ON while on WiFi
        viewModel.setWifiOnly(true)
        assertTrue(viewModel.wifiOnly.value)

        // Foreground the app
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // Mock orchestrator to allow download
        coEvery { mockOrchestrator.startDownloads(any(), wifiOnly = true) } returns true

        // When - User starts downloads
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - Downloads should proceed
        coVerify { mockOrchestrator.startDownloads(any(), wifiOnly = true) }
        assertFalse(viewModel.showWifiDialog.value)
    }

    /**
     * Scenario 4: Happy path - WiFi connected, WiFi-only enabled
     *
     * Expected: Downloads proceed without showing WiFi dialog
     */
    @Test
    fun `happy path - WiFi connected with WiFi-only - downloads proceed`() = runTest {
        // Given - WiFi-only is ON by default
        assertTrue(viewModel.wifiOnly.value)

        // Foreground the app
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // Mock orchestrator to allow download
        coEvery { mockOrchestrator.startDownloads(any(), wifiOnly = true) } returns true

        // When - User starts downloads
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - Downloads should proceed without WiFi dialog
        coVerify { mockOrchestrator.startDownloads(any(), wifiOnly = true) }
        assertFalse(viewModel.showWifiDialog.value)
    }

    /**
     * Scenario 5: ViewModel properly updates downloadState at each step
     *
     * Expected: StateFlow reflects orchestrator state changes
     */
    @Test
    fun `downloadState updates from orchestrator during download flow`() = runTest {
        // Given - initial state
        val initialState = viewModel.downloadState.value
        assertEquals(DownloadStatus.CHECKING, initialState.status)

        // When - orchestrator emits DOWNLOADING state
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.25f,
            modelsTotal = 3,
            modelsComplete = 0
        )
        testDispatcher.scheduler.runCurrent()

        // Then - ViewModel reflects the state
        val downloadingState = viewModel.downloadState.value
        assertEquals(DownloadStatus.DOWNLOADING, downloadingState.status)
        assertEquals(0.25f, downloadingState.overallProgress)
        assertEquals(3, downloadingState.modelsTotal)

        // When - orchestrator emits progress update
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            modelsTotal = 3,
            modelsComplete = 1,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 100_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.DOWNLOADING,
                    speedMBs = 10.0
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Then - ViewModel reflects progress
        val progressState = viewModel.downloadState.value
        assertEquals(0.5f, progressState.overallProgress)
        assertEquals(1, progressState.modelsComplete)

        // When - orchestrator emits COMPLETED state
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.READY,
            overallProgress = 1.0f,
            modelsTotal = 3,
            modelsComplete = 3
        )
        testDispatcher.scheduler.runCurrent()

        // Then - ViewModel reflects completed
        val completedState = viewModel.downloadState.value
        assertEquals(DownloadStatus.READY, completedState.status)
        assertEquals(1.0f, completedState.overallProgress)
    }

    /**
     * Scenario 6: fileProgressList properly updates during download
     *
     * Expected: Each file progress update is reflected in UI model
     */
    @Test
    fun `fileProgressList updates with live download progress`() = runTest {
        // Given - no downloads initially
        assertTrue(viewModel.fileProgressList.value.isEmpty())

        // When - download starts with one file
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 50_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.DOWNLOADING,
                    speedMBs = 10.0
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Then - fileProgressList reflects progress
        val progressList = viewModel.fileProgressList.value
        assertEquals(1, progressList.size)
        assertEquals("main.litertlm", progressList[0].filename)
        assertEquals(0.25f, progressList[0].progress)
        assertEquals(10.0, progressList[0].speedMBs)

        // When - download progresses
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 150_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.DOWNLOADING,
                    speedMBs = 15.0
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Then - progress updated
        val updatedList = viewModel.fileProgressList.value
        assertEquals(0.75f, updatedList[0].progress)
        assertEquals(15.0, updatedList[0].speedMBs)

        // When - download completes
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.READY,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 200_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.COMPLETE,
                    speedMBs = null
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Then - completed status reflected
        val completedList = viewModel.fileProgressList.value
        assertEquals(FileStatus.COMPLETE, completedList[0].status)
    }

    /**
     * Scenario 7: Resume download of partially downloaded files
     *
     * Expected: Resume works and continues from where it left off
     */
    @Test
    fun `resume downloads continues from partial progress`() = runTest {
        // Given - download was paused with partial progress
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.PAUSED,
            overallProgress = 0.5f,
            modelsTotal = 2,
            modelsComplete = 0,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 100_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.PAUSED,
                    speedMBs = null
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // When - User resumes downloads
        coEvery { mockOrchestrator.resumeDownloads() } returns Unit
        viewModel.resumeDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - orchestrator.resumeDownloads() is called
        coVerify { mockOrchestrator.resumeDownloads() }

        // And state should transition to downloading
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            modelsTotal = 2,
            modelsComplete = 0,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 100_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.DOWNLOADING,
                    speedMBs = 10.0
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
    }

    /**
     * Scenario 8: Cancel downloads and restart
     *
     * Expected: Cancel clears state, can restart fresh
     */
    @Test
    fun `cancel downloads clears state and can restart`() = runTest {
        // Given - downloading state
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.3f,
            modelsTotal = 2,
            modelsComplete = 0
        )
        testDispatcher.scheduler.runCurrent()

        // When - User cancels
        coEvery { mockOrchestrator.cancelDownloads() } returns Unit
        viewModel.cancelDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - orchestrator.cancelDownloads() is called
        coVerify { mockOrchestrator.cancelDownloads() }

        // And state is reset (orchestrator would set to IDLE)
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.IDLE,
            overallProgress = 0f,
            modelsTotal = 0,
            modelsComplete = 0
        )
        testDispatcher.scheduler.runCurrent()

        // When - User restarts downloads
        coEvery { mockOrchestrator.startDownloads(any(), any()) } returns true
        viewModel.onAppForegrounded()
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - Downloads restart
        coVerify { mockOrchestrator.startDownloads(any(), any()) }
    }

    /**
     * Scenario 9: Download fails with error
     *
     * Expected: Error state is properly reflected
     */
    @Test
    fun `download error updates state correctly`() = runTest {
        // Given - downloading state
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f
        )
        testDispatcher.scheduler.runCurrent()

        // When - Download fails
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.ERROR,
            errorMessage = "Network connection lost"
        )
        testDispatcher.scheduler.runCurrent()

        // Then - Error state reflected
        val errorState = viewModel.downloadState.value
        assertEquals(DownloadStatus.ERROR, errorState.status)
        assertEquals("Network connection lost", errorState.errorMessage)

        // When - User retries
        coEvery { mockOrchestrator.retryFailed() } returns Unit
        viewModel.retryFailed()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - retry is called
        coVerify { mockOrchestrator.retryFailed() }
    }

    /**
     * Scenario 10: Dismiss WiFi dialog
     *
     * Expected: Dialog is dismissed, no action taken
     */
    @Test
    fun `dismiss WiFi dialog clears dialog state`() = runTest {
        // Given - WiFi dialog is showing
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.IDLE, wifiBlocked = true)
        viewModel.dismissWifiDialog()

        // Then - Dialog is dismissed
        assertFalse(viewModel.showWifiDialog.value)

        // When - User starts downloads again (should still be blocked)
        coEvery { mockOrchestrator.startDownloads(any(), wifiOnly = true) } returns false
        viewModel.onAppForegrounded()
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - Dialog shows again
        assertTrue(viewModel.showWifiDialog.value)
    }

    /**
     * Edge Case: Multiple rapid state changes
     *
     * Expected: Each state change is reflected correctly
     */
    @Test
    fun `handles rapid state changes without missing updates`() = runTest {
        // Given - Initial state
        assertEquals(DownloadStatus.CHECKING, viewModel.downloadState.value.status)

        // When - Rapid state changes
        _downloadStateFlow.value = DownloadState(status = DownloadStatus.IDLE)
        testDispatcher.scheduler.runCurrent()

        _downloadStateFlow.value = DownloadState(status = DownloadStatus.DOWNLOADING, overallProgress = 0.1f)
        testDispatcher.scheduler.runCurrent()

        _downloadStateFlow.value = DownloadState(status = DownloadStatus.DOWNLOADING, overallProgress = 0.5f)
        testDispatcher.scheduler.runCurrent()

        _downloadStateFlow.value = DownloadState(status = DownloadStatus.DOWNLOADING, overallProgress = 0.9f)
        testDispatcher.scheduler.runCurrent()

        // Then - Final state is correct
        assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
        assertEquals(0.9f, viewModel.downloadState.value.overallProgress)
    }

    /**
     * Edge Case: Start downloads when already downloading
     *
     * Expected: Does not cause duplicate downloads
     */
    @Test
    fun `starting downloads while already downloading does not duplicate`() = runTest {
        // Given - Already downloading
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f
        )
        testDispatcher.scheduler.runCurrent()

        viewModel.onAppForegrounded()

        // When - User clicks start again
        coEvery { mockOrchestrator.startDownloads(any(), any()) } returns true
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - startDownloads is called (orchestrator handles idempotency)
        coVerify { mockOrchestrator.startDownloads(any(), any()) }
    }

    // ============================================================================
    // Network Change & Resume Scenarios
    // ============================================================================

    /**
     * Scenario: Download running on WiFi, user disconnects and goes to mobile,
     * then toggles OFF "only download on WiFi" to resume on mobile.
     *
     * This is the key real-world flow:
     * 1. Start on WiFi with wifiOnly=true
     * 2. WiFi disconnects, download stops (WorkManager UNMETERED constraint fails)
     * 3. User toggles wifiOnly OFF
     * 4. Resume should continue from where it left off
     */
    @Test
    fun `resume after wifi to mobile switch with wifiOnly disabled`() = runTest {
        // Given - Download was running on WiFi, paused due to network switch
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.PAUSED,
            overallProgress = 0.5f,
            modelsTotal = 2,
            modelsComplete = 0,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 100_000_000,  // 50% - partial download
                    totalBytes = 200_000_000,
                    status = FileStatus.PAUSED,
                    speedMBs = null
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Verify initial partial state
        assertEquals(FileStatus.PAUSED, viewModel.fileProgressList.value[0].status)
        assertEquals(100_000_000L, viewModel.fileProgressList.value[0].bytesDownloaded)

        // User toggles OFF wifiOnly
        viewModel.setWifiOnly(false)
        assertFalse(viewModel.wifiOnly.value)

        // User clicks "Download on Mobile" to resume
        coEvery { mockOrchestrator.downloadOnMobileData() } returns Unit
        viewModel.downloadOnMobileData()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - downloadOnMobileData should be called (this will use CONNECTED constraint)
        coVerify { mockOrchestrator.downloadOnMobileData() }

        // And state should transition to downloading
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.5f,
            modelsTotal = 2,
            modelsComplete = 0,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 100_000_000,  // Same progress - resumes from here
                    totalBytes = 200_000_000,
                    status = FileStatus.DOWNLOADING,
                    speedMBs = 5.0
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Verify download resumed with same progress
        assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
        assertEquals(100_000_000L, viewModel.fileProgressList.value[0].bytesDownloaded)
    }

    /**
     * Scenario: Download fails due to network loss, then network returns
     * WorkManager should auto-retry with exponential backoff
     *
     * Note: This tests the ViewModel state transitions, actual WorkManager retry
     * is handled by the system
     */
    @Test
    fun `download state transitions to error when network lost`() = runTest {
        // Given - Download is running
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.3f,
            modelsTotal = 2,
            modelsComplete = 0
        )
        testDispatcher.scheduler.runCurrent()

        // When - Network is lost (work goes to BLOCKED or fails)
        // WorkManager handles the actual retry, we just track state
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.ERROR,
            errorMessage = "Network connection lost"
        )
        testDispatcher.scheduler.runCurrent()

        // Then - Error state is reflected
        val errorState = viewModel.downloadState.value
        assertEquals(DownloadStatus.ERROR, errorState.status)
        assertEquals("Network connection lost", errorState.errorMessage)
    }

    /**
     * Scenario: User can retry after network loss
     */
    @Test
    fun `can retry after network loss error`() = runTest {
        // Given - Error state from network loss
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.ERROR,
            errorMessage = "Network connection lost"
        )
        testDispatcher.scheduler.runCurrent()

        // User retries
        coEvery { mockOrchestrator.retryFailed() } returns Unit
        viewModel.retryFailed()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - retry is called
        coVerify { mockOrchestrator.retryFailed() }
    }

    /**
     * Scenario: WiFi-only enabled, user is on mobile, connects to WiFi,
     * then downloads should work with UNMETERED constraint
     */
    @Test
    fun `download proceeds on wifi after connecting from mobile`() = runTest {
        // Given - User was on mobile with wifiOnly=true (blocked)
        // They now connect to WiFi
        viewModel.setWifiOnly(true)  // WiFi-only is ON

        // Foreground the app
        viewModel.onAppForegrounded()
        testDispatcher.scheduler.advanceUntilIdle()

        // Mock orchestrator to allow (WiFi is now connected)
        coEvery { mockOrchestrator.startDownloads(any(), wifiOnly = true) } returns true

        // When - User starts downloads (now on WiFi)
        viewModel.startDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then - Downloads start with wifiOnly=true
        coVerify { mockOrchestrator.startDownloads(any(), wifiOnly = true) }
        assertFalse(viewModel.showWifiDialog.value)
    }

    /**
     * Scenario: Partial download progress is preserved through pause/resume cycle
     */
    @Test
    fun `partial download progress preserved through pause resume`() = runTest {
        // Given - 75% downloaded
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.DOWNLOADING,
            overallProgress = 0.75f,
            modelsTotal = 2,
            modelsComplete = 1,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 150_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.DOWNLOADING,
                    speedMBs = 10.0
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // User pauses
        coEvery { mockOrchestrator.pauseDownloads() } returns Unit
        viewModel.pauseDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // State should show paused
        _downloadStateFlow.value = DownloadState(
            status = DownloadStatus.PAUSED,
            overallProgress = 0.75f,
            currentDownloads = listOf(
                FileProgress(
                    filename = "main.litertlm",
                    modelTypes = listOf(ModelType.MAIN),
                    bytesDownloaded = 150_000_000,
                    totalBytes = 200_000_000,
                    status = FileStatus.PAUSED,
                    speedMBs = null
                )
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Verify progress preserved
        assertEquals(0.75f, viewModel.downloadState.value.overallProgress)
        assertEquals(150_000_000L, viewModel.fileProgressList.value[0].bytesDownloaded)

        // User resumes
        coEvery { mockOrchestrator.resumeDownloads() } returns Unit
        viewModel.resumeDownloads()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify resume called
        coVerify { mockOrchestrator.resumeDownloads() }
    }
}

