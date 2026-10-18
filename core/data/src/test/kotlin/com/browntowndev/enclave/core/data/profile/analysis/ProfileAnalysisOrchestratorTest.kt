package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.FakeProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.FakeProfileCorpusDao
import com.browntowndev.enclave.core.data.FakeProfileFindingsDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusConversationEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusMessageEntity
import com.browntowndev.enclave.core.data.local.ProfileFindingsEntity
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileAnalysisOrchestratorTest {
    private val inferencePort = FakeProfileAnalysisInferencePort()
    private val corpusDao = FakeProfileCorpusDao()
    private val findingsDao = FakeProfileFindingsDao()
    private val toolExecutorFactory = ProfileAnalysisToolExecutorFactory(corpusDao, findingsDao)
    private val runDao = FakeProfileAnalysisRunDao()
    private val memoriesRepository = FakeMemoriesRepository()
    private val loggingPort = FakeLoggingPort()
    private lateinit var orchestrator: ProfileAnalysisOrchestrator

    private val runId = "run-001"
    private val configId = ApiModelConfigurationId("config-abc")

    private val testConvId = "conv-test-001"
    private val testConversation =
        ProfileCorpusConversationEntity(
            id = testConvId,
            corpusRunId = runId,
            externalId = "ext-001",
            title = "Test Conversation",
            provider = "XAI",
            createdAtMs = null,
            userTokenCount = 10,
            assistantTokenCount = 20,
            messageCount = 2,
            userMessageCount = 1,
            embeddingSeedText = "test",
            signalScore = 0.5f,
            clusterId = null,
            selectedForAnalysis = true,
        )
    private val testMessages =
        listOf(
            ProfileCorpusMessageEntity(
                conversationId = testConvId,
                corpusRunId = runId,
                role = "USER",
                content = "Hello there",
                createdAtMs = null,
                orderIndex = 0,
            ),
            ProfileCorpusMessageEntity(
                conversationId = testConvId,
                corpusRunId = runId,
                role = "ASSISTANT",
                content = "Hi!",
                createdAtMs = null,
                orderIndex = 1,
            ),
        )

    private val stopResult =
        AgentModelTurnResult(
            assistantText = "Analysis complete for this pass.",
            toolCalls = emptyList(),
            finishReason = AgentFinishReason.Stop,
        )

    // Happy-path: model calls record_finding then stops (2 turns per pass).
    private val recordFindingResult =
        AgentModelTurnResult(
            assistantText = null,
            toolCalls =
                listOf(
                    AgentRawToolCall(
                        id = "rf1",
                        name = "record_finding",
                        argumentsJson = """{"category":"EVIDENCE","content":"test finding"}""",
                    ),
                ),
            finishReason = AgentFinishReason.ToolUse,
        )

    @BeforeEach
    fun setup() {
        orchestrator =
            ProfileAnalysisOrchestrator(
                inferencePort = inferencePort,
                toolExecutorFactory = toolExecutorFactory,
                corpusDao = corpusDao,
                findingsDao = findingsDao,
                runDao = runDao,
                memoriesRepository = memoriesRepository,
                loggingPort = loggingPort,
            )
        corpusDao.selectedConversationsResult = listOf(testConversation)
        corpusDao.messagesForConversation[testConvId] = testMessages
        // Large window → all conversations fit in one extraction batch.
        inferencePort.contextWindowTokensResult = 200_000
        // Default: record_finding then stop (2 turns per pass / per batch).
        inferencePort.completeTurnHandler = { callIndex ->
            if (callIndex % 2 == 0) recordFindingResult else stopResult
        }
        findingsDao.getByCategoryResults["SYNTHESIS"] =
            listOf(
                ProfileFindingsEntity(corpusRunId = runId, category = "SYNTHESIS", content = "Final profile text"),
            )
    }

    @Test
    fun `PP-110 orchestrator runs all 4 passes in order`() =
        runTest {
            orchestrator.runAnalysis(runId, configId)

            val passStatuses = runDao.upsertCalls.map { it.status }
            val passCheckpoints = passStatuses.filter { it.startsWith("PASS_") }
            assertEquals(4, passCheckpoints.size)
            assertEquals("PASS_1", passCheckpoints[0])
            assertEquals("PASS_4", passCheckpoints[3])
            assertEquals("COMPLETE", passStatuses.last())
        }

    @Test
    fun `PP-111 orchestrator calls inference for each pass`() =
        runTest {
            orchestrator.runAnalysis(runId, configId)
            // Extraction: 1 batch × 2 turns; clustering/synthesis/critique: 2 turns each = 2+2+2+2=8
            assertEquals(8, inferencePort.completeTurnCalls.size)
            assertTrue(inferencePort.completeTurnCalls.all { it.configId == configId })
        }

    @Test
    fun `PP-112 orchestrator saves SYNTHESIS finding as PSYCH_PROFILE memory`() =
        runTest {
            orchestrator.runAnalysis(runId, configId)
            assertTrue(
                memoriesRepository.insertCalls.any {
                    it.first == MemoryCategory.PSYCH_PROFILE && it.second == "Final profile text"
                },
            )
        }

    @Test
    fun `PP-117 orchestrator updates existing PSYCH_PROFILE memory instead of inserting duplicate`() =
        runTest {
            val existing = Memory(id = "existing-mem-id", category = MemoryCategory.PSYCH_PROFILE, content = "Old content")
            memoriesRepository.seed(listOf(existing))

            orchestrator.runAnalysis(runId, configId)

            assertTrue(
                memoriesRepository.updateCalls.any {
                    it == Triple("existing-mem-id", "Final profile text", MemoryCategory.PSYCH_PROFILE)
                },
            )
            assertTrue(memoriesRepository.insertCalls.isEmpty())
        }

    @Test
    fun `PP-113 orchestrator skips completed passes on resume`() =
        runTest {
            // Resume from pass index 2 — extraction (0) and clustering (1) already done
            runDao.byRunIdResult =
                ProfileAnalysisRunEntity(
                    corpusRunId = runId,
                    configId = configId.value,
                    status = "PASS_3",
                    currentPassIndex = 2,
                )

            orchestrator.runAnalysis(runId, configId)

            // Synthesis (2) + critique (3): 2 turns each = 4
            assertEquals(4, inferencePort.completeTurnCalls.size)
            assertTrue(inferencePort.completeTurnCalls.all { it.configId == configId })
        }

    @Test
    fun `PP-114 orchestrator does nothing if run is already COMPLETE`() =
        runTest {
            runDao.byRunIdResult =
                ProfileAnalysisRunEntity(
                    corpusRunId = runId,
                    configId = configId.value,
                    status = "COMPLETE",
                    currentPassIndex = 4,
                )

            orchestrator.runAnalysis(runId, configId)

            assertTrue(inferencePort.completeTurnCalls.isEmpty())
        }

    @Test
    fun `PP-115 orchestrator executes record_finding tool calls returned by model`() =
        runTest {
            var callCount = 0
            inferencePort.completeTurnHandler = {
                if (callCount++ == 0) recordFindingResult else stopResult
            }

            orchestrator.runAnalysis(runId, configId)

            // The record_finding tool call is executed by the real tool executor
            // over the findings DAO: its observable effect is the persisted finding.
            assertTrue(
                findingsDao.insertCalls.any { it.category == "EVIDENCE" && it.content == "test finding" },
            )
        }

    @Test
    fun `PP-118 agent loop nudges model when it stops without calling record_finding`() =
        runTest {
            // Model always stops without calling record_finding.
            // Extraction: requireRecordFinding=false → 1 call per conversation (no nudge).
            // Clustering/synthesis/critique: nudge up to MAX_NUDGES=3 → 4 calls each.
            inferencePort.completeTurnHandler = { stopResult }

            orchestrator.runAnalysis(runId, configId)

            // 1 (extraction conv) + 3×4 (agent passes) = 13
            assertEquals(13, inferencePort.completeTurnCalls.size)
            assertTrue(inferencePort.completeTurnCalls.all { it.configId == configId })
        }

    @Test
    fun `PP-116 progress callback fires once per pass`() =
        runTest {
            val progress = mutableListOf<Pair<Int, Int>>()
            orchestrator.runAnalysis(runId, configId, onProgress = { c, t -> progress.add(c to t) })
            assertEquals(4 + 1, progress.size)
            assertEquals(1 to 4, progress.first())
            assertEquals(4 to 4, progress[progress.size - 2])
        }

    @Test
    fun `PP-119 extraction creates one batch per overflow when context window is tiny`() =
        runTest {
            // Three conversations with a context window so small each conv gets its own batch.
            val conv2Id = "conv-002"
            val conv3Id = "conv-003"
            val conversations =
                listOf(
                    testConversation,
                    testConversation.copy(id = conv2Id, externalId = "ext-002", title = "Conv 2"),
                    testConversation.copy(id = conv3Id, externalId = "ext-003", title = "Conv 3"),
                )
            corpusDao.selectedConversationsResult = conversations
            corpusDao.messagesForConversation[conv2Id] = testMessages
            corpusDao.messagesForConversation[conv3Id] = testMessages
            // Budget = (2 * 0.5).toInt() = 1 token — forces one conversation per batch.
            inferencePort.contextWindowTokensResult = 2

            var turnCount = 0
            inferencePort.completeTurnHandler = {
                if (turnCount++ % 2 == 0) recordFindingResult else stopResult
            }

            orchestrator.runAnalysis(runId, configId)

            // Extraction: 3 batches × 2 turns = 6; clustering/synthesis/critique: 2 turns each = 6
            assertEquals(12, inferencePort.completeTurnCalls.size)
            assertTrue(inferencePort.completeTurnCalls.all { it.configId == configId })
        }

    @Test
    fun `PP-120 extraction is skipped gracefully when corpus is empty`() =
        runTest {
            corpusDao.selectedConversationsResult = emptyList()

            orchestrator.runAnalysis(runId, configId)

            // No extraction turns; clustering/synthesis/critique: 2 turns each = 6
            assertEquals(6, inferencePort.completeTurnCalls.size)
            assertTrue(inferencePort.completeTurnCalls.all { it.configId == configId })
        }

    @Test
    fun `PP-121 extraction batches use EXTRACTION_BATCH system prompt`() =
        runTest {
            inferencePort.completeTurnHandler = { stopResult }

            orchestrator.runAnalysis(runId, configId)

            // The first completeTurn call (extraction batch) must use EXTRACTION_BATCH prompt.
            assertEquals(
                ProfileAnalysisPrompts.EXTRACTION_BATCH,
                inferencePort.completeTurnCalls.first().systemPrompt,
            )
        }
}

private class StubMemoriesRepository : MemoriesRepository {
    override fun getAllMemoriesFlow(): Flow<List<Memory>> = throw NotImplementedError()

    override suspend fun getCoreMemories(): List<Memory> = emptyList()

    override suspend fun insertMemory(
        category: MemoryCategory,
        content: String,
    ): Memory = Memory(category = category, content = content)

    override suspend fun updateMemory(
        id: String,
        content: String,
        category: MemoryCategory?,
    ) = Unit

    override suspend fun getMemoryById(id: String): Memory? = null

    override suspend fun deleteMemory(id: String) = Unit

    override suspend fun deleteAllMemories() = Unit

    override suspend fun searchMemories(
        queryVector: FloatArray,
        limit: Int,
        maxDistance: Float?,
        excludeCategories: Set<MemoryCategory>,
    ): List<Memory> = emptyList()
}
