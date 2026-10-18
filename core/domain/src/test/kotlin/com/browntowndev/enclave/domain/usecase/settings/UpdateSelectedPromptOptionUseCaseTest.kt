package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.settings.SystemPromptOption
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateSelectedPromptOptionUseCaseTest {
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var useCase: UpdateSelectedPromptOptionUseCase

    @BeforeEach
    fun setup() {
        settingsRepository = FakeSettingsRepository()
        useCase = UpdateSelectedPromptOptionUseCase(settingsRepository)
    }

    @Test
    fun `invoke calls repository with CONCISE option`() =
        runTest {
            val option = SystemPromptOption.CONCISE

            useCase(option)

            assertEquals(listOf(option), settingsRepository.selectedPromptOptionUpdates)
        }

    @Test
    fun `invoke calls repository with CUSTOM option`() =
        runTest {
            val option = SystemPromptOption.CUSTOM

            useCase(option)

            assertEquals(listOf(option), settingsRepository.selectedPromptOptionUpdates)
        }

    @Test
    fun `invoke calls repository with FORMAL option`() =
        runTest {
            val option = SystemPromptOption.FORMAL

            useCase(option)

            assertEquals(listOf(option), settingsRepository.selectedPromptOptionUpdates)
        }

    @Test
    fun `invoke calls repository with RIGOROUS option`() =
        runTest {
            val option = SystemPromptOption.RIGOROUS

            useCase(option)

            assertEquals(listOf(option), settingsRepository.selectedPromptOptionUpdates)
        }
}
