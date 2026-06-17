package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryEntity
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatRepositoryImplTelemetryPersistTest {
    @Test
    fun `persistAllMessageData inserts telemetry rows atomically with assistant message`() =
        runTest {
            val telemetryDao = mockk<MessageInferenceTelemetryDao>(relaxed = true)
            val replaced = slot<List<MessageInferenceTelemetryEntity>>()
            coEvery {
                telemetryDao.replaceTelemetryForMessage(
                    messageId = TelemetryTestFixtures.mOpenAi1.value,
                    rows = capture(replaced),
                )
            } returns Unit

            val repository = repository(telemetryDao)
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

            coVerify(exactly = 1) {
                telemetryDao.replaceTelemetryForMessage(
                    messageId = TelemetryTestFixtures.mOpenAi1.value,
                    rows = any(),
                )
            }
            val entity = replaced.captured.single()
            assertEquals("tel-openai-1", entity.id)
            assertEquals("m-openai-1", entity.messageId)
            assertEquals("chat-1", entity.chatId)
            assertEquals("0.002500", entity.costUsdDecimal)
            assertEquals(1, entity.pendingSync)
        }

    private fun repository(telemetryDao: MessageInferenceTelemetryDao): ChatRepositoryImpl {
        val transactionProvider = mockk<TransactionProvider>()
        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (it.invocation.args[0] as suspend () -> Any).invoke()
        }
        return ChatRepositoryImpl(
            syncTrigger = mockk<CloudSyncTrigger>(relaxed = true),
            chatDao = mockk<ChatDao>(relaxed = true),
            messageDao = mockk<MessageDao>(relaxed = true),
            messageTranscriptSegmentDao = mockk<MessageTranscriptSegmentDao>(relaxed = true),
            webSearchSourceDao = mockk<WebSearchSourceDao>(relaxed = true),
            documentDao = mockk<DocumentDao>(relaxed = true),
            transactionProvider = transactionProvider,
            cleanupDao = mockk(relaxed = true),
            messageInferenceTelemetryDao = telemetryDao,
            messageFileDao = mockk(relaxed = true),
            healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
            readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true),
        )
    }
}
