package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateAllowMemoriesUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: UpdateAllowMemoriesUseCase

    @BeforeEach
    fun setup() {
        settingsRepository = mockk()
        useCase = UpdateAllowMemoriesUseCase(settingsRepository)
    }

    @Test
    fun `invoke with true calls updateAllowMemories with true`() = runTest {
        // Arrange
        coEvery { settingsRepository.updateAllowMemories(true) } returns Unit

        // Act
        useCase(true)

        // Assert
        coVerify(exactly = 1) { settingsRepository.updateAllowMemories(true) }
    }

    @Test
    fun `invoke with false calls updateAllowMemories with false`() = runTest {
        // Arrange
        coEvery { settingsRepository.updateAllowMemories(false) } returns Unit

        // Act
        useCase(false)

        // Assert
        coVerify(exactly = 1) { settingsRepository.updateAllowMemories(false) }
    }
}
