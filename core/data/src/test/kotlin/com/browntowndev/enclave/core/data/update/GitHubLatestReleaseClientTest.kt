package com.browntowndev.enclave.core.data.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GitHubLatestReleaseClient")
class GitHubLatestReleaseClientTest {
    private var cannedCode: Int = 200
    private var cannedBody: String? = null
    private var cannedFailure: java.io.IOException? = null

    private lateinit var client: GitHubLatestReleaseClient

    @BeforeEach
    fun setup() {
        cannedCode = 200
        cannedBody = null
        cannedFailure = null
        val okHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    cannedFailure?.let { throw it }
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(cannedCode)
                        .message("Mock")
                        .body((cannedBody ?: "").toResponseBody())
                        .build()
                }.build()
        client = GitHubLatestReleaseClient(okHttpClient)
    }

    @Test
    fun `200 with valid JSON parses and builds the download URL`() =
        runTest {
            cannedBody =
                """
                {
                  "versionCode": 7,
                  "versionName": "1.1.0",
                  "apkFileName": "enclave-v1.1.0.apk",
                  "sha256": "abc",
                  "sizeBytes": 12,
                  "minSdk": 34,
                  "publishedAt": "2026-10-26T00:00:00Z"
                }
                """.trimIndent()

            val result = client.fetchLatestRelease()

            assertEquals(7, result?.versionCode)
            assertEquals("1.1.0", result?.versionName)
            assertEquals("enclave-v1.1.0.apk", result?.apkFileName)
            assertEquals(
                "https://github.com/shippopotamuslabs/enclave/releases/latest/download/enclave-v1.1.0.apk",
                result?.downloadUrl,
            )
        }

    @Test
    fun `404 returns null`() =
        runTest {
            cannedCode = 404
            cannedBody = "Not Found"

            assertNull(client.fetchLatestRelease())
        }

    @Test
    fun `malformed JSON returns null`() =
        runTest {
            cannedBody = "{ this is not json"

            assertNull(client.fetchLatestRelease())
        }

    @Test
    fun `missing field returns null`() =
        runTest {
            cannedBody =
                """
                {
                  "versionCode": 7,
                  "versionName": "1.1.0"
                }
                """.trimIndent()

            assertNull(client.fetchLatestRelease())
        }

    @Test
    fun `network failure returns null`() =
        runTest {
            cannedFailure = java.io.IOException("No route to host")

            assertNull(client.fetchLatestRelease())
        }

    @Test
    fun `traversal file name returns null`() =
        runTest {
            cannedBody =
                """
                {
                  "versionCode": 7,
                  "versionName": "1.1.0",
                  "apkFileName": "../evil.apk"
                }
                """.trimIndent()

            assertNull(client.fetchLatestRelease())
        }

    @Test
    fun `nested path file name returns null`() =
        runTest {
            cannedBody =
                """
                {
                  "versionCode": 7,
                  "versionName": "1.1.0",
                  "apkFileName": "sub/dir/enclave.apk"
                }
                """.trimIndent()

            assertNull(client.fetchLatestRelease())
        }

    @Test
    fun `absolute URL file name returns null`() =
        runTest {
            cannedBody =
                """
                {
                  "versionCode": 7,
                  "versionName": "1.1.0",
                  "apkFileName": "https://evil.example.com/enclave.apk"
                }
                """.trimIndent()

            assertNull(client.fetchLatestRelease())
        }

    @Test
    fun `normal apk file name still parses`() =
        runTest {
            cannedBody =
                """
                {
                  "versionCode": 8,
                  "versionName": "1.2.3",
                  "apkFileName": "enclave-v1.2.3.apk"
                }
                """.trimIndent()

            val result = client.fetchLatestRelease()

            assertEquals("enclave-v1.2.3.apk", result?.apkFileName)
            assertEquals(
                "https://github.com/shippopotamuslabs/enclave/releases/latest/download/enclave-v1.2.3.apk",
                result?.downloadUrl,
            )
        }
}
