package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SaveApiCredentialsUseCaseTest {
    private val repository = mockk<ApiModelRepositoryPort>()
    private lateinit var useCase: SaveApiCredentialsUseCase

    @BeforeEach
    fun setup() {
        useCase = SaveApiCredentialsUseCaseImpl(repository)
    }

    @Test
    fun `invoke calls repository saveCredentials`() = runTest {
        val credentials = ApiCredentials(
            id = ApiCredentialsId(""),
            displayName = "Test Provider",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4",
            credentialAlias = "test_alias"
        )
        val apiKey = "test_key"
        val expectedId = ApiCredentialsId("1")
        coEvery { repository.saveCredentials(credentials, apiKey, null) } returns expectedId

        val result = useCase(credentials, apiKey)

        assertEquals(expectedId, result)
        coVerify(exactly = 1) { repository.saveCredentials(credentials, apiKey, null) }
    }

    @Test
    fun `invoke forwards source alias when reusing a stored key`() = runTest {
        val credentials = ApiCredentials(
            id = ApiCredentialsId(""),
            displayName = "Test Provider",
            provider = ApiProvider.XAI,
            modelId = "grok-4.20",
            credentialAlias = "new_alias"
        )
        val expectedId = ApiCredentialsId("7")
        coEvery { repository.saveCredentials(credentials, "", "existing_alias") } returns expectedId

        val result = useCase(credentials, apiKey = "", sourceCredentialAlias = "existing_alias")

        assertEquals(expectedId, result)
        coVerify(exactly = 1) {
            repository.saveCredentials(credentials, "", "existing_alias")
        }
    }
}
