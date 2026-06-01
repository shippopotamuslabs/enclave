package com.browntowndev.enclave.domain.model.memory

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PsychProfileMemoryTest {
    @Test
    fun `PP-05 PSYCH_PROFILE category exists in MemoryCategory enum`() {
        val names = MemoryCategory.entries.map { it.name }
        assertTrue(names.contains("PSYCH_PROFILE"))
    }

    @Test
    fun `PP-06 PSYCH_PROFILE is excluded from semantic retrieval candidates`() {
        assertTrue(MemoryCategory.PSYCH_PROFILE in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES)
    }

    @Test
    fun `PP-07 CORE_IDENTITY and PREFERENCES remain excluded from semantic retrieval`() {
        assertTrue(MemoryCategory.CORE_IDENTITY in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES)
        assertTrue(MemoryCategory.PREFERENCES in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES)
    }

    @Test
    fun `PP-08 FACTS and PROJECT_CONTEXT remain eligible for semantic retrieval`() {
        assertFalse(MemoryCategory.FACTS in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES)
        assertFalse(MemoryCategory.PROJECT_CONTEXT in MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES)
    }
}
