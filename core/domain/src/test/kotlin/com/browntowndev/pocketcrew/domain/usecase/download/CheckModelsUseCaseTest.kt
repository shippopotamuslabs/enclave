package com.browntowndev.pocketcrew.domain.usecase.download

import com.browntowndev.pocketcrew.domain.exception.ModelsDirectoryException
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.UtilityType
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.download.ModelFileScannerPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CheckModelsUseCaseTest {

    private lateinit var fileScanner: ModelFileScannerPort
    private lateinit var checkModelEligibilityUseCase: CheckModelEligibilityUseCase
    private lateinit var logger: LoggingPort
    private lateinit var checkModelsUseCase: CheckModelsUseCase

    private val testAsset = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = LocalModelId("1"),
            huggingFaceModelName = "TheBloke/Mistral-7B-v0.1-GGUF",
            remoteFileName = "mistral-7b-v0.1.Q4_K_M.gguf",
            localFileName = "mistral-7b-v0.1.Q4_K_M.gguf",
            sha256 = "abc123",
            sizeInBytes = 1024L,
            modelFileFormat = ModelFileFormat.GGUF
        ),
        configurations = listOf(
            LocalModelConfiguration(
                localModelId = LocalModelId("1"),
                displayName = "Test Config",
                maxTokens = 2048,
                contextWindow = 4096,
                temperature = 0.7,
                topP = 0.9,
                topK = 40,
                repetitionPenalty = 1.1,
                systemPrompt = "Test prompt"
            )
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

    @Test
    fun `all models ready returns empty models to download`() = runTest {
        // Given
        val scanResult = emptyScanResult.copy(allValid = true)

        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                expectedModels = any(),
                utilityAssets = any(),
            )
        } returns scanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns emptyList()

        // When
        val result = checkModelsUseCase(
            mapOf(ModelType.FAST to testAsset)
        )

        // Then
        assertTrue(result.modelsToDownload.isEmpty())
        verify { logger.info(any(), "All 1 models are ready") }
    }

    @Test
    fun `some models missing returns missing models`() = runTest {
        // Given
        val scanResult = ModelScanResult(
            missingModels = listOf(testAsset),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = false
        )

        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                expectedModels = any(),
                utilityAssets = any(),
            )
        } returns scanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns listOf(testAsset)

        // When
        val result = checkModelsUseCase(mapOf(ModelType.FAST to testAsset))

        // Then
        assertEquals(1, result.modelsToDownload.size)
        assertEquals("TheBloke/Mistral-7B-v0.1-GGUF", result.modelsToDownload.first().metadata.huggingFaceModelName)
        verify { logger.info(any(), match { it.contains("1 assets need download: [TheBloke/Mistral-7B-v0.1-GGUF]") }) }
    }

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
                expectedModels = any(),
                utilityAssets = any(),
            )
        } returns scanResultWithError

        // When/Then - Should throw ModelsDirectoryException
        var exception: ModelsDirectoryException? = null
        try {
            checkModelsUseCase(mapOf(ModelType.FAST to testAsset))
        } catch (e: ModelsDirectoryException) {
            exception = e
        }

        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("models directory"))
    }

    @Test
    fun `logs results for debugging`() = runTest {
        // Given
        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                expectedModels = any(),
                utilityAssets = any(),
            )
        } returns emptyScanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns listOf(testAsset)

        // When
        checkModelsUseCase(mapOf(ModelType.FAST to testAsset))

        // Then
        verify { logger.info(eq("CheckModelsUseCase"), match { it.contains("1 assets need download: [TheBloke/Mistral-7B-v0.1-GGUF]") }) }
    }

    @Test
    fun `logs when all ready`() = runTest {
        // Given
        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                expectedModels = any(),
                utilityAssets = any(),
            )
        } returns emptyScanResult

        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns emptyList()

        // When
        checkModelsUseCase(mapOf(ModelType.FAST to testAsset))

        // Then
        verify { logger.info(eq("CheckModelsUseCase"), eq("All 1 models are ready")) }
    }

    @Test
    fun `utility assets are scanned and returned in result`() = runTest {
        val utilityAsset = testAsset.copy(
            metadata = testAsset.metadata.copy(
                localFileName = "ggml-base.en.bin",
                remoteFileName = "ggml-base.en.bin",
                modelFileFormat = ModelFileFormat.BIN,
                utilityType = UtilityType.WHISPER,
            ),
            configurations = emptyList(),
        )
        val scanResult = ModelScanResult(
            missingModels = listOf(utilityAsset),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = false,
        )

        coEvery {
            fileScanner.scanAndCreateDirIfNotExist(
                expectedModels = any(),
                utilityAssets = any(),
            )
        } returns scanResult
        coEvery {
            checkModelEligibilityUseCase.check(any(), any(), any())
        } returns listOf(utilityAsset)

        val result = checkModelsUseCase(
            expectedModels = mapOf(ModelType.FAST to testAsset),
            utilityAssets = listOf(utilityAsset),
        )

        assertEquals(listOf(utilityAsset), result.utilityAssets)
        assertEquals(listOf(utilityAsset), result.modelsToDownload)
    }
}
