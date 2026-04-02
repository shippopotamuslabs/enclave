package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiCredentialsDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var dao: ApiCredentialsDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.apiCredentialsDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve API credentials`() = runTest {
        val entity = ApiCredentialsEntity(
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "my_openai_key",
            displayName = "GPT-4o"
        )
        val id = dao.upsert(entity)
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals("my_openai_key", retrieved?.credentialAlias)
    }

    @Test
    fun `observe all credentials ordered by updated_at DESC`() = runTest {
        val entity1 = ApiCredentialsEntity(
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4",
            credentialAlias = "key1",
            displayName = "GPT-4",
            updatedAt = 1000
        )
        val entity2 = ApiCredentialsEntity(
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude",
            credentialAlias = "key2",
            displayName = "Claude",
            updatedAt = 2000
        )
        dao.upsert(entity1)
        dao.upsert(entity2)

        val list = dao.observeAll().first()
        assertEquals(2, list.size)
        assertEquals("key2", list[0].credentialAlias)
        assertEquals("key1", list[1].credentialAlias)
    }
    
    @Test
    fun `duplicate API credential identity is rejected via upsert replace`() = runTest {
        val entity1 = ApiCredentialsEntity(
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "key1",
            displayName = "GPT-4o",
            baseUrl = "https://api.openai.com/v1"
        )
        val entity2 = ApiCredentialsEntity(
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "key2",
            displayName = "GPT-4o Duplicate",
            baseUrl = "https://api.openai.com/v1"
        )
        dao.upsert(entity1)
        dao.upsert(entity2)
        
        val list = dao.getAll()
        assertEquals(1, list.size)
    }
}
