package com.browntowndev.enclave.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.model.inference.PipelineState
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.PipelineStateRepository
import com.browntowndev.enclave.domain.qualifier.PipelineDataStore
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of PipelineStateRepository.
 * Uses a dedicated DataStore with @PipelineDataStore qualifier to avoid conflicts.
 *
 * AAD field name is [StaticSensitiveFieldCatalog]'s `pipeline_state_chatId` (not the older
 * `pipeline_state` string from the feature sketch) so ciphertext matches
 * [SensitivePersistenceSelfHealer] / encryptor catalog semantics and existing persisted rows.
 */
@Singleton
class PipelineStateRepositoryImpl
    @Inject
    constructor(
        @param:PipelineDataStore private val dataStore: DataStore<Preferences>,
        private val localEncryption: LocalDataEncryptionService = LocalDataEncryptionService.NoOp,
        private val loggingPort: LoggingPort = NoOpLoggingPort,
    ) : PipelineStateRepository {
        companion object {
            private fun pipelineStateKey(chatId: String) = stringPreferencesKey("pipeline_state_$chatId")

            private fun pipelineAad(chatId: String) = AadContext("PipelineStateRepositoryImpl", "pipeline_state_chatId", chatId).toBytes()

            private const val TAG = "PipelineStateRepository"
        }

        override suspend fun persistPipelineState(
            chatId: String,
            state: PipelineState,
        ) {
            val key = pipelineStateKey(chatId)
            val serialized = state.toJson()

            val aad = pipelineAad(chatId)
            dataStore.edit { preferences ->
                preferences[key] = localEncryption.encryptString(serialized, aad)
            }
        }

        override suspend fun getPipelineState(chatId: String): PipelineState? {
            val key = pipelineStateKey(chatId)
            val preferences = dataStore.data.firstOrNull() ?: return null

            val stored = preferences[key] ?: return null
            val serialized = decryptAndRepair(chatId = chatId, stored = stored)

            return try {
                PipelineState.fromJson(serialized)
            } catch (_: Exception) {
                null
            }
        }

        private suspend fun decryptAndRepair(
            chatId: String,
            stored: String,
        ): String {
            val aad = pipelineAad(chatId)
            if (localEncryption.isEncrypted(stored)) {
                return localEncryption.decryptString(stored, aad)
            }
            if (!localEncryption.isUnlocked()) {
                loggingPort.warning(
                    TAG,
                    "Sensitive persistence self-heal owner=PipelineStateRepositoryImpl field=state action=DEFERRED_LOCKED",
                )
                return stored
            }
            val encrypted = localEncryption.encryptString(stored, aad)
            if (encrypted != stored) {
                dataStore.edit { preferences ->
                    preferences[pipelineStateKey(chatId)] = encrypted
                }
                loggingPort.warning(
                    TAG,
                    "Sensitive persistence self-heal owner=PipelineStateRepositoryImpl field=state action=REPAIRED",
                )
            }
            return stored
        }

        override suspend fun clearPipelineState(chatId: String) {
            val key = pipelineStateKey(chatId)

            dataStore.edit { preferences ->
                preferences.remove(key)
            }
        }

        private object NoOpLoggingPort : LoggingPort {
            override fun debug(
                tag: String,
                message: String,
            ) = Unit

            override fun info(
                tag: String,
                message: String,
            ) = Unit

            override fun warning(
                tag: String,
                message: String,
            ) = Unit

            override fun error(
                tag: String,
                message: String,
                throwable: Throwable?,
            ) = Unit
        }
    }
