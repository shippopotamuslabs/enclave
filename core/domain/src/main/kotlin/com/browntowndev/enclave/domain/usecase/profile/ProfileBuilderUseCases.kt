package com.browntowndev.enclave.domain.usecase.profile

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
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveApiModelConfigurationsUseCase @Inject constructor(
    private val repository: ApiModelRepositoryPort,
) {
    operator fun invoke(): Flow<List<ApiModelConfiguration>> = repository.observeAllConfigurations()
}

class PrepareProfileCorpusUseCase @Inject constructor(
    private val analysisPort: ProfileAnalysisPort,
) {
    suspend operator fun invoke(
        corpusRunId: String,
        fileUris: List<String>,
        configId: ApiModelConfigurationId?,
    ): ProfileCostEstimate = analysisPort.prepareCorpus(corpusRunId, fileUris, configId)
}

class StartProfileAnalysisUseCase @Inject constructor(
    private val analysisPort: ProfileAnalysisPort,
) {
    suspend operator fun invoke(corpusRunId: String, configId: ApiModelConfigurationId) =
        analysisPort.startAnalysis(corpusRunId, configId)
}

class ObserveProfileAnalysisProgressUseCase @Inject constructor(
    private val analysisPort: ProfileAnalysisPort,
) {
    operator fun invoke(corpusRunId: String): Flow<ProfileAnalysisProgress> =
        analysisPort.observeAnalysisProgress(corpusRunId)
}

class GetLatestProfileSynthesisUseCase @Inject constructor(
    private val analysisPort: ProfileAnalysisPort,
) {
    suspend operator fun invoke(corpusRunId: String): String? =
        analysisPort.getLatestProfileSynthesis(corpusRunId)
}

class ClearProfileCorpusUseCase @Inject constructor(
    private val ingestionPort: ProfileExportIngestionPort,
) {
    suspend operator fun invoke(corpusRunId: String) = ingestionPort.clearAll(corpusRunId)
}

/** Single write policy for the psych profile: upsert the PSYCH_PROFILE memory. */
class SavePsychProfileUseCase @Inject constructor(
    private val memoriesRepository: MemoriesRepository,
) {
    suspend operator fun invoke(content: String) {
        val existing: Memory? =
            memoriesRepository.getCoreMemories()
                .firstOrNull { it.category == MemoryCategory.PSYCH_PROFILE }
        if (existing != null) {
            memoriesRepository.updateMemory(existing.id, content, MemoryCategory.PSYCH_PROFILE)
        } else {
            memoriesRepository.insertMemory(MemoryCategory.PSYCH_PROFILE, content)
        }
    }
}
