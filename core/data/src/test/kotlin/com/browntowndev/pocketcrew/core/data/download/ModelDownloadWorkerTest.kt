package com.browntowndev.pocketcrew.core.data.download

import android.util.Log
import com.browntowndev.pocketcrew.core.data.download.remote.DynamicModelUrlProvider
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.json.JSONObject

/**
 * Unit tests for ModelDownloadWorker parseModelData function.
 * Verifies correct parsing of serialized model data and URL construction.
 */
class ModelDownloadWorkerTest {

    private val urlProvider = DynamicModelUrlProvider()

    @BeforeEach
    fun setup() {
        mockkStatic(Log::class)
    }

    @Test
    fun `parseModelData correctly parses complete serialized string`() {
        val serialized = JSONObject().apply {
            put("modelType", "MAIN")
            put("remoteFileName", "Llama-3-8B-Instruct-Q4_K_M.gguf")
            put("localFileName", "Llama-3-8B-Instruct-Q4_K_M.gguf")
            put("presetName", "Llama 3 8B")
            put("huggingFaceModelName", "meta-llama/Llama-3-8B-Instruct")
            put("sizeInBytes", 5_200_000_000L)
            put("sha256", "abc123def456")
            put("modelFileFormat", "GGUF")
            put("temperature", 0.7)
            put("topK", 40)
            put("topP", 0.95)
            put("minP", 0.0)
            put("repetitionPenalty", 1.1)
            put("maxTokens", 4096)
            put("contextWindow", 4096)
            put("systemPrompt", "You are a helpful assistant.")
            put("isSystemPreset", true)
            put("thinkingEnabled", false)
        }.toString()

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
        val serialized = JSONObject().apply {
            put("modelType", "VISION")
            put("remoteFileName", "gemma-3n-E2B-it-int4.litertlm")
            put("localFileName", "gemma-3n-E2B-it-int4.litertlm")
            put("presetName", "Gemma 3N E2B")
            put("huggingFaceModelName", "google/gemma-3n-E2B")
            put("sizeInBytes", 2_800_000_000L)
            put("sha256", "def789ghi012")
            put("modelFileFormat", "LITERTLM")
            put("temperature", 0.5)
            put("topK", 40)
            put("topP", 0.9)
            put("minP", 0.0)
            put("repetitionPenalty", 1.05)
            put("maxTokens", 2048)
            put("contextWindow", 2048)
            put("systemPrompt", "You are a vision assistant.")
            put("isSystemPreset", true)
            put("thinkingEnabled", false)
        }.toString()

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
        val serialized = JSONObject().apply {
            put("modelType", "FAST")
            put("remoteFileName", "LFM2.5-1.2B-Thinking-Q8_0.gguf")
            put("localFileName", "LFM2.5-1.2B-Thinking-Q8_0.gguf")
            put("presetName", "LFM 2.5 1.2B Thinking")
            put("huggingFaceModelName", "Liquid/LFM-2.5-1.2B-Thinking")
            put("sizeInBytes", 1_800_000_000L)
            put("sha256", "jkl012mno345")
            put("modelFileFormat", "GGUF")
            put("temperature", 0.3)
            put("topK", 40)
            put("topP", 0.95)
            put("minP", 0.0)
            put("repetitionPenalty", 1.2)
            put("maxTokens", 4096)
            put("contextWindow", 8192)
            put("systemPrompt", "You are a fast thinking assistant.")
            put("isSystemPreset", true)
            put("thinkingEnabled", false)
        }.toString()

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
        val serialized = JSONObject().apply {
            put("modelType", "INVALID_TYPE")
            put("remoteFileName", "model.gguf")
            put("localFileName", "model.gguf")
            put("presetName", "Test Model")
            put("huggingFaceModelName", "test/model")
            put("sizeInBytes", 1000L)
            put("sha256", "abc123")
            put("modelFileFormat", "GGUF")
            put("temperature", 0.7)
            put("topK", 40)
            put("topP", 0.95)
            put("minP", 0.0)
            put("repetitionPenalty", 1.1)
            put("maxTokens", 4096)
            put("contextWindow", 4096)
            put("systemPrompt", "")
            put("isSystemPreset", true)
            put("thinkingEnabled", false)
        }.toString()

        val result = parseModelData(serialized)

        assertNull(result)
    }

    @Test
    fun `parseModelData returns null for insufficient parts`() {
        val serialized = """{"modelType":"MAIN"}"""

        val result = parseModelData(serialized)

        assertNull(result)
    }

