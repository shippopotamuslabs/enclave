package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.FileProgress
import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.domain.model.ModelType
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
        val modelConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "main.litertlm",
                localFileName = "main.litertlm",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(modelConfig),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(modelConfig)

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
        val modelConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "main.litertlm",
                localFileName = "main.litertlm",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = mapOf("main.litertlm" to 500000L),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(modelConfig)

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
        val mainModel = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "main.litertlm",
                localFileName = "main.litertlm",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val visionModel = ModelConfiguration(
            modelType = ModelType.VISION,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/vision",
                remoteFileName = "vision.litertlm",
                localFileName = "vision.litertlm",
                displayName = "Vision Model",
                md5 = "vision123",
                sizeInBytes = 2000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a vision assistant."
            )
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(mainModel, visionModel),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(mainModel, visionModel)

        // When
        val result = useCase(scanResult, allModels)

        // Then
        assertEquals(2, result.fileProgressList.size)
        assertEquals(2, result.modelsTotal)
    }

    @Test
    fun invoke_handlesExistingFailedDownloads() {
        // Given
        val modelConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "main.litertlm",
                localFileName = "main.litertlm",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(modelConfig),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(modelConfig)

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
        val mainModel = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "shared.litertlm",
                localFileName = "main.litertlm",
                displayName = "Main Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a helpful assistant."
            )
        )

        val fastModel = ModelConfiguration(
            modelType = ModelType.FAST,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "shared.litertlm",
                localFileName = "fast.litertlm",
                displayName = "Fast Model",
                md5 = "abc123",
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are a fast assistant."
            )
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(mainModel, fastModel),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(mainModel, fastModel)

        // When
        val result = useCase(scanResult, allModels)

        // Then
        assertEquals(1, result.fileProgressList.size)
        assertTrue(result.fileProgressList.first().modelTypes.contains(ModelType.MAIN))
        assertTrue(result.fileProgressList.first().modelTypes.contains(ModelType.FAST))
    }
}
