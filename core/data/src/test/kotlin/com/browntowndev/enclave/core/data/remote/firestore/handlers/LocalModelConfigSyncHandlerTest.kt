package com.browntowndev.enclave.core.data.remote.firestore.handlers

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.atomic.AtomicBoolean
import org.robolectric.annotation.Config as RobolectricConfig

@ExtendWith(RobolectricExtension::class)
@RobolectricConfig(sdk = [34])
class LocalModelConfigSyncHandlerTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var handler: LocalModelConfigSyncHandler
    private lateinit var firestore: FirebaseFirestore

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        firestore = mockk(relaxed = true)
        handler = LocalModelConfigSyncHandler(createContext(database, firestore))
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `upsertLocal remaps cloud local_model_id to local sha256 row`() =
        runTest {
            val localModelId = LocalModelId("local-room-model-id")
            val cloudModelId = LocalModelId("cloud-model-doc-id")
            val configId = LocalModelConfigurationId("550e8400-e29b-41d4-a716-446655440002")

            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = localModelId,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    huggingFaceModelName = "gemma",
                    remoteFilename = "gemma.litertlm",
                    localFilename = "gemma.litertlm",
                    sha256 = "sha-gemma",
                    sizeInBytes = 1L,
                ),
            )

            val modelDoc = mockk<DocumentSnapshot>(relaxed = true)
            every { modelDoc.exists() } returns true
            every { modelDoc.getString("sha256") } returns "sha-gemma"
            val modelRef = mockk<DocumentReference>(relaxed = true)
            every { modelRef.get() } returns Tasks.forResult(modelDoc)
            every { firestore.document("users/user-1/local_models/cloud-model-doc-id") } returns modelRef

            val remoteConfig =
                LocalModelConfigurationEntity(
                    id = configId,
                    localModelId = cloudModelId,
                    displayName = "Fast",
                    updatedAt = 2_000L,
                )

            handler.upsertLocal(remoteConfig, mockk(relaxed = true), overwrite = true)

            val stored = database.localModelConfigurationsDao().getById(configId)
            assertEquals(localModelId, stored?.localModelId)
        }

    private fun createContext(
        database: EnclaveDatabase,
        firestore: FirebaseFirestore,
    ): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        return FirestoreSyncContext(
            firestore = firestore,
            daos =
                testFirestoreSyncDaos(
                    localModelsDao = database.localModelsDao(),
                    localModelConfigurationsDao = database.localModelConfigurationsDao(),
                ),
            localEncryption = localEncryption,
            sensitiveSelfHealer = SensitivePersistenceSelfHealer(localEncryption, loggingPort),
            firestoreSensitiveFieldRepairer = mockk(relaxed = true),
            cryptoManager = mockk<CryptoManagerPort>(relaxed = true),
            loggingPort = loggingPort,
            apiKeyManager = mockk(relaxed = true),
            embeddingCoordinator = mockk(relaxed = true),
            userIdProvider = { "user-1" },
            purgeInProgress = AtomicBoolean(false),
        )
    }
}
