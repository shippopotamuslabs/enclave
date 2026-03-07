package com.browntowndev.pocketcrew.domain.service

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.model.WorkParserModelFile
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.port.download.HashingPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileIntegrityValidatorTest {

    private lateinit var mockModelConfigProvider: ModelConfigProvider
    private lateinit var mockModelConfigCache: ModelConfigCachePort
    private lateinit var mockHashingPort: HashingPort
    private lateinit var mockLogger: LoggingPort
    private lateinit var validator: FileIntegrityValidator

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        mockModelConfigProvider = mockk(relaxed = true)
        mockModelConfigCache = mockk(relaxed = true)
        mockHashingPort = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)

        validator = FileIntegrityValidator(
            modelConfigProvider = mockModelConfigProvider,
            modelConfigCache = mockModelConfigCache,
            hashingPort = mockHashingPort,
            logger = mockLogger
        )
    }

    @Test
    fun verifyModelsExist_fails_whenModelsDirectoryMissing() {
        val nonExistentDir = File(tempDir, "nonexistent")
        every { mockModelConfigProvider.modelsDirectory } returns nonExistentDir

        val result = runBlocking { validator.verifyModelsExist() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Models directory does not exist") == true)
    }

    @Test
    fun verifyModelsExist_fails_whenFileMissing() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir

        val result = runBlocking { validator.verifyModelsExist() }

        assertTrue(result.isFailure)
    }

    @Test
    fun verifyModelsExist_fails_whenFileEmpty() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        val visionFile = File(modelsDir, "vision.litertlm")
        visionFile.createNewFile() // Empty file
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir

        // Pass specific files to verify (avoids needing cache setup)
        val requiredFiles = listOf(
            WorkParserModelFile(
                sizeBytes = 1000L,
                modelTypes = listOf(ModelType.VISION),
                modelFileFormat = ModelFileFormat.LITERTLM,
                localFileName = "vision.litertlm",
                md5 = null
            )
        )

        val result = runBlocking { validator.verifyModelsExist(requiredFiles) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun verifyModelsExist_fails_whenMd5Mismatch() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        val visionFile = File(modelsDir, "vision.litertlm")
        visionFile.writeBytes(ByteArray(1000))
        val draftFile = File(modelsDir, "draft.litertlm")
        draftFile.writeBytes(ByteArray(2000))
        val mainFile = File(modelsDir, "main.litertlm")
        mainFile.writeBytes(ByteArray(3000))
        val fastFile = File(modelsDir, "fast.litertlm")
        fastFile.writeBytes(ByteArray(4000))
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir

        // Setup cache configs
        val visionConfig = ModelConfiguration(
            modelType = ModelType.VISION,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/vision",
                remoteFileName = "vision.litertlm",
                localFileName = "vision.litertlm",
                displayName = "Vision Model",
                md5 = "correctHash123456789012345678",
                sizeInBytes = 1000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a vision assistant.")
        )
        val draftConfig = ModelConfiguration(
            modelType = ModelType.DRAFT,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/draft",
                remoteFileName = "draft.litertlm",
                localFileName = "draft.litertlm",
                displayName = "Draft Model",
                md5 = "correctHash123456789012345678",
                sizeInBytes = 2000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a draft assistant.")
        )
        val mainConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/main",
                remoteFileName = "main.litertlm",
                localFileName = "main.litertlm",
                displayName = "Main Model",
                md5 = "correctHash123456789012345678",
                sizeInBytes = 3000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
        )
        val fastConfig = ModelConfiguration(
            modelType = ModelType.FAST,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/fast",
                remoteFileName = "fast.litertlm",
                localFileName = "fast.litertlm",
                displayName = "Fast Model",
                md5 = "correctHash123456789012345678",
                sizeInBytes = 4000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a fast assistant.")
        )

        every { mockModelConfigCache.isInitialized() } returns true
        every { mockModelConfigCache.getVisionConfig() } returns visionConfig
        every { mockModelConfigCache.getDraftConfig() } returns draftConfig
        every { mockModelConfigCache.getMainConfig() } returns mainConfig
        every { mockModelConfigCache.getFastConfig() } returns fastConfig
        every { mockHashingPort.calculateMd5(any()) } returns "wrongHash987654321098765432"

        val result = runBlocking { validator.verifyModelsExist() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("MD5 mismatch") == true)
    }

    @Test
    fun verifyModelsExist_succeeds_whenAllFilesValid() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        val visionFile = File(modelsDir, "vision.litertlm")
        visionFile.writeBytes(ByteArray(1000))
        val draftFile = File(modelsDir, "draft.litertlm")
        draftFile.writeBytes(ByteArray(2000))
        val mainFile = File(modelsDir, "main.litertlm")
        mainFile.writeBytes(ByteArray(3000))
        val fastFile = File(modelsDir, "fast.litertlm")
        fastFile.writeBytes(ByteArray(4000))
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir

        // Setup cache configs
        val visionConfig = ModelConfiguration(
            modelType = ModelType.VISION,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/vision",
                remoteFileName = "vision.litertlm",
                localFileName = "vision.litertlm",
                displayName = "Vision Model",
                md5 = "d41d8cd98f00b204e9800998ecf8427e",
                sizeInBytes = 1000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a vision assistant.")
        )
        val draftConfig = ModelConfiguration(
            modelType = ModelType.DRAFT,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/draft",
                remoteFileName = "draft.litertlm",
                localFileName = "draft.litertlm",
                displayName = "Draft Model",
                md5 = "d41d8cd98f00b204e9800998ecf8427e",
                sizeInBytes = 2000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a draft assistant.")
        )
        val mainConfig = ModelConfiguration(
            modelType = ModelType.MAIN,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/main",
                remoteFileName = "main.litertlm",
                localFileName = "main.litertlm",
                displayName = "Main Model",
                md5 = "d41d8cd98f00b204e9800998ecf8427e",
                sizeInBytes = 3000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a helpful assistant.")
        )
        val fastConfig = ModelConfiguration(
            modelType = ModelType.FAST,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "model/fast",
                remoteFileName = "fast.litertlm",
                localFileName = "fast.litertlm",
                displayName = "Fast Model",
                md5 = "d41d8cd98f00b204e9800998ecf8427e",
                sizeInBytes = 4000,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.0,
                topK = 40,
                topP = 0.95,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(systemPrompt = "You are a fast assistant.")
        )

        every { mockModelConfigCache.isInitialized() } returns true
        every { mockModelConfigCache.getVisionConfig() } returns visionConfig
        every { mockModelConfigCache.getDraftConfig() } returns draftConfig
        every { mockModelConfigCache.getMainConfig() } returns mainConfig
        every { mockModelConfigCache.getFastConfig() } returns fastConfig
        every { mockHashingPort.calculateMd5(any()) } returns "d41d8cd98f00b204e9800998ecf8427e"

        val result = runBlocking { validator.verifyModelsExist() }

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun verifyModelsExist_fails_whenCacheNotInitialized() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir
        every { mockModelConfigCache.isInitialized() } returns false

        val result = runBlocking { validator.verifyModelsExist() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not initialized") == true)
    }

    @Test
    fun verifyModelsExist_fails_whenMissingModelConfigs() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir
        every { mockModelConfigCache.isInitialized() } returns true
        every { mockModelConfigCache.getVisionConfig() } returns null
        every { mockModelConfigCache.getDraftConfig() } returns null
        every { mockModelConfigCache.getMainConfig() } returns null
        every { mockModelConfigCache.getFastConfig() } returns null

        val result = runBlocking { validator.verifyModelsExist() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("missing") == true)
    }

    @Test
    fun verifyModelsExist_succeeds_withSpecificFilesAndMd5() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        val visionFile = File(modelsDir, "vision.litertlm")
        visionFile.writeBytes(ByteArray(1000))
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir

        val requiredFiles = listOf(
            WorkParserModelFile(
                sizeBytes = 1000L,
                modelTypes = listOf(ModelType.VISION),
                modelFileFormat = ModelFileFormat.LITERTLM,
                localFileName = "vision.litertlm",
                md5 = "d41d8cd98f00b204e9800998ecf8427e"
            )
        )

        every { mockHashingPort.calculateMd5(any()) } returns "d41d8cd98f00b204e9800998ecf8427e"

        val result = runBlocking { validator.verifyModelsExist(requiredFiles) }

        assertTrue(result.isSuccess)
    }

    @Test
    fun verifyModelsExist_succeeds_withSpecificFilesNoMd5() {
        val modelsDir = File(tempDir, "models")
        modelsDir.mkdirs()
        val visionFile = File(modelsDir, "vision.litertlm")
        visionFile.writeBytes(ByteArray(1000))
        every { mockModelConfigProvider.modelsDirectory } returns modelsDir

        val requiredFiles = listOf(
            WorkParserModelFile(
                sizeBytes = 1000L,
                modelTypes = listOf(ModelType.VISION),
                modelFileFormat = ModelFileFormat.LITERTLM,
                localFileName = "vision.litertlm",
                md5 = null
            )
        )

        val result = runBlocking { validator.verifyModelsExist(requiredFiles) }

        assertTrue(result.isSuccess)
    }
}
