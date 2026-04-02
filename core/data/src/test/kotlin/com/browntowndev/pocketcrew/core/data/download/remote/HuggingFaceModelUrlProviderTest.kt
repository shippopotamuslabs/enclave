package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HuggingFaceModelUrlProviderTest {

    private val urlProvider = HuggingFaceModelUrlProvider()

    @Test
    fun `getConfigUrl returns correct R2 bucket URL`() {
        val configUrl = urlProvider.getConfigUrl()
        assertEquals("https://config.pocketcrew.app/model_config.json", configUrl)
    }

    @Test
    fun `getModelDownloadUrl constructs correct URL for standard model`() {
        val asset = createAsset(
            huggingFaceModelName = "meta-llama/Llama-3-8B-Instruct",
            remoteFileName = "Llama-3-8B-Instruct-Q4_K_M.gguf"
        )

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)

        assertEquals(
            "https://huggingface.co/meta-llama/Llama-3-8B-Instruct/resolve/main/Llama-3-8B-Instruct-Q4_K_M.gguf",
            downloadUrl
        )
    }

    @Test
    fun `getModelDownloadUrl constructs correct URL for gemma model`() {
        val asset = createAsset(
            huggingFaceModelName = "google/gemma-2-9b-it",
            remoteFileName = "gemma-2-9b-it-Q4_K_M.gguf"
        )

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)

        assertEquals(
            "https://huggingface.co/google/gemma-2-9b-it/resolve/main/gemma-2-9b-it-Q4_K_M.gguf",
            downloadUrl
        )
    }

    @Test
    fun `getModelDownloadUrl uses remoteFileName not localFileName`() {
        val asset = createAsset(
            huggingFaceModelName = "mistralai/Mistral-7B-Instruct-v0.2",
            remoteFileName = "mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            localFileName = "custom-local-name.gguf"
        )

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)

        // Should use remoteFileName, not localFileName
        assertEquals(
            "https://huggingface.co/mistralai/Mistral-7B-Instruct-v0.2/resolve/main/mistral-7b-instruct-v0.2.Q4_K_M.gguf",
            downloadUrl
        )
        assertEquals(false, downloadUrl.contains("custom-local-name"))
    }

    @Test
    fun `getModelDownloadUrl handles model names with slashes correctly`() {
        val asset = createAsset(
            huggingFaceModelName = "TheBloke/Mistral-7B-v0.1-GGUF",
            remoteFileName = "mistral-7b-v0.1.Q4_K_M.gguf"
        )

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)

        assertEquals(
            "https://huggingface.co/TheBloke/Mistral-7B-v0.1-GGUF/resolve/main/mistral-7b-v0.1.Q4_K_M.gguf",
            downloadUrl
        )
    }

    @Test
    fun `getModelDownloadUrl handles litertlm format files`() {
        val asset = createAsset(
            huggingFaceModelName = "apple/OpenELM-3B",
            remoteFileName = "openelm-3b-it-preview.Q4_K_M.litertlm"
        )

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)

        assertEquals(
            "https://huggingface.co/apple/OpenELM-3B/resolve/main/openelm-3b-it-preview.Q4_K_M.litertlm",
            downloadUrl
        )
    }

    @Test
    fun `getModelDownloadUrl handles task format files`() {
        val asset = createAsset(
            huggingFaceModelName = "anthropic/claude-3-haiku",
            remoteFileName = "claude-3-haiku-task.Q8_0.task"
        )

        val downloadUrl = urlProvider.getModelDownloadUrl(asset)

        assertEquals(
            "https://huggingface.co/anthropic/claude-3-haiku/resolve/main/claude-3-haiku-task.Q8_0.task",
            downloadUrl
        )
    }

    private fun createAsset(
        huggingFaceModelName: String,
        remoteFileName: String,
        localFileName: String = remoteFileName
    ): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = huggingFaceModelName,
                remoteFileName = remoteFileName,
                localFileName = localFileName,
                sha256 = "abc123def456",
                sizeInBytes = 5_000_000_000L,
                modelFileFormat = ModelFileFormat.GGUF
            ),
            configurations = emptyList()
        )
    }
}
