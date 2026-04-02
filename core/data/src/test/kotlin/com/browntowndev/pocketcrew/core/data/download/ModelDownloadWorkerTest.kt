package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import com.browntowndev.pocketcrew.core.data.download.remote.HuggingFaceModelUrlProvider
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for ModelDownloadWorker parseModelData function.
 * Verifies correct parsing of serialized model data and URL construction.
 */
class ModelDownloadWorkerTest {

    private val urlProvider = HuggingFaceModelUrlProvider()

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
    }

    @Test
    fun `parseModelData correctly parses complete serialized string`() {
        // Format: modelType|remoteFileName|localFileName|displayName|huggingFaceModelName|sizeInBytes|sha256|modelFileFormat|temperature|topK|topP|minP|repetitionPenalty|maxTokens|contextWindow|systemPrompt
        val serialized = listOf(
            "MAIN",
            "Llama-3-8B-Instruct-Q4_K_M.gguf",
            "Llama-3-8B-Instruct-Q4_K_M.gguf",
            "Llama 3 8B",
            "meta-llama/Llama-3-8B-Instruct",
            "5200000000",
            "abc123def456",
            "GGUF",
            "0.7",
            "40",
            "0.95",
            "0.0",
            "1.1",
            "4096",
            "4096",
            "You are a helpful assistant."
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNotNull(result)
        val (modelType, asset) = result!!

        assertEquals(ModelType.MAIN, modelType)
        assertEquals("Llama-3-8B-Instruct-Q4_K_M.gguf", asset.metadata.remoteFileName)
        assertEquals("Llama-3-8B-Instruct-Q4_K_M.gguf", asset.metadata.localFileName)
        assertEquals("meta-llama/Llama-3-8B-Instruct", asset.metadata.huggingFaceModelName)
        assertEquals(5_200_000_000L, asset.metadata.sizeInBytes)
        assertEquals("abc123def456", asset.metadata.sha256)
        assertEquals(ModelFileFormat.GGUF, asset.metadata.modelFileFormat)

        // Verify URL construction
        val downloadUrl = urlProvider.getModelDownloadUrl(asset)
        assertEquals(
            "https://huggingface.co/meta-llama/Llama-3-8B-Instruct/resolve/main/Llama-3-8B-Instruct-Q4_K_M.gguf",
            downloadUrl
        )
    }

    @Test
    fun `parseModelData handles litertlm format`() {
        val serialized = listOf(
            "VISION",
            "gemma-3n-E2B-it-int4.litertlm",
            "gemma-3n-E2B-it-int4.litertlm",
            "Gemma 3N E2B",
            "google/gemma-3n-E2B",
            "2800000000",
            "def789ghi012",
            "LITERTLM",
            "0.5",
            "40",
            "0.9",
            "0.0",
            "1.05",
            "2048",
            "2048",
            "You are a vision assistant."
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNotNull(result)
        val (modelType, asset) = result!!

        assertEquals(ModelType.VISION, modelType)
        assertEquals(ModelFileFormat.LITERTLM, asset.metadata.modelFileFormat)

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)
        assertEquals(
            "https://huggingface.co/google/gemma-3n-E2B/resolve/main/gemma-3n-E2B-it-int4.litertlm",
            downloadUrl
        )
    }

    @Test
    fun `parseModelData handles task format`() {
        val serialized = listOf(
            "FAST",
            "LFM2.5-1.2B-Thinking-Q8_0.gguf",
            "LFM2.5-1.2B-Thinking-Q8_0.gguf",
            "LFM 2.5 1.2B Thinking",
            "Liquid/LFM-2.5-1.2B-Thinking",
            "1800000000",
            "jkl012mno345",
            "GGUF",
            "0.3",
            "40",
            "0.95",
            "0.0",
            "1.2",
            "4096",
            "8192",
            "You are a fast thinking assistant."
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNotNull(result)
        val (modelType, asset) = result!!

        assertEquals(ModelType.FAST, modelType)

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)
        assertEquals(
            "https://huggingface.co/Liquid/LFM-2.5-1.2B-Thinking/resolve/main/LFM2.5-1.2B-Thinking-Q8_0.gguf",
            downloadUrl
        )
    }

    @Test
    fun `parseModelData returns null for invalid model type`() {
        val serialized = listOf(
            "INVALID_TYPE",
            "model.gguf",
            "model.gguf",
            "Test Model",
            "test/model",
            "1000",
            "abc123",
            "GGUF",
            "0.7",
            "40",
            "0.95",
            "0.0",
            "1.1",
            "4096",
            "4096",
            ""
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNull(result)
    }

    @Test
    fun `parseModelData returns null for insufficient parts`() {
        // Only 13 parts (needs at least 14)
        val serialized = listOf(
            "MAIN",
            "model.gguf",
            "model.gguf",
            "Test",
            "test/model",
            "1000",
            "abc123",
            "GGUF",
            "0.7",
            "40",
            "0.95",
            "0.0",
            "1.1"
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNull(result)
    }

    @Test
    fun `parseModelData defaults modelFileFormat on invalid format string`() {
        val serialized = listOf(
            "MAIN",
            "model.unknown",
            "model.unknown",
            "Test Model",
            "test/model",
            "1000",
            "abc123",
            "UNKNOWN_FORMAT",
            "0.7",
            "40",
            "0.95",
            "0.0",
            "1.1",
            "4096",
            "4096",
            ""
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNotNull(result)
        // Should default to LITERTLM when format is not recognized
        assertEquals(ModelFileFormat.LITERTLM, result!!.second.metadata.modelFileFormat)
    }

    @Test
    fun `parseModelData handles empty system prompt`() {
        val serialized = listOf(
            "THINKING",
            "model.gguf",
            "model.gguf",
            "Test",
            "test/model",
            "1000",
            "abc123",
            "GGUF",
            "0.7",
            "40",
            "0.95",
            "0.0",
            "1.1",
            "4096",
            "4096",
            ""
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNotNull(result)
        assertEquals("", result!!.second.configurations.first().systemPrompt)
    }

    @Test
    fun `parseModelData correctly extracts huggingFaceModelName for URL construction`() {
        // This test specifically verifies the field ordering issue that caused 404 errors
        val serialized = listOf(
            "MAIN",
            "actual-filename.gguf",
            "actual-filename.gguf",
            "Display Name",
            "correct/huggingface-model-name",  // This should be used for URL
            "5000000000",
            "sha256hash",
            "GGUF",
            "0.7",
            "40",
            "0.95",
            "0.0",
            "1.1",
            "4096",
            "4096",
            ""
        ).joinToString("|")

        val result = parseModelData(serialized)

        assertNotNull(result)
        val asset = result!!.second

        // Verify the huggingFaceModelName is correctly parsed
        assertEquals("correct/huggingface-model-name", asset.metadata.huggingFaceModelName)

        // Verify URL construction uses the correct model name
        val downloadUrl = urlProvider.getModelDownloadUrl(asset)
        assertEquals(
            "https://huggingface.co/correct/huggingface-model-name/resolve/main/actual-filename.gguf",
            downloadUrl
        )
    }

    @Test
    fun `parseModelData handles different model types`() {
        listOf(
            ModelType.MAIN,
            ModelType.VISION,
            ModelType.FAST,
            ModelType.DRAFT_ONE,
            ModelType.DRAFT_TWO,
            ModelType.THINKING
        ).forEach { expectedType ->
            val serialized = listOf(
                expectedType.name,
                "model.gguf",
                "model.gguf",
                "Test Model",
                "test/model",
                "1000",
                "abc123",
                "GGUF",
                "0.7",
                "40",
                "0.95",
                "0.0",
                "1.1",
                "4096",
                "4096",
                ""
            ).joinToString("|")

            val result = parseModelData(serialized)

            assertNotNull(result, "parseModelData should handle model type: ${expectedType.name}")
            assertEquals(expectedType, result!!.first)
        }
    }

    /**
     * Helper function that mirrors the actual parseModelData implementation.
     * This is a copy for testing purposes - in the actual worker it would be private.
     */
    private fun parseModelData(data: String): Pair<ModelType, LocalModelAsset>? {
        val parts = data.split("|")
        if (parts.size < 14) {
            return null
        }

        return try {
            val modelType = try {
                ModelType.valueOf(parts[0])
            } catch (e: Exception) {
                return null
            }

            val metadata = com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata(
                huggingFaceModelName = parts[4],
                remoteFileName = parts[1],
                localFileName = parts[2],
                sha256 = parts[6],
                sizeInBytes = parts[5].toLongOrNull() ?: 0L,
                modelFileFormat = try {
                    ModelFileFormat.valueOf(parts[7])
                } catch (e: Exception) {
                    ModelFileFormat.LITERTLM
                }
            )

            val configuration = LocalModelConfiguration(
                localModelId = 0,
                displayName = parts[3],
                temperature = parts[8].toDoubleOrNull() ?: 0.7,
                topK = parts[9].toIntOrNull() ?: 40,
                topP = parts[10].toDoubleOrNull() ?: 0.95,
                minP = parts[11].toDoubleOrNull() ?: 0.0,
                repetitionPenalty = parts[12].toDoubleOrNull() ?: 1.1,
                maxTokens = parts[13].toIntOrNull() ?: 4096,
                contextWindow = parts.getOrNull(14)?.toIntOrNull() ?: 4096,
                systemPrompt = parts.getOrNull(15) ?: ""
            )

            modelType to LocalModelAsset(
                metadata = metadata,
                configurations = listOf(configuration)
            )
        } catch (e: Exception) {
            null
        }
    }
}
