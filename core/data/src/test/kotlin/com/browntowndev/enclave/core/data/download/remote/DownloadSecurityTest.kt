package com.browntowndev.enclave.core.data.download.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DownloadSecurityTest {
    @Test
    fun `requireTrustedDownloadUrl accepts presigned R2 host over https`() {
        val url =
            DownloadSecurity.requireTrustedDownloadUrl(
                "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?X-Amz-Expires=900&X-Amz-Signature=test",
            )
        assertEquals("abc123.r2.cloudflarestorage.com", url.host)
    }

    @Test
    fun `requireTrustedDownloadUrl rejects http scheme`() {
        assertThrows<SecurityException> {
            DownloadSecurity.requireTrustedDownloadUrl(
                "http://r2.cloudflarestorage.com.evil.com/models/foo.gguf",
            )
        }
        assertThrows<SecurityException> {
            DownloadSecurity.requireTrustedDownloadUrl(
                "http://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=test",
            )
        }
    }

    @Test
    fun `requireTrustedRedirect allows presigned host redirecting to itself`() {
        val original = "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=1".toHttpUrl()
        val final = "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=2".toHttpUrl()

        assertDoesNotThrow {
            DownloadSecurity.requireTrustedRedirect(original, final)
        }
    }

    @Test
    fun `requireTrustedRedirect rejects presigned host redirecting to another account`() {
        val original = "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=1".toHttpUrl()
        val final = "https://xyz999.r2.cloudflarestorage.com/models/foo.gguf?sig=1".toHttpUrl()

        assertThrows<SecurityException> {
            DownloadSecurity.requireTrustedRedirect(original, final)
        }
    }

    @Test
    fun `requireTrustedRedirect rejects evil lookalike host`() {
        val original = "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=1".toHttpUrl()
        val final = "https://r2.cloudflarestorage.com.evil.com/models/foo.gguf".toHttpUrl()

        assertThrows<SecurityException> {
            DownloadSecurity.requireTrustedRedirect(original, final)
        }
    }

    @Test
    fun `requireTrustedRedirect rejects presigned host redirecting to http`() {
        val original = "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=1".toHttpUrl()
        val final = "http://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=1".toHttpUrl()

        assertThrows<SecurityException> {
            DownloadSecurity.requireTrustedRedirect(original, final)
        }
    }

    @Test
    fun `requireTrustedRedirect keeps existing R2 rule unchanged`() {
        val original = "https://config.enclave-app.com/model.gguf".toHttpUrl()

        assertDoesNotThrow {
            DownloadSecurity.requireTrustedRedirect(original, "https://config.enclave-app.com/model.gguf".toHttpUrl())
        }
        assertThrows<SecurityException> {
            DownloadSecurity.requireTrustedRedirect(
                original,
                "https://abc123.r2.cloudflarestorage.com/model.gguf?sig=1".toHttpUrl(),
            )
        }
    }
}
