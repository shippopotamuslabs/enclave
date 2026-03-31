package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.settings.SystemPromptOption
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateSelectedPromptOptionUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: UpdateSelectedPromptOptionUseCase

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        useCase = UpdateSelectedPromptOptionUseCase(settingsRepository)
    }

    @Test
    fun `invoke calls repository with CONCISE option`() = runTest {
        val option = SystemPromptOption.CONCISE
        coEvery { settingsRepository.updateSelectedPromptOption(option) } returns Unit

        useCase(option)

        coVerify(exactly = 1) { settingsRepository.updateSelectedPromptOption(option) }
    }

    @Test
    fun `invoke calls repository with CUSTOM option`() = runTest {
        val option = SystemPromptOption.CUSTOM
        coEvery { settingsRepository.updateSelectedPromptOption(option) } returns Unit

        useCase(option)

        coVerify(exactly = 1) { settingsRepository.updateSelectedPromptOption(option) }
    }

    @Test
    fun `invoke calls repository with FORMAL option`() = runTest {
        val option = SystemPromptOption.FORMAL
        coEvery { settingsRepository.updateSelectedPromptOption(option) } returns Unit

        useCase(option)

        coVerify(exactly = 1) { settingsRepository.updateSelectedPromptOption(option) }
    }

    @Test
    fun `invoke calls repository with RIGOROUS option`() = runTest {
        val option = SystemPromptOption.RIGOROUS
        coEvery { settingsRepository.updateSelectedPromptOption(option) } returns Unit

        useCase(option)

        coVerify(exactly = 1) { settingsRepository.updateSelectedPromptOption(option) }
    }
}
