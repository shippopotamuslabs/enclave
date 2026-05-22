package com.browntowndev.enclave.core.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.browntowndev.enclave.core.data.local.CleanupDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoriesRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val memoriesDao: MemoriesDao,
        private val embeddingDao: EmbeddingDao,
        private val embeddingEngine: EmbeddingEnginePort,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
        private val cleanupDao: CleanupDao,
        private val transactionProvider: TransactionProvider,
    ) : MemoriesRepository {
        private fun triggerSync() = syncTrigger.trigger()

        override fun getAllMemoriesFlow(): Flow<List<Memory>> =
            memoriesDao.getAllMemoriesFlow().mapLatest { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { toDomain(it, repairs) }
                readRepairApplier.apply(repairs)
                out
            }

        override suspend fun getCoreMemories(): List<Memory> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                memoriesDao
                    .getMemoriesByCategories(
                        listOf(MemoryCategory.CORE_IDENTITY, MemoryCategory.PREFERENCES),
                    ).map { toDomain(it, repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun insertMemory(
            category: MemoryCategory,
            content: String,
        ): Memory {
            val entity =
                SensitiveEntityFieldEncryptors.encrypt(
                    MemoriesEntity(category = category, content = content, pendingSync = 1),
                    healer,
                )
            memoriesDao.insertMemory(entity)
            updateEmbedding(entity.id, content)
            triggerSync()
            return toDomain(entity, null)
        }

        override suspend fun updateMemory(
            id: String,
            content: String,
            category: MemoryCategory?,
        ) {
            val existing = memoriesDao.getMemoryById(id) ?: return
            val updated =
                SensitiveEntityFieldEncryptors.encrypt(
                    existing.copy(
                        content = content,
                        category = category ?: existing.category,
                        updatedAt = System.currentTimeMillis(),
                        pendingSync = 1,
                    ),
                    healer,
                )
            memoriesDao.updateMemory(updated)
            updateEmbedding(updated.id, content)
            triggerSync()
        }

        override suspend fun getMemoryById(id: String): Memory? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = memoriesDao.getMemoryById(id)?.let { toDomain(it, repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun deleteMemory(id: String) {
            memoriesDao.softDeleteById(id)
            deleteEmbedding(id)
            triggerSync()
        }

        override suspend fun deleteAllMemories() {
            transactionProvider.runInTransaction {
                cleanupDao.deleteMemories()
                cleanupDao.deleteMemoryEmbeddings(SimpleSQLiteQuery("DELETE FROM memory_embeddings"))
            }
            syncTrigger.triggerCloudMemoriesPurge()
        }

        private suspend fun updateEmbedding(
            id: String,
            content: String,
        ) {
            val vector = embeddingEngine.getEmbedding(content)
            val vectorString = vector.joinToString(",", prefix = "[", postfix = "]")
            val query = SimpleSQLiteQuery("INSERT OR REPLACE INTO memory_embeddings(id, vector) VALUES ('$id', '$vectorString')")
            embeddingDao.insertEmbedding(query)
        }

        private suspend fun deleteEmbedding(id: String) {
            val query = SimpleSQLiteQuery("DELETE FROM memory_embeddings WHERE id = '$id'")
            embeddingDao.insertEmbedding(query) // Re-using insert method for general execution
        }

        override suspend fun searchMemories(
            queryVector: FloatArray,
            limit: Int,
        ): List<Memory> {
            val vectorString = queryVector.joinToString(",", prefix = "[", postfix = "]")
            val sql =
                """
                SELECT m.* FROM memories m
                JOIN memory_embeddings me ON m.id = me.id
                WHERE me.vector MATCH '$vectorString' AND k = $limit
                ORDER BY distance
                """.trimIndent()

            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = memoriesDao.searchMemoriesRaw(SimpleSQLiteQuery(sql)).map { toDomain(it, repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        private fun toDomain(
            entity: MemoriesEntity,
            readRepairs: MutableList<SensitiveRoomReadRepair>?,
        ): Memory {
            val decrypted = SensitiveEntityFieldEncryptors.decrypt(entity, healer, readRepairs)
            return Memory(
                id = decrypted.id,
                category = decrypted.category,
                content = decrypted.content,
                createdAt = decrypted.createdAt,
                updatedAt = decrypted.updatedAt,
            )
        }
    }
