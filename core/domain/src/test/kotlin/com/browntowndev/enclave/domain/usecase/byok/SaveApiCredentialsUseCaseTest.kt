package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.domain.model.config.ApiCredentials
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveApiCredentialsUseCaseTest {
    private val repository = FakeApiModelRepository()
    private lateinit var useCase: SaveApiCredentialsUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveApiCredentialsUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveCredentials`() =
        runTest {
            val credentials =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test Provider",
                    provider = ApiProvider.OPENAI,
                    credentialAlias = "test_alias",
                )
            val apiKey = "test_key"
            val expectedId = ApiCredentialsId("1")
            repository.saveCredentialsResultId = expectedId

            val result = useCase(credentials, apiKey)

            assertEquals(expectedId, result)
            assertEquals(
                listOf(FakeApiModelRepository.SaveCredentialsCall(credentials, apiKey, null)),
                repository.saveCredentialsCalls,
            )
        }

    @Test
    fun `invoke forwards source alias when reusing a stored key`() =
        runTest {
            val credentials =
                ApiCredentials(
                    id = ApiCredentialsId(""),
                    displayName = "Test Provider",
                    provider = ApiProvider.XAI,
                    credentialAlias = "new_alias",
                )
            val expectedId = ApiCredentialsId("7")
            repository.saveCredentialsResultId = expectedId

            val result = useCase(credentials, apiKey = "", sourceCredentialAlias = "existing_alias")

            assertEquals(expectedId, result)
            assertEquals(
                listOf(FakeApiModelRepository.SaveCredentialsCall(credentials, "", "existing_alias")),
                repository.saveCredentialsCalls,
            )
        }
}
