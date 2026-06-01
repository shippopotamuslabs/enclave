package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ProfileAnalysisToolDefinitionTest {
    @Test
    fun `PP-100 LIST_CONVERSATIONS is in ALL_TOOLS and resolves by name`() {
        val found = ProfileAnalysisTools.fromName("list_conversations")
        assertNotNull(found)
        assertEquals(ProfileAnalysisTools.LIST_CONVERSATIONS, found)
    }

    @Test
    fun `PP-101 READ_CONVERSATION is in ALL_TOOLS and resolves by name`() {
        assertNotNull(ProfileAnalysisTools.fromName("read_conversation"))
    }

    @Test
    fun `PP-102 SEARCH_CONVERSATIONS is in ALL_TOOLS and resolves by name`() {
        assertNotNull(ProfileAnalysisTools.fromName("search_conversations"))
    }

    @Test
    fun `PP-103 RECORD_FINDING is in ALL_TOOLS and resolves by name`() {
        assertNotNull(ProfileAnalysisTools.fromName("record_finding"))
    }

    @Test
    fun `PP-104 READ_FINDINGS is in ALL_TOOLS and resolves by name`() {
        assertNotNull(ProfileAnalysisTools.fromName("read_findings"))
    }

    @Test
    fun `PP-105 ListConversationsParams decodes from JSON`() {
        val td = ProfileAnalysisTools.fromName("list_conversations")!!
        val params = td.decodeArguments("""{"page":2,"page_size":10}""") as ListConversationsParams
        assertEquals(2, params.page)
        assertEquals(10, params.page_size)
    }

    @Test
    fun `PP-106 ReadConversationParams decodes from JSON`() {
        val td = ProfileAnalysisTools.fromName("read_conversation")!!
        val params = td.decodeArguments("""{"conversation_id":"abc123","page":3}""") as ReadConversationParams
        assertEquals("abc123", params.conversation_id)
        assertEquals(3, params.page)
    }

    @Test
    fun `PP-107 RecordFindingParams decodes category enum`() {
        val td = ProfileAnalysisTools.fromName("record_finding")!!
        val params = td.decodeArguments("""{"content":"test","category":"PATTERN"}""") as RecordFindingParams
        assertEquals(FindingCategory.PATTERN, params.category)
    }

    @Test
    fun `PP-108 ReadFindingsParams defaults category to null`() {
        val td = ProfileAnalysisTools.fromName("read_findings")!!
        val params = td.decodeArguments("{}") as ReadFindingsParams
        assertEquals(null, params.category)
    }
}

