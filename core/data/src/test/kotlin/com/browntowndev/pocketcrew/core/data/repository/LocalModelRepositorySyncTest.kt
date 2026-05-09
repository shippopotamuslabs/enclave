package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocalModelRepositorySyncTest {

    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private val modelsDao = mockk<LocalModelsDao>(relaxed = true)
    private val configsDao = mockk<LocalModelConfigurationsDao>(relaxed = true)
    private val defaultModelsDao = mockk<DefaultModelsDao>(relaxed = true)
    private val transactionProvider = FakeTransactionProvider()

    private lateinit var repository: LocalModelRepositoryImpl

    @BeforeEach
    fun setup() {
        repository = LocalModelRepositoryImpl(
            syncTrigger = syncTrigger,
            modelsDao = modelsDao,
            configsDao = configsDao,
            defaultModelsDao = defaultModelsDao,
            transactionProvider = transactionProvider
        )
    }

    @Test
    fun `upsertLocalConfiguration sets pendingSync to 1 and triggers sync`() = runTest {
        val config = LocalModelConfiguration(
            id = LocalModelConfigurationId("id"),
            localModelId = LocalModelId("model-id"),
            displayName = "Config",
            maxTokens = 100,
            contextWindow = 100,
            temperature = 0.5,
            topP = 0.5,
            topK = 50,
            repetitionPenalty = 1.0,
            systemPrompt = ""
        )

        repository.upsertLocalConfiguration(config)

        coVerify {
            configsDao.upsert(match { it.pendingSync == 1 })
            syncTrigger.trigger()
        }
    }

    @Test
    fun `deleteConfiguration triggers sync`() = runTest {
        repository.deleteConfiguration(LocalModelConfigurationId("id"))

        coVerify {
            configsDao.softDeleteById(LocalModelConfigurationId("id"), any())
            syncTrigger.trigger()
        }
    }

    private class FakeTransactionProvider : TransactionProvider {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }
}
