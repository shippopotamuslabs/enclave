package com.browntowndev.enclave.core.data.artifact

import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.SearchArtifactParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.core.data.FakeDocumentDao
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class SearchArtifactToolExecutorTest {
    private val enc = CryptoAtRestEncryptionService.ENVELOPE_PREFIX

    private val documentDao = FakeDocumentDao()
    private val embeddingEngine = FakeEmbeddingEngine()
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
            embeddingEngine.embeddingsByText["find greeting"] = floatArrayOf(0.1f, 0.2f)
            documentDao.searchSimilarBlocksResult = listOf("artifact-1_0")
            documentDao.documentsById["artifact-1"] =
                DocumentEntity(
                    id = "artifact-1",
                    messageId = MessageId("assistant-1"),
                    json = """$enc{"version":"1.0","metadata":{"title":"T"},"blocks":[{"type":"paragraph","text":"hi"}]}""",
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

    @Test
    fun `execute rejects artifact id with sql injection characters`() =
        runTest {
            val result =
                executor.execute(
                    ToolCallRequest(
                        toolName = ToolDefinition.SEARCH_CURRENT_ARTIFACT.name,
                        argumentsJson =
                            ToolDefinition.SEARCH_CURRENT_ARTIFACT.encodeArguments<SearchArtifactParams>(
                                SearchArtifactParams(
                                    artifactId = "' OR '1'='1",
                                    query = "needle",
                                ),
                            ),
                        provider = "test",
                        modelType = ModelType.FAST,
                    ),
                )

            assertTrue(result.resultJson.contains("\"status\":\"error\""))
            assertTrue(result.resultJson.contains("Invalid artifactId"))
            assertTrue(documentDao.searchSimilarBlocksQueries.isEmpty())
            assertTrue(embeddingEngine.queries.isEmpty())
        }

    @Test
    fun `execute uses parameterized sql for vector search`() =
        runTest {
            embeddingEngine.embeddingsByText["find greeting"] = floatArrayOf(0.1f, 0.2f)
            documentDao.searchSimilarBlocksResult = emptyList()

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

            val sql = documentDao.searchSimilarBlocksQueries.single().sql
            assertTrue(sql.contains("id LIKE ?"))
            assertTrue(sql.contains("vector MATCH ?"))
            assertFalse(sql.contains("artifact-1"))
            assertFalse(sql.contains("[0.1,0.2]"))
        }
}
