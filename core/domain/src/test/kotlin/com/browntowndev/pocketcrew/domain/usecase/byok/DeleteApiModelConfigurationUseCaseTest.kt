package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteApiModelConfigurationUseCaseTest {
    private val apiRepository = mockk<ApiModelRepositoryPort>()
    private val defaultRepository = mockk<DefaultModelRepositoryPort>()
    private val transactionProvider = mockk<TransactionProvider>()
    private lateinit var useCase: DeleteApiModelConfigurationUseCase

    @BeforeEach
    fun setup() {
        useCase = DeleteApiModelConfigurationUseCaseImpl(apiRepository, defaultRepository, transactionProvider)
        coEvery { transactionProvider.runInTransaction<Unit>(any()) } coAnswers {
            (args[0] as suspend () -> Unit).invoke()
        }
    }

    @Test
    fun `invoke deletes specific configuration`() = runTest {
        val configId = 1L
        coEvery { apiRepository.deleteConfigurationsForCredentials(configId) } returns Unit // Assuming this name for now, update if plural

        val result = useCase(configId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiRepository.deleteConfigurationsForCredentials(configId) }
    }
}