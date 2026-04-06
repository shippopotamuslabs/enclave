package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class CheckModelEligibilityUseCaseTest {

    private lateinit var useCase: CheckModelEligibilityUseCase
    private lateinit var mockLogger: LoggingPort

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0

        mockLogger = mockk(relaxed = true)
        useCase = CheckModelEligibilityUseCase(mockLogger)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createModelAsset(
        sha256: String,
        sizeInBytes: Long = 1000000L,
        modelFileFormat: ModelFileFormat = ModelFileFormat.LITERTLM,
        localFileName: String = "model.litertlm",
        displayName: String = "Test Config",
        thinkingEnabled: Boolean = false
    ): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "test/model",
                remoteFileName = localFileName,
                localFileName = localFileName,
                sha256 = sha256,
                sizeInBytes = sizeInBytes,
                modelFileFormat = modelFileFormat
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    localModelId = 1L,
                    displayName = displayName,
                    maxTokens = 2048,
                    contextWindow = 2048,
                    temperature = 0.7,
                    topP = 0.95,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    thinkingEnabled = thinkingEnabled,
                    systemPrompt = "You are helpful."
                )
            )
        )
    }

    @Test
    fun `check returns models for missing files`() {
        val asset = createModelAsset(
            sha256 = "abc123",
            localFileName = "main.litertlm"
        )
        val scanResult = ModelScanResult(
            missingModels = listOf(asset),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = false,
            directoryError = false
        )

        val result = useCase.check(
            mapOf(ModelType.MAIN to asset),
            scanResult
        )

        assertTrue(result.isNotEmpty())
        assertEquals(1, result.size)
    }

    @Test
    fun `check handles empty scan result (all valid)`() {
        val asset = createModelAsset(sha256 = "abc123")
        val scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = true,
            directoryError = false
        )

        val result = useCase.check(
            mapOf(ModelType.MAIN to asset),
            scanResult
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `check handles multiple missing models`() {
        val asset1 = createModelAsset(
            sha256 = "abc123",
            localFileName = "main1.litertlm"
        )
        val asset2 = createModelAsset(
            sha256 = "def456",
            localFileName = "vision.litertlm"
        )
        val scanResult = ModelScanResult(
            missingModels = listOf(asset1, asset2),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = false,
            directoryError = false
        )

        val result = useCase.check(
            mapOf(ModelType.MAIN to asset1, ModelType.VISION to asset2),
            scanResult
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `check returns empty list when no missing partial or invalid`() {
        val asset = createModelAsset(
            sha256 = "abc123",
            localFileName = "main.litertlm"
        )
        val scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = true,
            directoryError = false
        )

        val result = useCase.check(
            mapOf(ModelType.MAIN to asset),
            scanResult
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `check includes models with partial downloads`() {
        val asset = createModelAsset(
            sha256 = "abc123",
            localFileName = "main.litertlm"
        )
        val scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = mapOf("main.litertlm" to 500000L),
            invalidModels = emptyList(),
            allValid = false,
            directoryError = false
        )

        val result = useCase.check(
            mapOf(ModelType.MAIN to asset),
            scanResult
        )

        assertTrue(result.any { it.metadata.sha256 == "abc123" })
    }

    @Test
    fun `check includes invalid models due to MD5 mismatch`() {
        val asset = createModelAsset(
            sha256 = "abc123",
            localFileName = "main.litertlm"
        )

        val scanResult = ModelScanResult(
            missingModels = emptyList(),
            partialDownloads = emptyMap(),
            invalidModels = listOf(asset),
            allValid = false,
            directoryError = false
        )

        val result = useCase.check(
            mapOf(ModelType.MAIN to asset),
            scanResult
        )

        assertTrue(result.any { it.metadata.sha256 == "abc123" })
    }

    @Test
    fun `check preserves slot specific assets with same SHA256`() {
        val asset1 = createModelAsset(
            sha256 = "shared-sha256",
            localFileName = "shared.litertlm",
            displayName = "Gemma 4 E4B (Vision)"
        )
        val asset2 = createModelAsset(
            sha256 = "shared-sha256",
            localFileName = "shared.litertlm",
            displayName = "Gemma 4 E4B (Fast)"
        )
        val asset3 = createModelAsset(
            sha256 = "shared-sha256",
            localFileName = "shared.litertlm",
            displayName = "Gemma 4 E4B (Thinking)",
            thinkingEnabled = true
        )
        val scanResult = ModelScanResult(
            missingModels = listOf(asset1, asset2, asset3),
            partialDownloads = emptyMap(),
            invalidModels = emptyList(),
            allValid = false,
            directoryError = false
        )

        val result = useCase.check(
            mapOf(
                ModelType.VISION to asset1,
                ModelType.FAST to asset2,
                ModelType.THINKING to asset3
            ),
            scanResult
        )

        assertEquals(3, result.size)
        assertEquals(
            setOf("Gemma 4 E4B (Vision)", "Gemma 4 E4B (Fast)", "Gemma 4 E4B (Thinking)"),
            result.map { it.configurations.first().displayName }.toSet()
        )
    }
}
