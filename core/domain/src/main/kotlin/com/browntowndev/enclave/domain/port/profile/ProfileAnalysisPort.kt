package com.browntowndev.enclave.domain.port.profile

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.profile.ProfileAnalysisProgress
import com.browntowndev.enclave.domain.model.profile.ProfileCostEstimate
import kotlinx.coroutines.flow.Flow

interface ProfileAnalysisPort {
    suspend fun prepareCorpus(
        corpusRunId: String,
        fileUris: List<String>,
        configId: ApiModelConfigurationId?,
    ): ProfileCostEstimate

    suspend fun startAnalysis(
        corpusRunId: String,
        configId: ApiModelConfigurationId,
    )

    fun observeAnalysisProgress(corpusRunId: String): Flow<ProfileAnalysisProgress>

    suspend fun getLatestProfileSynthesis(corpusRunId: String): String?
}
