@file:Suppress("ForbiddenMockKInTests")

// MockK retained only for the Firebase Firestore SDK surface (DocumentSnapshot / FirebaseFirestore), which has no JVM fake.

package com.browntowndev.enclave.core.data.remote.firestore.handlers

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.UtilityModelEntity
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.testSyncContext
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class UtilityModelSyncHandlerTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var handler: UtilityModelSyncHandler

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        handler = UtilityModelSyncHandler(createContext(database))
    }

    @Test
    @DisplayName("SCN-SYNC-001")
    fun scnSync001_firestoreRoundTripPreservesFields() =
        runTest {
            val entity =
                UtilityModelEntity(
                    id = UtilityModelId("vocab-id"),
                    utilityType = UtilityType.ONNX_VOCAB,
                    modelFileFormat = ModelFileFormat.TXT,
                    huggingFaceModelName = "vocab",
                    remoteFilename = "vocab.txt",
                    localFilename = "vocab.txt",
                    sha256 = "vocab-sha",
                    sizeInBytes = 231_508L,
                    updatedAt = 1_700_000_001_000L,
                    deleted = false,
                )

            val firestoreMap = handler.toFirestore(entity)
            val doc = mockk<DocumentSnapshot>(relaxed = true)
            every { doc.id } returns entity.id.value
            firestoreMap.forEach { (key, value) ->
                when (value) {
                    is String -> every { doc.getString(key) } returns value
                    is Long -> every { doc.getLong(key) } returns value
                    is Boolean -> every { doc.getBoolean(key) } returns value
                    else -> Unit
                }
            }
            every { doc.getString("utility_type") } returns UtilityType.ONNX_VOCAB.apiValue

            val parsed = handler.fromFirestore(doc)

            assertEquals(UtilityType.ONNX_VOCAB.apiValue, parsed.utilityType.apiValue)
            assertEquals("vocab-sha", parsed.sha256)
            assertEquals(231_508L, parsed.sizeInBytes)
        }

    @Test
    @DisplayName("SCN-SYNC-002")
    fun scnSync002_remoteNewerUpdatedAtOverwritesLocalRow() =
        runTest {
            val dao = database.utilityModelsDao()
            dao.upsert(
                UtilityModelEntity(
                    id = UtilityModelId("whisper-id"),
                    utilityType = UtilityType.WHISPER,
                    modelFileFormat = ModelFileFormat.BIN,
                    huggingFaceModelName = "whisper",
                    remoteFilename = "old.bin",
                    localFilename = "old.bin",
                    sha256 = "whisper-sha",
                    sizeInBytes = 4L,
                    updatedAt = 1000L,
                    pendingSync = 0,
                ),
            )
            val remoteEntity =
                UtilityModelEntity(
                    id = UtilityModelId("whisper-id"),
                    utilityType = UtilityType.WHISPER,
                    modelFileFormat = ModelFileFormat.BIN,
                    huggingFaceModelName = "whisper",
                    remoteFilename = "ggml-base.en.bin",
                    localFilename = "ggml-base.en.bin",
                    sha256 = "whisper-sha",
                    sizeInBytes = 4L,
                    updatedAt = 2000L,
                    deleted = false,
                )
            val doc = mockk<DocumentSnapshot>(relaxed = true)

            handler.upsertLocal(remoteEntity, doc, overwrite = false)

            val row = dao.getByUtilityType(UtilityType.WHISPER)
            assertEquals("ggml-base.en.bin", row?.localFilename)
            assertEquals(0, row?.pendingSync)
        }

    @Test
    @DisplayName("SCN-SYNC-003")
    fun scnSync003_localNewerUpdatedAtSkipsRemoteOverwrite() =
        runTest {
            val dao = database.utilityModelsDao()
            dao.upsert(
                UtilityModelEntity(
                    id = UtilityModelId("whisper-id"),
                    utilityType = UtilityType.WHISPER,
                    modelFileFormat = ModelFileFormat.BIN,
                    huggingFaceModelName = "whisper",
                    remoteFilename = "keep.bin",
                    localFilename = "keep.bin",
                    sha256 = "whisper-sha",
                    sizeInBytes = 4L,
                    updatedAt = 5000L,
                    pendingSync = 0,
                ),
            )
            val remoteEntity =
                UtilityModelEntity(
                    id = UtilityModelId("whisper-id"),
                    utilityType = UtilityType.WHISPER,
                    modelFileFormat = ModelFileFormat.BIN,
                    huggingFaceModelName = "whisper",
                    remoteFilename = "remote.bin",
                    localFilename = "remote.bin",
                    sha256 = "whisper-sha",
                    sizeInBytes = 4L,
                    updatedAt = 2000L,
                    deleted = false,
                )
            val doc = mockk<DocumentSnapshot>(relaxed = true)

            handler.upsertLocal(remoteEntity, doc, overwrite = false)

            assertEquals("keep.bin", dao.getByUtilityType(UtilityType.WHISPER)?.localFilename)
        }

    private fun createContext(database: EnclaveDatabase): FirestoreSyncContext =
        testSyncContext(daos = testFirestoreSyncDaos(utilityModelsDao = database.utilityModelsDao()))
}
