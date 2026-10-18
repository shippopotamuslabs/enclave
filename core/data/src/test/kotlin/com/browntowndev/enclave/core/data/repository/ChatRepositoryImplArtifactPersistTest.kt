package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.ChatEntity
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.messageDao
import com.browntowndev.enclave.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Date

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatRepositoryImplArtifactPersistTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var documentDao: DocumentDao
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
            documentDao = database.documentDao()
            repository = database.chatRepositoryImpl(RecordingCloudSyncTrigger())

            // Parent chat + message rows required by the documents table's foreign key.
            val chatId = ChatId("chat-artifact")
            database.chatDao().upsert(
                ChatEntity(id = chatId, name = "Chat", created = Date(), lastModified = Date(), pinned = false),
            )
            database.messageDao().upsert(
                MessageEntity(
                    id = MessageId("msg-artifact-1"),
                    chatId = chatId,
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                    content = "assistant",
                    createdAt = 1000L,
                ),
            )
        }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `FAAL-AR-01 blank accumulator does not wipe DAO json`() =
        runTest {
            documentDao.insertDocument(existingDocument("doc-1", "Saved"))

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

            val stored = documentDao.getDocumentById("doc-1")
            assertTrue(stored?.id == "doc-1", "FAAL-AR-01 inserted id")
            assertTrue(stored?.json?.contains("Saved") == true, "FAAL-AR-01 preserved json")
        }

    @Test
    fun `FAAL-AR-04 failed append_blocks persist retains prior document`() =
        runTest {
            documentDao.insertDocument(existingDocument("doc-fail-1", "block-A"))

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

            val stored = documentDao.getDocumentById("doc-fail-1")
            assertTrue(stored?.id == "doc-fail-1", "FAAL-AR-04 inserted id")
            assertTrue(stored?.json?.contains("block-A") == true, "FAAL-AR-04 retained json")
        }

    @Test
    fun `FAAL-MD-03 empty insert forbidden over non empty document`() =
        runTest {
            val existingJson = """{"version":"1.0","blocks":[{"type":"paragraph","text":"${"x".repeat(60)}"}]}"""
            val seeded = existingDocument("doc-md-3", existingJson)
            documentDao.insertDocument(seeded)

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

            // The stored document is byte-for-byte unchanged: no insert overwrote it.
            assertEquals(seeded, documentDao.getDocumentById("doc-md-3"))
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
