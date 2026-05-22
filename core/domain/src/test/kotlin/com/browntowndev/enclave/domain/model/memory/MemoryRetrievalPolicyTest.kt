package com.browntowndev.enclave.domain.model.memory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoryRetrievalPolicyTest {
    @Test
    fun `chat excluded categories cover always-injected core memories`() {
        assertTrue(MemoryCategory.CORE_IDENTITY in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES)
        assertTrue(MemoryCategory.PREFERENCES in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES)
        assertEquals(2, MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES.size)
    }

    @Test
    fun `candidate pool exceeds chat retrieval limit`() {
        assertTrue(MemoryRetrievalPolicy.SEARCH_CANDIDATE_POOL > MemoryRetrievalPolicy.CHAT_RETRIEVAL_LIMIT)
    }
}
