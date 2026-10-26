@file:Suppress("ForbiddenMockKInTests") // FirebaseFunctions/HttpsCallableReference are Firebase SDK types with no JVM fake.

package com.browntowndev.enclave.core.data.download.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirebaseModelDownloadUrlMinterTest {
    private val functions = mockk<FirebaseFunctions>()
    private lateinit var minter: FirebaseModelDownloadUrlMinter

    @BeforeEach
    fun setup() {
        minter = FirebaseModelDownloadUrlMinter(functions)
    }

    @Test
    fun `mint sends filePath and parses url plus expiresAtMillis`() =
        runTest {
            val callable = mockk<HttpsCallableReference>()
            every {
                functions.getHttpsCallable(FirebaseModelDownloadUrlMinter.CALLABLE_NAME)
            } returns callable
            every { callable.call(mapOf("filePath" to "models/foo.gguf")) } returns
                Tasks.forResult(callableResult(mapOf(
                    "url" to "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=test",
                    "expiresAtMillis" to 1_700_000_000_123L,
                )))

            val result = minter.mintModelDownloadUrl("models/foo.gguf")

            assertEquals(
                "https://abc123.r2.cloudflarestorage.com/models/foo.gguf?sig=test",
                result.url,
            )
            assertEquals(1_700_000_000_123L, result.expiresAtMillis)
            verify(exactly = 1) { callable.call(mapOf("filePath" to "models/foo.gguf")) }
        }

    @Test
    fun `mint rejects response missing url`() =
        runTest {
            val callable = mockk<HttpsCallableReference>()
            every {
                functions.getHttpsCallable(FirebaseModelDownloadUrlMinter.CALLABLE_NAME)
            } returns callable
            every { callable.call(any()) } returns
                Tasks.forResult(callableResult(mapOf("expiresAtMillis" to 1L)))

            val error = runCatching { minter.mintModelDownloadUrl("models/foo.gguf") }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
        }

    @Test
    fun `mint rejects response missing expiresAtMillis`() =
        runTest {
            val callable = mockk<HttpsCallableReference>()
            every {
                functions.getHttpsCallable(FirebaseModelDownloadUrlMinter.CALLABLE_NAME)
            } returns callable
            every { callable.call(any()) } returns
                Tasks.forResult(callableResult(mapOf("url" to "https://example.com/x")))

            val error = runCatching { minter.mintModelDownloadUrl("models/foo.gguf") }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
        }

    @Test
    fun `mint propagates callable failure`() =
        runTest {
            val callable = mockk<HttpsCallableReference>()
            every {
                functions.getHttpsCallable(FirebaseModelDownloadUrlMinter.CALLABLE_NAME)
            } returns callable
            every { callable.call(any()) } returns Tasks.forException(RuntimeException("UNAVAILABLE"))

            val error = runCatching { minter.mintModelDownloadUrl("models/foo.gguf") }.exceptionOrNull()
            assertTrue(error is RuntimeException)
            assertEquals("UNAVAILABLE", error?.message)
        }

    // HttpsCallableResult's constructor is internal to the Functions SDK, so build
    // instances reflectively (same approach as FirebaseFunctionsException in the
    // FirestoreSyncFeedCallableClientTest precedent).
    private fun callableResult(data: Any?): HttpsCallableResult {
        val constructor = HttpsCallableResult::class.java.getDeclaredConstructor(Object::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(data)
    }
}
