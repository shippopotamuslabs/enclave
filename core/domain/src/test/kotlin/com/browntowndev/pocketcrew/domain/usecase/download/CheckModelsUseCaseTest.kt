package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.exception.ModelsDirectoryException
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for CheckModelsUseCase.
 * 
 * REF: CLARIFIED_REQUIREMENTS.md - Section 8 (CheckModelsUseCase - Directory Error)
 * 
 * Desired Behavior:
 * - Directory creation failure should throw ModelsDirectoryException (domain-specific)
 * - UI should show snackbar with recovery option
 */
class CheckModelsUseCaseTest {

    private lateinit var fileScanner: ModelFileScannerPort
    private lateinit var checkModelEligibilityUseCase: CheckModelEligibilityUseCase
    private lateinit var logger: LoggingPort
    private lateinit var checkModelsUseCase: CheckModelsUseCase

    private val testModel = ModelConfiguration(
        modelType = ModelType.FAST,
        metadata = ModelConfiguration.Metadata(
            huggingFaceModelName = "TheBloke/Mistral-7B-v0.1-GGUF",
            remoteFileName = "mistral-7b-v0.1.Q4_K_M.gguf",
            localFileName = "mistral-7b-v0.1.Q4_K_M.gguf",
            displayName = "Test Model",
            sha256 = "abc123",
            sizeInBytes = 1024L,
            modelFileFormat = ModelFileFormat.GGUF
        ),
        tunings = ModelConfiguration.Tunings(
            temperature = 0.7,
            topK = 40,
            topP = 0.9,
            repetitionPenalty = 1.1,
            maxTokens = 2048,
            contextWindow = 4096
        ),
        persona = ModelConfiguration.Persona(
            systemPrompt = "Test prompt"
        )
    )

    private val emptyScanResult = ModelScanResult(
        missingModels = emptyList(),
        partialDownloads = emptyMap(),
        invalidModels = emptyList(),
        allValid = true
    )

    @BeforeEach
    fun setUp() {
        fileScanner = mockk()
        checkModelEligibilityUseCase = mockk()
        logger = mockk(relaxed = true)
        checkModelsUseCase = CheckModelsUseCase(fileScanner, checkModelEligibilityUseCase, logger)
    }

    // ========================================================================
    // Test: All Models Present - Returns Empty List
    // Evidence: When all models are downloaded, should return empty modelsToDownload
    // ========================================================================

    @Test
    fun `all models ready returns empty models to download`() = runTest {
        // Given
        val scanResult = emptyScanResult.copy(allValid = true)

        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                downloadedModels = any(),
                expectedModels = any()
            )
        } returns scanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns emptyList()

        // When
        val result = checkModelsUseCase(listOf(testModel), listOf(testModel))

        // Then
        assertTrue(result.modelsToDownload.isEmpty())
        verify { logger.info(any(), "All 1 models are ready") }
    }

    // ========================================================================
    // Test: Some Models Missing - Returns Missing Models
    // Evidence: Should return models that need downloading
    // ========================================================================

    @Test
    fun `some models missing returns missing models`() = runTest {
        // Given
        val scanResult = ModelScanResult(
            missingModels = listOf(testModel),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = false
        )

        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                downloadedModels = any(),
                expectedModels = any()
            )
        } returns scanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns listOf(testModel)

        // When
        val result = checkModelsUseCase(emptyList(), listOf(testModel))

        // Then
        assertEquals(1, result.modelsToDownload.size)
        assertEquals(testModel.modelType, result.modelsToDownload.first().modelType)
        verify { logger.info(any(), "1 models need download: [Test Model]") }
    }

    // ========================================================================
    // Test: Directory Creation Error - Throws Domain Exception
    // Evidence: Should throw ModelsDirectoryException (not generic Exception)
    // REF: CLARIFIED_REQUIREMENTS.md - Section 8
    // ========================================================================

    @Test
    fun `directory error throws ModelsDirectoryException`() = runTest {
        // Given
        val scanResultWithError = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = true,
            directoryError = true
        )

        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                downloadedModels = any(),
                expectedModels = any()
            )
        } returns scanResultWithError

        // When/Then - Should throw ModelsDirectoryException
        var exception: ModelsDirectoryException? = null
        try {
            checkModelsUseCase(emptyList(), listOf(testModel))
        } catch (e: ModelsDirectoryException) {
            exception = e
        }

        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("models directory"))
    }

    // ========================================================================
    // Test: Logging Verification
    // Evidence: Results should be logged for debugging
    // ========================================================================

    @Test
    fun `logs results for debugging`() = runTest {
        // Given
        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                downloadedModels = any(),
                expectedModels = any()
            )
        } returns emptyScanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns listOf(testModel)

        // When
        checkModelsUseCase(emptyList(), listOf(testModel))

        // Then
        verify { logger.info(eq("CheckModelsUseCase"), eq("1 models need download: [Test Model]")) }
    }

    // ========================================================================
    // Test: Logging When All Ready
    // Evidence: Should log when all models are ready
    // ========================================================================

    @Test
    fun `logs when all models are ready`() = runTest {
        // Given
        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                downloadedModels = any(),
                expectedModels = any()
            )
        } returns emptyScanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns emptyList()

        // When
        checkModelsUseCase(listOf(testModel), listOf(testModel))

        // Then
        verify { logger.info(eq("CheckModelsUseCase"), eq("All 1 models are ready")) }
    }
}
