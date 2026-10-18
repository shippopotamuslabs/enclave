package com.browntowndev.enclave.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
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
class DefaultModelRepositorySyncTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var syncTrigger: RecordingCloudSyncTrigger
    private lateinit var defaultModelsDao: DefaultModelsDao

    private lateinit var repository: DefaultModelRepositoryImpl

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        syncTrigger = RecordingCloudSyncTrigger()
        defaultModelsDao = database.defaultModelsDao()
        repository =
            DefaultModelRepositoryImpl(
                syncTrigger = syncTrigger,
                defaultModelsDao = defaultModelsDao,
                localModelConfigurationsDao = database.localModelConfigurationsDao(),
                localModelsDao = database.localModelsDao(),
                apiModelConfigurationsDao = database.apiModelConfigurationsDao(),
                apiCredentialsDao = database.apiCredentialsDao(),
                ttsProviderDao = database.ttsProviderDao(),
                mediaProviderDao = database.mediaProviderDao(),
                localEncryption = FakeLocalDataEncryptionService(),
            )
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    /** Seeds the local model + configuration rows the default's foreign keys require. */
    private suspend fun seedLocalConfiguration() {
        database.localModelsDao().upsert(
            com.browntowndev.enclave.core.data.local.LocalModelEntity(
                id = com.browntowndev.enclave.domain.model.config.LocalModelId("model-1"),
                modelFileFormat = com.browntowndev.enclave.domain.model.inference.ModelFileFormat.GGUF,
                huggingFaceModelName = "hf/model",
                remoteFilename = "model.gguf",
                localFilename = "model.gguf",
                sha256 = "abc",
                sizeInBytes = 1L,
            ),
        )
        database.localModelConfigurationsDao().upsert(
            com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity(
                id = LocalModelConfigurationId("id"),
                localModelId = com.browntowndev.enclave.domain.model.config.LocalModelId("model-1"),
                displayName = "Config",
            ),
        )
    }

    @Test
    fun `setDefault sets pendingSync to 1 and triggers sync`() =
        runTest {
            seedLocalConfiguration()

            repository.setDefault(ModelType.FAST, LocalModelConfigurationId("id"), null, null, null)

            val stored = defaultModelsDao.getDefault(ModelType.FAST)
            assertEquals(1, stored?.pendingSync)
            assertEquals(1, syncTrigger.triggerCalls)
        }

    @Test
    fun `clearDefault triggers sync`() =
        runTest {
            seedLocalConfiguration()
            repository.setDefault(ModelType.FAST, LocalModelConfigurationId("id"), null, null, null)

            repository.clearDefault(ModelType.FAST)

            assertNull(defaultModelsDao.getDefault(ModelType.FAST))
            assertEquals(2, syncTrigger.triggerCalls)
        }
}
