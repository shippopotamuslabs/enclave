package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
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

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatRepositoryImplTelemetryPersistTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var telemetryDao: MessageInferenceTelemetryDao
    private lateinit var repository: ChatRepositoryImpl

    @BeforeEach
    fun setup() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database =
                Room
                    .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            telemetryDao = database.messageInferenceTelemetryDao()
            repository = database.chatRepositoryImpl(RecordingCloudSyncTrigger())

            // Parent chat + message rows required by the telemetry table's foreign key.
            val chatId = ChatId("chat-1")
            database.chatDao().upsert(
                ChatEntity(id = chatId, name = "Chat", created = Date(), lastModified = Date(), pinned = false),
            )
            database.messageDao().upsert(
                MessageEntity(
                    id = TelemetryTestFixtures.mOpenAi1,
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                    content = "assistant response",
                    createdAt = 1000L,
                ),
            )
        }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `persistAllMessageData inserts telemetry rows atomically with assistant message`() =
        runTest {
            val telemetry = listOf(TelemetryTestFixtures.telOpenAi1)

            repository.persistAllMessageData(
                messageId = TelemetryTestFixtures.mOpenAi1,
                modelType = ModelType.FAST,
                thinkingStartTime = 0L,
                thinkingEndTime = 0L,
                thinkingDuration = null,
                thinkingRaw = null,
                content = "assistant response",
                messageState = MessageState.COMPLETE,
                pipelineStep = null,
                webSearchSources = emptyList(),
                artifacts = emptyList(),
                generatedMediaUris = emptyList(),
                generatedMediaAssetIds = emptyList(),
                generatedMediaTypes = emptyList(),
                generatedMediaPrompts = emptyList(),
                transcriptSegments = emptyList(),
                telemetry = telemetry,
            )

            val entity = telemetryDao.observeAll().first().single()
            assertEquals("tel-openai-1", entity.id)
            assertEquals("m-openai-1", entity.messageId)
            assertEquals("chat-1", entity.chatId)
            assertEquals("0.002500", entity.costUsdDecimal)
            assertEquals(1, entity.pendingSync)
        }
}
