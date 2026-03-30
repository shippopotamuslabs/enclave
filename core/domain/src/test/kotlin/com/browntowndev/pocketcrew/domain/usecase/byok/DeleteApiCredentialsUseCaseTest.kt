package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteApiCredentialsUseCaseTest {
    private val apiRepository = mockk<ApiModelRepositoryPort>()
    private val defaultRepository = mockk<DefaultModelRepositoryPort>()
    private val transactionProvider = mockk<TransactionProvider>()
    private lateinit var useCase: DeleteApiCredentialsUseCase

    @BeforeEach
    fun setup() {
        useCase = DeleteApiCredentialsUseCaseImpl(apiRepository, defaultRepository, transactionProvider)
        coEvery { transactionProvider.runInTransaction<Unit>(any()) } coAnswers {
            (args[0] as suspend () -> Unit).invoke()
        }
    }

    @Test
    fun `invoke deletes credentials and cascaded configurations`() = runTest {
        val credentialsId = 1L
        coEvery { apiRepository.deleteCredentials(credentialsId) } returns Unit

        useCase(credentialsId)

        coVerify(exactly = 1) { apiRepository.deleteCredentials(credentialsId) }
    }
}