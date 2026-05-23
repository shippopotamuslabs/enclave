package com.browntowndev.enclave.core.data.remote.firestore.handlers

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LocalModelEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
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
import org.robolectric.annotation.Config as RobolectricConfig
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.atomic.AtomicBoolean

@ExtendWith(RobolectricExtension::class)
@RobolectricConfig(sdk = [34])
class LocalModelSyncHandlerTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var handler: LocalModelSyncHandler

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        handler = LocalModelSyncHandler(createContext(database))
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `SCN-SYNC-001 toFirestore writes display_name`() =
        runTest {
        val entity =
            LocalModelEntity(
                id = LocalModelId("model-1"),
                modelFileFormat = ModelFileFormat.GGUF,
                displayName = "Qwen 3.5 2B",
                huggingFaceModelName = "Jackrong/Qwen3.5-2B-Claude-4.6-Opus-Reasoning-Distilled-GGUF",
                remoteFilename = "model.gguf",
                localFilename = "model.gguf",
                sha256 = "qwen-sha",
                sizeInBytes = 100L,
                updatedAt = 1_700_000_000_000L,
                deleted = false,
            )

        val payload = handler.toFirestore(entity)

        assertEquals("Qwen 3.5 2B", payload["display_name"])
        }

    @Test
    fun `SCN-SYNC-002 fromFirestore reads display_name`() {
        val doc =
            mockDocumentSnapshot(
                mapOf(
                    "display_name" to "Gemma 4 E4B",
                    "huggingface_model_name" to "litert-community/gemma-4-E4B-it-litert-lm",
                    "sha256" to "gemma-sha",
                    "model_file_format" to "LITERTLM",
                    "remote_filename" to "gemma-4-E4B-it.litertlm",
                    "local_filename" to "gemma-4-E4B-it.litertlm",
                    "size_in_bytes" to 3_659_530_240L,
                    "updated_at" to 1_700_000_000_000L,
                    "deleted" to false,
                ),
            )

        val entity = handler.fromFirestore(doc)

        assertEquals("Gemma 4 E4B", entity.displayName)
    }

    @Test
    fun `SCN-SYNC-003 remote newer updated_at overwrites display_name`() =
        runTest {
            val modelId = LocalModelId("model-1")
            database.localModelsDao().upsert(
                LocalModelEntity(
                    id = modelId,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    displayName = "Old Label",
                    huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                    remoteFilename = "gemma-4-E4B-it.litertlm",
                    localFilename = "gemma-4-E4B-it.litertlm",
                    sha256 = "gemma-sha",
                    sizeInBytes = 100L,
                    updatedAt = 1000L,
                    pendingSync = 0,
                ),
            )

            val remoteEntity =
                LocalModelEntity(
                    id = LocalModelId("remote-id"),
                    modelFileFormat = ModelFileFormat.LITERTLM,
                    displayName = "Gemma 4 E4B",
                    huggingFaceModelName = "litert-community/gemma-4-E4B-it-litert-lm",
                    remoteFilename = "gemma-4-E4B-it.litertlm",
                    localFilename = "gemma-4-E4B-it.litertlm",
                    sha256 = "gemma-sha",
                    sizeInBytes = 100L,
                    updatedAt = 2000L,
                    deleted = false,
                )
            val doc =
                mockDocumentSnapshot(
                    mapOf(
                        "display_name" to "Gemma 4 E4B",
                        "huggingface_model_name" to "litert-community/gemma-4-E4B-it-litert-lm",
                        "sha256" to "gemma-sha",
                        "model_file_format" to "LITERTLM",
                        "remote_filename" to "gemma-4-E4B-it.litertlm",
                        "local_filename" to "gemma-4-E4B-it.litertlm",
                        "size_in_bytes" to 100L,
                        "updated_at" to 2000L,
                        "deleted" to false,
                    ),
                )

            handler.upsertLocal(remoteEntity, doc, overwrite = false)

            val stored = database.localModelsDao().getById(modelId)
            assertEquals("Gemma 4 E4B", stored?.displayName)
        }

    private fun createContext(database: EnclaveDatabase): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = mockk<LoggingPort>(relaxed = true)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(localModelsDao = database.localModelsDao()),
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

    private fun mockDocumentSnapshot(fields: Map<String, Any?>): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true)
        every { snapshot.id } returns "doc-1"
        fields.forEach { (field, value) ->
            when (value) {
                is String -> every { snapshot.getString(field) } returns value
                is Long -> every { snapshot.getLong(field) } returns value
                is Boolean -> every { snapshot.getBoolean(field) } returns value
            }
        }
        return snapshot
    }
}
