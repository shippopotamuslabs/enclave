package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.buildApiCredentialsIdentitySignature
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.mapper.ApiModelMapper
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiModelRepositoryImpl @Inject constructor(
    private val apiCredentialsDao: ApiCredentialsDao,
    private val apiModelConfigurationsDao: ApiModelConfigurationsDao,
    private val apiKeyManager: ApiKeyManager
) : ApiModelRepositoryPort {

    override fun observeAllCredentials(): Flow<List<ApiCredentials>> =
        apiCredentialsDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>> =
        apiModelConfigurationsDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getAllCredentials(): List<ApiCredentials> =
        apiCredentialsDao.getAll().map { it.toDomain() }

    override suspend fun getCredentialsById(id: ApiCredentialsId): ApiCredentials? =
        apiCredentialsDao.getById(id)?.toDomain()

    override suspend fun saveCredentials(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?
    ): ApiCredentialsId {
        val existingEntity = if (credentials.id.value.isNotEmpty()) {
            apiCredentialsDao.getById(credentials.id) ?: throw IllegalArgumentException("API credentials not found: ${credentials.id}")
        } else null
        
        val storedBaseUrl = credentials.baseUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: credentials.provider.defaultBaseUrl()
        val sourceApiKey = resolveApiKey(
            apiKey = apiKey,
            sourceCredentialAlias = sourceCredentialAlias,
            fallbackAlias = existingEntity?.credentialAlias,
        )
        val apiKeySignature = sourceApiKey?.let {
            buildApiCredentialsIdentitySignature(
                provider = credentials.provider,
                modelId = credentials.modelId,
                baseUrl = storedBaseUrl,
                apiKey = it,
            )
        } ?: existingEntity?.apiKeySignature
        val now = System.currentTimeMillis()
        val persistedId = if (credentials.id.value.isEmpty()) {
            val entity = ApiCredentialsEntity(
                id = ApiCredentialsId(UUID.randomUUID().toString()),
                displayName = credentials.displayName,
                provider = credentials.provider,
                modelId = credentials.modelId,
                baseUrl = storedBaseUrl,
                isVision = credentials.isVision,
                credentialAlias = credentials.credentialAlias,
                apiKeySignature = apiKeySignature,
                createdAt = now,
                updatedAt = now
            )
            apiCredentialsDao.insert(entity)
            entity.id
        } else {
            val entity = ApiCredentialsEntity(
                id = credentials.id,
                displayName = credentials.displayName,
                provider = credentials.provider,
                modelId = credentials.modelId,
                baseUrl = storedBaseUrl,
                isVision = credentials.isVision,
                credentialAlias = credentials.credentialAlias,
                apiKeySignature = apiKeySignature,
                createdAt = existingEntity?.createdAt ?: now,
                updatedAt = now
            )
            apiCredentialsDao.update(entity)
            credentials.id
        }
        val persistedCredentials = requireNotNull(apiCredentialsDao.getById(persistedId)) {
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
        
        return persistedCredentials.id
    }

    override suspend fun findMatchingCredentials(
        provider: ApiProvider,
        modelId: String,
        baseUrl: String?,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentials? {
        val resolvedApiKey = resolveApiKey(
            apiKey = apiKey,
            sourceCredentialAlias = sourceCredentialAlias,
            fallbackAlias = null,
        ) ?: return null
        val normalizedBaseUrl = baseUrl?.trim()?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl()
        val signature = buildApiCredentialsIdentitySignature(
            provider = provider,
            modelId = modelId,
            baseUrl = normalizedBaseUrl,
            apiKey = resolvedApiKey,
        )
        return apiCredentialsDao.getByApiKeySignature(signature)?.toDomain()
    }

    override suspend fun deleteCredentials(id: ApiCredentialsId) {
        apiCredentialsDao.getById(id)?.let { entity ->
            apiCredentialsDao.deleteById(id)
            apiKeyManager.delete(entity.credentialAlias)
        }
    }

    override suspend fun getConfigurationsForCredentials(credentialsId: ApiCredentialsId): List<ApiModelConfiguration> =
        apiModelConfigurationsDao.getAllForCredentials(credentialsId).map { it.toDomain() }

    override suspend fun getConfigurationById(id: ApiModelConfigurationId): ApiModelConfiguration? =
        apiModelConfigurationsDao.getById(id)?.toDomain()

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override suspend fun saveConfiguration(config: ApiModelConfiguration): ApiModelConfigurationId {
        val entityId = if (config.id.value.isNotEmpty()) config.id else ApiModelConfigurationId(kotlin.uuid.Uuid.random().toString())
        val entity = ApiModelConfigurationEntity(
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
            reasoningEffort = config.reasoningEffort?.wireValue,
            customHeaders = ApiModelMapper.serializeCustomHeaders(config.customHeaders),
            openRouterProviderSort = config.openRouterRouting.providerSort.wireValue,
            openRouterAllowFallbacks = config.openRouterRouting.allowFallbacks,
            openRouterRequireParameters = config.openRouterRouting.requireParameters,
            openRouterDataCollectionPolicy = config.openRouterRouting.dataCollectionPolicy.wireValue,
            openRouterZeroDataRetention = config.openRouterRouting.zeroDataRetention,
        )
        apiModelConfigurationsDao.upsert(entity)
        return entityId
    }

    override suspend fun deleteConfiguration(id: ApiModelConfigurationId) {
        apiModelConfigurationsDao.deleteById(id)
    }

    override suspend fun deleteConfigurationsForCredentials(credentialsId: ApiCredentialsId) {
        apiModelConfigurationsDao.deleteAllForCredentials(credentialsId)
    }
    
    private fun ApiCredentialsEntity.toDomain() = ApiCredentials(
        id = id,
        displayName = displayName,
        provider = provider,
        modelId = modelId,
        baseUrl = baseUrl,
        isVision = isVision,
        credentialAlias = credentialAlias
    )
    
    private fun ApiModelConfigurationEntity.toDomain() = ApiModelConfiguration(
        id = id,
        apiCredentialsId = apiCredentialsId,
        displayName = displayName,
        maxTokens = maxTokens,
        contextWindow = contextWindow,
        temperature = temperature,
        topP = topP,
        topK = topK,
        minP = minP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        systemPrompt = systemPrompt,
        reasoningEffort = ApiReasoningEffort.fromWireValue(reasoningEffort),
        customHeaders = ApiModelMapper.deserializeCustomHeaders(customHeaders),
        openRouterRouting = OpenRouterRoutingConfiguration(
            providerSort = OpenRouterProviderSort.fromWireValue(openRouterProviderSort),
            allowFallbacks = openRouterAllowFallbacks ?: true,
            requireParameters = openRouterRequireParameters ?: false,
            dataCollectionPolicy = OpenRouterDataCollectionPolicy.fromWireValue(openRouterDataCollectionPolicy),
            zeroDataRetention = openRouterZeroDataRetention ?: false
        )
    )

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
