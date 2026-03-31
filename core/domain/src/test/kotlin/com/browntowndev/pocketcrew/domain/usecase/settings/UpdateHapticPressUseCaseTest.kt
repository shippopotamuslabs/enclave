package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateHapticPressUseCaseTest {

    private lateinit var useCase: UpdateHapticPressUseCase
    private lateinit var mockRepository: SettingsRepository

    @BeforeEach
    fun setup() {
        mockRepository = mockk(relaxed = true)
        useCase = UpdateHapticPressUseCase(mockRepository)
    }

    @Test
    fun `invoke calls repository with true when enabled is true`() = runTest {
        // Arrange
        val isEnabled = true
        coEvery { mockRepository.updateHapticPress(isEnabled) } returns Unit

        // Act
        useCase(isEnabled)

        // Assert
        coVerify(exactly = 1) { mockRepository.updateHapticPress(isEnabled) }
    }

    @Test
    fun `invoke calls repository with false when enabled is false`() = runTest {
        // Arrange
        val isEnabled = false
        coEvery { mockRepository.updateHapticPress(isEnabled) } returns Unit

        // Act
        useCase(isEnabled)

        // Assert
        coVerify(exactly = 1) { mockRepository.updateHapticPress(isEnabled) }
    }
}
