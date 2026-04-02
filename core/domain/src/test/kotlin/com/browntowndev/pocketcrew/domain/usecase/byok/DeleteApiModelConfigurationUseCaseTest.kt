package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
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
        coEvery { defaultRepository.observeDefaults() } returns flowOf(emptyList())
    }

    @Test
    fun `invoke deletes specific configuration`() = runTest {
        val configId = 1L
        coEvery { apiRepository.deleteConfiguration(configId) } returns Unit

        val result = useCase(configId)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiRepository.deleteConfiguration(configId) }
    }
}
