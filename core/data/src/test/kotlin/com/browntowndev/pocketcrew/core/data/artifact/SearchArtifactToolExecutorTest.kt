package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.SearchArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class SearchArtifactToolExecutorTest {
    private val enc = CryptoAtRestEncryptionService.ENVELOPE_PREFIX

    private val documentDao = mockk<DocumentDao>()
    private val embeddingEngine = mockk<EmbeddingEnginePort>()
    private val encFake = FakeLocalDataEncryptionService()
    private val executor =
        SearchArtifactToolExecutor(
            documentDao = documentDao,
            embeddingEngine = embeddingEngine,
            localEncryption = encFake,
        )

    @Test
    fun `execute decrypts stored document JSON before searching blocks`() =
        runTest {
            coEvery { embeddingEngine.getEmbedding("find greeting") } returns floatArrayOf(0.1f, 0.2f)
            coEvery { documentDao.searchSimilarBlocks(any()) } returns listOf("artifact-1_0")
            coEvery { documentDao.getDocumentById("artifact-1") } returns
                DocumentEntity(
                    id = "artifact-1",
                    messageId = MessageId("assistant-1"),
                    json = """${enc}{"version":"1.0","metadata":{"title":"T"},"blocks":[{"type":"paragraph","text":"hi"}]}""",
                    title = "${enc}T",
                )

            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                        argumentsJson =
                            ToolDefinition.SEARCH_CURRENT_ARTIFACT.encodeArguments<SearchArtifactParams>(
                                SearchArtifactParams(
                                    artifactId = "artifact-1",
                                    query = "find greeting",
                                ),
                            ),
                        provider = "test",
                        modelType = ModelType.FAST,
                    ),
                )

            assertTrue(result.resultJson.contains("\"status\":\"success\""))
            assertTrue(result.resultJson.contains("\"text\":\"hi\""))
            assertEquals(
                "DocumentEntity|json|artifact-1",
                encFake.lastDecryptAad!!.toString(StandardCharsets.UTF_8),
            )
        }
}
