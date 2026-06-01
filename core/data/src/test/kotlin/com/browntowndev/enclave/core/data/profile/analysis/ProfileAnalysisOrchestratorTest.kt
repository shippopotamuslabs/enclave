package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusConversationEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusMessageEntity
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsEntity
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileAnalysisOrchestratorTest {
    private val inferencePort = mockk<ProfileAnalysisInferencePort>()
    private val toolExecutorFactory = mockk<ProfileAnalysisToolExecutorFactory>()
    private val toolExecutor = mockk<ProfileAnalysisToolExecutor>()
    private val corpusDao = mockk<ProfileCorpusDao>()
    private val findingsDao = mockk<ProfileFindingsDao>()
    private val runDao = mockk<ProfileAnalysisRunDao>()
    private val memoriesRepository = mockk<MemoriesRepository>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private lateinit var orchestrator: ProfileAnalysisOrchestrator

    private val runId = "run-001"
    private val configId = ApiModelConfigurationId("config-abc")

    private val testConvId = "conv-test-001"
    private val testConversation = ProfileCorpusConversationEntity(
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
    private val testMessages = listOf(
        ProfileCorpusMessageEntity(
            conversationId = testConvId, corpusRunId = runId,
            role = "USER", content = "Hello there", createdAtMs = null, orderIndex = 0,
        ),
        ProfileCorpusMessageEntity(
            conversationId = testConvId, corpusRunId = runId,
            role = "ASSISTANT", content = "Hi!", createdAtMs = null, orderIndex = 1,
        ),
    )

    private val stopResult = AgentModelTurnResult(
        assistantText = "Analysis complete for this pass.",
        toolCalls = emptyList(),
        finishReason = AgentFinishReason.Stop,
    )

    // Happy-path: model calls record_finding then stops (2 turns per pass).
    private val recordFindingResult = AgentModelTurnResult(
        assistantText = null,
        toolCalls = listOf(AgentRawToolCall(id = "rf1", name = "record_finding", argumentsJson = """{"category":"EVIDENCE","content":"test finding"}""")),
        finishReason = AgentFinishReason.ToolUse,
    )

    @BeforeEach
    fun setup() {
        orchestrator = ProfileAnalysisOrchestrator(
            inferencePort = inferencePort,
            toolExecutorFactory = toolExecutorFactory,
            corpusDao = corpusDao,
            findingsDao = findingsDao,
            runDao = runDao,
            memoriesRepository = memoriesRepository,
            loggingPort = loggingPort,
        )
        coEvery { toolExecutorFactory.create(runId) } returns toolExecutor
        coEvery { runDao.getByRunId(runId) } returns null
        coEvery { runDao.upsert(any()) } returns Unit
        coEvery { corpusDao.getSelectedConversations(runId) } returns listOf(testConversation)
        coEvery { corpusDao.getMessagesForConversation(testConvId) } returns testMessages
        // Large window → all conversations fit in one extraction batch.
        coEvery { inferencePort.getContextWindowTokens(configId) } returns 200_000
        // Default: record_finding then stop (2 turns per pass / per batch).
        var defaultTurnCount = 0
        coEvery { inferencePort.completeTurn(any(), any(), any(), any(), any()) } answers {
            if (defaultTurnCount++ % 2 == 0) recordFindingResult else stopResult
        }
        coEvery { toolExecutor.execute(any()) } returns
            com.browntowndev.enclave.domain.model.inference.ToolExecutionResult("record_finding", """{"status":"ok"}""")
        coEvery { findingsDao.getByCategory(runId, "SYNTHESIS") } returns listOf(
            ProfileFindingsEntity(corpusRunId = runId, category = "SYNTHESIS", content = "Final profile text"),
        )
        coEvery { memoriesRepository.getCoreMemories() } returns emptyList()
        coEvery { memoriesRepository.insertMemory(any(), any()) } returns
            Memory(category = MemoryCategory.PSYCH_PROFILE, content = "Final profile text")
        coEvery { memoriesRepository.updateMemory(any(), any(), any()) } returns Unit
    }

    @Test
    fun `PP-110 orchestrator runs all 4 passes in order`() = runTest {
        val passStatuses = mutableListOf<String>()
        val runEntitySlot = slot<ProfileAnalysisRunEntity>()
        coEvery { runDao.upsert(capture(runEntitySlot)) } answers {
            passStatuses.add(runEntitySlot.captured.status)
        }

        orchestrator.runAnalysis(runId, configId)

        val passCheckpoints = passStatuses.filter { it.startsWith("PASS_") }
        assertEquals(4, passCheckpoints.size)
        assertEquals("PASS_1", passCheckpoints[0])
        assertEquals("PASS_4", passCheckpoints[3])
        assertEquals("COMPLETE", passStatuses.last())
    }

    @Test
    fun `PP-111 orchestrator calls inference for each pass`() = runTest {
        orchestrator.runAnalysis(runId, configId)
        // Extraction: 1 batch × 2 turns; clustering/synthesis/critique: 2 turns each = 2+2+2+2=8
        coVerify(exactly = 8) { inferencePort.completeTurn(configId, any(), any(), any(), any()) }
    }

    @Test
    fun `PP-112 orchestrator saves SYNTHESIS finding as PSYCH_PROFILE memory`() = runTest {
        orchestrator.runAnalysis(runId, configId)
        coVerify { memoriesRepository.insertMemory(MemoryCategory.PSYCH_PROFILE, "Final profile text") }
    }

    @Test
    fun `PP-117 orchestrator updates existing PSYCH_PROFILE memory instead of inserting duplicate`() = runTest {
        val existing = Memory(id = "existing-mem-id", category = MemoryCategory.PSYCH_PROFILE, content = "Old content")
        coEvery { memoriesRepository.getCoreMemories() } returns listOf(existing)

        orchestrator.runAnalysis(runId, configId)

        coVerify { memoriesRepository.updateMemory("existing-mem-id", "Final profile text", MemoryCategory.PSYCH_PROFILE) }
        coVerify(exactly = 0) { memoriesRepository.insertMemory(any(), any()) }
    }

    @Test
    fun `PP-113 orchestrator skips completed passes on resume`() = runTest {
        // Resume from pass index 2 — extraction (0) and clustering (1) already done
        coEvery { runDao.getByRunId(runId) } returns ProfileAnalysisRunEntity(
            corpusRunId = runId,
            configId = configId.value,
            status = "PASS_3",
            currentPassIndex = 2,
        )

        orchestrator.runAnalysis(runId, configId)

        // Synthesis (2) + critique (3): 2 turns each = 4
        coVerify(exactly = 4) { inferencePort.completeTurn(configId, any(), any(), any(), any()) }
    }

    @Test
    fun `PP-114 orchestrator does nothing if run is already COMPLETE`() = runTest {
        coEvery { runDao.getByRunId(runId) } returns ProfileAnalysisRunEntity(
            corpusRunId = runId,
            configId = configId.value,
            status = "COMPLETE",
            currentPassIndex = 4,
        )

        orchestrator.runAnalysis(runId, configId)

        coVerify(exactly = 0) { inferencePort.completeTurn(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `PP-115 orchestrator executes record_finding tool calls returned by model`() = runTest {
        var callCount = 0
        coEvery { inferencePort.completeTurn(any(), any(), any(), any(), any()) } answers {
            if (callCount++ == 0) recordFindingResult else stopResult
        }

        orchestrator.runAnalysis(runId, configId)

        coVerify(atLeast = 1) { toolExecutor.execute(match { it.toolName == "record_finding" }) }
    }

    @Test
    fun `PP-118 agent loop nudges model when it stops without calling record_finding`() = runTest {
        // Model always stops without calling record_finding.
        // Extraction: requireRecordFinding=false → 1 call per conversation (no nudge).
        // Clustering/synthesis/critique: nudge up to MAX_NUDGES=3 → 4 calls each.
        coEvery { inferencePort.completeTurn(any(), any(), any(), any(), any()) } returns stopResult

        orchestrator.runAnalysis(runId, configId)

        // 1 (extraction conv) + 3×4 (agent passes) = 13
        coVerify(exactly = 13) { inferencePort.completeTurn(configId, any(), any(), any(), any()) }
    }

    @Test
    fun `PP-116 progress callback fires once per pass`() = runTest {
        val progress = mutableListOf<Pair<Int, Int>>()
        orchestrator.runAnalysis(runId, configId, onProgress = { c, t -> progress.add(c to t) })
        assertEquals(4 + 1, progress.size)
        assertEquals(1 to 4, progress.first())
        assertEquals(4 to 4, progress[progress.size - 2])
    }

    @Test
    fun `PP-119 extraction creates one batch per overflow when context window is tiny`() = runTest {
        // Three conversations with a context window so small each conv gets its own batch.
        val conv2Id = "conv-002"
        val conv3Id = "conv-003"
        val conversations = listOf(
            testConversation,
            testConversation.copy(id = conv2Id, externalId = "ext-002", title = "Conv 2"),
            testConversation.copy(id = conv3Id, externalId = "ext-003", title = "Conv 3"),
        )
        coEvery { corpusDao.getSelectedConversations(runId) } returns conversations
        coEvery { corpusDao.getMessagesForConversation(conv2Id) } returns testMessages
        coEvery { corpusDao.getMessagesForConversation(conv3Id) } returns testMessages
        // Budget = (2 * 0.5).toInt() = 1 token — forces one conversation per batch.
        coEvery { inferencePort.getContextWindowTokens(configId) } returns 2

        var turnCount = 0
        coEvery { inferencePort.completeTurn(any(), any(), any(), any(), any()) } answers {
            if (turnCount++ % 2 == 0) recordFindingResult else stopResult
        }

        orchestrator.runAnalysis(runId, configId)

        // Extraction: 3 batches × 2 turns = 6; clustering/synthesis/critique: 2 turns each = 6
        coVerify(exactly = 12) { inferencePort.completeTurn(configId, any(), any(), any(), any()) }
    }

    @Test
    fun `PP-120 extraction is skipped gracefully when corpus is empty`() = runTest {
        coEvery { corpusDao.getSelectedConversations(runId) } returns emptyList()

        orchestrator.runAnalysis(runId, configId)

        // No extraction turns; clustering/synthesis/critique: 2 turns each = 6
        coVerify(exactly = 6) { inferencePort.completeTurn(configId, any(), any(), any(), any()) }
    }

    @Test
    fun `PP-121 extraction batches use EXTRACTION_BATCH system prompt`() = runTest {
        val systemPromptSlot = mutableListOf<String>()
        coEvery { inferencePort.completeTurn(any(), any(), capture(systemPromptSlot), any(), any()) } answers {
            stopResult
        }

        orchestrator.runAnalysis(runId, configId)

        // The first completeTurn call (extraction batch) must use EXTRACTION_BATCH prompt.
        assertEquals(ProfileAnalysisPrompts.EXTRACTION_BATCH, systemPromptSlot.first())
    }
}

private class StubMemoriesRepository : MemoriesRepository {
    override fun getAllMemoriesFlow(): Flow<List<Memory>> = throw NotImplementedError()
    override suspend fun getCoreMemories(): List<Memory> = emptyList()
    override suspend fun insertMemory(category: MemoryCategory, content: String): Memory =
        Memory(category = category, content = content)
    override suspend fun updateMemory(id: String, content: String, category: MemoryCategory?) = Unit
    override suspend fun getMemoryById(id: String): Memory? = null
    override suspend fun deleteMemory(id: String) = Unit
    override suspend fun deleteAllMemories() = Unit
    override suspend fun searchMemories(queryVector: FloatArray, limit: Int, maxDistance: Float?, excludeCategories: Set<MemoryCategory>): List<Memory> = emptyList()
}
