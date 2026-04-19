package com.browntowndev.pocketcrew.feature.download

import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.core.data.download.WorkProgressParser
import com.browntowndev.pocketcrew.core.data.repository.DownloadWorkRepository
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadViewModelUiModelTest {

    @Test
    fun `ModelType displayName returns correct names`() {
        assertEquals("Vision", ModelType.VISION.displayName())
        assertEquals("Draft One", ModelType.DRAFT_ONE.displayName())
        assertEquals("Draft Two", ModelType.DRAFT_TWO.displayName())
        assertEquals("Synthesis", ModelType.MAIN.displayName())
        assertEquals("Fast", ModelType.FAST.displayName())
        assertEquals("Thinking", ModelType.THINKING.displayName())
        assertEquals("Final Review", ModelType.FINAL_SYNTHESIS.displayName())
    }

    @Test
    fun `toUiModel uses role display name for single role`() {
        // Given - a FileProgress with a single ModelType
        val progress = FileProgress(
            filename = "test.bin",
            sha256 = "test_sha",
            bytesDownloaded = 100,
            totalBytes = 1000,
            status = FileStatus.DOWNLOADING,
            modelTypes = listOf(ModelType.DRAFT_ONE)
        )
        
        // Mock dependencies for ViewModel (though we only need the extension)
        val orchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)
        val workRepo = mockk<DownloadWorkRepository>(relaxed = true)
        val registry = mockk<LocalModelRepositoryPort>(relaxed = true)
        val parser = mockk<WorkProgressParser>(relaxed = true)
        val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
        val result = mockk<DownloadModelsResult>(relaxed = true)
        
        val viewModel = DownloadViewModel(
            modelDownloadOrchestrator = orchestrator,
            downloadWorkRepository = workRepo,
            localModelRepository = registry,
            progressParser = parser,
            errorHandler = errorHandler,
            modelsResult = result,
            initialErrorMessage = null,
            autoStartDownloads = false
        )

        // When
        val uiModel = with(viewModel) { progress.toUiModel() }

        // Then
        assertEquals("Draft One", uiModel.displayName)
    }

    @Test
    fun `toUiModel joins multiple roles with plus sign`() {
        // Given - a FileProgress with multiple ModelTypes
        val progress = FileProgress(
            filename = "test.bin",
            sha256 = "test_sha",
            bytesDownloaded = 100,
            totalBytes = 1000,
            status = FileStatus.DOWNLOADING,
            modelTypes = listOf(ModelType.VISION, ModelType.DRAFT_ONE)
        )
        
        // Mock dependencies
        val orchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)
        val workRepo = mockk<DownloadWorkRepository>(relaxed = true)
        val registry = mockk<LocalModelRepositoryPort>(relaxed = true)
        val parser = mockk<WorkProgressParser>(relaxed = true)
        val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
        val result = mockk<DownloadModelsResult>(relaxed = true)
        
        val viewModel = DownloadViewModel(
            modelDownloadOrchestrator = orchestrator,
            downloadWorkRepository = workRepo,
            localModelRepository = registry,
            progressParser = parser,
            errorHandler = errorHandler,
            modelsResult = result,
            initialErrorMessage = null,
            autoStartDownloads = false
        )

        // When
        val uiModel = with(viewModel) { progress.toUiModel() }

        // Then
        assertEquals("Vision + Draft One", uiModel.displayName)
    }

    @Test
    fun `toUiModel maps sha256 from FileProgress to FileProgressUiModel`() {
        // Given - a FileProgress with a specific SHA256
        val progress = FileProgress(
            filename = "test.bin",
            sha256 = "abc123def456",
            bytesDownloaded = 100,
            totalBytes = 1000,
            status = FileStatus.DOWNLOADING,
            modelTypes = listOf(ModelType.MAIN)
        )

        val orchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)
        val workRepo = mockk<DownloadWorkRepository>(relaxed = true)
        val registry = mockk<LocalModelRepositoryPort>(relaxed = true)
        val parser = mockk<WorkProgressParser>(relaxed = true)
        val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
        val result = mockk<DownloadModelsResult>(relaxed = true)

        val viewModel = DownloadViewModel(
            modelDownloadOrchestrator = orchestrator,
            downloadWorkRepository = workRepo,
            localModelRepository = registry,
            progressParser = parser,
            errorHandler = errorHandler,
            modelsResult = result,
            initialErrorMessage = null,
            autoStartDownloads = false
        )

        // When
        val uiModel = with(viewModel) { progress.toUiModel() }

        // Then - sha256 is properly mapped for use as LazyColumn key
        assertEquals("abc123def456", uiModel.sha256)
        assertEquals("test.bin", uiModel.filename)
    }

    @Test
    fun `FileProgressUiModel progress calculates correctly`() {
        // Given
        val uiModel = DownloadViewModel.FileProgressUiModel(
            filename = "test.bin",
            sha256 = "sha-test",
            displayName = "Main",
            bytesDownloaded = 500,
            totalBytes = 1000,
            status = FileStatus.DOWNLOADING,
            speedMBs = 10.0
        )

        // Then
        assertEquals(0.5f, uiModel.progress)
    }

    @Test
    fun `FileProgressUiModel progress returns zero when totalBytes is zero`() {
        // Given
        val uiModel = DownloadViewModel.FileProgressUiModel(
            filename = "test.bin",
            sha256 = "sha-test",
            displayName = "Main",
            bytesDownloaded = 0,
            totalBytes = 0,
            status = FileStatus.QUEUED
        )

        // Then
        assertEquals(0f, uiModel.progress)
    }
}
