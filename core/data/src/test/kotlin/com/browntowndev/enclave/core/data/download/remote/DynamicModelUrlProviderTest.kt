package com.browntowndev.enclave.core.data.download.remote

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DynamicModelUrlProviderTest {
    /** Fake minter: records the requested object key and returns a scripted URL. */
    private class FakeMinter(
        var mintedUrl: String = "https://abc123.r2.cloudflarestorage.com/presigned?sig=test",
        var failure: Throwable? = null,
    ) : ModelDownloadUrlMinter {
        val requestedKeys = mutableListOf<String>()

        override suspend fun mintModelDownloadUrl(filePath: String): MintedModelDownloadUrl {
            requestedKeys += filePath
            failure?.let { throw it }
            return MintedModelDownloadUrl(url = mintedUrl, expiresAtMillis = 1_700_000_000_000L)
        }
    }

    private lateinit var minter: FakeMinter
    private lateinit var urlProvider: DynamicModelUrlProvider

    @BeforeEach
    fun setup() {
        minter = FakeMinter()
        urlProvider = DynamicModelUrlProvider(minter)
    }

    @Test
    fun getModelDownloadUrl_mintsPresignedUrl_whenSourceIsR2() =
        runTest {
            // Given
            val spec = createFileSpec(source = "CLOUDFLARE_R2", fileName = "test-model.gguf")

            // When
            val url = urlProvider.getModelDownloadUrl(spec)

            // Then: object key is exactly the former public-bucket path, URL comes from the callable
            assertEquals(listOf("test-model.gguf"), minter.requestedKeys)
            assertEquals(minter.mintedUrl, url)
        }

    @Test
    fun getModelDownloadUrl_mintsWithPathPrefix_whenSourceIsR2AndPathIsProvided() =
        runTest {
            // Given
            val spec =
                createFileSpec(
                    source = "CLOUDFLARE_R2",
                    fileName = "test-model.gguf",
                    huggingFacePath = "my-sub-folder",
                )

            // When
            val url = urlProvider.getModelDownloadUrl(spec)

            // Then
            assertEquals(listOf("my-sub-folder/test-model.gguf"), minter.requestedKeys)
            assertEquals(minter.mintedUrl, url)
        }

    @Test
    fun getModelDownloadUrl_propagatesCallableFailure_withoutPublicR2Fallback() =
        runTest {
            // Given: the callable fails (e.g. permission-denied for non-subscribers)
            minter.failure = RuntimeException("PERMISSION_DENIED")
            val spec = createFileSpec(source = "CLOUDFLARE_R2", fileName = "test-model.gguf")

            // When / Then: the failure propagates; no public bucket URL is returned
            val error = runCatching { urlProvider.getModelDownloadUrl(spec) }.exceptionOrNull()
            assertTrue(error is RuntimeException)
            assertEquals("PERMISSION_DENIED", error?.message)
            assertEquals(listOf("test-model.gguf"), minter.requestedKeys)
        }

    @Test
    fun getModelDownloadUrl_returnsHFUrl_whenSourceIsHF() =
        runTest {
            // Given
            val spec =
                createFileSpec(
                    source = "HUGGING_FACE",
                    huggingFaceModelName = "user/repo",
                    fileName = "model.gguf",
                )

            // When
            val url = urlProvider.getModelDownloadUrl(spec)

            // Then: byte-identical HF behavior, callable untouched
            assertTrue(url.startsWith("https://huggingface.co/user/repo/resolve/main/model.gguf"))
            assertTrue(minter.requestedKeys.isEmpty())
        }

    @Test
    fun getModelDownloadUrl_returnsConfigAndTrustedDomainsUrls_unchanged() {
        assertEquals("https://config.enclave-app.com/model_config.json", urlProvider.getConfigUrl())
        assertEquals("https://config.enclave-app.com/trusted_domains.json", urlProvider.getTrustedDomainsUrl())
        assertTrue(minter.requestedKeys.isEmpty())
    }

    @Test
    fun getModelDownloadUrl_rejectsTraversalInRemoteFileName() =
        runTest {
            val spec =
                createFileSpec(
                    source = "CLOUDFLARE_R2",
                    fileName = "../secrets.db",
                )

            val error = runCatching { urlProvider.getModelDownloadUrl(spec) }.exceptionOrNull()
            assertTrue(error is SecurityException)
            assertTrue(minter.requestedKeys.isEmpty())
        }

    @Test
    fun getModelDownloadUrl_rejectsInvalidHuggingFaceRepoId() =
        runTest {
            val spec =
                createFileSpec(
                    source = "HUGGING_FACE",
                    huggingFaceModelName = "user/repo/extra",
                    fileName = "model.gguf",
                )

            val error = runCatching { urlProvider.getModelDownloadUrl(spec) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(minter.requestedKeys.isEmpty())
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
