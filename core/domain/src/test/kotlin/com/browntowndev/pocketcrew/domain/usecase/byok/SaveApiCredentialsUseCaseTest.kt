package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
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
            displayName = "Test Provider",
            provider = ApiProvider.OPENAI,
            modelId = "gpt-4",
            credentialAlias = "test_alias"
        )
        val apiKey = "test_key"
        coEvery { repository.saveCredentials(credentials, apiKey) } returns 1L

        val result = useCase(credentials, apiKey)

        assertEquals(1L, result)
        coVerify(exactly = 1) { repository.saveCredentials(credentials, apiKey) }
    }
}