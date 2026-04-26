package com.browntowndev.pocketcrew.domain.model.memory

import java.util.UUID

enum class MemoryCategory {
    CORE_IDENTITY, PREFERENCES, FACTS, PROJECT_CONTEXT
}

data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val category: MemoryCategory,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
