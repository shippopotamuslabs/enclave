package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ModelFileScannerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var modelsDir: File
    private lateinit var scanner: ModelFileScanner

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        context = mockk()
        every { context.getExternalFilesDir(null) } returns tempDir

        modelsDir = File(tempDir, ModelConfig.MODELS_DIR)
        modelsDir.deleteRecursively()
        modelsDir.mkdirs()

        scanner = ModelFileScanner(
            context = context,
            localModelRepository = mockk<LocalModelRepositoryPort>(relaxed = true),
            activeModelProvider = mockk<ActiveModelProviderPort>(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        modelsDir.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `scan accepts matching sized final file`() = kotlinx.coroutines.test.runTest {
        val asset = createAsset(filename = "model.gguf", sizeBytes = 4L)
        File(modelsDir, asset.metadata.localFileName).writeBytes(byteArrayOf(1, 2, 3, 4))

        val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset))

        assertTrue(result.missingModels.isEmpty())
        assertTrue(result.partialDownloads.isEmpty())
        assertTrue(result.invalidModels.isEmpty())
        assertTrue(result.allValid)
    }

    @Test
    fun `scan rejects final file with wrong size`() = kotlinx.coroutines.test.runTest {
        val asset = createAsset(filename = "model.gguf", sizeBytes = 4L)
        File(modelsDir, asset.metadata.localFileName).writeBytes(byteArrayOf(1, 2))

        val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset))

        assertEquals(listOf(asset), result.invalidModels)
        assertFalse(result.allValid)
    }

    @Test
    fun `scan treats temp file as partial download`() = kotlinx.coroutines.test.runTest {
        val asset = createAsset(filename = "model.gguf", sizeBytes = 4L)
        File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_EXTENSION}")
            .writeBytes(byteArrayOf(1, 2))

        val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset))

        assertEquals(2L, result.partialDownloads[asset.metadata.localFileName])
        assertTrue(result.missingModels.isEmpty())
        assertTrue(result.invalidModels.isEmpty())
        assertFalse(result.allValid)
    }

    @Test
    fun `scan prefers temp file over final file`() = kotlinx.coroutines.test.runTest {
        val asset = createAsset(filename = "model.gguf", sizeBytes = 4L)
        File(modelsDir, asset.metadata.localFileName).writeBytes(byteArrayOf(1, 2, 3, 4))
        File(modelsDir, "${asset.metadata.localFileName}${ModelConfig.TEMP_EXTENSION}")
            .writeBytes(byteArrayOf(1, 2))

        val result = scanner.scanAndCreateDirIfNotExist(mapOf(ModelType.MAIN to asset))

        assertEquals(2L, result.partialDownloads[asset.metadata.localFileName])
        assertTrue(result.invalidModels.isEmpty())
        assertFalse(result.allValid)
    }

    private fun createAsset(filename: String, sizeBytes: Long): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "test/model",
                remoteFileName = filename,
                localFileName = filename,
                sha256 = "unused-for-startup",
                sizeInBytes = sizeBytes,
                modelFileFormat = ModelFileFormat.GGUF
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    localModelId = 1L,
                    displayName = "Test",
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
    }
}
