package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderEntity
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.port.repository.MediaProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProviderRepositoryImpl @Inject constructor(
    private val syncTrigger: CloudSyncTrigger,
    private val mediaProviderDao: MediaProviderDao,
    private val apiKeyManager: ApiKeyManager,
) : MediaProviderRepositoryPort {

    private fun triggerSync() = syncTrigger.trigger()

    override fun getMediaProviders(): Flow<List<MediaProviderAsset>> {
        return mediaProviderDao.observeAllMediaProviders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMediaProvidersSync(): List<MediaProviderAsset> {
        return getMediaProviders().first()
    }

    override suspend fun getMediaProvider(id: MediaProviderId): MediaProviderAsset? {
        return mediaProviderDao.getMediaProvider(id.value)?.toDomain()
    }

    override suspend fun saveMediaProvider(asset: MediaProviderAsset, apiKey: String?): MediaProviderId {
        val id = if (asset.id.value.isBlank()) {
            MediaProviderId(UUID.randomUUID().toString())
        } else {
            asset.id
        }

        if (!apiKey.isNullOrBlank()) {
            apiKeyManager.save(asset.credentialAlias, apiKey)
        }

        mediaProviderDao.upsert(
            MediaProviderEntity(
                id = id.value,
                displayName = asset.displayName,
                provider = asset.provider,
                capability = asset.capability,
                modelName = asset.modelName,
                baseUrl = asset.baseUrl,
                credentialAlias = asset.credentialAlias,
                pendingSync = 1,
            )
        )

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

    private fun MediaProviderEntity.toDomain() = MediaProviderAsset(
        id = MediaProviderId(id),
        displayName = displayName,
        provider = provider,
        capability = capability,
        modelName = modelName,
        baseUrl = baseUrl,
        credentialAlias = credentialAlias,
    )
}
