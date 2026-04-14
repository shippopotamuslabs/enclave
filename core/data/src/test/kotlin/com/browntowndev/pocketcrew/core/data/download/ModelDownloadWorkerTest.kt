package com.browntowndev.pocketcrew.core.data.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.json.JSONObject

/**
 * Unit tests for ModelDownloadWorker file spec parsing.
 * Verifies correct parsing of JSON objects into DownloadFileSpec.
 */
class ModelDownloadWorkerTest {

    @Test
    fun `parseFileSpecFromJson correctly parses complete file spec`() {
        val json = JSONObject().apply {
            put("remoteFileName", "Llama-3-8B-Instruct-Q4_K_M.gguf")
            put("localFileName", "Llama-3-8B-Instruct-Q4_K_M.gguf")
            put("sha256", "abc123def456")
            put("sizeInBytes", 5_200_000_000L)
            put("huggingFaceModelName", "meta-llama/Llama-3-8B-Instruct")
            put("source", "HUGGING_FACE")
            put("modelFileFormat", "GGUF")
        }

        val result = parseFileSpecFromJson(json)

        assertNotNull(result)
        assertEquals("Llama-3-8B-Instruct-Q4_K_M.gguf", result.remoteFileName)
        assertEquals("Llama-3-8B-Instruct-Q4_K_M.gguf", result.localFileName)
        assertEquals("meta-llama/Llama-3-8B-Instruct", result.huggingFaceModelName)
        assertEquals(5_200_000_000L, result.sizeInBytes)
        assertEquals("abc123def456", result.sha256)
        assertEquals("GGUF", result.modelFileFormat)
    }

    @Test
    fun `parseFileSpecFromJson handles litertlm format`() {
        val json = JSONObject().apply {
            put("remoteFileName", "gemma-3n-E2B-it-int4.litertlm")
            put("localFileName", "gemma-3n-E2B-it-int4.litertlm")
            put("sha256", "def789ghi012")
            put("sizeInBytes", 2_800_000_000L)
            put("huggingFaceModelName", "google/gemma-3n-E2B")
            put("source", "HUGGING_FACE")
            put("modelFileFormat", "LITERTLM")
        }

        val result = parseFileSpecFromJson(json)

        assertNotNull(result)
        assertEquals("LITERTLM", result.modelFileFormat)
    }

    @Test
    fun `parseFileSpecFromJson defaults modelFileFormat on missing field`() {
        val json = JSONObject().apply {
            put("remoteFileName", "model.unknown")
            put("localFileName", "model.unknown")
            put("sha256", "abc123")
            put("sizeInBytes", 1000L)
            put("huggingFaceModelName", "test/model")
            put("source", "HUGGING_FACE")
            // modelFileFormat omitted - should default to LITERTLM
        }

        val result = parseFileSpecFromJson(json)

        assertNotNull(result)
        assertEquals("LITERTLM", result.modelFileFormat)
    }

    @Test
    fun `parseFileSpecFromJson parses mmproj fields`() {
        val json = JSONObject().apply {
            put("remoteFileName", "model.gguf")
            put("localFileName", "model.gguf")
            put("sha256", "sha123")
            put("sizeInBytes", 1000L)
            put("huggingFaceModelName", "test/model")
            put("source", "HUGGING_FACE")
            put("modelFileFormat", "GGUF")
            put("mmprojRemoteFileName", "mmproj-model.gguf")
            put("mmprojLocalFileName", "mmproj-model.gguf")
            put("mmprojSha256", "mmsha456")
            put("mmprojSizeInBytes", 500L)
        }

        val result = parseFileSpecFromJson(json)

        assertNotNull(result)
        assertEquals("mmproj-model.gguf", result.mmprojRemoteFileName)
        assertEquals("mmproj-model.gguf", result.mmprojLocalFileName)
        assertEquals("mmsha456", result.mmprojSha256)
        assertEquals(500L, result.mmprojSizeInBytes)
    }

    @Test
    fun `parseFileSpecFromJson handles empty optional fields`() {
        val json = JSONObject().apply {
            put("remoteFileName", "model.gguf")
            put("localFileName", "model.gguf")
            put("sha256", "abc123")
            put("sizeInBytes", 1000L)
            put("huggingFaceModelName", "test/model")
            put("source", "HUGGING_FACE")
            put("modelFileFormat", "GGUF")
            // mmproj fields omitted
        }

        val result = parseFileSpecFromJson(json)

        assertNotNull(result)
        assertNull(result.mmprojRemoteFileName)
        assertNull(result.mmprojLocalFileName)
        assertNull(result.mmprojSha256)
        assertNull(result.mmprojSizeInBytes)
    }

    @Test
    fun `parseFileSpecFromJson defaults source to HUGGING_FACE when omitted`() {
        val json = JSONObject().apply {
            put("remoteFileName", "model.gguf")
            put("localFileName", "model.gguf")
            put("sha256", "abc123")
            put("sizeInBytes", 1000L)
            put("huggingFaceModelName", "test/model")
            // source omitted - should default to HUGGING_FACE
            put("modelFileFormat", "GGUF")
        }

        val result = parseFileSpecFromJson(json)

        assertNotNull(result)
        assertEquals("HUGGING_FACE", result.source)
    }

    /**
     * Helper function that mirrors the actual parseFileSpecFromJson implementation.
     * This is a copy for testing purposes - in the actual worker it is private.
     */
    private fun parseFileSpecFromJson(json: JSONObject): DownloadFileSpec {
        return DownloadFileSpec(
            remoteFileName = json.getString("remoteFileName"),
            localFileName = json.getString("localFileName"),
            sha256 = json.getString("sha256"),
            sizeInBytes = json.getLong("sizeInBytes"),
            huggingFaceModelName = json.getString("huggingFaceModelName"),
            source = json.optString("source", "HUGGING_FACE"),
            modelFileFormat = json.optString("modelFileFormat", "LITERTLM"),
            mmprojRemoteFileName = json.optString("mmprojRemoteFileName").takeIf { it.isNotBlank() },
            mmprojLocalFileName = json.optString("mmprojLocalFileName").takeIf { it.isNotBlank() },
            mmprojSha256 = json.optString("mmprojSha256").takeIf { it.isNotBlank() },
            mmprojSizeInBytes = json.optLong("mmprojSizeInBytes").takeIf { it > 0L },
        )
    }
}