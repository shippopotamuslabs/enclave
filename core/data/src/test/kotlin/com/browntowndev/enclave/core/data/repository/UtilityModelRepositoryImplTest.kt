package com.browntowndev.enclave.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.UtilityModelEntity
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import org.robolectric.annotation.Config as RoboConfig

@ExtendWith(RobolectricExtension::class)
@RoboConfig(sdk = [34])
class UtilityModelRepositoryImplTest {
    private lateinit var database: EnclaveDatabase
    private val syncTrigger = mockk<CloudSyncTrigger>(relaxed = true)
    private lateinit var repository: UtilityModelRepositoryImpl

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        repository =
            UtilityModelRepositoryImpl(
                utilityModelsDao = database.utilityModelsDao(),
                syncTrigger = syncTrigger,
            )
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    @DisplayName("SCN-REPO-001")
    fun scnRepo001_upsertStoresRowAndTriggersCloudSyncTrigger() =
        runTest {
            val metadata =
                UtilityModelMetadata(
                    id = UtilityModelId(""),
                    utilityType = UtilityType.ONNX_MODEL,
                    huggingFaceModelName = "onnx",
                    remoteFileName = "model.onnx",
                    localFileName = "model.onnx",
                    sha256 = "onnx-sha",
                    sizeInBytes = 90_439_680L,
                    modelFileFormat = ModelFileFormat.ONNX,
                    source = DownloadSource.HUGGING_FACE,
                )

            repository.upsert(metadata)

            val row = database.utilityModelsDao().getByUtilityType(UtilityType.ONNX_MODEL)
            assertEquals("onnx-sha", row?.sha256)
            assertEquals(1, row?.pendingSync)
            coVerify(exactly = 1) { syncTrigger.trigger() }
        }

    @Test
    @DisplayName("SCN-REPO-002")
    fun scnRepo002_getByUtilityTypeReturnsActiveMetadata() =
        runTest {
            database.utilityModelsDao().upsert(
                UtilityModelEntity(
                    id = UtilityModelId("vocab-id"),
                    utilityType = UtilityType.ONNX_VOCAB,
                    modelFileFormat = ModelFileFormat.TXT,
                    huggingFaceModelName = "vocab",
                    remoteFilename = "vocab.txt",
                    localFilename = "vocab.txt",
                    sha256 = "vocab-sha",
                    sizeInBytes = 231_508L,
                    deleted = false,
                ),
            )

            val result = repository.getByUtilityType(UtilityType.ONNX_VOCAB)

            assertEquals(UtilityType.ONNX_VOCAB, result?.utilityType)
            assertEquals("vocab-sha", result?.sha256)
        }

    @Test
    @DisplayName("SCN-REPO-003")
    fun scnRepo003_reUpsertSameUtilityTypeReusesExistingId() =
        runTest {
            val existingId = UtilityModelId("11111111-1111-1111-1111-111111111111")
            database.utilityModelsDao().upsert(
                UtilityModelEntity(
                    id = existingId,
                    utilityType = UtilityType.WHISPER,
                    modelFileFormat = ModelFileFormat.BIN,
                    huggingFaceModelName = "whisper",
                    remoteFilename = "ggml-base.en.bin",
                    localFilename = "ggml-base.en.bin",
                    sha256 = "old-sha",
                    sizeInBytes = 4L,
                ),
            )

            val returned =
                repository.upsert(
                    UtilityModelMetadata(
                        id = UtilityModelId(""),
                        utilityType = UtilityType.WHISPER,
                        huggingFaceModelName = "whisper",
                        remoteFileName = "ggml-base.en.bin",
                        localFileName = "ggml-base.en.bin",
                        sha256 = "new-sha",
                        sizeInBytes = 4L,
                        modelFileFormat = ModelFileFormat.BIN,
                    ),
                )

            assertEquals(existingId, returned)
            assertEquals("new-sha", database.utilityModelsDao().getByUtilityType(UtilityType.WHISPER)?.sha256)
        }
}
