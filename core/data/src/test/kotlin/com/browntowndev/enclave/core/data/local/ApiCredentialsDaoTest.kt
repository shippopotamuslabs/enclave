package com.browntowndev.enclave.core.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.UUID
import kotlin.test.assertFailsWith

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ApiCredentialsDaoTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var dao: ApiCredentialsDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.apiCredentialsDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private fun nextId() = ApiCredentialsId(UUID.randomUUID().toString())

    @Test
    fun `SCN-DAO-001 ApiCredentialsDao Round-Trip`() =
        runTest {
            val entityId = nextId()
            val entity =
                ApiCredentialsEntity(
                    id = entityId,
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "openai-main",
                    displayName = "PCENC:v1:…",
                )
            dao.upsert(entity)
            val retrieved = dao.getById(entityId)
            assertNotNull(retrieved)
            assertEquals(ApiProvider.OPENAI, retrieved?.provider)
            assertEquals("openai-main", retrieved?.credentialAlias)
            assertFalse(ApiCredentialsEntity::class.members.any { it.name == "modelId" })
        }

    @Test
    fun `observe all credentials ordered by updated_at DESC`() =
        runTest {
            val entity1 =
                ApiCredentialsEntity(
                    id = nextId(),
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key1",
                    displayName = "GPT-4",
                    updatedAt = 1000,
                )
            val entity2 =
                ApiCredentialsEntity(
                    id = nextId(),
                    provider = ApiProvider.ANTHROPIC,
                    credentialAlias = "key2",
                    displayName = "Claude",
                    updatedAt = 2000,
                )
            dao.upsert(entity1)
            dao.upsert(entity2)

            val list = dao.observeAll().first()
            assertEquals(2, list.size)
            assertEquals("key2", list[0].credentialAlias)
            assertEquals("key1", list[1].credentialAlias)
        }

    @Test
    fun `duplicate API credential identity can be inserted as separate rows`() =
        runTest {
            val entity1 =
                ApiCredentialsEntity(
                    id = nextId(),
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key1",
                    displayName = "GPT-4o",
                    baseUrl = "https://api.openai.com/v1",
                    updatedAt = 1_000L,
                )
            val entity2 =
                ApiCredentialsEntity(
                    id = nextId(),
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key2",
                    displayName = "GPT-4o Duplicate",
                    baseUrl = "https://api.openai.com/v1",
                    updatedAt = 2_000L,
                )
            dao.upsert(entity1)
            dao.upsert(entity2)

            val list = dao.getAll()
            assertEquals(2, list.size)
            assertEquals(listOf("key2", "key1"), list.map { it.credentialAlias })
        }

    @Test
    fun `duplicate API key signature is rejected`() =
        runTest {
            val signature = "duplicate-signature"
            val entity1 =
                ApiCredentialsEntity(
                    id = nextId(),
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "key1",
                    displayName = "GPT-4o",
                    baseUrl = "https://api.openai.com/v1",
                    apiKeySignature = signature,
                )
            val entity2 =
                entity1.copy(
                    id = nextId(),
                    credentialAlias = "key2",
                    displayName = "GPT-4o Duplicate",
                )
            dao.insert(entity1)

            assertFailsWith<SQLiteConstraintException> {
                runBlocking {
                    dao.insert(entity2)
                }
            }
        }
}
