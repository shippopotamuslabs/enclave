package com.browntowndev.enclave.core.data.profile.analysis

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.profile.ProfileAnalysisProgress
import com.browntowndev.enclave.domain.model.profile.ProfileCostEstimate
import com.browntowndev.enclave.domain.port.profile.ConversationCorpusScorerPort
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisPort
import com.browntowndev.enclave.domain.port.profile.ProfileExportIngestionPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileAnalysisManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val apiModelRepository: ApiModelRepositoryPort,
        private val ingestionPort: ProfileExportIngestionPort,
        private val scorerPort: ConversationCorpusScorerPort,
        private val costEstimator: ProfileCostEstimator,
        private val findingsDao: ProfileFindingsDao,
    ) : ProfileAnalysisPort {

        private val workManager = WorkManager.getInstance(context)

        override suspend fun prepareCorpus(
            corpusRunId: String,
            fileUris: List<String>,
            configId: ApiModelConfigurationId?,
        ): ProfileCostEstimate = withContext(Dispatchers.IO) {
            // Re-import safety: clear all previous data for this run first
            ingestionPort.clearAll(corpusRunId)

            // Ingest zip files
            ingestionPort.ingestZipFiles(fileUris, corpusRunId)

            // Score and select using default budget
            val selected = scorerPort.scoreAndSelect(corpusRunId, tokenBudget = DEFAULT_TOKEN_BUDGET)

            // Load config pricing to build cost estimate
            val config = configId?.let { apiModelRepository.getConfigurationById(it) }
            costEstimator.estimate(
                selectedCorpusInputTokens = selected.estimatedInputTokens,
                selectedConversationCount = selected.selectedConversationIds.size,
                totalConversationCount = selected.totalConversationsInCorpus,
                pricing = config?.pricing,
            )
        }

        override suspend fun startAnalysis(
            corpusRunId: String,
            configId: ApiModelConfigurationId,
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ProfileAnalysisWorker>()
                .setInputData(
                    workDataOf(
                        ProfileAnalysisWorker.KEY_CORPUS_RUN_ID to corpusRunId,
                        ProfileAnalysisWorker.KEY_CONFIG_ID to configId.value,
                    )
                )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag(ANALYSIS_WORK_TAG)
                .build()

            // Enqueue unique work to prevent parallel runs for the same corpus
            val uniqueWorkName = getUniqueWorkName(corpusRunId)
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }

        override fun observeAnalysisProgress(corpusRunId: String): Flow<ProfileAnalysisProgress> {
            val uniqueWorkName = getUniqueWorkName(corpusRunId)
            return workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName)
                .map { workInfos ->
                    val info = workInfos.firstOrNull()
                    if (info == null) {
                        ProfileAnalysisProgress(
                            currentPass = 0,
                            totalPasses = 4,
                            isRunning = false,
                            isComplete = false,
                            error = null,
                        )
                    } else {
                        val pass = info.progress.getInt(ProfileAnalysisWorker.KEY_CURRENT_PASS, 0)
                        val total = info.progress.getInt(ProfileAnalysisWorker.KEY_TOTAL_PASSES, 4)
                        val failed = info.state == WorkInfo.State.FAILED
                        val succeeded = info.state == WorkInfo.State.SUCCEEDED
                        val running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED

                        ProfileAnalysisProgress(
                            currentPass = pass,
                            totalPasses = total,
                            isRunning = running && !failed && !succeeded,
                            isComplete = succeeded,
                            error = if (failed) info.outputData.getString(ProfileAnalysisWorker.KEY_ERROR) else null,
                        )
                    }
                }
        }

        override suspend fun getLatestProfileSynthesis(corpusRunId: String): String? =
            withContext(Dispatchers.IO) {
                findingsDao.getByCategory(corpusRunId, "SYNTHESIS")
                    .lastOrNull()?.content
            }

        private fun getUniqueWorkName(corpusRunId: String): String =
            "profile_analysis_$corpusRunId"

        companion object {
            private const val DEFAULT_TOKEN_BUDGET = 100_000
            private const val ANALYSIS_WORK_TAG = "profile_analysis"
        }
    }
