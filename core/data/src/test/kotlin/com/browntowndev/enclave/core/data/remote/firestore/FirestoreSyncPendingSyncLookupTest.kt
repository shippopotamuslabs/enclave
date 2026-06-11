package com.browntowndev.enclave.core.data.remote.firestore

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderEntity
import com.browntowndev.enclave.core.data.local.UtilityModelEntity
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class FirestoreSyncPendingSyncLookupTest {
    private lateinit var database: EnclaveDatabase
    private lateinit var lookup: FirestoreSyncPendingSyncLookup

    @BeforeEach
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    EnclaveDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        lookup = FirestoreSyncPendingSyncLookup(database)
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    @DisplayName("SCN-SYNC-005")
    fun scnSync005_pendingSyncLookupReturnsPendingSyncForUtilityModels() =
        runTest {
            database.utilityModelsDao().upsert(
                UtilityModelEntity(
                    id = UtilityModelId("sync-id"),
                    utilityType = UtilityType.WHISPER,
                    modelFileFormat = ModelFileFormat.BIN,
                    huggingFaceModelName = "whisper",
                    remoteFilename = "ggml-base.en.bin",
                    localFilename = "ggml-base.en.bin",
                    sha256 = "whisper-sha",
                    sizeInBytes = 4L,
                    pendingSync = 2,
                ),
            )

            val result = lookup.pendingSync("utility_models", "sync-id")

            assertEquals(2, result)
        }

    @Test
    fun pendingSyncLookupReturnsPendingSyncForLiveVoiceProviders() =
        runTest {
            database.liveVoiceProviderDao().upsertLiveVoiceProvider(
                LiveVoiceProviderEntity(
                    id = "live-voice-sync-id",
                    displayName = "Realtime",
                    provider = ApiProvider.OPENAI,
                    voiceName = "alloy",
                    modelName = null,
                    baseUrl = null,
                    credentialAlias = "lv-live-voice-sync-id",
                    pendingSync = 2,
                ),
            )

            val result = lookup.pendingSync("live_voice_providers", "live-voice-sync-id")

            assertEquals(2, result)
        }
}
