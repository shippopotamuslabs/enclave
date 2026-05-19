package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteApiCredentialsUseCaseTest {
    private val apiModelRepository: ApiModelRepositoryPort = mockk()
    private val localModelRepository: LocalModelRepositoryPort = mockk()
    private val defaultModelRepository: DefaultModelRepositoryPort = mockk()
    private val transactionProvider: TransactionProvider = FakeTransactionProvider()

    private lateinit var useCase: DeleteApiCredentialsUseCaseImpl

    @BeforeEach
    fun setUp() {
        useCase =
            DeleteApiCredentialsUseCaseImpl(
                apiModelRepository,
                localModelRepository,
                defaultModelRepository,
                transactionProvider,
            )
    }

    class FakeTransactionProvider : TransactionProvider {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    @Test
    fun `isLastModel returns true if only one api credentials and no local models`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            coEvery { localModelRepository.getAllLocalAssets() } returns emptyList()
            coEvery { apiModelRepository.getAllCredentials() } returns listOf(mockk { every { this@mockk.id } returns id })

            val result = useCase.isLastModel(id)

            assertTrue(result)
        }

    @Test
    fun `isLastModel returns false if there are local models`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            coEvery { localModelRepository.getAllLocalAssets() } returns listOf(mockk())
            coEvery { apiModelRepository.getAllCredentials() } returns listOf(mockk { every { this@mockk.id } returns id })

            val result = useCase.isLastModel(id)

            assertFalse(result)
        }

    @Test
    fun `invoke fails if it is the last model`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            coEvery { localModelRepository.getAllLocalAssets() } returns emptyList()
            coEvery { apiModelRepository.getAllCredentials() } returns listOf(mockk { every { this@mockk.id } returns id })

            val result = useCase(id)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun `invoke reassigns defaults before deleting`() =
        runTest {
            val id = ApiCredentialsId("api-1")
            val configId = ApiModelConfigurationId("config-1")
            val replacementId = ApiModelConfigurationId("replacement-1")

            coEvery { localModelRepository.getAllLocalAssets() } returns listOf(mockk()) // Not last model
            coEvery { apiModelRepository.getAllCredentials() } returns listOf(mockk { every { this@mockk.id } returns id }, mockk())

            coEvery { apiModelRepository.getConfigurationsForCredentials(id) } returns
                listOf(
                    mockk { every { this@mockk.id } returns configId },
                )
            coEvery { defaultModelRepository.observeDefaults() } returns
                flowOf(
                    listOf(
                        mockk {
                            every { modelType } returns ModelType.MAIN
                            every { apiConfigId } returns configId
                        },
                    ),
                )

            coEvery { defaultModelRepository.setDefault(ModelType.MAIN, null, replacementId) } returns Unit
            coEvery { apiModelRepository.deleteCredentials(id) } returns Unit

            val result = useCase(id, null, replacementId)

            assertTrue(result.isSuccess)
            coVerify { defaultModelRepository.setDefault(ModelType.MAIN, null, replacementId) }
            coVerify { apiModelRepository.deleteCredentials(id) }
        }
}
