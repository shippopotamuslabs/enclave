package com.browntowndev.enclave.domain.search

import com.browntowndev.enclave.domain.model.inference.ToolMatchMethod
import com.browntowndev.enclave.domain.usecase.chat.ToolSearchTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolBm25IndexTest {
    private val descriptions =
        ToolSearchTestFixtures.bm25Catalog.associate { entry -> entry.name to entry.description }

    @Test
    fun `TSD-04 BM25 ranks append_blocks for name-like query`() {
        val index = ToolSearchTestFixtures.bm25Index()
        val matches = index.search("append_blocks", limit = 5, descriptionsByName = descriptions)

        assertTrue(matches.isNotEmpty())
        assertEquals("append_blocks", matches.first().name)
        assertEquals(ToolMatchMethod.BM25, matches.first().matchMethod)
        assertTrue(matches.first().score > 0.0)
    }

    @Test
    fun `TSD-05 BM25 ranks append_blocks for description keyword`() {
        val index = ToolSearchTestFixtures.bm25Index()
        val matches = index.search("in-progress PDF document", limit = 5, descriptionsByName = descriptions)

        assertTrue(matches.isNotEmpty())
        assertEquals("append_blocks", matches.first().name)
        assertEquals(ToolMatchMethod.BM25, matches.first().matchMethod)
    }

    @Test
    fun `TSD-06 BM25 ranks memory tool for memory tag query`() {
        val index = ToolSearchTestFixtures.bm25Index()
        val matches = index.search("memory recall", limit = 5, descriptionsByName = descriptions)

        assertTrue(matches.isNotEmpty())
        assertEquals("search_chat_history", matches.first().name)
        assertEquals(ToolMatchMethod.BM25, matches.first().matchMethod)
    }
}
