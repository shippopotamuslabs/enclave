package com.browntowndev.enclave.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class UtilityModelsDaoTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var dao: UtilityModelsDao

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.utilityModelsDao()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private fun whisperEntity(id: String = "util-1") =
        UtilityModelEntity(
            id = UtilityModelId(id),
            utilityType = UtilityType.WHISPER,
            modelFileFormat = ModelFileFormat.BIN,
            huggingFaceModelName = "ggerganov/whisper.cpp",
            remoteFilename = "ggml-base.en.bin",
            localFilename = "ggml-base.en.bin",
            sha256 = "whisper-sha",
            sizeInBytes = 4L,
            pendingSync = 0,
            deleted = false,
        )

    @Test
    @DisplayName("SCN-DAO-001")
    fun scnDao001_uniqueUtilityTypePreventsDuplicateActiveTypes() =
        runTest {
            dao.upsert(whisperEntity("aaaa-bbbb"))

            assertThrows<android.database.sqlite.SQLiteConstraintException> {
                insertUtilityRowRaw(
                    id = "cccc-dddd",
                    utilityType = UtilityType.WHISPER.apiValue,
                    sha256 = "other-sha",
                )
            }

            assertEquals(1, dao.getAll().size)
        }

    @Test
    @DisplayName("SCN-DAO-002")
    fun scnDao002_softDeleteSetsDeletedAndPendingSync() =
        runTest {
            dao.upsert(whisperEntity("util-1"))

            dao.softDeleteById(UtilityModelId("util-1"))

            val row = dao.getById(UtilityModelId("util-1"))
            assertNull(row)
            val allRows = database.openHelper.writableDatabase.query("SELECT * FROM utility_models WHERE id = 'util-1'")
            allRows.moveToFirst()
            assertEquals(1, allRows.getInt(allRows.getColumnIndexOrThrow("deleted")))
            assertEquals(1, allRows.getInt(allRows.getColumnIndexOrThrow("pending_sync")))
            allRows.close()
            assertNull(dao.getByUtilityType(UtilityType.WHISPER))
        }

    @Test
    @DisplayName("SCN-DOMAIN-002")
    fun scnDomain002_duplicateUtilityTypeConstraintLeavesSingleRow() =
        runTest {
            dao.upsert(whisperEntity("aaaa-bbbb"))

            assertThrows<android.database.sqlite.SQLiteConstraintException> {
                insertUtilityRowRaw(
                    id = "new-id",
                    utilityType = UtilityType.WHISPER.apiValue,
                    sha256 = "other-sha",
                )
            }

            assertEquals(1, dao.getAll().size)
        }

    @Test
    @DisplayName("SCN-DAO-003")
    fun scnDao003_claimPendingAndMarkSyncedMatchLocalModelsSemantics() =
        runTest {
            dao.upsert(whisperEntity("sync-id").copy(pendingSync = 1))

            dao.claimPending()
            val claimed = dao.getClaimedForSync().single()
            assertEquals(2, claimed.pendingSync)

            dao.markSynced(listOf(claimed.id))
            val synced = dao.getById(claimed.id)
            assertEquals(0, synced?.pendingSync)
        }

    private fun insertUtilityRowRaw(
        id: String,
        utilityType: String,
        sha256: String,
    ) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO utility_models (
                id, utility_type, model_file_format, huggingface_model_name,
                remote_filename, local_filename, sha256, size_in_bytes,
                updated_at, pending_sync, deleted
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                id,
                utilityType,
                ModelFileFormat.BIN.name,
                "ggerganov/whisper.cpp",
                "ggml-base.en.bin",
                "ggml-base.en.bin",
                sha256,
                4L,
                System.currentTimeMillis(),
                0,
                0,
            ),
        )
    }
}
