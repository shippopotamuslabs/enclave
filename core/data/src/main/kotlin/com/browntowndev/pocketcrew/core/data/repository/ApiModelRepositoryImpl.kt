package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.buildApiCredentialsIdentitySignature
import com.browntowndev.pocketcrew.core.data.mapper.ApiModelMapper
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiModelRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val apiCredentialsDao: ApiCredentialsDao,
        private val apiModelConfigurationsDao: ApiModelConfigurationsDao,
        private val apiKeyManager: ApiKeyManager,
        private val cryptoManager: CryptoManagerPort,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) : ApiModelRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        override fun observeAllCredentials(): Flow<List<ApiCredentials>> =
            apiCredentialsDao.observeAll().transform { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { it.toDomain(repairs) }
                readRepairApplier.apply(repairs)
                emit(out)
            }

        override fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>> =
            apiModelConfigurationsDao.observeAll().transform { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.map { it.toDomain(repairs) }
                readRepairApplier.apply(repairs)
                emit(out)
            }

        override suspend fun getAllCredentials(): List<ApiCredentials> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = apiCredentialsDao.getAll().map { it.toDomain(repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getCredentialsById(id: ApiCredentialsId): ApiCredentials? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = apiCredentialsDao.getById(id)?.toDomain(repairs)
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun saveCredentials(
            credentials: ApiCredentials,
            apiKey: String,
            sourceCredentialAlias: String?,
        ): ApiCredentialsId {
            val existingEntity =
                if (credentials.id.value.isNotEmpty()) {
                    apiCredentialsDao.getById(credentials.id)
                        ?: throw IllegalArgumentException("API credentials not found: ${credentials.id}")
                } else {
                    null
                }

            val storedBaseUrl =
                credentials.baseUrl?.trim()?.takeIf { it.isNotBlank() }
                    ?: credentials.provider.defaultBaseUrl().orEmpty()
            val sourceApiKey =
                resolveApiKey(
                    apiKey = apiKey,
                    sourceCredentialAlias = sourceCredentialAlias,
                    fallbackAlias = existingEntity?.credentialAlias,
                )
            val apiKeySignature =
                sourceApiKey?.let {
                    buildApiCredentialsIdentitySignature(
                        provider = credentials.provider,
                        modelId = credentials.modelId,
                        baseUrl = storedBaseUrl,
                        apiKey = it,
                    )
                } ?: existingEntity?.apiKeySignature

            val encryptedKeyPayload =
                if (sourceApiKey != null) {
                    check(cryptoManager.hasCachedKey()) { "Encryption session locked" }
                    cryptoManager.encrypt(sourceApiKey).getOrThrow()
                } else {
                    existingEntity?.encryptedApiKey
                }

            val now = System.currentTimeMillis()
            val persistedId =
                if (credentials.id.value.isEmpty()) {
                    val entity =
                        SensitiveEntityFieldEncryptors.encrypt(
                            ApiCredentialsEntity(
                                id = ApiCredentialsId(UUID.randomUUID().toString()),
                                displayName = credentials.displayName,
                                provider = credentials.provider,
                                modelId = credentials.modelId,
                                baseUrl = storedBaseUrl,
                                isMultimodal = credentials.isMultimodal,
                                credentialAlias = credentials.credentialAlias,
                                encryptedApiKey = encryptedKeyPayload,
                                apiKeySignature = apiKeySignature,
                                createdAt = now,
                                updatedAt = now,
                                pendingSync = 1,
                            ),
                            healer,
                        )
                    apiCredentialsDao.insert(entity)
                    entity.id
                } else {
                    val entity =
                        SensitiveEntityFieldEncryptors.encrypt(
                            ApiCredentialsEntity(
                                id = credentials.id,
                                displayName = credentials.displayName,
                                provider = credentials.provider,
                                modelId = credentials.modelId,
                                baseUrl = storedBaseUrl,
                                isMultimodal = credentials.isMultimodal,
                                credentialAlias = credentials.credentialAlias,
                                encryptedApiKey = encryptedKeyPayload,
                                apiKeySignature = apiKeySignature,
                                createdAt = existingEntity?.createdAt ?: now,
                                updatedAt = now,
                                pendingSync = 1,
                            ),
                            healer,
                        )
                    apiCredentialsDao.update(entity)
                    credentials.id
                }
            val persistedCredentials =
                requireNotNull(apiCredentialsDao.getById(persistedId)) {
                    "Failed to resolve persisted API credentials for id $persistedId"
                }

            when {
                apiKey.isNotBlank() -> {
                    apiKeyManager.save(credentials.credentialAlias, apiKey)
                }
                sourceApiKey != null -> {
                    apiKeyManager.save(credentials.credentialAlias, sourceApiKey)
                }
            }

            triggerSync()
            return persistedCredentials.id
        }

        override suspend fun findMatchingCredentials(
            provider: ApiProvider,
            modelId: String,
            baseUrl: String?,
            apiKey: String,
            sourceCredentialAlias: String?,
        ): ApiCredentials? {
            val resolvedApiKey =
                resolveApiKey(
                    apiKey = apiKey,
                    sourceCredentialAlias = sourceCredentialAlias,
                    fallbackAlias = null,
                ) ?: return null
            val normalizedBaseUrl = baseUrl?.trim()?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()
            val signature =
                buildApiCredentialsIdentitySignature(
                    provider = provider,
                    modelId = modelId,
                    baseUrl = normalizedBaseUrl,
                    apiKey = resolvedApiKey,
                )
            return apiCredentialsDao.getByApiKeySignature(signature)?.toDomain(null)
        }

        override suspend fun deleteCredentials(id: ApiCredentialsId) {
            apiCredentialsDao.getById(id)?.let { entity ->
                apiModelConfigurationsDao.deleteAllForCredentials(id)
                apiCredentialsDao.softDeleteById(id)
                apiKeyManager.delete(entity.credentialAlias)
                triggerSync()
            }
        }

        override suspend fun getConfigurationsForCredentials(credentialsId: ApiCredentialsId): List<ApiModelConfiguration> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = apiModelConfigurationsDao.getAllForCredentials(credentialsId).map { it.toDomain(repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getConfigurationById(id: ApiModelConfigurationId): ApiModelConfiguration? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = apiModelConfigurationsDao.getById(id)?.toDomain(repairs)
            readRepairApplier.apply(repairs)
            return out
        }

        @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
        override suspend fun saveConfiguration(config: ApiModelConfiguration): ApiModelConfigurationId {
            val entityId =
                if (config.id.value.isNotEmpty()) {
                    config.id
                } else {
                    ApiModelConfigurationId(
                        kotlin.uuid.Uuid
                            .random()
                            .toString(),
                    )
                }
            val entity =
                SensitiveEntityFieldEncryptors.encrypt(
                    ApiModelConfigurationEntity(
                        id = entityId,
                        apiCredentialsId = config.apiCredentialsId,
                        displayName = config.displayName,
                        maxTokens = config.maxTokens,
                        contextWindow = config.contextWindow,
                        temperature = config.temperature,
                        topP = config.topP,
                        topK = config.topK,
                        minP = config.minP,
                        frequencyPenalty = config.frequencyPenalty,
                        presencePenalty = config.presencePenalty,
                        systemPrompt = config.systemPrompt,
                        maxTokensDisabled = config.maxTokensDisabled,
                        temperatureDisabled = config.temperatureDisabled,
                        topPDisabled = config.topPDisabled,
                        topKDisabled = config.topKDisabled,
                        minPDisabled = config.minPDisabled,
                        frequencyPenaltyDisabled = config.frequencyPenaltyDisabled,
                        presencePenaltyDisabled = config.presencePenaltyDisabled,
                        reasoningEffort = config.reasoningEffort?.wireValue,
                        customHeaders = ApiModelMapper.serializeCustomHeaders(config.customHeaders),
                        openRouterProviderSort = config.openRouterRouting.providerSort.wireValue,
                        openRouterAllowFallbacks = config.openRouterRouting.allowFallbacks,
                        openRouterRequireParameters = config.openRouterRouting.requireParameters,
                        openRouterDataCollectionPolicy = config.openRouterRouting.dataCollectionPolicy.wireValue,
                        openRouterZeroDataRetention = config.openRouterRouting.zeroDataRetention,
                        pendingSync = 1,
                    ),
                    healer,
                )
            apiModelConfigurationsDao.upsert(entity)
            triggerSync()
            return entityId
        }

        override suspend fun deleteConfiguration(id: ApiModelConfigurationId) {
            apiModelConfigurationsDao.softDeleteById(id)
            triggerSync()
        }

        override suspend fun deleteConfigurationsForCredentials(credentialsId: ApiCredentialsId) {
            apiModelConfigurationsDao.deleteAllForCredentials(credentialsId)
            triggerSync()
        }

        private fun ApiCredentialsEntity.toDomain(readRepairs: MutableList<SensitiveRoomReadRepair>?): ApiCredentials {
            val e = SensitiveEntityFieldEncryptors.decrypt(this, healer, readRepairs)
            return ApiCredentials(
                id = e.id,
                displayName = e.displayName,
                provider = e.provider,
                modelId = e.modelId,
                baseUrl = e.baseUrl,
                isMultimodal = e.isMultimodal,
                credentialAlias = e.credentialAlias,
                encryptedApiKey = e.encryptedApiKey,
            )
        }

        private fun ApiModelConfigurationEntity.toDomain(readRepairs: MutableList<SensitiveRoomReadRepair>?): ApiModelConfiguration {
            val e = SensitiveEntityFieldEncryptors.decrypt(this, healer, readRepairs)
            return ApiModelConfiguration(
                id = e.id,
                apiCredentialsId = e.apiCredentialsId,
                displayName = e.displayName,
                maxTokens = e.maxTokens,
                contextWindow = e.contextWindow,
                temperature = e.temperature,
                topP = e.topP,
                topK = e.topK,
                minP = e.minP,
                frequencyPenalty = e.frequencyPenalty,
                presencePenalty = e.presencePenalty,
                systemPrompt = e.systemPrompt,
                reasoningEffort = ApiReasoningEffort.fromWireValue(e.reasoningEffort),
                maxTokensDisabled = e.maxTokensDisabled,
                temperatureDisabled = e.temperatureDisabled,
                topPDisabled = e.topPDisabled,
                topKDisabled = e.topKDisabled,
                minPDisabled = e.minPDisabled,
                frequencyPenaltyDisabled = e.frequencyPenaltyDisabled,
                presencePenaltyDisabled = e.presencePenaltyDisabled,
                customHeaders = ApiModelMapper.deserializeCustomHeaders(e.customHeaders),
                openRouterRouting =
                    OpenRouterRoutingConfiguration(
                        providerSort = OpenRouterProviderSort.fromWireValue(e.openRouterProviderSort),
                        allowFallbacks = e.openRouterAllowFallbacks ?: true,
                        requireParameters = e.openRouterRequireParameters ?: false,
                        dataCollectionPolicy = OpenRouterDataCollectionPolicy.fromWireValue(e.openRouterDataCollectionPolicy),
                        zeroDataRetention = e.openRouterZeroDataRetention ?: false,
                    ),
            )
        }

        private fun resolveApiKey(
            apiKey: String,
            sourceCredentialAlias: String?,
            fallbackAlias: String?,
        ): String? {
            if (apiKey.isNotBlank()) {
                return apiKey
            }
            if (!sourceCredentialAlias.isNullOrBlank() && sourceCredentialAlias != fallbackAlias) {
                return requireNotNull(apiKeyManager.get(sourceCredentialAlias)) {
                    "Stored API key not found for alias: $sourceCredentialAlias"
                }
            }
            if (!fallbackAlias.isNullOrBlank()) {
                return apiKeyManager.get(fallbackAlias)
            }
            return null
        }
    }
