package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelAsset
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetApiModelAssetsUseCaseTest {
    private val repository = mockk<ApiModelRepositoryPort>()
    private lateinit var useCase: GetApiModelAssetsUseCase

    @BeforeEach
    fun setup() {
        useCase = GetApiModelAssetsUseCaseImpl(repository)
    }

    @Test
    fun `invoke returns stream of api model assets`() = runTest {
        val creds = mockk<ApiCredentials> { every { id } returns 1L }
        val config = mockk<ApiModelConfiguration> { every { apiCredentialsId } returns 1L }
        val configs = listOf(config)
        
        every { repository.observeAllCredentials() } returns flowOf(listOf(creds))
        every { repository.observeAllConfigurations() } returns flowOf(configs)

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals(creds, result[0].credentials)
        assertEquals(configs, result[0].configurations)
    }
}