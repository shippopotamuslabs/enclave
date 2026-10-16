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

    fun seedCredentials(credentials: List<ApiCredentials>) {
        credentialsFlow.value = credentials
    }

    fun seedConfigurations(configurations: List<ApiModelConfiguration>) {
        configurationsFlow.value = configurations
    }

    override fun observeAllCredentials(): Flow<List<ApiCredentials>> = credentialsFlow.asStateFlow()

    override fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>> = configurationsFlow.asStateFlow()

    override suspend fun getAllCredentials(): List<ApiCredentials> = credentialsFlow.value

    override suspend fun getAllCredentialAliases(): Set<String> =
        credentialsFlow.value.map { it.credentialAlias }.toSet()

    override suspend fun getCredentialsById(id: ApiCredentialsId): ApiCredentials? =
        credentialsFlow.value.firstOrNull { it.id == id }

    override suspend fun saveCredentials(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentialsId {
        val existing = credentialsFlow.value.firstOrNull { it.id == credentials.id }
        credentialsFlow.value =
            if (existing == null) {
                credentialsFlow.value + credentials
            } else {
                credentialsFlow.value.map { if (it.id == credentials.id) credentials else it }
            }
        return credentials.id
    }

    override suspend fun findMatchingCredentials(
        provider: ApiProvider,
        baseUrl: String?,
        apiKey: String,
        sourceCredentialAlias: String?,
    ): ApiCredentials? =
        credentialsFlow.value.firstOrNull {
            it.provider == provider && it.baseUrl == baseUrl &&
                (sourceCredentialAlias == null || it.credentialAlias == sourceCredentialAlias)
        }

    override suspend fun deleteCredentials(id: ApiCredentialsId) {
        credentialsFlow.value = credentialsFlow.value.filterNot { it.id == id }
    }

    override suspend fun getConfigurationsForCredentials(credentialsId: ApiCredentialsId): List<ApiModelConfiguration> =
        configurationsFlow.value.filter { it.apiCredentialsId == credentialsId }

    override suspend fun getConfigurationById(id: ApiModelConfigurationId): ApiModelConfiguration? =
        configurationsFlow.value.firstOrNull { it.id == id }

    override suspend fun saveConfiguration(config: ApiModelConfiguration): ApiModelConfigurationId {
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
