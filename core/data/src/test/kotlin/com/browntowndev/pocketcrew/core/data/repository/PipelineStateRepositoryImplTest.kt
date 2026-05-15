package com.browntowndev.pocketcrew.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class PipelineStateRepositoryImplTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun persistPipelineState_storesEncryptedJsonAndReturnsPlaintextDomainState() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val repository =
                PipelineStateRepositoryImpl(
                    dataStore = dataStore,
                    localEncryption = FakeLocalDataEncryptionService(),
                )
            val state = PipelineState("chat-1", PipelineStep.SYNTHESIS, "private prompt", startTimeMs = 1L)

            repository.persistPipelineState("chat-1", state)

            assertEquals("PCENC:v1:aes-gcm:${state.toJson()}", dataStore.data.first()[pipelineStateKey("chat-1")])
            assertEquals(state, repository.getPipelineState("chat-1"))
        }

    @Test
    fun getPipelineState_selfHealsPlaintextJson() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            val state = PipelineState("chat-1", PipelineStep.DRAFT_TWO, "private prompt", startTimeMs = 1L)
            dataStore.edit { preferences ->
                preferences[pipelineStateKey("chat-1")] = state.toJson()
            }
            val repository =
                PipelineStateRepositoryImpl(
                    dataStore = dataStore,
                    localEncryption = FakeLocalDataEncryptionService(),
                )

            assertEquals(state, repository.getPipelineState("chat-1"))

            assertEquals("PCENC:v1:aes-gcm:${state.toJson()}", dataStore.data.first()[pipelineStateKey("chat-1")])
        }

    private fun createDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempDir.resolve("pipeline-${System.nanoTime()}.preferences_pb").toFile() },
        )

    private class FakeLocalDataEncryptionService : LocalDataEncryptionService {
        override fun encryptString(plaintext: String): String = "PCENC:v1:aes-gcm:$plaintext"

        override fun decryptString(stored: String): String = stored.removePrefix("PCENC:v1:aes-gcm:")

        override fun isEncrypted(stored: String): Boolean = stored.startsWith("PCENC:v1:aes-gcm:")

        override fun encryptString(plaintext: String, aad: ByteArray): String = "PCENC:v1:aes-gcm:$plaintext"
        override fun decryptString(stored: String, aad: ByteArray): String = stored.removePrefix("PCENC:v1:aes-gcm:")
    }

    private companion object {
        private fun pipelineStateKey(chatId: String) = stringPreferencesKey("pipeline_state_$chatId")
    }
}