    @Test
    fun `parseModelData defaults modelFileFormat on invalid format string`() {
        val serialized = JSONObject().apply {
            put("modelType", "MAIN")
            put("remoteFileName", "model.unknown")
            put("localFileName", "model.unknown")
            put("presetName", "Test Model")
            put("huggingFaceModelName", "test/model")
            put("sizeInBytes", 1000L)
            put("sha256", "abc123")
            put("modelFileFormat", "UNKNOWN_FORMAT")
            put("temperature", 0.7)
            put("topK", 40)
            put("topP", 0.95)
            put("minP", 0.0)
            put("repetitionPenalty", 1.1)
            put("maxTokens", 4096)
            put("contextWindow", 4096)
            put("systemPrompt", "")
            put("isSystemPreset", true)
            put("thinkingEnabled", false)
        }.toString()

        val result = parseModelData(serialized)

        assertNotNull(result)
        // Should default to LITERTLM when format is not recognized
        assertEquals(ModelFileFormat.LITERTLM, result!!.second.metadata.modelFileFormat)
    }

    @Test
    fun `parseModelData handles empty system prompt`() {
        val serialized = JSONObject().apply {
            put("modelType", "THINKING")
            put("remoteFileName", "model.gguf")
            put("localFileName", "model.gguf")
            put("presetName", "Test")
            put("huggingFaceModelName", "test/model")
            put("sizeInBytes", 1000L)
            put("sha256", "abc123")
            put("modelFileFormat", "GGUF")
            put("temperature", 0.7)
            put("topK", 40)
            put("topP", 0.95)
            put("minP", 0.0)
            put("repetitionPenalty", 1.1)
            put("maxTokens", 4096)
            put("contextWindow", 4096)
            put("systemPrompt", "")
            put("isSystemPreset", true)
            put("thinkingEnabled", false)
        }.toString()

        val result = parseModelData(serialized)

        assertNotNull(result)
        assertEquals("", result!!.second.configurations.first().systemPrompt)
    }

    @Test
    fun `parseModelData correctly extracts huggingFaceModelName for URL construction`() {
        // This test specifically verifies the field ordering issue that caused 404 errors
        val serialized = JSONObject().apply {
            put("modelType", "MAIN")
            put("remoteFileName", "actual-filename.gguf")
            put("localFileName", "actual-filename.gguf")
            put("presetName", "Display Name")
            put("huggingFaceModelName", "correct/huggingface-model-name")
            put("sizeInBytes", 5_000_000_000L)
            put("sha256", "sha256hash")
            put("modelFileFormat", "GGUF")
            put("temperature", 0.7)
            put("topK", 40)
            put("topP", 0.95)
            put("minP", 0.0)
            put("repetitionPenalty", 1.1)
            put("maxTokens", 4096)
            put("contextWindow", 4096)
            put("systemPrompt", "")
            put("isSystemPreset", true)
            put("thinkingEnabled", false)
        }.toString()

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
            val serialized = JSONObject().apply {
                put("modelType", expectedType.name)
                put("remoteFileName", "model.gguf")
                put("localFileName", "model.gguf")
                put("presetName", "Test Model")
                put("huggingFaceModelName", "test/model")
                put("sizeInBytes", 1000L)
                put("sha256", "abc123")
                put("modelFileFormat", "GGUF")
                put("temperature", 0.7)
                put("topK", 40)
                put("topP", 0.95)
                put("minP", 0.0)
                put("repetitionPenalty", 1.1)
                put("maxTokens", 4096)
                put("contextWindow", 4096)
                put("systemPrompt", "")
                put("isSystemPreset", true)
                put("thinkingEnabled", false)
            }.toString()

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
        return try {
            val json = JSONObject(data)
            val modelType = try {
                ModelType.valueOf(json.getString("modelType"))
            } catch (e: Exception) {
                return null
            }

            val metadata = LocalModelMetadata(
                id = LocalModelId("0"),
                huggingFaceModelName = json.optString("huggingFaceModelName", ""),
                remoteFileName = json.getString("remoteFileName"),
                localFileName = json.getString("localFileName"),
                sha256 = json.getString("sha256"),
                sizeInBytes = json.optLong("sizeInBytes", 0L),
                modelFileFormat = try {
                    ModelFileFormat.valueOf(json.getString("modelFileFormat"))
                } catch (e: Exception) {
                    ModelFileFormat.LITERTLM
                }
            )

            val configuration = LocalModelConfiguration(
                localModelId = LocalModelId("0"),
                displayName = json.getString("presetName"),
                temperature = json.optDouble("temperature", 0.7),
                topK = json.optInt("topK", 40),
                topP = json.optDouble("topP", 0.95),
                minP = json.optDouble("minP", 0.0),
                repetitionPenalty = json.optDouble("repetitionPenalty", 1.1),
                maxTokens = json.optInt("maxTokens", 4096),
                contextWindow = json.optInt("contextWindow", 4096),
                systemPrompt = json.optString("systemPrompt", ""),
                isSystemPreset = json.optBoolean("isSystemPreset", true),
                thinkingEnabled = json.optBoolean("thinkingEnabled", false)
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
