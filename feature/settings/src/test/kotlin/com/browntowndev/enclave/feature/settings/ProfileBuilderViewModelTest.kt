package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
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
import com.browntowndev.enclave.domain.usecase.profile.ClearProfileCorpusUseCase
import com.browntowndev.enclave.domain.usecase.profile.GetLatestProfileSynthesisUseCase
import com.browntowndev.enclave.domain.usecase.profile.ObserveApiModelConfigurationsUseCase
import com.browntowndev.enclave.domain.usecase.profile.ObserveProfileAnalysisProgressUseCase
import com.browntowndev.enclave.domain.usecase.profile.PrepareProfileCorpusUseCase
import com.browntowndev.enclave.domain.usecase.profile.SavePsychProfileUseCase
import com.browntowndev.enclave.domain.usecase.profile.StartProfileAnalysisUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val progressFlow =
        MutableStateFlow(
            ProfileAnalysisProgress(
                currentPass = 0,
                totalPasses = 4,
                isRunning = true,
                isComplete = false,
                error = null,
            ),
        )

    // ENC-27: recorded invocations replace MockK coVerify.
    private val startAnalysisCalls = mutableListOf<Pair<String, ApiModelConfigurationId>>()
    private val observeProgressCalls = mutableListOf<String>()
    private val insertMemoryCalls = mutableListOf<Pair<MemoryCategory, String>>()
    private val updateMemoryCalls = mutableListOf<Triple<String, String, MemoryCategory?>>()
    private val clearAllCalls = mutableListOf<String>()

    private lateinit var viewModel: ProfileBuilderViewModel

    @BeforeEach
    fun setup() {
        every { apiModelRepository.observeAllConfigurations() } returns configsFlow
        coEvery { memoriesRepository.getCoreMemories() } returns emptyList()
        every { analysisPort.observeAnalysisProgress(any()) } answers {
            observeProgressCalls += firstArg<String>()
            progressFlow
        }
        coEvery { analysisPort.startAnalysis(any(), any()) } coAnswers {
            startAnalysisCalls += firstArg<String>() to ApiModelConfigurationId(secondArg<String>())
            Unit
        }
        coEvery { memoriesRepository.insertMemory(any(), any()) } coAnswers {
            insertMemoryCalls += firstArg<MemoryCategory>() to secondArg<String>()
            mockk()
        }
        coEvery { memoriesRepository.updateMemory(any(), any(), any()) } coAnswers {
            updateMemoryCalls += Triple(firstArg<String>(), secondArg<String>(), thirdArg<MemoryCategory?>())
            Unit
        }
        coEvery { ingestionPort.clearAll(any()) } coAnswers {
            clearAllCalls += firstArg<String>()
            Unit
        }

        viewModel =
            ProfileBuilderViewModel(
                observeApiModelConfigurations = ObserveApiModelConfigurationsUseCase(apiModelRepository),
                clearProfileCorpus = ClearProfileCorpusUseCase(ingestionPort),
                prepareProfileCorpus = PrepareProfileCorpusUseCase(analysisPort),
                startProfileAnalysis = StartProfileAnalysisUseCase(analysisPort),
                observeProfileAnalysisProgress = ObserveProfileAnalysisProgressUseCase(analysisPort),
                getLatestProfileSynthesis = GetLatestProfileSynthesisUseCase(analysisPort),
                savePsychProfile = SavePsychProfileUseCase(memoriesRepository),
            )
    }

    @Test
    fun `initial state loads configurations`() =
        runTest {
            val config =
                ApiModelConfiguration(
                    id = ApiModelConfigurationId("config-1"),
                    apiCredentialsId = ApiCredentialsId("cred-1"),
                    modelId = "gpt-test",
                    displayName = "Test Config",
                )
            configsFlow.value = listOf(config)
            advanceUntilIdle()

            assertEquals(listOf(config.toUi()), viewModel.uiState.value.configuredModels)
        }

    @Test
    fun `onProceedFromFiles invokes prepareCorpus and updates estimate`() =
        runTest {
            val uris = listOf("file://export.zip")
            val names = listOf("export.zip")
            viewModel.onFilesSelected(uris, names)

            val estimate =
                ProfileCostEstimate(
                    estimatedInputTokens = 50000,
                    estimatedOutputTokens = 3200,
                    estimatedCostUsd = BigDecimal("0.75"),
                    selectedConversationCount = 10,
                    totalConversationCount = 20,
                )
            coEvery { analysisPort.prepareCorpus(any(), uris, null) } returns estimate

            viewModel.onProceedFromFiles()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isPreparingCorpus)
            assertEquals(estimate.toUi(), state.costEstimate)
            assertEquals(PROFILE_BUILDER_STEP_COST, state.currentStep)
        }

    @Test
    fun `startAnalysis starts analysis and registers observer`() =
        runTest {
            val configId = ApiModelConfigurationId("config-abc")
            viewModel.onModelSelected(configId.value)

            viewModel.startAnalysis()
            advanceUntilIdle()

            val runId = viewModel.uiState.value.corpusRunId
            assertEquals(listOf(runId to configId), startAnalysisCalls)
            assertTrue(observeProgressCalls.contains(runId))
            assertTrue(viewModel.uiState.value.isAnalysisRunning)
            assertEquals(PROFILE_BUILDER_STEP_PROGRESS, viewModel.uiState.value.currentStep)
        }

    @Test
    fun `double-start is protected by run guard`() =
        runTest {
            val configId = ApiModelConfigurationId("config-abc")
            viewModel.onModelSelected(configId.value)

            // Set analysis running state manually
            viewModel.startAnalysis()
            advanceUntilIdle()

            // Verify start called once
            assertEquals(1, startAnalysisCalls.size)

            // Start again
            viewModel.startAnalysis()
            advanceUntilIdle()

            // Should not call startAnalysis again
            assertEquals(1, startAnalysisCalls.size)
        }

    @Test
    fun `progress updates are collected and fetch synthesis on complete`() =
        runTest {
            val configId = ApiModelConfigurationId("config-abc")
            viewModel.onModelSelected(configId.value)

            coEvery { analysisPort.getLatestProfileSynthesis(any()) } returns "Tends toward extreme focus."

            viewModel.startAnalysis()
            advanceUntilIdle()

            // Emit pass progress
            progressFlow.value =
                ProfileAnalysisProgress(
                    currentPass = 2,
                    totalPasses = 4,
                    isRunning = true,
                    isComplete = false,
                    error = null,
                )
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertEquals(2, state.currentPass)
            assertTrue(state.isAnalysisRunning)
            assertFalse(state.isAnalysisComplete)

            // Emit completion
            progressFlow.value =
                ProfileAnalysisProgress(
                    currentPass = 4,
                    totalPasses = 4,
                    isRunning = false,
                    isComplete = true,
                    error = null,
                )
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertFalse(state.isAnalysisRunning)
            assertTrue(state.isAnalysisComplete)
            assertEquals("Tends toward extreme focus.", state.profileContent)
            assertEquals(PROFILE_BUILDER_STEP_REVIEW, state.currentStep)
        }

    @Test
    fun `saveProfile upserts core memory and purges raw data`() =
        runTest {
            viewModel.onProfileContentEdited("Refined profile content.")

            // 1. Test Insert
            coEvery { memoriesRepository.getCoreMemories() } returns emptyList()

            viewModel.saveProfile()
            advanceUntilIdle()

            assertEquals(listOf(MemoryCategory.PSYCH_PROFILE to "Refined profile content."), insertMemoryCalls)
            assertEquals(1, clearAllCalls.size)
            assertTrue(viewModel.uiState.value.saved)

            // 2. Test Update
            val existing = Memory(id = "mem-123", category = MemoryCategory.PSYCH_PROFILE, content = "Old content")
            coEvery { memoriesRepository.getCoreMemories() } returns listOf(existing)

            viewModel.saveProfile()
            advanceUntilIdle()

            assertEquals(listOf(Triple("mem-123", "Refined profile content.", MemoryCategory.PSYCH_PROFILE)), updateMemoryCalls)
        }
}
