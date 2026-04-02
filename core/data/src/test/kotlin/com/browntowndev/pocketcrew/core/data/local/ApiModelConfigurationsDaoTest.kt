package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApiModelConfigurationsDaoTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var credDao: ApiCredentialsDao
    private lateinit var configDao: ApiModelConfigurationsDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()
        credDao = database.apiCredentialsDao()
        configDao = database.apiModelConfigurationsDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve an API tuning preset`() = runTest {
        val credId = credDao.upsert(ApiCredentialsEntity(
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "key1",
            displayName = "GPT-4o"
        ))

        val config = ApiModelConfigurationEntity(
            apiCredentialsId = credId,
            displayName = "Default",
            temperature = 0.7,
            maxTokens = 4096
        )
        val configId = configDao.upsert(config)
        
        val retrieved = configDao.getById(configId)
        assertNotNull(retrieved)
        assertEquals("Default", retrieved?.displayName)
    }

    @Test
    fun `multiple presets per API credential`() = runTest {
        val credId = credDao.upsert(ApiCredentialsEntity(
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4o",
            credentialAlias = "key1",
            displayName = "GPT-4o"
        ))

        configDao.upsert(ApiModelConfigurationEntity(apiCredentialsId = credId, displayName = "Fast"))
        configDao.upsert(ApiModelConfigurationEntity(apiCredentialsId = credId, displayName = "Thorough"))

        val allConfigs = configDao.getAllForCredentials(credId)
        assertEquals(2, allConfigs.size)
    }
}