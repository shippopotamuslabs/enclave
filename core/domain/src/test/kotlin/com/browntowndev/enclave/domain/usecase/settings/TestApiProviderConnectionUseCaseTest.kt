package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.usecase.byok.FetchApiProviderModelsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.full.declaredFunctions

class TestApiProviderConnectionUseCaseTest {
    private val fetchApiProviderModelsUseCase = mockk<FetchApiProviderModelsUseCase>()
    private val useCase = TestApiProviderConnectionUseCase(fetchApiProviderModelsUseCase)

    @Test
    fun `SCN-USECASE-TEST-001 Test Connection Success`() =
        runTest {
            coEvery {
                fetchApiProviderModelsUseCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "sk-valid",
                    credentialAlias = null,
                    baseUrl = null,
                )
            } returns listOf(DiscoveredApiModel(id = "gpt-4o", name = "GPT-4o"))

            val result =
                useCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "sk-valid",
                    credentialAlias = null,
                    baseUrl = null,
                )

            assertTrue(result.isSuccess)
        }

    @Test
    fun `SCN-USECASE-TEST-002 Test Connection Failure`() =
        runTest {
            coEvery {
                fetchApiProviderModelsUseCase(any(), any(), any(), any())
            } throws RuntimeException("HTTP 401")

            val result =
                useCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "invalid",
                    credentialAlias = null,
                    baseUrl = null,
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
        }

    @Test
    fun `SCN-USECASE-TEST-003 Test Connection Without Model`() =
        runTest {
            coEvery {
                fetchApiProviderModelsUseCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "sk-valid",
                    credentialAlias = null,
                    baseUrl = null,
                )
            } returns emptyList()

            val result =
                useCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "sk-valid",
                    credentialAlias = null,
                    baseUrl = null,
                )

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                fetchApiProviderModelsUseCase(
                    provider = ApiProvider.OPENAI,
                    currentApiKey = "sk-valid",
                    credentialAlias = null,
                    baseUrl = null,
                )
            }
            val invokeFunction =
                TestApiProviderConnectionUseCase::class.declaredFunctions.first { it.name == "invoke" }
            val paramNames =
                invokeFunction.parameters
                    .mapNotNull { it.name }
                    .filter { it != "\$this" && it != "\$completion" }
            assertEquals(listOf("provider", "currentApiKey", "credentialAlias", "baseUrl"), paramNames)
            assertFalse("modelId" in paramNames)
        }
}
