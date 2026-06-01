package com.browntowndev.enclave.core.data.profile

import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.domain.model.profile.ConversationScoringPolicy
import com.browntowndev.enclave.domain.model.profile.SelectedCorpus
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.profile.ConversationCorpusScorerPort
import com.browntowndev.enclave.domain.usecase.profile.ConversationScorer
import com.browntowndev.enclave.domain.usecase.profile.ConversationSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileCorpusScorerImpl
    @Inject
    constructor(
        private val dao: ProfileCorpusDao,
        private val embeddingEngine: EmbeddingEnginePort,
    ) : ConversationCorpusScorerPort {
        override suspend fun scoreAndSelect(
            corpusRunId: String,
            tokenBudget: Int,
        ): SelectedCorpus =
            withContext(Dispatchers.Default) {
                val conversations = dao.getConversationsForRun(corpusRunId)
                if (conversations.isEmpty()) {
                    return@withContext SelectedCorpus(
                        corpusRunId = corpusRunId,
                        selectedConversationIds = emptyList(),
                        totalConversationsInCorpus = 0,
                        estimatedInputTokens = 0,
                        coveragePercent = 0f,
                    )
                }

                val oldestMs = conversations.mapNotNull { it.createdAtMs }.minOrNull() ?: 0L
                val newestMs = conversations.mapNotNull { it.createdAtMs }.maxOrNull() ?: System.currentTimeMillis()

                // Score each conversation
                val candidates = conversations.map { conv ->
                    val userText = withContext(Dispatchers.IO) {
                        dao.getMessagesForConversation(conv.id)
                            .filter { it.role == "USER" }
                            .sortedBy { it.orderIndex }
                            .joinToString("\n") { it.content }
                    }

                    val score = ConversationScorer.score(
                        userText = userText,
                        title = conv.title,
                        userTokenCount = conv.userTokenCount,
                        createdAtMs = conv.createdAtMs,
                        oldestMs = oldestMs,
                        newestMs = newestMs,
                    )

                    withContext(Dispatchers.IO) {
                        dao.updateSignalScore(conv.id, score)
                    }

                    score to conv
                }.filter { (score, _) -> score >= ConversationScoringPolicy.MIN_ELIGIBLE_SCORE }
                 .map { (score, conv) -> score to conv }

                // Embed seed text for MMR diversity
                val candidatesWithEmbeddings = candidates.map { (score, conv) ->
                    val embedding = embeddingEngine.getEmbedding(conv.embeddingSeedText)
                    ConversationSelector.Candidate(
                        id = conv.id,
                        signalScore = score,
                        userTokenCount = conv.userTokenCount,
                        embedding = embedding,
                    )
                }

                // MMR selection
                val result = ConversationSelector.select(candidatesWithEmbeddings, tokenBudget)

                // Persist selection flags
                withContext(Dispatchers.IO) {
                    result.selectedIds.forEach { id -> dao.updateSelected(id, true) }
                }

                SelectedCorpus(
                    corpusRunId = corpusRunId,
                    selectedConversationIds = result.selectedIds,
                    totalConversationsInCorpus = conversations.size,
                    estimatedInputTokens = result.totalTokens,
                    coveragePercent = if (conversations.isEmpty()) 0f
                                      else result.selectedIds.size.toFloat() / conversations.size,
                )
            }
    }
