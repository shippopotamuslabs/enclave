package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [ApiModelRepositoryPort] fake (ENC-17). */
class FakeApiModelRepository : ApiModelRepositoryPort {
    private val credentialsFlow = MutableStateFlow<List<ApiCredentials>>(emptyList())
    private val configurationsFlow = MutableStateFlow<List<ApiModelConfiguration>>(emptyList())

    /** Recorded save/delete calls for assertions (ENC-28). */
    data class SaveCredentialsCall(
        val credentials: ApiCredentials,
        val apiKey: String,
        val sourceCredentialAlias: String?,
    )

    val saveCredentialsCalls = mutableListOf<SaveCredentialsCall>()
    val saveConfigurationCalls = mutableListOf<ApiModelConfiguration>()
    val deleteCredentialsCalls = mutableListOf<ApiCredentialsId>()

    /** When set, [saveCredentials] returns this id instead of the credentials' own id. */
    var saveCredentialsResultId: ApiCredentialsId? = null

    /** Extra aliases unioned into [getAllCredentialAliases], e.g. aliases held by soft-deleted credentials (ENC-28). */
    var additionalCredentialAliases: Set<String> = emptySet()

    /** Api keys recorded per credentials id, mirroring the real repository's identity signature (ENC-28). */
    private val apiKeysByCredentialsId = mutableMapOf<ApiCredentialsId, String>()

    fun seedCredentials(
        credentials: List<ApiCredentials>,
        apiKeys: Map<ApiCredentialsId, String> = emptyMap(),
    ) {
        credentialsFlow.value = credentials
        apiKeysByCredentialsId.putAll(apiKeys)
    }

    fun seedConfigurations(configurations: List<ApiModelConfiguration>) {
        configurationsFlow.value = configurations
    }

    override fun observeAllCredentials(): Flow<List<ApiCredentials>> = credentialsFlow.asStateFlow()

    override fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>> = configurationsFlow.asStateFlow()

    override suspend fun getAllCredentials(): List<ApiCredentials> = credentialsFlow.value

    override suspend fun getAllCredentialAliases(): Set<String> =
        credentialsFlow.value.map { it.credentialAlias }.toSet() + additionalCredentialAliases

    override suspend fun getCredentialsById(id: ApiCredentialsId): ApiCredentials? =
        credentialsFlow.value.firstOrNull { it.id == id }

    override suspend fun saveCredentials(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentialsId {
        saveCredentialsCalls += SaveCredentialsCall(credentials, apiKey, sourceCredentialAlias)
        // When a result id is forced, the stored row carries it too, mirroring a
        // repository that assigns the id on insert (ENC-28).
        val effectiveId = saveCredentialsResultId ?: credentials.id
        val stored = if (credentials.id == effectiveId) credentials else credentials.copy(id = effectiveId)
        apiKeysByCredentialsId[stored.id] = apiKey
        val existing = credentialsFlow.value.firstOrNull { it.id == stored.id }
        credentialsFlow.value =
            if (existing == null) {
                credentialsFlow.value + stored
            } else {
                credentialsFlow.value.map { if (it.id == stored.id) stored else it }
            }
        return effectiveId
    }

    override suspend fun findMatchingCredentials(
        provider: ApiProvider,
        baseUrl: String?,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentials? =
        credentialsFlow.value.firstOrNull {
            it.provider == provider && it.baseUrl == baseUrl &&
                (sourceCredentialAlias == null || it.credentialAlias == sourceCredentialAlias) &&
                // The real repository matches on a (provider, baseUrl, apiKey) signature; when a
                // key is recorded for a credential, a different key must not match (ENC-28).
                (apiKeysByCredentialsId[it.id] == null || apiKeysByCredentialsId[it.id] == apiKey)
        }

    override suspend fun deleteCredentials(id: ApiCredentialsId) {
        deleteCredentialsCalls += id
        credentialsFlow.value = credentialsFlow.value.filterNot { it.id == id }
    }

    override suspend fun getConfigurationsForCredentials(credentialsId: ApiCredentialsId): List<ApiModelConfiguration> =
        configurationsFlow.value.filter { it.apiCredentialsId == credentialsId }

    override suspend fun getConfigurationById(id: ApiModelConfigurationId): ApiModelConfiguration? =
        configurationsFlow.value.firstOrNull { it.id == id }

    override suspend fun saveConfiguration(config: ApiModelConfiguration): ApiModelConfigurationId {
        saveConfigurationCalls += config
        val existing = configurationsFlow.value.firstOrNull { it.id == config.id }
        configurationsFlow.value =
            if (existing == null) {
                configurationsFlow.value + config
            } else {
                configurationsFlow.value.map { if (it.id == config.id) config else it }
            }
        return config.id
    }

    override suspend fun deleteConfiguration(id: ApiModelConfigurationId) {
        configurationsFlow.value = configurationsFlow.value.filterNot { it.id == id }
    }

    override suspend fun deleteConfigurationsForCredentials(credentialsId: ApiCredentialsId) {
        configurationsFlow.value = configurationsFlow.value.filterNot { it.apiCredentialsId == credentialsId }
    }
}
