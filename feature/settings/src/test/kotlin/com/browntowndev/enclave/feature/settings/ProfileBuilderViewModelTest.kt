package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.profile.ProfileAnalysisProgress
import com.browntowndev.enclave.domain.model.profile.ProfileCostEstimate
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisPort
import com.browntowndev.enclave.domain.port.profile.ProfileExportIngestionPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileBuilderViewModelTest {
    private val apiModelRepository = mockk<ApiModelRepositoryPort>()
    private val ingestionPort = mockk<ProfileExportIngestionPort>(relaxed = true)
    private val analysisPort = mockk<ProfileAnalysisPort>(relaxed = true)
    private val memoriesRepository = mockk<MemoriesRepository>(relaxed = true)

    private val configsFlow = MutableStateFlow<List<ApiModelConfiguration>>(emptyList())
    private val progressFlow = MutableStateFlow(
        ProfileAnalysisProgress(
            currentPass = 0,
            totalPasses = 4,
            isRunning = true,
            isComplete = false,
            error = null
        )
    )

    private lateinit var viewModel: ProfileBuilderViewModel

    @BeforeEach
    fun setup() {
        every { apiModelRepository.observeAllConfigurations() } returns configsFlow
        coEvery { memoriesRepository.getCoreMemories() } returns emptyList()
        every { analysisPort.observeAnalysisProgress(any()) } returns progressFlow

        viewModel = ProfileBuilderViewModel(
            apiModelRepository = apiModelRepository,
            ingestionPort = ingestionPort,
            analysisPort = analysisPort,
            memoriesRepository = memoriesRepository
        )
    }

    @Test
    fun `initial state loads configurations`() = runTest {
        val config = mockk<ApiModelConfiguration>()
        configsFlow.value = listOf(config)
        advanceUntilIdle()

        assertEquals(listOf(config), viewModel.uiState.value.configuredModels)
    }

    @Test
    fun `onProceedFromFiles invokes prepareCorpus and updates estimate`() = runTest {
        val uris = listOf("file://export.zip")
        val names = listOf("export.zip")
        viewModel.onFilesSelected(uris, names)

        val estimate = ProfileCostEstimate(
            estimatedInputTokens = 50000,
            estimatedOutputTokens = 3200,
            estimatedCostUsd = BigDecimal("0.75"),
            selectedConversationCount = 10,
            totalConversationCount = 20
        )
        coEvery { analysisPort.prepareCorpus(any(), uris, null) } returns estimate

        viewModel.onProceedFromFiles()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPreparingCorpus)
        assertEquals(estimate, state.costEstimate)
        assertEquals(PROFILE_BUILDER_STEP_COST, state.currentStep)
    }

    @Test
    fun `startAnalysis starts analysis and registers observer`() = runTest {
        val configId = ApiModelConfigurationId("config-abc")
        viewModel.onModelSelected(configId)

        viewModel.startAnalysis()
        advanceUntilIdle()

        val runId = viewModel.uiState.value.corpusRunId
        coVerify { analysisPort.startAnalysis(runId, configId) }
        coVerify { analysisPort.observeAnalysisProgress(runId) }
        assertTrue(viewModel.uiState.value.isAnalysisRunning)
        assertEquals(PROFILE_BUILDER_STEP_PROGRESS, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `double-start is protected by run guard`() = runTest {
        val configId = ApiModelConfigurationId("config-abc")
        viewModel.onModelSelected(configId)

        // Set analysis running state manually
        viewModel.startAnalysis()
        advanceUntilIdle()

        // Verify start called once
        coVerify(exactly = 1) { analysisPort.startAnalysis(any(), any()) }

        // Start again
        viewModel.startAnalysis()
        advanceUntilIdle()

        // Should not call startAnalysis again
        coVerify(exactly = 1) { analysisPort.startAnalysis(any(), any()) }
    }

    @Test
    fun `progress updates are collected and fetch synthesis on complete`() = runTest {
        val configId = ApiModelConfigurationId("config-abc")
        viewModel.onModelSelected(configId)

        coEvery { analysisPort.getLatestProfileSynthesis(any()) } returns "Tends toward extreme focus."

        viewModel.startAnalysis()
        advanceUntilIdle()

        // Emit pass progress
        progressFlow.value = ProfileAnalysisProgress(
            currentPass = 2,
            totalPasses = 4,
            isRunning = true,
            isComplete = false,
            error = null
        )
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(2, state.currentPass)
        assertTrue(state.isAnalysisRunning)
        assertFalse(state.isAnalysisComplete)

        // Emit completion
        progressFlow.value = ProfileAnalysisProgress(
            currentPass = 4,
            totalPasses = 4,
            isRunning = false,
            isComplete = true,
            error = null
        )
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertFalse(state.isAnalysisRunning)
        assertTrue(state.isAnalysisComplete)
        assertEquals("Tends toward extreme focus.", state.profileContent)
        assertEquals(PROFILE_BUILDER_STEP_REVIEW, state.currentStep)
    }

    @Test
    fun `saveProfile upserts core memory and purges raw data`() = runTest {
        viewModel.onProfileContentEdited("Refined profile content.")

        // 1. Test Insert
        coEvery { memoriesRepository.getCoreMemories() } returns emptyList()

        viewModel.saveProfile()
        advanceUntilIdle()

        coVerify { memoriesRepository.insertMemory(MemoryCategory.PSYCH_PROFILE, "Refined profile content.") }
        coVerify { ingestionPort.clearAll(any()) }
        assertTrue(viewModel.uiState.value.saved)

        // 2. Test Update
        val existing = Memory(id = "mem-123", category = MemoryCategory.PSYCH_PROFILE, content = "Old content")
        coEvery { memoriesRepository.getCoreMemories() } returns listOf(existing)

        viewModel.saveProfile()
        advanceUntilIdle()

        coVerify { memoriesRepository.updateMemory("mem-123", "Refined profile content.", MemoryCategory.PSYCH_PROFILE) }
    }
}
