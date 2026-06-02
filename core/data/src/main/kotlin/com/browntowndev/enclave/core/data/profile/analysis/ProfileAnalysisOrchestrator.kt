package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusConversationEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusMessageEntity
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ProfileAnalysisTools
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.profile.ProfileAnalysisInferencePort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
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
        private val corpusDao: ProfileCorpusDao,
        private val findingsDao: ProfileFindingsDao,
        private val runDao: ProfileAnalysisRunDao,
        private val memoriesRepository: MemoriesRepository,
        private val loggingPort: LoggingPort,
    ) {
        private val analysisTools = ProfileAnalysisTools.ALL_PROFILE_TOOLS

        suspend fun runAnalysis(
            corpusRunId: String,
            configId: ApiModelConfigurationId,
            onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> },
        ) {
            val existing = withContext(Dispatchers.IO) { runDao.getByRunId(corpusRunId) }
            if (existing?.status == STATUS_COMPLETE) {
                loggingPort.info(TAG, "[$corpusRunId] already complete — skipping")
                return
            }

            val startFromPass = existing?.currentPassIndex ?: 0
            val toolExecutor = toolExecutorFactory.create(corpusRunId)
            val totalPasses = ProfileAnalysisPrompts.ALL.size

            loggingPort.info(TAG, "[$corpusRunId] starting from pass ${startFromPass + 1} of $totalPasses")

            for (passIndex in startFromPass until totalPasses) {
                val passName = PASS_NAMES.getOrElse(passIndex) { "PASS_${passIndex + 1}" }
                loggingPort.info(TAG, "[$corpusRunId] beginning $passName (${passIndex + 1}/$totalPasses)")
                onProgress(passIndex + 1, totalPasses)

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

                if (passIndex == EXTRACTION_PASS_INDEX) {
                    runExtractionPass(corpusRunId, configId, toolExecutor)
                } else {
                    runAgentPass(
                        passIndex = passIndex,
                        passName = passName,
                        configId = configId,
                        toolExecutor = toolExecutor,
                    )
                }

                loggingPort.info(TAG, "[$corpusRunId] completed $passName")
            }

            onProgress(totalPasses, totalPasses)

            val synthesesCount =
                withContext(Dispatchers.IO) {
                    findingsDao.getByCategory(corpusRunId, "SYNTHESIS").size
                }
            loggingPort.info(TAG, "[$corpusRunId] all passes done — SYNTHESIS findings: $synthesesCount")

            saveProfileToMemory(corpusRunId)

            withContext(Dispatchers.IO) {
                runDao.upsert(
                    ProfileAnalysisRunEntity(
                        corpusRunId = corpusRunId,
                        configId = configId.value,
                        status = STATUS_COMPLETE,
                        currentPassIndex = totalPasses,
                        completedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
        }

        /**
         * Token-budgeted extraction: packs selected conversations into batches sized to a
         * fraction of the model's context window, then runs one fresh [ProfileAgentLoop] per
         * batch. The model's only job per batch is to call record_finding for any
         * psychological evidence it sees — no list/read/search tools needed.
         *
         * Starting a fresh loop per batch is the "compaction": raw conversation text is
         * discarded between batches while findings persist in the database. At the end all
         * findings feed into the CLUSTERING → SYNTHESIS → CRITIQUE passes.
         */
        private suspend fun runExtractionPass(
            corpusRunId: String,
            configId: ApiModelConfigurationId,
            toolExecutor: ProfileAnalysisToolExecutor,
        ) {
            val conversations =
                withContext(Dispatchers.IO) {
                    corpusDao.getSelectedConversations(corpusRunId)
                }
            loggingPort.info(TAG, "[$corpusRunId] EXTRACTION: ${conversations.size} conversations")

            if (conversations.isEmpty()) return

            // Build text for every conversation up front so we can measure token counts.
            data class ConvWithText(
                val conv: ProfileCorpusConversationEntity,
                val text: String,
            )

            val withText =
                withContext(Dispatchers.IO) {
                    conversations.mapNotNull { conv ->
                        val messages = corpusDao.getMessagesForConversation(conv.id)
                        val text = buildConversationText(messages)
                        if (text.isBlank()) null else ConvWithText(conv, text)
                    }
                }
            loggingPort.info(TAG, "[$corpusRunId] EXTRACTION: ${withText.size} non-empty conversations after text build")

            val contextWindow = inferencePort.getContextWindowTokens(configId)
            val batchBudget = (contextWindow * EXTRACTION_INPUT_RATIO).toInt()
            loggingPort.info(TAG, "[$corpusRunId] EXTRACTION: contextWindow=$contextWindow batchBudget=$batchBudget tokens")

            val batches =
                ProfileCorpusBatcher.pack(withText, batchBudget) { item ->
                    JTokkitTokenCounter.countTokens(item.text)
                }
            loggingPort.info(TAG, "[$corpusRunId] EXTRACTION: ${batches.size} batches")

            val extractionTools = listOf(ProfileAnalysisTools.RECORD_FINDING)

            for ((batchIndex, batch) in batches.withIndex()) {
                val batchPassName = "EXTRACTION[batch ${batchIndex + 1}/${batches.size}]"
                loggingPort.debug(TAG, "[$corpusRunId] $batchPassName — ${batch.size} conversations")

                val userContent =
                    buildString {
                        for ((i, item) in batch.withIndex()) {
                            appendLine("--- Conversation ${i + 1} of ${batch.size}: \"${item.conv.title}\" ---")
                            appendLine()
                            appendLine(item.text)
                        }
                    }.trimEnd()

                val seed =
                    mutableListOf(
                        AgentLoopMessage(
                            id = UUID.randomUUID().toString(),
                            role = Role.USER,
                            content = userContent,
                        ),
                    )

                val loop =
                    ProfileAgentLoop(
                        inferencePort = inferencePort,
                        configId = configId,
                        systemPrompt = ProfileAnalysisPrompts.EXTRACTION_BATCH,
                        tools = extractionTools,
                        toolExecutor = toolExecutor,
                        loggingPort = loggingPort,
                        passName = batchPassName,
                        requireRecordFinding = false,
                    )
                loop.run(seed)
            }
        }

        private fun buildConversationText(messages: List<ProfileCorpusMessageEntity>): String =
            buildString {
                for (msg in messages) {
                    val label =
                        when (msg.role.uppercase()) {
                            "USER" -> "User"
                            "ASSISTANT" -> "Assistant"
                            else -> continue
                        }
                    val content = msg.content.take(MAX_MESSAGE_CHARS)
                    appendLine("$label: $content")
                }
            }.take(MAX_CONVERSATION_CHARS)

        private suspend fun runAgentPass(
            passIndex: Int,
            passName: String,
            configId: ApiModelConfigurationId,
            toolExecutor: ProfileAnalysisToolExecutor,
        ) {
            val systemPrompt = ProfileAnalysisPrompts.ALL[passIndex]
            val messages =
                mutableListOf(
                    AgentLoopMessage(
                        id = UUID.randomUUID().toString(),
                        role = Role.USER,
                        content = "Begin your analysis for this pass.",
                    ),
                )

            val loop =
                ProfileAgentLoop(
                    inferencePort = inferencePort,
                    configId = configId,
                    systemPrompt = systemPrompt,
                    tools = analysisTools,
                    toolExecutor = toolExecutor,
                    loggingPort = loggingPort,
                    passName = passName,
                )
            loop.run(messages)
        }

        private suspend fun saveProfileToMemory(corpusRunId: String) {
            val syntheses =
                withContext(Dispatchers.IO) {
                    findingsDao.getByCategory(corpusRunId, "SYNTHESIS")
                }
            val profileContent = syntheses.lastOrNull()?.content
            if (profileContent == null) {
                loggingPort.warning(TAG, "[$corpusRunId] no SYNTHESIS finding — profile will not be saved to memory")
                return
            }

            val existing =
                memoriesRepository
                    .getCoreMemories()
                    .firstOrNull { it.category == MemoryCategory.PSYCH_PROFILE }
            if (existing != null) {
                memoriesRepository.updateMemory(existing.id, profileContent, MemoryCategory.PSYCH_PROFILE)
            } else {
                memoriesRepository.insertMemory(MemoryCategory.PSYCH_PROFILE, profileContent)
            }
            loggingPort.info(TAG, "[$corpusRunId] profile saved to memory (${profileContent.length} chars)")
        }

        companion object {
            private const val TAG = "ProfileAnalysisOrchestrator"
            private const val STATUS_COMPLETE = "COMPLETE"
            private const val EXTRACTION_PASS_INDEX = 0
            private const val MAX_MESSAGE_CHARS = 2000
            private const val MAX_CONVERSATION_CHARS = 8000

            /**
             * Fraction of the model's context window reserved for conversation text in each
             * extraction batch. The other half is reserved for the system prompt, tool schema,
             * and the model's tool-call output across multiple loop steps.
             */
            private const val EXTRACTION_INPUT_RATIO = 0.5

            private val PASS_NAMES = listOf("EXTRACTION", "CLUSTERING", "SYNTHESIS", "CRITIQUE")
        }
    }
