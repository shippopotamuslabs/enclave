package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateCustomizationEnabledUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var updateCustomizationEnabledUseCase: UpdateCustomizationEnabledUseCase

    @BeforeEach
    fun setUp() {
        settingsRepository = mockk()
        updateCustomizationEnabledUseCase = UpdateCustomizationEnabledUseCase(settingsRepository)
    }

    @Test
    fun `invoke with true calls updateCustomizationEnabled with true`() = runTest {
        // Given
        val enabled = true
        coEvery { settingsRepository.updateCustomizationEnabled(any()) } returns Unit

        // When
        updateCustomizationEnabledUseCase(enabled)

        // Then
        coVerify(exactly = 1) { settingsRepository.updateCustomizationEnabled(enabled) }
    }

    @Test
    fun `invoke with false calls updateCustomizationEnabled with false`() = runTest {
        // Given
        val enabled = false
        coEvery { settingsRepository.updateCustomizationEnabled(any()) } returns Unit

        // When
        updateCustomizationEnabledUseCase(enabled)

        // Then
        coVerify(exactly = 1) { settingsRepository.updateCustomizationEnabled(enabled) }
    }
}
