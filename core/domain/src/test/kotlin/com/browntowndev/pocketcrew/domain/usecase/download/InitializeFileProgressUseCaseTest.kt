package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.download.FileProgress
import com.browntowndev.pocketcrew.domain.model.download.FileStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class InitializeFileProgressUseCaseTest {

    private lateinit var useCase: InitializeFileProgressUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        useCase = InitializeFileProgressUseCase()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun invoke_createsFileProgressList_fromMissingModels() {
        // Given
        val asset = createLocalModelAsset(
            modelType = ModelType.MAIN,
            localFileName = "main.litertlm",
            sha256 = "abc123",
            sizeInBytes = 1000000L
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(asset),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = mapOf(ModelType.MAIN to asset)

        // When
        val result = useCase(scanResult, allModels)

        // Then
        assertEquals(1, result.fileProgressList.size)
        assertEquals("main.litertlm", result.fileProgressList.first().filename)
        assertEquals(0L, result.fileProgressList.first().bytesDownloaded)
        assertEquals(1000000L, result.fileProgressList.first().totalBytes)
        assertEquals(FileStatus.QUEUED, result.fileProgressList.first().status)
    }

    @Test
    fun invoke_handlesPartialDownloads() {
        // Given
        val asset = createLocalModelAsset(
            modelType = ModelType.MAIN,
            localFileName = "main.litertlm",
            sha256 = "abc123",
            sizeInBytes = 1000000L
        )

        val scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = mapOf("main.litertlm" to 500000L),
            allValid = false,
            directoryError = false
        )

        val allModels = mapOf(ModelType.MAIN to asset)

        // When
        val result = useCase(scanResult, allModels)

        // Then
        assertEquals(1, result.fileProgressList.size)
        assertEquals(500000L, result.fileProgressList.first().bytesDownloaded)
        assertEquals(FileStatus.DOWNLOADING, result.fileProgressList.first().status)
    }

    @Test
    fun invoke_calculatesOverallProgress() {
        // Given
        val mainAsset = createLocalModelAsset(
            modelType = ModelType.MAIN,
            localFileName = "main.litertlm",
            sha256 = "abc123",
            sizeInBytes = 1000000L
        )

        val visionAsset = createLocalModelAsset(
            modelType = ModelType.VISION,
            localFileName = "vision.litertlm",
            sha256 = "vision123",
            sizeInBytes = 2000000L
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(mainAsset, visionAsset),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = mapOf(
            ModelType.MAIN to mainAsset,
            ModelType.VISION to visionAsset
        )

        // When
        val result = useCase(scanResult, allModels)

        // Then
        assertEquals(2, result.fileProgressList.size)
        assertEquals(2, result.modelsTotal)
    }

    @Test
    fun invoke_handlesExistingFailedDownloads() {
        // Given
        val asset = createLocalModelAsset(
            modelType = ModelType.MAIN,
            localFileName = "main.litertlm",
            sha256 = "abc123",
            sizeInBytes = 1000000L
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(asset),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = mapOf(ModelType.MAIN to asset)

        val existingDownloads = listOf(
            FileProgress(
                filename = "main.litertlm",
                modelTypes = listOf(ModelType.MAIN),
                bytesDownloaded = 750000L,
                totalBytes = 1000000L,
                status = FileStatus.FAILED
            )
        )

        // When
        val result = useCase(scanResult, allModels, existingDownloads)

        // Then
        assertEquals(1, result.fileProgressList.size)
        assertEquals(750000L, result.fileProgressList.first().bytesDownloaded)
    }

    @Test
    fun invoke_handlesMultipleModelTypes() {
        // Given - Multiple configurations with same remote file
        val mainAsset = createLocalModelAsset(
            modelType = ModelType.MAIN,
            localFileName = "shared.litertlm",
            sha256 = "abc123",
            sizeInBytes = 1000000L
        )

        val fastAsset = createLocalModelAsset(
            modelType = ModelType.FAST,
            localFileName = "shared.litertlm",
            sha256 = "abc123",
            sizeInBytes = 1000000L
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(mainAsset), // Only need one in missingModels since they share SHA
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = mapOf(
            ModelType.MAIN to mainAsset,
            ModelType.FAST to fastAsset
        )

        // When
        val result = useCase(scanResult, allModels)

        // Then
        assertEquals(1, result.fileProgressList.size)
        assertTrue(result.fileProgressList.first().modelTypes.contains(ModelType.MAIN))
        assertTrue(result.fileProgressList.first().modelTypes.contains(ModelType.FAST))
    }

    private fun createLocalModelAsset(
        modelType: ModelType,
        localFileName: String,
        sha256: String,
        sizeInBytes: Long
    ): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "test/model",
                remoteFileName = localFileName,
                localFileName = localFileName,
                sha256 = sha256,
                sizeInBytes = sizeInBytes,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    localModelId = 1L,
                    displayName = "${modelType.name} Config",
                    maxTokens = 2048,
                    contextWindow = 2048,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    systemPrompt = "You are a helpful assistant."
                )
            )
        )
    }
}
