package com.browntowndev.enclave.feature.download

import com.browntowndev.enclave.core.data.download.WorkProgressParser
import com.browntowndev.enclave.core.data.repository.DownloadWorkRepository
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.FileProgress
import com.browntowndev.enclave.domain.model.download.FileStatus
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import io.mockk.every
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
        val progress =
            FileProgress(
                filename = "test.bin",
                sha256 = "test_sha",
                bytesDownloaded = 100,
                totalBytes = 1000,
                status = FileStatus.DOWNLOADING,
                modelTypes = listOf(ModelType.DRAFT_ONE),
            )

        // Mock dependencies for ViewModel (though we only need the extension)
        val orchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)
        val workRepo = mockk<DownloadWorkRepository>(relaxed = true)
        val parser = mockk<WorkProgressParser>(relaxed = true)
        val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
        val result = mockk<DownloadModelsResult>(relaxed = true)

        val viewModel =
            DownloadViewModel(
                modelDownloadOrchestrator = orchestrator,
                downloadWorkRepository = workRepo,
                progressParser = parser,
                errorHandler = errorHandler,
                modelsResult = result,
                initialErrorMessage = null,
                autoStartDownloads = false,
            )

        // When
        val uiModel = with(viewModel) { progress.toUiModel() }

        // Then
        assertEquals("Draft One", uiModel.displayName)
    }

    @Test
    fun `toUiModel joins multiple roles with plus sign`() {
        // Given - a FileProgress with multiple ModelTypes
        val progress =
            FileProgress(
                filename = "test.bin",
                sha256 = "test_sha",
                bytesDownloaded = 100,
                totalBytes = 1000,
                status = FileStatus.DOWNLOADING,
                modelTypes = listOf(ModelType.VISION, ModelType.DRAFT_ONE),
            )

        // Mock dependencies
        val orchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)
        val workRepo = mockk<DownloadWorkRepository>(relaxed = true)
        val parser = mockk<WorkProgressParser>(relaxed = true)
        val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
        val result = mockk<DownloadModelsResult>(relaxed = true)

        val viewModel =
            DownloadViewModel(
                modelDownloadOrchestrator = orchestrator,
                downloadWorkRepository = workRepo,
                progressParser = parser,
                errorHandler = errorHandler,
                modelsResult = result,
                initialErrorMessage = null,
                autoStartDownloads = false,
            )

        // When
        val uiModel = with(viewModel) { progress.toUiModel() }

        // Then
        assertEquals("Vision + Draft One", uiModel.displayName)
    }

    @Test
    fun `toUiModel uses utility display name when model types are empty`() {
        val whisperSha = "whisper-sha256"
        val utilityAsset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("whisper"),
                        huggingFaceModelName = "ggerganov/whisper.cpp",
                        remoteFileName = "ggml-base.en.bin",
                        localFileName = "ggml-base.en.bin",
                        sha256 = whisperSha,
                        sizeInBytes = 147_964_211L,
                        modelFileFormat = ModelFileFormat.BIN,
                        utilityType = UtilityType.WHISPER,
                    ),
                configurations = emptyList(),
            )
        val modelsResult =
            mockk<DownloadModelsResult>(relaxed = true) {
                every { utilityAssets } returns listOf(utilityAsset)
            }
        val progress =
            FileProgress(
                filename = "ggml-base.en.bin",
                sha256 = whisperSha,
                bytesDownloaded = 0,
                totalBytes = 147_964_211L,
                status = FileStatus.QUEUED,
                modelTypes = emptyList(),
            )

        val viewModel =
            DownloadViewModel(
                modelDownloadOrchestrator = mockk(relaxed = true),
                downloadWorkRepository = mockk(relaxed = true),
                progressParser = mockk(relaxed = true),
                errorHandler = mockk(relaxed = true),
                modelsResult = modelsResult,
                initialErrorMessage = null,
                autoStartDownloads = false,
            )

        val uiModel = with(viewModel) { progress.toUiModel() }

        assertEquals("Speech Recognition", uiModel.displayName)
    }

    @Test
    fun `toUiModel uses image safety classifier display name from catalog or utility type`() {
        val classifierSha = "6de8c7322b88223f734dabe0dfed6761b476a7d31c0b7443c35ec0c887ab2464"
        val utilityAsset =
            LocalModelAsset(
                metadata =
                    LocalModelMetadata(
                        id = LocalModelId("image-safety"),
                        huggingFaceModelName = null,
                        remoteFileName = "model_quantized.onnx",
                        localFileName = "model_quantized.onnx",
                        sha256 = classifierSha,
                        sizeInBytes = 87_018_507L,
                        modelFileFormat = ModelFileFormat.ONNX,
                        displayName = "Image Safety Classifier",
                        utilityType = UtilityType.IMAGE_SAFETY_CLASSIFIER,
                    ),
                configurations = emptyList(),
            )
        val modelsResult =
            mockk<DownloadModelsResult>(relaxed = true) {
                every { utilityAssets } returns listOf(utilityAsset)
            }
        val progress =
            FileProgress(
                filename = "model_quantized.onnx",
                sha256 = classifierSha,
                bytesDownloaded = 0,
                totalBytes = 87_018_507L,
                status = FileStatus.QUEUED,
                modelTypes = emptyList(),
            )

        val viewModel =
            DownloadViewModel(
                modelDownloadOrchestrator = mockk(relaxed = true),
                downloadWorkRepository = mockk(relaxed = true),
                progressParser = mockk(relaxed = true),
                errorHandler = mockk(relaxed = true),
                modelsResult = modelsResult,
                initialErrorMessage = null,
                autoStartDownloads = false,
            )

        val uiModel = with(viewModel) { progress.toUiModel() }

        assertEquals("Image Safety Classifier", uiModel.displayName)
        assertEquals(UtilityType.IMAGE_SAFETY_CLASSIFIER.displayName(), uiModel.displayName)
    }

    @Test
    fun `toUiModel maps sha256 from FileProgress to FileProgressUiModel`() {
        // Given - a FileProgress with a specific SHA256
        val progress =
            FileProgress(
                filename = "test.bin",
                sha256 = "abc123def456",
                bytesDownloaded = 100,
                totalBytes = 1000,
                status = FileStatus.DOWNLOADING,
                modelTypes = listOf(ModelType.MAIN),
            )

        val orchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)
        val workRepo = mockk<DownloadWorkRepository>(relaxed = true)
        val parser = mockk<WorkProgressParser>(relaxed = true)
        val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)
        val result = mockk<DownloadModelsResult>(relaxed = true)

        val viewModel =
            DownloadViewModel(
                modelDownloadOrchestrator = orchestrator,
                downloadWorkRepository = workRepo,
                progressParser = parser,
                errorHandler = errorHandler,
                modelsResult = result,
                initialErrorMessage = null,
                autoStartDownloads = false,
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
        val uiModel =
            DownloadViewModel.FileProgressUiModel(
                filename = "test.bin",
                sha256 = "sha-test",
                displayName = "Main",
                bytesDownloaded = 500,
                totalBytes = 1000,
                status = FileStatus.DOWNLOADING,
                speedMBs = 10.0,
            )

        // Then
        assertEquals(0.5f, uiModel.progress)
    }

    @Test
    fun `FileProgressUiModel progress returns zero when totalBytes is zero`() {
        // Given
        val uiModel =
            DownloadViewModel.FileProgressUiModel(
                filename = "test.bin",
                sha256 = "sha-test",
                displayName = "Main",
                bytesDownloaded = 0,
                totalBytes = 0,
                status = FileStatus.QUEUED,
            )

        // Then
        assertEquals(0f, uiModel.progress)
    }
}
