package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.data.local.MemoryEmbeddingMatch
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.memory.MemoryRetrievalPolicy
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

/**
 * Translated from MockK to hand-written fakes (ENC-28): the DAOs under test
 * are recording fakes (the assertions target the entities and raw SQL the
 * repository builds), the embedding engine is the `:core:testing` fake, the
 * sync trigger records its calls, and the incidental cleanup DAO /
 * read-repair applier are the real implementations over an in-memory Room
 * database.
 */
@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class MemoriesRepositoryTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var memoriesDao: FakeMemoriesDao
    private lateinit var embeddingDao: FakeEmbeddingDao
    private lateinit var embeddingEngine: FakeEmbeddingEngine
    private lateinit var repository: MemoriesRepository
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private val healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService(), FakeLoggingPort())

    @BeforeEach
    fun setup() {
        syncTrigger = RecordingCloudSyncTrigger()
        database = createInMemoryEnclaveDatabase()
        memoriesDao = FakeMemoriesDao()
        embeddingDao = FakeEmbeddingDao()
        embeddingEngine = FakeEmbeddingEngine()
        repository =
            MemoriesRepositoryImpl(
                syncTrigger,
                memoriesDao,
                embeddingDao,
                embeddingEngine,
                healer,
                database.realReadRepairApplier(),
                cleanupDao = database.cleanupDao(),
                transactionProvider = FakeTransactionProvider(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getAllMemoriesFlow returns flow from dao`() =
        runTest {
            val entities =
                listOf(
                    MemoriesEntity(category = MemoryCategory.FACTS, content = "Fact 1"),
                )
            memoriesDao.flowEntities = entities

            val result = repository.getAllMemoriesFlow().first()

            assertEquals(entities.size, result.size)
            assertEquals(entities[0].content, result[0].content)
            assertEquals(1, memoriesDao.getAllMemoriesFlowCalls)
        }

    @Test
    fun `getCoreMemories returns core identity, preferences, and psych profile`() =
        runTest {
            val coreEntities =
                listOf(
                    MemoriesEntity(category = MemoryCategory.CORE_IDENTITY, content = "I am an AI"),
                    MemoriesEntity(category = MemoryCategory.PREFERENCES, content = "I like blue"),
                    MemoriesEntity(category = MemoryCategory.PSYCH_PROFILE, content = "Profile content."),
                )
            coreEntities.forEach { memoriesDao.entities[it.id] = it }

            val result = repository.getCoreMemories()

            assertEquals(coreEntities.size, result.size)
            assertEquals(coreEntities[0].content, result[0].content)
            assertEquals(
                listOf(
                    listOf(MemoryCategory.CORE_IDENTITY, MemoryCategory.PREFERENCES, MemoryCategory.PSYCH_PROFILE),
                ),
                memoriesDao.getMemoriesByCategoriesCalls,
            )
        }

    @Test
    fun `insertMemory inserts into dao and updates embedding`() =
        runTest {
            val category = MemoryCategory.FACTS
            val content = "New fact"
            val vector = floatArrayOf(0.1f, 0.2f)
            embeddingEngine.embeddingsByText[content] = vector

            val result = repository.insertMemory(category, content)

            assertEquals(category, result.category)
            assertEquals(content, result.content)
            assertEquals(1, memoriesDao.insertCalls.size)
            assertEquals(listOf(content), embeddingEngine.queries)

            val sql = embeddingDao.insertEmbeddingQueries.single()
            assert(sql.contains("INSERT OR REPLACE INTO memory_embeddings"))
            assert(sql.contains(result.id))
            assert(sql.contains("[0.1,0.2]"))
        }

    @Test
    fun `insertMemory stores content encrypted and returns plaintext domain model`() =
        runTest {
            val category = MemoryCategory.FACTS
            val content = "Private memory"
            embeddingEngine.embeddingsByText[content] = floatArrayOf(0.1f)

            val result = repository.insertMemory(category, content)

            assertEquals(content, result.content)
            val inserted = memoriesDao.insertCalls.single()
            assertEquals(category, inserted.category)
            assertEquals("PCENC:v1:aes-gcm:Private memory", inserted.content)
        }

    @Test
    fun `updateMemory updates dao and embedding`() =
        runTest {
            val id = "memory-1"
            val existing = MemoriesEntity(id = id, category = MemoryCategory.FACTS, content = "Old content")
            val newContent = "Updated content"
            val vector = floatArrayOf(0.3f, 0.4f)
            memoriesDao.entities[id] = existing
            embeddingEngine.embeddingsByText[newContent] = vector

            repository.updateMemory(id, newContent)

            val updated = memoriesDao.updateCalls.single()
            assertEquals(id, updated.id)
            assertEquals("PCENC:v1:aes-gcm:$newContent", updated.content)
            assertEquals(listOf(newContent), embeddingEngine.queries)

            val sql = embeddingDao.insertEmbeddingQueries.single()
            assert(sql.contains("INSERT OR REPLACE INTO memory_embeddings"))
            assert(sql.contains(id))
            assert(sql.contains("[0.3,0.4]"))
        }

    @Test
    fun `deleteMemory deletes from dao and removes embedding`() =
        runTest {
            val id = "memory-1"
            val existing = MemoriesEntity(id = id, category = MemoryCategory.FACTS, content = "Content")
            memoriesDao.entities[id] = existing

            repository.deleteMemory(id)

            assertEquals(listOf(id), memoriesDao.softDeleteCalls.map { it.first })

            val sql = embeddingDao.insertEmbeddingQueries.single()
            assert(sql.contains("DELETE FROM memory_embeddings WHERE id = 'memory-1'"))
        }

    @Test
    fun `searchMemories queries embedding matches and loads memories`() =
        runTest {
            val queryVector = floatArrayOf(0.5f, 0.6f)
            val limit = 3
            val fact =
                MemoriesEntity(
                    id = "fact-1",
                    category = MemoryCategory.FACTS,
                    content = "Result",
                )
            embeddingDao.matches = listOf(MemoryEmbeddingMatch(id = "fact-1", distance = 0.2f))
            memoriesDao.entities[fact.id] = fact

            val result = repository.searchMemories(queryVector, limit)

            assertEquals(1, result.size)
            assertEquals(fact.content, result[0].content)

            val sql = embeddingDao.searchMatchesQueries.single()
            assert(sql.contains("SELECT id, distance"))
            assert(sql.contains("FROM memory_embeddings"))
            assert(sql.contains("WHERE vector MATCH '[0.5,0.6]' AND k = 3"))
        }

    @Test
    fun `searchMemories excludes categories and filters by distance`() =
        runTest {
            val queryVector = floatArrayOf(0.5f, 0.6f)
            val relevantFact =
                MemoriesEntity(
                    id = "fact-1",
                    category = MemoryCategory.FACTS,
                    content = "User owns a cat",
                )
            val irrelevantFact =
                MemoriesEntity(
                    id = "fact-2",
                    category = MemoryCategory.FACTS,
                    content = "Unrelated fact",
                )
            val coreIdentity =
                MemoriesEntity(
                    id = "core-1",
                    category = MemoryCategory.CORE_IDENTITY,
                    content = "User is a developer",
                )
            embeddingDao.matches =
                listOf(
                    MemoryEmbeddingMatch(id = "core-1", distance = 0.1f),
                    MemoryEmbeddingMatch(id = "fact-2", distance = 0.9f),
                    MemoryEmbeddingMatch(id = "fact-1", distance = 0.4f),
                )
            memoriesDao.entities[coreIdentity.id] = coreIdentity
            memoriesDao.entities[irrelevantFact.id] = irrelevantFact
            memoriesDao.entities[relevantFact.id] = relevantFact

            val result =
                repository.searchMemories(
                    queryVector = queryVector,
                    limit = 5,
                    maxDistance = MemoryRetrievalPolicy.MAX_L2_DISTANCE,
                    excludeCategories = MemoryRetrievalPolicy.CHAT_EXCLUDED_CATEGORIES,
                )

            assertEquals(1, result.size)
            assertEquals(relevantFact.content, result[0].content)

            val sql = embeddingDao.searchMatchesQueries.single()
            assert(sql.contains("AND k = ${MemoryRetrievalPolicy.SEARCH_CANDIDATE_POOL}"))
        }

    @Test
    fun `searchMemories skips deleted memories`() =
        runTest {
            val queryVector = floatArrayOf(0.5f, 0.6f)
            val deletedFact =
                MemoriesEntity(
                    id = "fact-1",
                    category = MemoryCategory.FACTS,
                    content = "Deleted",
                    deleted = true,
                )
            val activeFact =
                MemoriesEntity(
                    id = "fact-2",
                    category = MemoryCategory.FACTS,
                    content = "Active",
                )
            embeddingDao.matches =
                listOf(
                    MemoryEmbeddingMatch(id = "fact-1", distance = 0.1f),
                    MemoryEmbeddingMatch(id = "fact-2", distance = 0.2f),
                )
            memoriesDao.entities[deletedFact.id] = deletedFact
            memoriesDao.entities[activeFact.id] = activeFact

            val result = repository.searchMemories(queryVector, limit = 5)

            assertEquals(1, result.size)
            assertEquals(activeFact.content, result[0].content)
        }

    @Test
    fun `insertMemory calls triggerSync once`() =
        runTest {
            embeddingEngine.uniformEmbedding = floatArrayOf(0.1f)

            repository.insertMemory(MemoryCategory.FACTS, "New fact")

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `updateMemory calls triggerSync once`() =
        runTest {
            val id = "mem-1"
            memoriesDao.entities[id] = MemoriesEntity(id = id, category = MemoryCategory.FACTS, content = "Old")
            embeddingEngine.uniformEmbedding = floatArrayOf(0.1f)

            repository.updateMemory(id, "New content")

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteMemory calls triggerSync once`() =
        runTest {
            val id = "mem-1"
            memoriesDao.entities[id] = MemoriesEntity(id = id, category = MemoryCategory.FACTS, content = "Content")

            repository.deleteMemory(id)

            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `getAllMemoriesFlow read-only does not call triggerSync`() =
        runTest {
            memoriesDao.flowEntities = emptyList()

            repository.getAllMemoriesFlow().first()

            assertEquals(0, syncTrigger.triggerCalls)
        }

    @Test
    fun `insertMemory inserts entity with pendingSync true`() =
        runTest {
            embeddingEngine.uniformEmbedding = floatArrayOf(0.1f)

            repository.insertMemory(MemoryCategory.FACTS, "New fact")

            assertEquals(1, memoriesDao.insertCalls.single().pendingSync)
        }

    @Test
    fun `updateMemory updates entity with pendingSync true`() =
        runTest {
            val id = "mem-1"
            memoriesDao.entities[id] = MemoriesEntity(id = id, category = MemoryCategory.FACTS, content = "Old")
            embeddingEngine.uniformEmbedding = floatArrayOf(0.1f)

            repository.updateMemory(id, "New content")

            assertEquals(1, memoriesDao.updateCalls.single().pendingSync)
        }

    // S15: Memories persist encrypted content and return plaintext domain content
    @Test
    fun `S15 insertMemory persists encrypted content and getMemoryById returns plaintext domain content`() =
        runTest {
            val content = "likes quiet mornings"
            val category = MemoryCategory.PREFERENCES
            embeddingEngine.embeddingsByText[content] = floatArrayOf(0.1f)

            val result = repository.insertMemory(category, content)

            // Stored entity must have encrypted content
            assertTrue(memoriesDao.insertCalls.single().content.startsWith("PCENC:v1:aes-gcm:"))

            // getMemoryById should return plaintext domain content
            val encryptedContent = "PCENC:v1:aes-gcm:$content"
            memoriesDao.entities[result.id] =
                MemoriesEntity(id = result.id, category = category, content = encryptedContent)

            val domainMemory = repository.getMemoryById(result.id)

            assertEquals(content, domainMemory?.content)
        }
}
