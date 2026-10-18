package com.browntowndev.enclave.core.data.artifact

import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.core.data.FakeDocumentDao
import com.browntowndev.enclave.core.data.FakeEmbeddingDao
import com.browntowndev.enclave.core.testing.FakeDocumentValidationPort
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class ArtifactToolExecutorImplTest {
    private val enc = CryptoAtRestEncryptionService.ENVELOPE_PREFIX

    private val documentValidation = FakeDocumentValidationPort()

    @Test
    fun `execute returns success for start_artifact with title only`() =
        runTest {
            val docDao = FakeDocumentDao()
            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = FakeEmbeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = documentValidation,
                    localEncryption = FakeLocalDataEncryptionService(),
                    externalScope = TestScope(),
                )
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    assistantMessageId = MessageId("assistant-123"),
                    toolName = ToolDefinition.START_ARTIFACT.name,
                    argumentsJson = """{"title":"Report"}""",
                    provider = "test",
                    modelType = ModelType.FAST,
                )
            val result = cut.execute(request)
            assertTrue(result.resultJson.contains("\"status\":\"success\""))
            assertTrue(result.resultJson.contains("\"artifactId\""))
            assertTrue(result.resultJson.contains("isComplete"))
            val capturedDocument = docDao.insertedDocuments.single()
            assertEquals("${enc}Report", capturedDocument.title)
            assertTrue(capturedDocument.json.startsWith(enc))
            assertEquals(0, capturedDocument.pendingSync)
        }

    @Test
    fun `execute rejects start_artifact with unknown keys such as targetFormat`() =
        runTest {
            val validation = FakeDocumentValidationPort()
            validation.validateStartArtifactArgsResult =
                Result.failure(IllegalArgumentException("unknown field targetFormat"))
            val cut =
                ArtifactToolExecutor(
                    documentDao = FakeDocumentDao(),
                    embeddingDao = FakeEmbeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = validation,
                    localEncryption = FakeLocalDataEncryptionService(),
                    externalScope = TestScope(),
                )
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    assistantMessageId = MessageId("assistant-123"),
                    toolName = ToolDefinition.START_ARTIFACT.name,
                    argumentsJson = """{"title":"Report","targetFormat":"WORD"}""",
                    provider = "test",
                    modelType = ModelType.FAST,
                )
            val result = cut.execute(request)
            assertTrue(result.resultJson.contains("TOOL_ARGUMENTS_VIOLATION"))
        }

    @Test
    fun `appendBlocks returns schema violation and skips persist when validation fails`() =
        runTest {
            val docDao = FakeDocumentDao()
            val validation = FakeDocumentValidationPort()
            docDao.documentsById["aid"] =
                DocumentEntity(
                    id = "aid",
                    messageId = MessageId("assistant-123"),
                    title = "${enc}T",
                    author = null,
                    targetFormat = MimeType.PDF,
                    json = """$enc{"version":"1.0","metadata":{"title":"T"},"blocks":[]}""",
                )
            validation.validateResult =
                Result.failure(
                    IllegalArgumentException("heading block missing required field \"level\""),
                )

            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = FakeEmbeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = validation,
                    localEncryption = FakeLocalDataEncryptionService(),
                    externalScope = TestScope(),
                )

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    assistantMessageId = MessageId("assistant-123"),
                    toolName = ToolDefinition.APPEND_BLOCKS.name,
                    argumentsJson = """{"artifactId":"aid","blocks":[{"type":"heading","text":"x"}],"isComplete":false}""",
                    provider = "test",
                    modelType = ModelType.FAST,
                )
            val result = cut.execute(request)
            assertTrue(result.resultJson.contains("DOCUMENT_SCHEMA_VIOLATION"))
            assertTrue(result.resultJson.contains("validationDetails"))
            assertTrue(result.resultJson.contains("heading block missing"))
            assertTrue(docDao.updatedDocuments.isEmpty())
        }

    @Test
    fun `appendBlocks incomplete includes isComplete reminder in message`() =
        runTest {
            val docDao = FakeDocumentDao()
            docDao.documentsById["aid"] =
                DocumentEntity(
                    id = "aid",
                    messageId = MessageId("assistant-123"),
                    title = "${enc}T",
                    author = null,
                    targetFormat = MimeType.PDF,
                    json = """$enc{"version":"1.0","metadata":{"title":"T"},"blocks":[]}""",
                )
            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = FakeEmbeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = documentValidation,
                    localEncryption = FakeLocalDataEncryptionService(),
                    externalScope = TestScope(),
                )

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    assistantMessageId = MessageId("assistant-123"),
                    toolName = ToolDefinition.APPEND_BLOCKS.name,
                    argumentsJson =
                        """{"artifactId":"aid","blocks":[{"type":"paragraph","text":"hi"}],"isComplete":false}""",
                    provider = "test",
                    modelType = ModelType.FAST,
                )
            val result = cut.execute(request)
            assertTrue(result.resultJson.contains("isComplete"), "incomplete result must mention isComplete")
            // Regression: old message instructed a separate empty-blocks finalization call, which
            // caused model fragmentation. The new message must not encourage that pattern.
            assertTrue(
                !result.resultJson.contains("blocks empty"),
                "must not tell model to make a separate empty-blocks call",
            )
            assertTrue(
                result.resultJson.contains("same call"),
                "must instruct final blocks + isComplete in the same call",
            )
            val updatedDocument = docDao.updatedDocuments.single()
            assertEquals(0, updatedDocument.pendingSync)
            assertTrue(updatedDocument.json.startsWith(enc))
        }

    @Test
    fun `appendBlocks complete marks encrypted document pending sync`() =
        runTest {
            val docDao = FakeDocumentDao()
            docDao.documentsById["aid"] =
                DocumentEntity(
                    id = "aid",
                    messageId = MessageId("assistant-123"),
                    title = "${enc}T",
                    author = null,
                    targetFormat = MimeType.PDF,
                    json = """$enc{"version":"1.0","metadata":{"title":"T"},"blocks":[]}""",
                )
            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = FakeEmbeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = documentValidation,
                    localEncryption = FakeLocalDataEncryptionService(),
                    externalScope = TestScope(),
                )

            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    assistantMessageId = MessageId("assistant-123"),
                    toolName = ToolDefinition.APPEND_BLOCKS.name,
                    argumentsJson =
                        """{"artifactId":"aid","blocks":[{"type":"paragraph","text":"hi"}],"isComplete":true}""",
                    provider = "test",
                    modelType = ModelType.FAST,
                )
            val result = cut.execute(request)
            assertTrue(result.resultJson.contains("\"isComplete\":true"))
            val updatedDocument = docDao.updatedDocuments.single()
            assertEquals(1, updatedDocument.pendingSync)
            assertTrue(updatedDocument.json.startsWith(enc))
        }

    @Test
    fun `FAAL-RC-04 canonicalized append_blocks args pass docgen validation`() =
        runTest {
            val docDao = FakeDocumentDao()
            docDao.documentsById["art-exec-1"] =
                DocumentEntity(
                    id = "art-exec-1",
                    messageId = MessageId("assistant-123"),
                    title = "${enc}T",
                    author = null,
                    targetFormat = MimeType.PDF,
                    json = """$enc{"version":"1.0","metadata":{"title":"T"},"blocks":[]}""",
                )
            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = FakeEmbeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = documentValidation,
                    localEncryption = FakeLocalDataEncryptionService(),
                    externalScope = TestScope(),
                )
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    assistantMessageId = MessageId("assistant-123"),
                    toolName = ToolDefinition.APPEND_BLOCKS.name,
                    argumentsJson =
                        """{"artifactId":"art-exec-1","blocks":[{"type":"paragraph","text":"Valid"}],"isComplete":false}""",
                    provider = "test",
                    modelType = ModelType.FAST,
                )

            val result = cut.execute(request)

            assertTrue(result.resultJson.contains("\"status\":\"success\""), "FAAL-RC-04 status")
        }

    @Test
    fun `document json encryption uses DocumentEntity json AAD binding`() =
        runTest {
            val docDao = FakeDocumentDao()
            val encSvc = FakeLocalDataEncryptionService()
            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = FakeEmbeddingDao(),
                    embeddingEngine = FakeEmbeddingEngine(),
                    documentValidation = documentValidation,
                    localEncryption = encSvc,
                    externalScope = TestScope(),
                    newArtifactDocumentId = { "doc-7" },
                )
            val request =
                ToolCallRequest(
                    chatId = ChatId("chat-123"),
                    userMessageId = MessageId("msg-123"),
                    assistantMessageId = MessageId("assistant-123"),
                    toolName = ToolDefinition.START_ARTIFACT.name,
                    argumentsJson = """{"title":"Report"}""",
                    provider = "test",
                    modelType = ModelType.FAST,
                )
            cut.execute(request)
            assertEquals(
                "DocumentEntity|json|doc-7",
                encSvc.lastEncryptAad!!.toString(StandardCharsets.UTF_8),
            )
        }
}
