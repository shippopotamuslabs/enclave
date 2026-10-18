@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (DocumentSnapshot), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore.handlers

import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.core.data.remote.firestore.FakeMessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.mockk
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
class MessageInferenceTelemetrySyncHandlerTest {
    private val telemetryDao = FakeMessageInferenceTelemetryDao()
    private lateinit var handler: MessageInferenceTelemetrySyncHandler

    companion object {
        private const val UPDATED_AT = 5_000L
    }

    @BeforeEach
    fun setup() {
        handler = MessageInferenceTelemetrySyncHandler(createContext(telemetryDao))
    }

    @Test
    fun `upsertLocal coalesces null token fields on equal updated_at`() =
        runTest {
            val existing =
                TelemetryTestFixtures.telOpenAi1
                    .toEntity()
                    .copy(
                        updatedAt = UPDATED_AT,
                        pendingSync = 1,
                    )
            val remote =
                existing.copy(
                    inputTokens = null,
                    outputTokens = null,
                    reasoningTokens = null,
                    cacheReadInputTokens = null,
                    firstOutputTokenAtMs = null,
                    completedAtMs = null,
                    costUsdDecimal = null,
                    pendingSync = 0,
                )
            telemetryDao.entities[existing.id] = existing

            handler.upsertLocal(
                entity = remote,
                doc = mockk<DocumentSnapshot>(relaxed = true),
                overwrite = false,
            )

            // Coalesced merge matches the local row (except pendingSync), so the handler must not
            // upsert a remote doc that would have clobbered token columns with nulls.
            assertTrue(telemetryDao.upsertCalls.isEmpty())
        }

    @Test
    fun `upsertLocal coalesces null token fields and upserts when remote adds completedAt`() =
        runTest {
            val existing =
                TelemetryTestFixtures.telOpenAi1
                    .toEntity()
                    .copy(
                        updatedAt = UPDATED_AT,
                        pendingSync = 1,
                        completedAtMs = null,
                    )
            val remote =
                existing.copy(
                    inputTokens = null,
                    outputTokens = null,
                    reasoningTokens = null,
                    cacheReadInputTokens = null,
                    completedAtMs = 9_999L,
                    pendingSync = 0,
                )
            telemetryDao.entities[existing.id] = existing

            handler.upsertLocal(
                entity = remote,
                doc = mockk<DocumentSnapshot>(relaxed = true),
                overwrite = false,
            )

            assertEquals(1, telemetryDao.upsertCalls.size)
            val merged = telemetryDao.upsertCalls.single()
            assertEquals(1_000, merged.inputTokens)
            assertEquals(200, merged.outputTokens)
            assertEquals(9_999L, merged.completedAtMs)
            assertEquals(0, merged.pendingSync)
        }

    private fun createContext(telemetryDao: MessageInferenceTelemetryDao): FirestoreSyncContext =
        testSyncContext(daos = testFirestoreSyncDaos(messageInferenceTelemetryDao = telemetryDao))
}
