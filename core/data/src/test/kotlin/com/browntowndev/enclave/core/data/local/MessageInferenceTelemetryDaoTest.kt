package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.core.data.mapper.telemetry.toFirestoreFields
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date
import java.util.UUID

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MessageInferenceTelemetryDaoTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var telemetryDao: MessageInferenceTelemetryDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        telemetryDao = database.messageInferenceTelemetryDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private suspend fun seedMessage() {
        val chatDao = database.chatDao()
        val messageDao = database.messageDao()
        chatDao.upsert(
            ChatEntity(
                id = TelemetryTestFixtures.chat1,
                name = "Chat",
                created = Date(),
                lastModified = Date(),
                pinned = false,
            ),
        )
        messageDao.upsert(
            MessageEntity(
                id = TelemetryTestFixtures.mOpenAi1,
                chatId = TelemetryTestFixtures.chat1,
                content = "Hi",
                role = com.browntowndev.enclave.domain.model.chat.Role.ASSISTANT,
            ),
        )
    }

    @Test
    fun `TEL_014 telemetry rows persist app version and schema version for fleet diagnostics`() =
        runTest {
            seedMessage()
            val entity = TelemetryTestFixtures.telOpenAi1.toEntity()
            telemetryDao.insertAll(listOf(entity))

            val rows = telemetryDao.observeByMessageId(TelemetryTestFixtures.mOpenAi1.value).first()
            assertEquals(1, rows.size)
            assertEquals("1.4.0", rows.single().appVersionName)
            assertEquals(140L, rows.single().appVersionCode)
            assertEquals(1, rows.single().telemetrySchemaVersion)

            val firestore = rows.single().toFirestoreFields()
            assertEquals("1.4.0", firestore["appVersionName"])
            assertEquals(140L, firestore["appVersionCode"])
            assertEquals(1, firestore["telemetrySchemaVersion"])
        }
}
