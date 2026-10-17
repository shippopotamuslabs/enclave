package com.browntowndev.enclave.feature.download

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.model.download.FileProgress
import com.browntowndev.enclave.domain.model.download.FileStatus
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.core.testing.download.FakeModelDownloadOrchestrator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelActionsTest {
    private val orchestrator = FakeModelDownloadOrchestrator()
    private val refreshDownloadPlan = RecordingDownloadPlanRefresher()
    private val errorHandler = FakeViewModelErrorHandler()
    private val downloadStateFlow = orchestrator.downloadStateFlow

    private val testMetadata =
        LocalModelMetadata(
            id = LocalModelId("model-1"),
            huggingFaceModelName = "test/model",
            remoteFileName = "model.bin",
            localFileName = "model.bin",
            sha256 = "abc123",
            sizeInBytes = 1_000_000L,
            modelFileFormat = ModelFileFormat.GGUF,
        )

    private val testModelAsset =
        LocalModelAsset(
            metadata = testMetadata,
            configurations = emptyList(),
        )

    private val testModelsResult =
        DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = listOf(testModelAsset),
            scanResult =
                ModelScanResult(
                    missingModels = listOf(testModelAsset),
                    partialDownloads = emptyMap(),
                    allValid = true,
                ),
        )

    private fun createViewModel(
        modelsResult: DownloadModelsResult = testModelsResult,
        initialErrorMessage: String? = null,
        autoStartDownloads: Boolean = false,
        refreshPlan: DownloadPlanRefresher? = refreshDownloadPlan,
    ): DownloadViewModel =
        DownloadViewModel(
            modelDownloadOrchestrator = orchestrator,
            observeDownloadWork = com.browntowndev.enclave.domain.usecase.download.ObserveDownloadWorkUseCase(com.browntowndev.enclave.core.testing.download.FakeDownloadWorkObserverPort()),
            parseDownloadProgress = com.browntowndev.enclave.domain.usecase.download.ParseDownloadProgressUseCase(object : com.browntowndev.enclave.domain.port.download.DownloadProgressParserPort {
                override fun parseProgress(currentDownloads: List<com.browntowndev.enclave.domain.model.download.FileProgress>) = null
            }),
            errorHandler = errorHandler,
            modelsResult = modelsResult,
            initialErrorMessage = initialErrorMessage,
            autoStartDownloads = autoStartDownloads,
            refreshDownloadPlan = refreshPlan,
        )

    @Test
    fun `startDownloads calls orchestrator with modelsResult`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.startDownloads()
            advanceUntilIdle()

            assertEquals(listOf(testModelsResult to true), orchestrator.startCalls)
        }

    @Test
    fun `startDownloads skips duplicate calls`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.startDownloads()
            viewModel.startDownloads()
            advanceUntilIdle()

            assertEquals(listOf(testModelsResult to true), orchestrator.startCalls)
        }

    @Test
    fun `pauseDownloads calls orchestrator and resets downloadsStarted`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.startDownloads()
            advanceUntilIdle()

            viewModel.pauseDownloads()

            assertEquals(1, orchestrator.pauseCount)
        }

    @Test
    fun `resumeDownloads calls orchestrator`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.resumeDownloads()
            advanceUntilIdle()

            assertEquals(1, orchestrator.resumeCount)
        }

    @Test
    fun `cancelDownloads calls orchestrator`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.cancelDownloads()
            advanceUntilIdle()

            assertEquals(1, orchestrator.cancelCount)
        }

    @Test
    fun `retryFailed refreshes catalog then calls orchestrator and resets downloadsStarted`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.retryFailed()
            advanceUntilIdle()

            assertEquals(1, refreshDownloadPlan.refreshCount)
            assertEquals(1, orchestrator.pauseCount)
            assertEquals(1, orchestrator.retryCount)
        }

    @Test
    fun `downloadOnMobileData sets wifiOnly to false and calls orchestrator`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            assertTrue(viewModel.wifiOnly.value)

            viewModel.downloadOnMobileData()
            advanceUntilIdle()

            assertFalse(viewModel.wifiOnly.value)
            assertEquals(1, orchestrator.downloadOnMobileDataCount)
        }

    @Test
    fun `setWifiOnly updates wifiOnly state`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            assertTrue(viewModel.wifiOnly.value)

            viewModel.setWifiOnly(false)
            assertEquals(false, viewModel.wifiOnly.value)

            viewModel.setWifiOnly(true)
            assertEquals(true, viewModel.wifiOnly.value)
        }

    @Test
    fun `initial error message sets error on orchestrator`() =
        runTest {
            val viewModel =
                createViewModel(
                    initialErrorMessage = "Initialization failed",
                    autoStartDownloads = false,
                )
            advanceUntilIdle()

            assertEquals(listOf("Initialization failed"), orchestrator.setErrorCalls)
        }

    @Test
    fun `autoStartDownloads triggers startDownloads when models present`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = true)
            advanceUntilIdle()

            assertEquals(listOf(testModelsResult to true), orchestrator.startCalls)
        }

    @Test
    fun `autoStartDownloads does not trigger when no models to download`() =
        runTest {
            val emptyResult =
                DownloadModelsResult(
                    allModels = emptyMap(),
                    modelsToDownload = emptyList(),
                    scanResult =
                        ModelScanResult(
                            missingModels = emptyList(),
                            partialDownloads = emptyMap(),
                            allValid = true,
                        ),
                )
            val viewModel =
                createViewModel(
                    modelsResult = emptyResult,
                    autoStartDownloads = true,
                )
            advanceUntilIdle()

            assertTrue(orchestrator.startCalls.isEmpty())
        }

    @Test
    fun `checkModels starts downloads when models are missing`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.checkModels()
            advanceUntilIdle()

            assertEquals(listOf(testModelsResult to true), orchestrator.startCalls)
        }

    @Test
    fun `downloadState reflects orchestrator state`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            assertEquals(DownloadStatus.IDLE, viewModel.downloadState.value.status)

            downloadStateFlow.value =
                downloadStateFlow.value.copy(
                    status = DownloadStatus.DOWNLOADING,
                    overallProgress = 0.5f,
                )
            advanceUntilIdle()

            assertEquals(DownloadStatus.DOWNLOADING, viewModel.downloadState.value.status)
            assertEquals(0.5f, viewModel.downloadState.value.overallProgress)
        }

    @Test
    fun `fileProgressList maps FileProgress to UiModel with display names`() =
        runTest {
            val fileProgress =
                FileProgress(
                    filename = "model.bin",
                    sha256 = "abc123",
                    bytesDownloaded = 500,
                    totalBytes = 1000,
                    status = FileStatus.DOWNLOADING,
                    modelTypes = listOf(ModelType.FAST),
                )
            downloadStateFlow.value =
                downloadStateFlow.value.copy(
                    currentDownloads = listOf(fileProgress),
                )

            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            assertEquals(1, viewModel.fileProgressList.value.size)
            assertEquals("Fast", viewModel.fileProgressList.value[0].displayName)
            assertEquals(0.5f, viewModel.fileProgressList.value[0].progress)
        }

    @Test
    fun `pauseDownloads allows restart after pause`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.startDownloads()
            advanceUntilIdle()

            viewModel.pauseDownloads()
            advanceUntilIdle()

            // After pause, startDownloads should work again since downloadsStarted is reset
            viewModel.startDownloads()
            advanceUntilIdle()

            assertEquals(listOf(testModelsResult to true, testModelsResult to true), orchestrator.startCalls)
        }

    @Test
    fun `onAppForegrounded and onAppBackgrounded do not crash`() =
        runTest {
            val viewModel = createViewModel(autoStartDownloads = false)
            advanceUntilIdle()

            viewModel.onAppForegrounded()
            viewModel.onAppBackgrounded()
            viewModel.onAppForegrounded()
        }
}
