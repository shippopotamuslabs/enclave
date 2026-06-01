package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsEntity
import com.browntowndev.enclave.domain.model.inference.FindingCategory
import com.browntowndev.enclave.domain.model.inference.ListConversationsParams
import com.browntowndev.enclave.domain.model.inference.ReadConversationParams
import com.browntowndev.enclave.domain.model.inference.ReadFindingsParams
import com.browntowndev.enclave.domain.model.inference.RecordFindingParams
import com.browntowndev.enclave.domain.model.inference.SearchConversationsParams
import com.browntowndev.enclave.domain.model.inference.ProfileAnalysisTools
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.inference.ToolParameters
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { encodeDefaults = true }

// region result models

@Serializable
private data class ConversationMeta(
    val id: String,
    val title: String,
    val provider: String,
    val created_at_ms: Long?,
    val signal_score: Float?,
    val user_token_count: Int,
)

@Serializable
private data class ListConversationsResult(
    val conversations: List<ConversationMeta>,
    val page: Int,
    val page_size: Int,
    val total: Int,
)

@Serializable
private data class MessageItem(
    val role: String,
    val content: String,
)

@Serializable
private data class ReadConversationResult(
    val id: String,
    val title: String,
    val page: Int,
    val total_pages: Int,
    val messages: List<MessageItem>,
)

@Serializable
private data class SearchSnippet(
    val conversation_id: String,
    val title: String,
    val signal_score: Float?,
    val snippet: String,
)

@Serializable
private data class SearchConversationsResult(val results: List<SearchSnippet>)

@Serializable
private data class RecordFindingResult(val id: String, val status: String)

@Serializable
private data class FindingItem(val id: String, val category: String, val content: String)

@Serializable
private data class ReadFindingsResult(val findings: List<FindingItem>)

@Serializable
private data class ErrorResult(val status: String = "error", val message: String)

// endregion

/**
 * Run-scoped executor for the five profile analysis tools.
 *
 * Not a Singleton — created per analysis run via [ProfileAnalysisToolExecutorFactory].
 * Does NOT route through [CompositeToolExecutor]: the analysis orchestrator calls this directly.
 */
@Suppress("MagicNumber")
class ProfileAnalysisToolExecutor(
    private val corpusRunId: String,
    private val corpusDao: ProfileCorpusDao,
    private val findingsDao: ProfileFindingsDao,
) : ToolExecutorPort {
    private val analysisToolNames = setOf(
        ProfileAnalysisTools.LIST_CONVERSATIONS.name,
        ProfileAnalysisTools.READ_CONVERSATION.name,
        ProfileAnalysisTools.SEARCH_CONVERSATIONS.name,
        ProfileAnalysisTools.RECORD_FINDING.name,
        ProfileAnalysisTools.READ_FINDINGS.name,
    )

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        require(request.toolName in analysisToolNames) { "Unsupported tool: ${request.toolName}" }
        val resultJson = runCatching {
            val definition = ProfileAnalysisTools.fromName(request.toolName)
                ?: throw IllegalArgumentException("Unknown tool: ${request.toolName}")
            val params: ToolParameters = definition.decodeArguments(request.argumentsJson)
            when (request.toolName) {
                ProfileAnalysisTools.LIST_CONVERSATIONS.name -> listConversations(params as ListConversationsParams)
                ProfileAnalysisTools.READ_CONVERSATION.name -> readConversation(params as ReadConversationParams)
                ProfileAnalysisTools.SEARCH_CONVERSATIONS.name -> searchConversations(params as SearchConversationsParams)
                ProfileAnalysisTools.RECORD_FINDING.name -> recordFinding(params as RecordFindingParams)
                ProfileAnalysisTools.READ_FINDINGS.name -> readFindings(params as ReadFindingsParams)
                else -> "{}"
            }
        }.getOrElse { e -> json.encodeToString(ErrorResult(message = e.message ?: "Unknown error")) }

        return ToolExecutionResult(toolName = request.toolName, resultJson = resultJson)
    }

    private suspend fun listConversations(params: ListConversationsParams): String {
        val all = corpusDao.getSelectedConversations(corpusRunId)
        val minScore = params.min_score
        val filtered = if (minScore != null) {
            all.filter { (it.signalScore ?: 0f) >= minScore }
        } else all

        val pageSize = params.page_size.coerceIn(1, 50)
        val page = params.page.coerceAtLeast(1)
        val paged = filtered.drop((page - 1) * pageSize).take(pageSize)

        return json.encodeToString(
            ListConversationsResult(
                conversations = paged.map {
                    ConversationMeta(
                        id = it.id,
                        title = it.title,
                        provider = it.provider,
                        created_at_ms = it.createdAtMs,
                        signal_score = it.signalScore,
                        user_token_count = it.userTokenCount,
                    )
                },
                page = page,
                page_size = pageSize,
                total = filtered.size,
            ),
        )
    }

    private suspend fun readConversation(params: ReadConversationParams): String {
        val conv = corpusDao.getSelectedConversations(corpusRunId)
            .firstOrNull { it.id == params.conversation_id }
            ?: return json.encodeToString(ErrorResult(message = "Conversation not found: ${params.conversation_id}"))

        val page = params.page.coerceAtLeast(1)
        val offset = (page - 1) * MESSAGES_PER_PAGE
        val messages = corpusDao.getMessagesPage(params.conversation_id, MESSAGES_PER_PAGE, offset)
        val totalMessages = conv.messageCount
        val totalPages = ((totalMessages - 1) / MESSAGES_PER_PAGE) + 1

        return json.encodeToString(
            ReadConversationResult(
                id = conv.id,
                title = conv.title,
                page = page,
                total_pages = totalPages,
                messages = messages.map { MessageItem(role = it.role, content = it.content) },
            ),
        )
    }

    private suspend fun searchConversations(params: SearchConversationsParams): String {
        val query = "%${params.query}%"
        val limit = params.limit.coerceIn(1, 20)
        val results = corpusDao.searchSelectedConversations(corpusRunId, query, limit)

        return json.encodeToString(
            SearchConversationsResult(
                results = results.map { conv ->
                    SearchSnippet(
                        conversation_id = conv.id,
                        title = conv.title,
                        signal_score = conv.signalScore,
                        snippet = conv.embeddingSeedText.take(300),
                    )
                },
            ),
        )
    }

    private suspend fun recordFinding(params: RecordFindingParams): String {
        val entity = ProfileFindingsEntity(
            corpusRunId = corpusRunId,
            category = params.category.name,
            content = params.content,
        )
        findingsDao.insert(entity)
        return json.encodeToString(RecordFindingResult(id = entity.id, status = "recorded"))
    }

    private suspend fun readFindings(params: ReadFindingsParams): String {
        val categoryFilter = params.category
        val findings = if (categoryFilter != null) {
            findingsDao.getByCategory(corpusRunId, categoryFilter.name)
        } else {
            findingsDao.getAll(corpusRunId)
        }

        return json.encodeToString(
            ReadFindingsResult(
                findings = findings.map { FindingItem(id = it.id, category = it.category, content = it.content) },
            ),
        )
    }

    companion object {
        const val MESSAGES_PER_PAGE = 15
    }
}
