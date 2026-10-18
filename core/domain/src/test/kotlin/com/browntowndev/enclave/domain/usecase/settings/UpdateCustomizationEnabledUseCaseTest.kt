package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateCustomizationEnabledUseCaseTest {
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var updateCustomizationEnabledUseCase: UpdateCustomizationEnabledUseCase

    @BeforeEach
    fun setUp() {
        settingsRepository = FakeSettingsRepository()
        updateCustomizationEnabledUseCase = UpdateCustomizationEnabledUseCase(settingsRepository)
    }

    @Test
    fun `invoke with true calls updateCustomizationEnabled with true`() =
        runTest {
            // Given
            val enabled = true

            // When
            updateCustomizationEnabledUseCase(enabled)

            // Then
            assertEquals(listOf(true), settingsRepository.customizationEnabledUpdates)
        }

    @Test
    fun `invoke with false calls updateCustomizationEnabled with false`() =
        runTest {
            // Given
            val enabled = false

            // When
            updateCustomizationEnabledUseCase(enabled)

            // Then
            assertEquals(listOf(false), settingsRepository.customizationEnabledUpdates)
        }
}
