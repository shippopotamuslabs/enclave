package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LocalModelRepositorySyncTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private lateinit var configsDao: LocalModelConfigurationsDao
    private val transactionProvider = FakeTransactionProvider()

    private lateinit var repository: LocalModelRepositoryImpl

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        syncTrigger = RecordingCloudSyncTrigger()
        configsDao = database.localModelConfigurationsDao()
        repository =
            LocalModelRepositoryImpl(
                syncTrigger = syncTrigger,
                modelsDao = database.localModelsDao(),
                configsDao = configsDao,
                defaultModelsDao = database.defaultModelsDao(),
                transactionProvider = transactionProvider,
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = database.realReadRepairApplier(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    /** Seeds the parent local-model row the configuration table's foreign key requires. */
    private suspend fun seedLocalModel() {
        database.localModelsDao().upsert(
            LocalModelEntity(
                id = LocalModelId("model-id"),
                modelFileFormat = ModelFileFormat.GGUF,
                huggingFaceModelName = "hf/model",
                remoteFilename = "model.gguf",
                localFilename = "model.gguf",
                sha256 = "abc",
                sizeInBytes = 1L,
            ),
        )
    }

    private fun config() =
        LocalModelConfiguration(
            id = LocalModelConfigurationId("id"),
            localModelId = LocalModelId("model-id"),
            displayName = "Config",
            maxTokens = 100,
            contextWindow = 100,
            temperature = 0.5,
            topP = 0.5,
            topK = 50,
            repetitionPenalty = 1.0,
            systemPrompt = "",
        )

    @Test
    fun `upsertLocalConfiguration sets pendingSync to 1 and triggers sync`() =
        runTest {
            seedLocalModel()

            repository.upsertLocalConfiguration(config())

            val stored = configsDao.getById(LocalModelConfigurationId("id"))
            assertEquals(1, stored?.pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `deleteConfiguration triggers sync`() =
        runTest {
            seedLocalModel()
            repository.upsertLocalConfiguration(config())

            repository.deleteConfiguration(LocalModelConfigurationId("id"))

            assertNull(configsDao.getById(LocalModelConfigurationId("id")))
            assertEquals(2, syncTrigger.triggerCalls)
        }
}
