package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteApiCredentialsUseCaseTest {
    private val apiRepository = mockk<ApiModelRepositoryPort>()
    private val modelRegistry = mockk<ModelRegistryPort>()
    private val defaultRepository = mockk<DefaultModelRepositoryPort>()
    private val transactionProvider = mockk<TransactionProvider>()
    private lateinit var useCase: DeleteApiCredentialsUseCase

    @BeforeEach
    fun setup() {
        useCase = DeleteApiCredentialsUseCaseImpl(
            apiModelRepository = apiRepository,
            modelRegistry = modelRegistry,
            defaultModelRepository = defaultRepository,
            transactionProvider = transactionProvider
        )
        coEvery { transactionProvider.runInTransaction<Unit>(any()) } coAnswers {
            (args[0] as suspend () -> Unit).invoke()
        }
        coEvery { modelRegistry.getRegisteredAssets() } returns emptyList()
        coEvery { apiRepository.getAllCredentials() } returns listOf(
            mockk<ApiCredentials> { coEvery { id } returns 1L },
            mockk<ApiCredentials> { coEvery { id } returns 2L }
        )
        coEvery { apiRepository.getConfigurationsForCredentials(any()) } returns emptyList()
        coEvery { defaultRepository.observeDefaults() } returns flowOf(emptyList())
    }

    @Test
    fun `invoke deletes credentials and cascaded configurations`() = runTest {
        val credentialsId = 1L
        coEvery { apiRepository.deleteCredentials(credentialsId) } returns Unit

        useCase(credentialsId)

        coVerify(exactly = 1) { apiRepository.deleteCredentials(credentialsId) }
    }

    @Test
    fun `isLastModel returns false when another api credential exists`() = runTest {
        assertFalse(useCase.isLastModel(1L))
    }
}
