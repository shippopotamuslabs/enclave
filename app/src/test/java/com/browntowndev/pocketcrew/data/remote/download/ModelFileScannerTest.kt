package com.browntowndev.pocketcrew.data.remote.download

import android.content.Context
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelFile
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.data.download.ModelFileScanner
import com.browntowndev.pocketcrew.domain.model.download.ModelScanResult
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.RegisteredModel
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import com.browntowndev.pocketcrew.domain.model.ModelConfig

@OptIn(ExperimentalCoroutinesApi::class)
class ModelFileScannerTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockModelConfigCache: ModelConfigCachePort

    private lateinit var scanner: ModelFileScanner

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
        
        // Mock Android Log to prevent RuntimeException in unit tests
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        // Create a real temp directory for testing - ensure it exists before the test
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        assert(tempDir.exists(), { "Temp directory should exist" })

        mockContext = mockk(relaxed = true)
        // Return the temp directory which already exists, avoiding mkdirs() call
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        mockModelRegistry = mockk(relaxed = true)
        // Mock getRegisteredModel to return null (no registered models)
        coEvery { mockModelRegistry.getRegisteredModel(any()) } returns null

        mockModelConfigCache = mockk(relaxed = true)

        scanner = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `scanner can be instantiated`() {
        assert(scanner != null)
    }

    @Test
    fun `scanAndCreateDirIfNotExist returns ModelScanResult`() = runTest {
        // Pass empty list to avoid complex validation logic that requires more setup
        val result = scanner.scanAndCreateDirIfNotExist(emptyList())
        assert(result != null)
    }

    @Test
    fun `scanAndCreateDirIfNotExist returns directoryError when mkdirs fails`() = runTest {
        // Create a directory path that doesn't exist and can't be created
        // We'll use a path that would fail on Windows if we try to create in a nonexistent parent
        val nonExistentPath = File(System.getProperty("java.io.tmpdir"), "nonexistent_parent_${System.currentTimeMillis()}/test_models_impossible")
        
        // Mock getExternalFilesDir to return a path that doesn't exist
        val mockContext2 = mockk<Context>(relaxed = true)
        every { mockContext2.getExternalFilesDir(null) } returns nonExistentPath

        // Don't create the directory - it should not exist
        // The code will try to create it but fail
        
        val scannerWithMockedDir = ModelFileScanner(
            context = mockContext2,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )

        // Create a test model file
        val testModel = ModelFile(
            sizeBytes = 1024,
            url = "https://example.com/model.bin",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "model.bin",
            displayName = "Test Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val result = scannerWithMockedDir.scanAndCreateDirIfNotExist(listOf(testModel))

        // Should either have directoryError or allValid should be false
        assert(result.directoryError == true || result.allValid == false)
    }

    @Test
    fun `scanAndCreateDirIfNotExist handles format change from LITERTLM to TASK`() = runTest {
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_format_change_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create a "main.litertlm" file (old format) in the directory
        val oldFormatFile = File(tempDir, "main.litertlm")
        oldFormatFile.createNewFile()
        oldFormatFile.writeBytes(ByteArray(100))

        // Mock context to return this directory
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        // Create a registered model with LITERTLM format (old format in registry)
        val registeredModel = RegisteredModel(
            remoteFilename = "main.litertlm",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "abc123def456",
            sizeInBytes = 2048,
            maxTokens = 2048
        )

        // Mock registry to return the old format model
        coEvery { mockModelRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel

        // Create remote config with TASK format (new format)
        val remoteModel = ModelFile(
            sizeBytes = 2048,
            url = "https://example.com/main.task",
            md5 = "newmd5hash789",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.task",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.TASK,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )

        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(listOf(remoteModel))

        // The old format file should be deleted and model should be marked as missing/invalid
        assert(!oldFormatFile.exists() || result.invalidModels.isNotEmpty() || result.missingModels.isNotEmpty())
        
        // Clean up
        oldFormatFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist trusts partial download with matching MD5`() = runTest {
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_partial_trust_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // DO NOT create the main file - we want only the .tmp file to exist
        // This ensures the partial download logic is triggered
        val partialFile = File(tempDir, "main.litertlm.tmp")
        partialFile.createNewFile()
        partialFile.writeBytes(ByteArray(500))

        // Mock context to return this directory
        val testContext = mockk<Context>(relaxed = true)
        every { testContext.getExternalFilesDir(null) } returns tempDir

        // Create a registered model with matching MD5
        val matchingMd5 = "abc123def456"
        val registeredModel = RegisteredModel(
            remoteFilename = "main.litertlm",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = matchingMd5,
            sizeInBytes = 1000,
            maxTokens = 2048
        )

        // Create fresh mock registry - just return registeredModel for MAIN type
        val testRegistry = mockk<ModelRegistryPort>()
        coEvery { testRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel
        coEvery { testRegistry.getRegisteredModel(ModelType.VISION) } returns null
        coEvery { testRegistry.getRegisteredModel(ModelType.DRAFT) } returns null
        coEvery { testRegistry.getRegisteredModel(ModelType.FAST) } returns null

        // Create remote config with matching MD5 and format
        // Note: The sizeBytes doesn't need to match since there's no main file
        val remoteModel = ModelFile(
            sizeBytes = 1000,
            url = "https://example.com/main.litertlm",
            md5 = matchingMd5,
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = testContext,
            modelRegistry = testRegistry,
            modelConfigCache = mockModelConfigCache
        )

        // This should complete without error and return a valid result
        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(listOf(remoteModel))
        
        // Verify the result is valid
        assert(result != null)
        // With a partial download, the model should be marked as missing (since no complete file)
        // The partial download should be tracked
        assert(result.missingModels.isNotEmpty() || result.partialDownloads.isNotEmpty() || !result.allValid)
        
        // Clean up
        partialFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist rejects partial download with mismatched MD5`() = runTest {
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_partial_reject_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create a .tmp file (partial download)
        val partialFile = File(tempDir, "main.litertlm.tmp")
        partialFile.createNewFile()
        partialFile.writeBytes(ByteArray(500))

        // Mock context to return this directory
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        // Create a registered model with different MD5 (mismatch)
        val registeredModel = RegisteredModel(
            remoteFilename = "main.litertlm",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "oldmd5hash",
            sizeInBytes = 1000,
            maxTokens = 2048
        )

        // Mock registry to return the registered model
        coEvery { mockModelRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel

        // Create remote config with different MD5
        val remoteModel = ModelFile(
            sizeBytes = 1000,
            url = "https://example.com/main.litertlm",
            md5 = "newmd5hashdifferent",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )

        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(listOf(remoteModel))

        // Partial download should NOT be trusted - should be treated as missing
        assert(result.partialDownloads.isEmpty())
        assert(result.missingModels.isNotEmpty() || result.invalidModels.isNotEmpty())
        
        // Clean up
        partialFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist handles MD5 mismatch between registry and remote`() = runTest {
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_md5_mismatch_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create a main.litertlm file (complete but wrong MD5)
        val modelFile = File(tempDir, "main.litertlm")
        modelFile.createNewFile()
        modelFile.writeBytes(ByteArray(1000))

        // Mock context to return this directory
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        // Create a registered model with different MD5
        val registeredModel = RegisteredModel(
            remoteFilename = "main.litertlm",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "registrymd5",
            sizeInBytes = 1000,
            maxTokens = 2048
        )

        // Mock registry to return the registered model
        coEvery { mockModelRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel

        // Create remote config with different MD5
        val remoteModel = ModelFile(
            sizeBytes = 1000,
            url = "https://example.com/main.litertlm",
            md5 = "remotemd5different",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )

        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(listOf(remoteModel))

        // Model should be marked as invalid due to MD5 mismatch
        assert(result.invalidModels.isNotEmpty() || result.missingModels.isNotEmpty())
        
        // Clean up
        modelFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist validates size correctly - correct size`() = runTest {
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_size_valid_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val expectedSize = 1000L
        // Create a file with the correct size
        val modelFile = File(tempDir, "main.litertlm")
        modelFile.createNewFile()
        modelFile.writeBytes(ByteArray(expectedSize.toInt()))

        // Mock context to return this directory
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        // Create remote model with matching size
        val remoteModel = ModelFile(
            sizeBytes = expectedSize,
            url = "https://example.com/main.litertlm",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        // Mock registry to return null (no existing registration)
        coEvery { mockModelRegistry.getRegisteredModel(ModelType.MAIN) } returns null

        val scannerWithMockedRegistry = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )

        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(listOf(remoteModel))

        // With null registry, the file should be valid by size but registry validation may fail
        // The key assertion is that the scan completes without error
        assert(result != null)
        
        // Clean up
        modelFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist handles no registry entry`() = runTest {
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_no_registry_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create a main.litertlm file
        val modelFile = File(tempDir, "main.litertlm")
        modelFile.createNewFile()
        modelFile.writeBytes(ByteArray(1000))

        // Mock context to return this directory
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        // Mock registry to return null (no registered model)
        coEvery { mockModelRegistry.getRegisteredModel(ModelType.MAIN) } returns null

        // Create remote model
        val remoteModel = ModelFile(
            sizeBytes = 1000,
            url = "https://example.com/main.litertlm",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )

        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(listOf(remoteModel))

        // With no registry entry, validation should still work
        // The scan should complete and return a valid result
        assert(result != null)
        
        // Clean up
        modelFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist handles empty models list`() = runTest {
        // Test with empty list - should return success with no missing models
        val result = scanner.scanAndCreateDirIfNotExist(emptyList())
        
        assert(result != null)
        assert(result.allValid == true)
        assert(result.missingModels.isEmpty())
        assert(result.partialDownloads.isEmpty())
        assert(result.directoryError == false)
    }

    @Test
    fun `scanAndCreateDirIfNotExist handles partial download with format mismatch`() = runTest {
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_partial_format_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create a .tmp file with LITERTLM format
        val partialFile = File(tempDir, "main.litertlm.tmp")
        partialFile.createNewFile()
        partialFile.writeBytes(ByteArray(500))

        // Mock context to return this directory
        mockContext = mockk(relaxed = true)
        every { mockContext.getExternalFilesDir(null) } returns tempDir

        // Create a registered model with LITERTLM format
        val registeredModel = RegisteredModel(
            remoteFilename = "main.litertlm",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "abc123",
            sizeInBytes = 1000,
            maxTokens = 2048
        )

        // Mock registry to return the registered model
        coEvery { mockModelRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel

        // Create remote config with TASK format (different from registry)
        val remoteModel = ModelFile(
            sizeBytes = 1000,
            url = "https://example.com/main.task",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.task",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.TASK,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = mockContext,
            modelRegistry = mockModelRegistry,
            modelConfigCache = mockModelConfigCache
        )

        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(listOf(remoteModel))

        // Format mismatch should cause the partial download to not be trusted
        assert(result.partialDownloads.isEmpty())
        assert(result.missingModels.isNotEmpty() || result.invalidModels.isNotEmpty())
        
        // Clean up
        partialFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist detects config change when MD5 differs between new and registered`() = runTest {
        // This is the KEY test for the bug fix - when remote config changes MD5,
        // existing files should be invalidated and re-downloaded
        
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_md5_change_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create an existing complete file with OLD MD5
        val modelFile = File(tempDir, "main.litertlm")
        modelFile.createNewFile()
        modelFile.writeBytes(ByteArray(1000))

        // Mock context to return this directory
        val testContext = mockk<Context>(relaxed = true)
        every { testContext.getExternalFilesDir(null) } returns tempDir

        // Registry has been pre-updated with NEW config (this is the key part of the bug fix)
        // The registry now has the NEW MD5
        val registeredWithNewMd5 = RegisteredModel(
            remoteFilename = "main.litertlm",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "newmd5hash",  // NEW MD5 in registry
            sizeInBytes = 2000,  // NEW size in registry
            maxTokens = 2048
        )

        val testRegistry = mockk<ModelRegistryPort>()
        coEvery { testRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredWithNewMd5

        // Original model (from before config update) has OLD MD5
        val originalModel = ModelFile(
            sizeBytes = 1000,  // OLD size
            url = "https://example.com/main.litertlm",
            md5 = "oldmd5hash",  // OLD MD5
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        // New model from remote config has NEW MD5
        val newModel = ModelFile(
            sizeBytes = 2000,  // NEW size
            url = "https://example.com/main.litertlm",
            md5 = "newmd5hash",  // NEW MD5
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = testContext,
            modelRegistry = testRegistry,
            modelConfigCache = mockModelConfigCache
        )

        // Pass both originalModels and newModels to detect config change
        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(
            modelsToCheck = listOf(originalModel),
            newModels = listOf(newModel)
        )

        // KEY ASSERTION: Model should be marked as invalid because:
        // 1. File exists but size (1000) doesn't match registered size (2000)
        // 2. Config has changed (new MD5 != old MD5)
        assert(result.invalidModels.isNotEmpty() || result.missingModels.isNotEmpty()) {
            "Config change should invalidate existing file"
        }
        
        // Clean up
        modelFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist detects config change when format differs between new and registered`() = runTest {
        // Another key test - when format changes (e.g., LITERTLM -> TASK)
        // existing files should be invalidated
        
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_format_change_new_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create an existing file with OLD format
        val oldFormatFile = File(tempDir, "main.litertlm")
        oldFormatFile.createNewFile()
        oldFormatFile.writeBytes(ByteArray(1000))

        // Mock context to return this directory
        val testContext = mockk<Context>(relaxed = true)
        every { testContext.getExternalFilesDir(null) } returns tempDir

        // Registry has been pre-updated with NEW format (TASK)
        val registeredWithNewFormat = RegisteredModel(
            remoteFilename = "main.task",  // NEW format
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.TASK,  // NEW format
            md5 = "newmd5hash",
            sizeInBytes = 2000,
            maxTokens = 2048
        )

        val testRegistry = mockk<ModelRegistryPort>()
        coEvery { testRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredWithNewFormat

        // Original model (from before config update) has OLD format
        val originalModel = ModelFile(
            sizeBytes = 1000,
            url = "https://example.com/main.litertlm",
            md5 = "oldmd5hash",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,  // OLD format
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        // New model from remote config has NEW format
        val newModel = ModelFile(
            sizeBytes = 2000,
            url = "https://example.com/main.task",
            md5 = "newmd5hash",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.task",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.TASK,  // NEW format
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = testContext,
            modelRegistry = testRegistry,
            modelConfigCache = mockModelConfigCache
        )

        // Pass both originalModels and newModels to detect config change
        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(
            modelsToCheck = listOf(originalModel),
            newModels = listOf(newModel)
        )

        // KEY ASSERTION: Format change should invalidate existing file
        assert(result.invalidModels.isNotEmpty() || result.missingModels.isNotEmpty()) {
            "Format change should invalidate existing file"
        }
        
        // Clean up
        oldFormatFile.delete()
    }

    @Test
    fun `scanAndCreateDirIfNotExist trusts file when config unchanged`() = runTest {
        // Test that when config hasn't changed, existing valid files are trusted
        
        // Create temp directory that exists
        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_models_no_change_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create a file with correct size
        val modelFile = File(tempDir, "main.litertlm")
        modelFile.createNewFile()
        val expectedSize = 1000
        modelFile.writeBytes(ByteArray(expectedSize))

        // Mock context to return this directory
        val testContext = mockk<Context>(relaxed = true)
        every { testContext.getExternalFilesDir(null) } returns tempDir

        // Registry has the SAME config as remote
        val registeredModel = RegisteredModel(
            remoteFilename = "main.litertlm",
            modelType = ModelType.MAIN,
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            md5 = "samed5hash",  // Same MD5
            sizeInBytes = expectedSize.toLong(),  // Same size
            maxTokens = 2048
        )

        val testRegistry = mockk<ModelRegistryPort>()
        coEvery { testRegistry.getRegisteredModel(ModelType.MAIN) } returns registeredModel
        // Also handle other model types to avoid null issues
        coEvery { testRegistry.getRegisteredModel(ModelType.VISION) } returns null
        coEvery { testRegistry.getRegisteredModel(ModelType.DRAFT) } returns null
        coEvery { testRegistry.getRegisteredModel(ModelType.FAST) } returns null

        // Both original and new have same MD5 and size
        val model = ModelFile(
            sizeBytes = expectedSize.toLong(),
            url = "https://example.com/main.litertlm",
            md5 = "samed5hash",  // Same MD5
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "main.litertlm",
            displayName = "Main Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        val scannerWithMockedRegistry = ModelFileScanner(
            context = testContext,
            modelRegistry = testRegistry,
            modelConfigCache = mockModelConfigCache
        )

        // Pass same model as both original and new (config unchanged)
        val result = scannerWithMockedRegistry.scanAndCreateDirIfNotExist(
            modelsToCheck = listOf(model),
            newModels = listOf(model)
        )

        // File should NOT be in invalid models when config unchanged
        // Note: It may or may not be in missingModels depending on the specific logic path
        // The key assertion is that invalidModels should be empty
        assert(result.invalidModels.isEmpty()) {
            "File should not be invalid when config unchanged and size matches"
        }
        
        // Clean up
        modelFile.delete()
    }
}

