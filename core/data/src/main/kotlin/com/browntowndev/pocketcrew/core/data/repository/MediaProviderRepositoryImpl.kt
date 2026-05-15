package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderEntity
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProviderRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val mediaProviderDao: MediaProviderDao,
        private val apiKeyManager: ApiKeyManager,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) : MediaProviderRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        override fun getMediaProviders(): Flow<List<MediaProviderAsset>> =
            mediaProviderDao.observeAllMediaProviders().transform { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { it.toDomain(repairs) }
                readRepairApplier.apply(repairs)
                emit(out)
            }

        override suspend fun getMediaProvidersSync(): List<MediaProviderAsset> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val entities = mediaProviderDao.observeAllMediaProviders().first()
            val out = entities.map { it.toDomain(repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getMediaProvider(id: MediaProviderId): MediaProviderAsset? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = mediaProviderDao.getMediaProvider(id.value)?.toDomain(repairs)
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun saveMediaProvider(
            asset: MediaProviderAsset,
            apiKey: String?,
        ): MediaProviderId {
            val id =
                if (asset.id.value.isBlank()) {
                    MediaProviderId(UUID.randomUUID().toString())
                } else {
                    asset.id
                }

            if (!apiKey.isNullOrBlank()) {
                apiKeyManager.save(asset.credentialAlias, apiKey)
            }

            val entity =
                SensitiveEntityFieldEncryptors.encrypt(
                    MediaProviderEntity(
                        id = id.value,
                        displayName = asset.displayName,
                        provider = asset.provider,
                        capability = asset.capability,
                        modelName = asset.modelName,
                        baseUrl = asset.baseUrl,
                        credentialAlias = asset.credentialAlias,
                        pendingSync = 1,
                    ),
                    healer,
                )
            mediaProviderDao.upsert(entity)

            triggerSync()
            return id
        }

        override suspend fun deleteMediaProvider(id: MediaProviderId) {
            val asset = mediaProviderDao.getMediaProvider(id.value)
            if (asset != null) {
                apiKeyManager.delete(asset.credentialAlias)
                mediaProviderDao.softDeleteById(id.value)
                triggerSync()
            }
        }

        private fun MediaProviderEntity.toDomain(readRepairs: MutableList<SensitiveRoomReadRepair>?): MediaProviderAsset {
            val e = SensitiveEntityFieldEncryptors.decrypt(this, healer, readRepairs)
            return MediaProviderAsset(
                id = MediaProviderId(e.id),
                displayName = e.displayName,
                provider = e.provider,
                capability = e.capability,
                modelName = e.modelName,
                baseUrl = e.baseUrl,
                credentialAlias = e.credentialAlias,
            )
        }
    }
