@file:Suppress("ForbiddenMockKInTests") // FirebaseFunctions/HttpsCallableReference are Firebase SDK types with no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableReference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreSyncFeedCallableClientTest {
    private val functions = mockk<FirebaseFunctions>()
    private val loggingPort = FakeLoggingPort()
    private lateinit var client: FirestoreSyncFeedCallableClient

    @BeforeEach
    fun setup() {
        client = FirestoreSyncFeedCallableClient(functions, loggingPort)
    }

    @Test
    fun `permission denied stops further backfill calls`() =
        runTest {
            val callable = mockk<HttpsCallableReference>()
            every {
                functions.getHttpsCallable(FirestoreSyncFeedCallableClient.BACKFILL_CALLABLE)
            } returns callable
            every { callable.call(any()) } returns Tasks.forException(permissionDenied())

            assertNull(client.backfillIfNeeded("uid"))
            assertNull(client.backfillIfNeeded("uid"))

            verify(exactly = 1) {
                functions.getHttpsCallable(FirestoreSyncFeedCallableClient.BACKFILL_CALLABLE)
            }
            assertTrue(loggingPort.warningCalls.isNotEmpty())
        }

    @Test
    fun `permission denied stops mark initialized calls too`() =
        runTest {
            val callable = mockk<HttpsCallableReference>()
            every {
                functions.getHttpsCallable(FirestoreSyncFeedCallableClient.BACKFILL_CALLABLE)
            } returns callable
            every { callable.call(any()) } returns Tasks.forException(permissionDenied())

            assertNull(client.backfillIfNeeded("uid"))
            assertNull(client.markFeedInitializedIfNeeded("uid"))

            verify(exactly = 1) {
                functions.getHttpsCallable(FirestoreSyncFeedCallableClient.BACKFILL_CALLABLE)
            }
        }

    @Test
    fun `transient failure keeps calling`() =
        runTest {
            val callable = mockk<HttpsCallableReference>()
            every {
                functions.getHttpsCallable(FirestoreSyncFeedCallableClient.BACKFILL_CALLABLE)
            } returns callable
            every { callable.call(any()) } returns Tasks.forException(RuntimeException("unavailable"))

            assertNull(client.backfillIfNeeded("uid"))
            assertNull(client.backfillIfNeeded("uid"))

            verify(exactly = 2) {
                functions.getHttpsCallable(FirestoreSyncFeedCallableClient.BACKFILL_CALLABLE)
            }
        }

    // FirebaseFunctionsException is final with only Kotlin-internal constructors, so it
    // can neither be MockK-mocked nor directly constructed; reach the declared
    // constructor reflectively (runtime-legal on the JVM classpath).
    private fun permissionDenied(): FirebaseFunctionsException {
        val constructor =
            FirebaseFunctionsException::class.java.getDeclaredConstructor(
                String::class.java,
                FirebaseFunctionsException.Code::class.java,
                Any::class.java,
            )
        constructor.isAccessible = true
        return constructor.newInstance(
            "PERMISSION_DENIED",
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            null,
        )
    }
}
