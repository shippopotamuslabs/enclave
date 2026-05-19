package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatRepositoryImplArtifactPersistTest {
    @Test
    fun `FAAL-AR-01 blank accumulator does not wipe DAO json`() =
        runTest {
            val documentDao = mockk<DocumentDao>(relaxed = true)
            val inserted = slot<DocumentEntity>()
            coEvery { documentDao.getDocumentById("doc-1") } returns existingDocument("doc-1", "Saved")
            coEvery { documentDao.insertDocument(capture(inserted)) } returns Unit
            val repository = repository(documentDao)

            repository.persistAllMessageData(
                messageId = MessageId("msg-artifact-1"),
                modelType = ModelType.FAST,
                thinkingStartTime = 0L,
                thinkingEndTime = 0L,
                thinkingDuration = null,
                thinkingRaw = null,
                content = "assistant",
                messageState = MessageState.COMPLETE,
                pipelineStep = null,
                webSearchSources = emptyList(),
                artifacts = listOf(ArtifactGenerationRequest(artifactId = "doc-1", title = "Report", content = "")),
                generatedMediaUris = emptyList(),
                generatedMediaAssetIds = emptyList(),
                generatedMediaTypes = emptyList(),
                generatedMediaPrompts = emptyList(),
                transcriptSegments = emptyList(),
            )

            assertTrue(inserted.captured.id == "doc-1", "FAAL-AR-01 inserted id")
            assertTrue(inserted.captured.json.contains("Saved"), "FAAL-AR-01 preserved json")
        }

    @Test
    fun `FAAL-AR-04 failed append_blocks persist retains prior document`() =
        runTest {
            val documentDao = mockk<DocumentDao>(relaxed = true)
            val inserted = slot<DocumentEntity>()
            coEvery { documentDao.getDocumentById("doc-fail-1") } returns existingDocument("doc-fail-1", "block-A")
            coEvery { documentDao.insertDocument(capture(inserted)) } returns Unit
            val repository = repository(documentDao)

            repository.persistAllMessageData(
                messageId = MessageId("msg-artifact-fail"),
                modelType = ModelType.FAST,
                thinkingStartTime = 0L,
                thinkingEndTime = 0L,
                thinkingDuration = null,
                thinkingRaw = null,
                content = "append_blocks error",
                messageState = MessageState.COMPLETE,
                pipelineStep = null,
                webSearchSources = emptyList(),
                artifacts = listOf(ArtifactGenerationRequest(artifactId = "doc-fail-1", title = "Report", content = "")),
                generatedMediaUris = emptyList(),
                generatedMediaAssetIds = emptyList(),
                generatedMediaTypes = emptyList(),
                generatedMediaPrompts = emptyList(),
                transcriptSegments = emptyList(),
            )

            assertTrue(inserted.captured.id == "doc-fail-1", "FAAL-AR-04 inserted id")
            assertTrue(inserted.captured.json.contains("block-A"), "FAAL-AR-04 retained json")
        }

    @Test
    fun `FAAL-MD-03 empty insert forbidden over non empty document`() =
        runTest {
            val documentDao = mockk<DocumentDao>(relaxed = true)
            val existingJson = """{"version":"1.0","blocks":[{"type":"paragraph","text":"${"x".repeat(60)}"}]}"""
            coEvery { documentDao.getDocumentById("doc-md-3") } returns existingDocument("doc-md-3", existingJson)
            val repository = repository(documentDao)

            repository.persistAllMessageData(
                messageId = MessageId("msg-artifact-md"),
                modelType = ModelType.FAST,
                thinkingStartTime = 0L,
                thinkingEndTime = 0L,
                thinkingDuration = null,
                thinkingRaw = null,
                content = "assistant",
                messageState = MessageState.COMPLETE,
                pipelineStep = null,
                webSearchSources = emptyList(),
                artifacts = listOf(ArtifactGenerationRequest(artifactId = "doc-md-3", title = "Report", content = "")),
                generatedMediaUris = emptyList(),
                generatedMediaAssetIds = emptyList(),
                generatedMediaTypes = emptyList(),
                generatedMediaPrompts = emptyList(),
                transcriptSegments = emptyList(),
            )

            coVerify(exactly = 0) { documentDao.insertDocument(any()) }
        }

    private fun repository(documentDao: DocumentDao): ChatRepositoryImpl {
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
            documentDao = documentDao,
            transactionProvider = transactionProvider,
            cleanupDao = mockk(relaxed = true),
            healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
            readRepairApplier = mockk<SensitiveRoomReadRepairApplier>(relaxed = true),
        )
    }

    private fun existingDocument(
        id: String,
        json: String,
    ): DocumentEntity =
        DocumentEntity(
            id = id,
            messageId = MessageId("msg-artifact-1"),
            json = encrypted(json),
            title = encrypted("Report"),
            author = null,
            targetFormat = null,
            pendingSync = 0,
        )

    private fun encrypted(value: String): String = "${CryptoAtRestEncryptionService.ENVELOPE_PREFIX}$value"
}
