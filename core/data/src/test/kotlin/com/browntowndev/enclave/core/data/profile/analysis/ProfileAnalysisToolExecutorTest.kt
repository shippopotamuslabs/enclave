package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.local.ProfileCorpusConversationEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusMessageEntity
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsEntity
import com.browntowndev.enclave.domain.model.inference.FindingCategory
import com.browntowndev.enclave.domain.model.inference.ListConversationsParams
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ReadConversationParams
import com.browntowndev.enclave.domain.model.inference.ReadFindingsParams
import com.browntowndev.enclave.domain.model.inference.RecordFindingParams
import com.browntowndev.enclave.domain.model.inference.SearchConversationsParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ProfileAnalysisTools
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileAnalysisToolExecutorTest {
    private val corpusDao = mockk<ProfileCorpusDao>()
    private val findingsDao = mockk<ProfileFindingsDao>()
    private lateinit var executor: ProfileAnalysisToolExecutor

    private val runId = "run-123"
    private val sampleConversation = ProfileCorpusConversationEntity(
        id = "conv-1",
        corpusRunId = runId,
        externalId = "ext-1",
        title = "Why do I procrastinate?",
        provider = "OPENAI",
        createdAtMs = 1710000000000L,
        userTokenCount = 1200,
        assistantTokenCount = 1500,
        messageCount = 8,
        userMessageCount = 4,
        embeddingSeedText = "Why do I procrastinate?",
        selectedForAnalysis = true,
        signalScore = 0.8f,
    )

    @BeforeEach
    fun setup() {
        executor = ProfileAnalysisToolExecutor(runId, corpusDao, findingsDao)
    }

    private fun request(toolName: String, argsJson: String) = ToolCallRequest(
        toolName = toolName,
        argumentsJson = argsJson,
        provider = "OPENAI",
        modelType = ModelType.MAIN,
    )

    // region list_conversations

    @Test
    fun `PP-90 list_conversations returns paginated metadata`() = runTest {
        coEvery { corpusDao.getSelectedConversations(runId) } returns listOf(sampleConversation)

        val result = executor.execute(
            request(ProfileAnalysisTools.LIST_CONVERSATIONS.name, """{"page":1,"page_size":20}"""),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val convs = json["conversations"]!!.jsonArray
        assertEquals(1, convs.size)
        assertEquals("conv-1", convs[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Why do I procrastinate?", convs[0].jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(1, json["total"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `PP-91 list_conversations filters by min_score`() = runTest {
        val lowScore = sampleConversation.copy(id = "conv-low", signalScore = 0.05f)
        coEvery { corpusDao.getSelectedConversations(runId) } returns listOf(sampleConversation, lowScore)

        val result = executor.execute(
            request(ProfileAnalysisTools.LIST_CONVERSATIONS.name, """{"min_score":0.5}"""),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals(1, json["total"]!!.jsonPrimitive.content.toInt())
        assertEquals("conv-1", json["conversations"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
    }

    // endregion

    // region read_conversation

    @Test
    fun `PP-92 read_conversation returns messages for valid id`() = runTest {
        coEvery { corpusDao.getSelectedConversations(runId) } returns listOf(sampleConversation)
        coEvery { corpusDao.getMessagesPage("conv-1", 15, 0) } returns listOf(
            ProfileCorpusMessageEntity(
                conversationId = "conv-1", corpusRunId = runId,
                role = "USER", content = "Why do I keep delaying things?",
                createdAtMs = null, orderIndex = 0,
            ),
        )

        val result = executor.execute(
            request(ProfileAnalysisTools.READ_CONVERSATION.name, """{"conversation_id":"conv-1","page":1}"""),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("conv-1", json["id"]!!.jsonPrimitive.content)
        val messages = json["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("USER", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `PP-93 read_conversation returns error for unknown id`() = runTest {
        coEvery { corpusDao.getSelectedConversations(runId) } returns emptyList()

        val result = executor.execute(
            request(ProfileAnalysisTools.READ_CONVERSATION.name, """{"conversation_id":"unknown"}"""),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("error", json["status"]!!.jsonPrimitive.content)
    }

    // endregion

    // region search_conversations

    @Test
    fun `PP-94 search_conversations returns matching results`() = runTest {
        coEvery { corpusDao.searchSelectedConversations(runId, "%procrastinate%", 5) } returns listOf(sampleConversation)

        val result = executor.execute(
            request(ProfileAnalysisTools.SEARCH_CONVERSATIONS.name, """{"query":"procrastinate","limit":5}"""),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val results = json["results"]!!.jsonArray
        assertEquals(1, results.size)
        assertEquals("conv-1", results[0].jsonObject["conversation_id"]!!.jsonPrimitive.content)
    }

    // endregion

    // region record_finding / read_findings

    @Test
    fun `PP-95 record_finding persists to findings dao`() = runTest {
        coEvery { findingsDao.insert(any()) } returns 1L

        val result = executor.execute(
            request(
                ProfileAnalysisTools.RECORD_FINDING.name,
                """{"content":"User shows avoidance pattern when discussing career.","category":"EVIDENCE"}""",
            ),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("recorded", json["status"]!!.jsonPrimitive.content)
        coVerify { findingsDao.insert(match { it.corpusRunId == runId && it.category == "EVIDENCE" }) }
    }

    @Test
    fun `PP-96 read_findings returns all findings without filter`() = runTest {
        coEvery { findingsDao.getAll(runId) } returns listOf(
            ProfileFindingsEntity(id = "f1", corpusRunId = runId, category = "EVIDENCE", content = "Finding A"),
            ProfileFindingsEntity(id = "f2", corpusRunId = runId, category = "PATTERN", content = "Finding B"),
        )

        val result = executor.execute(
            request(ProfileAnalysisTools.READ_FINDINGS.name, "{}"),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        val findings = json["findings"]!!.jsonArray
        assertEquals(2, findings.size)
    }

    @Test
    fun `PP-97 read_findings with category filter calls getByCategory`() = runTest {
        coEvery { findingsDao.getByCategory(runId, "PATTERN") } returns listOf(
            ProfileFindingsEntity(id = "f2", corpusRunId = runId, category = "PATTERN", content = "Pattern found"),
        )

        val result = executor.execute(
            request(ProfileAnalysisTools.READ_FINDINGS.name, """{"category":"PATTERN"}"""),
        )

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals(1, json["findings"]!!.jsonArray.size)
        coVerify { findingsDao.getByCategory(runId, "PATTERN") }
    }

    @Test
    fun `PP-98 executor rejects unknown tool name`() = runTest {
        val ex = runCatching {
            executor.execute(request("unknown_tool", "{}"))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun `PP-99 list and read tools cannot see conversations where selected_for_analysis is false`() = runTest {
        val selected = sampleConversation.copy(id = "selected-conv", selectedForAnalysis = true)
        val unselected = sampleConversation.copy(id = "unselected-conv", selectedForAnalysis = false)

        coEvery { corpusDao.getSelectedConversations(runId) } returns listOf(selected)

        // List tool should only return the selected one
        val listResult = executor.execute(
            request(ProfileAnalysisTools.LIST_CONVERSATIONS.name, "{}"),
        )
        val listJson = Json.parseToJsonElement(listResult.resultJson).jsonObject
        val conversations = listJson["conversations"]!!.jsonArray
        assertEquals(1, conversations.size)
        assertEquals("selected-conv", conversations[0].jsonObject["id"]!!.jsonPrimitive.content)

        // Read tool for unselected conversation ID should return error
        val readResult = executor.execute(
            request(ProfileAnalysisTools.READ_CONVERSATION.name, """{"conversation_id":"unselected-conv"}"""),
        )
        val readJson = Json.parseToJsonElement(readResult.resultJson).jsonObject
        assertEquals("error", readJson["status"]!!.jsonPrimitive.content)
    }

    // endregion
}
