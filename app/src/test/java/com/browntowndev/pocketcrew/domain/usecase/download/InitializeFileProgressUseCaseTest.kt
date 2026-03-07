package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.model.FileProgress
import com.browntowndev.pocketcrew.domain.model.FileStatus
import com.browntowndev.pocketcrew.domain.model.ModelFile
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
        val modelFile = ModelFile(
            sizeBytes = 1000000L,
            url = "https://example.com/main.bin",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",  // Must match computed filenames
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(modelFile),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(modelFile)

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
        val modelFile = ModelFile(
            sizeBytes = 1000000L,
            url = "https://example.com/main.bin",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = mapOf("main.litertlm" to 500000L),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(modelFile)

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
        val mainModel = ModelFile(
            sizeBytes = 1000000L,
            url = "https://example.com/main.bin",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val visionModel = ModelFile(
            sizeBytes = 2000000L,
            url = "https://example.com/vision.bin",
            md5 = "vision123",
            modelTypes = listOf(ModelType.VISION),
            originalFileName = "vision.litertlm",
            displayName = "Vision Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a vision assistant."
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
        val modelFile = ModelFile(
            sizeBytes = 1000000L,
            url = "https://example.com/main.bin",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(modelFile),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(modelFile)

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
        // Given
        val modelFile = ModelFile(
            sizeBytes = 1000000L,
            url = "https://example.com/main.bin",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN, ModelType.FAST),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scanResult = ModelScanResult(
            missingModels = listOf(modelFile),
            partialDownloads = emptyMap(),
            allValid = false,
            directoryError = false
        )

        val allModels = listOf(modelFile)

        // When
        val result = useCase(scanResult, allModels)

        // Then
        assertEquals(1, result.fileProgressList.size)
        assertTrue(result.fileProgressList.first().modelTypes.contains(ModelType.MAIN))
        assertTrue(result.fileProgressList.first().modelTypes.contains(ModelType.FAST))
    }
}

