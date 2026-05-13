package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderEntity
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.port.repository.TtsProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        private val localEncryption: LocalDataEncryptionService,
    ) : TtsProviderRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        override fun getTtsProviders(): Flow<List<TtsProviderAsset>> =
            ttsProviderDao.getTtsProviders().map { entities ->
                entities.map { it.toDomain() }
            }

        override suspend fun getTtsProvidersSync(): List<TtsProviderAsset> = ttsProviderDao.getTtsProvidersSync().map { it.toDomain() }

        override suspend fun getTtsProvider(id: TtsProviderId): TtsProviderAsset? = ttsProviderDao.getTtsProvider(id.value)?.toDomain()

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
                TtsProviderEntity(
                    id = id.value,
                    displayName = localEncryption.encryptString(asset.displayName),
                    provider = asset.provider,
                    voiceName = localEncryption.encryptString(asset.voiceName),
                    modelName = asset.modelName?.let(localEncryption::encryptString),
                    baseUrl = asset.baseUrl?.let(localEncryption::encryptString),
                    credentialAlias = asset.credentialAlias,
                    pendingSync = 1,
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

        private fun TtsProviderEntity.toDomain() =
            TtsProviderAsset(
                id = TtsProviderId(id),
                displayName = localEncryption.decryptString(displayName),
                provider = provider,
                voiceName = localEncryption.decryptString(voiceName),
                modelName = modelName?.let(localEncryption::decryptString),
                baseUrl = baseUrl?.let(localEncryption::decryptString),
                credentialAlias = credentialAlias,
            )
    }
