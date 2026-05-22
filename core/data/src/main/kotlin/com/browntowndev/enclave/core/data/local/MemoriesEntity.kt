package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import java.util.UUID

@Entity(tableName = "memories")
data class MemoriesEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "category")
    val category: MemoryCategory,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pending_sync", defaultValue = "0")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
) {
    fun toDomain(): Memory =
        Memory(
            id = id,
            category = category,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun fromDomain(memory: Memory): MemoriesEntity =
            MemoriesEntity(
                id = memory.id,
                category = memory.category,
                content = memory.content,
                createdAt = memory.createdAt,
                updatedAt = memory.updatedAt,
            )
    }
}
