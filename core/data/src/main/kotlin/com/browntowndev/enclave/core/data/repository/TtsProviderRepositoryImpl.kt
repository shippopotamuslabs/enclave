package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsProviderRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val ttsProviderDao: TtsProviderDao,
        private val apiKeyManager: ApiKeyManager,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) : TtsProviderRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        override fun getTtsProviders(): Flow<List<TtsProviderAsset>> =
            ttsProviderDao.getTtsProviders().transform { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { it.toDomain(repairs) }
                readRepairApplier.apply(repairs)
                emit(out)
            }

        override suspend fun getTtsProvidersSync(): List<TtsProviderAsset> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = ttsProviderDao.getTtsProvidersSync().map { it.toDomain(repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getTtsProvider(id: TtsProviderId): TtsProviderAsset? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = ttsProviderDao.getTtsProvider(id.value)?.toDomain(repairs)
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun saveTtsProvider(
            asset: TtsProviderAsset,
            apiKey: String?,
        ): TtsProviderId {
            val id =
                if (asset.id.value.isEmpty()) {
                    TtsProviderId(UUID.randomUUID().toString())
                } else {
                    asset.id
                }

            val entity =
                SensitiveEntityFieldEncryptors.encrypt(
                    TtsProviderEntity(
                        id = id.value,
                        displayName = asset.displayName,
                        provider = asset.provider,
                        voiceName = asset.voiceName,
                        modelName = asset.modelName,
                        baseUrl = asset.baseUrl,
                        credentialAlias = asset.credentialAlias,
                        pendingSync = 1,
                    ),
                    healer,
                )

            ttsProviderDao.upsertTtsProvider(entity)

            if (!apiKey.isNullOrBlank()) {
                apiKeyManager.save(asset.credentialAlias, apiKey)
            }

            triggerSync()
            return id
        }

        override suspend fun deleteTtsProvider(id: TtsProviderId) {
            val asset = ttsProviderDao.getTtsProvider(id.value)
            if (asset != null) {
                ttsProviderDao.softDeleteById(id.value)
                apiKeyManager.delete(asset.credentialAlias)
                triggerSync()
            }
        }

        private fun TtsProviderEntity.toDomain(readRepairs: MutableList<SensitiveRoomReadRepair>?): TtsProviderAsset {
            val e = SensitiveEntityFieldEncryptors.decrypt(this, healer, readRepairs)
            return TtsProviderAsset(
                id = TtsProviderId(e.id),
                displayName = e.displayName,
                provider = e.provider,
                voiceName = e.voiceName,
                modelName = e.modelName,
                baseUrl = e.baseUrl,
                credentialAlias = e.credentialAlias,
            )
        }
    }
