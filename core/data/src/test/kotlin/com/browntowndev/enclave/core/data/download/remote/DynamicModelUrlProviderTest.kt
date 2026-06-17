package com.browntowndev.enclave.core.data.download.remote

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DynamicModelUrlProviderTest {
    private lateinit var urlProvider: DynamicModelUrlProvider

    @BeforeEach
    fun setup() {
        urlProvider = DynamicModelUrlProvider()
    }

    @Test
    fun getModelDownloadUrl_returnsR2Url_whenSourceIsR2() {
        // Given
        val spec = createFileSpec(source = "CLOUDFLARE_R2", fileName = "test-model.gguf")

        // When
        val url = urlProvider.getModelDownloadUrl(spec)

        // Then
        assertEquals("https://config.enclave-app.com/test-model.gguf", url)
    }

    @Test
    fun getModelDownloadUrl_returnsHFUrl_whenSourceIsHF() {
        // Given
        val spec =
            createFileSpec(
                source = "HUGGING_FACE",
                huggingFaceModelName = "user/repo",
                fileName = "model.gguf",
            )

        // When
        val url = urlProvider.getModelDownloadUrl(spec)

        // Then
        assertTrue(url.startsWith("https://huggingface.co/user/repo/resolve/main/model.gguf"))
    }

    @Test
    fun getModelDownloadUrl_rejectsTraversalInRemoteFileName() {
        val spec =
            createFileSpec(
                source = "CLOUDFLARE_R2",
                fileName = "../secrets.db",
            )

        assertThrows(SecurityException::class.java) {
            urlProvider.getModelDownloadUrl(spec)
        }
    }

    @Test
    fun getModelDownloadUrl_rejectsInvalidHuggingFaceRepoId() {
        val spec =
            createFileSpec(
                source = "HUGGING_FACE",
                huggingFaceModelName = "user/repo/extra",
                fileName = "model.gguf",
            )

        assertThrows(IllegalArgumentException::class.java) {
            urlProvider.getModelDownloadUrl(spec)
        }
    }

    @Test
    fun getModelDownloadUrl_returnsR2UrlWithPathPrefix_whenSourceIsR2AndPathIsProvided() {
        // Given
        val spec = createFileSpec(source = "CLOUDFLARE_R2", fileName = "test-model.gguf", huggingFacePath = "my-sub-folder")

        // When
        val url = urlProvider.getModelDownloadUrl(spec)

        // Then
        assertEquals("https://config.enclave-app.com/my-sub-folder/test-model.gguf", url)
    }

    private fun createFileSpec(
        source: String,
        fileName: String = "model.gguf",
        huggingFaceModelName: String = "",
        huggingFacePath: String? = null,
    ): DownloadFileSpec =
        DownloadFileSpec(
            remoteFileName = fileName,
            localFileName = fileName,
            sha256 = "abc123",
            sizeInBytes = 1000L,
            huggingFaceModelName = huggingFaceModelName,
            huggingFacePath = huggingFacePath,
            source = source,
            modelFileFormat = "GGUF",
        )
}
