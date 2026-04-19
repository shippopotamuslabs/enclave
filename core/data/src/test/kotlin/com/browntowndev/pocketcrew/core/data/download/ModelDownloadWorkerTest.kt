package com.browntowndev.pocketcrew.core.data.download

import com.browntowndev.pocketcrew.domain.model.download.DownloadFileSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for ModelDownloadWorker file spec parsing.
 * Verifies correct parsing of JSON objects into DownloadFileSpec.
 */
class ModelDownloadWorkerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parseFileSpecFromJson correctly parses complete file spec`() {
        val jsonStr = """
            {
                "remoteFileName": "Llama-3-8B-Instruct-Q4_K_M.gguf",
                "localFileName": "Llama-3-8B-Instruct-Q4_K_M.gguf",
                "sha256": "abc123def456",
                "sizeInBytes": 5200000000,
                "huggingFaceModelName": "meta-llama/Llama-3-8B-Instruct",
                "source": "HUGGING_FACE",
                "modelFileFormat": "GGUF"
            }
        """.trimIndent()

        val result = json.decodeFromString<DownloadFileSpec>(jsonStr)

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
        val jsonStr = """
            {
                "remoteFileName": "gemma-3n-E2B-it-int4.litertlm",
                "localFileName": "gemma-3n-E2B-it-int4.litertlm",
                "sha256": "def789ghi012",
                "sizeInBytes": 2800000000,
                "huggingFaceModelName": "google/gemma-3n-E2B",
                "source": "HUGGING_FACE",
                "modelFileFormat": "LITERTLM"
            }
        """.trimIndent()

        val result = json.decodeFromString<DownloadFileSpec>(jsonStr)

        assertNotNull(result)
        assertEquals("LITERTLM", result.modelFileFormat)
    }

    @Test
    fun `parseFileSpecFromJson defaults modelFileFormat on missing field`() {
        val jsonStr = """
            {
                "remoteFileName": "model.unknown",
                "localFileName": "model.unknown",
                "sha256": "abc123",
                "sizeInBytes": 1000,
                "huggingFaceModelName": "test/model",
                "source": "HUGGING_FACE"
            }
        """.trimIndent()

        val result = json.decodeFromString<DownloadFileSpec>(jsonStr)

        assertNotNull(result)
        assertEquals("LITERTLM", result.modelFileFormat)
    }

    @Test
    fun `parseFileSpecFromJson parses mmproj fields`() {
        val jsonStr = """
            {
                "remoteFileName": "model.gguf",
                "localFileName": "model.gguf",
                "sha256": "sha123",
                "sizeInBytes": 1000,
                "huggingFaceModelName": "test/model",
                "source": "HUGGING_FACE",
                "modelFileFormat": "GGUF",
                "mmprojRemoteFileName": "mmproj-model.gguf",
                "mmprojLocalFileName": "mmproj-model.gguf",
                "mmprojSha256": "mmsha456",
                "mmprojSizeInBytes": 500
            }
        """.trimIndent()

        val result = json.decodeFromString<DownloadFileSpec>(jsonStr)

        assertNotNull(result)
        assertEquals("mmproj-model.gguf", result.mmprojRemoteFileName)
        assertEquals("mmproj-model.gguf", result.mmprojLocalFileName)
        assertEquals("mmsha456", result.mmprojSha256)
        assertEquals(500L, result.mmprojSizeInBytes)
    }

    @Test
    fun `parseFileSpecFromJson handles empty optional fields`() {
        val jsonStr = """
            {
                "remoteFileName": "model.gguf",
                "localFileName": "model.gguf",
                "sha256": "abc123",
                "sizeInBytes": 1000,
                "huggingFaceModelName": "test/model",
                "source": "HUGGING_FACE",
                "modelFileFormat": "GGUF"
            }
        """.trimIndent()

        val result = json.decodeFromString<DownloadFileSpec>(jsonStr)

        assertNotNull(result)
        assertNull(result.mmprojRemoteFileName)
        assertNull(result.mmprojLocalFileName)
        assertNull(result.mmprojSha256)
        assertNull(result.mmprojSizeInBytes)
    }

    @Test
    fun `parseFileSpecFromJson defaults source to HUGGING_FACE when omitted`() {
        val jsonStr = """
            {
                "remoteFileName": "model.gguf",
                "localFileName": "model.gguf",
                "sha256": "abc123",
                "sizeInBytes": 1000,
                "huggingFaceModelName": "test/model",
                "modelFileFormat": "GGUF"
            }
        """.trimIndent()

        val result = json.decodeFromString<DownloadFileSpec>(jsonStr)

        assertNotNull(result)
        assertEquals("HUGGING_FACE", result.source)
    }
}
