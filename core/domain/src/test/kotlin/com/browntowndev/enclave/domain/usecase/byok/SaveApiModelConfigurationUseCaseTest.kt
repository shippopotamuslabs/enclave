package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveApiModelConfigurationUseCaseTest {
    private val repository = FakeApiModelRepository()
    private lateinit var useCase: SaveApiModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveApiModelConfigurationUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveConfiguration`() =
        runTest {
            val credId = ApiCredentialsId("10")
            val config =
                ApiModelConfiguration(
                    id = ApiModelConfigurationId("1"),
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    displayName = "Test Config",
                )
            repository.seedCredentials(
                listOf(
                    ApiCredentials(
                        id = credId,
                        displayName = "Test Provider",
                        provider = ApiProvider.OPENAI,
                        credentialAlias = "test_alias",
                    ),
                ),
            )

            val result = useCase(config)

            assertTrue(result.isSuccess)
            assertEquals(ApiModelConfigurationId("1"), result.getOrNull())
            assertEquals(listOf(config), repository.saveConfigurationCalls)
        }

    @Test
    fun `invoke returns failure when parent credentials do not exist`() =
        runTest {
            val credId = ApiCredentialsId("999")
            val config =
                ApiModelConfiguration(
                    id = ApiModelConfigurationId("1"),
                    apiCredentialsId = credId,
                    modelId = "gpt-4o",
                    displayName = "Test Config",
                )
            // No credentials seeded, so getCredentialsById returns null.

            val result = useCase(config)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }
}
