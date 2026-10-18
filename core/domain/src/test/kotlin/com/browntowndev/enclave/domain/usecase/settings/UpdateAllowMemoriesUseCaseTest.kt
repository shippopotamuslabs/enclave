package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UpdateAllowMemoriesUseCaseTest {
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var useCase: UpdateAllowMemoriesUseCase

    @BeforeEach
    fun setup() {
        settingsRepository = FakeSettingsRepository()
        useCase = UpdateAllowMemoriesUseCase(settingsRepository)
    }

    @Test
    fun `invoke with true calls updateAllowMemories with true`() =
        runTest {
            // Act
            useCase(true)

            // Assert
            assertEquals(listOf(true), settingsRepository.allowMemoriesUpdates)
        }

    @Test
    fun `invoke with false calls updateAllowMemories with false`() =
        runTest {
            // Act
            useCase(false)

            // Assert
            assertEquals(listOf(false), settingsRepository.allowMemoriesUpdates)
        }
}
