package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunEntity
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ProfileAnalysisTools
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileAnalysisOrchestrator
    @Inject
    constructor(
        private val inferencePort: ProfileAnalysisInferencePort,
        private val toolExecutorFactory: ProfileAnalysisToolExecutorFactory,
        private val findingsDao: ProfileFindingsDao,
        private val runDao: ProfileAnalysisRunDao,
        private val memoriesRepository: MemoriesRepository,
    ) {
        private val analysisTools = ProfileAnalysisTools.ALL_PROFILE_TOOLS

        /**
         * Runs (or resumes) a 4-pass profile analysis for [corpusRunId] using [configId].
         * Reports progress via [onProgress](currentPass 1-4, total 4).
         */
        suspend fun runAnalysis(
            corpusRunId: String,
            configId: ApiModelConfigurationId,
            onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> },
        ) {
            val existing = withContext(Dispatchers.IO) { runDao.getByRunId(corpusRunId) }
            if (existing?.status == STATUS_COMPLETE) return

            val startFromPass = existing?.currentPassIndex ?: 0
            val toolExecutor = toolExecutorFactory.create(corpusRunId)

            for (passIndex in startFromPass until ProfileAnalysisPrompts.ALL.size) {
                onProgress(passIndex + 1, ProfileAnalysisPrompts.ALL.size)

                withContext(Dispatchers.IO) {
                    runDao.upsert(
                        ProfileAnalysisRunEntity(
                            corpusRunId = corpusRunId,
                            configId = configId.value,
                            status = "PASS_${passIndex + 1}",
                            currentPassIndex = passIndex,
                        ),
                    )
                }

                runPass(
                    passIndex = passIndex,
                    configId = configId,
                    toolExecutor = toolExecutor,
                )
            }

            onProgress(ProfileAnalysisPrompts.ALL.size, ProfileAnalysisPrompts.ALL.size)
            saveProfileToMemory(corpusRunId)

            withContext(Dispatchers.IO) {
                runDao.upsert(
                    ProfileAnalysisRunEntity(
                        corpusRunId = corpusRunId,
                        configId = configId.value,
                        status = STATUS_COMPLETE,
                        currentPassIndex = ProfileAnalysisPrompts.ALL.size,
                        completedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
        }

        private suspend fun runPass(
            passIndex: Int,
            configId: ApiModelConfigurationId,
            toolExecutor: ProfileAnalysisToolExecutor,
        ) {
            val systemPrompt = ProfileAnalysisPrompts.ALL[passIndex]
            val messages = mutableListOf<AgentLoopMessage>()
            messages.add(
                AgentLoopMessage(
                    id = UUID.randomUUID().toString(),
                    role = Role.USER,
                    content = "Begin your analysis for this pass.",
                ),
            )

            val loop = ProfileAgentLoop(
                inferencePort = inferencePort,
                configId = configId,
                systemPrompt = systemPrompt,
                tools = analysisTools,
                toolExecutor = toolExecutor,
            )
            loop.run(messages)
        }

        private suspend fun saveProfileToMemory(corpusRunId: String) {
            // Use the last SYNTHESIS finding as the profile content
            val syntheses = withContext(Dispatchers.IO) {
                findingsDao.getByCategory(corpusRunId, "SYNTHESIS")
            }
            val profileContent = syntheses.lastOrNull()?.content ?: return

            // Single write policy: upsert by checking existing and calling updateMemory if present
            val existing = memoriesRepository.getCoreMemories()
                .firstOrNull { it.category == MemoryCategory.PSYCH_PROFILE }
            if (existing != null) {
                memoriesRepository.updateMemory(existing.id, profileContent, MemoryCategory.PSYCH_PROFILE)
            } else {
                memoriesRepository.insertMemory(MemoryCategory.PSYCH_PROFILE, profileContent)
            }
        }

        companion object {
            private const val STATUS_COMPLETE = "COMPLETE"
        }
    }
