package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.profile.ParseHealthReport
import com.browntowndev.enclave.domain.model.profile.ProfileAnalysisProgress
import com.browntowndev.enclave.domain.model.profile.ProfileCostEstimate
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisPort
import com.browntowndev.enclave.domain.port.profile.ProfileExportIngestionPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

/** Recording [ProfileAnalysisPort] fake (ENC-28). */
class FakeProfileAnalysisPort : ProfileAnalysisPort {
    var progressFlow: MutableStateFlow<ProfileAnalysisProgress> =
        MutableStateFlow(
            ProfileAnalysisProgress(
                currentPass = 0,
                totalPasses = 4,
                isRunning = true,
                isComplete = false,
                error = null,
            ),
        )

    var prepareCorpusResult: ProfileCostEstimate =
        ProfileCostEstimate(
            estimatedInputTokens = 0,
            estimatedOutputTokens = 0,
            estimatedCostUsd = BigDecimal.ZERO,
            selectedConversationCount = 0,
            totalConversationCount = 0,
        )

    val prepareCorpusCalls = mutableListOf<Triple<String, List<String>, ApiModelConfigurationId?>>()
    val startAnalysisCalls = mutableListOf<Pair<String, ApiModelConfigurationId>>()
    val observeProgressCalls = mutableListOf<String>()

    var latestSynthesis: String? = null
    val getLatestSynthesisCalls = mutableListOf<String>()

    override suspend fun prepareCorpus(
        corpusRunId: String,
        fileUris: List<String>,
        configId: ApiModelConfigurationId?,
    ): ProfileCostEstimate {
        prepareCorpusCalls += Triple(corpusRunId, fileUris, configId)
        return prepareCorpusResult
    }

    override suspend fun startAnalysis(corpusRunId: String, configId: ApiModelConfigurationId) {
        startAnalysisCalls += corpusRunId to configId
    }

    override fun observeAnalysisProgress(corpusRunId: String): Flow<ProfileAnalysisProgress> {
        observeProgressCalls += corpusRunId
        return progressFlow
    }

    override suspend fun getLatestProfileSynthesis(corpusRunId: String): String? {
        getLatestSynthesisCalls += corpusRunId
        return latestSynthesis
    }
}

/** Recording [ProfileExportIngestionPort] fake (ENC-28). */
class FakeProfileExportIngestionPort : ProfileExportIngestionPort {
    var ingestResult: List<ParseHealthReport> = emptyList()
    val ingestCalls = mutableListOf<Pair<List<String>, String>>()
    val clearCorpusCalls = mutableListOf<String>()
    val clearAllCalls = mutableListOf<String>()

    override suspend fun ingestZipFiles(uris: List<String>, corpusRunId: String): List<ParseHealthReport> {
        ingestCalls += uris to corpusRunId
        return ingestResult
    }

    override suspend fun clearCorpus(corpusRunId: String) {
        clearCorpusCalls += corpusRunId
    }

    override suspend fun clearAll(corpusRunId: String) {
        clearAllCalls += corpusRunId
    }
}
