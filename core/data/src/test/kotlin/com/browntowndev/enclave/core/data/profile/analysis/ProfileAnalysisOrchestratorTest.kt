package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunEntity
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsEntity
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
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
    private val findingsDao = mockk<ProfileFindingsDao>()
    private val runDao = mockk<ProfileAnalysisRunDao>()
    private val memoriesRepository = mockk<MemoriesRepository>()
    private lateinit var orchestrator: ProfileAnalysisOrchestrator

    private val runId = "run-001"
    private val configId = ApiModelConfigurationId("config-abc")

    private val stopResult = AgentModelTurnResult(
        assistantText = "Analysis complete for this pass.",
        toolCalls = emptyList(),
        finishReason = AgentFinishReason.Stop,
    )

    @BeforeEach
    fun setup() {
        orchestrator = ProfileAnalysisOrchestrator(
            inferencePort = inferencePort,
            toolExecutorFactory = toolExecutorFactory,
            findingsDao = findingsDao,
            runDao = runDao,
            memoriesRepository = memoriesRepository,
        )
        coEvery { toolExecutorFactory.create(runId) } returns toolExecutor
        coEvery { runDao.getByRunId(runId) } returns null
        coEvery { runDao.upsert(any()) } returns Unit
        coEvery { inferencePort.completeTurn(any(), any(), any(), any(), any()) } returns stopResult
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

        // 4 pass checkpoints + 1 COMPLETE
        val passCheckpoints = passStatuses.filter { it.startsWith("PASS_") }
        assertEquals(4, passCheckpoints.size)
        assertEquals("PASS_1", passCheckpoints[0])
        assertEquals("PASS_4", passCheckpoints[3])
        assertEquals("COMPLETE", passStatuses.last())
    }

    @Test
    fun `PP-111 orchestrator calls inference for each pass`() = runTest {
        orchestrator.runAnalysis(runId, configId)
        coVerify(exactly = 4) { inferencePort.completeTurn(configId, any(), any(), any(), any()) }
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
        // Run is already at pass index 2 (passes 0 and 1 are done)
        coEvery { runDao.getByRunId(runId) } returns ProfileAnalysisRunEntity(
            corpusRunId = runId,
            configId = configId.value,
            status = "PASS_3",
            currentPassIndex = 2,
        )

        orchestrator.runAnalysis(runId, configId)

        // Only 2 remaining passes (indices 2 and 3) should run
        coVerify(exactly = 2) { inferencePort.completeTurn(configId, any(), any(), any(), any()) }
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
    fun `PP-115 orchestrator executes tool calls returned by model`() = runTest {
        val toolCallResult = AgentModelTurnResult(
            assistantText = null,
            toolCalls = listOf(AgentRawToolCall(id = "tc1", name = "list_conversations", argumentsJson = "{}")),
            finishReason = AgentFinishReason.ToolUse,
        )
        // First turn returns a tool call; second returns stop
        var callCount = 0
        coEvery { inferencePort.completeTurn(any(), any(), any(), any(), any()) } answers {
            if (callCount++ == 0) toolCallResult else stopResult
        }
        coEvery { toolExecutor.execute(any()) } returns
            com.browntowndev.enclave.domain.model.inference.ToolExecutionResult("list_conversations", """{"conversations":[]}""")

        orchestrator.runAnalysis(runId, configId)

        coVerify(atLeast = 1) { toolExecutor.execute(match { it.toolName == "list_conversations" }) }
    }

    @Test
    fun `PP-116 progress callback fires once per pass`() = runTest {
        val progress = mutableListOf<Pair<Int, Int>>()
        orchestrator.runAnalysis(runId, configId, onProgress = { c, t -> progress.add(c to t) })
        assertEquals(4 + 1, progress.size) // 4 pass starts + 1 completion
        assertEquals(1 to 4, progress.first())
        assertEquals(4 to 4, progress[progress.size - 2])
    }
}

// Minimal MemoriesRepository fake to avoid unresolved abstract members
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
