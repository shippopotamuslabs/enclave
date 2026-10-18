package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetApiModelAssetsUseCaseTest {
    private val repository = FakeApiModelRepository()
    private lateinit var useCase: GetApiModelAssetsUseCase

    @BeforeEach
    fun setup() {
        useCase = GetApiModelAssetsUseCaseImpl(repository)
    }

    @Test
    fun `invoke returns stream of api model assets`() =
        runTest {
            val credId = ApiCredentialsId("1")
            val creds =
                ApiCredentials(
                    id = credId,
                    displayName = "Test Provider",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "test_alias",
                )
            val config =
                ApiModelConfiguration(
                    id = ApiModelConfigurationId("config-1"),
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    displayName = "Test Config",
                )
            val configs = listOf(config)
            repository.seedCredentials(listOf(creds))
            repository.seedConfigurations(configs)

            val result = useCase().first()

            assertEquals(1, result.size)
            assertEquals(creds, result[0].credentials)
            assertEquals(configs, result[0].configurations)
        }
}
