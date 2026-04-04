package com.browntowndev.pocketcrew.core.data.download

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.download.HashingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ModelFileScannerTest {

    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherRule(testDispatcher)

    private lateinit var mockContext: Context
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockHashingPort: HashingPort
    private lateinit var scanner: ModelFileScanner

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockModelRegistry = mockk(relaxed = true)
        mockHashingPort = mockk(relaxed = true)

        // Mock context.getExternalFilesDir to return our temp dir
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        scanner = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            hashingPort = mockHashingPort
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun createModelAsset(
        modelId: Long = 1L,
        sha256: String = "testSha256",
        localFileName: String = "fast.bin"
    ): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = modelId,
                huggingFaceModelName = "test/model",
                remoteFileName = localFileName,
                localFileName = localFileName,
                sha256 = sha256,
                sizeInBytes = 1024,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    id = 1L,
                    localModelId = modelId,
                    displayName = "Test Config",
                    maxTokens = 2048,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.95,
                    topK = 40,
                    minP = 0.0,
                    repetitionPenalty = 1.0,
                    thinkingEnabled = false,
                    systemPrompt = "You are helpful."
                )
            )
        )
    }

    /**
     * Mutation Defense Test: deleteModelFile should actually delete the physical file
     *
     * Given: A model file exists on disk at the expected location
     * When: deleteModelFile is called with the model ID
     * Then: The physical file should be deleted from disk
     */
    @Test
    fun `deleteModelFile deletes the physical file from disk`() = runTest {
        // Given: a model file exists on disk and registry knows about it
        val modelId = 42L
        val localFilename = "test_model.bin"
        val modelAsset = createModelAsset(modelId = modelId, localFileName = localFilename)
        val modelsDir = File(tempDir, ModelConfig.MODELS_DIR)
        modelsDir.mkdirs()
        val modelFile = File(modelsDir, localFilename)
        modelFile.createNewFile()
        assertTrue(modelFile.exists(), "Model file should exist before deletion")

        // Mock registry to return the model when looked up by ID
        coEvery { mockModelRegistry.getAssetById(modelId) } returns modelAsset

        // When: deleteModelFile is called
        scanner.deleteModelFile(modelId)

        // Then: the physical file should be deleted
        assertFalse(modelFile.exists(), "Model file should be deleted from disk")
    }

    /**
     * Mutation Defense Test: deleteModelFile should not throw when file doesn't exist
     *
     * Given: Registry knows about model but file doesn't exist on disk
     * When: deleteModelFile is called
     * Then: It should not throw, should handle gracefully
     */
    @Test
    fun `deleteModelFile handles missing file gracefully`() = runTest {
        // Given: registry knows about the model but file doesn't exist
        val modelId = 999L
        val modelAsset = createModelAsset(modelId = modelId, localFileName = "nonexistent.bin")
        val modelsDir = File(tempDir, ModelConfig.MODELS_DIR)
        modelsDir.mkdirs()

        // Mock registry to return the model
        coEvery { mockModelRegistry.getAssetById(modelId) } returns modelAsset

        val modelFile = File(modelsDir, "nonexistent.bin")
        assertFalse(modelFile.exists(), "Model file should not exist")

        // When/Then: deleteModelFile should not throw
        scanner.deleteModelFile(modelId)
    }

    /**
     * Mutation Defense Test: deleteModelFile handles model not found in registry
     *
     * Given: Registry doesn't know about this model ID
     * When: deleteModelFile is called
     * Then: It should not throw, should handle gracefully
     */
    @Test
    fun `deleteModelFile handles model not found in registry`() = runTest {
        // Given: registry returns null for this ID
        coEvery { mockModelRegistry.getAssetById(999L) } returns null

        // When/Then: deleteModelFile should not throw
        scanner.deleteModelFile(999L)
    }

    /**
     * Integration test: Full soft-delete flow should delete physical file
     *
     * This test verifies the integration between the use case and the scanner.
     * The test creates a real file and verifies it gets deleted.
     */
    @Test
    fun `soft delete flow removes physical file`() = runTest {
        // Given: a model file that was "downloaded"
        val modelId = 42L
        val localFilename = "fast.bin"
        val modelAsset = createModelAsset(modelId = modelId, localFileName = localFilename)

        val modelsDir = File(tempDir, ModelConfig.MODELS_DIR)
        modelsDir.mkdirs()
        val modelFile = File(modelsDir, localFilename)
        modelFile.writeBytes(ByteArray(1024)) // Create a 1KB file
        assertTrue(modelFile.exists())

        // Mock registry
        coEvery { mockModelRegistry.getAssetById(modelId) } returns modelAsset

        // When: deleteModelFile is called (simulating soft-delete)
        scanner.deleteModelFile(modelId)

        // Then: file should be gone
        assertFalse(modelFile.exists(), "Physical file should be deleted during soft-delete")
    }

    /**
     * Mutation Defense Test: deleteModelFile should clean up partial download temp files
     *
     * Given: A model file exists along with a .tmp partial download file
     * When: deleteModelFile is called
     * Then: BOTH the model file AND the .tmp file should be deleted
     *
     * This prevents orphaned temp files from accumulating in the models directory.
     */
    @Test
    fun `deleteModelFile cleans up partial download temp files`() = runTest {
        // Given: model file and its associated .tmp partial download file exist
        val modelId = 42L
        val localFilename = "fast.bin"
        val modelAsset = createModelAsset(modelId = modelId, localFileName = localFilename)
        val modelsDir = File(tempDir, ModelConfig.MODELS_DIR)
        modelsDir.mkdirs()

        val modelFile = File(modelsDir, localFilename)
        val tempFile = File(modelsDir, "$localFilename${ModelConfig.TEMP_EXTENSION}")

        modelFile.writeBytes(ByteArray(1024))
        tempFile.writeBytes(ByteArray(512)) // Partial download

        assertTrue(modelFile.exists(), "Model file should exist")
        assertTrue(tempFile.exists(), "Temp file should exist")

        coEvery { mockModelRegistry.getAssetById(modelId) } returns modelAsset

        // When
        scanner.deleteModelFile(modelId)

        // Then: BOTH files should be deleted
        assertFalse(modelFile.exists(), "Model file should be deleted")
        assertFalse(tempFile.exists(), "Temp file (.tmp) should also be deleted during soft-delete")
    }

    /**
     * Mutation Defense Test: deleteModelFile handles empty filename gracefully
     *
     * Given: Registry returns an asset with an empty or whitespace filename
     * When: deleteModelFile is called
     * Then: It should not throw, should handle gracefully (no file to delete)
     */
    @Test
    fun `deleteModelFile handles empty filename gracefully`() = runTest {
        // Given: registry returns asset with empty localFileName
        val modelId = 42L
        val modelAssetWithEmptyFilename = createModelAsset(modelId = modelId, localFileName = "")
        coEvery { mockModelRegistry.getAssetById(modelId) } returns modelAssetWithEmptyFilename

        // When/Then: should not throw
        scanner.deleteModelFile(modelId)
    }

    @Test
    fun `scanAndCreateDirIfNotExist preserves all shared file slots in missing models`() = runTest {
        val sharedFilename = "gemma-4-E4B-it.litertlm"
        val visionAsset = createModelAsset(modelId = 1L, sha256 = "shared-sha", localFileName = sharedFilename).copy(
            configurations = listOf(
                createModelAsset(modelId = 1L, sha256 = "shared-sha", localFileName = sharedFilename)
                    .configurations.first()
                    .copy(displayName = "Gemma 4 E4B (Vision)")
            )
        )
        val fastAsset = createModelAsset(modelId = 2L, sha256 = "shared-sha", localFileName = sharedFilename).copy(
            configurations = listOf(
                createModelAsset(modelId = 2L, sha256 = "shared-sha", localFileName = sharedFilename)
                    .configurations.first()
                    .copy(displayName = "Gemma 4 E4B (Fast)")
            )
        )
        val thinkingAsset = createModelAsset(modelId = 3L, sha256 = "shared-sha", localFileName = sharedFilename).copy(
            configurations = listOf(
                createModelAsset(modelId = 3L, sha256 = "shared-sha", localFileName = sharedFilename)
                    .configurations.first()
                    .copy(displayName = "Gemma 4 E4B (Thinking)", thinkingEnabled = true)
            )
        )

        val result = scanner.scanAndCreateDirIfNotExist(
            downloadedModels = emptyMap(),
            expectedModels = mapOf(
                ModelType.VISION to visionAsset,
                ModelType.FAST to fastAsset,
                ModelType.THINKING to thinkingAsset
            )
        )

        assertEquals(3, result.missingModels.size)
        assertEquals(
            setOf("Gemma 4 E4B (Vision)", "Gemma 4 E4B (Fast)", "Gemma 4 E4B (Thinking)"),
            result.missingModels.map { it.configurations.first().displayName }.toSet()
        )
    }
}
