package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.docgen.DocumentValidationPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactToolExecutorImplTest {
    private val documentValidation =
        mockk<DocumentValidationPort> {
            every { validate(any()) } returns Result.success(Unit)
            every { validateStartArtifactArgs(any()) } returns Result.success(Unit)
            every { validateAppendBlocksArgs(any()) } returns Result.success(Unit)
        }

    private val executor =
        ArtifactToolExecutor(
            documentDao = mockk(relaxed = true),
            embeddingDao = mockk(relaxed = true),
            embeddingEngine = mockk(relaxed = true),
            documentValidation = documentValidation,
            localEncryption = FakeLocalDataEncryptionService(),
            externalScope = TestScope(),
        )

    @Test
    fun `execute returns success for start_artifact with title only`() =
        runTest {
            val docDao = mockk<DocumentDao>()
            val capturedDocument = slot<DocumentEntity>()
            coEvery { docDao.insertDocument(capture(capturedDocument)) } returns Unit
            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = mockk(relaxed = true),
                    embeddingEngine = mockk(relaxed = true),
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
            assertEquals("ENC:v1:Report", capturedDocument.captured.title)
            assertTrue(capturedDocument.captured.json.startsWith("ENC:v1:"))
            assertEquals(0, capturedDocument.captured.pendingSync)
        }

    @Test
    fun `execute rejects start_artifact with unknown keys such as targetFormat`() =
        runTest {
            val validation =
                mockk<DocumentValidationPort> {
                    every { validate(any()) } returns Result.success(Unit)
                    every { validateAppendBlocksArgs(any()) } returns Result.success(Unit)
                    every { validateStartArtifactArgs(any()) } returns
                        Result.failure(IllegalArgumentException("unknown field targetFormat"))
                }
            val cut =
                ArtifactToolExecutor(
                    documentDao = mockk(relaxed = true),
                    embeddingDao = mockk(relaxed = true),
                    embeddingEngine = mockk(relaxed = true),
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
            val docDao = mockk<DocumentDao>()
            val validation = mockk<DocumentValidationPort>()
            every { validation.validateAppendBlocksArgs(any()) } returns Result.success(Unit)
            every { validation.validateStartArtifactArgs(any()) } returns Result.success(Unit)
            coEvery { docDao.getDocumentById("aid") } returns
                DocumentEntity(
                    id = "aid",
                    messageId = MessageId("assistant-123"),
                    title = "ENC:v1:T",
                    author = null,
                    targetFormat = MimeType.PDF,
                    json = """ENC:v1:{"version":"1.0","metadata":{"title":"T"},"blocks":[]}""",
                )
            every { validation.validate(any()) } returns
                Result.failure(
                    IllegalArgumentException("heading block missing required field \"level\""),
                )

            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = mockk(relaxed = true),
                    embeddingEngine = mockk(relaxed = true),
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
            coVerify(exactly = 0) { docDao.updateDocument(any()) }
        }

    @Test
    fun `appendBlocks incomplete includes isComplete reminder in message`() =
        runTest {
            val docDao = mockk<DocumentDao>()
            val updatedDocument = slot<DocumentEntity>()
            coEvery { docDao.getDocumentById("aid") } returns
                DocumentEntity(
                    id = "aid",
                    messageId = MessageId("assistant-123"),
                    title = "ENC:v1:T",
                    author = null,
                    targetFormat = MimeType.PDF,
                    json = """ENC:v1:{"version":"1.0","metadata":{"title":"T"},"blocks":[]}""",
                )
            coEvery { docDao.updateDocument(capture(updatedDocument)) } returns Unit

            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = mockk(relaxed = true),
                    embeddingEngine = mockk(relaxed = true),
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
            assertTrue(result.resultJson.contains("REMINDER"))
            assertTrue(result.resultJson.contains("isComplete"))
            assertEquals(0, updatedDocument.captured.pendingSync)
            assertTrue(updatedDocument.captured.json.startsWith("ENC:v1:"))
        }

    @Test
    fun `appendBlocks complete marks encrypted document pending sync`() =
        runTest {
            val docDao = mockk<DocumentDao>()
            val updatedDocument = slot<DocumentEntity>()
            coEvery { docDao.getDocumentById("aid") } returns
                DocumentEntity(
                    id = "aid",
                    messageId = MessageId("assistant-123"),
                    title = "ENC:v1:T",
                    author = null,
                    targetFormat = MimeType.PDF,
                    json = """ENC:v1:{"version":"1.0","metadata":{"title":"T"},"blocks":[]}""",
                )
            coEvery { docDao.updateDocument(capture(updatedDocument)) } returns Unit

            val cut =
                ArtifactToolExecutor(
                    documentDao = docDao,
                    embeddingDao = mockk(relaxed = true),
                    embeddingEngine = mockk(relaxed = true),
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
            assertEquals(1, updatedDocument.captured.pendingSync)
            assertTrue(updatedDocument.captured.json.startsWith("ENC:v1:"))
        }
}
