package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import org.junit.jupiter.api.Assertions.assertEquals
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
        val asset = createMockAsset(source = DownloadSource.CLOUDFLARE_R2, fileName = "test-model.gguf")

        // When
        val url = urlProvider.getModelDownloadUrl(asset)

        // Then
        assertEquals("https://config.pocketcrew.app/test-model.gguf", url)
    }

    @Test
    fun getModelDownloadUrl_returnsHFUrl_whenSourceIsHF() {
        // Given
        val asset = createMockAsset(
            source = DownloadSource.HUGGING_FACE,
            huggingFaceModelName = "user/repo",
            fileName = "model.gguf"
        )

        // When
        val url = urlProvider.getModelDownloadUrl(asset)

        // Then
        assertTrue(url.startsWith("https://huggingface.co/user/repo/resolve/main/model.gguf"))
    }

    private fun createMockAsset(
        source: DownloadSource,
        fileName: String = "model.gguf",
        huggingFaceModelName: String = ""
    ): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = huggingFaceModelName,
                remoteFileName = fileName,
                localFileName = fileName,
                sha256 = "abc123",
                sizeInBytes = 1000L,
                modelFileFormat = ModelFileFormat.GGUF,
                source = source
            ),
            configurations = emptyList()
        )
    }
}
