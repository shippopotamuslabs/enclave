package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.port.repository.LiveVoiceProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveVoiceProviderRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val liveVoiceProviderDao: LiveVoiceProviderDao,
        private val apiKeyManager: ApiKeyManager,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) : LiveVoiceProviderRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        override fun getLiveVoiceProviders(): Flow<List<LiveVoiceProviderAsset>> =
            liveVoiceProviderDao.getLiveVoiceProviders().transform { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { it.toDomain(repairs) }
                readRepairApplier.apply(repairs)
                emit(out)
            }

        override suspend fun getLiveVoiceProvidersSync(): List<LiveVoiceProviderAsset> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = liveVoiceProviderDao.getLiveVoiceProvidersSync().map { it.toDomain(repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getLiveVoiceProvider(id: LiveVoiceProviderId): LiveVoiceProviderAsset? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = liveVoiceProviderDao.getLiveVoiceProvider(id.value)?.toDomain(repairs)
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun saveLiveVoiceProvider(
            asset: LiveVoiceProviderAsset,
            apiKey: String?,
        ): LiveVoiceProviderId {
            val id =
                if (asset.id.value.isEmpty()) {
                    LiveVoiceProviderId(UUID.randomUUID().toString())
                } else {
                    asset.id
                }
            val credentialAlias = asset.credentialAlias.trim().ifBlank { "lv-${id.value}" }

            val entity =
                SensitiveEntityFieldEncryptors.encrypt(
                    LiveVoiceProviderEntity(
                        id = id.value,
                        displayName = asset.displayName,
                        provider = asset.provider,
                        voiceName = asset.voiceName,
                        modelName = asset.modelName,
                        baseUrl = asset.baseUrl,
                        credentialAlias = credentialAlias,
                        pendingSync = 1,
                    ),
                    healer,
                )

            liveVoiceProviderDao.upsertLiveVoiceProvider(entity)

            if (!apiKey.isNullOrBlank()) {
                apiKeyManager.save(credentialAlias, apiKey)
            }

            triggerSync()
            return id
        }

        override suspend fun deleteLiveVoiceProvider(id: LiveVoiceProviderId) {
            val asset = liveVoiceProviderDao.getLiveVoiceProvider(id.value)
            if (asset != null) {
                liveVoiceProviderDao.softDeleteById(id.value)
                apiKeyManager.delete(asset.credentialAlias)
                triggerSync()
            }
        }

        private fun LiveVoiceProviderEntity.toDomain(readRepairs: MutableList<SensitiveRoomReadRepair>?): LiveVoiceProviderAsset {
            val e = SensitiveEntityFieldEncryptors.decrypt(this, healer, readRepairs)
            return LiveVoiceProviderAsset(
                id = LiveVoiceProviderId(e.id),
                displayName = e.displayName,
                provider = e.provider,
                voiceName = e.voiceName,
                modelName = e.modelName,
                baseUrl = e.baseUrl,
                credentialAlias = e.credentialAlias,
            )
        }
    }
